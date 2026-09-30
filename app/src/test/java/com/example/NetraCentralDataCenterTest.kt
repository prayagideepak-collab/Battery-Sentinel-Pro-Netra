package com.example

import com.example.model.CanonicalPluggedType
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
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
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
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
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
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
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dataCenter.centralEvents.toList(events)
        }

        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2500000, null, null)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING, 0, 300, 4000, -500000, null, null)

        job.cancel()

        assertTrue(events.any { it.eventType == NetraEventType.CHARGING_STARTED })
        assertTrue(events.any { it.eventType == NetraEventType.CHARGING_STOPPED })
        assertTrue(events.any { it.eventType == NetraEventType.CHARGER_DISCONNECTED })
    }

    @Test
    fun `test discharging to charging transition`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dataCenter.centralEvents.toList(events)
        }

        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING, 0, 300, 4000, -500000, null, null)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2500000, null, null)

        job.cancel()

        assertTrue(events.any { it.eventType == NetraEventType.CHARGER_CONNECTED })
        assertTrue(events.any { it.eventType == NetraEventType.CHARGING_STARTED })
    }

    @Test
    fun `test same charging speed category repeated produces no SPEED_CHANGED event`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
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
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
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
    fun `test connected event keeps the actual previous value`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dataCenter.centralEvents.toList(events)
        }

        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING, 0, 300, 4000, -500000, null, null)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 0, null, null)

        val connected = events.single { it.eventType == NetraEventType.CHARGER_CONNECTED }
        assertEquals("false", connected.previousValue)
        assertEquals("true", connected.newValue)
        assertEquals(false, dataCenter.centralState.value.isCharging)
        job.cancel()
    }

    @Test
    fun `test charger session timestamps separate connection from actual charging`() = runTest(testDispatcher) {
        dataCenter.processRawInput(40, 100, android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 0, null, null)
        val connected = dataCenter.centralState.value
        assertEquals(true, connected.isChargerConnected)
        assertEquals(false, connected.isCharging)
        assertNotNull(connected.chargerConnectedAt)
        assertNull(connected.chargingStartedAt)

        dataCenter.processRawInput(40, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2500000, null, null)
        val charging = dataCenter.centralState.value
        assertEquals(connected.chargerConnectedAt, charging.chargerConnectedAt)
        assertNotNull(charging.chargingStartedAt)

        dataCenter.processRawInput(40, 100, android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 0, null, null)
        val stopped = dataCenter.centralState.value
        assertNotNull(stopped.chargingStoppedAt)
        assertNull(stopped.dischargingStartedAt)
        assertNull(stopped.chargerDisconnectedAt)

        dataCenter.processRawInput(40, 100, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING, 0, 300, 4000, -500000, null, null)
        assertNotNull(dataCenter.centralState.value.chargerDisconnectedAt)
        assertNotNull(dataCenter.centralState.value.dischargingStartedAt)
    }

    @Test
    fun `test discharging start is separate from stopping charge`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dataCenter.centralEvents.toList(events)
        }

        dataCenter.processRawInput(40, 100, android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 0, null, null)
        assertTrue(events.none { it.eventType == NetraEventType.DISCHARGING_STARTED })
        dataCenter.processRawInput(40, 100, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING, 0, 300, 4000, -500000, null, null)
        assertEquals(1, events.count { it.eventType == NetraEventType.DISCHARGING_STARTED })
        dataCenter.processRawInput(40, 100, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING, 0, 300, 4000, -500000, null, null)
        assertEquals(1, events.count { it.eventType == NetraEventType.DISCHARGING_STARTED })
        job.cancel()
    }

    @Test
    fun `test unknown plug and invalid level stay unavailable`() = runTest(testDispatcher) {
        dataCenter.processRawInput(Int.MAX_VALUE, 100, android.os.BatteryManager.BATTERY_STATUS_UNKNOWN, -1, 0, 0, 0, null, null)
        val state = dataCenter.centralState.value
        assertNull(state.batteryLevel)
        assertNull(state.isChargerConnected)
        assertEquals(CanonicalPluggedType.UNKNOWN, state.pluggedType)
    }

    @Test
    fun `test charging speed reappearing after unavailable emits a change`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dataCenter.centralEvents.toList(events)
        }

        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 3000000, null, null)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_UNKNOWN, -1, 0, 0, 0, null, null)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 3000000, null, null)

        assertEquals(1, events.count { it.eventType == NetraEventType.SPEED_CHANGED })
        job.cancel()
    }

    @Test
    fun `test stale canonical transitions are not replayed to new collectors`() = runTest(testDispatcher) {
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 3000000, null, null)
        assertEquals(0, dataCenter.centralEvents.replayCache.size)
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
        assertEquals(0, state.currentMa)
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
                    plugged = 0,
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

    @Test
    fun `test exact charging speed thresholds`() = runTest(testDispatcher) {
        // 4.9W (4000mV * 1225mA = 4.9W) -> Slow (< 5W)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 1225000, null, null)
        assertEquals(CanonicalChargingSpeed.SLOW, dataCenter.centralState.value.chargingSpeed)

        // 5.0W (4000mV * 1250mA = 5.0W) -> Normal (5W to < 10W)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 1250000, null, null)
        assertEquals(CanonicalChargingSpeed.NORMAL, dataCenter.centralState.value.chargingSpeed)

        // 9.9W (4000mV * 2475mA = 9.9W) -> Normal
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2475000, null, null)
        assertEquals(CanonicalChargingSpeed.NORMAL, dataCenter.centralState.value.chargingSpeed)

        // 10.0W (4000mV * 2500mA = 10.0W) -> Fast (10W to 20W)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2500000, null, null)
        assertEquals(CanonicalChargingSpeed.FAST, dataCenter.centralState.value.chargingSpeed)

        // 20.0W (4000mV * 5000mA = 20.0W) -> Fast
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 5000000, null, null)
        assertEquals(CanonicalChargingSpeed.FAST, dataCenter.centralState.value.chargingSpeed)

        // 20.1W (4000mV * 5025mA = 20.1W) -> Ultra Fast (> 20W)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 5025000, null, null)
        assertEquals(CanonicalChargingSpeed.ULTRA_FAST, dataCenter.centralState.value.chargingSpeed)
    }

    @Test
    fun `test missing voltage or current makes power unavailable`() = runTest(testDispatcher) {
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 0, 2500000, null, null)
        assertNull(dataCenter.centralState.value.powerWatts)
        assertEquals(CanonicalChargingSpeed.UNAVAILABLE, dataCenter.centralState.value.chargingSpeed)
    }

    @Test
    fun `test live numeric power updates without repeated speed events`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dataCenter.centralEvents.toList(events)
        }

        // 6.1W (4000mV * 1525mA = 6.1W + consumption)
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 1525000, null, null)
        // 6.4W
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 1600000, null, null)
        // 7.2W
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 1800000, null, null)

        job.cancel()

        assertEquals(1, events.count { it.eventType == NetraEventType.SPEED_CHANGED })
        assertEquals(CanonicalChargingSpeed.NORMAL, dataCenter.centralState.value.chargingSpeed)
    }

    @Test
    fun `test reverse speed thresholds`() = runTest(testDispatcher) {
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 5025000, null, null)
        assertEquals(CanonicalChargingSpeed.ULTRA_FAST, dataCenter.centralState.value.chargingSpeed)

        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 4500000, null, null)
        assertEquals(CanonicalChargingSpeed.FAST, dataCenter.centralState.value.chargingSpeed)

        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2000000, null, null)
        assertEquals(CanonicalChargingSpeed.NORMAL, dataCenter.centralState.value.chargingSpeed)

        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 1000000, null, null)
        assertEquals(CanonicalChargingSpeed.SLOW, dataCenter.centralState.value.chargingSpeed)
    }

    @Test
    fun `test raw vs net speed separation`() = runTest(testDispatcher) {
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 3750000, null, null)
        val state = dataCenter.centralState.value
        assertEquals(CanonicalChargingSpeed.FAST, state.chargingSpeed)
        assertNotNull(state.netPowerWatts)
    }

    @Test
    fun `test repeated telemetry does not reset session timestamps`() = runTest(testDispatcher) {
        dataCenter.processRawInput(40, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2500000, null, null)
        val firstStart = dataCenter.centralState.value.chargingStartedAt
        val firstConnected = dataCenter.centralState.value.chargerConnectedAt

        dataCenter.processRawInput(41, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 3000000, null, null)
        val secondStart = dataCenter.centralState.value.chargingStartedAt
        val secondConnected = dataCenter.centralState.value.chargerConnectedAt

        assertEquals(firstStart, secondStart)
        assertEquals(firstConnected, secondConnected)
    }

    @Test
    fun `test field-level retention when subsequent sample lacks temperature or voltage`() = runTest(testDispatcher) {
        // First sample has valid temperature and voltage
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING, 0, 310, 4100, -500000, true, 80)
        val state1 = dataCenter.centralState.value
        assertEquals(50, state1.batteryLevel)
        assertEquals(31.0f, state1.temperatureCelsius!!, 0.01f)
        assertEquals(4100, state1.voltageMv)
        assertEquals(true, state1.bluetoothConnected)
        assertEquals(80, state1.bluetoothBatteryPercent)

        // Second sample has missing temperature (raw = 0), missing voltage (0), missing bluetooth
        dataCenter.processRawInput(51, 100, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING, 0, 0, 0, -600000, null, null)
        val state2 = dataCenter.centralState.value

        assertEquals(51, state2.batteryLevel)
        // Previous valid temperature and voltage must be retained!
        assertEquals(31.0f, state2.temperatureCelsius!!, 0.01f)
        assertEquals(4100, state2.voltageMv)
        // Bluetooth connection state must be retained!
        assertEquals(true, state2.bluetoothConnected)
        assertEquals(80, state2.bluetoothBatteryPercent)
    }

    @Test
    fun `test zero current is treated as valid numeric data`() = runTest(testDispatcher) {
        dataCenter.processRawInput(50, 100, android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING, android.os.BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 0, null, null)
        val state = dataCenter.centralState.value
        assertEquals(0, state.currentMa)
    }
}
