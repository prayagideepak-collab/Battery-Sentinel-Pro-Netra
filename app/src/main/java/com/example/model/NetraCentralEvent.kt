package com.example.model

enum class NetraEventType {
    CHARGER_CONNECTED,
    CHARGER_DISCONNECTED,
    CHARGING_STARTED,
    CHARGING_STOPPED,
    DISCHARGING_STARTED,
    SPEED_CHANGED,
    BATTERY_LEVEL_CROSSED,
    TEMPERATURE_STATE_CHANGED,
    BLUETOOTH_CONNECTED,
    BLUETOOTH_DISCONNECTED,
    UNAVAILABLE
}

data class NetraCentralEvent(
    val eventId: String,
    val eventType: NetraEventType,
    val timestamp: Long = System.currentTimeMillis(),
    val previousValue: String? = null,
    val newValue: String? = null,
    val source: String = "unknown",
    val identityKey: String = eventId
)
