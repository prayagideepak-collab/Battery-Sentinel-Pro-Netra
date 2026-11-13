package com.prayagi.netraguardian.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit

object GuardianWorkScheduler {
    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
        val request = PeriodicWorkRequestBuilder<HistoryRetentionWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "guardian-history-retention", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}
