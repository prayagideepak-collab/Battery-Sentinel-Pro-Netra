package com.example.ai

import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import com.example.model.BatteryTelemetry

data class NightChargingThrottleStatus(
    val isThrottleActive: Boolean = false,
    val statusHeadline: String = "Night charging control unavailable",
    val detailedAdvice: String = "This app cannot set charger power, pause charging, or guarantee a wake-time charge. Use controls provided by your device if available."
)

object NightChargingThrottler {
    /** Android telemetry and saved history provide no charger power-control API. */
    @Suppress("UNUSED_PARAMETER")
    fun evaluateNightThrottle(
        telemetry: BatteryTelemetry,
        isFeatureEnabled: Boolean,
        targetWakeHour: Int = 7,
        records: List<BatteryRecord> = emptyList(),
        sessions: List<ChargingSession> = emptyList()
    ): NightChargingThrottleStatus = NightChargingThrottleStatus()
}
