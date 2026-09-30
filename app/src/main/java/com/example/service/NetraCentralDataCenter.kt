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

    private var lastThermalWarningState = false
    private var lastCriticalOverheatState = false

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
            val oldState = _centralState.value

            if (chargerConnectedAt == null) chargerConnectedAt = oldState.chargerConnectedAt
            if (chargingStartedAt == null) chargingStartedAt = oldState.chargingStartedAt
            if (chargingStoppedAt == null) chargingStoppedAt = oldState.chargingStoppedAt
            if (chargerDisconnectedAt == null) chargerDisconnectedAt = oldState.chargerDisconnectedAt
            if (dischargingStartedAt == null) dischargingStartedAt = oldState.dischargingStartedAt
            if (lastConnectedState == null) lastConnectedState = oldState.isChargerConnected
            if (lastChargingState == null) lastChargingState = oldState.isCharging
            lastDischargingStatus = oldState.isCharging == false && (oldState.dischargingStartedAt != null)

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
            val currentMa = if (currentMicroAmps != Int.MIN_VALUE) {
                currentMicroAmps / 1000
            } else null

            // Field-level merging with oldState, respecting explicit unknown/unavailable resets
            val isExplicitlyUnknown = (status == BatteryManager.BATTERY_STATUS_UNKNOWN)

            val mergedLevel = if (isExplicitlyUnknown) validatedLevel else (validatedLevel ?: oldState.batteryLevel)
            val mergedIsCharging = if (isExplicitlyUnknown) isCharging else (isCharging ?: oldState.isCharging)
            val mergedIsConnected = if (isExplicitlyUnknown) isConnected else (isConnected ?: oldState.isChargerConnected)
            val mergedPluggedType = if (isExplicitlyUnknown) pluggedType else (if (plugged != -1) pluggedType else (oldState.pluggedType ?: pluggedType))
            val mergedTempCelsius = if (isExplicitlyUnknown) tempCelsius else (tempCelsius ?: oldState.temperatureCelsius)
            val mergedVoltageMv = if (isExplicitlyUnknown) voltageMv else (voltageMv ?: oldState.voltageMv)
            val mergedCurrentMa = if (isExplicitlyUnknown) currentMa else (currentMa ?: oldState.currentMa)

            // Central ChargingSpeedEngine calculation
            val speedResult = chargingSpeedEngine.calculate(mergedIsCharging, mergedVoltageMv, mergedCurrentMa)
            val mergedRawPower = speedResult.rawPowerWatts ?: oldState.powerWatts
            val mergedNetPower = speedResult.netPowerWatts ?: oldState.netPowerWatts
            val mergedConsumption = speedResult.consumptionPowerWatts ?: oldState.consumptionPowerWatts
            val mergedSpeed = if (speedResult.speedCategory != CanonicalChargingSpeed.UNAVAILABLE) speedResult.speedCategory else oldState.chargingSpeed
            val mergedAnnouncementSpeed = if (speedResult.announcementCategory != CanonicalChargingSpeed.UNAVAILABLE) speedResult.announcementCategory else oldState.announcementSpeed

            val mergedBluetoothConnected = bluetoothConnected ?: oldState.bluetoothConnected
            val mergedBluetoothBattery = bluetoothBattery ?: oldState.bluetoothBatteryPercent

            // Session Timestamp tracking
            if (mergedIsConnected != null && mergedIsConnected != lastConnectedState) {
                if (mergedIsConnected) {
                    chargerConnectedAt = now
                    chargerDisconnectedAt = null
                } else {
                    chargerDisconnectedAt = now
                    chargerConnectedAt = null
                    chargingStartedAt = null
                    chargingStoppedAt = null
                }
            }
            if (mergedIsCharging != null && mergedIsCharging != lastChargingState) {
                if (mergedIsCharging) {
                    chargingStartedAt = now
                    chargingStoppedAt = null
                    dischargingStartedAt = null
                } else {
                    chargingStoppedAt = now
                }
            }

            val isDischarging = (status == BatteryManager.BATTERY_STATUS_DISCHARGING) || (mergedIsCharging == false && mergedIsConnected == false)
            val wasDischarging = lastDischargingStatus
            if (isDischarging && !wasDischarging) {
                dischargingStartedAt = now
            }
            lastDischargingStatus = isDischarging

            // Live ETA calculation based on observed progression over time
            if (mergedLevel != null) {
                levelSamples.add(LevelSample(mergedLevel, now))
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
                    if (mergedIsCharging == true && ratePerMinute > 0.01f && mergedLevel != null && mergedLevel < 100) {
                        val remainingPct = 100 - mergedLevel
                        chargingEta = (remainingPct / ratePerMinute).toInt().coerceIn(1, 720)
                    } else if (mergedIsCharging == false && ratePerMinute < -0.005f && mergedLevel != null && mergedLevel > 0) {
                        dischargingEta = (abs(mergedLevel / ratePerMinute)).toInt().coerceIn(1, 1440)
                    }
                }
            }

            val mergedChargingEta = chargingEta ?: oldState.chargingEtaMinutes
            val mergedDischargingEta = dischargingEta ?: oldState.dischargingEtaMinutes

            val newState = NetraCentralState(
                batteryLevel = mergedLevel,
                isCharging = mergedIsCharging,
                isChargerConnected = mergedIsConnected,
                chargerConnectedAt = chargerConnectedAt,
                chargingStartedAt = chargingStartedAt,
                chargingStoppedAt = chargingStoppedAt,
                chargerDisconnectedAt = chargerDisconnectedAt,
                dischargingStartedAt = dischargingStartedAt,
                pluggedType = mergedPluggedType,
                temperatureCelsius = mergedTempCelsius,
                voltageMv = mergedVoltageMv,
                currentMa = mergedCurrentMa,
                powerWatts = mergedRawPower,
                netPowerWatts = mergedNetPower,
                consumptionPowerWatts = mergedConsumption,
                chargingSpeed = mergedSpeed,
                announcementSpeed = mergedAnnouncementSpeed,
                bluetoothConnected = mergedBluetoothConnected,
                bluetoothBatteryPercent = mergedBluetoothBattery,
                bluetoothDevices = oldState.bluetoothDevices,
                lastUpdateTimestamp = now,
                isDataFresh = true,
                chargingEtaMinutes = mergedChargingEta,
                dischargingEtaMinutes = mergedDischargingEta
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

            if (mergedSpeed == CanonicalChargingSpeed.UNAVAILABLE) {
                lastSpeedCategory = mergedSpeed
            } else if (mergedSpeed != lastSpeedCategory) {
                val prev = lastSpeedCategory?.name ?: "UNKNOWN"
                lastSpeedCategory = mergedSpeed
                val event = NetraCentralEvent(
                    eventId = "event_speed_change_$now",
                    eventType = NetraEventType.SPEED_CHANGED,
                    timestamp = now,
                    previousValue = prev,
                    newValue = mergedSpeed.name,
                    source = source
                )
                _centralEvents.emit(event)
            }

            if (mergedLevel != null) {
                val boundary = (mergedLevel / 5) * 5
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

            // Thermal warning/critical/recovered event generation
            if (mergedTempCelsius != null) {
                val isWarning = mergedTempCelsius >= 40.0f
                val isCritical = mergedTempCelsius >= 45.0f

                if (isCritical && !lastCriticalOverheatState) {
                    lastCriticalOverheatState = true
                    lastThermalWarningState = true
                    _centralEvents.emit(
                        NetraCentralEvent(
                            eventId = "event_thermal_crit_$now",
                            eventType = NetraEventType.THERMAL_CRITICAL,
                            timestamp = now,
                            newValue = mergedTempCelsius.toString(),
                            source = source
                        )
                    )
                } else if (isWarning && !lastThermalWarningState && !isCritical) {
                    lastThermalWarningState = true
                    _centralEvents.emit(
                        NetraCentralEvent(
                            eventId = "event_thermal_warn_$now",
                            eventType = NetraEventType.THERMAL_WARNING,
                            timestamp = now,
                            newValue = mergedTempCelsius.toString(),
                            source = source
                        )
                    )
                } else if (!isWarning && !isCritical && (lastThermalWarningState || lastCriticalOverheatState)) {
                    lastThermalWarningState = false
                    lastCriticalOverheatState = false
                    _centralEvents.emit(
                        NetraCentralEvent(
                            eventId = "event_thermal_rec_$now",
                            eventType = NetraEventType.THERMAL_RECOVERED,
                            timestamp = now,
                            newValue = mergedTempCelsius.toString(),
                            source = source
                        )
                    )
                }
            }
        }
    }

    suspend fun processBluetoothDevices(devices: List<com.example.model.BluetoothDeviceItem>, source: String = "BluetoothHelper") {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val oldState = _centralState.value

            val mergedDevices = devices.map { device ->
                val deviceKey = device.address.ifBlank { device.name }
                val oldDevice = oldState.bluetoothDevices.find { (it.address.ifBlank { it.name }) == deviceKey }

                // Carry forward last valid battery level if temporarily null
                val mergedBattery = device.batteryPercent ?: oldDevice?.batteryPercent
                device.copy(batteryPercent = mergedBattery)
            }

            // Connection and Disconnection checks
            for (device in mergedDevices) {
                val deviceKey = device.address.ifBlank { device.name }
                val oldDevice = oldState.bluetoothDevices.find { (it.address.ifBlank { it.name }) == deviceKey }

                if (device.isConnected && (oldDevice == null || !oldDevice.isConnected)) {
                    _centralEvents.emit(
                        NetraCentralEvent(
                            eventId = "event_bt_conn_${deviceKey}_$now",
                            eventType = NetraEventType.BLUETOOTH_CONNECTED,
                            timestamp = now,
                            newValue = device.name,
                            source = source
                        )
                    )
                }

                // Check battery boundary crossing for Bluetooth (10% increments)
                if (device.isConnected && device.batteryPercent != null) {
                    val currentPercent = device.batteryPercent
                    val oldPercent = oldDevice?.batteryPercent
                    if (currentPercent % 10 == 0 && (oldPercent == null || oldPercent != currentPercent)) {
                        _centralEvents.emit(
                            NetraCentralEvent(
                                eventId = "event_bt_bat_${deviceKey}_${currentPercent}_$now",
                                eventType = NetraEventType.BLUETOOTH_BATTERY_BOUNDARY,
                                timestamp = now,
                                previousValue = device.name,
                                newValue = currentPercent.toString(),
                                source = source
                            )
                        )
                    }
                }
            }

            for (oldDevice in oldState.bluetoothDevices) {
                val deviceKey = oldDevice.address.ifBlank { oldDevice.name }
                val newDevice = mergedDevices.find { (it.address.ifBlank { it.name }) == deviceKey }
                if (oldDevice.isConnected && (newDevice == null || !newDevice.isConnected)) {
                    _centralEvents.emit(
                        NetraCentralEvent(
                            eventId = "event_bt_disc_${deviceKey}_$now",
                            eventType = NetraEventType.BLUETOOTH_DISCONNECTED,
                            timestamp = now,
                            newValue = oldDevice.name,
                            source = source
                        )
                    )
                }
            }

            val bluetoothConnected = mergedDevices.any { it.isConnected }
            val highestBattery = mergedDevices.filter { it.isConnected && it.batteryPercent != null }
                .maxOfOrNull { it.batteryPercent!! }

            val newState = oldState.copy(
                bluetoothConnected = bluetoothConnected,
                bluetoothBatteryPercent = highestBattery,
                bluetoothDevices = mergedDevices,
                lastUpdateTimestamp = now
            )

            _centralState.value = newState
        }
    }
}
