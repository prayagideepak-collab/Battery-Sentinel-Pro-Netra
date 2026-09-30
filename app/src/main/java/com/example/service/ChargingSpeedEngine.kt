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
        val batteryPowerWatts = if (voltageMv != null && currentMa != null) {
            (voltageMv.toFloat() * currentMa.toFloat()) / 1_000_000f
        } else null

        // Raw incoming charging power: strictly positive power delivered to battery while charging
        val rawPowerWatts = if (isCharging == true) {
            batteryPowerWatts?.coerceAtLeast(0f)
        } else if (batteryPowerWatts != null && batteryPowerWatts < 0) {
            0f
        } else {
            null
        }

        // Monitored strictly for independent phone discharge telemetry; never modifies charging speed
        val consumptionWatts = if (currentMa != null && currentMa < 0 && voltageMv != null) {
            abs(voltageMv.toFloat() * currentMa.toFloat()) / 1_000_000f
        } else {
            null
        }

        // Hardcoded speed tiers: <5W = Slow, >=5W and <10W = Normal, >=10W and <=20W = Fast, >20W = Ultra Fast
        // Rely exclusively on raw battery input power. No 'effective' or 'net' charging power calculations.
        val speedCategory = if (isCharging == true && rawPowerWatts != null) {
            when {
                rawPowerWatts > 20.0f -> CanonicalChargingSpeed.ULTRA_FAST
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
