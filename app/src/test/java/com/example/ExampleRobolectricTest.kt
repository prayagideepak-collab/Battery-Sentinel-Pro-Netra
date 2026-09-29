package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.BatteryDegradationPredictor
import com.example.ai.FailureRiskLevel
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import com.example.model.BatteryTelemetry
import com.example.model.DotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Netra Sentinel Pro", appName)
    }

    @Test
    fun `test battery telemetry modeling and safety thresholds`() {
        val telemetry = BatteryTelemetry(
            level = 82,
            isCharging = true,
            temperature = 36.5f,
            voltageMv = 4200,
            currentMa = 1500,
            powerWatts = 6.3f,
            serviceDotState = DotState.CONNECTED
        )

        assertEquals(82, telemetry.level)
        assertEquals(true, telemetry.isCharging)
        assertEquals(3.5f, telemetry.distanceTo40C, 0.01f)
        assertEquals(false, telemetry.isCriticalOverheat)
    }

    @Test
    fun `test degradation and failure risk prediction heuristics`() {
        val records = listOf(
            BatteryRecord(level = 95, temperature = 43.5f, voltageMv = 4380, currentMa = 2200, powerWatts = 9.6f, isCharging = true, pluggedType = "AC", healthStatus = "GOOD"),
            BatteryRecord(level = 90, temperature = 42.0f, voltageMv = 4320, currentMa = 1800, powerWatts = 7.7f, isCharging = true, pluggedType = "AC", healthStatus = "GOOD"),
            BatteryRecord(level = 10, temperature = 35.0f, voltageMv = 3650, currentMa = -800, powerWatts = 2.9f, isCharging = false, pluggedType = "BATTERY", healthStatus = "GOOD")
        )

        val sessions = listOf(
            ChargingSession(startTime = 1000L, endTime = 2000L, startLevel = 20, endLevel = 100, peakTemperature = 43.5f, avgPowerWatts = 8.5f, chargerType = "AC", durationMinutes = 55)
        )

        val report = BatteryDegradationPredictor.analyzeDegradationAndFailureRisk(records, sessions)
        assertNotNull(report)
        assertTrue(report.riskPercent > 0)
        assertTrue(report.estimatedCapacityHealthPercent in 50..100)
        assertNotNull(report.primaryRiskFactor)
    }
}
