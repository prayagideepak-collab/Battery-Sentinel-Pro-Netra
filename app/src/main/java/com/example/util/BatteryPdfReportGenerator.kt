package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.ai.DegradationReport
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import com.example.model.BatteryTelemetry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BatteryPdfReportGenerator {
    private const val TAG = "BatteryPdfReport"

    /**
     * Generates an automated daily PDF report from Room database telemetry
     * and saves it to local device storage.
     */
    fun generateDailyReport(
        context: Context,
        records: List<BatteryRecord>,
        sessions: List<ChargingSession>,
        degradationReport: DegradationReport,
        telemetry: BatteryTelemetry
    ): File? {
        return try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 page dimensions in points
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            drawReportContent(canvas, records, sessions, degradationReport, telemetry)
            pdfDocument.finishPage(page)

            // Ensure reports directory exists
            val reportDir = File(context.filesDir, "reports").apply { mkdirs() }
            val dateSlug = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(reportDir, "Netra_Battery_Report_$dateSlug.pdf")

            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            Log.d(TAG, "Daily PDF Report successfully saved to: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate daily PDF report", e)
            null
        }
    }

    internal fun drawReportContent(
        canvas: Canvas,
        records: List<BatteryRecord>,
        sessions: List<ChargingSession>,
        degradationReport: DegradationReport,
        telemetry: BatteryTelemetry
    ) {
        val width = 595f
        val height = 842f
        val margin = 36f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Background fill
        paint.color = Color.rgb(16, 25, 38) // Cyber dark theme
        canvas.drawRect(0f, 0f, width, height, paint)

        // 2. Header Banner
        paint.color = Color.rgb(22, 36, 56)
        canvas.drawRoundRect(RectF(margin, margin, width - margin, margin + 85f), 12f, 12f, paint)

        // Brand Title
        paint.color = Color.rgb(0, 229, 255) // Netra Cyan
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("NETRA SENTINEL PRO", margin + 18f, margin + 32f, paint)

        paint.color = Color.rgb(0, 230, 118) // Emerald
        paint.textSize = 12f
        canvas.drawText("• DAILY BATTERY HEALTH & ENERGY EFFICIENCY REPORT", margin + 18f, margin + 50f, paint)

        val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy • HH:mm", Locale.getDefault()).format(Date())
        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Generated from SQLite Room Telemetry • $dateStr", margin + 18f, margin + 68f, paint)

        var currentY = margin + 105f

        // 3. Health & Degradation Scorecards (3 Columns)
        val colWidth = (width - (margin * 2) - 20f) / 3f

        // Card 1: Estimated Health
        drawKpiCard(
            canvas,
            x = margin,
            y = currentY,
            w = colWidth,
            h = 65f,
            title = "CAPACITY HEALTH",
            value = degradationReport.estimatedCapacityHealthPercent?.let { "$it%" } ?: "Unavailable",
            sub = "Not measured by this app",
            accentColor = Color.rgb(0, 230, 118)
        )

        // Card 2: ML Failure Risk
        drawKpiCard(
            canvas,
            x = margin + colWidth + 10f,
            y = currentY,
            w = colWidth,
            h = 65f,
            title = "FAILURE RISK",
            value = degradationReport.riskPercent?.let { "$it%" } ?: "Unavailable",
            sub = degradationReport.riskLevel?.let { "Level: $it" } ?: "Unavailable",
            accentColor = if ((degradationReport.riskPercent ?: 0) >= 50) Color.rgb(255, 82, 82) else Color.rgb(255, 179, 0)
        )

        // Card 3: Cycle Equivalent
        drawKpiCard(
            canvas,
            x = margin + (colWidth + 10f) * 2,
            y = currentY,
            w = colWidth,
            h = 65f,
            title = "OBSERVED CHARGE",
            value = "${String.format("%.1f", degradationReport.totalEquivalentCycles)}x",
            sub = "Partial recorded history",
            accentColor = Color.rgb(0, 229, 255)
        )

        currentY += 80f

        // 4. Energy Efficiency & Charging Performance Section
        paint.color = Color.rgb(22, 36, 56)
        canvas.drawRoundRect(RectF(margin, currentY, width - margin, currentY + 125f), 12f, 12f, paint)

        paint.color = Color.rgb(0, 229, 255)
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("RECORDED CHARGING SESSIONS", margin + 16f, currentY + 24f, paint)

        val avgWatt = sessions.map { it.avgPowerWatts }.filter { it.isFinite() && it > 0f }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val peakT = sessions.map { it.peakTemperature }.filter { it.isFinite() && it > 0f }.maxOrNull()
        val totalSessions = sessions.size

        paint.color = Color.WHITE
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("• Recorded Charging Sessions: $totalSessions observed sessions", margin + 16f, currentY + 45f, paint)
        canvas.drawText("• Mean recorded battery-side power: ${avgWatt?.let { "${String.format("%.2f", it)} Watts" } ?: "Unavailable"}", margin + 16f, currentY + 62f, paint)
        canvas.drawText("• Peak Recorded Cell Temperature: ${peakT?.let { "${String.format("%.1f", it)}°C" } ?: "Unavailable"}", margin + 16f, currentY + 79f, paint)
        canvas.drawText("• Coulombic Charging Efficiency: Unavailable (not measured)", margin + 16f, currentY + 96f, paint)
        canvas.drawText("• Primary Stress Driver: Unavailable (capacity history not measured)", margin + 16f, currentY + 113f, paint)

        currentY += 140f

        // 5. Thermal & High-Voltage Dwell Breakdown
        paint.color = Color.rgb(22, 36, 56)
        canvas.drawRoundRect(RectF(margin, currentY, width - margin, currentY + 115f), 12f, 12f, paint)

        paint.color = Color.rgb(255, 179, 0) // Amber
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DURATION / EPISODE MEASURES UNAVAILABLE", margin + 16f, currentY + 24f, paint)

        paint.color = Color.WHITE
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("• Elevated Heat Time (>38°C): Unavailable", margin + 16f, currentY + 45f, paint)
        canvas.drawText("• High Voltage Dwell (>80% SoC): Unavailable", margin + 16f, currentY + 62f, paint)
        canvas.drawText("• Deep Discharge Occurrences (<15% SoC): Unavailable", margin + 16f, currentY + 79f, paint)
        canvas.drawText("• Voltage: ${if (telemetry.isDataAvailable && telemetry.voltageMv > 0) "${telemetry.voltageMv} mV" else "Unavailable"} • Current: ${if (telemetry.isDataAvailable && telemetry.currentMa != 0) "${telemetry.currentMa} mA" else "Unavailable"}", margin + 16f, currentY + 96f, paint)

        currentY += 130f

        // 6. Optimal Charging Window Recommendation (Time-Series AI)
        paint.color = Color.rgb(22, 36, 56)
        canvas.drawRoundRect(RectF(margin, currentY, width - margin, currentY + 95f), 12f, 12f, paint)

        paint.color = Color.rgb(0, 230, 118) // Emerald
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CHARGING PATTERN", margin + 16f, currentY + 24f, paint)

        paint.color = Color.WHITE
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("• Recommended Charging Slot: Unavailable (daily pattern not established)", margin + 16f, currentY + 45f, paint)
        canvas.drawText("• This app cannot measure lifespan gains or enforce charger cutoffs.", margin + 16f, currentY + 62f, paint)
        canvas.drawText("• Habit Suggestion: Avoid charging in high-heat conditions", margin + 16f, currentY + 79f, paint)

        currentY += 110f

        // 7. Actionable Longevity Plan
        paint.color = Color.rgb(22, 36, 56)
        canvas.drawRoundRect(RectF(margin, currentY, width - margin, currentY + 90f), 12f, 12f, paint)

        paint.color = Color.rgb(0, 229, 255)
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("GENERAL GUIDANCE (NOT A DIAGNOSIS)", margin + 16f, currentY + 24f, paint)

        paint.color = Color.WHITE
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("1. Avoid excessive heat; use device-supported charging controls.", margin + 16f, currentY + 45f, paint)
        canvas.drawText("2. This report is not a capacity test or a battery-failure diagnosis.", margin + 16f, currentY + 62f, paint)
        canvas.drawText("3. For unusual swelling, heat or shutdowns, contact device support.", margin + 16f, currentY + 79f, paint)

        // Footer
        paint.color = Color.rgb(100, 116, 139)
        paint.textSize = 8.5f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Netra Sentinel Pro • Recorded observations, not a hardware diagnosis", width / 2f, height - margin, paint)
    }

    private fun drawKpiCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        title: String,
        value: String,
        sub: String,
        accentColor: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(22, 36, 56)
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 10f, 10f, paint)

        // Accent top bar
        paint.color = accentColor
        canvas.drawRoundRect(RectF(x, y, x + w, y + 4f), 2f, 2f, paint)

        // Title
        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(title, x + 10f, y + 18f, paint)

        // Value
        paint.color = accentColor
        paint.textSize = 17f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(value, x + 10f, y + 42f, paint)

        // Sub
        paint.color = Color.rgb(203, 213, 225)
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(sub, x + 10f, y + 56f, paint)
    }

    /**
     * Opens the generated PDF report in the user's PDF viewer.
     */
    fun openPdfReport(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening PDF file", e)
        }
    }

    /**
     * Shares the generated PDF report via Android share sheet.
     */
    fun sharePdfReport(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Netra Sentinel Pro - Daily Battery Health Report")
                putExtra(Intent.EXTRA_TEXT, "Attached is your automated daily battery health and efficiency PDF report from Netra Sentinel Pro.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Battery Health Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing PDF file", e)
        }
    }
}
