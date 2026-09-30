package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.ActivityLog
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BatteryCsvExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * Generates a comprehensive CSV export containing time-series battery telemetry,
     * historical charging sessions, and activity logs.
     */
    fun exportTelemetryToCsv(
        context: Context,
        records: List<BatteryRecord>,
        sessions: List<ChargingSession>,
        logs: List<ActivityLog> = emptyList()
    ): File? {
        return try {
            val exportDir = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }
            val timeStamp = fileTimestampFormat.format(Date())
            val file = File(exportDir, "netra_battery_telemetry_$timeStamp.csv")

            FileWriter(file).use { writer ->
                // Section 1: Metadata Header
                writer.append("# Netra Sentinel Pro Battery Telemetry Export\n")
                writer.append("# Generated At: ${isoFormat.format(Date())}\n")
                writer.append("# Total Records: ${records.size}, Total Charging Sessions: ${sessions.size}\n\n")

                // Section 2: Time-Series Telemetry Records
                writer.append("=== BATTERY_TELEMETRY_RECORDS ===\n")
                writer.append("Timestamp_ISO,Timestamp_Epoch_Ms,Battery_Level_Pct,Temperature_C,Voltage_mV,Current_mA,Power_Watts,Is_Charging,Plugged_Type,Health_Status,Screen_On\n")

                for (record in records) {
                    val isoDate = isoFormat.format(Date(record.timestamp))
                    writer.append("$isoDate,")
                    writer.append("${record.timestamp},")
                    writer.append("${record.level},")
                    writer.append("${String.format(Locale.US, "%.1f", record.temperature)},")
                    writer.append("${record.voltageMv},")
                    writer.append("${record.currentMa},")
                    writer.append("${String.format(Locale.US, "%.2f", record.powerWatts)},")
                    writer.append("${record.isCharging},")
                    writer.append("${escapeCsv(record.pluggedType)},")
                    writer.append("${escapeCsv(record.healthStatus)},")
                    writer.append("${record.screenOn}\n")
                }

                // Section 3: Charging Sessions
                writer.append("\n=== CHARGING_SESSIONS ===\n")
                writer.append("Start_Time_ISO,End_Time_ISO,Duration_Minutes,Start_Level_Pct,End_Level_Pct,Total_Gain_Pct,Peak_Temp_C,Avg_Power_Watts,Charger_Type\n")

                for (session in sessions) {
                    val startIso = isoFormat.format(Date(session.startTime))
                    val endIso = isoFormat.format(Date(session.endTime))
                    val gain = session.endLevel - session.startLevel
                    writer.append("$startIso,")
                    writer.append("$endIso,")
                    writer.append("${session.durationMinutes},")
                    writer.append("${session.startLevel},")
                    writer.append("${session.endLevel},")
                    writer.append("$gain,")
                    writer.append("${String.format(Locale.US, "%.1f", session.peakTemperature)},")
                    writer.append("${String.format(Locale.US, "%.2f", session.avgPowerWatts)},")
                    writer.append("${escapeCsv(session.chargerType)}\n")
                }

                // Section 4: System Logs
                if (logs.isNotEmpty()) {
                    writer.append("\n=== ACTIVITY_LOGS ===\n")
                    writer.append("Timestamp_ISO,Category,Severity,Title,Message\n")
                    for (log in logs) {
                        val logIso = isoFormat.format(Date(log.timestamp))
                        writer.append("$logIso,")
                        writer.append("${escapeCsv(log.category)},")
                        writer.append("${escapeCsv(log.severity)},")
                        writer.append("${escapeCsv(log.title)},")
                        writer.append("${escapeCsv(log.message)}\n")
                    }
                }
            }
            file
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Launches Android Share sheet to export/save the CSV file.
     */
    fun shareCsvFile(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Netra Battery Telemetry CSV (${file.name})")
                putExtra(Intent.EXTRA_TEXT, "Exported battery health and usage telemetry dataset from Battery Sentinel Pro Netra.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Export Battery Telemetry CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
