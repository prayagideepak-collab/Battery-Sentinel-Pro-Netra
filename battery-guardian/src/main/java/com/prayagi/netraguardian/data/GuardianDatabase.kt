package com.prayagi.netraguardian.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [BatterySnapshot::class], version = 1, exportSchema = false)
abstract class GuardianDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao
}
