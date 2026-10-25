package com.example.service

import com.example.model.CanonicalChargingSpeed
import kotlin.math.abs

data class SpeedEngineResult(
    val rawPowerWatts: Float?,
    val consumptionPowerWatts: Float?,
    val speedCategory: CanonicalChargingSpeed,
    val announcementCategory: CanonicalChargingSpeed
)

class ChargingSpeedEngine {

    fun calculate(
        isCharging: Boolean?,
        voltageMv: Int?,
        currentMa: Int?
    ): SpeedEngineResult {
        val normalizedChargingCurrentMa = currentMa?.let { abs(it.toFloat()) }
        // mV × mA = microwatts; divide by 1,000,000 to obtain watts.
        val batteryPowerWatts = if (voltageMv != null && normalizedChargingCurrentMa != null) {
            (voltageMv.toFloat() * normalizedChargingCurrentMa) / 1_000_000f
        } else null

        // Raw incoming charging power: strictly positive power delivered to battery while charging.
        val rawPowerWatts = if (isCharging == true) {
            batteryPowerWatts?.coerceAtLeast(0f)
        } else if (batteryPowerWatts != null && currentMa != null && currentMa < 0) {
            0f
        } else {
            null
        }

        // Independent discharge telemetry only; never modifies charging speed.
        val consumptionWatts = if (currentMa != null && currentMa < 0 && voltageMv != null) {
            abs(voltageMv.toFloat() * currentMa.toFloat()) / 1_000_000f
        } else {
            null
        }

        // Canonical raw incoming power tiers: <5W Slow, 5-<10W Normal, 10-<20W Fast, 20-<40W Super Fast, >=40W Ultra Fast.
        // Never infer charger-side wattage from battery-side telemetry; this is a battery-side estimate.
        val speedCategory = if (isCharging == true && rawPowerWatts != null) {
            when {
                rawPowerWatts >= 40.0f -> CanonicalChargingSpeed.ULTRA_FAST
                rawPowerWatts >= 20.0f -> CanonicalChargingSpeed.SUPER_FAST
                rawPowerWatts >= 10.0f -> CanonicalChargingSpeed.FAST
                rawPowerWatts >= 5.0f -> CanonicalChargingSpeed.NORMAL
                else -> CanonicalChargingSpeed.SLOW
            }
        } else {
            CanonicalChargingSpeed.UNAVAILABLE
        }

        return SpeedEngineResult(
            rawPowerWatts = rawPowerWatts,
            consumptionPowerWatts = consumptionWatts,
            speedCategory = speedCategory,
            announcementCategory = speedCategory
        )
    }
}
