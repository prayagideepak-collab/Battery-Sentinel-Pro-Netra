package com.example.model

enum class PowerProfileMode(
    val title: String,
    val subtitle: String,
    val syncIntervalSeconds: Int,
    val brightnessCappedPercent: Int,
    val isAggressiveThrottle: Boolean
) {
    SMART_ADAPTIVE("Smart Adaptive", "Autonomously shifts profiles based on live SoC level & charging state", 60, 80, false),
    BALANCED("Balanced Standard", "Optimal mix of real-time telemetry accuracy and battery longevity", 60, 80, false),
    PERFORMANCE("High Performance", "Maximum sampling frequency (10s) and zero background throttling", 15, 100, false),
    ENDURANCE("Endurance Saver", "Throttled background sync (300s), capped display brightness (60%)", 300, 60, true),
    ULTRA_SAVER("Ultra Battery Saver", "Extreme conservation (900s sync, 30% brightness, background sync paused)", 900, 30, true)
}

data class PowerProfileState(
    val selectedMode: PowerProfileMode = PowerProfileMode.SMART_ADAPTIVE,
    val activeEffectiveMode: PowerProfileMode = PowerProfileMode.BALANCED,
    val dynamicSyncThrottled: Boolean = false,
    val adaptiveBrightnessSuggested: Int = 80,
    val backgroundSyncPaused: Boolean = false,
    val lastProfileTransitionReason: String = "Normal balanced operation"
)
