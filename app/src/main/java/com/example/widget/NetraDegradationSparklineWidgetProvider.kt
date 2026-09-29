package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.NetraApplication
import com.example.R
import com.example.data.local.BatteryRecord
import com.example.model.BatteryTelemetry
import com.example.service.BatteryMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Home screen widget showing long-term battery degradation trends and
 * telemetry using a custom Canvas-rendered sparkline visual fetched
 * from the Room database.
 */
class NetraDegradationSparklineWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val telemetry = BatteryMonitorService.liveTelemetryFlow.value
        CoroutineScope(Dispatchers.IO).launch {
            val repository = try { NetraApplication.instance.batteryRepository } catch (_: Exception) { null }
            val records = repository?.getRecordsSince(0L)?.firstOrNull() ?: emptyList()
            for (widgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId, telemetry, records)
            }
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, NetraDegradationSparklineWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds.isNotEmpty()) {
                val telemetry = BatteryMonitorService.liveTelemetryFlow.value
                CoroutineScope(Dispatchers.IO).launch {
                    val repository = try { NetraApplication.instance.batteryRepository } catch (_: Exception) { null }
                    val records = repository?.getRecordsSince(0L)?.firstOrNull() ?: emptyList()
                    for (widgetId in widgetIds) {
                        updateAppWidget(context, appWidgetManager, widgetId, telemetry, records)
                    }
                }
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            telemetry: BatteryTelemetry,
            records: List<BatteryRecord>
        ) {
            val settings = try {
                NetraApplication.instance.settingsRepository.settings.value
            } catch (_: Exception) {
                null
            }

            val themeColorHex = getThemeColorHex(settings?.widgetThemeColor ?: "CYAN")
            val bgColorHex = getBgColorHex(settings?.widgetBackgroundStyle ?: "GLASS_DARK")

            val views = RemoteViews(context.packageName, R.layout.widget_netra_degradation_sparkline)

            // Dynamic styling
            views.setInt(R.id.widget_sparkline_root, "setBackgroundColor", Color.parseColor(bgColorHex))
            views.setTextColor(R.id.widget_sparkline_title, Color.parseColor(themeColorHex))
            views.setTextViewText(R.id.widget_sparkline_level, "${telemetry.level}%")
            views.setTextViewText(R.id.widget_health_score, "Score: ${telemetry.healthScore}/100 (${telemetry.healthGrade})")

            val chargingStatus = if (telemetry.isCharging) "Charging (${telemetry.pluggedType})" else "Discharging"
            views.setTextViewText(
                R.id.widget_sparkline_meta,
                "• $chargingStatus • ${String.format("%.1f", telemetry.temperature)}°C • ${telemetry.voltageMv}mV"
            )

            // Render custom Canvas Sparkline Bitmap
            val sparklineBitmap = generateSparklineBitmap(records, themeColorHex)
            views.setImageViewBitmap(R.id.widget_sparkline_image, sparklineBitmap)

            // Click to open App
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                110,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_sparkline_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun generateSparklineBitmap(records: List<BatteryRecord>, strokeColorHex: String): Bitmap {
            val width = 450
            val height = 120
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val primaryColor = Color.parseColor(strokeColorHex)

            // Background subtle grid lines
            val gridPaint = Paint().apply {
                color = Color.parseColor("#253346")
                strokeWidth = 1.5f
                style = Paint.Style.STROKE
            }
            canvas.drawLine(0f, height * 0.25f, width.toFloat(), height * 0.25f, gridPaint)
            canvas.drawLine(0f, height * 0.50f, width.toFloat(), height * 0.50f, gridPaint)
            canvas.drawLine(0f, height * 0.75f, width.toFloat(), height * 0.75f, gridPaint)

            // Safe baseline marker (e.g. 80% healthy ceiling or 40°C thermal line)
            val baselinePaint = Paint().apply {
                color = Color.parseColor("#FF5252")
                strokeWidth = 2f
                style = Paint.Style.STROKE
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f)
            }
            canvas.drawLine(0f, height * 0.20f, width.toFloat(), height * 0.20f, baselinePaint)

            val dataPoints = if (records.isNotEmpty()) {
                records.takeLast(40).map { it.level.toFloat() }
            } else {
                listOf(95f, 92f, 88f, 85f, 80f, 78f, 75f, 82f, 85f, 84f)
            }

            if (dataPoints.size >= 2) {
                val minVal = (dataPoints.minOrNull() ?: 0f).coerceAtLeast(0f)
                val maxVal = (dataPoints.maxOrNull() ?: 100f).coerceAtMost(100f).coerceAtLeast(minVal + 10f)
                val range = maxVal - minVal

                val stepX = width.toFloat() / (dataPoints.size - 1)

                val linePath = Path()
                val fillPath = Path()

                dataPoints.forEachIndexed { index, value ->
                    val x = index * stepX
                    val normalized = (value - minVal) / range
                    val y = height - (normalized * (height - 24f) + 12f)

                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, height.toFloat())
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = (index - 1) * stepX
                        val prevNorm = (dataPoints[index - 1] - minVal) / range
                        val prevY = height - (prevNorm * (height - 24f) + 12f)
                        val midX = (prevX + x) / 2f
                        linePath.cubicTo(midX, prevY, midX, y, x, y)
                        fillPath.cubicTo(midX, prevY, midX, y, x, y)
                    }
                }

                fillPath.lineTo(width.toFloat(), height.toFloat())
                fillPath.close()

                // Gradient Fill under curve
                val fillPaint = Paint().apply {
                    style = Paint.Style.FILL
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        primaryColor and 0x55FFFFFF,
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawPath(fillPath, fillPaint)

                // Stroke Line
                val linePaint = Paint().apply {
                    color = primaryColor
                    strokeWidth = 3.5f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawPath(linePath, linePaint)
            }

            return bitmap
        }

        fun getThemeColorHex(themeName: String): String {
            return when (themeName.uppercase()) {
                "EMERALD" -> "#00E676"
                "AMBER" -> "#FFB300"
                "RED" -> "#FF5252"
                "PURPLE" -> "#B388FF"
                "MONO" -> "#FFFFFF"
                else -> "#00E5FF" // CYAN
            }
        }

        fun getBgColorHex(bgStyle: String): String {
            return when (bgStyle.uppercase()) {
                "AMOLED_BLACK" -> "#000000"
                "TRANSLUCENT" -> "#0D1520"
                else -> "#101926" // GLASS_DARK
            }
        }
    }
}
