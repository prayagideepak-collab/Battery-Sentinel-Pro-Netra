package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BatteryRecord): Long

    @Query("SELECT * FROM battery_telemetry ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentRecords(limit: Int = 100): Flow<List<BatteryRecord>>

    @Query("SELECT * FROM battery_telemetry WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getRecordsSince(sinceTimestamp: Long): Flow<List<BatteryRecord>>

    @Query("SELECT * FROM battery_telemetry ORDER BY timestamp DESC LIMIT 1")
    fun getLatestRecord(): Flow<BatteryRecord?>

    @Query("SELECT COUNT(*) FROM battery_telemetry")
    fun getTotalRecordCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM battery_telemetry WHERE temperature > 40.0")
    fun getOverheatEventCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM battery_telemetry WHERE level <= 15 AND NOT isCharging")
    fun getDeepDischargeCount(): Flow<Int>

    @Query("SELECT * FROM battery_telemetry WHERE NOT isCharging ORDER BY timestamp DESC LIMIT 20")
    fun getRecentDischargeCheckpoints(): Flow<List<BatteryRecord>>

    @Query("DELETE FROM battery_telemetry WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldRecords(beforeTimestamp: Long): Int

    @Query("DELETE FROM battery_telemetry")
    suspend fun clearAll()
}
