package com.example.util

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.example.model.AppUsageItem
import java.util.Calendar

object UsageStatsHelper {

    fun getAppUsageDrainList(context: Context, totalDeviceCapacityMah: Int = 5000): List<AppUsageItem> {
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
                    var appName = pkgName.substringAfterLast('.')
                    var category = "Tools & Utilities"

                    try {
                        val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                        appName = packageManager.getApplicationLabel(appInfo).toString()

                        category = when {
                            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 -> "Android System"
                            pkgName.contains("youtube", ignoreCase = true) || pkgName.contains("netflix", ignoreCase = true) || pkgName.contains("spotify", ignoreCase = true) -> "Media & Streaming"
                            pkgName.contains("chrome", ignoreCase = true) || pkgName.contains("browser", ignoreCase = true) -> "Web Browser"
                            pkgName.contains("instagram", ignoreCase = true) || pkgName.contains("tiktok", ignoreCase = true) || pkgName.contains("whatsapp", ignoreCase = true) || pkgName.contains("facebook", ignoreCase = true) -> "Social & Chat"
                            pkgName.contains("game", ignoreCase = true) || pkgName.contains("unity", ignoreCase = true) -> "Gaming"
                            else -> "Productivity & Apps"
                        }
                    } catch (_: PackageManager.NameNotFoundException) {}

                    val foregroundMinutes = stat.totalTimeInForeground / 60_000L
                    val drainPct = ((stat.totalTimeInForeground.toFloat() / effectiveTotal.toFloat()) * 100f)
                    val estMah = ((drainPct / 100f) * (totalDeviceCapacityMah * 0.7f)).toInt()
                    val rate = if (foregroundMinutes > 0) (estMah.toFloat() / (foregroundMinutes / 60f)) else 0f
                    val isHigh = drainPct > 15f || foregroundMinutes > 120

                    val anomaly = when {
                        rate > 450f -> "High GPU / CPU thermal draw detected"
                        foregroundMinutes > 180 -> "Heavy active display screen time"
                        drainPct > 20f -> "Consuming over 20% of daily battery budget"
                        else -> null
                    }

                    results.add(
                        AppUsageItem(
                            packageName = pkgName,
                            appName = appName,
                            foregroundTimeMinutes = foregroundMinutes,
                            backgroundTimeMinutes = maxOf(0L, (stat.totalTimeInForeground / 4000L)),
                            estimatedDrainPercent = String.format("%.1f", drainPct).toFloatOrNull() ?: drainPct,
                            estimatedEnergyMah = estMah,
                            consumptionRateMahPerHour = String.format("%.1f", rate).toFloatOrNull() ?: rate,
                            category = category,
                            isHighDrain = isHigh,
                            anomalyWarning = anomaly
                        )
                    )
                }
            }

            results.sortedByDescending { it.estimatedDrainPercent }.take(20)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun openAppDetailsSettings(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openUsageAccessSettings(context)
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
