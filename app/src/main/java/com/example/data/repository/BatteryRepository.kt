package com.example.data.repository

import com.example.data.local.ActivityLog
import com.example.data.local.ActivityLogDao
import com.example.data.local.BatteryDao
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import com.example.data.local.ChargingSessionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatteryRepository(
    private val batteryDao: BatteryDao,
    private val chargingSessionDao: ChargingSessionDao,
    private val activityLogDao: ActivityLogDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Last recorded values for intelligent low-power debouncing
    @Volatile private var lastRecordedRecord: BatteryRecord? = null
    @Volatile private var lastRecordTimeMs: Long = 0L
    @Volatile private var currentChargingSessionStart: BatteryRecord? = null
    @Volatile private var currentSessionPeakTemp: Float = 0f

    val recentRecords: Flow<List<BatteryRecord>> = batteryDao.getRecentRecords(100)
    val latestRecord: Flow<BatteryRecord?> = batteryDao.getLatestRecord()
    val totalRecordCount: Flow<Int> = batteryDao.getTotalRecordCount()
    val overheatCount: Flow<Int> = batteryDao.getOverheatEventCount()
    val deepDischargeCount: Flow<Int> = batteryDao.getDeepDischargeCount()
    val recentDischargeCheckpoints: Flow<List<BatteryRecord>> = batteryDao.getRecentDischargeCheckpoints()

    val recentChargingSessions: Flow<List<ChargingSession>> = chargingSessionDao.getRecentSessions(20)
    val latestChargingSession: Flow<ChargingSession?> = chargingSessionDao.getLatestSession()
    val totalSessionsCount: Flow<Int> = chargingSessionDao.getTotalSessionsCount()

    val recentLogs: Flow<List<ActivityLog>> = activityLogDao.getRecentLogs(100)

    fun getLogsByCategory(category: String): Flow<List<ActivityLog>> {
        return if (category == "ALL") {
            activityLogDao.getRecentLogs(100)
        } else {
            activityLogDao.getLogsByCategory(category, 100)
        }
    }

    fun getRecordsSince(sinceTimestamp: Long): Flow<List<BatteryRecord>> {
        return batteryDao.getRecordsSince(sinceTimestamp)
    }

    /**
     * Smart debounced write to SQLite Room Database.
     * Complies with ULTRA-LOW POWER protocol:
     * Only writes to flash storage if:
     * 1. Charging state toggled
     * 2. Battery level changed
     * 3. Temperature shifted by >= 0.5°C
     * 4. More than 3 minutes (180,000ms) have elapsed since last write
     */
    suspend fun recordTelemetryDebounced(record: BatteryRecord) = withContext(Dispatchers.IO) {
        val last = lastRecordedRecord
        val now = System.currentTimeMillis()
        val timeDiff = now - lastRecordTimeMs

        val isSignificant = last == null ||
                last.isCharging != record.isCharging ||
                last.level != record.level ||
                kotlin.math.abs(last.temperature - record.temperature) >= 0.5f ||
                timeDiff >= 180_000L

        if (isSignificant) {
            batteryDao.insert(record)
            lastRecordedRecord = record
            lastRecordTimeMs = now

            // Handle Charging Session Tracking
            handleChargingSessionTransition(record)
        }
    }

    private suspend fun handleChargingSessionTransition(current: BatteryRecord) {
        if (current.isCharging) {
            val session = currentChargingSessionStart
            if (session == null) {
                // New charging session started
                currentChargingSessionStart = current
                currentSessionPeakTemp = current.temperature
                logEvent(
                    title = "Charging Started",
                    message = "Connected to ${current.pluggedType} power at ${current.level}%. Battery Temp: ${current.temperature}°C",
                    category = "CHARGING",
                    severity = "INFO",
                    dotColor = "GREEN"
                )
            } else {
                if (current.temperature > currentSessionPeakTemp) {
                    currentSessionPeakTemp = current.temperature
                }
            }
        } else {
            val session = currentChargingSessionStart
            if (session != null) {
                // Charging session finished
                val durationMs = current.timestamp - session.timestamp
                val durationMin = kotlin.math.max(1, (durationMs / 60_000L).toInt())
                val avgPower = (session.powerWatts + current.powerWatts) / 2f

                val completedSession = ChargingSession(
                    startTime = session.timestamp,
                    endTime = current.timestamp,
                    startLevel = session.level,
                    endLevel = current.level,
                    peakTemperature = kotlin.math.max(currentSessionPeakTemp, current.temperature),
                    avgPowerWatts = avgPower,
                    chargerType = session.pluggedType,
                    durationMinutes = durationMin
                )
                chargingSessionDao.insert(completedSession)
                currentChargingSessionStart = null

                logEvent(
                    title = "Charging Disconnected",
                    message = "Charged from ${session.level}% to ${current.level}% in ${durationMin}m. Peak Temp: ${completedSession.peakTemperature}°C",
                    category = "CHARGING",
                    severity = "INFO",
                    dotColor = "BLUE"
                )
            }
        }
    }

    suspend fun logEvent(
        title: String,
        message: String,
        category: String,
        severity: String = "INFO",
        dotColor: String = "GREEN"
    ) = withContext(Dispatchers.IO) {
        val log = ActivityLog(
            timestamp = System.currentTimeMillis(),
            title = title,
            message = message,
            category = category,
            severity = severity,
            dotColor = dotColor
        )
        activityLogDao.insert(log)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        batteryDao.clearAll()
        chargingSessionDao.clearAll()
        lastRecordedRecord = null
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        activityLogDao.clearAll()
    }
}
