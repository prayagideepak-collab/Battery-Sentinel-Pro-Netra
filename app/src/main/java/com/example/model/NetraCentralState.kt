package com.example.model

enum class CanonicalChargerState {
    CHARGER_CONNECTED_CHARGING,
    CHARGER_CONNECTED_NOT_CHARGING,
    CHARGER_DISCONNECTED,
    DISCHARGING,
    UNKNOWN
}

enum class CanonicalChargingSpeed {
    SLOW,       // < 5W
    NORMAL,     // 5W to < 10W
    FAST,       // 10W to < 20W
    SUPER_FAST, // 20W to < 40W
    ULTRA_FAST, // >= 40W
    UNAVAILABLE;

    val displayLabel: String
        get() = when (this) {
            SLOW -> "Slow Charging"
            NORMAL -> "Normal Charging"
            FAST -> "Fast Charging"
            SUPER_FAST -> "Super Fast Charging"
            ULTRA_FAST -> "Ultra Fast Charging"
            UNAVAILABLE -> "Unavailable"
        }

    val tierRange: String
        get() = when (this) {
            SLOW -> "< 5W"
            NORMAL -> "5W–<10W"
            FAST -> "10W–<20W"
            SUPER_FAST -> "20W–<40W"
            ULTRA_FAST -> "≥ 40W"
            UNAVAILABLE -> "N/A"
        }

    val fullLabelWithTier: String
        get() = when (this) {
            SLOW -> "Slow (<5W)"
            NORMAL -> "Normal (5W–<10W)"
            FAST -> "Fast (10W–<20W)"
            SUPER_FAST -> "Super Fast (20W–<40W)"
            ULTRA_FAST -> "Ultra Fast (≥40W)"
            UNAVAILABLE -> "Unavailable"
        }
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
    STORAGE_CACHE_OPERATIONS,
    BRIGHTNESS_CONTROL
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
    val canonicalChargerState: CanonicalChargerState = CanonicalChargerState.UNKNOWN,
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
    val announcementSpeed: CanonicalChargingSpeed = CanonicalChargingSpeed.UNAVAILABLE, // Based on the same raw incoming power classification as chargingSpeed
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
    val isNightProtectionActive: Boolean = false,
    // Thermal & Battery Protection States (Part 16 & 17)
    val isCriticalThermalActive: Boolean = false,
    val isLowBatteryControlActive: Boolean = false,
    val targetBrightnessPercent: Int? = null,
    val thermalCauseDiagnosis: String? = null,
    // Real-Time Pipeline Latency Instrumentation (Target: <= 100ms)
    val pipelineLatency: PipelineLatencyMetrics? = null
)

data class PipelineLatencyMetrics(
    val t0ReceivedNanos: Long = 0L,
    val t1ValidatedNanos: Long = 0L,
    val t2StateUpdatedNanos: Long = 0L,
    val t3EmittedNanos: Long = 0L,
    val validationDurationMs: Float = 0f,
    val updateDurationMs: Float = 0f,
    val totalProcessingMs: Float = 0f,
    val meetsBudget: Boolean = true // <= 100ms
)

/** The legacy non-null telemetry model may only receive a complete hardware sample. */
fun NetraCentralState.hasCompleteLegacyReading(): Boolean =
    batteryLevel != null && isCharging != null &&
        temperatureCelsius != null && voltageMv != null &&
        currentMa != null && powerWatts != null
