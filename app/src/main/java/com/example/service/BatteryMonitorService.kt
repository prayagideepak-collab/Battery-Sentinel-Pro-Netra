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
    private var lastTemp: Float = 0f
    private var lastTempTimestamp: Long = 0L
    private var lastNotified80PercentSession = false
    private var lastOverheatAlertTime = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                processBatteryChangedIntent(intent)
            } else if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                lastNotified80PercentSession = false
            } else if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                lastNotified80PercentSession = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildSentinelNotification(BatteryTelemetry()))

        lifecyclePolling = LifecycleAwarePolling(this) { isScreenOn, isPowerSave ->
            val current = _liveTelemetryFlow.value
            _liveTelemetryFlow.value = current.copy(
                isScreenOn = isScreenOn,
                isPowerSaverActive = isPowerSave
            )
        }
        lifecyclePolling.start()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        registerReceiver(batteryReceiver, filter)

        // Initial check via sticky intent
        val initialIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (initialIntent != null) {
            processBatteryChangedIntent(initialIntent)
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
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 101, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🔋 Battery Target Reached ($level%)")
            .setContentText("Target of $target% reached. Disconnect charger to protect battery health.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
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
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_SERVICE_ID = "netra_service_channel"
        const val CHANNEL_ALERTS_ID = "netra_alerts_channel"
        const val NOTIFICATION_ID = 2001
        const val NOTIFICATION_ALARM_ID = 2002
        const val NOTIFICATION_OVERHEAT_ID = 2003

        private val _liveTelemetryFlow = MutableStateFlow(BatteryTelemetry())
        val liveTelemetryFlow: StateFlow<BatteryTelemetry> = _liveTelemetryFlow.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, BatteryMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
