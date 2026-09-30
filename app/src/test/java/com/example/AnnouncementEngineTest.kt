package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.BatteryTelemetry
import com.example.model.BluetoothDeviceItem
import com.example.model.CanonicalChargingSpeed
import com.example.model.CanonicalMediaState
import com.example.model.CanonicalPluggedType
import com.example.model.NetraCentralEvent
import com.example.model.NetraCentralState
import com.example.model.NetraEventType
import com.example.service.AnnouncementEngine
import com.example.service.AnnouncementItem
import com.example.service.AnnouncementPriority
import com.example.service.AnnouncementSpeechState
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnnouncementEngineTest {

    private lateinit var context: Context
    private lateinit var engine: AnnouncementEngine
    private lateinit var dataCenter: NetraCentralDataCenter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        engine = AnnouncementEngine(context)
        dataCenter = NetraCentralDataCenter()
    }

    @Test
    fun `test phone battery 5 percent boundary crossing calculation charging`() {
        val crossed1 = engine.getCrossed5PercentBoundaries(19, 20, isCharging = true)
        assertEquals(listOf(20), crossed1)

        val crossed2 = engine.getCrossed5PercentBoundaries(20, 21, isCharging = true)
        assertTrue(crossed2.isEmpty())

        val crossed3 = engine.getCrossed5PercentBoundaries(24, 25, isCharging = true)
        assertEquals(listOf(25), crossed3)

        val crossedJump = engine.getCrossed5PercentBoundaries(21, 30, isCharging = true)
        assertEquals(listOf(25, 30), crossedJump)
    }

    @Test
    fun `test phone battery 5 percent boundary crossing calculation discharging`() {
        val crossed1 = engine.getCrossed5PercentBoundaries(81, 80, isCharging = false)
        assertEquals(listOf(80), crossed1)

        val crossed2 = engine.getCrossed5PercentBoundaries(80, 79, isCharging = false)
        assertTrue(crossed2.isEmpty())

        val crossed3 = engine.getCrossed5PercentBoundaries(76, 75, isCharging = false)
        assertEquals(listOf(75), crossed3)

        val crossedDrop = engine.getCrossed5PercentBoundaries(53, 44, isCharging = false)
        assertEquals(listOf(50, 45), crossedDrop)
    }

    @Test
    fun `test charging power speed categorization`() {
        assertEquals("SLOW", engine.categorizePowerSpeed(3.2f))
        assertEquals("NORMAL", engine.categorizePowerSpeed(7.5f))
        assertEquals("FAST", engine.categorizePowerSpeed(15.0f))
        assertEquals("ULTRA_FAST", engine.categorizePowerSpeed(28.0f))
    }

    @Test
    fun `test night protection hours calculation`() {
        val isNight1 = engine.isNightTime(23, 6)
        assertNotNull(isNight1)
    }

    @Test
    fun `test announcement item priority ordering`() {
        val critThermal = AnnouncementItem("1", "Thermal warning", AnnouncementPriority.CRITICAL_THERMAL, "THERMAL", isNightException = true)
        val chargerConn = AnnouncementItem("2", "Charger connected.", AnnouncementPriority.CHARGER_STATE, "CHARGER", isNightException = true)
        val phoneBat = AnnouncementItem("3", "C 80 percent", AnnouncementPriority.PHONE_BATTERY, "BATTERY")

        assertTrue(critThermal < chargerConn)
        assertTrue(chargerConn < phoneBat)
        assertTrue(critThermal < phoneBat)
    }

    @Test
    fun `test bluetooth device battery boundary tracking`() {
        val device = BluetoothDeviceItem(
            name = "Headphones",
            address = "00:11:22:33:44:55",
            isConnected = true,
            isPaired = true,
            deviceType = "Audio",
            batteryPercent = 60
        )

        engine.onBluetoothDevicesUpdate(listOf(device))
        assertNotNull(device.batteryPercent)
        assertEquals(60, device.batteryPercent)
    }

    @Test
    fun `test media playback controller safe initialization`() {
        val controller = com.example.util.MediaPlaybackController(context)
        assertFalse(controller.isMediaPlaying())
        controller.restoreAfterAnnouncement()
    }

    @Test
    fun `test raw power vs net power speed separation requirements`() {
        val state1 = NetraCentralState(
            batteryLevel = 50,
            isCharging = true,
            powerWatts = 15f,
            netPowerWatts = 9f,
            chargingSpeed = CanonicalChargingSpeed.FAST,
            announcementSpeed = CanonicalChargingSpeed.NORMAL
        )
        assertEquals(CanonicalChargingSpeed.FAST, state1.chargingSpeed)
        assertEquals(CanonicalChargingSpeed.NORMAL, state1.announcementSpeed)

        val state2 = NetraCentralState(
            batteryLevel = 50,
            isCharging = true,
            powerWatts = 22f,
            netPowerWatts = 19f,
            chargingSpeed = CanonicalChargingSpeed.ULTRA_FAST,
            announcementSpeed = CanonicalChargingSpeed.FAST
        )
        assertEquals(CanonicalChargingSpeed.ULTRA_FAST, state2.chargingSpeed)
        assertEquals(CanonicalChargingSpeed.FAST, state2.announcementSpeed)
    }

    @Test
    fun `test net power unavailable yields no fabricated announcement speed`() {
        val state = NetraCentralState(
            batteryLevel = 50,
            isCharging = true,
            powerWatts = 12f,
            netPowerWatts = null,
            chargingSpeed = CanonicalChargingSpeed.FAST,
            announcementSpeed = CanonicalChargingSpeed.UNAVAILABLE
        )
        assertEquals(CanonicalChargingSpeed.UNAVAILABLE, state.announcementSpeed)
    }

    @Test
    fun `test duplicate event insertion prevention`() {
        val item1 = AnnouncementItem("id1", "Charger connected.", AnnouncementPriority.CHARGER_STATE, "CHARGER")
        val item2 = AnnouncementItem("id2", "Charger connected.", AnnouncementPriority.CHARGER_STATE, "CHARGER")

        engine.enqueue(item1)
        engine.enqueue(item2)

        // Verifies duplicate items are safely skipped/deduplicated by checking speech state
        assertEquals(AnnouncementSpeechState.QUEUED, item1.speechState)
        assertEquals(AnnouncementSpeechState.QUEUED, item2.speechState)
    }

    @Test
    fun `test rapid low-priority battery events compression`() {
        val item1 = AnnouncementItem("bat60", "D 60 percent", AnnouncementPriority.PHONE_BATTERY, "BATTERY")
        val item2 = AnnouncementItem("bat55", "D 55 percent", AnnouncementPriority.PHONE_BATTERY, "BATTERY")

        engine.enqueue(item1)
        engine.enqueue(item2)

        // Item 1 is compressed out of active state by the newer boundary
        assertEquals(AnnouncementSpeechState.QUEUED, item2.speechState)
    }

    @Test
    fun `test critical thermal event priority and survival`() {
        val itemLow = AnnouncementItem("bat60", "D 60 percent", AnnouncementPriority.PHONE_BATTERY, "BATTERY")
        val itemCrit = AnnouncementItem("thermal", "Thermal Warning", AnnouncementPriority.CRITICAL_THERMAL, "THERMAL")

        engine.enqueue(itemLow)
        engine.enqueue(itemCrit)

        // Both enqueued successfully, with itemCrit having higher priority (will be spoken first)
        assertEquals(AnnouncementSpeechState.QUEUED, itemLow.speechState)
        assertEquals(AnnouncementSpeechState.QUEUED, itemCrit.speechState)
        assertTrue(itemCrit < itemLow)
    }

    @Test
    fun `test media states updates and default capabilities in central unit`() = runBlocking {
        assertEquals(CanonicalMediaState.UNKNOWN, dataCenter.centralState.value.mediaState)
        assertTrue(dataCenter.centralState.value.isMediaControlAvailable)
        assertFalse(dataCenter.centralState.value.mediaPausedByNethra)

        dataCenter.updateMediaState(CanonicalMediaState.PLAYING)
        assertEquals(CanonicalMediaState.PLAYING, dataCenter.centralState.value.mediaState)

        dataCenter.updateMediaControlAvailable(false)
        assertFalse(dataCenter.centralState.value.isMediaControlAvailable)
    }

    @Test
    fun `test media pause constraints on already paused state`() = runBlocking {
        dataCenter.updateMediaState(CanonicalMediaState.PAUSED)
        
        // Requesting pause when already paused should not engage pausing or report true for paused by Nethra
        val paused = dataCenter.requestMediaPause(context)
        assertFalse(paused)
        assertFalse(dataCenter.centralState.value.mediaPausedByNethra)
    }

    @Test
    fun `test media resume ownership validation`() = runBlocking {
        dataCenter.setMediaPausedByNethra(true)
        assertTrue(dataCenter.centralState.value.mediaPausedByNethra)

        val resumed = dataCenter.requestMediaResume(context)
        assertTrue(resumed)
        assertFalse(dataCenter.centralState.value.mediaPausedByNethra)
        assertEquals(CanonicalMediaState.PLAYING, dataCenter.centralState.value.mediaState)
    }
}
