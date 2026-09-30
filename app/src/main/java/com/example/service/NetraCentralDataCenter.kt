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
    private val chargingSpeedEngine = ChargingSpeedEngine()

    private val _centralState = MutableStateFlow(NetraCentralState())
    val centralState: StateFlow<NetraCentralState> = _centralState.asStateFlow()

    private val _centralEvents = MutableSharedFlow<NetraCentralEvent>(extraBufferCapacity = 64)
    val centralEvents: SharedFlow<NetraCentralEvent> = _centralEvents.asSharedFlow()

    // Tracking for deduplication & sessions
    private var lastConnectedState: Boolean? = null
    private var lastChargingState: Boolean? = null
    private var lastSpeedCategory: CanonicalChargingSpeed? = null
    private var lastBatteryLevelBoundary: Int? = null

    private var chargerConnectedAt: Long? = null
    private var chargingStartedAt: Long? = null
    private var chargingStoppedAt: Long? = null
    private var chargerDisconnectedAt: Long? = null
    private var dischargingStartedAt: Long? = null
    private var lastDischargingStatus = false

    // Historical samples for live ETA calculation
    private data class LevelSample(val level: Int, val timestamp: Long)
    private val levelSamples = mutableListOf<LevelSample>()

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
            val validatedLevel = if (level in 0..scale && scale > 0) (level.toLong() * 100 / scale).toInt() else null
            val isCharging = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_STATUS_FULL -> true
                BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                else -> null
            }

            val isConnected = when {
                plugged == 0 -> false
                plugged > 0 -> true
                isCharging == true -> true
                else -> null
            }

            val pluggedType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> CanonicalPluggedType.AC
                BatteryManager.BATTERY_PLUGGED_USB -> CanonicalPluggedType.USB
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> CanonicalPluggedType.WIRELESS
                0 -> CanonicalPluggedType.NONE
                else -> if (isCharging == true) CanonicalPluggedType.OTHER else CanonicalPluggedType.UNKNOWN
            }

            val tempCelsius = if (temperatureRaw > 0) temperatureRaw / 10.0f else null
            val voltageMv = if (voltage > 0) voltage else null

            // BatteryManager.BATTERY_PROPERTY_CURRENT_NOW is specified in microamps.
            val currentMa = if (currentMicroAmps != Int.MIN_VALUE && currentMicroAmps != 0) {
                currentMicroAmps / 1000
            } else null

            // Central ChargingSpeedEngine calculation
            val speedResult = chargingSpeedEngine.calculate(isCharging, voltageMv, currentMa)
            val rawPowerWatts = speedResult.rawPowerWatts
            val netPowerWatts = speedResult.netPowerWatts
            val consumptionWatts = speedResult.consumptionPowerWatts
            val speedCategory = speedResult.speedCategory
            val announcementCategory = speedResult.announcementCategory

            // Session Timestamp tracking
            if (isConnected != null && isConnected != lastConnectedState) {
                if (isConnected) {
                    chargerConnectedAt = now
                    chargerDisconnectedAt = null
                } else {
                    chargerDisconnectedAt = now
                    chargerConnectedAt = null
                    chargingStartedAt = null
                    chargingStoppedAt = null
                }
            }
            if (isCharging != null && isCharging != lastChargingState) {
                if (isCharging) {
                    chargingStartedAt = now
                    chargingStoppedAt = null
                    dischargingStartedAt = null
                } else {
                    chargingStoppedAt = now
                }
            }

            val isDischarging = status == BatteryManager.BATTERY_STATUS_DISCHARGING
            val wasDischarging = lastDischargingStatus
            if (isDischarging && !wasDischarging) {
                dischargingStartedAt = now
            }
            lastDischargingStatus = isDischarging

            // Live ETA calculation based on observed progression over time
            if (validatedLevel != null) {
                levelSamples.add(LevelSample(validatedLevel, now))
                if (levelSamples.size > 20) {
                    levelSamples.removeAt(0)
                }
            }

            var chargingEta: Int? = null
            var dischargingEta: Int? = null

            if (levelSamples.size >= 2) {
                val oldest = levelSamples.first()
                val newest = levelSamples.last()
                val timeDiffMinutes = (newest.timestamp - oldest.timestamp) / 60_000f
                val levelDiff = newest.level - oldest.level

                if (timeDiffMinutes >= 2.0f && abs(levelDiff) >= 1) {
                    val ratePerMinute = levelDiff / timeDiffMinutes
                    if (isCharging == true && ratePerMinute > 0.01f && validatedLevel != null && validatedLevel < 100) {
                        val remainingPct = 100 - validatedLevel
                        chargingEta = (remainingPct / ratePerMinute).toInt().coerceIn(1, 720)
                    } else if (isCharging == false && ratePerMinute < -0.005f && validatedLevel != null && validatedLevel > 0) {
                        dischargingEta = (abs(validatedLevel / ratePerMinute)).toInt().coerceIn(1, 1440)
                    }
                }
            }

            val newState = NetraCentralState(
                batteryLevel = validatedLevel,
                isCharging = isCharging,
                isChargerConnected = isConnected,
                chargerConnectedAt = chargerConnectedAt,
                chargingStartedAt = chargingStartedAt,
                chargingStoppedAt = chargingStoppedAt,
                chargerDisconnectedAt = chargerDisconnectedAt,
                dischargingStartedAt = dischargingStartedAt,
                pluggedType = pluggedType,
                temperatureCelsius = tempCelsius,
                voltageMv = voltageMv,
                currentMa = currentMa,
                powerWatts = rawPowerWatts,
                netPowerWatts = netPowerWatts,
                consumptionPowerWatts = consumptionWatts,
                chargingSpeed = speedCategory,
                announcementSpeed = announcementCategory,
                bluetoothConnected = bluetoothConnected,
                bluetoothBatteryPercent = bluetoothBattery,
                lastUpdateTimestamp = now,
                isDataFresh = true,
                chargingEtaMinutes = chargingEta,
                dischargingEtaMinutes = dischargingEta
            )

            _centralState.value = newState

            // 2. Deduplication & Event Generation
            if (isConnected != null && isConnected != lastConnectedState) {
                val previousConnectedState = lastConnectedState
                lastConnectedState = isConnected
                val eventType = if (isConnected) NetraEventType.CHARGER_CONNECTED else NetraEventType.CHARGER_DISCONNECTED
                val event = NetraCentralEvent(
                    eventId = "event_${eventType}_$now",
                    eventType = eventType,
                    timestamp = now,
                    previousValue = previousConnectedState?.toString(),
                    newValue = isConnected.toString(),
                    source = source
                )
                _centralEvents.emit(event)
            }

            if (isCharging != null && isCharging != lastChargingState) {
                val previousChargingState = lastChargingState
                lastChargingState = isCharging
                val eventType = if (isCharging) {
                    NetraEventType.CHARGING_STARTED
                } else {
                    NetraEventType.CHARGING_STOPPED
                }
                _centralEvents.emit(
                    NetraCentralEvent(
                        eventId = "event_${eventType}_$now",
                        eventType = eventType,
                        timestamp = now,
                        previousValue = previousChargingState?.toString(),
                        newValue = isCharging.toString(),
                        source = source
                    )
                )
            }

            if (isDischarging && !wasDischarging) {
                _centralEvents.emit(
                    NetraCentralEvent(
                        eventId = "event_DISCHARGING_STARTED_$now",
                        eventType = NetraEventType.DISCHARGING_STARTED,
                        timestamp = now,
                        previousValue = wasDischarging.toString(),
                        newValue = isDischarging.toString(),
                        source = source
                    )
                )
            }

            if (speedCategory == CanonicalChargingSpeed.UNAVAILABLE) {
                lastSpeedCategory = speedCategory
            } else if (speedCategory != lastSpeedCategory) {
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
                if (lastBatteryLevelBoundary != boundary) {
                    val previousBoundary = lastBatteryLevelBoundary
                    lastBatteryLevelBoundary = boundary
                    val event = NetraCentralEvent(
                        eventId = "event_battery_boundary_${boundary}_$now",
                        eventType = NetraEventType.BATTERY_LEVEL_CROSSED,
                        timestamp = now,
                        previousValue = previousBoundary?.toString(),
                        newValue = boundary.toString(),
                        source = source
                    )
                    _centralEvents.emit(event)
                }
            }
        }
    }
}
