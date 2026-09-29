package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager

/**
 * Ultra-low power lifecycle & power state tracker.
 * Adapts engine behavior according to screen state and battery saver.
 */
class LifecycleAwarePolling(
    private val context: Context,
    private val onStateChanged: (isScreenOn: Boolean, isPowerSaveMode: Boolean) -> Unit
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var isRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isScreenOn = powerManager?.isInteractive ?: true
            val isPowerSave = powerManager?.isPowerSaveMode ?: false
            onStateChanged(isScreenOn, isPowerSave)
        }
    }

    fun start() {
        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            context.registerReceiver(screenReceiver, filter)
            isRegistered = true

            val isScreenOn = powerManager?.isInteractive ?: true
            val isPowerSave = powerManager?.isPowerSaveMode ?: false
            onStateChanged(isScreenOn, isPowerSave)
        }
    }

    fun stop() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(screenReceiver)
            } catch (_: Exception) {}
            isRegistered = false
        }
    }

    fun isScreenInteractive(): Boolean = powerManager?.isInteractive ?: true
    fun isPowerSaveMode(): Boolean = powerManager?.isPowerSaveMode ?: false
}
