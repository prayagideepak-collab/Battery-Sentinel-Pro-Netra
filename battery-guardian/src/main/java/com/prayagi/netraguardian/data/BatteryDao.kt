package com.prayagi.netraguardian.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: BatterySnapshot)
    @Query("SELECT * FROM battery_snapshots ORDER BY capturedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<BatterySnapshot>>
    @Query("SELECT * FROM battery_snapshots WHERE capturedAt >= :since ORDER BY capturedAt ASC")
    fun observeSince(since: Long): Flow<List<BatterySnapshot>>
    @Query("DELETE FROM battery_snapshots WHERE capturedAt < :before")
    suspend fun deleteBefore(before: Long)
    @Query("DELETE FROM battery_snapshots")
    suspend fun clearAll()
}
