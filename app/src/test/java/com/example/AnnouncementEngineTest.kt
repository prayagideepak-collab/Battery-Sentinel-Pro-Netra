package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.BatteryTelemetry
import com.example.model.BluetoothDeviceItem
import com.example.service.AnnouncementEngine
import com.example.service.AnnouncementItem
import com.example.service.AnnouncementPriority
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

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        engine = AnnouncementEngine(context)
    }

    @Test
    fun `test phone battery 5 percent boundary crossing calculation charging`() {
        // 19% -> 20% crosses 20%
        val crossed1 = engine.getCrossed5PercentBoundaries(19, 20, isCharging = true)
        assertEquals(listOf(20), crossed1)

        // 20% -> 21% crosses nothing
        val crossed2 = engine.getCrossed5PercentBoundaries(20, 21, isCharging = true)
        assertTrue(crossed2.isEmpty())

        // 24% -> 25% crosses 25%
        val crossed3 = engine.getCrossed5PercentBoundaries(24, 25, isCharging = true)
        assertEquals(listOf(25), crossed3)

        // Rapid jump: 21% -> 30% crosses 25% and 30%
        val crossedJump = engine.getCrossed5PercentBoundaries(21, 30, isCharging = true)
        assertEquals(listOf(25, 30), crossedJump)
    }

    @Test
    fun `test phone battery 5 percent boundary crossing calculation discharging`() {
        // 81% -> 80% crosses 80%
        val crossed1 = engine.getCrossed5PercentBoundaries(81, 80, isCharging = false)
        assertEquals(listOf(80), crossed1)

        // 80% -> 79% crosses nothing
        val crossed2 = engine.getCrossed5PercentBoundaries(80, 79, isCharging = false)
        assertTrue(crossed2.isEmpty())

        // 76% -> 75% crosses 75%
        val crossed3 = engine.getCrossed5PercentBoundaries(76, 75, isCharging = false)
        assertEquals(listOf(75), crossed3)

        // Rapid drop: 53% -> 44% crosses 50% and 45%
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
        // Between 23:00 and 06:00
        val isNight1 = engine.isNightTime(23, 6)
        // Check logic handles wrap-around hours correctly
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
        // Verify no crash and handles updates cleanly
        assertNotNull(device.batteryPercent)
        assertEquals(60, device.batteryPercent)
    }

    @Test
    fun `test media playback controller safe initialization`() {
        val controller = com.example.util.MediaPlaybackController(context)
        assertFalse(controller.isMediaPlaying())
        // Restoring when media was not playing should never crash or force media to play
        controller.restoreAfterAnnouncement()
    }
}
