package com.example.service

import com.example.model.CanonicalChargingSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargingSpeedEngineTest {

    private val engine = ChargingSpeedEngine()

    @Test
    fun convertsMilliampAndMillivoltToWattsCorrectly() {
        val result = engine.calculate(isCharging = true, voltageMv = 4_000, currentMa = 1_000)

        assertEquals(4.0f, result.rawPowerWatts!!, 0.001f)
        assertEquals(CanonicalChargingSpeed.SLOW, result.speedCategory)
    }

    @Test
    fun classifiesCanonicalPowerThresholds() {
        assertEquals(CanonicalChargingSpeed.NORMAL, engine.calculate(true, 4_000, 1_250).speedCategory)
        assertEquals(CanonicalChargingSpeed.FAST, engine.calculate(true, 4_000, 2_500).speedCategory)
        assertEquals(CanonicalChargingSpeed.SUPER_FAST, engine.calculate(true, 4_000, 5_000).speedCategory)
        assertEquals(CanonicalChargingSpeed.ULTRA_FAST, engine.calculate(true, 4_000, 10_000).speedCategory)
    }

    @Test
    fun dischargePowerDoesNotBecomeChargingSpeed() {
        val result = engine.calculate(isCharging = false, voltageMv = 4_000, currentMa = -1_000)

        assertEquals(CanonicalChargingSpeed.UNAVAILABLE, result.speedCategory)
        assertEquals(4.0f, result.consumptionPowerWatts!!, 0.001f)
    }

    @Test
    fun missingTelemetryDoesNotInventPower() {
        val result = engine.calculate(isCharging = true, voltageMv = null, currentMa = null)

        assertNull(result.rawPowerWatts)
        assertEquals(CanonicalChargingSpeed.UNAVAILABLE, result.speedCategory)
    }
}
