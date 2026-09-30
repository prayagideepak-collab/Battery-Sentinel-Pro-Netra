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
        val normalizedChargingCurrentMa = if (currentMa != null) kotlin.math.abs(currentMa.toFloat()) else null
        val batteryPowerWatts = if (voltageMv != null && normalizedChargingCurrentMa != null) {
            (voltageMv.toFloat() * normalizedChargingCurrentMa) / 1_000f
        } else null

        // Raw incoming charging power: strictly positive power delivered to battery while charging
        val rawPowerWatts = if (isCharging == true) {
            batteryPowerWatts?.coerceAtLeast(0f)
        } else if (batteryPowerWatts != null && batteryPowerWatts < 0) {
            0f
        } else {
            null
        }

        // Independent discharge telemetry only; never modifies charging speed.
        val consumptionWatts = if (currentMa != null && currentMa < 0 && voltageMv != null) {
            abs(voltageMv.toFloat() * currentMa.toFloat()) / 1_000f
        } else {
            null
        }

        // Canonical raw incoming power tiers: <5W Slow, 5-<10W Normal, 10-<20W Fast, 20-<40W Super Fast, >=40W Ultra Fast
        // Rely exclusively on raw battery input power. No 'effective' or 'net' charging power calculations.
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
