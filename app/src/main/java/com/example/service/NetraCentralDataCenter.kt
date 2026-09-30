package com.example.service

import android.content.Context
import android.os.BatteryManager
import com.example.model.CanonicalChargingSpeed
import com.example.model.CanonicalPluggedType
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType
import com.example.model.FieldStatus
import com.example.model.NetraCentralEvent
import com.example.model.NetraCentralState
import com.example.model.NetraEventType
import com.example.model.TelemetryFieldState
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

    private var capabilityRegistry: CentralCapabilityRegistry? = null
    private var lastValidStatePrefs: android.content.SharedPreferences? = null

    fun initCapabilityRegistry(context: Context) {
        capabilityRegistry = CentralCapabilityRegistry(context)
        refreshCapabilities()
    }

    fun initPersistence(context: Context) {
        lastValidStatePrefs = context.getSharedPreferences("netra_last_valid_state_prefs", Context.MODE_PRIVATE)
        val prefs = lastValidStatePrefs ?: return
        if (prefs.contains("saved_battery_level") && _centralState.value.batteryLevel == null) {
            val savedLevel = prefs.getInt("saved_battery_level", -1).takeIf { it in 0..100 }
            val savedTemp = prefs.getFloat("saved_temp", -1f).takeIf { it > 0f }
            val savedVoltage = prefs.getInt("saved_voltage", -1).takeIf { it > 0 }
            val savedCurrent = prefs.getInt("saved_current", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            val savedPower = prefs.getFloat("saved_power", -1f).takeIf { it >= 0f }

            // Restored persisted state is marked as LAST_KNOWN / NOT FRESH
            _centralState.value = _centralState.value.copy(
                batteryLevel = savedLevel,
                temperatureCelsius = savedTemp,
                voltageMv = savedVoltage,
                currentMa = savedCurrent,
                powerWatts = savedPower,
                isDataFresh = false, // CRITICAL: Restored state is historical / last-known, NOT fresh live!
                fieldStates = TelemetryFieldState(
                    levelStatus = if (savedLevel != null) FieldStatus.LAST_VALID else FieldStatus.UNAVAILABLE,
                    tempStatus = if (savedTemp != null) FieldStatus.LAST_VALID else FieldStatus.UNAVAILABLE,
                    voltageStatus = if (savedVoltage != null) FieldStatus.LAST_VALID else FieldStatus.UNAVAILABLE,
                    currentStatus = if (savedCurrent != null) FieldStatus.LAST_VALID else FieldStatus.UNAVAILABLE,
                    powerStatus = if (savedPower != null) FieldStatus.LAST_VALID else FieldStatus.UNAVAILABLE
                )
            )
        }
    }

    fun refreshCapabilities() {
        val oldState = _centralState.value
        val detected = capabilityRegistry?.detectAllCapabilities(
            currentMicroAmps = (oldState.currentMa ?: 0) * 1000,
            temperatureRaw = ((oldState.temperatureCelsius ?: 0f) * 10).toInt(),
            voltageRaw = oldState.voltageMv ?: 0,
            connectedBluetoothCount = oldState.bluetoothDevices.count { it.isConnected }
        ) ?: return
        _centralState.value = _centralState.value.copy(capabilities = detected)
    }

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
                BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                BatteryManager.BATTERY_STATUS_UNKNOWN -> false
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

            // 2. Strict Field-Level Last-Valid-Value Retention Mechanism
            // Missing, null, or invalid fields do NOT overwrite valid existing values.
            val mergedLevel = validatedLevel ?: oldState.batteryLevel
            val mergedIsCharging = isCharging ?: oldState.isCharging
            val mergedIsConnected = isConnected ?: oldState.isChargerConnected
            val mergedPluggedType = if (plugged > 0 || plugged == 0) pluggedType else (oldState.pluggedType ?: pluggedType)
            val mergedTempCelsius = tempCelsius ?: oldState.temperatureCelsius
            val mergedVoltageMv = voltageMv ?: oldState.voltageMv
            val mergedCurrentMa = currentMa ?: oldState.currentMa

            // Central ChargingSpeedEngine calculation
            val speedResult = chargingSpeedEngine.calculate(mergedIsCharging, mergedVoltageMv, mergedCurrentMa)
            val mergedRawPower = speedResult.rawPowerWatts ?: oldState.powerWatts
            val mergedNetPower = speedResult.netPowerWatts ?: oldState.netPowerWatts
            val mergedConsumption = speedResult.consumptionPowerWatts ?: oldState.consumptionPowerWatts
            val mergedSpeed = if (mergedIsCharging == true) {
                if (speedResult.speedCategory != CanonicalChargingSpeed.UNAVAILABLE) speedResult.speedCategory else oldState.chargingSpeed
            } else {
                CanonicalChargingSpeed.UNAVAILABLE
            }
            val mergedAnnouncementSpeed = if (mergedIsCharging == true) {
                if (speedResult.announcementCategory != CanonicalChargingSpeed.UNAVAILABLE) speedResult.announcementCategory else oldState.announcementSpeed
            } else {
                CanonicalChargingSpeed.UNAVAILABLE
            }

            // Calculate precise FieldStatus for each telemetry parameter
            val fieldStates = TelemetryFieldState(
                levelStatus = when {
                    validatedLevel != null -> FieldStatus.LIVE
                    oldState.batteryLevel != null -> FieldStatus.LAST_VALID
                    else -> FieldStatus.UNAVAILABLE
                },
                tempStatus = when {
                    tempCelsius != null -> FieldStatus.LIVE
                    oldState.temperatureCelsius != null -> FieldStatus.LAST_VALID
                    else -> FieldStatus.UNAVAILABLE
                },
                voltageStatus = when {
                    voltageMv != null -> FieldStatus.LIVE
                    oldState.voltageMv != null -> FieldStatus.LAST_VALID
                    else -> FieldStatus.UNAVAILABLE
                },
                currentStatus = when {
                    currentMa != null -> FieldStatus.LIVE
                    oldState.currentMa != null -> FieldStatus.LAST_VALID
                    else -> FieldStatus.UNAVAILABLE
                },
                powerStatus = when {
                    speedResult.rawPowerWatts != null -> FieldStatus.LIVE
                    oldState.powerWatts != null -> FieldStatus.LAST_VALID
                    else -> FieldStatus.UNAVAILABLE
                }
            )

            // Central Capability Registry Detection
            val detectedCapabilities = capabilityRegistry?.detectAllCapabilities(
                currentMicroAmps = currentMicroAmps,
                temperatureRaw = temperatureRaw,
                voltageRaw = voltage,
                connectedBluetoothCount = oldState.bluetoothDevices.count { it.isConnected }
            ) ?: oldState.capabilities

            // Persist latest valid values asynchronously for reliable reboot restoration
            lastValidStatePrefs?.edit()?.apply {
                if (mergedLevel != null) putInt("saved_battery_level", mergedLevel)
                if (mergedTempCelsius != null) putFloat("saved_temp", mergedTempCelsius)
                if (mergedVoltageMv != null) putInt("saved_voltage", mergedVoltageMv)
                if (mergedCurrentMa != null) putInt("saved_current", mergedCurrentMa)
                if (mergedRawPower != null) putFloat("saved_power", mergedRawPower)
                apply()
            }

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
                bluetoothHistory = oldState.bluetoothHistory,
                lastUpdateTimestamp = now,
                isDataFresh = true,
                fieldStates = fieldStates,
                capabilities = detectedCapabilities,
                chargingEtaMinutes = mergedChargingEta,
                dischargingEtaMinutes = mergedDischargingEta,
                mediaState = oldState.mediaState,
                isMediaControlAvailable = oldState.isMediaControlAvailable,
                mediaPausedByNethra = oldState.mediaPausedByNethra,
                isNightProtectionActive = oldState.isNightProtectionActive
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

            if (mergedSpeed != CanonicalChargingSpeed.UNAVAILABLE) {
                if (lastSpeedCategory != mergedSpeed) {
                    val prev = lastSpeedCategory?.name ?: "UNAVAILABLE"
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

            // Process Persistent Bluetooth History
            val historyMap = mutableMapOf<String, com.example.model.BluetoothDeviceItem>()
            
            // 1. Load from persistent history storage
            loadBluetoothHistory().forEach { dev ->
                historyMap[dev.address.ifBlank { dev.name }] = dev
            }

            // 2. Add current connected devices, setting isConnected to false inside history (showing last connected info)
            mergedDevices.forEach { dev ->
                val key = dev.address.ifBlank { dev.name }
                historyMap[key] = dev.copy(isConnected = false)
            }

            // 3. Fallback to paired devices to populate history
            try {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                if (adapter != null && adapter.isEnabled) {
                    val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    } else {
                        android.Manifest.permission.BLUETOOTH
                    }
                    val context = com.example.NetraApplication.instance.applicationContext
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        adapter.bondedDevices.forEach { bd ->
                            val name = bd.name ?: "Bluetooth Device"
                            val address = bd.address ?: ""
                            val key = address.ifBlank { name }
                            if (!historyMap.containsKey(key)) {
                                historyMap[key] = com.example.model.BluetoothDeviceItem(
                                    name = name,
                                    address = address,
                                    isConnected = false,
                                    isPaired = true,
                                    deviceType = "Bluetooth Peripheral",
                                    batteryPercent = null
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            val updatedHistoryList = historyMap.values.toList()
            saveBluetoothHistory(updatedHistoryList)

            val bluetoothConnected = mergedDevices.any { it.isConnected }
            val highestBattery = mergedDevices.filter { it.isConnected && it.batteryPercent != null }
                .maxOfOrNull { it.batteryPercent!! }

            val settings = com.example.NetraApplication.instance.settingsRepository.settings.value
            val isNightActive = calculateIsNightProtectionActive(settings)

            val newState = oldState.copy(
                bluetoothConnected = bluetoothConnected,
                bluetoothBatteryPercent = highestBattery,
                bluetoothDevices = mergedDevices,
                bluetoothHistory = updatedHistoryList,
                lastUpdateTimestamp = now,
                isNightProtectionActive = isNightActive
            )

            _centralState.value = newState
        }
    }

    private fun saveBluetoothHistory(historyList: List<com.example.model.BluetoothDeviceItem>) {
        try {
            val context = com.example.NetraApplication.instance.applicationContext
            val prefs = context.getSharedPreferences("netra_bluetooth_history_prefs", Context.MODE_PRIVATE)
            val array = org.json.JSONArray()
            for (device in historyList) {
                val obj = org.json.JSONObject()
                obj.put("name", device.name)
                obj.put("address", device.address)
                obj.put("isConnected", false)
                obj.put("isPaired", device.isPaired)
                obj.put("deviceType", device.deviceType)
                obj.put("batteryPercent", device.batteryPercent ?: -1)
                obj.put("profile", device.profile)
                array.put(obj)
            }
            prefs.edit().putString("bluetooth_history", array.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun loadBluetoothHistory(): List<com.example.model.BluetoothDeviceItem> {
        val list = mutableListOf<com.example.model.BluetoothDeviceItem>()
        try {
            val context = com.example.NetraApplication.instance.applicationContext
            val prefs = context.getSharedPreferences("netra_bluetooth_history_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("bluetooth_history", null) ?: return emptyList()
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val batteryVal = obj.optInt("batteryPercent", -1)
                list.add(
                    com.example.model.BluetoothDeviceItem(
                        name = obj.getString("name"),
                        address = obj.getString("address"),
                        isConnected = false,
                        isPaired = obj.optBoolean("isPaired", true),
                        deviceType = obj.getString("deviceType"),
                        batteryPercent = if (batteryVal >= 0) batteryVal else null,
                        profile = obj.optString("profile", "A2DP / HFP")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun calculateIsNightProtectionActive(settings: com.example.data.repository.SentinelSettings): Boolean {
        if (!settings.nightProtectionEnabled) return false
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val start = settings.nightStartHour
        val end = settings.nightEndHour
        return if (start == end) {
            false
        } else if (start > end) {
            currentHour >= start || currentHour < end
        } else {
            currentHour in start until end
        }
    }

    fun refreshNightProtectionStateSynchronously() {
        val settings = com.example.NetraApplication.instance.settingsRepository.settings.value
        val isNightActive = calculateIsNightProtectionActive(settings)
        val oldState = _centralState.value
        if (oldState.isNightProtectionActive != isNightActive) {
            _centralState.value = oldState.copy(isNightProtectionActive = isNightActive)
        }
    }

    suspend fun refreshNightProtectionState() {
        mutex.withLock {
            refreshNightProtectionStateSynchronously()
        }
    }

    private var mediaPlaybackController: com.example.util.MediaPlaybackController? = null

    private fun getMediaController(context: Context): com.example.util.MediaPlaybackController {
        if (mediaPlaybackController == null) {
            mediaPlaybackController = com.example.util.MediaPlaybackController(context.applicationContext)
        }
        return mediaPlaybackController!!
    }

    suspend fun updateMediaState(state: com.example.model.CanonicalMediaState) {
        mutex.withLock {
            val oldState = _centralState.value
            _centralState.value = oldState.copy(mediaState = state)
        }
    }

    suspend fun updateMediaControlAvailable(available: Boolean) {
        mutex.withLock {
            val oldState = _centralState.value
            _centralState.value = oldState.copy(isMediaControlAvailable = available)
        }
    }

    suspend fun setMediaPausedByNethra(paused: Boolean) {
        mutex.withLock {
            val oldState = _centralState.value
            _centralState.value = oldState.copy(mediaPausedByNethra = paused)
        }
    }

    suspend fun requestMediaPause(context: Context): Boolean {
        return mutex.withLock {
            val oldState = _centralState.value
            if (!oldState.isMediaControlAvailable || oldState.mediaState == com.example.model.CanonicalMediaState.UNSUPPORTED) {
                return false
            }

            val controller = getMediaController(context)
            val isPlaying = controller.isMediaPlaying()
            
            if (isPlaying) {
                controller.prepareForAnnouncement()
                _centralState.value = oldState.copy(
                    mediaState = com.example.model.CanonicalMediaState.PAUSED,
                    mediaPausedByNethra = true
                )
                true
            } else {
                _centralState.value = oldState.copy(
                    mediaState = if (controller.isMediaPlaying()) com.example.model.CanonicalMediaState.PLAYING else com.example.model.CanonicalMediaState.PAUSED
                )
                false
            }
        }
    }

    suspend fun requestMediaResume(context: Context): Boolean {
        return mutex.withLock {
            val oldState = _centralState.value
            if (oldState.mediaPausedByNethra) {
                val controller = getMediaController(context)
                controller.restoreAfterAnnouncement()
                _centralState.value = oldState.copy(
                    mediaState = com.example.model.CanonicalMediaState.PLAYING,
                    mediaPausedByNethra = false
                )
                true
            } else {
                false
            }
        }
    }
}
