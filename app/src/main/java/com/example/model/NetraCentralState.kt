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

enum class CanonicalMediaState {
    PLAYING, PAUSED, STOPPED, UNKNOWN, UNSUPPORTED
}

enum class CapabilityStatus {
    SUPPORTED,
    UNSUPPORTED,
    AVAILABLE,
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    DISABLED,
    UNKNOWN
}

enum class CapabilityType {
    BATTERY_TELEMETRY,
    BATTERY_TEMPERATURE,
    BATTERY_VOLTAGE,
    BATTERY_CURRENT,
    BATTERY_CHARGE_COUNTER,
    BATTERY_HEALTH_STATUS,
    BATTERY_POWER_CALCULATION,
    CHARGING_SPEED_CALCULATION,
    FAST_CHARGING_DETECTION,
    CHARGING_STATE,
    CHARGER_CONNECTION_STATE,
    BLUETOOTH_HARDWARE,
    BLUETOOTH_LE,
    BLUETOOTH_CONNECTED_INFO,
    BLUETOOTH_BATTERY_LEVEL,
    NOTIFICATIONS,
    EXACT_ALARM,
    POWER_SAVE_MODE,
    BATTERY_OPTIMIZATION_WHITELIST,
    TEXT_TO_SPEECH,
    MEDIA_PLAYBACK_CONTROL,
    USAGE_ACCESS,
    BACKGROUND_MONITORING,
    STORAGE_CACHE_OPERATIONS
}

enum class FieldStatus {
    LIVE,
    LAST_VALID,
    UNAVAILABLE,
    UNSUPPORTED
}

data class TelemetryFieldState(
    val levelStatus: FieldStatus = FieldStatus.UNAVAILABLE,
    val tempStatus: FieldStatus = FieldStatus.UNAVAILABLE,
    val voltageStatus: FieldStatus = FieldStatus.UNAVAILABLE,
    val currentStatus: FieldStatus = FieldStatus.UNAVAILABLE,
    val powerStatus: FieldStatus = FieldStatus.UNAVAILABLE
)

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
    val bluetoothDevices: List<BluetoothDeviceItem> = emptyList(),
    val bluetoothHistory: List<BluetoothDeviceItem> = emptyList(),
    val lastUpdateTimestamp: Long = 0L,
    val isDataFresh: Boolean = false,
    // Field-level retention tracking
    val fieldStates: TelemetryFieldState = TelemetryFieldState(),
    // Central Capability Registry states
    val capabilities: Map<CapabilityType, CapabilityStatus> = emptyMap(),
    // ETAs (null if unavailable)
    val chargingEtaMinutes: Int? = null,
    val dischargingEtaMinutes: Int? = null,
    // Media Playback State Integration
    val mediaState: CanonicalMediaState = CanonicalMediaState.UNKNOWN,
    val isMediaControlAvailable: Boolean = true,
    val mediaPausedByNethra: Boolean = false,
    // Night Protection Policy State
    val isNightProtectionActive: Boolean = false
)

/** The legacy non-null telemetry model may only receive a complete hardware sample. */
fun NetraCentralState.hasCompleteLegacyReading(): Boolean =
    batteryLevel != null && isCharging != null &&
        temperatureCelsius != null && voltageMv != null &&
        currentMa != null && powerWatts != null
