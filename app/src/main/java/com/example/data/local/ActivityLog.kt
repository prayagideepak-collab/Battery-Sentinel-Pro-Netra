package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val message: String,
    val category: String, // "BATTERY", "THERMAL", "CHARGING", "PROTECTION", "DEVICES", "SYSTEM"
    val severity: String = "INFO", // "INFO", "WARNING", "CRITICAL", "SUCCESS"
    val dotColor: String = "GREEN" // "GREEN", "AMBER", "RED", "BLUE"
)
