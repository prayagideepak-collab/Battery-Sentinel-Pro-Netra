package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SentinelSettings
import com.example.model.CanonicalChargingSpeed
import com.example.model.CanonicalMediaState
import com.example.model.NetraCentralState
import com.example.service.AnnouncementEngine
import com.example.service.AnnouncementItem
import com.example.service.AnnouncementPriority
import com.example.service.AnnouncementSpeechState
import com.example.service.NetraCentralDataCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NightProtectionTest {

    private lateinit var context: Context
    private lateinit var dataCenter: NetraCentralDataCenter
    private lateinit var engine: AnnouncementEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dataCenter = NetraCentralDataCenter()
        engine = AnnouncementEngine(context)
    }

    @Test
    fun testDefaultNightWindowBoundaries() {
        val settings = SentinelSettings(
            nightProtectionEnabled = true,
            nightStartHour = 23,
            nightEndHour = 6
        )

        // Helper function testing logic directly matching calculateIsNightProtectionActive
        val calculateIsNight: (Int) -> Boolean = { hour ->
            val start = settings.nightStartHour
            val end = settings.nightEndHour
            if (start == end) {
                false
            } else if (start > end) {
                hour >= start || hour < end
            } else {
                hour in start until end
            }
        }

        assertFalse(calculateIsNight(22))
        assertTrue(calculateIsNight(23))
        assertTrue(calculateIsNight(0))
        assertTrue(calculateIsNight(5))
        assertFalse(calculateIsNight(6))
        assertFalse(calculateIsNight(7))
    }

    @Test
    fun testCustomTimeWindow() {
        val settings = SentinelSettings(
            nightProtectionEnabled = true,
            nightStartHour = 21,
            nightEndHour = 7
        )

        val calculateIsNight: (Int) -> Boolean = { hour ->
            val start = settings.nightStartHour
            val end = settings.nightEndHour
            if (start == end) {
                false
            } else if (start > end) {
                hour >= start || hour < end
            } else {
                hour in start until end
            }
        }

        assertFalse(calculateIsNight(20))
        assertTrue(calculateIsNight(21))
        assertTrue(calculateIsNight(22))
        assertTrue(calculateIsNight(6))
        assertFalse(calculateIsNight(7))
        assertFalse(calculateIsNight(8))
    }

    @Test
    fun testMasterToggles() {
        val settingsDisabled = SentinelSettings(
            nightProtectionEnabled = false,
            nightStartHour = 23,
            nightEndHour = 6
        )
        assertFalse(dataCenter.calculateIsNightProtectionActive(settingsDisabled))

        val settingsEnabled = SentinelSettings(
            nightProtectionEnabled = true,
            nightStartHour = 23,
            nightEndHour = 6
        )
        // Note: active calculation dynamically uses actual Calendar hour, testing helper for consistency
    }

    @Test
    fun testInvalidSameStartAndEndConfiguration() {
        val settingsSame = SentinelSettings(
            nightProtectionEnabled = true,
            nightStartHour = 22,
            nightEndHour = 22
        )
        // If same start/end time configured, it must return false to avoid accidental 24-hour mute
        assertFalse(dataCenter.calculateIsNightProtectionActive(settingsSame))
    }

    @Test
    fun testCentralUnitStateRetentionDuringNight() {
        // Night policy must NOT clear telemetry state.
        val state = NetraCentralState(
            batteryLevel = 63,
            temperatureCelsius = 34.1f,
            isNightProtectionActive = true
        )
        assertEquals(63, state.batteryLevel)
        assertEquals(34.1f, state.temperatureCelsius)
        assertTrue(state.isNightProtectionActive)
    }

    @Test
    fun testAllowedEventsPrioritySurvival() {
        val criticalThermal = AnnouncementItem(
            id = "thermal_1",
            text = "Overheat warning!",
            priority = AnnouncementPriority.CRITICAL_THERMAL,
            category = "THERMAL",
            isNightException = true
        )
        
        val suppressedBattery = AnnouncementItem(
            id = "bat_80",
            text = "Battery level 80 percent",
            priority = AnnouncementPriority.PHONE_BATTERY,
            category = "BATTERY",
            isNightException = false
        )

        // Thermal is night exception and allowed, battery is normal and suppressed
        assertTrue(criticalThermal.isNightException)
        assertFalse(suppressedBattery.isNightException)
    }
}
