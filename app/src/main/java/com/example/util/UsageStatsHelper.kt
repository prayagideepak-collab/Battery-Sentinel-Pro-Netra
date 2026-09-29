package com.example.util

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.example.model.AppUsageItem
import java.util.Calendar

object UsageStatsHelper {

    fun getAppUsageDrainList(context: Context): List<AppUsageItem> {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
            val packageManager = context.packageManager

            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val startTime = calendar.timeInMillis

            val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            ) ?: emptyList()

            if (usageStatsList.isEmpty()) return emptyList()

            val totalForegroundMs = usageStatsList.sumOf { it.totalTimeInForeground }
            val effectiveTotal = if (totalForegroundMs > 0) totalForegroundMs else 1L

            val results = mutableListOf<AppUsageItem>()

            for (stat in usageStatsList) {
                if (stat.totalTimeInForeground > 30_000) { // at least 30 seconds
                    val pkgName = stat.packageName
                    val appName = try {
                        val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (_: PackageManager.NameNotFoundException) {
                        pkgName.substringAfterLast('.')
                    }

                    val foregroundMinutes = stat.totalTimeInForeground / 60_000L
                    val drainPct = ((stat.totalTimeInForeground.toFloat() / effectiveTotal.toFloat()) * 100f)
                    val isHigh = drainPct > 15f || foregroundMinutes > 120

                    results.add(
                        AppUsageItem(
                            packageName = pkgName,
                            appName = appName,
                            foregroundTimeMinutes = foregroundMinutes,
                            estimatedDrainPercent = String.format("%.1f", drainPct).toFloatOrNull() ?: drainPct,
                            isHighDrain = isHigh
                        )
                    )
                }
            }

            results.sortedByDescending { it.foregroundTimeMinutes }.take(15)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
