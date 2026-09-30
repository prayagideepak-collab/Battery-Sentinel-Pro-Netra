package com.example.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.Locale

/**
 * Investigates potential root causes of high temperature when critical thermal control is active (>40°C).
 * Distinguishes external/environmental heat vs internal device-generated heat using public Android Sensor APIs.
 * Only activated conditionally on thermal events to avoid battery drain, and stopped upon recovery (<=35°C).
 */
class ThermalCauseInvestigator(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val ambientTempSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)

    private var isInvestigating = false
    private var lastAmbientReading: Float? = null

    val isSensorAvailable: Boolean
        get() = ambientTempSensor != null

    fun startInvestigation() {
        if (!isInvestigating && ambientTempSensor != null) {
            isInvestigating = true
            sensorManager?.registerListener(this, ambientTempSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopInvestigation() {
        if (isInvestigating) {
            isInvestigating = false
            sensorManager?.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            val ambient = event.values.firstOrNull()
            if (ambient != null && ambient > -50f && ambient < 100f) {
                lastAmbientReading = ambient
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun diagnoseThermalCause(batteryTempCelsius: Float): String {
        val ambient = lastAmbientReading
        return when {
            ambient != null && ambient >= 35.0f -> {
                "External Environmental Heat: Ambient temperature is high (${String.format(Locale.US, "%.1f", ambient)}°C). Move device to a cooler shade."
            }
            ambient != null && ambient < 30.0f && batteryTempCelsius >= 40.0f -> {
                "Internal Component Heat: Ambient is normal (${String.format(Locale.US, "%.1f", ambient)}°C). Thermal stress caused by active processor or charging workload."
            }
            ambientTempSensor == null -> {
                "Internal Component Heat (Hardware ambient sensor unavailable on this device)."
            }
            else -> {
                "Evaluating thermal dissipation conditions..."
            }
        }
    }
}
