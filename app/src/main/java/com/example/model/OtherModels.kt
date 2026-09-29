package com.example.model

data class BluetoothDeviceItem(
    val name: String,
    val address: String,
    val isConnected: Boolean,
    val isPaired: Boolean,
    val deviceType: String, // "Audio / Headphones", "Wearable / Watch", "Input Device", "Phone", "Generic Bluetooth"
    val batteryPercent: Int?, // null if hardware/vendor doesn't expose it
    val profile: String = "A2DP / HFP"
)

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val foregroundTimeMinutes: Long,
    val estimatedDrainPercent: Float,
    val isHighDrain: Boolean
)

data class SystemPermissionsState(
    val isNotificationGranted: Boolean,
    val isBluetoothGranted: Boolean,
    val isUsageStatsGranted: Boolean,
    val isIgnoringBatteryOptimizations: Boolean
)

data class AiDiagnosticResult(
    val summary: String = "",
    val thermalAnalysis: String = "",
    val degradationRisk: String = "",
    val optimalChargingAdvice: String = "",
    val recommendedActions: List<String> = emptyList(),
    val modelUsed: String = "",
    val isThinkingMode: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class LongevityChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
