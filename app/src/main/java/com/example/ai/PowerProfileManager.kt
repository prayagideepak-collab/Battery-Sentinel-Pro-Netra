package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BatteryTelemetry
import com.example.model.PowerProfileMode
import com.example.model.PowerProfileState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Resolves the requested profile against battery and thermal safety constraints.
 *
 * Safety constraints always override performance preferences. This class only selects app
 * workload/sync profiles; it does not claim to control CPU frequency or charging hardware.
 */
class PowerProfileManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("netra_power_profile_prefs", Context.MODE_PRIVATE)

    private var thermalOverrideActive = false
    private var lastEffectiveMode: PowerProfileMode? = null

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
            lastProfileTransitionReason = "Initial profile loaded: ${mode.title}; live safety constraints apply after telemetry arrives"
        )
    }

    fun setPowerProfile(mode: PowerProfileMode, currentTelemetry: BatteryTelemetry? = null) {
        prefs.edit().putString("selected_profile", mode.name).apply()
        evaluateProfile(mode, currentTelemetry)
    }

    /** Re-evaluates only from the canonical telemetry event path. */
    fun onTelemetryUpdate(telemetry: BatteryTelemetry) {
        evaluateProfile(_profileState.value.selectedMode, telemetry)
    }

    private fun downgradeOneTier(mode: PowerProfileMode): PowerProfileMode = when (mode) {
        PowerProfileMode.PERFORMANCE -> PowerProfileMode.BALANCED
        PowerProfileMode.SMART_ADAPTIVE, PowerProfileMode.BALANCED -> PowerProfileMode.ENDURANCE
        PowerProfileMode.ENDURANCE -> PowerProfileMode.ULTRA_SAVER
        PowerProfileMode.ULTRA_SAVER -> PowerProfileMode.ULTRA_SAVER
    }

    private fun evaluateProfile(selected: PowerProfileMode, telemetry: BatteryTelemetry?) {
        // Do not assume a missing telemetry sample means a healthy/cool device.
        val level = telemetry?.level
        val temperature = telemetry?.temperature ?: 0f
        val isCharging = telemetry?.isCharging ?: false
        val isPowerSave = telemetry?.isPowerSaverActive ?: false

        // Thermal hysteresis: enter protection at 40°C, leave only after cooling to <=38°C.
        // BatteryManager temperature can be unavailable (0); in that case do not infer overheating.
        if (temperature >= 40f || telemetry?.isCriticalOverheat == true) {
            thermalOverrideActive = true
        } else if (temperature in 0f..38f && telemetry?.isCriticalOverheat != true) {
            thermalOverrideActive = false
        }

        var effectiveMode = when {
            // Critical / survival battery levels override every manual profile, including Performance.
            level != null && level < 15 -> PowerProfileMode.ULTRA_SAVER
            level != null && level <= 30 -> PowerProfileMode.ENDURANCE
            selected == PowerProfileMode.SMART_ADAPTIVE -> PowerProfileMode.BALANCED
            else -> selected
        }

        var reason = when {
            level != null && level < 5 ->
                "Survival tier (<5%): Tier-0 only; optional work must be suspended"
            level != null && level < 15 ->
                "Critical tier (<15%): Tier-0 only; maximum batching"
            level != null && level <= 30 ->
                "Saver tier (15–30%): longer intervals and non-essential sync paused"
            selected == PowerProfileMode.SMART_ADAPTIVE && isCharging ->
                "Charging: balanced monitoring; charging alone does not enable Performance mode"
            selected == PowerProfileMode.SMART_ADAPTIVE ->
                "Normal tier (>30%): balanced monitoring with batched work"
            else -> "Manual profile requested: ${selected.title}; safety constraints still apply"
        }

        // Battery Saver adds one extra reduction unless already at the minimum profile.
        if (isPowerSave && effectiveMode != PowerProfileMode.ULTRA_SAVER) {
            effectiveMode = downgradeOneTier(effectiveMode)
            reason += "; Android Battery Saver active, reduced one tier"
        }

        // Thermal protection is higher priority than battery/performance preferences.
        if (thermalOverrideActive) {
            effectiveMode = downgradeOneTier(effectiveMode)
            reason += "; thermal protection active (>=40°C, released only at <=38°C)"
        }

        // The screen being off must never trigger a foreground UI workload. Profile selection is
        // kept separate from UI lifecycle; callers should pause rendering and non-essential UI work.
        if (telemetry?.isScreenOn == false) {
            reason += "; screen off: UI work should remain paused"
        }

        // Avoid publishing redundant state transitions from repeated identical telemetry.
        val current = _profileState.value
        val newState = PowerProfileState(
            selectedMode = selected,
            activeEffectiveMode = effectiveMode,
            dynamicSyncThrottled = effectiveMode.isAggressiveThrottle,
            adaptiveBrightnessSuggested = effectiveMode.brightnessCappedPercent,
            backgroundSyncPaused = effectiveMode == PowerProfileMode.ULTRA_SAVER,
            lastProfileTransitionReason = reason
        )
        if (lastEffectiveMode != effectiveMode || current.lastProfileTransitionReason != reason ||
            current.selectedMode != selected
        ) {
            _profileState.value = newState
            lastEffectiveMode = effectiveMode
        }
    }
}
