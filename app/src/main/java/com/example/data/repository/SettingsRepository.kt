package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SentinelSettings(
    val chargeTargetPercent: Int = 80,
    val lowBatteryThreshold: Int = 15,
    val unplugAlarmEnabled: Boolean = true,
    val powerSaverEnabled: Boolean = false,
    val brightnessOptimization: Boolean = true,
    val thermalWarningThreshold: Float = 40.0f,
    val criticalOverheatThreshold: Float = 45.0f,
    val notificationEnabled: Boolean = true,
    val soundAlertEnabled: Boolean = true,
    val nightChargingThrottleEnabled: Boolean = true,
    val nightTargetWakeHour: Int = 7, // 07:00 AM predicted wake
    val ultraBatterySaverActive: Boolean = false,
    val widgetThemeColor: String = "CYAN", // CYAN, EMERALD, AMBER, RED, PURPLE, MONO
    val widgetRefreshIntervalMinutes: Int = 0, // 0 = Real-time event-driven, 1, 5, 15, 30
    val widgetBackgroundStyle: String = "GLASS_DARK", // GLASS_DARK, AMOLED_BLACK, TRANSLUCENT
    // Voice Announcement Engine Settings
    val announcementsMasterEnabled: Boolean = true,
    val announcePhoneBattery: Boolean = true,
    val announceBluetoothBattery: Boolean = true,
    val announceChargerConnected: Boolean = true,
    val announceChargingSpeed: Boolean = true,
    val announceThermalWarning: Boolean = true,
    val nightProtectionEnabled: Boolean = true,
    val nightStartHour: Int = 23, // 11:00 PM
    val nightEndHour: Int = 6,    // 06:00 AM
    val mediaPlaybackHandlingEnabled: Boolean = true
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("netra_sentinel_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<SentinelSettings> = _settings.asStateFlow()

    private fun loadSettings(): SentinelSettings {
        return SentinelSettings(
            chargeTargetPercent = prefs.getInt("charge_target", 80),
            lowBatteryThreshold = prefs.getInt("low_battery_threshold", 15),
            unplugAlarmEnabled = prefs.getBoolean("unplug_alarm_enabled", true),
            powerSaverEnabled = prefs.getBoolean("power_saver_enabled", false),
            brightnessOptimization = prefs.getBoolean("brightness_opt", true),
            thermalWarningThreshold = prefs.getFloat("thermal_warning", 40.0f),
            criticalOverheatThreshold = prefs.getFloat("critical_overheat", 45.0f),
            notificationEnabled = prefs.getBoolean("notif_enabled", true),
            soundAlertEnabled = prefs.getBoolean("sound_enabled", true),
            nightChargingThrottleEnabled = prefs.getBoolean("night_charging_throttle", true),
            nightTargetWakeHour = prefs.getInt("night_target_wake_hour", 7),
            ultraBatterySaverActive = prefs.getBoolean("ultra_battery_saver", false),
            widgetThemeColor = prefs.getString("widget_theme_color", "CYAN") ?: "CYAN",
            widgetRefreshIntervalMinutes = prefs.getInt("widget_refresh_interval", 0),
            widgetBackgroundStyle = prefs.getString("widget_bg_style", "GLASS_DARK") ?: "GLASS_DARK",
            announcementsMasterEnabled = prefs.getBoolean("announcements_master", true),
            announcePhoneBattery = prefs.getBoolean("announce_phone_battery", true),
            announceBluetoothBattery = prefs.getBoolean("announce_bt_battery", true),
            announceChargerConnected = prefs.getBoolean("announce_charger_connected", true),
            announceChargingSpeed = prefs.getBoolean("announce_charging_speed", true),
            announceThermalWarning = prefs.getBoolean("announce_thermal_warning", true),
            nightProtectionEnabled = prefs.getBoolean("night_protection_enabled", true),
            nightStartHour = prefs.getInt("night_start_hour", 23),
            nightEndHour = prefs.getInt("night_end_hour", 6),
            mediaPlaybackHandlingEnabled = prefs.getBoolean("media_playback_handling", true)
        )
    }

    fun updateChargeTarget(target: Int) {
        prefs.edit().putInt("charge_target", target).apply()
        _settings.value = _settings.value.copy(chargeTargetPercent = target)
    }

    fun updateLowBatteryThreshold(threshold: Int) {
        prefs.edit().putInt("low_battery_threshold", threshold).apply()
        _settings.value = _settings.value.copy(lowBatteryThreshold = threshold)
    }

    fun setUnplugAlarmEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("unplug_alarm_enabled", enabled).apply()
        _settings.value = _settings.value.copy(unplugAlarmEnabled = enabled)
    }

    fun setPowerSaverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("power_saver_enabled", enabled).apply()
        _settings.value = _settings.value.copy(powerSaverEnabled = enabled)
    }

    fun setBrightnessOptimization(enabled: Boolean) {
        prefs.edit().putBoolean("brightness_opt", enabled).apply()
        _settings.value = _settings.value.copy(brightnessOptimization = enabled)
    }

    fun setThermalWarningThreshold(threshold: Float) {
        prefs.edit().putFloat("thermal_warning", threshold).apply()
        _settings.value = _settings.value.copy(thermalWarningThreshold = threshold)
    }

    fun setNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notif_enabled", enabled).apply()
        _settings.value = _settings.value.copy(notificationEnabled = enabled)
    }

    fun setNightChargingThrottleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("night_charging_throttle", enabled).apply()
        _settings.value = _settings.value.copy(nightChargingThrottleEnabled = enabled)
    }

    fun setNightTargetWakeHour(hour: Int) {
        prefs.edit().putInt("night_target_wake_hour", hour).apply()
        _settings.value = _settings.value.copy(nightTargetWakeHour = hour)
    }

    fun setUltraBatterySaverActive(active: Boolean) {
        prefs.edit().putBoolean("ultra_battery_saver", active).apply()
        _settings.value = _settings.value.copy(ultraBatterySaverActive = active)
    }

    fun setWidgetThemeColor(colorName: String) {
        prefs.edit().putString("widget_theme_color", colorName).apply()
        _settings.value = _settings.value.copy(widgetThemeColor = colorName)
    }

    fun setWidgetRefreshInterval(minutes: Int) {
        prefs.edit().putInt("widget_refresh_interval", minutes).apply()
        _settings.value = _settings.value.copy(widgetRefreshIntervalMinutes = minutes)
    }

    fun setWidgetBackgroundStyle(styleName: String) {
        prefs.edit().putString("widget_bg_style", styleName).apply()
        _settings.value = _settings.value.copy(widgetBackgroundStyle = styleName)
    }

    fun setAnnouncementsMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("announcements_master", enabled).apply()
        _settings.value = _settings.value.copy(announcementsMasterEnabled = enabled)
    }

    fun setAnnouncePhoneBattery(enabled: Boolean) {
        prefs.edit().putBoolean("announce_phone_battery", enabled).apply()
        _settings.value = _settings.value.copy(announcePhoneBattery = enabled)
    }

    fun setAnnounceBluetoothBattery(enabled: Boolean) {
        prefs.edit().putBoolean("announce_bt_battery", enabled).apply()
        _settings.value = _settings.value.copy(announceBluetoothBattery = enabled)
    }

    fun setAnnounceChargerConnected(enabled: Boolean) {
        prefs.edit().putBoolean("announce_charger_connected", enabled).apply()
        _settings.value = _settings.value.copy(announceChargerConnected = enabled)
    }

    fun setAnnounceChargingSpeed(enabled: Boolean) {
        prefs.edit().putBoolean("announce_charging_speed", enabled).apply()
        _settings.value = _settings.value.copy(announceChargingSpeed = enabled)
    }

    fun setAnnounceThermalWarning(enabled: Boolean) {
        prefs.edit().putBoolean("announce_thermal_warning", enabled).apply()
        _settings.value = _settings.value.copy(announceThermalWarning = enabled)
    }

    fun setNightProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("night_protection_enabled", enabled).apply()
        _settings.value = _settings.value.copy(nightProtectionEnabled = enabled)
    }

    fun setNightSchedule(startHour: Int, endHour: Int) {
        prefs.edit().putInt("night_start_hour", startHour).putInt("night_end_hour", endHour).apply()
        _settings.value = _settings.value.copy(nightStartHour = startHour, nightEndHour = endHour)
    }

    fun setMediaPlaybackHandlingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("media_playback_handling", enabled).apply()
        _settings.value = _settings.value.copy(mediaPlaybackHandlingEnabled = enabled)
    }
}
