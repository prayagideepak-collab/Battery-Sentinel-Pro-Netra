package com.prayagi.netraguardian.work

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prayagi.netraguardian.data.GuardianDatabase

class HistoryRetentionWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val db = Room.databaseBuilder(applicationContext, GuardianDatabase::class.java, "netra_guardian.db").build()
        db.batteryDao().deleteBefore(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        db.close()
        Result.success()
    } catch (_: Exception) { Result.retry() }
}
