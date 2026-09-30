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

    /** Export is unavailable until the real Android PDF path is verified. Never emit fake bytes. */
    @Suppress("UNUSED_PARAMETER")
    fun generateDailyReport(
        context: Context,
        records: List<BatteryRecord>,
        sessions: List<ChargingSession>,
        degradationReport: DegradationReport,
        telemetry: BatteryTelemetry
    ): File? {
        Log.w(TAG, "PDF export unavailable: Android report generation is pending verification")
        return null
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
