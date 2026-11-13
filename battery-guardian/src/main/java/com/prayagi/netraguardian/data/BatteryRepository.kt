package com.prayagi.netraguardian.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.prayagi.netraguardian.domain.BatteryReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: BatteryDao
) {
    fun observeBattery(): Flow<BatteryReading> = callbackFlow {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent ?: return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 0
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }
                val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).takeIf { it != Int.MIN_VALUE && it != 0 }
                } else null
                val power = if (voltage != null && current != null)
                    voltage.toDouble().times(current.toDouble()).div(1_000_000_000.0).toFloat() else null
                val healthCode = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val health = when (healthCode) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good (system-reported)"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat (system-reported)"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead (system-reported)"
                    BatteryManager.BATTERY_HEALTH_COLD -> "Cold (system-reported)"
                    else -> null
                }
                trySend(BatteryReading(percent, charging, plugged,
                    tempRaw.takeIf { it != Int.MIN_VALUE }?.div(10f), voltage, current, power, health))
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        context.registerReceiver(receiver, filter)?.let { receiver.onReceive(context, it) }
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    suspend fun saveSnapshot(reading: BatteryReading) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        dao.insert(BatterySnapshot(
            capturedAt = reading.capturedAt, levelPercent = reading.levelPercent,
            temperatureCelsius = reading.temperatureCelsius, voltageMillivolts = reading.voltageMillivolts,
            currentMicroamps = reading.currentMicroamps, powerWatts = reading.powerWatts,
            charging = reading.charging, screenInteractive = powerManager.isInteractive
        ))
    }
    fun history(): Flow<List<BatterySnapshot>> = dao.observeRecent()
    fun historySince(time: Long): Flow<List<BatterySnapshot>> = dao.observeSince(time)
    suspend fun clearHistory() = dao.clearAll()
}
