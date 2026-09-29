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
    val soundAlertEnabled: Boolean = true
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
            soundAlertEnabled = prefs.getBoolean("sound_enabled", true)
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
}
