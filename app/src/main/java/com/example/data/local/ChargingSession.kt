package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charging_sessions")
data class ChargingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val startLevel: Int,
    val endLevel: Int,
    val peakTemperature: Float,
    val avgPowerWatts: Float,
    val chargerType: String, // "AC", "USB", "WIRELESS", "FAST"
    val durationMinutes: Int
)
