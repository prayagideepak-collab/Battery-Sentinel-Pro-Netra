package com.example.model

enum class DotState {
    CONNECTED, // Green
    THROTTLED, // Amber
    CRITICAL,  // Red
    STANDBY    // Blue
}

enum class ChargerSpeed {
    SLOW,     // < 5W
    STANDARD, // 5W - 10W
    FAST,     // 10W - 25W
    RAPID,    // 25W - 45W
    SUPER,    // > 45W
    DISCHARGING,
    UNKNOWN
}

data class BatteryTelemetry(
    val level: Int = 0,
    val isCharging: Boolean = false,
    val pluggedType: String = "UNKNOWN", // "AC", "USB", "WIRELESS", "BATTERY"
    val temperature: Float = 0f, // °C
    val voltageMv: Int = 0, // mV
    val currentMa: Int = 0, // mA (instantaneous current)
    val powerWatts: Float = 0f, // W
    val healthString: String = "Unavailable",
    val technology: String = "Unavailable",
    val chargingSpeed: ChargerSpeed = ChargerSpeed.UNKNOWN,
    val chargingSpeedLabel: String = "Unavailable",
    val timeToFullMinutes: Int? = null,
    val estimatedDischargeHours: Float? = null,
    val observedDischargeRatePerHour: Float = 0f, // % / hr
    val distanceTo40C: Float = 0f, // 40 - currentTemp
    val thermalVelocity: Float = 0f, // °C / min
    val predictedThrottlingMinutes: Int? = null,
    val healthScore: Int? = null, // measured capacity health not exposed by Android
    val healthGrade: String = "Unavailable",
    val isOverheated: Boolean = false, // > 40°C
    val isCriticalOverheat: Boolean = false, // > 45°C
    val serviceDotState: DotState = DotState.STANDBY,
    val isServiceConnected: Boolean = false,
    val isPowerSaverActive: Boolean = false,
    val isScreenOn: Boolean = true,
    val lastUpdateTimestamp: Long = 0L,
    val isDataAvailable: Boolean = false
)
