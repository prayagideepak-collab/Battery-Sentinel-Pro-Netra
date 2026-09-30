package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.StorageCacheManager
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType
import com.example.model.FieldStatus
import com.example.model.NetraCentralState
import com.example.model.TelemetryFieldState
import com.example.service.CentralCapabilityRegistry
import com.example.service.NetraCentralDataCenter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageCacheAndCapabilityTest {

    private lateinit var context: Context
    private lateinit var dataCenter: NetraCentralDataCenter
    private lateinit var capabilityRegistry: CentralCapabilityRegistry
    private lateinit var storageCacheManager: StorageCacheManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dataCenter = NetraCentralDataCenter()
        capabilityRegistry = CentralCapabilityRegistry(context)
        storageCacheManager = StorageCacheManager(context)
    }

    @Test
    fun testValidCentralUnitDataSurvivesLaterPartialUpdate() = runBlocking {
        // Step 1: Complete initial sample
        dataCenter.processRawInput(
            level = 63,
            scale = 100,
            status = 2, // Charging
            plugged = 1, // AC
            temperatureRaw = 341, // 34.1°C
            voltage = 4200, // 4200 mV
            currentMicroAmps = 1800000, // 1800 mA
            bluetoothConnected = false,
            bluetoothBattery = null
        )

        var state = dataCenter.centralState.value
        assertEquals(63, state.batteryLevel)
        assertEquals(34.1f, state.temperatureCelsius ?: 0f, 0.01f)
        assertEquals(4200, state.voltageMv)
        assertEquals(1800, state.currentMa)
        assertEquals(FieldStatus.LIVE, state.fieldStates.levelStatus)
        assertEquals(FieldStatus.LIVE, state.fieldStates.tempStatus)
        assertEquals(FieldStatus.LIVE, state.fieldStates.voltageStatus)
        assertEquals(FieldStatus.LIVE, state.fieldStates.currentStatus)

        // Step 2: Partial sample (temperature missing, current missing)
        dataCenter.processRawInput(
            level = 64,
            scale = 100,
            status = 2,
            plugged = 1,
            temperatureRaw = -1, // missing/unavailable
            voltage = 4200,
            currentMicroAmps = Int.MIN_VALUE, // missing/unavailable
            bluetoothConnected = false,
            bluetoothBattery = null
        )

        state = dataCenter.centralState.value
        // Battery level updated
        assertEquals(64, state.batteryLevel)
        assertEquals(FieldStatus.LIVE, state.fieldStates.levelStatus)
        // Temperature retained previous valid value (34.1°C) and marked LAST_VALID
        assertEquals(34.1f, state.temperatureCelsius ?: 0f, 0.01f)
        assertEquals(FieldStatus.LAST_VALID, state.fieldStates.tempStatus)
        // Voltage retained / updated
        assertEquals(4200, state.voltageMv)
        assertEquals(FieldStatus.LIVE, state.fieldStates.voltageStatus)
        // Current retained previous valid value (1800 mA) and marked LAST_VALID
        assertEquals(1800, state.currentMa)
        assertEquals(FieldStatus.LAST_VALID, state.fieldStates.currentStatus)
    }

    @Test
    fun testPersistedLastKnownDataIsNotIncorrectlyMarkedLive() {
        val prefs = context.getSharedPreferences("netra_last_valid_state_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("saved_battery_level", 75)
            .putFloat("saved_temp", 31.0f)
            .putInt("saved_voltage", 4150)
            .putInt("saved_current", 1200)
            .putFloat("saved_power", 4.98f)
            .apply()

        val freshDataCenter = NetraCentralDataCenter()
        freshDataCenter.initPersistence(context)

        val restoredState = freshDataCenter.centralState.value
        assertEquals(75, restoredState.batteryLevel)
        assertEquals(31.0f, restoredState.temperatureCelsius ?: 0f, 0.01f)
        // MUST NOT be marked live!
        assertFalse(restoredState.isDataFresh)
        assertEquals(FieldStatus.LAST_VALID, restoredState.fieldStates.levelStatus)
        assertEquals(FieldStatus.LAST_VALID, restoredState.fieldStates.tempStatus)
    }

    @Test
    fun testLiveCentralStateWinsOverCachedData() = runBlocking {
        // Init with persisted stale data
        val prefs = context.getSharedPreferences("netra_last_valid_state_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("saved_battery_level", 50).apply()

        val freshDataCenter = NetraCentralDataCenter()
        freshDataCenter.initPersistence(context)
        assertEquals(50, freshDataCenter.centralState.value.batteryLevel)
        assertFalse(freshDataCenter.centralState.value.isDataFresh)

        // Incoming live telemetry overrides cached data
        freshDataCenter.processRawInput(
            level = 88,
            scale = 100,
            status = 3, // Discharging
            plugged = 0,
            temperatureRaw = 295,
            voltage = 3900,
            currentMicroAmps = -350000,
            bluetoothConnected = false,
            bluetoothBattery = null
        )

        val liveState = freshDataCenter.centralState.value
        assertEquals(88, liveState.batteryLevel)
        assertTrue(liveState.isDataFresh)
        assertEquals(FieldStatus.LIVE, liveState.fieldStates.levelStatus)
    }

    @Test
    fun testCacheCleanupSafetyAndThreshold() = runBlocking {
        // Create dummy cache file in cacheDir
        val cacheFile = File(context.cacheDir, "temp_data.bin")
        cacheFile.writeBytes(ByteArray(1024 * 50)) // 50 KB
        assertTrue(cacheFile.exists())

        // Create a simulated database file to ensure USER DATA is NEVER deleted
        val dbDir = File(context.filesDir, "databases")
        dbDir.mkdirs()
        val dummyDb = File(dbDir, "netra_database.db")
        dummyDb.writeText("CRITICAL_USER_DATABASE_DATA")
        assertTrue(dummyDb.exists())

        val freed = storageCacheManager.cleanCache(force = true)
        assertTrue(freed > 0)
        assertFalse(cacheFile.exists()) // Cache file deleted
        assertTrue(dummyDb.exists()) // User database PRESERVED!
    }

    @Test
    fun testCapabilityRegistryDistinguishesStates() {
        val capabilities = capabilityRegistry.detectAllCapabilities(
            currentMicroAmps = 1500000,
            temperatureRaw = 320,
            voltageRaw = 4100,
            hasBluetoothHardware = true,
            hasBluetoothPermission = false,
            isBluetoothEnabled = true,
            connectedBluetoothCount = 0
        )

        // Core battery telemetry is available
        assertEquals(CapabilityStatus.AVAILABLE, capabilities[CapabilityType.BATTERY_TELEMETRY])
        assertEquals(CapabilityStatus.AVAILABLE, capabilities[CapabilityType.BATTERY_TEMPERATURE])
        assertEquals(CapabilityStatus.AVAILABLE, capabilities[CapabilityType.BATTERY_VOLTAGE])
        assertEquals(CapabilityStatus.AVAILABLE, capabilities[CapabilityType.BATTERY_CURRENT])

        // When Bluetooth permission is false, connected info is PERMISSION_REQUIRED
        assertEquals(CapabilityStatus.PERMISSION_REQUIRED, capabilities[CapabilityType.BLUETOOTH_CONNECTED_INFO])
    }

    @Test
    fun testBluetoothDisabledCapabilityState() {
        val capabilities = capabilityRegistry.detectAllCapabilities(
            hasBluetoothHardware = true,
            hasBluetoothPermission = true,
            isBluetoothEnabled = false,
            connectedBluetoothCount = 0
        )

        // When Bluetooth is switched off, state is DISABLED
        assertEquals(CapabilityStatus.DISABLED, capabilities[CapabilityType.BLUETOOTH_CONNECTED_INFO])
    }

    @Test
    fun testBatteryCurrentUnavailableWhenOemRestricted() {
        val capabilities = capabilityRegistry.detectAllCapabilities(
            currentMicroAmps = Int.MIN_VALUE, // OEM restricted property
            temperatureRaw = 300,
            voltageRaw = 4000
        )

        assertEquals(CapabilityStatus.UNAVAILABLE, capabilities[CapabilityType.BATTERY_CURRENT])
        assertEquals(CapabilityStatus.UNAVAILABLE, capabilities[CapabilityType.BATTERY_POWER_CALCULATION])
    }
}
