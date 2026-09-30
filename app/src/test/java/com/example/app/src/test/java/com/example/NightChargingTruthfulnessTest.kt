package com.example

import com.example.ai.NightChargingThrottler
import com.example.model.BatteryTelemetry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightChargingTruthfulnessTest {
    @Test
    fun `enabled preference does not claim charger control`() {
        val status = NightChargingThrottler.evaluateNightThrottle(
            BatteryTelemetry(level = 80, isCharging = true, temperature = 42f),
            isFeatureEnabled = true,
            targetWakeHour = 7
        )
        assertFalse(status.isThrottleActive)
        assertTrue(status.statusHeadline.contains("unavailable"))
        assertTrue(status.detailedAdvice.contains("cannot set charger power"))
    }

    @Test
    fun `disabled preference and missing telemetry stay unavailable`() {
        val status = NightChargingThrottler.evaluateNightThrottle(
            BatteryTelemetry(), isFeatureEnabled = false
        )
        assertFalse(status.isThrottleActive)
        assertTrue(status.detailedAdvice.contains("cannot set charger power"))
    }
}
