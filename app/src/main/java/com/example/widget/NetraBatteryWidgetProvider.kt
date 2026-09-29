package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.NetraApplication
import com.example.R
import com.example.model.BatteryTelemetry
import com.example.service.BatteryMonitorService

/**
 * Compact, non-interactive home screen widget implementation.
 * Displays current battery percentage and charging/discharging status
 * using the existing background telemetry service.
 */
class NetraBatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val telemetry = BatteryMonitorService.liveTelemetryFlow.value
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId, telemetry)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context, telemetry: BatteryTelemetry) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, NetraBatteryWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds.isNotEmpty()) {
                for (widgetId in widgetIds) {
                    updateAppWidget(context, appWidgetManager, widgetId, telemetry)
                }
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            telemetry: BatteryTelemetry
        ) {
            val settings = try {
                NetraApplication.instance.settingsRepository.settings.value
            } catch (_: Exception) {
                null
            }

            val themeColorHex = NetraDegradationSparklineWidgetProvider.getThemeColorHex(settings?.widgetThemeColor ?: "CYAN")
            val bgColorHex = NetraDegradationSparklineWidgetProvider.getBgColorHex(settings?.widgetBackgroundStyle ?: "GLASS_DARK")

            val views = RemoteViews(context.packageName, R.layout.widget_netra_battery)

            // Dynamic styling
            views.setInt(R.id.widget_root, "setBackgroundColor", Color.parseColor(bgColorHex))

            // Set Battery Level & Dynamic Accent
            views.setTextViewText(R.id.widget_battery_percent, "${telemetry.level}%")
            views.setTextColor(R.id.widget_battery_percent, Color.parseColor(themeColorHex))

            // Set Charging / Discharging Status
            val statusText = if (telemetry.isCharging) {
                "⚡ Charging (${telemetry.pluggedType})"
            } else {
                "🔋 Discharging"
            }
            views.setTextViewText(R.id.widget_battery_status, statusText)
            views.setTextColor(R.id.widget_battery_status, if (telemetry.isCharging) Color.parseColor(themeColorHex) else Color.parseColor("#00E676"))

            // Set Voltage and Temperature
            val detailsText = "${String.format("%.1f", telemetry.temperature)}°C • ${telemetry.voltageMv}mV"
            views.setTextViewText(R.id.widget_temp_voltage, detailsText)

            // Non-interactive or opens app on tap
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
