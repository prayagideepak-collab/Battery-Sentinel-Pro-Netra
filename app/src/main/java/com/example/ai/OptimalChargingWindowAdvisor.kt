package com.example.ai

import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession

data class HourlyChargingScore(
    val hourOfDay: Int,
    val sessionCount: Int,
    val avgTemperature: Float,
    val isOvernightDwell: Boolean,
    val score: Int,
    val statusLabel: String
)

data class OptimalWindowSuggestion(
    val primaryOptimalWindow: String,
    val secondaryOptimalWindow: String,
    val discouragedWindow: String,
    val currentWindowRating: String,
    val predictedLifespanExtension: String,
    val keyScientificRationale: String,
    val hourlyScores: List<HourlyChargingScore>
)

object OptimalChargingWindowAdvisor {
    /** No ambient temperature, wake schedule, or overnight dwell history is measured here. */
    fun analyzeChargingHabitsAndSuggestWindows(
        records: List<BatteryRecord>,
        sessions: List<ChargingSession>
    ): OptimalWindowSuggestion = OptimalWindowSuggestion(
        primaryOptimalWindow = "Unavailable",
        secondaryOptimalWindow = "Unavailable",
        discouragedWindow = "Unavailable",
        currentWindowRating = "Unavailable",
        predictedLifespanExtension = "Unavailable",
        keyScientificRationale = "${records.size} battery samples and ${sessions.size} charging sessions do not establish a suitable daily charging time or a lifespan gain.",
        hourlyScores = emptyList()
    )
}
