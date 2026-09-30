package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NetraApplication
import com.example.ai.BatteryDegradationPredictor
import com.example.ai.ConversationalLongevityInsight
import com.example.ai.DegradationReport
import com.example.ai.FailureRiskLevel
import com.example.ai.FirebaseAiHealthService
import com.example.ai.GeminiAiService
import com.example.data.local.ActivityLog
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import com.example.data.repository.BatteryRepository
import com.example.data.repository.SentinelSettings
import com.example.data.repository.SettingsRepository
import com.example.model.AiDiagnosticResult
import com.example.model.AppUsageItem
import com.example.model.BatteryTelemetry
import com.example.model.BluetoothDeviceItem
import com.example.model.LongevityChatMessage
import com.example.model.SystemPermissionsState
import com.example.service.BatteryMonitorService
import com.example.util.BluetoothHelper
import com.example.util.PermissionHelper
import com.example.util.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.map

enum class TimeWindowFilter {
    ONE_HOUR,
    SIX_HOURS,
    TWENTY_FOUR_HOURS,
    ALL_TIME
}

class NetraViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BatteryRepository = (application as NetraApplication).batteryRepository
    private val settingsRepository: SettingsRepository = (application as NetraApplication).settingsRepository

    // Live Telemetry from 24/7 Foreground Service
    val liveTelemetry: StateFlow<BatteryTelemetry> = BatteryMonitorService.liveTelemetryFlow

    // Settings
    val settings: StateFlow<SentinelSettings> = settingsRepository.settings

    // Time window filter for graphs
    private val _selectedTimeWindow = MutableStateFlow(TimeWindowFilter.TWENTY_FOUR_HOURS)
    val selectedTimeWindow: StateFlow<TimeWindowFilter> = _selectedTimeWindow.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val graphRecords: StateFlow<List<BatteryRecord>> = _selectedTimeWindow.flatMapLatest { window ->
        val now = System.currentTimeMillis()
        val since = when (window) {
            TimeWindowFilter.ONE_HOUR -> now - 3600_000L
            TimeWindowFilter.SIX_HOURS -> now - 6 * 3600_000L
            TimeWindowFilter.TWENTY_FOUR_HOURS -> now - 24 * 3600_000L
            TimeWindowFilter.ALL_TIME -> 0L
        }
        repository.getRecordsSince(since)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 1-Hour Records for Status Screen Sparkline
    val sparkline1HourRecords: StateFlow<List<BatteryRecord>> = repository.getRecordsSince(
        System.currentTimeMillis() - 3600_000L
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All-time recent records for ML Degradation analysis
    val allRecentRecords: StateFlow<List<BatteryRecord>> = repository.recentRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Database Counters
    val totalRecordCount: StateFlow<Int> = repository.totalRecordCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val overheatCount: StateFlow<Int> = repository.overheatCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val deepDischargeCount: StateFlow<Int> = repository.deepDischargeCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Battery Calibration State
    val calibrationState = NetraApplication.instance.calibrationManager.calibrationState

    // Dynamic Power-Saving Profile State
    val powerProfileState = NetraApplication.instance.powerProfileManager.profileState

    // Central State
    val canonicalState = NetraApplication.instance.centralDataCenter.centralState

    // Charging & Discharging Lists
    val recentChargingSessions: StateFlow<List<ChargingSession>> = repository.recentChargingSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDischargeCheckpoints: StateFlow<List<BatteryRecord>> = repository.recentDischargeCheckpoints
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Activity Logs & Category Filter
    private val _selectedLogCategory = MutableStateFlow("ALL")
    val selectedLogCategory: StateFlow<String> = _selectedLogCategory.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activityLogs: StateFlow<List<ActivityLog>> = _selectedLogCategory.flatMapLatest { cat ->
        repository.getLogsByCategory(cat)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bluetooth Devices State (Streaming strictly from Central Unit)
    val bluetoothDevices: StateFlow<List<BluetoothDeviceItem>> = NetraApplication.instance.centralDataCenter.centralState
        .map { it.bluetoothDevices }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bluetoothHistory: StateFlow<List<BluetoothDeviceItem>> = NetraApplication.instance.centralDataCenter.centralState
        .map { it.bluetoothHistory }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // App Usage Drain State
    private val _appUsageDrain = MutableStateFlow<List<AppUsageItem>>(emptyList())
    val appUsageDrain: StateFlow<List<AppUsageItem>> = _appUsageDrain.asStateFlow()

    // Permissions State
    private val _systemPermissions = MutableStateFlow(
        PermissionHelper.checkAllPermissions(application)
    )
    val systemPermissions: StateFlow<SystemPermissionsState> = _systemPermissions.asStateFlow()

    // AI Diagnostics State
    private val _quickAiDiagnostic = MutableStateFlow<AiDiagnosticResult?>(null)
    val quickAiDiagnostic: StateFlow<AiDiagnosticResult?> = _quickAiDiagnostic.asStateFlow()
    private val _isQuickAiLoading = MutableStateFlow(false)
    val isQuickAiLoading: StateFlow<Boolean> = _isQuickAiLoading.asStateFlow()

    private val _deepAiDiagnostic = MutableStateFlow<AiDiagnosticResult?>(null)
    val deepAiDiagnostic: StateFlow<AiDiagnosticResult?> = _deepAiDiagnostic.asStateFlow()
    private val _isDeepAiLoading = MutableStateFlow(false)
    val isDeepAiLoading: StateFlow<Boolean> = _isDeepAiLoading.asStateFlow()

    // Machine Learning / Heuristic Degradation & Failure Prediction State
    val degradationReport: StateFlow<DegradationReport> = combine(
        allRecentRecords,
        recentChargingSessions
    ) { records, sessions ->
        val report = BatteryDegradationPredictor.analyzeDegradationAndFailureRisk(records, sessions)
        // Check and notify user via notification if failure risk is elevated
        BatteryDegradationPredictor.checkAndNotifyFailureRisk(getApplication(), report)
        report
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BatteryDegradationPredictor.analyzeDegradationAndFailureRisk(emptyList(), emptyList())
    )

    // Gemini Health Insights (Firebase AI SDK + Room Trends)
    private val _longevityInsight = MutableStateFlow<ConversationalLongevityInsight?>(null)
    val longevityInsight: StateFlow<ConversationalLongevityInsight?> = _longevityInsight.asStateFlow()
    private val _isLongevityLoading = MutableStateFlow(false)
    val isLongevityLoading: StateFlow<Boolean> = _isLongevityLoading.asStateFlow()

    // Conversational Chat Q&A State
    private val _chatMessages = MutableStateFlow<List<LongevityChatMessage>>(
        listOf(
            LongevityChatMessage(
                text = "Battery records can show observations, but cannot measure capacity health or predict battery failure.",
                isUser = false
            )
        )
    )
    val chatMessages: StateFlow<List<LongevityChatMessage>> = _chatMessages.asStateFlow()
    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    init {
        refreshHardwareState()
        // Preload initial longevity analysis in background
        generateFirebaseLongevityInsights()
    }

    fun refreshHardwareState() {
        val app = getApplication<Application>()
        _systemPermissions.value = PermissionHelper.checkAllPermissions(app)
        viewModelScope.launch(Dispatchers.IO) {
            val list = BluetoothHelper.getBluetoothDevices(app)
            NetraApplication.instance.centralDataCenter.processBluetoothDevices(list)
            _appUsageDrain.value = UsageStatsHelper.getAppUsageDrainList(app)
        }
    }

    fun setTimeWindow(window: TimeWindowFilter) {
        _selectedTimeWindow.value = window
    }

    fun setLogCategory(category: String) {
        _selectedLogCategory.value = category
    }

    fun updateChargeTarget(target: Int) {
        settingsRepository.updateChargeTarget(target)
    }

    fun updateLowBatteryThreshold(threshold: Int) {
        settingsRepository.updateLowBatteryThreshold(threshold)
    }

    fun setUnplugAlarmEnabled(enabled: Boolean) {
        settingsRepository.setUnplugAlarmEnabled(enabled)
    }

    fun setPowerSaverEnabled(enabled: Boolean) {
        settingsRepository.setPowerSaverEnabled(enabled)
    }

    fun setBrightnessOptimization(enabled: Boolean) {
        settingsRepository.setBrightnessOptimization(enabled)
    }

    fun setThermalWarningThreshold(threshold: Float) {
        settingsRepository.setThermalWarningThreshold(threshold)
    }

    // Compatibility entry points: no Android fuel-gauge calibration API is available.
    fun startCalibration() = Unit
    fun cancelCalibration() = Unit
    fun advanceCalibrationStep() = Unit

    // Power Profile Selection
    fun setPowerProfile(mode: com.example.model.PowerProfileMode) {
        NetraApplication.instance.powerProfileManager.setPowerProfile(mode, liveTelemetry.value)
        viewModelScope.launch {
            repository.logEvent(
                title = "Power Profile Updated: ${mode.title}",
                message = mode.subtitle,
                category = "SYSTEM",
                severity = "INFO",
                dotColor = "GREEN"
            )
        }
    }

    // Night Charging Throttling
    fun setNightChargingThrottleEnabled(enabled: Boolean) {
        settingsRepository.setNightChargingThrottleEnabled(enabled)
        viewModelScope.launch {
            repository.logEvent(
                title = if (enabled) "Night Thermal Throttling Armed" else "Night Throttling Disabled",
                message = if (enabled) "Charging speed will be throttled at night to limit thermal stress." else "Full charging speed permitted 24/7.",
                category = "SYSTEM",
                severity = "INFO",
                dotColor = if (enabled) "GREEN" else "AMBER"
            )
        }
    }

    fun setNightTargetWakeHour(hour: Int) {
        settingsRepository.setNightTargetWakeHour(hour)
    }

    fun setWidgetThemeColor(colorName: String) {
        settingsRepository.setWidgetThemeColor(colorName)
    }

    fun setWidgetRefreshInterval(minutes: Int) {
        settingsRepository.setWidgetRefreshInterval(minutes)
    }

    fun setWidgetBackgroundStyle(styleName: String) {
        settingsRepository.setWidgetBackgroundStyle(styleName)
    }

    // Voice Announcement Engine Controls
    fun setAnnouncementsMasterEnabled(enabled: Boolean) {
        settingsRepository.setAnnouncementsMasterEnabled(enabled)
    }

    fun setAnnouncePhoneBattery(enabled: Boolean) {
        settingsRepository.setAnnouncePhoneBattery(enabled)
    }

    fun setAnnounceBluetoothBattery(enabled: Boolean) {
        settingsRepository.setAnnounceBluetoothBattery(enabled)
    }

    fun setAnnounceChargerConnected(enabled: Boolean) {
        settingsRepository.setAnnounceChargerConnected(enabled)
    }

    fun setAnnounceChargingSpeed(enabled: Boolean) {
        settingsRepository.setAnnounceChargingSpeed(enabled)
    }

    fun setAnnounceThermalWarning(enabled: Boolean) {
        settingsRepository.setAnnounceThermalWarning(enabled)
    }

    fun setNightProtectionEnabled(enabled: Boolean) {
        settingsRepository.setNightProtectionEnabled(enabled)
    }

    fun setNightSchedule(startHour: Int, endHour: Int) {
        settingsRepository.setNightSchedule(startHour, endHour)
    }

    fun setMediaPlaybackHandlingEnabled(enabled: Boolean) {
        settingsRepository.setMediaPlaybackHandlingEnabled(enabled)
    }

    fun testVoiceAnnouncement(sampleText: String? = null) {
        val text = sampleText ?: if (liveTelemetry.value.isCharging) {
            "C ${liveTelemetry.value.level} percent"
        } else {
            "D ${liveTelemetry.value.level} percent"
        }
        NetraApplication.instance.announcementEngine.speakDirect(text)
    }

    // One-Tap Ultra Battery Saver Toggle
    fun toggleUltraBatterySaver() {
        val current = settingsRepository.settings.value.ultraBatterySaverActive
        val target = !current
        settingsRepository.setUltraBatterySaverActive(target)

        if (target) {
            // Engage ultra saver profile & 0Hz animation restriction
            NetraApplication.instance.powerProfileManager.setPowerProfile(
                com.example.model.PowerProfileMode.ULTRA_SAVER,
                liveTelemetry.value
            )
            settingsRepository.setPowerSaverEnabled(true)
        } else {
            // Restore smart adaptive profile
            NetraApplication.instance.powerProfileManager.setPowerProfile(
                com.example.model.PowerProfileMode.SMART_ADAPTIVE,
                liveTelemetry.value
            )
        }

        viewModelScope.launch {
            repository.logEvent(
                title = if (target) "⚡ Ultra Battery Saver Engaged" else "Ultra Battery Saver Disabled",
                message = if (target) "Background network sync restricted, display capped to 30%, and UI animations throttled to 0Hz." else "Restored normal background sync and full animation refresh rates.",
                category = "SYSTEM",
                severity = if (target) "WARNING" else "INFO",
                dotColor = if (target) "RED" else "GREEN"
            )
        }
    }

    // Export Telemetry to CSV
    fun exportTelemetryCsv(context: android.content.Context, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val records = repository.getRecordsSince(0L).first()
            val sessions = repository.recentChargingSessions.first()
            val logs = repository.recentLogs.first()

            val csvFile = com.example.util.BatteryCsvExporter.exportTelemetryToCsv(context, records, sessions, logs)
            if (csvFile != null) {
                com.example.util.BatteryCsvExporter.shareCsvFile(context, csvFile)
                repository.logEvent(
                    title = "Battery Telemetry Exported (CSV)",
                    message = "Exported ${records.size} telemetry points and ${sessions.size} charging sessions to ${csvFile.name}.",
                    category = "SYSTEM",
                    severity = "INFO",
                    dotColor = "BLUE"
                )
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    // AI Diagnostic Triggers
    fun runQuickAiTriage() {
        viewModelScope.launch {
            _isQuickAiLoading.value = true
            val telemetry = liveTelemetry.value
            val result = GeminiAiService.runQuickTriage(telemetry)
            _quickAiDiagnostic.value = result.getOrNull()
            _isQuickAiLoading.value = false

            repository.logEvent(
                title = "AI Quick Triage Executed",
                message = "Analysis via gemini-3.1-flash-lite: ${result.getOrNull()?.summary ?: "Complete"}",
                category = "SYSTEM",
                severity = "INFO",
                dotColor = "BLUE"
            )
        }
    }

    fun runDeepThinkingAnalysis() {
        viewModelScope.launch {
            _isDeepAiLoading.value = true
            val telemetry = liveTelemetry.value
            val records = graphRecords.value.take(20)
            val sessions = recentChargingSessions.value.take(5)

            val historySummary = if (records.isNotEmpty()) {
                records.joinToString("\n") {
                    "Time: ${it.timestamp}, Level: ${it.level}%, Temp: ${it.temperature}°C, Power: ${it.powerWatts}W, Charging: ${it.isCharging}"
                }
            } else "No long term records logged yet."

            val sessionsSummary = if (sessions.isNotEmpty()) {
                sessions.joinToString("\n") {
                    "Session: ${it.startLevel}% -> ${it.endLevel}%, Duration: ${it.durationMinutes}m, PeakTemp: ${it.peakTemperature}°C, Charger: ${it.chargerType}"
                }
            } else "No completed charging sessions recorded."

            val result = GeminiAiService.runDeepThinkingAnalysis(telemetry, historySummary, sessionsSummary)
            _deepAiDiagnostic.value = result.getOrNull()
            _isDeepAiLoading.value = false

            repository.logEvent(
                title = "AI Deep Thinking Complete",
                message = "Deep telemetry reasoning via gemini-3.1-pro (HIGH thinking). Risk: ${result.getOrNull()?.degradationRisk}",
                category = "SYSTEM",
                severity = "SUCCESS",
                dotColor = "GREEN"
            )
        }
    }

    /**
     * Triggers Firebase AI SDK to analyze Room database historical trends
     * for personalized conversational battery longevity advice.
     */
    fun generateFirebaseLongevityInsights() {
        viewModelScope.launch {
            _isLongevityLoading.value = true
            val records = allRecentRecords.value.take(50)
            val sessions = recentChargingSessions.value.take(10)
            val report = degradationReport.value

            val insight = FirebaseAiHealthService.analyzeLongevityTrends(records, sessions, report)
            _longevityInsight.value = insight
            _isLongevityLoading.value = false

            repository.logEvent(
                title = "Battery History Summary Generated",
                message = "Habit score unavailable • ${insight.title} via ${insight.aiModelUsed}",
                category = "SYSTEM",
                severity = "INFO",
                dotColor = "GREEN"
            )
        }
    }

    /**
     * Handles interactive conversational queries with Gemini & Firebase AI
     */
    fun sendConversationalQuestion(question: String) {
        if (question.isBlank()) return
        val userMsg = LongevityChatMessage(text = question.trim(), isUser = true)
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch {
            _isChatLoading.value = true
            val records = allRecentRecords.value.take(30)
            val sessions = recentChargingSessions.value.take(5)
            val report = degradationReport.value

            val answer = FirebaseAiHealthService.answerConversationalQuery(question, records, sessions, report)
            val aiMsg = LongevityChatMessage(text = answer, isUser = false)
            _chatMessages.value = _chatMessages.value + aiMsg
            _isChatLoading.value = false
        }
    }

    // Storage & Cache Stats & Control (Centralized via StorageCacheManager)
    val cacheStats: StateFlow<com.example.data.repository.CacheStorageStats> =
        NetraApplication.instance.storageCacheManager.cacheStats

    fun refreshCacheStats() {
        NetraApplication.instance.storageCacheManager.refreshCacheStats()
    }

    fun cleanCache(onResult: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val freedBytes = NetraApplication.instance.storageCacheManager.cleanCache(force = true)
            if (freedBytes > 0) {
                repository.logEvent(
                    title = "Temporary Cache Cleaned",
                    message = "Freed ${freedBytes / 1024} KB of temporary cache safely without touching user databases or settings.",
                    category = "SYSTEM",
                    severity = "INFO",
                    dotColor = "CYAN"
                )
            }
            withContext(Dispatchers.Main) {
                onResult(freedBytes)
            }
        }
    }

    /**
     * Generates and saves an automated daily PDF report from Room database telemetry
     */
    fun generateDailyPdfReport(onResult: (java.io.File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val records = allRecentRecords.value
            val sessions = recentChargingSessions.value
            val report = degradationReport.value
            val telemetry = liveTelemetry.value

            val pdfFile = com.example.util.BatteryPdfReportGenerator.generateDailyReport(
                context = getApplication(),
                records = records,
                sessions = sessions,
                degradationReport = report,
                telemetry = telemetry
            )

            if (pdfFile != null) {
                repository.logEvent(
                    title = "Daily PDF Report Generated",
                    message = "Saved to local storage: ${pdfFile.name} (${pdfFile.length() / 1024} KB)",
                    category = "SYSTEM",
                    severity = "SUCCESS",
                    dotColor = "GREEN"
                )
            }

            launch(Dispatchers.Main) {
                onResult(pdfFile)
            }
        }
    }
}
