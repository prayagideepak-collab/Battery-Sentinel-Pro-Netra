package com.example.service

import android.content.Context
import android.util.Log
import com.example.NetraApplication
import com.example.model.BatteryTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TelemetryHealthState {
    HEALTHY,
    DEGRADED,
    STALE,
    UNAVAILABLE,
    FAILED,
    RECOVERING
}

data class TelemetryHealthReport(
    val state: TelemetryHealthState,
    val lastUpdateTimestamp: Long,
    val isBatteryActive: Boolean,
    val isThermalActive: Boolean,
    val isBluetoothActive: Boolean,
    val message: String
)

/**
 * Telemetry & Runtime Sentinel:
 * - Supervises the existing single-source battery, thermal, and Bluetooth telemetry pipeline.
 * - Detects stale or missing telemetry, monitors collector health, and logs significant events.
 * - Ensures 24/7 background monitoring recoverability without creating duplicate polling loops or duplicate announcements.
 */
class TelemetrySentinel(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _healthReport = MutableStateFlow(
        TelemetryHealthReport(
            state = TelemetryHealthState.HEALTHY,
            lastUpdateTimestamp = System.currentTimeMillis(),
            isBatteryActive = true,
            isThermalActive = true,
            isBluetoothActive = true,
            message = "Sentinel supervising canonical telemetry pipeline."
        )
    )
    val healthReport: StateFlow<TelemetryHealthReport> = _healthReport.asStateFlow()

    private var lastTelemetryUpdateMs: Long = System.currentTimeMillis()
    private var lastLoggedState: TelemetryHealthState = TelemetryHealthState.HEALTHY
    private val staleThresholdMs: Long = 300_000L // 5 minutes

    /**
     * Called by the canonical BatteryMonitorService whenever new telemetry is received.
     */
    fun onTelemetryReceived(telemetry: BatteryTelemetry) {
        val now = System.currentTimeMillis()
        lastTelemetryUpdateMs = now

        val newState = if (telemetry.temperature <= 0f && telemetry.voltageMv <= 0) {
            TelemetryHealthState.DEGRADED
        } else {
            TelemetryHealthState.HEALTHY
        }

        updateHealthState(newState, "Canonical telemetry updated successfully. Level: ${telemetry.level}%, Temp: ${telemetry.temperature}°C")
    }

    /**
     * Periodically checked or triggered to inspect telemetry staleness.
     */
    fun checkStaleStatus() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastTelemetryUpdateMs

        if (elapsed > staleThresholdMs) {
            updateHealthState(TelemetryHealthState.STALE, "Telemetry stream is stale. Last update was ${elapsed / 1000}s ago.")
        }
    }

    private fun updateHealthState(newState: TelemetryHealthState, msg: String) {
        val current = _healthReport.value
        if (current.state != newState || current.message != msg) {
            _healthReport.value = current.copy(
                state = newState,
                lastUpdateTimestamp = System.currentTimeMillis(),
                message = msg
            )

            // Log meaningful state transitions to Room activity log (avoid spamming every cycle)
            if (newState != lastLoggedState && newState != TelemetryHealthState.HEALTHY) {
                lastLoggedState = newState
                logSentinelEventToDb(newState, msg)
            } else if (newState == TelemetryHealthState.HEALTHY && lastLoggedState != TelemetryHealthState.HEALTHY) {
                lastLoggedState = newState
                logSentinelEventToDb(newState, "Telemetry recovered to HEALTHY state.")
            }
        }
    }

    private fun logSentinelEventToDb(state: TelemetryHealthState, message: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val repository = NetraApplication.instance.batteryRepository
                val severity = when (state) {
                    TelemetryHealthState.HEALTHY -> "INFO"
                    TelemetryHealthState.DEGRADED, TelemetryHealthState.STALE -> "WARNING"
                    TelemetryHealthState.UNAVAILABLE, TelemetryHealthState.FAILED -> "CRITICAL"
                    TelemetryHealthState.RECOVERING -> "INFO"
                }
                repository.logEvent(
                    title = "Runtime Sentinel [$state]",
                    message = message,
                    category = "SENTINEL",
                    severity = severity,
                    dotColor = when (severity) {
                        "CRITICAL" -> "RED"
                        "WARNING" -> "AMBER"
                        else -> "CYAN"
                    }
                )
            } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "TelemetrySentinel"
    }
}
