package com.example.service

import android.app.AppOpsManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType

class CentralCapabilityRegistry(private val context: Context) {

    // Fast memory cache for static hardware and OS capabilities that never change at runtime
    private val staticHardwareFeatures = mutableMapOf<CapabilityType, CapabilityStatus>()

    init {
        detectStaticCapabilities()
    }

    private fun detectStaticCapabilities() {
        val pm = context.packageManager

        // Bluetooth Hardware
        val btAdapter = try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        } catch (_: Exception) { null }
        val hasBtHardware = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) || btAdapter != null
        staticHardwareFeatures[CapabilityType.BLUETOOTH_HARDWARE] = if (hasBtHardware) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED

        // Bluetooth LE
        staticHardwareFeatures[CapabilityType.BLUETOOTH_LE] = if (pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            CapabilityStatus.SUPPORTED
        } else {
            CapabilityStatus.UNSUPPORTED
        }

        // Text-to-Speech
        val ttsIntent = Intent("android.intent.action.TTS_SERVICE")
        val ttsEngines = try {
            pm.queryIntentServices(ttsIntent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Exception) { emptyList() }
        staticHardwareFeatures[CapabilityType.TEXT_TO_SPEECH] = if (ttsEngines.isNotEmpty()) CapabilityStatus.AVAILABLE else CapabilityStatus.UNSUPPORTED

        // Media Playback Control
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        staticHardwareFeatures[CapabilityType.MEDIA_PLAYBACK_CONTROL] = if (am != null) CapabilityStatus.AVAILABLE else CapabilityStatus.UNSUPPORTED

        // Storage & Cache Operations
        staticHardwareFeatures[CapabilityType.STORAGE_CACHE_OPERATIONS] = if (context.cacheDir.canWrite()) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE

        // Background Monitoring
        staticHardwareFeatures[CapabilityType.BACKGROUND_MONITORING] = CapabilityStatus.AVAILABLE

        // Battery Health Status
        staticHardwareFeatures[CapabilityType.BATTERY_HEALTH_STATUS] = CapabilityStatus.AVAILABLE

        // Power Save Mode Detection support
        staticHardwareFeatures[CapabilityType.POWER_SAVE_MODE] = CapabilityStatus.AVAILABLE
    }

    fun detectAllCapabilities(
        currentMicroAmps: Int = 0,
        temperatureRaw: Int = 0,
        voltageRaw: Int = 0,
        hasBluetoothHardware: Boolean? = null,
        hasBluetoothPermission: Boolean? = null,
        isBluetoothEnabled: Boolean? = null,
        connectedBluetoothCount: Int = 0
    ): Map<CapabilityType, CapabilityStatus> {
        val map = mutableMapOf<CapabilityType, CapabilityStatus>()

        // 1. Battery Telemetry (Core)
        map[CapabilityType.BATTERY_TELEMETRY] = CapabilityStatus.AVAILABLE

        // 2. Battery Temperature
        map[CapabilityType.BATTERY_TEMPERATURE] = if (temperatureRaw > 0) {
            CapabilityStatus.AVAILABLE
        } else {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE
        }

        // 3. Battery Voltage
        map[CapabilityType.BATTERY_VOLTAGE] = if (voltageRaw > 0) {
            CapabilityStatus.AVAILABLE
        } else {
            CapabilityStatus.AVAILABLE
        }

        // 4. Battery Current (OEM restricted on certain hardware)
        map[CapabilityType.BATTERY_CURRENT] = if (currentMicroAmps != Int.MIN_VALUE && currentMicroAmps != 0) {
            CapabilityStatus.AVAILABLE
        } else {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val currentNow = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
            if (currentNow != Int.MIN_VALUE && currentNow != 0) {
                CapabilityStatus.AVAILABLE
            } else {
                CapabilityStatus.UNAVAILABLE // OEM restricted
            }
        }

        // 5. Battery Power Calculation
        map[CapabilityType.BATTERY_POWER_CALCULATION] = if (map[CapabilityType.BATTERY_CURRENT] == CapabilityStatus.AVAILABLE) {
            CapabilityStatus.AVAILABLE
        } else {
            CapabilityStatus.UNAVAILABLE
        }

        // 6. Charging State
        map[CapabilityType.CHARGING_STATE] = CapabilityStatus.AVAILABLE

        // 7. Charger Connection State
        map[CapabilityType.CHARGER_CONNECTION_STATE] = CapabilityStatus.AVAILABLE

        // 8. Bluetooth Hardware
        val hasBtHardware = hasBluetoothHardware ?: (staticHardwareFeatures[CapabilityType.BLUETOOTH_HARDWARE] == CapabilityStatus.SUPPORTED)
        map[CapabilityType.BLUETOOTH_HARDWARE] = if (hasBtHardware) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED

        // 9. Bluetooth Connected Info
        val btAdapter = try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        } catch (_: Exception) { null }
        val hasBtPerm = hasBluetoothPermission ?: checkBluetoothPermission()
        val btEnabled = isBluetoothEnabled ?: (btAdapter?.isEnabled == true)

        map[CapabilityType.BLUETOOTH_CONNECTED_INFO] = when {
            !hasBtHardware -> CapabilityStatus.UNSUPPORTED
            !hasBtPerm -> CapabilityStatus.PERMISSION_REQUIRED
            !btEnabled -> CapabilityStatus.DISABLED
            connectedBluetoothCount > 0 -> CapabilityStatus.AVAILABLE
            else -> CapabilityStatus.SUPPORTED // Enabled, permission granted, 0 connected
        }

        // 10. Bluetooth Battery Level
        map[CapabilityType.BLUETOOTH_BATTERY_LEVEL] = when {
            !hasBtHardware -> CapabilityStatus.UNSUPPORTED
            !hasBtPerm -> CapabilityStatus.PERMISSION_REQUIRED
            !btEnabled -> CapabilityStatus.DISABLED
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> CapabilityStatus.SUPPORTED
            else -> CapabilityStatus.UNSUPPORTED
        }

        // 11. Notifications
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        map[CapabilityType.NOTIFICATIONS] = if (notifGranted) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED

        // 12. Text-to-Speech (from fast static cache)
        map[CapabilityType.TEXT_TO_SPEECH] = staticHardwareFeatures[CapabilityType.TEXT_TO_SPEECH] ?: CapabilityStatus.UNSUPPORTED

        // 13. Media Playback Control (from fast static cache)
        map[CapabilityType.MEDIA_PLAYBACK_CONTROL] = staticHardwareFeatures[CapabilityType.MEDIA_PLAYBACK_CONTROL] ?: CapabilityStatus.AVAILABLE

        // 14. Usage Access (App battery drain stats)
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val mode = appOps?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                it.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
        } ?: AppOpsManager.MODE_DEFAULT
        map[CapabilityType.USAGE_ACCESS] = if (mode == AppOpsManager.MODE_ALLOWED) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED

        // 15. Background Monitoring
        map[CapabilityType.BACKGROUND_MONITORING] = CapabilityStatus.AVAILABLE

        // 16. Storage & Cache Operations
        map[CapabilityType.STORAGE_CACHE_OPERATIONS] = staticHardwareFeatures[CapabilityType.STORAGE_CACHE_OPERATIONS] ?: CapabilityStatus.AVAILABLE

        // 17. Battery Charge Counter
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val chargeCounter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
        map[CapabilityType.BATTERY_CHARGE_COUNTER] = if (chargeCounter != Int.MIN_VALUE && chargeCounter > 0) {
            CapabilityStatus.AVAILABLE
        } else {
            CapabilityStatus.UNAVAILABLE
        }

        // 18. Battery Health Status
        map[CapabilityType.BATTERY_HEALTH_STATUS] = CapabilityStatus.AVAILABLE

        // 19. Charging Speed Calculation
        map[CapabilityType.CHARGING_SPEED_CALCULATION] = if (map[CapabilityType.BATTERY_CURRENT] == CapabilityStatus.AVAILABLE) {
            CapabilityStatus.AVAILABLE
        } else {
            CapabilityStatus.UNAVAILABLE
        }

        // 20. Fast Charging Detection
        map[CapabilityType.FAST_CHARGING_DETECTION] = if (map[CapabilityType.BATTERY_CURRENT] == CapabilityStatus.AVAILABLE) {
            CapabilityStatus.AVAILABLE
        } else {
            CapabilityStatus.UNAVAILABLE
        }

        // 21. Bluetooth LE (Low Energy)
        map[CapabilityType.BLUETOOTH_LE] = staticHardwareFeatures[CapabilityType.BLUETOOTH_LE] ?: CapabilityStatus.UNSUPPORTED

        // 22. Exact Alarm
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
        map[CapabilityType.EXACT_ALARM] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmMgr?.canScheduleExactAlarms() == true) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED
        } else {
            CapabilityStatus.AVAILABLE
        }

        // 23. Power Save Mode Detection
        map[CapabilityType.POWER_SAVE_MODE] = CapabilityStatus.AVAILABLE

        // 24. Battery Optimization Whitelist Detection
        val powerMgr = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        map[CapabilityType.BATTERY_OPTIMIZATION_WHITELIST] = if (powerMgr?.isIgnoringBatteryOptimizations(context.packageName) == true) {
            CapabilityStatus.AVAILABLE
        } else {
            CapabilityStatus.PERMISSION_REQUIRED
        }

        // 25. Brightness Control Capability
        map[CapabilityType.BRIGHTNESS_CONTROL] = if (android.provider.Settings.System.canWrite(context)) {
            CapabilityStatus.AVAILABLE
        } else {
            CapabilityStatus.PERMISSION_REQUIRED
        }

        return map
    }

    fun getCapabilityLabel(type: CapabilityType): String = when (type) {
        CapabilityType.BATTERY_TELEMETRY -> "Battery Telemetry Core"
        CapabilityType.BATTERY_TEMPERATURE -> "Battery Temperature Sensor"
        CapabilityType.BATTERY_VOLTAGE -> "Hardware Voltage Sensor"
        CapabilityType.BATTERY_CURRENT -> "Instantaneous Current Sensor"
        CapabilityType.BATTERY_CHARGE_COUNTER -> "Hardware Charge Counter"
        CapabilityType.BATTERY_HEALTH_STATUS -> "Battery Health Diagnostics"
        CapabilityType.BATTERY_POWER_CALCULATION -> "Active Power Calculation"
        CapabilityType.CHARGING_SPEED_CALCULATION -> "Charging Speed Classification"
        CapabilityType.FAST_CHARGING_DETECTION -> "Fast Charging Detection"
        CapabilityType.CHARGING_STATE -> "Charging State Sensing"
        CapabilityType.CHARGER_CONNECTION_STATE -> "Charger Connection Sensing"
        CapabilityType.BLUETOOTH_HARDWARE -> "Bluetooth Adapter Hardware"
        CapabilityType.BLUETOOTH_LE -> "Bluetooth Low Energy (BLE)"
        CapabilityType.BLUETOOTH_CONNECTED_INFO -> "Connected Peripheral Telemetry"
        CapabilityType.BLUETOOTH_BATTERY_LEVEL -> "Bluetooth Battery Service (BAS)"
        CapabilityType.NOTIFICATIONS -> "System Notification Alerts"
        CapabilityType.EXACT_ALARM -> "Exact Alarms Scheduling"
        CapabilityType.POWER_SAVE_MODE -> "Android Power Saver Detection"
        CapabilityType.BATTERY_OPTIMIZATION_WHITELIST -> "Battery Optimization Exemption"
        CapabilityType.TEXT_TO_SPEECH -> "Voice Announcements (TTS Engine)"
        CapabilityType.MEDIA_PLAYBACK_CONTROL -> "Audio Focus & Media Control"
        CapabilityType.USAGE_ACCESS -> "App Battery Drain Attribution"
        CapabilityType.BACKGROUND_MONITORING -> "24/7 Autonomous Background Service"
        CapabilityType.STORAGE_CACHE_OPERATIONS -> "Local Storage & Cache Manager"
        CapabilityType.BRIGHTNESS_CONTROL -> "Adaptive Brightness & Thermal Control"
    }

    private fun checkBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }
}
