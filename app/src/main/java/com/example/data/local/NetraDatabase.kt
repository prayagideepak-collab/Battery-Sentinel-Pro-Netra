package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BatteryRecord::class,
        ChargingSession::class,
        ActivityLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NetraDatabase : RoomDatabase() {

    abstract fun batteryDao(): BatteryDao
    abstract fun chargingSessionDao(): ChargingSessionDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        @Volatile
        private var INSTANCE: NetraDatabase? = null

        fun getDatabase(context: Context): NetraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NetraDatabase::class.java,
                    "netra_battery_sentinel.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
