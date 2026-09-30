package com.example.ai

import android.content.Context
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession

enum class FailureRiskLevel {
    LOW,
    MODERATE,
    ELEVATED,
    CRITICAL
}

data class DegradationReport(
    val riskPercent: Int?,
    val riskLevel: FailureRiskLevel?,
    val estimatedCapacityHealthPercent: Int?,
    val totalEquivalentCycles: Float,
    val highVoltageDwellMinutes: Long?,
    val thermalStressHours: Float?,
    val deepDischargeCount: Int?,
    val primaryRiskFactor: String,
    val keyMitigation: String,
    val detailedInsights: List<String>,
    val requiresAlert: Boolean
)

object BatteryDegradationPredictor {

    /** Summarize recorded observations without diagnosing chemistry or predicting failure. */
    fun analyzeDegradationAndFailureRisk(
        records: List<BatteryRecord>,
        sessions: List<ChargingSession>
    ): DegradationReport {
        // A partial charging history gives only a lower-bound count of observed
        // charge throughput, never a device-lifetime cycle or capacity estimate.
        val observedCycles = sessions.filter {
            it.startLevel in 0..100 && it.endLevel in 0..100 &&
                it.endTime > it.startTime && it.endLevel >= it.startLevel
        }.sumOf { it.endLevel - it.startLevel } / 100f
        val details = mutableListOf<String>()
        if (records.isEmpty()) {
            details.add("No battery records are available yet.")
        } else {
            val warm = records.count { it.temperature > 38f }
            val low = records.count { !it.isCharging && it.level < 15 }
            if (warm > 0) details.add("$warm sample(s) above 38°C; duration unavailable.")
            if (low > 0) details.add("$low low-battery sample(s); distinct episodes unavailable.")
            if (details.isEmpty()) details.add("No high-temperature or low-battery samples in the available records.")
        }
        details.add("Measured capacity health and calibrated failure risk are unavailable.")
        return DegradationReport(
            riskPercent = null,
            riskLevel = null,
            estimatedCapacityHealthPercent = null,
            totalEquivalentCycles = observedCycles,
            highVoltageDwellMinutes = null,
            thermalStressHours = null,
            deepDischargeCount = null,
            primaryRiskFactor = "Unavailable without calibrated capacity history",
            keyMitigation = "Avoid prolonged heat; use charging controls available on your device.",
            detailedInsights = details,
            requiresAlert = false
        )
    }

    // No calibrated failure-risk model exists; historical samples never trigger failure notifications.
    @Suppress("UNUSED_PARAMETER")
    fun checkAndNotifyFailureRisk(context: Context, report: DegradationReport) = Unit
}
