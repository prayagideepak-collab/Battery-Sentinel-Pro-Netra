package com.example.service

import com.example.model.CanonicalChargingSpeed
import kotlin.math.abs

data class SpeedEngineResult(
    val rawPowerWatts: Float?,
    val netPowerWatts: Float?,
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

        val rawPowerWatts = if (isCharging == true) {
            batteryPowerWatts?.coerceAtLeast(0f)
        } else if (batteryPowerWatts != null && batteryPowerWatts < 0) {
            0f
        } else {
            null
        }

        val netPowerWatts = batteryPowerWatts

        val consumptionWatts = if (currentMa != null && currentMa < 0 && voltageMv != null) {
            abs(voltageMv.toFloat() * currentMa.toFloat()) / 1_000_000f
        } else {
            null
        }

        // Exact thresholds: <5W = Slow, >=5W and <10W = Normal, >=10W and <=20W = Fast, >20W = Ultra Fast
        // RAW INCOMING POWER ONLY. Consumption / net power does NOT affect charging speed classification.
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

        val announcementCategory = speedCategory

        return SpeedEngineResult(
            rawPowerWatts = rawPowerWatts,
            netPowerWatts = netPowerWatts,
            consumptionPowerWatts = consumptionWatts,
            speedCategory = speedCategory,
            announcementCategory = announcementCategory
        )
    }
}
