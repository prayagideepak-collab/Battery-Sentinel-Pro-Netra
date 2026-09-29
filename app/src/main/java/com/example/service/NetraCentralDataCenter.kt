package com.example.service

import android.os.BatteryManager
import com.example.model.CanonicalChargingSpeed
import com.example.model.CanonicalPluggedType
import com.example.model.NetraCentralEvent
import com.example.model.NetraCentralState
import com.example.model.NetraEventType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

class NetraCentralDataCenter {

    private val mutex = Mutex()

    private val _centralState = MutableStateFlow(NetraCentralState())
    val centralState: StateFlow<NetraCentralState> = _centralState.asStateFlow()

    private val _centralEvents = MutableSharedFlow<NetraCentralEvent>(replay = 50, extraBufferCapacity = 64)
    val centralEvents: SharedFlow<NetraCentralEvent> = _centralEvents.asSharedFlow()

    // Tracking for deduplication
    private var lastConnectedState: Boolean? = null
    private var lastChargingState: Boolean? = null
    private var lastSpeedCategory: CanonicalChargingSpeed? = null
    private var lastBatteryLevelBoundary: Int? = null

    suspend fun processRawInput(
        level: Int,
        scale: Int,
        status: Int,
        plugged: Int,
        temperatureRaw: Int,
        voltage: Int,
        currentMicroAmps: Int,
        bluetoothConnected: Boolean?,
        bluetoothBattery: Int?,
        source: String = "BatteryMonitorService"
    ) {
        mutex.withLock {
            val now = System.currentTimeMillis()

            // 1. Validation & Normalization (No fake fallbacks)
            val validatedLevel = if (level >= 0 && scale > 0) (level * 100) / scale else null
            val isCharging = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_STATUS_FULL -> true
                BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                else -> null
            }

            val isConnected = when {
                plugged != 0 && plugged != -1 -> true
                isCharging == true -> true
                status == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                else -> null
            }

            val pluggedType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> CanonicalPluggedType.AC
                BatteryManager.BATTERY_PLUGGED_USB -> CanonicalPluggedType.USB
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> CanonicalPluggedType.WIRELESS
                else -> if (isCharging == true) CanonicalPluggedType.OTHER else CanonicalPluggedType.NONE
            }

            val tempCelsius = if (temperatureRaw > 0) temperatureRaw / 10.0f else null
            val voltageMv = if (voltage > 0) voltage else null

            val currentMa = if (currentMicroAmps != Int.MIN_VALUE && currentMicroAmps != 0) {
                if (abs(currentMicroAmps) > 10_000) currentMicroAmps / 1000 else currentMicroAmps
            } else null

            val powerWatts = if (voltageMv != null && currentMa != null) {
                (voltageMv.toFloat() * abs(currentMa).toFloat()) / 1_000_000f
            } else null

            // User defined charging speed rule:
            // < 5W = Slow, 5W to <10W = Normal, 10W to 20W = Fast, >20W = Ultra Fast
            val speedCategory = if (isCharging == true && powerWatts != null) {
                when {
                    powerWatts > 20f -> CanonicalChargingSpeed.ULTRA_FAST
                    powerWatts >= 10f -> CanonicalChargingSpeed.FAST
                    powerWatts >= 5f -> CanonicalChargingSpeed.NORMAL
                    else -> CanonicalChargingSpeed.SLOW
                }
            } else if (isCharging == false) {
                CanonicalChargingSpeed.UNAVAILABLE
            } else {
                CanonicalChargingSpeed.UNAVAILABLE
            }

            val newState = NetraCentralState(
                batteryLevel = validatedLevel,
                isCharging = isCharging,
                isChargerConnected = isConnected,
                pluggedType = pluggedType,
                temperatureCelsius = tempCelsius,
                voltageMv = voltageMv,
                currentMa = currentMa,
                powerWatts = powerWatts,
                chargingSpeed = speedCategory,
                bluetoothConnected = bluetoothConnected,
                bluetoothBatteryPercent = bluetoothBattery,
                lastUpdateTimestamp = now,
                isDataFresh = true
            )

            _centralState.value = newState

            // 2. Deduplication & Event Generation
            if (isConnected != null && isConnected != lastConnectedState) {
                lastConnectedState = isConnected
                val eventType = if (isConnected) NetraEventType.CHARGER_CONNECTED else NetraEventType.CHARGER_DISCONNECTED
                val event = NetraCentralEvent(
                    eventId = "event_${eventType}_$now",
                    eventType = eventType,
                    timestamp = now,
                    previousValue = (!isConnected).toString(),
                    newValue = isConnected.toString(),
                    source = source
                )
                _centralEvents.emit(event)
            }

            if (isCharging != null && isCharging != lastChargingState) {
                lastChargingState = isCharging
                val eventType = when {
                    isCharging -> NetraEventType.CHARGING_STARTED
                    else -> NetraEventType.CHARGING_STOPPED
                }
                val event = NetraCentralEvent(
                    eventId = "event_${eventType}_$now",
                    eventType = eventType,
                    timestamp = now,
                    previousValue = (!isCharging).toString(),
                    newValue = isCharging.toString(),
                    source = source
                )
                _centralEvents.emit(event)
            }

            if (speedCategory != lastSpeedCategory && speedCategory != CanonicalChargingSpeed.UNAVAILABLE) {
                val prev = lastSpeedCategory?.name ?: "UNKNOWN"
                lastSpeedCategory = speedCategory
                val event = NetraCentralEvent(
                    eventId = "event_speed_change_$now",
                    eventType = NetraEventType.SPEED_CHANGED,
                    timestamp = now,
                    previousValue = prev,
                    newValue = speedCategory.name,
                    source = source
                )
                _centralEvents.emit(event)
            }

            if (validatedLevel != null) {
                val boundary = (validatedLevel / 5) * 5
                if (lastBatteryLevelBoundary == null || lastBatteryLevelBoundary != boundary) {
                    lastBatteryLevelBoundary = boundary
                    val event = NetraCentralEvent(
                        eventId = "event_battery_boundary_${boundary}_$now",
                        eventType = NetraEventType.BATTERY_LEVEL_CROSSED,
                        timestamp = now,
                        previousValue = lastBatteryLevelBoundary?.toString(),
                        newValue = boundary.toString(),
                        source = source
                    )
                    _centralEvents.emit(event)
                }
            }
        }
    }
}
