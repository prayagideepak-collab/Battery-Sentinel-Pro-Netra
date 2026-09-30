package com.example.model

enum class CanonicalChargingSpeed {
    SLOW,       // < 5W
    NORMAL,     // 5W to < 10W
    FAST,       // 10W to 20W
    ULTRA_FAST, // > 20W
    UNAVAILABLE
}

enum class CanonicalPluggedType {
    AC, USB, WIRELESS, OTHER, NONE, UNKNOWN
}

data class NetraCentralState(
    val batteryLevel: Int? = null, // null if unavailable
    val isCharging: Boolean? = null,
    val isChargerConnected: Boolean? = null,
    val chargerConnectedAt: Long? = null,
    val chargingStartedAt: Long? = null,
    val chargingStoppedAt: Long? = null,
    val chargerDisconnectedAt: Long? = null,
    val dischargingStartedAt: Long? = null,
    val pluggedType: CanonicalPluggedType? = null,
    val temperatureCelsius: Float? = null, // null if unavailable
    val voltageMv: Int? = null, // null if unavailable
    val currentMa: Int? = null, // null if unavailable
    val powerWatts: Float? = null, // Raw incoming charging power (or discharge power if discharging)
    val netPowerWatts: Float? = null, // Net effective power (Raw incoming - consumption)
    val consumptionPowerWatts: Float? = null, // Phone consumption power
    val chargingSpeed: CanonicalChargingSpeed = CanonicalChargingSpeed.UNAVAILABLE, // Based on raw power
    val announcementSpeed: CanonicalChargingSpeed = CanonicalChargingSpeed.UNAVAILABLE, // Based on net effective power
    val bluetoothConnected: Boolean? = null,
    val bluetoothBatteryPercent: Int? = null,
    val lastUpdateTimestamp: Long = 0L,
    val isDataFresh: Boolean = false,
    // ETAs (null if unavailable)
    val chargingEtaMinutes: Int? = null,
    val dischargingEtaMinutes: Int? = null
)

/** The legacy non-null telemetry model may only receive a complete hardware sample. */
fun NetraCentralState.hasCompleteLegacyReading(): Boolean =
    batteryLevel != null && isCharging != null &&
        temperatureCelsius != null && voltageMv != null &&
        currentMa != null && powerWatts != null
