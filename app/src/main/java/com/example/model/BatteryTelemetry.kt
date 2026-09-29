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
    val pluggedType: String = "BATTERY", // "AC", "USB", "WIRELESS", "BATTERY"
    val temperature: Float = 0f, // °C
    val voltageMv: Int = 0, // mV
    val currentMa: Int = 0, // mA (instantaneous current)
    val powerWatts: Float = 0f, // W
    val healthString: String = "Good",
    val technology: String = "Li-ion",
    val chargingSpeed: ChargerSpeed = ChargerSpeed.UNKNOWN,
    val chargingSpeedLabel: String = "Standard",
    val timeToFullMinutes: Int? = null,
    val estimatedDischargeHours: Float = 0f,
    val observedDischargeRatePerHour: Float = 0f, // % / hr
    val distanceTo40C: Float = 0f, // 40 - currentTemp
    val thermalVelocity: Float = 0f, // °C / min
    val predictedThrottlingMinutes: Int? = null,
    val healthScore: Int = 94, // 0 - 100
    val healthGrade: String = "Excellent (A+)",
    val isOverheated: Boolean = false, // > 40°C
    val isCriticalOverheat: Boolean = false, // > 45°C
    val serviceDotState: DotState = DotState.CONNECTED,
    val isServiceConnected: Boolean = true,
    val isPowerSaverActive: Boolean = false,
    val isScreenOn: Boolean = true,
    val lastUpdateTimestamp: Long = System.currentTimeMillis()
)
