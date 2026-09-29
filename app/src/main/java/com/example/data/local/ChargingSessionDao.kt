package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChargingSession): Long

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 20): Flow<List<ChargingSession>>

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC LIMIT 1")
    fun getLatestSession(): Flow<ChargingSession?>

    @Query("SELECT COUNT(*) FROM charging_sessions")
    fun getTotalSessionsCount(): Flow<Int>

    @Query("DELETE FROM charging_sessions")
    suspend fun clearAll()
}
