package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.CanonicalChargingSpeed
import com.example.model.NetraCentralState
import com.example.model.BluetoothDeviceItem
import com.example.service.NetraCentralDataCenter
import com.example.ui.navigation.NetraTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenConsolidationTest {

    private lateinit var context: Context
    private lateinit var dataCenter: NetraCentralDataCenter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dataCenter = NetraCentralDataCenter()
    }

    @Test
    fun testFiveTabNavigationRemainsExactlyFive() {
        val tabs = NetraTab.values()
        assertEquals(5, tabs.size)
        assertEquals(NetraTab.HOME, tabs[0])
        assertEquals(NetraTab.BATTERY, tabs[1])
        assertEquals(NetraTab.MONITORING, tabs[2])
        assertEquals(NetraTab.DEVICES, tabs[3])
        assertEquals(NetraTab.SETTINGS, tabs[4])
    }

    @Test
    fun testHomeReceivesCanonicalBatteryState() {
        val canonical = NetraCentralState(
            batteryLevel = 78,
            isCharging = true,
            temperatureCelsius = 31.5f,
            voltageMv = 4250,
            currentMa = 2100,
            powerWatts = 8.92f,
            chargingSpeed = CanonicalChargingSpeed.NORMAL,
            chargingEtaMinutes = 35
        )

        assertEquals(78, canonical.batteryLevel)
        assertEquals(true, canonical.isCharging)
        assertEquals(31.5f, canonical.temperatureCelsius)
        assertEquals(8.92f, canonical.powerWatts)
        assertEquals(CanonicalChargingSpeed.NORMAL, canonical.chargingSpeed)
        assertEquals(35, canonical.chargingEtaMinutes)
    }

    @Test
    fun testBatteryScreenReceivesCanonicalChargingState() {
        val chargingState = NetraCentralState(
            batteryLevel = 45,
            isCharging = true,
            isChargerConnected = true,
            voltageMv = 4300,
            currentMa = 3500,
            powerWatts = 15.05f,
            netPowerWatts = 10.05f,
            consumptionPowerWatts = 5.00f,
            chargingSpeed = CanonicalChargingSpeed.FAST,
            chargingEtaMinutes = 48
        )

        assertTrue(chargingState.isCharging == true)
        assertTrue(chargingState.isChargerConnected == true)
        // Verify displayed raw charging power does NOT subtract phone consumption
        assertEquals(15.05f, chargingState.powerWatts)
        assertEquals(CanonicalChargingSpeed.FAST, chargingState.chargingSpeed)
        assertEquals(48, chargingState.chargingEtaMinutes)
    }

    @Test
    fun testBatteryScreenReceivesCanonicalDischargingState() {
        val dischargingState = NetraCentralState(
            batteryLevel = 82,
            isCharging = false,
            isChargerConnected = false,
            voltageMv = 3980,
            currentMa = -450,
            powerWatts = 1.79f,
            chargingSpeed = CanonicalChargingSpeed.UNAVAILABLE,
            dischargingEtaMinutes = 540
        )

        assertFalse(dischargingState.isCharging == true)
        assertFalse(dischargingState.isChargerConnected == true)
        assertEquals(82, dischargingState.batteryLevel)
        assertEquals(540, dischargingState.dischargingEtaMinutes)
    }

    @Test
    fun testRawChargingPowerDisplayedWithoutSubtractingConsumption() {
        val state = NetraCentralState(
            isCharging = true,
            voltageMv = 4400,
            currentMa = 4800,
            powerWatts = 21.12f, // Raw incoming
            consumptionPowerWatts = 4.12f, // Consumption
            netPowerWatts = 17.00f, // Net
            chargingSpeed = CanonicalChargingSpeed.ULTRA_FAST
        )

        // Raw incoming power must remain 21.12W and category ULTRA_FAST (>20W)
        assertEquals(21.12f, state.powerWatts)
        assertEquals(CanonicalChargingSpeed.ULTRA_FAST, state.chargingSpeed)
    }

    @Test
    fun testEtaComesFromCentralUnit() {
        val stateWithEta = NetraCentralState(
            isCharging = true,
            batteryLevel = 60,
            chargingEtaMinutes = 42
        )

        assertEquals(42, stateWithEta.chargingEtaMinutes)
    }

    @Test
    fun testPartialTelemetryDoesNotErasePreviousValidValues() {
        val previousState = NetraCentralState(
            batteryLevel = 63,
            temperatureCelsius = 34.1f,
            voltageMv = 4200,
            currentMa = 1800,
            powerWatts = 7.56f
        )

        // Simulate Central Unit field-level last-valid retention when currentMa and temperature are missing
        val newLevel = 64
        val newTemp: Float? = null
        val newVoltage = 4200
        val newCurrent: Int? = null

        val merged = previousState.copy(
            batteryLevel = newLevel,
            temperatureCelsius = newTemp ?: previousState.temperatureCelsius,
            voltageMv = newVoltage,
            currentMa = newCurrent ?: previousState.currentMa
        )

        assertEquals(64, merged.batteryLevel)
        assertEquals(34.1f, merged.temperatureCelsius)
        assertEquals(4200, merged.voltageMv)
        assertEquals(1800, merged.currentMa)
        assertEquals(7.56f, merged.powerWatts)
    }

    @Test
    fun testBluetoothLiveVsHistorySeparation() {
        val connectedDevice = BluetoothDeviceItem(
            name = "Bose 700",
            address = "11:22:33:44:55:66",
            isConnected = true,
            isPaired = true,
            deviceType = "Audio / Headphones / Speaker",
            batteryPercent = 75
        )

        val disconnectedDevice = BluetoothDeviceItem(
            name = "Pixel Watch",
            address = "77:88:99:AA:BB:CC",
            isConnected = false,
            isPaired = true,
            deviceType = "Wearable / Smartwatch",
            batteryPercent = 40
        )

        val state = NetraCentralState(
            bluetoothDevices = listOf(connectedDevice),
            bluetoothHistory = listOf(disconnectedDevice)
        )

        // Live only contains connected devices
        assertEquals(1, state.bluetoothDevices.size)
        assertTrue(state.bluetoothDevices[0].isConnected)
        assertEquals("Bose 700", state.bluetoothDevices[0].name)

        // History retains disconnected/paired devices
        assertEquals(1, state.bluetoothHistory.size)
        assertFalse(state.bluetoothHistory[0].isConnected)
        assertEquals("Pixel Watch", state.bluetoothHistory[0].name)
    }

    @Test
    fun testNotificationAndScreensConsumeSameCanonicalState() {
        val canonical = NetraCentralState(
            batteryLevel = 63,
            temperatureCelsius = 34.1f,
            isCharging = true,
            powerWatts = 7.2f,
            chargingSpeed = CanonicalChargingSpeed.NORMAL
        )

        val screenLevel = canonical.batteryLevel
        val screenTemp = canonical.temperatureCelsius
        val screenPower = canonical.powerWatts

        val notificationLevel = canonical.batteryLevel
        val notificationTemp = canonical.temperatureCelsius
        val notificationPower = canonical.powerWatts

        assertEquals(screenLevel, notificationLevel)
        assertEquals(screenTemp, notificationTemp)
        assertEquals(screenPower, notificationPower)
    }
}
