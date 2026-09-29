package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_telemetry")
data class BatteryRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: Int, // 0 - 100
    val temperature: Float, // °C
    val voltageMv: Int, // mV (e.g. 4150)
    val currentMa: Int, // mA (e.g. +850 charging, -320 discharging, or 0 if unavailable)
    val powerWatts: Float, // W (voltageMv * currentMa / 1_000_000)
    val isCharging: Boolean,
    val pluggedType: String, // "AC", "USB", "WIRELESS", "BATTERY"
    val healthStatus: String, // "GOOD", "OVERHEAT", "DEAD", "OVER_VOLTAGE", "UNSPECIFIED_FAILURE", "COLD"
    val screenOn: Boolean = true
)
