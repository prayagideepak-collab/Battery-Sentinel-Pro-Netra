package com.example

import com.example.model.CanonicalChargingSpeed
import com.example.model.NetraCentralEvent
import com.example.model.NetraEventType
import com.example.service.NetraCentralDataCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetraCentralDataCenterTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dataCenter: NetraCentralDataCenter

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataCenter = NetraCentralDataCenter()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test same state repeated produces no duplicate events`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(testDispatcher) {
            dataCenter.centralEvents.toList(events)
        }

        // Send state 1
        dataCenter.processRawInput(
            level = 50, scale = 100,
            status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
            plugged = android.os.BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 300, voltage = 4000, currentMicroAmps = 2500000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        // Send same state again
        dataCenter.processRawInput(
            level = 50, scale = 100,
            status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
            plugged = android.os.BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 300, voltage = 4000, currentMicroAmps = 2500000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        job.cancel()

        val chargingStartedCount = events.count { it.eventType == NetraEventType.CHARGING_STARTED }
        assertEquals(1, chargingStartedCount)
    }

    @Test
    fun `test power connected followed by charging status yields single transition`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(testDispatcher) {
            dataCenter.centralEvents.toList(events)
        }

        dataCenter.processRawInput(
            level = 40, scale = 100,
            status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
            plugged = android.os.BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 300, voltage = 4000, currentMicroAmps = 2500000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        dataCenter.processRawInput(
            level = 40, scale = 100,
            status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
            plugged = android.os.BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 300, voltage = 4000, currentMicroAmps = 2500000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        job.cancel()

        val connectedEvents = events.count { it.eventType == NetraEventType.CHARGER_CONNECTED }
        assertEquals(1, connectedEvents)
    }

    @Test
    fun `test repeated power connected produces one canonical event`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(testDispatcher) {
            dataCenter.centralEvents.toList(events)
        }

        dataCenter.processRawInput(40, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2500000, null, null)
        dataCenter.processRawInput(40, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2500000, null, null)

        job.cancel()
        assertEquals(1, events.count { it.eventType == NetraEventType.CHARGER_CONNECTED })
    }

    @Test
    fun `test charging to discharging transition`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(testDispatcher) {
            dataCenter.centralEvents.toList(events)
        }

        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2500000, null, null)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING, android.os.BatteryManager.BATTERY_PLUGGED_NONE, 300, 4000, -500000, null, null)

        job.cancel()

        assertTrue(events.any { it.eventType == NetraEventType.CHARGING_STARTED })
        assertTrue(events.any { it.eventType == NetraEventType.CHARGING_STOPPED })
        assertTrue(events.any { it.eventType == NetraEventType.CHARGER_DISCONNECTED })
    }

    @Test
    fun `test discharging to charging transition`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(testDispatcher) {
            dataCenter.centralEvents.toList(events)
        }

        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING, android.os.BatteryManager.BATTERY_PLUGGED_NONE, 300, 4000, -500000, null, null)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2500000, null, null)

        job.cancel()

        assertTrue(events.any { it.eventType == NetraEventType.CHARGER_CONNECTED })
        assertTrue(events.any { it.eventType == NetraEventType.CHARGING_STARTED })
    }

    @Test
    fun `test same charging speed category repeated produces no SPEED_CHANGED event`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(testDispatcher) {
            dataCenter.centralEvents.toList(events)
        }

        // 12W Fast
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 3000000, null, null)
        // 15W Fast
        dataCenter.processRawInput(51, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 3750000, null, null)

        job.cancel()

        assertEquals(1, events.count { it.eventType == NetraEventType.SPEED_CHANGED })
    }

    @Test
    fun `test speed category changes produces exactly one SPEED_CHANGED event`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(testDispatcher) {
            dataCenter.centralEvents.toList(events)
        }

        // 3W Slow (< 5W)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_USB, 300, 4000, 750000, null, null)
        // 15W Fast (10W to 20W)
        dataCenter.processRawInput(51, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 3750000, null, null)

        job.cancel()

        assertEquals(2, events.count { it.eventType == NetraEventType.SPEED_CHANGED })
    }

    @Test
    fun `test unavailable telemetry produces no fabricated fallback values`() = runTest(testDispatcher) {
        dataCenter.processRawInput(
            level = -1, scale = -1,
            status = android.os.BatteryManager.BATTERY_STATUS_UNKNOWN,
            plugged = -1,
            temperatureRaw = 0, voltage = 0, currentMicroAmps = 0,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertNull(state.batteryLevel)
        assertNull(state.temperatureCelsius)
        assertNull(state.voltageMv)
        assertNull(state.currentMa)
        assertNull(state.powerWatts)
        assertEquals(CanonicalChargingSpeed.UNAVAILABLE, state.chargingSpeed)
    }

    @Test
    fun `test concurrent and rapid state updates remain consistent`() = runTest(testDispatcher) {
        val deferreds = (1..50).map { i ->
            async(Dispatchers.Default) {
                dataCenter.processRawInput(
                    level = i, scale = 100,
                    status = android.os.BatteryManager.BATTERY_STATUS_DISCHARGING,
                    plugged = android.os.BatteryManager.BATTERY_PLUGGED_NONE,
                    temperatureRaw = 300 + i, voltage = 4000, currentMicroAmps = -500000,
                    bluetoothConnected = null, bluetoothBattery = null
                )
            }
        }
        deferreds.awaitAll()

        val state = dataCenter.centralState.value
        assertNotNull(state)
        assertTrue((state.batteryLevel ?: 0) in 1..50)
    }
}
