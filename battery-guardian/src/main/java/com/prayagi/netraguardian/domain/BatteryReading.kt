package com.prayagi.netraguardian.domain

data class BatteryReading(
    val levelPercent: Int,
    val charging: Boolean,
    val plugged: Boolean,
    val temperatureCelsius: Float?,
    val voltageMillivolts: Int?,
    val currentMicroamps: Int?,
    val powerWatts: Float?,
    val health: String?,
    val capturedAt: Long = System.currentTimeMillis()
) {
    val temperatureLabel: String get() = temperatureCelsius?.let { "%.1f °C".format(it) } ?: "Unavailable"
    val chargingLabel: String get() = when {
        charging -> "Charging"
        plugged -> "Connected · not charging"
        else -> "Discharging"
    }
    val thermalLabel: String get() = when {
        temperatureCelsius == null -> "Temperature unavailable"
        temperatureCelsius >= 50f -> "Emergency"
        temperatureCelsius >= 46f -> "Critical"
        temperatureCelsius >= 43f -> "Very hot"
        temperatureCelsius >= 40f -> "Hot"
        temperatureCelsius >= 36f -> "Warm"
        temperatureCelsius < 0f -> "Too cold"
        else -> "Normal"
    }
}
