package com.example.ai

import android.content.Context
import com.example.model.BatteryTelemetry
import com.example.model.CalibrationSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android battery samples provide no fuel-gauge calibration or capacity measurement API. */
@Suppress("UNUSED_PARAMETER")
class BatteryCalibrationManager(context: Context) {
    // Do not load the old wizard's fabricated capacity/accuracy preferences.
    private val _calibrationState = MutableStateFlow(CalibrationSessionState())
    val calibrationState: StateFlow<CalibrationSessionState> = _calibrationState.asStateFlow()

    fun startCalibration(currentLevel: Int) = Unit
    fun cancelCalibration() = Unit
    fun manuallyAdvanceStep() = Unit
    fun onTelemetryUpdate(telemetry: BatteryTelemetry) = Unit
}
