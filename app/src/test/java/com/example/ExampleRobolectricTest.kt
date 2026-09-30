package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.BatteryDegradationPredictor
import com.example.ai.FailureRiskLevel
import com.example.ai.OptimalChargingWindowAdvisor
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import com.example.model.BatteryTelemetry
import com.example.model.DotState
import com.example.util.BatteryPdfReportGenerator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            distanceTo40C = 3.5f,
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
        assertNull(report.riskPercent)
        assertNull(report.riskLevel)
        assertNull(report.estimatedCapacityHealthPercent)
        assertNull(report.highVoltageDwellMinutes)
        assertNull(report.thermalStressHours)
        assertNull(report.deepDischargeCount)
        assertEquals(0.8f, report.totalEquivalentCycles, 0.001f)
        assertFalse(report.requiresAlert)
        assertNotNull(report.primaryRiskFactor)
    }

    @Test
    fun `empty battery history has no invented capacity cycles or risk`() {
        val report = BatteryDegradationPredictor.analyzeDegradationAndFailureRisk(emptyList(), emptyList())
        assertNull(report.riskPercent)
        assertNull(report.riskLevel)
        assertNull(report.estimatedCapacityHealthPercent)
        assertEquals(0f, report.totalEquivalentCycles, 0.001f)
        assertFalse(report.requiresAlert)
    }

    @Test
    fun `test time-series optimal charging window advisor`() {
        val records = listOf(
            BatteryRecord(level = 80, temperature = 28.0f, voltageMv = 4100, currentMa = 1500, powerWatts = 6.0f, isCharging = true, pluggedType = "AC", healthStatus = "GOOD")
        )
        val sessions = listOf(
            ChargingSession(startTime = 1000L, endTime = 2000L, startLevel = 30, endLevel = 80, peakTemperature = 32.0f, avgPowerWatts = 7.0f, chargerType = "AC", durationMinutes = 40)
        )

        val suggestion = OptimalChargingWindowAdvisor.analyzeChargingHabitsAndSuggestWindows(records, sessions)
        assertNotNull(suggestion.primaryOptimalWindow)
        assertNotNull(suggestion.discouragedWindow)
        assertEquals("Unavailable", suggestion.primaryOptimalWindow)
        assertEquals("Unavailable", suggestion.discouragedWindow)
        assertEquals("Unavailable", suggestion.predictedLifespanExtension)
        assertTrue(suggestion.hourlyScores.isEmpty())
    }

    @Test
    fun `test daily PDF report generator creation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val records = listOf(
            BatteryRecord(level = 80, temperature = 28.0f, voltageMv = 4100, currentMa = 1500, powerWatts = 6.0f, isCharging = true, pluggedType = "AC", healthStatus = "GOOD")
        )
        val sessions = listOf(
            ChargingSession(startTime = 1000L, endTime = 2000L, startLevel = 30, endLevel = 80, peakTemperature = 32.0f, avgPowerWatts = 7.0f, chargerType = "AC", durationMinutes = 40)
        )
        val report = BatteryDegradationPredictor.analyzeDegradationAndFailureRisk(records, sessions)
        val telemetry = BatteryTelemetry(level = 80, isCharging = true)

        val pdfFile = BatteryPdfReportGenerator.generateDailyReport(context, records, sessions, report, telemetry)
        assertNotNull(pdfFile)
        assertTrue(pdfFile!!.exists())
        assertTrue(pdfFile.length() > 0)
    }

    @Test
    fun `unsupported calibration never claims completion or loads fabricated preferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("netra_calibration_prefs", Context.MODE_PRIVATE).edit()
            .putInt("calibrated_capacity", 4850).putInt("accuracy_score", 99).apply()
        val manager = com.example.ai.BatteryCalibrationManager(context)
        manager.startCalibration(75)
        manager.onTelemetryUpdate(BatteryTelemetry(level = 9, isCharging = false))
        manager.manuallyAdvanceStep()
        assertEquals(com.example.model.CalibrationStep.NOT_STARTED, manager.calibrationState.value.currentStep)
        assertFalse(manager.calibrationState.value.isWizardActive)
        assertEquals(0L, manager.calibrationState.value.lastCalibratedTimestamp)
    }

    @Test
    fun `test dynamic power-saving profile threshold transitions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profileManager = com.example.ai.PowerProfileManager(context)

        profileManager.setPowerProfile(com.example.model.PowerProfileMode.SMART_ADAPTIVE)

        // Normal level (80%) -> Balanced
        profileManager.onTelemetryUpdate(BatteryTelemetry(level = 80, isCharging = false))
        assertEquals(com.example.model.PowerProfileMode.BALANCED, profileManager.profileState.value.activeEffectiveMode)

        // Low level (18%) -> Endurance
        profileManager.onTelemetryUpdate(BatteryTelemetry(level = 18, isCharging = false))
        assertEquals(com.example.model.PowerProfileMode.ENDURANCE, profileManager.profileState.value.activeEffectiveMode)
        assertTrue(profileManager.profileState.value.dynamicSyncThrottled)

        // Critical level (8%) -> Ultra Saver
        profileManager.onTelemetryUpdate(BatteryTelemetry(level = 8, isCharging = false))
        assertEquals(com.example.model.PowerProfileMode.ULTRA_SAVER, profileManager.profileState.value.activeEffectiveMode)

        // Charging -> Performance
        profileManager.onTelemetryUpdate(BatteryTelemetry(level = 8, isCharging = true))
        assertEquals(com.example.model.PowerProfileMode.PERFORMANCE, profileManager.profileState.value.activeEffectiveMode)
    }

    @Test
    fun `test night charging thermal throttling evaluation`() {
        val telemetry = BatteryTelemetry(level = 85, isCharging = true, temperature = 34.0f)
        val status = com.example.ai.NightChargingThrottler.evaluateNightThrottle(
            telemetry = telemetry,
            isFeatureEnabled = true,
            targetWakeHour = 7
        )

        assertNotNull(status)
        assertNotNull(status.statusHeadline)
        assertNotNull(status.detailedAdvice)
    }

    @Test
    fun `test battery telemetry CSV exporter format and generation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val records = listOf(
            BatteryRecord(level = 80, temperature = 28.0f, voltageMv = 4100, currentMa = 1500, powerWatts = 6.0f, isCharging = true, pluggedType = "AC", healthStatus = "GOOD")
        )
        val sessions = listOf(
            ChargingSession(startTime = 1000L, endTime = 2000L, startLevel = 30, endLevel = 80, peakTemperature = 32.0f, avgPowerWatts = 7.0f, chargerType = "AC", durationMinutes = 40)
        )

        val csvFile = com.example.util.BatteryCsvExporter.exportTelemetryToCsv(context, records, sessions)
        assertNotNull(csvFile)
        assertTrue(csvFile!!.exists())
        assertTrue(csvFile.length() > 0)
        val text = csvFile.readText()
        assertTrue(text.contains("BATTERY_TELEMETRY_RECORDS"))
        assertTrue(text.contains("CHARGING_SESSIONS"))
    }

    @Test
    fun `test degradation sparkline widget color and configuration`() {
        val emeraldHex = com.example.widget.NetraDegradationSparklineWidgetProvider.getThemeColorHex("EMERALD")
        assertEquals("#00E676", emeraldHex)

        val amoledBg = com.example.widget.NetraDegradationSparklineWidgetProvider.getBgColorHex("AMOLED_BLACK")
        assertEquals("#000000", amoledBg)
    }
}
