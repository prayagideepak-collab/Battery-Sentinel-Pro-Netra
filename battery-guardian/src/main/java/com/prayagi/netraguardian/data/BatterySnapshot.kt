package com.prayagi.netraguardian.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_snapshots")
data class BatterySnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capturedAt: Long,
    val levelPercent: Int,
    val temperatureCelsius: Float?,
    val voltageMillivolts: Int?,
    val currentMicroamps: Int?,
    val powerWatts: Float?,
    val charging: Boolean,
    val screenInteractive: Boolean
)
