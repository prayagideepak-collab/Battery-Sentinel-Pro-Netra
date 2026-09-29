package com.example.service

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.NetraApplication
import com.example.data.repository.SentinelSettings
import com.example.model.BatteryTelemetry
import com.example.model.BluetoothDeviceItem
import com.example.model.ChargerSpeed
import com.example.util.MediaPlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicBoolean

enum class AnnouncementPriority(val level: Int) {
    CRITICAL_THERMAL(1),
    CHARGER_STATE(2),
    BLUETOOTH_STATE(3),
    CHARGING_SPEED(4),
    PHONE_BATTERY(5),
    BLUETOOTH_BATTERY(6),
    INFORMATIONAL(7)
}

data class AnnouncementItem(
    val id: String,
    val text: String,
    val priority: AnnouncementPriority,
    val category: String,
    val isNightException: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) : Comparable<AnnouncementItem> {
    override fun compareTo(other: AnnouncementItem): Int {
        val prioDiff = this.priority.level.compareTo(other.priority.level)
        return if (prioDiff != 0) prioDiff else this.timestamp.compareTo(other.timestamp)
    }
}

/**
 * Centralized Announcement Engine for Battery Sentinel Pro Netra.
 * All voice alerts pass through this engine with controlled priority queuing,
 * state deduplication, night protection, and media playback pausing/resuming.
 */
class AnnouncementEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val mediaController = MediaPlaybackController(context)

    // Controlled Priority Queue
    private val queue = PriorityQueue<AnnouncementItem>()
    private val isSpeaking = AtomicBoolean(false)
    private var currentPlayingAnnouncement: AnnouncementItem? = null

    // State Tracking & Baseline for Deduplication
    private var isBaselineEstablished = false
    private var lastPhoneLevel: Int? = null
    private var lastPhoneBoundary: Int? = null
    private var lastChargingState: Boolean? = null
    private var lastSpeedCategory: String? = null // "SLOW", "NORMAL", "FAST", "ULTRA_FAST"
    private var lastThermalWarningState: Boolean = false
    private var lastCriticalOverheatState: Boolean = false

    // Bluetooth Per-Device State Tracking
    private val lastBtConnectionMap = mutableMapOf<String, Boolean>() // address/name -> isConnected
    private val lastBtBatteryBoundaryMap = mutableMapOf<String, Int>() // address/name -> last 10% boundary

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            // Natural, understandable speech rate
            tts?.setSpeechRate(0.95f)
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS done: $utteranceId")
                    onSpeechFinished()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error on utterance: $utteranceId")
                    onSpeechFinished()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "TTS error ($errorCode) on utterance: $utteranceId")
                    onSpeechFinished()
                }
            })

            isTtsReady = true
            processQueue()
        } else {
            Log.e(TAG, "TextToSpeech init failed with status: $status")
        }
    }

    /**
     * Primary entry point to evaluate phone battery, charging, and thermal telemetry updates.
     */
    @Synchronized
    fun onTelemetryUpdate(telemetry: BatteryTelemetry) {
        val settings = getSettings()

        // 1. Establish baseline on initial startup/restart to avoid spamming
        if (!isBaselineEstablished) {
            establishBaseline(telemetry)
            return
        }

        // 2. Charger Connect / Disconnect Transition
        if (lastChargingState != null && lastChargingState != telemetry.isCharging) {
            val isNowCharging = telemetry.isCharging
            lastChargingState = isNowCharging
            // Reset boundary and speed tracking on connection change
            lastSpeedCategory = null
            lastPhoneBoundary = calculate5PercentBoundary(telemetry.level)

            if (settings.announceChargerConnected) {
                val text = if (isNowCharging) "Charger connected." else "Charger disconnected."
                enqueue(
                    AnnouncementItem(
                        id = "charger_${System.currentTimeMillis()}",
                        text = text,
                        priority = AnnouncementPriority.CHARGER_STATE,
                        category = "CHARGER",
                        isNightException = true // Allowed at night
                    )
                )
            }
        } else if (lastChargingState == null) {
            lastChargingState = telemetry.isCharging
        }

        // 3. Charging Speed Transition (<5W: Slow, 5-10W: Normal, 10-20W: Fast, >20W: Ultra Fast)
        if (telemetry.isCharging && telemetry.powerWatts > 0f) {
            val currentCategory = categorizePowerSpeed(telemetry.powerWatts)
            if (lastSpeedCategory != null && lastSpeedCategory != currentCategory) {
                lastSpeedCategory = currentCategory
                if (settings.announceChargingSpeed) {
                    val speechText = when (currentCategory) {
                        "SLOW" -> "Slow charging."
                        "NORMAL" -> "Normal charging."
                        "FAST" -> "F charging."
                        "ULTRA_FAST" -> "UF charging."
                        else -> "Normal charging."
                    }
                    enqueue(
                        AnnouncementItem(
                            id = "speed_${System.currentTimeMillis()}",
                            text = speechText,
                            priority = AnnouncementPriority.CHARGING_SPEED,
                            category = "CHARGING_SPEED",
                            isNightException = false
                        )
                    )
                }
            } else if (lastSpeedCategory == null) {
                lastSpeedCategory = currentCategory
            }
        } else if (!telemetry.isCharging) {
            lastSpeedCategory = null
        }

        // 4. Phone Battery 5% Boundary Crossing
        val currentLevel = telemetry.level.coerceIn(0, 100)
        val prevLevel = lastPhoneLevel ?: currentLevel
        val isCharging = telemetry.isCharging

        if (settings.announcePhoneBattery) {
            checkPhoneBatteryBoundaryCrossing(prevLevel, currentLevel, isCharging)
        }
        lastPhoneLevel = currentLevel

        // 5. Thermal Warning & Critical Overheat Announcements
        val isWarning = telemetry.temperature >= settings.thermalWarningThreshold
        val isCritical = telemetry.temperature >= settings.criticalOverheatThreshold

        if (settings.announceThermalWarning) {
            if (isCritical && !lastCriticalOverheatState) {
                lastCriticalOverheatState = true
                lastThermalWarningState = true
                val tempFormatted = String.format(Locale.US, "%.1f", telemetry.temperature)
                val text = "Thermal warning. Your phone temperature is $tempFormatted degrees. Please stop using the phone. Nethra is cooling down the device."
                enqueue(
                    AnnouncementItem(
                        id = "thermal_crit_${System.currentTimeMillis()}",
                        text = text,
                        priority = AnnouncementPriority.CRITICAL_THERMAL,
                        category = "THERMAL_CRITICAL",
                        isNightException = true // Allowed at night
                    )
                )
            } else if (isWarning && !lastThermalWarningState && !isCritical) {
                lastThermalWarningState = true
                val tempFormatted = String.format(Locale.US, "%.1f", telemetry.temperature)
                val text = "Thermal warning. Your phone temperature is $tempFormatted degrees. Please stop using the phone. Nethra is cooling down the device."
                enqueue(
                    AnnouncementItem(
                        id = "thermal_warn_${System.currentTimeMillis()}",
                        text = text,
                        priority = AnnouncementPriority.CRITICAL_THERMAL,
                        category = "THERMAL_WARNING",
                        isNightException = true // Allowed at night
                    )
                )
            } else if (!isWarning && !isCritical && (lastThermalWarningState || lastCriticalOverheatState)) {
                // Thermal state recovered
                lastThermalWarningState = false
                lastCriticalOverheatState = false
            }
        }
    }

    /**
     * Primary entry point to evaluate Bluetooth device connection and battery level updates.
     */
    @Synchronized
    fun onBluetoothDevicesUpdate(devices: List<BluetoothDeviceItem>) {
        val settings = getSettings()

        for (device in devices) {
            val deviceKey = device.address.ifBlank { device.name }

            // 1. Connection State Transition
            val lastConnected = lastBtConnectionMap[deviceKey]
            if (lastConnected != null && lastConnected != device.isConnected) {
                lastBtConnectionMap[deviceKey] = device.isConnected
                if (!device.isConnected) {
                    lastBtBatteryBoundaryMap.remove(deviceKey)
                }

                val text = if (device.isConnected) "BT connected." else "BT disconnected."
                enqueue(
                    AnnouncementItem(
                        id = "bt_conn_${deviceKey}_${System.currentTimeMillis()}",
                        text = text,
                        priority = AnnouncementPriority.BLUETOOTH_STATE,
                        category = "BLUETOOTH_STATE",
                        isNightException = false
                    )
                )
            } else if (lastConnected == null) {
                lastBtConnectionMap[deviceKey] = device.isConnected
            }

            // 2. Bluetooth Battery 10% Boundary (0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
            if (device.isConnected && device.batteryPercent != null && settings.announceBluetoothBattery) {
                val btBattery = device.batteryPercent.coerceIn(0, 100)
                val currentBoundary = calculate10PercentBoundary(btBattery)
                val lastBoundary = lastBtBatteryBoundaryMap[deviceKey]

                if (currentBoundary != null) {
                    if (lastBoundary == null) {
                        // Establish device baseline
                        lastBtBatteryBoundaryMap[deviceKey] = currentBoundary
                    } else if (currentBoundary != lastBoundary) {
                        lastBtBatteryBoundaryMap[deviceKey] = currentBoundary
                        val text = "BT $currentBoundary percent"
                        enqueue(
                            AnnouncementItem(
                                id = "bt_bat_${deviceKey}_${currentBoundary}",
                                text = text,
                                priority = AnnouncementPriority.BLUETOOTH_BATTERY,
                                category = "BLUETOOTH_BATTERY",
                                isNightException = false
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Checks if a 5% phone battery boundary was crossed in the active direction.
     */
    private fun checkPhoneBatteryBoundaryCrossing(prev: Int, current: Int, isCharging: Boolean) {
        if (prev == current) return

        if (isCharging && current > prev) {
            // Charging upward
            val crossedBoundaries = getCrossed5PercentBoundaries(prev, current, isCharging = true)
            if (crossedBoundaries.isNotEmpty()) {
                // Announce the highest reached boundary
                val targetBoundary = crossedBoundaries.last()
                if (targetBoundary != lastPhoneBoundary) {
                    lastPhoneBoundary = targetBoundary
                    val text = "C $targetBoundary percent"
                    enqueue(
                        AnnouncementItem(
                            id = "phone_c_$targetBoundary",
                            text = text,
                            priority = AnnouncementPriority.PHONE_BATTERY,
                            category = "PHONE_BATTERY",
                            isNightException = false
                        )
                    )
                }
            }
        } else if (!isCharging && current < prev) {
            // Discharging downward
            val crossedBoundaries = getCrossed5PercentBoundaries(prev, current, isCharging = false)
            if (crossedBoundaries.isNotEmpty()) {
                // Announce the lowest reached boundary
                val targetBoundary = crossedBoundaries.last()
                if (targetBoundary != lastPhoneBoundary) {
                    lastPhoneBoundary = targetBoundary
                    val text = "D $targetBoundary percent"
                    enqueue(
                        AnnouncementItem(
                            id = "phone_d_$targetBoundary",
                            text = text,
                            priority = AnnouncementPriority.PHONE_BATTERY,
                            category = "PHONE_BATTERY",
                            isNightException = false
                        )
                    )
                }
            }
        }
    }

    /**
     * Calculates crossed 5% boundaries between prev and current levels.
     */
    fun getCrossed5PercentBoundaries(prev: Int, current: Int, isCharging: Boolean): List<Int> {
        val result = mutableListOf<Int>()
        val validBoundaries = (0..100 step 5).toList()

        if (isCharging) {
            for (b in validBoundaries) {
                if (b in (prev + 1)..current) {
                    result.add(b)
                }
            }
        } else {
            for (b in validBoundaries.reversed()) {
                if (b in current until prev) {
                    result.add(b)
                }
            }
        }

        return result
    }

    private fun calculate5PercentBoundary(level: Int): Int? {
        val rem = level % 5
        return if (rem == 0) level else null
    }

    private fun calculate10PercentBoundary(level: Int): Int? {
        val rem = level % 10
        return if (rem == 0) level else null
    }

    fun categorizePowerSpeed(powerWatts: Float): String {
        return when {
            powerWatts >= 20.0f -> "ULTRA_FAST"
            powerWatts >= 10.0f -> "FAST"
            powerWatts >= 5.0f -> "NORMAL"
            else -> "SLOW"
        }
    }

    private fun establishBaseline(telemetry: BatteryTelemetry) {
        lastPhoneLevel = telemetry.level
        lastPhoneBoundary = calculate5PercentBoundary(telemetry.level)
        lastChargingState = telemetry.isCharging
        lastSpeedCategory = if (telemetry.isCharging && telemetry.powerWatts > 0f) {
            categorizePowerSpeed(telemetry.powerWatts)
        } else null
        lastThermalWarningState = telemetry.temperature >= 40.0f
        lastCriticalOverheatState = telemetry.temperature >= 45.0f
        isBaselineEstablished = true
        Log.d(TAG, "Baseline established: level=${telemetry.level}%, charging=${telemetry.isCharging}, speed=$lastSpeedCategory")
    }

    /**
     * Enqueues an announcement item respecting master enabled, night protection, and deduplication.
     */
    @Synchronized
    fun enqueue(item: AnnouncementItem) {
        val settings = getSettings()

        // 1. Global Master Switch Check
        if (!settings.announcementsMasterEnabled) {
            Log.d(TAG, "Announcement suppressed: master switch is OFF -> ${item.text}")
            return
        }

        // 2. Night Protection Check (Default 11:00 PM -> 6:00 AM)
        if (settings.nightProtectionEnabled && isNightTime(settings.nightStartHour, settings.nightEndHour)) {
            if (!item.isNightException) {
                Log.d(TAG, "Announcement suppressed by Night Protection (${settings.nightStartHour}:00-${settings.nightEndHour}:00) -> ${item.text}")
                return
            }
        }

        // 3. Prevent exact duplicate item already waiting in queue
        if (queue.any { it.text == item.text }) {
            Log.d(TAG, "Duplicate announcement discarded -> ${item.text}")
            return
        }

        // 4. Cap queue size to prevent backlog
        if (queue.size >= 8) {
            queue.poll() // remove lowest priority
        }

        queue.offer(item)
        processQueue()
    }

    /**
     * Helper to test or trigger manual spoken preview.
     */
    fun speakDirect(text: String, priority: AnnouncementPriority = AnnouncementPriority.INFORMATIONAL) {
        enqueue(
            AnnouncementItem(
                id = "direct_${System.currentTimeMillis()}",
                text = text,
                priority = priority,
                category = "DIRECT_TEST",
                isNightException = true
            )
        )
    }

    @Synchronized
    private fun processQueue() {
        if (!isTtsReady || isSpeaking.get() || queue.isEmpty()) {
            return
        }

        val nextItem = queue.poll() ?: return
        isSpeaking.set(true)
        currentPlayingAnnouncement = nextItem

        scope.launch(Dispatchers.IO) {
            // Media playback coordination: pause media before speaking if media is actively playing
            val settings = getSettings()
            if (settings.mediaPlaybackHandlingEnabled) {
                mediaController.prepareForAnnouncement()
            }

            // Speak via TTS
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, nextItem.id)
            }

            val result = tts?.speak(nextItem.text, TextToSpeech.QUEUE_FLUSH, params, nextItem.id)
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS speak failed for: ${nextItem.text}")
                onSpeechFinished()
            } else {
                Log.i(TAG, "Spoken: '${nextItem.text}' [Priority: ${nextItem.priority}]")
                // Log to Room Database Activity Log
                logAnnouncementToDb(nextItem)
            }
        }
    }

    private fun onSpeechFinished() {
        scope.launch(Dispatchers.IO) {
            try {
                // Restore media playback ONLY if it was playing before
                val settings = getSettings()
                if (settings.mediaPlaybackHandlingEnabled) {
                    mediaController.restoreAfterAnnouncement()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring media playback", e)
            } finally {
                isSpeaking.set(false)
                currentPlayingAnnouncement = null
                // Process next in queue on main thread
                scope.launch(Dispatchers.Main) {
                    processQueue()
                }
            }
        }
    }

    /**
     * Evaluates if current system time falls within Night Protection window.
     * Supports wrap-around schedules (e.g. 23 to 6).
     */
    fun isNightTime(startHour: Int, endHour: Int): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (startHour > endHour) {
            // Wrap-around across midnight (e.g. 23:00 to 06:00)
            currentHour >= startHour || currentHour < endHour
        } else {
            currentHour in startHour until endHour
        }
    }

    private fun logAnnouncementToDb(item: AnnouncementItem) {
        scope.launch(Dispatchers.IO) {
            try {
                val repository = NetraApplication.instance.batteryRepository
                val dotColor = when (item.priority) {
                    AnnouncementPriority.CRITICAL_THERMAL -> "RED"
                    AnnouncementPriority.CHARGER_STATE -> "CYAN"
                    AnnouncementPriority.CHARGING_SPEED -> "GREEN"
                    AnnouncementPriority.PHONE_BATTERY -> "BLUE"
                    AnnouncementPriority.BLUETOOTH_STATE -> "PURPLE"
                    AnnouncementPriority.BLUETOOTH_BATTERY -> "CYAN"
                    AnnouncementPriority.INFORMATIONAL -> "AMBER"
                }
                repository.logEvent(
                    title = "Voice Announcement",
                    message = item.text,
                    category = item.category,
                    severity = if (item.priority == AnnouncementPriority.CRITICAL_THERMAL) "CRITICAL" else "INFO",
                    dotColor = dotColor
                )
            } catch (_: Exception) {}
        }
    }

    private fun getSettings(): SentinelSettings {
        return try {
            NetraApplication.instance.settingsRepository.settings.value
        } catch (_: Exception) {
            SentinelSettings()
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        mediaController.restoreAfterAnnouncement()
    }

    companion object {
        private const val TAG = "NetraAnnouncementEngine"
    }
}
