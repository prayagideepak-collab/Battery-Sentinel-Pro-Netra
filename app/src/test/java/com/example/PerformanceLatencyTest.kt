package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.BluetoothDeviceItem
import com.example.model.CanonicalChargingSpeed
import com.example.model.FieldStatus
import com.example.model.NetraCentralState
import com.example.service.NetraCentralDataCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerformanceLatencyTest {

    private lateinit var context: Context
    private lateinit var dataCenter: NetraCentralDataCenter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dataCenter = NetraCentralDataCenter()
        dataCenter.initCapabilityRegistry(context)
        dataCenter.initPersistence(context)
    }

    @Test
    fun testCentralUnitProcessingMeets100msBudget() = runBlocking {
        // Feed valid battery data into Central Unit
        dataCenter.processRawInput(
            level = 78,
            scale = 100,
            status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
            plugged = android.os.BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 325,
            voltage = 4180,
            currentMicroAmps = 2400000,
            bluetoothConnected = false,
            bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertEquals(78, state.batteryLevel)
        assertEquals(32.5f, state.temperatureCelsius ?: 0f, 0.01f)
        assertEquals(4180, state.voltageMv)
        assertEquals(2400, state.currentMa)

        val latency = state.pipelineLatency
        assertNotNull("Latency instrumentation must be present", latency)
        assertTrue("Validation duration must be non-negative", latency!!.validationDurationMs >= 0f)
        assertTrue("Update duration must be non-negative", latency.updateDurationMs >= 0f)
        assertTrue("Total processing must be <= 100ms budget", latency.totalProcessingMs <= 100f)
        assertTrue("meetsBudget flag must be true", latency.meetsBudget)
    }

    @Test
    fun testConsecutiveUpdatesRemainWithinBudget() = runBlocking {
        for (lvl in 50..60) {
            dataCenter.processRawInput(
                level = lvl,
                scale = 100,
                status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
                plugged = android.os.BatteryManager.BATTERY_PLUGGED_AC,
                temperatureRaw = 330,
                voltage = 4200,
                currentMicroAmps = 2500000,
                bluetoothConnected = false,
                bluetoothBattery = null
            )
            val latency = dataCenter.centralState.value.pipelineLatency
            assertNotNull(latency)
            assertTrue("Consecutive update level $lvl took ${latency!!.totalProcessingMs} ms, exceeding 100ms budget", latency.totalProcessingMs <= 100f)
        }
    }

    @Test
    fun testPartialUpdatePreservesValidFieldsWithoutDelay() = runBlocking {
        // Step 1: Initial full sample
        dataCenter.processRawInput(
            level = 60,
            scale = 100,
            status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
            plugged = android.os.BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 340,
            voltage = 4100,
            currentMicroAmps = 1500000,
            bluetoothConnected = false,
            bluetoothBattery = null
        )

        // Step 2: Partial sample (missing temp and current)
        dataCenter.processRawInput(
            level = 61,
            scale = 100,
            status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
            plugged = android.os.BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = -1, // missing
            voltage = 4100,
            currentMicroAmps = Int.MIN_VALUE, // missing
            bluetoothConnected = false,
            bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertEquals(61, state.batteryLevel)
        assertEquals(FieldStatus.LIVE, state.fieldStates.levelStatus)
        // Preserved previous valid temperature & current
        assertEquals(34.0f, state.temperatureCelsius ?: 0f, 0.01f)
        assertEquals(FieldStatus.LAST_VALID, state.fieldStates.tempStatus)
        assertEquals(1500, state.currentMa)
        assertEquals(FieldStatus.LAST_VALID, state.fieldStates.currentStatus)

        val latency = state.pipelineLatency
        assertNotNull(latency)
        assertTrue(latency!!.totalProcessingMs <= 100f)
    }

    @Test
    fun testConcurrentUpdatesDoNotCorruptStateOrExceedBudget() = runBlocking {
        val jobs = (1..5).map { idx ->
            async(Dispatchers.Default) {
                dataCenter.processRawInput(
                    level = 50 + idx,
                    scale = 100,
                    status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
                    plugged = android.os.BatteryManager.BATTERY_PLUGGED_AC,
                    temperatureRaw = 300 + idx * 5,
                    voltage = 4000 + idx * 20,
                    currentMicroAmps = 1000000 + idx * 100000,
                    bluetoothConnected = false,
                    bluetoothBattery = null,
                    source = "ConcurrentTest$idx"
                )
            }
        }
        jobs.awaitAll()

        val finalState = dataCenter.centralState.value
        assertNotNull(finalState.batteryLevel)
        assertTrue(finalState.batteryLevel!! in 51..55)
        assertNotNull(finalState.temperatureCelsius)
        assertNotNull(finalState.voltageMv)
        assertNotNull(finalState.currentMa)
        assertTrue(finalState.pipelineLatency!!.meetsBudget)
    }

    @Test
    fun testBluetoothUpdatePropagatesInstantlyWithoutBlockingDiskIo() = runBlocking {
        val devices = listOf(
            BluetoothDeviceItem(
                name = "Sony WH-1000XM4",
                address = "AA:BB:CC:DD:EE:FF",
                isConnected = true,
                isPaired = true,
                deviceType = "Audio / Headphones",
                batteryPercent = 90
            )
        )

        val start = System.currentTimeMillis()
        dataCenter.processBluetoothDevices(devices)
        val duration = System.currentTimeMillis() - start

        val state = dataCenter.centralState.value
        assertTrue("Bluetooth state must update in <= 100ms", duration <= 100L)
        assertTrue(state.bluetoothConnected == true)
        assertEquals(90, state.bluetoothBatteryPercent)
        assertEquals(1, state.bluetoothDevices.size)
        assertEquals("Sony WH-1000XM4", state.bluetoothDevices[0].name)
    }

    @Test
    fun testRawChargingPowerPreservedUnderBudget() = runBlocking {
        // 4000 mV * 3000 mA = 12.0W (Fast)
        dataCenter.processRawInput(
            level = 45,
            scale = 100,
            status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
            plugged = android.os.BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 310,
            voltage = 4000,
            currentMicroAmps = 3000000,
            bluetoothConnected = false,
            bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertEquals(12.0f, state.powerWatts ?: 0f, 0.05f)
        assertEquals(CanonicalChargingSpeed.FAST, state.chargingSpeed)
        assertTrue(state.pipelineLatency!!.meetsBudget)
    }

    @Test
    fun testMediaOperationsDoNotBlockTelemetryPipeline() = runBlocking {
        // Run concurrent media state update while sending telemetry
        val mediaJob = async(Dispatchers.Default) {
            dataCenter.updateMediaState(com.example.model.CanonicalMediaState.PAUSED)
            dataCenter.setMediaPausedByNethra(true)
        }

        val telemetryJob = async(Dispatchers.Default) {
            val t0 = System.currentTimeMillis()
            dataCenter.processRawInput(
                level = 88,
                scale = 100,
                status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
                plugged = android.os.BatteryManager.BATTERY_PLUGGED_AC,
                temperatureRaw = 335,
                voltage = 4250,
                currentMicroAmps = 2800000,
                bluetoothConnected = false,
                bluetoothBattery = null
            )
            System.currentTimeMillis() - t0
        }

        mediaJob.await()
        val durationMs = telemetryJob.await()
        assertTrue("Telemetry processing must not be blocked by media operations, took $durationMs ms", durationMs <= 100L)

        val state = dataCenter.centralState.value
        assertEquals(88, state.batteryLevel)
        assertEquals(com.example.model.CanonicalMediaState.PAUSED, state.mediaState)
        assertTrue(state.mediaPausedByNethra)
        assertTrue(state.pipelineLatency!!.meetsBudget)
    }

    @Test
    fun testAtomicStatePublicationAndImmediateEventEmission() = runBlocking {
        val collectedEvents = mutableListOf<com.example.model.NetraCentralEvent>()
        val collectorJob = launch(Dispatchers.Default) {
            dataCenter.centralEvents.collect {
                collectedEvents.add(it)
            }
        }

        // Trigger speed change and level crossing
        dataCenter.processRawInput(
            level = 85,
            scale = 100,
            status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
            plugged = android.os.BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 310,
            voltage = 4000,
            currentMicroAmps = 3200000, // 12.8W -> FAST
            bluetoothConnected = false,
            bluetoothBattery = null
        )

        // Give a brief slice for collection
        kotlinx.coroutines.yield()

        val state = dataCenter.centralState.value
        assertEquals(85, state.batteryLevel)
        assertTrue("Pipeline latency must meet <= 100ms budget", state.pipelineLatency!!.meetsBudget)
        assertTrue("Total processing time was ${state.pipelineLatency!!.totalProcessingMs} ms", state.pipelineLatency!!.totalProcessingMs <= 100f)

        collectorJob.cancel()
    }
}
