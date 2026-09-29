package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BatteryTelemetry
import com.example.model.PowerProfileMode
import com.example.model.PowerProfileState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PowerProfileManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("netra_power_profile_prefs", Context.MODE_PRIVATE)

    private val _profileState = MutableStateFlow(loadProfileState())
    val profileState: StateFlow<PowerProfileState> = _profileState.asStateFlow()

    private fun loadProfileState(): PowerProfileState {
        val modeName = prefs.getString("selected_profile", PowerProfileMode.SMART_ADAPTIVE.name)
        val mode = try {
            PowerProfileMode.valueOf(modeName ?: PowerProfileMode.SMART_ADAPTIVE.name)
        } catch (_: Exception) {
            PowerProfileMode.SMART_ADAPTIVE
        }

        return PowerProfileState(
            selectedMode = mode,
            activeEffectiveMode = if (mode == PowerProfileMode.SMART_ADAPTIVE) PowerProfileMode.BALANCED else mode,
            dynamicSyncThrottled = mode.isAggressiveThrottle,
            adaptiveBrightnessSuggested = mode.brightnessCappedPercent,
            backgroundSyncPaused = mode == PowerProfileMode.ULTRA_SAVER,
            lastProfileTransitionReason = "Initial profile loaded: ${mode.title}"
        )
    }

    fun setPowerProfile(mode: PowerProfileMode, currentTelemetry: BatteryTelemetry? = null) {
        prefs.edit().putString("selected_profile", mode.name).apply()
        evaluateProfile(mode, currentTelemetry)
    }

    /**
     * Dynamically adjusts power profile, background sync interval,
     * and screen brightness targets based on real-time battery level thresholds.
     */
    fun onTelemetryUpdate(telemetry: BatteryTelemetry) {
        val selected = _profileState.value.selectedMode
        evaluateProfile(selected, telemetry)
    }

    private fun evaluateProfile(selected: PowerProfileMode, telemetry: BatteryTelemetry?) {
        val level = telemetry?.level ?: 80
        val isCharging = telemetry?.isCharging ?: false

        val effectiveMode: PowerProfileMode
        val reason: String

        if (selected == PowerProfileMode.SMART_ADAPTIVE) {
            when {
                isCharging -> {
                    effectiveMode = PowerProfileMode.PERFORMANCE
                    reason = "Charger connected: Full sampling rate enabled (15s sync)"
                }
                level <= 10 -> {
                    effectiveMode = PowerProfileMode.ULTRA_SAVER
                    reason = "Critical battery (≤10%): Ultra power preservation active (900s sync, 30% brightness cap)"
                }
                level <= 20 -> {
                    effectiveMode = PowerProfileMode.ENDURANCE
                    reason = "Low battery (≤20%): Endurance mode engaged (300s sync, 60% brightness cap)"
                }
                level <= 45 -> {
                    effectiveMode = PowerProfileMode.BALANCED
                    reason = "Moderate battery (≤45%): Balanced power optimization active"
                }
                else -> {
                    effectiveMode = PowerProfileMode.BALANCED
                    reason = "Normal battery (>45%): Standard balanced operation"
                }
            }
        } else {
            effectiveMode = selected
            reason = "Manual override profile: ${selected.title}"
        }

        val newState = PowerProfileState(
            selectedMode = selected,
            activeEffectiveMode = effectiveMode,
            dynamicSyncThrottled = effectiveMode.isAggressiveThrottle,
            adaptiveBrightnessSuggested = effectiveMode.brightnessCappedPercent,
            backgroundSyncPaused = effectiveMode == PowerProfileMode.ULTRA_SAVER,
            lastProfileTransitionReason = reason
        )

        _profileState.value = newState
    }
}
