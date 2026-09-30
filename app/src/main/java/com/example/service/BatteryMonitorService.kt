package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import com.example.MainActivity
import com.example.NetraApplication
import com.example.R
import com.example.data.local.BatteryRecord
import com.example.model.BatteryTelemetry
import com.example.model.CanonicalChargingSpeed
import com.example.model.NetraCentralState
import com.example.model.ChargerSpeed
import com.example.model.DotState
import com.example.model.hasCompleteLegacyReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BatteryMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // One consumer keeps broadcasts in order while the canonical reducer suspends.
    private val batteryInputs = Channel<Intent>(Channel.UNLIMITED)
    private lateinit var lifecyclePolling: LifecycleAwarePolling
    private var isReceiverRegistered = false
    private var lastTemp: Float = 0f
    private var lastTempTimestamp: Long = 0L
    private var lastNotified80PercentSession = false
    private var lastOverheatAlertTime = 0L
    private var lastThresholdAlertTime = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> batteryInputs.trySend(intent)
                Intent.ACTION_POWER_CONNECTED -> lastNotified80PercentSession = false
                Intent.ACTION_POWER_DISCONNECTED -> {
                    lastNotified80PercentSession = false
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(NOTIFICATION_ALARM_ID)
                }
                ACTION_DISMISS_ALARM -> {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(NOTIFICATION_ALARM_ID)
                }
                ACTION_SET_TARGET_100 -> {
                    NetraApplication.instance.settingsRepository.updateChargeTarget(100)
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(NOTIFICATION_ALARM_ID)
                }
                android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED,
                android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED,
                android.bluetooth.BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    checkBluetoothUpdates()
                }
            }
        }
    }

    private fun checkBluetoothUpdates() {
        serviceScope.launch {
            try {
                val devices = com.example.util.BluetoothHelper.getBluetoothDevices(this@BatteryMonitorService)
                NetraApplication.instance.announcementEngine.onBluetoothDevicesUpdate(devices)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildSentinelNotification(NetraApplication.instance.centralDataCenter.centralState.value))
        startCollectorsAndPolling()
        startLifecycleSupervisor()
        serviceScope.launch {
            for (batteryIntent in batteryInputs) {
                try {
                    processBatteryChangedIntent(batteryIntent)
                } catch (e: Exception) {
                    Log.e("BatteryMonitorService", "Battery sample processing failed", e)
                }
            }
        }
    }

    private fun startCollectorsAndPolling() {
        try {
            if (!::lifecyclePolling.isInitialized) {
                lifecyclePolling = LifecycleAwarePolling(this) { isScreenOn, isPowerSave ->
                    val current = _liveTelemetryFlow.value
                    _liveTelemetryFlow.value = current.copy(
                        isScreenOn = isScreenOn,
                        isPowerSaverActive = isPowerSave
                    )
                    if (isScreenOn) {
                        checkBluetoothUpdates()
                    }
                }
            }
            lifecyclePolling.start()
        } catch (e: Exception) {
            Log.e("BatteryMonitorService", "Error starting lifecycle polling", e)
        }

        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                    addAction(Intent.ACTION_BATTERY_LOW)
                    addAction(Intent.ACTION_BATTERY_OKAY)
                    addAction(ACTION_DISMISS_ALARM)
                    addAction(ACTION_SET_TARGET_100)
                    addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    addAction(android.bluetooth.BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(batteryReceiver, filter)
                }
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.e("BatteryMonitorService", "Error registering battery receiver", e)
            }
        }

        // Initial check via sticky intent
        try {
            val initialIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (initialIntent != null) {
                batteryInputs.trySend(initialIntent)
            }
        } catch (_: Exception) {}
    }

    private fun startLifecycleSupervisor() {
        serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(45_000L) // check every 45 seconds
                try {
                    // Ensure collectors are active and restart if stopped without spawning duplicates
                    startCollectorsAndPolling()
                } catch (e: Exception) {
                    Log.e("BatteryMonitorService", "Supervisor check failed", e)
                }
            }
        }
    }

    private suspend fun processBatteryChangedIntent(intent: Intent) {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val rawCurrent = try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        } catch (_: Exception) {
            Int.MIN_VALUE
        }
        val center = NetraApplication.instance.centralDataCenter
        center.processRawInput(
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1),
            temperatureRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
            voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            currentMicroAmps = rawCurrent,
            bluetoothConnected = null, bluetoothBattery = null
        )
        val canonical = center.centralState.value
        updateForegroundNotification(canonical)
        checkSafetyAlerts(canonical)

        // The legacy non-null model cannot encode missing fields yet. Do not publish
        // a guessed sample into its UI, announcements, or non-null Room columns.
        if (!canonical.hasCompleteLegacyReading()) {
            val unavailable = BatteryTelemetry(
                isServiceConnected = true,
                lastUpdateTimestamp = canonical.lastUpdateTimestamp
            )
            _liveTelemetryFlow.value = unavailable
            try {
                com.example.widget.NetraBatteryWidgetProvider.updateAllWidgets(this, unavailable)
                com.example.widget.NetraDegradationSparklineWidgetProvider.updateAllWidgets(this)
            } catch (e: Exception) {
                Log.w("BatteryMonitorService", "Widget refresh failed", e)
            }
            return
        }

        val batteryPct = requireNotNull(canonical.batteryLevel)

        val isCharging = requireNotNull(canonical.isCharging)

        val pluggedType = when (canonical.pluggedType) {
            com.example.model.CanonicalPluggedType.AC -> "AC"
            com.example.model.CanonicalPluggedType.USB -> "USB"
            com.example.model.CanonicalPluggedType.WIRELESS -> "WIRELESS"
            com.example.model.CanonicalPluggedType.NONE -> "BATTERY"
            else -> "UNKNOWN"
        }

        val tempCelsius = requireNotNull(canonical.temperatureCelsius)

        val voltageMv = requireNotNull(canonical.voltageMv)
        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val healthString = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unavailable"
        }
        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unavailable"

        val currentMa = requireNotNull(canonical.currentMa)
        val powerWatts = requireNotNull(canonical.powerWatts)
        val (chargingSpeed, speedLabel) = when (canonical.chargingSpeed) {
            CanonicalChargingSpeed.SLOW -> ChargerSpeed.SLOW to "Slow charging"
            CanonicalChargingSpeed.NORMAL -> ChargerSpeed.STANDARD to "Normal charging"
            CanonicalChargingSpeed.FAST -> ChargerSpeed.FAST to "Fast charging"
            CanonicalChargingSpeed.ULTRA_FAST -> ChargerSpeed.SUPER to "Ultra-fast charging"
            CanonicalChargingSpeed.UNAVAILABLE -> ChargerSpeed.UNKNOWN to "Unavailable"
        }

        // Thermal velocity calculation (°C / minute)
        val now = canonical.lastUpdateTimestamp
        var thermalVelocity = 0f
        if (lastTempTimestamp > 0 && now > lastTempTimestamp) {
            val deltaMinutes = (now - lastTempTimestamp) / 60_000f
            if (deltaMinutes > 0.1f) {
                thermalVelocity = (tempCelsius - lastTemp) / deltaMinutes
            }
        }
        lastTemp = tempCelsius
        lastTempTimestamp = now

        val distanceTo40C = 40.0f - tempCelsius
        val predictedThrottlingMinutes = if (thermalVelocity > 0.2f && distanceTo40C > 0) {
            (distanceTo40C / thermalVelocity).toInt().coerceIn(1, 120)
        } else null

        // Time to Full / Remaining discharge estimate
        val timeToFullMinutes: Int? = canonical.chargingEtaMinutes
        val estimatedDischargeHours: Float? = canonical.dischargingEtaMinutes?.let { it / 60.0f }

        val isOverheat = tempCelsius >= 40.0f
        val isCritical = tempCelsius >= 45.0f

        val dotState = when {
            isCritical -> DotState.CRITICAL
            lifecyclePolling.isPowerSaveMode() -> DotState.THROTTLED
            !isCharging && batteryPct <= 15 -> DotState.THROTTLED
            else -> DotState.CONNECTED
        }

        // Android battery-health status does not reveal capacity health or a numeric score.
        val healthScore: Int? = null
        val healthGrade = "Unavailable"

        val telemetry = BatteryTelemetry(
            level = batteryPct,
            isCharging = isCharging,
            pluggedType = pluggedType,
            temperature = tempCelsius,
            voltageMv = voltageMv,
            currentMa = currentMa,
            powerWatts = powerWatts,
            healthString = healthString,
            technology = technology,
            chargingSpeed = chargingSpeed,
            chargingSpeedLabel = speedLabel,
            timeToFullMinutes = timeToFullMinutes,
            estimatedDischargeHours = estimatedDischargeHours,
            observedDischargeRatePerHour = 0f, // historical rate not yet available in this legacy model
            distanceTo40C = distanceTo40C,
            thermalVelocity = thermalVelocity,
            predictedThrottlingMinutes = predictedThrottlingMinutes,
            healthScore = healthScore,
            healthGrade = healthGrade,
            isOverheated = isOverheat,
            isCriticalOverheat = isCritical,
            serviceDotState = dotState,
            isServiceConnected = true,
            isDataAvailable = true,
            isPowerSaverActive = lifecyclePolling.isPowerSaveMode(),
            isScreenOn = lifecyclePolling.isScreenInteractive(),
            lastUpdateTimestamp = now
        )

        _liveTelemetryFlow.value = telemetry

        // Update Home Screen Widget
        try {
            com.example.widget.NetraBatteryWidgetProvider.updateAllWidgets(this, telemetry)
        } catch (_: Exception) {}

        // Evaluate Calibration progression
        try {
            NetraApplication.instance.calibrationManager.onTelemetryUpdate(telemetry)
        } catch (_: Exception) {}

        // Evaluate Dynamic Power Profile adjustments
        try {
            NetraApplication.instance.powerProfileManager.onTelemetryUpdate(telemetry)
        } catch (_: Exception) {}

        // Evaluate Voice Announcements Engine
        try {
            NetraApplication.instance.announcementEngine.onTelemetryUpdate(telemetry)
        } catch (_: Exception) {}

        // Evaluate Telemetry & Runtime Sentinel health supervision
        try {
            NetraApplication.instance.telemetrySentinel.onTelemetryReceived(telemetry)
        } catch (_: Exception) {}

        // Persist to Room DB with intelligent debouncing
        serviceScope.launch {
            val record = BatteryRecord(
                timestamp = now,
                level = batteryPct,
                temperature = tempCelsius,
                voltageMv = voltageMv,
                currentMa = currentMa,
                powerWatts = powerWatts,
                isCharging = isCharging,
                pluggedType = pluggedType,
                healthStatus = healthString,
                screenOn = lifecyclePolling.isScreenInteractive()
            )
            NetraApplication.instance.batteryRepository.recordTelemetryDebounced(record)
        }
    }

    private fun checkSafetyAlerts(state: NetraCentralState) {
        val settings = NetraApplication.instance.settingsRepository.settings.value
        val now = System.currentTimeMillis()

        // 80% (or user target) Unplug Alert
        if (settings.unplugAlarmEnabled && state.isCharging == true && state.batteryLevel != null && state.batteryLevel >= settings.chargeTargetPercent) {
            if (!lastNotified80PercentSession) {
                lastNotified80PercentSession = true
                sendUnplugAlarmNotification(requireNotNull(state.batteryLevel), settings.chargeTargetPercent)
                triggerVibrationAlert()
                serviceScope.launch {
                    NetraApplication.instance.batteryRepository.logEvent(
                        title = "Target Charge Reached (${requireNotNull(state.batteryLevel)}%)",
                        message = "Battery reached ${requireNotNull(state.batteryLevel)}%. Unplug now to preserve lithium lifespan.",
                        category = "PROTECTION",
                        severity = "WARNING",
                        dotColor = "AMBER"
                    )
                }
            }
        }

        // Critical Overheat Alarm (>45°C)
        if (state.temperatureCelsius?.let { it >= 45f } == true && (now - lastOverheatAlertTime > 120_000L)) {
            lastOverheatAlertTime = now
            sendOverheatNotification(requireNotNull(state.temperatureCelsius))
            triggerVibrationAlert()
            serviceScope.launch {
                NetraApplication.instance.batteryRepository.logEvent(
                    title = "CRITICAL OVERHEAT (${requireNotNull(state.temperatureCelsius)}°C)",
                    message = "Battery exceeded 45°C safety limit! Immediate unplug & cool-down advised.",
                    category = "THERMAL",
                    severity = "CRITICAL",
                    dotColor = "RED"
                )
            }
        } else if (state.temperatureCelsius != null && state.temperatureCelsius >= settings.thermalWarningThreshold && (now - lastThresholdAlertTime > 180_000L)) {
            // User-defined safe temperature threshold alert
            lastThresholdAlertTime = now
            sendThermalThresholdNotification(requireNotNull(state.temperatureCelsius), settings.thermalWarningThreshold)
            triggerVibrationAlert()
            serviceScope.launch {
                NetraApplication.instance.batteryRepository.logEvent(
                    title = "Thermal Safe Limit Exceeded (${requireNotNull(state.temperatureCelsius)}°C)",
                    message = "Battery temperature exceeded user-defined safe threshold of ${settings.thermalWarningThreshold}°C.",
                    category = "THERMAL",
                    severity = "WARNING",
                    dotColor = "AMBER"
                )
            }
        }
    }

    private fun triggerVibrationAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 200, 100, 200), -1)
            }
        } catch (_: Exception) {}
    }

    private fun sendUnplugAlarmNotification(level: Int, target: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Open App Intent
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 101, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Dismiss / Mute Alarm Broadcast
        val dismissIntent = Intent(ACTION_DISMISS_ALARM)
        val pendingDismiss = PendingIntent.getBroadcast(
            this, 201, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Set Target to 100% (Continue Charging)
        val continueIntent = Intent(ACTION_SET_TARGET_100)
        val pendingContinue = PendingIntent.getBroadcast(
            this, 202, continueIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Charge target reached ($level%)")
            .setContentText("Target of $target% reached. Consider unplugging.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Battery reached the $target% target. Disconnect the charger if you want to avoid further charging.")
                    .setSummaryText("Electrochemical Longevity Recommendation")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingOpenApp)
            .addAction(R.drawable.ic_launcher_foreground, "Dismiss Alarm", pendingDismiss)
            .addAction(R.drawable.ic_launcher_foreground, "Charge to 100%", pendingContinue)
            .addAction(R.drawable.ic_launcher_foreground, "View Health", pendingOpenApp)
            .build()

        notificationManager.notify(NOTIFICATION_ALARM_ID, notification)
    }

    private fun sendOverheatNotification(temp: Float) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 102, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🔥 Critical Overheat: ${temp}°C")
            .setContentText("Battery temperature is dangerously high. Unplug immediately and allow to cool.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_OVERHEAT_ID, notification)
    }

    private fun sendThermalThresholdNotification(temp: Float, threshold: Float) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 103, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ Temperature Alert: ${temp}°C")
            .setContentText("Battery reached ${temp}°C, exceeding your safe threshold of ${threshold}°C.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_THRESHOLD_ID, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val persistentChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Netra Battery Sentinel Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "24/7 ultra-low power real-time battery & thermal telemetry status"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Battery Sentinel Safety Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical overheat warnings and 80% charge target alerts"
                enableVibration(true)
            }

            manager.createNotificationChannel(persistentChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildSentinelNotification(t: NetraCentralState): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val levelStr = t.batteryLevel?.let { "$it%" } ?: "Unavailable"
        val tempStr = t.temperatureCelsius?.let { String.format(java.util.Locale.US, "%.1f°C", it) }

        val speedText = when (t.chargingSpeed) {
            CanonicalChargingSpeed.SLOW -> "Slow"
            CanonicalChargingSpeed.NORMAL -> "Normal"
            CanonicalChargingSpeed.FAST -> "Fast"
            CanonicalChargingSpeed.ULTRA_FAST -> "Ultra Fast"
            CanonicalChargingSpeed.UNAVAILABLE -> ""
        }

        val rawPowerText = t.powerWatts?.let { String.format(java.util.Locale.US, "%.1fW", it) }
        val speedDisplay = if (rawPowerText != null && speedText.isNotEmpty()) {
            "$rawPowerText • $speedText"
        } else if (rawPowerText != null) {
            rawPowerText
        } else if (speedText.isNotEmpty()) {
            speedText
        } else {
            ""
        }

        val now = System.currentTimeMillis()
        val title: String
        val lines = mutableListOf<String>()

        if (t.isCharging == true) {
            title = "Charging • $levelStr"
            if (speedDisplay.isNotEmpty()) {
                lines.add(speedDisplay)
            }
            t.chargingEtaMinutes?.let { eta ->
                lines.add(if (eta >= 60) "ETA ${eta / 60}h ${eta % 60}m" else "ETA $eta min")
            }
            t.chargingStartedAt?.let { at ->
                val mins = ((now - at).coerceAtLeast(0L) / 60_000L)
                lines.add(if (mins >= 60) "Charging ${mins / 60}h ${mins % 60}m" else "Charging $mins min")
            }
            tempStr?.let { lines.add(it) }
        } else if (t.isChargerConnected == false || t.isCharging == false) {
            if (t.isChargerConnected == true) {
                title = "Connected, not charging • $levelStr"
                t.chargerConnectedAt?.let { at ->
                    val mins = ((now - at).coerceAtLeast(0L) / 60_000L)
                    lines.add(if (mins >= 60) "Connected ${mins / 60}h ${mins % 60}m" else "Connected $mins min")
                }
                tempStr?.let { lines.add(it) }
            } else {
                title = "Battery • $levelStr"
                val drainPower = t.consumptionPowerWatts ?: t.netPowerWatts?.let { if (it < 0) abs(it) else null } ?: t.powerWatts?.let { if (it < 0) abs(it) else null }
                drainPower?.let {
                    lines.add(String.format(java.util.Locale.US, "Drain %.1fW", it))
                }
                t.dischargingEtaMinutes?.let { eta ->
                    lines.add(if (eta >= 60) "ETA ${eta / 60}h ${eta % 60}m" else "ETA $eta min")
                }
                t.dischargingStartedAt?.let { at ->
                    val mins = ((now - at).coerceAtLeast(0L) / 60_000L)
                    lines.add(if (mins >= 60) "On battery ${mins / 60}h ${mins % 60}m" else "On battery $mins min")
                }
                tempStr?.let { lines.add(it) }
            }
        } else {
            title = "Battery • $levelStr"
            lines.add("Idle")
            tempStr?.let { lines.add(it) }
        }

        val contentText = lines.firstOrNull() ?: (tempStr ?: "Monitoring active")
        val bigText = lines.joinToString(" • ")

        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(bigText.ifEmpty { contentText })
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText.ifEmpty { contentText }))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateForegroundNotification(t: NetraCentralState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildSentinelNotification(t))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        lifecyclePolling.stop()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        isReceiverRegistered = false
        serviceScope.cancel()
        batteryInputs.close()
    }

    companion object {
        const val ACTION_DISMISS_ALARM = "com.example.ACTION_DISMISS_ALARM"
        const val ACTION_SET_TARGET_100 = "com.example.ACTION_SET_TARGET_100"
        const val CHANNEL_SERVICE_ID = "netra_service_channel"
        const val CHANNEL_ALERTS_ID = "netra_alerts_channel"
        const val NOTIFICATION_ID = 2001
        const val NOTIFICATION_ALARM_ID = 2002
        const val NOTIFICATION_OVERHEAT_ID = 2003
        const val NOTIFICATION_THRESHOLD_ID = 2004

        private val _liveTelemetryFlow = MutableStateFlow(BatteryTelemetry())
        val liveTelemetryFlow: StateFlow<BatteryTelemetry> = _liveTelemetryFlow.asStateFlow()

        fun startService(context: Context) {
            try {
                val intent = Intent(context, BatteryMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        context.startForegroundService(intent)
                    } catch (e: Exception) {
                        context.startService(intent)
                    }
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }
    }
}
