package com.prayagi.netraguardian.domain

enum class ChargingBand(val label: String) {
    SLOW("Slow / trickle"),
    STANDARD("Standard"),
    FAST("Fast"),
    SUPER_FAST("Super fast"),
    ULTRA_FAST("Ultra fast"),
    EXTREME_FAST("Extreme fast"),
    NOT_CHARGING("Not charging"),
    UNAVAILABLE("Unavailable")
}

object GuardianRules {
    /** Battery-side power derived from millivolts × microamps. Sign is preserved. */
    fun batteryPowerWatts(voltageMillivolts: Int?, currentMicroamps: Int?): Float? {
        if (voltageMillivolts == null || currentMicroamps == null || voltageMillivolts <= 0) return null
        return (voltageMillivolts.toDouble() * currentMicroamps.toDouble() / 1_000_000_000.0).toFloat()
    }

    fun chargingBand(isCharging: Boolean, signedBatteryPowerWatts: Float?): ChargingBand {
        if (!isCharging) return ChargingBand.NOT_CHARGING
        val power = signedBatteryPowerWatts ?: return ChargingBand.UNAVAILABLE
        if (power <= 0f) return ChargingBand.SLOW
        return when {
            power < 5f -> ChargingBand.SLOW
            power < 10f -> ChargingBand.STANDARD
            power < 18f -> ChargingBand.FAST
            power < 30f -> ChargingBand.SUPER_FAST
            power < 60f -> ChargingBand.ULTRA_FAST
            else -> ChargingBand.EXTREME_FAST
        }
    }

    fun thermalZone(celsius: Float?): String = when {
        celsius == null -> "Unavailable"
        celsius < 0f -> "Battery too cold"
        celsius <= 15f -> "Cold"
        celsius <= 35f -> "Normal"
        celsius <= 39f -> "Warm"
        celsius <= 42f -> "Hot"
        celsius <= 45f -> "Very hot"
        celsius <= 49f -> "Critical"
        else -> "Emergency"
    }
}
