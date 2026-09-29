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
import com.example.MainActivity
import com.example.NetraApplication
import com.example.R
import com.example.data.local.BatteryRecord
import com.example.model.BatteryTelemetry
import com.example.model.ChargerSpeed
import com.example.model.DotState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BatteryMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
                Intent.ACTION_BATTERY_CHANGED -> processBatteryChangedIntent(intent)
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
        startForeground(NOTIFICATION_ID, buildSentinelNotification(BatteryTelemetry()))

        startCollectorsAndPolling()
        startLifecycleSupervisor()
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
                processBatteryChangedIntent(initialIntent)
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

    private fun processBatteryChangedIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 50

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val pluggedType = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            else -> if (isCharging) "CHARGER" else "BATTERY"
        }

        val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val tempCelsius = if (rawTemp > 0) rawTemp / 10.0f else 32.0f

        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000)
        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val healthString = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }
        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

        // Read real-time current from BatteryManager API if supported by hardware
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        var currentMicroAmps = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        if (currentMicroAmps == Int.MIN_VALUE) currentMicroAmps = 0

        // Handle OEM units (some return microamps e.g. 850000, others return mA e.g. 850)
        val currentMa = if (kotlin.math.abs(currentMicroAmps) > 10_000) {
            currentMicroAmps / 1000
        } else {
            currentMicroAmps
        }

        // Calculate real power in Watts
        val powerWatts = (voltageMv.toFloat() * kotlin.math.abs(currentMa).toFloat()) / 1_000_000f

        // Charging speed determination
        val (chargingSpeed, speedLabel) = if (isCharging) {
            when {
                powerWatts >= 45f -> ChargerSpeed.SUPER to "Super Fast (45W+)"
                powerWatts >= 25f -> ChargerSpeed.RAPID to "Rapid Charge (25W+)"
                powerWatts >= 10f -> ChargerSpeed.FAST to "Fast Charge (15-25W)"
                powerWatts >= 5f -> ChargerSpeed.STANDARD to "Standard Charge (5-10W)"
                else -> ChargerSpeed.SLOW to "Trickle Charge (<5W)"
            }
        } else {
            ChargerSpeed.DISCHARGING to "Discharging"
        }

        // Thermal velocity calculation (°C / minute)
        val now = System.currentTimeMillis()
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
        val timeToFullMinutes = if (isCharging && batteryPct < 100) {
            val remainingPct = 100 - batteryPct
            val effectiveMa = if (currentMa > 200) currentMa else 1500
            val estimatedMinutes = (remainingPct * 45 * 60) / effectiveMa
            estimatedMinutes.coerceIn(5, 360)
        } else null

        val estimatedDischargeHours = if (!isCharging && batteryPct > 0) {
            val effectiveDrainMa = if (currentMa < -50) kotlin.math.abs(currentMa) else 350
            (batteryPct * 40f) / effectiveDrainMa
        } else 0f

        val isOverheat = tempCelsius >= 40.0f
        val isCritical = tempCelsius >= 45.0f

        val dotState = when {
            isCritical -> DotState.CRITICAL
            lifecyclePolling.isPowerSaveMode() -> DotState.THROTTLED
            !isCharging && batteryPct <= 15 -> DotState.THROTTLED
            else -> DotState.CONNECTED
        }

        // Calculate dynamic health score
        val healthScore = calculateHealthScore(tempCelsius, isCritical, healthInt)
        val healthGrade = when {
            healthScore >= 95 -> "Pristine (A+)"
            healthScore >= 90 -> "Excellent (A)"
            healthScore >= 80 -> "Good (B+)"
            healthScore >= 70 -> "Moderate (B)"
            else -> "Degraded (C)"
        }

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
            observedDischargeRatePerHour = 3.2f, // will be augmented by repository
            distanceTo40C = distanceTo40C,
            thermalVelocity = thermalVelocity,
            predictedThrottlingMinutes = predictedThrottlingMinutes,
            healthScore = healthScore,
            healthGrade = healthGrade,
            isOverheated = isOverheat,
            isCriticalOverheat = isCritical,
            serviceDotState = dotState,
            isServiceConnected = true,
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

        // Update persistent notification
        updateForegroundNotification(telemetry)

        // Safety Alerts: 80% Unplug & Critical Overheat
        checkSafetyAlerts(telemetry)

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

    private fun calculateHealthScore(temp: Float, isCritical: Boolean, healthInt: Int): Int {
        var base = 96
        if (healthInt != BatteryManager.BATTERY_HEALTH_GOOD) base -= 15
        if (temp > 45f) base -= 10
        else if (temp > 40f) base -= 4
        return base.coerceIn(40, 100)
    }

    private fun checkSafetyAlerts(telemetry: BatteryTelemetry) {
        val settings = NetraApplication.instance.settingsRepository.settings.value
        val now = System.currentTimeMillis()

        // 80% (or user target) Unplug Alert
        if (settings.unplugAlarmEnabled && telemetry.isCharging && telemetry.level >= settings.chargeTargetPercent) {
            if (!lastNotified80PercentSession) {
                lastNotified80PercentSession = true
                sendUnplugAlarmNotification(telemetry.level, settings.chargeTargetPercent)
                triggerVibrationAlert()
                serviceScope.launch {
                    NetraApplication.instance.batteryRepository.logEvent(
                        title = "Target Charge Reached (${telemetry.level}%)",
                        message = "Battery reached ${telemetry.level}%. Unplug now to preserve lithium lifespan.",
                        category = "PROTECTION",
                        severity = "WARNING",
                        dotColor = "AMBER"
                    )
                }
            }
        }

        // Critical Overheat Alarm (>45°C)
        if (telemetry.isCriticalOverheat && (now - lastOverheatAlertTime > 120_000L)) {
            lastOverheatAlertTime = now
            sendOverheatNotification(telemetry.temperature)
            triggerVibrationAlert()
            serviceScope.launch {
                NetraApplication.instance.batteryRepository.logEvent(
                    title = "CRITICAL OVERHEAT (${telemetry.temperature}°C)",
                    message = "Battery exceeded 45°C safety limit! Immediate unplug & cool-down advised.",
                    category = "THERMAL",
                    severity = "CRITICAL",
                    dotColor = "RED"
                )
            }
        } else if (telemetry.temperature >= settings.thermalWarningThreshold && (now - lastThresholdAlertTime > 180_000L)) {
            // User-defined safe temperature threshold alert
            lastThresholdAlertTime = now
            sendThermalThresholdNotification(telemetry.temperature, settings.thermalWarningThreshold)
            triggerVibrationAlert()
            serviceScope.launch {
                NetraApplication.instance.batteryRepository.logEvent(
                    title = "Thermal Safe Limit Exceeded (${telemetry.temperature}°C)",
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
            .setContentTitle("⚡ Healthy 80% Target Reached ($level%)")
            .setContentText("Target of $target% reached. Disconnect now to extend lithium lifespan by 3x.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Your battery reached the optimal $level% charge limit! Disconnecting the charger now prevents high-voltage cathode oxidation, reduces thermal dwell, and extends battery lifespan by up to 300%.")
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

    private fun buildSentinelNotification(t: BatteryTelemetry): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (t.isCharging) {
            "⚡ Netra Sentinel: ${t.level}% (Charging)"
        } else {
            "🔋 Netra Sentinel: ${t.level}% (${t.temperature}°C)"
        }

        val content = if (t.isCharging) {
            "${t.chargingSpeedLabel} • ${t.temperature}°C • ${t.voltageMv}mV"
        } else {
            "Voltage: ${t.voltageMv}mV • Power: ${String.format("%.2f", t.powerWatts)}W"
        }

        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateForegroundNotification(t: BatteryTelemetry) {
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
