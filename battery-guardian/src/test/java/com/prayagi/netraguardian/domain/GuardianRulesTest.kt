package com.prayagi.netraguardian.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuardianRulesTest {
    @Test fun convertsMillivoltsAndMicroampsToWatts() {
        assertEquals(4f, GuardianRules.batteryPowerWatts(4000, 1_000_000)!!, 0.001f)
    }

    @Test fun preservesNegativeDischargeDirection() {
        assertEquals(-2f, GuardianRules.batteryPowerWatts(4000, -500_000)!!, 0.001f)
    }

    @Test fun missingTelemetryRemainsUnavailable() {
        assertNull(GuardianRules.batteryPowerWatts(null, 1000))
    }

    @Test fun classifiesBatterySideChargingPower() {
        assertEquals(ChargingBand.STANDARD, GuardianRules.chargingBand(true, 8f))
        assertEquals(ChargingBand.SLOW, GuardianRules.chargingBand(true, 4.9f))
        assertEquals(ChargingBand.NOT_CHARGING, GuardianRules.chargingBand(false, 25f))
    }

    @Test fun mapsThermalThresholds() {
        assertEquals("Hot", GuardianRules.thermalZone(40f))
        assertEquals("Critical", GuardianRules.thermalZone(46f))
        assertEquals("Emergency", GuardianRules.thermalZone(50f))
        assertEquals("Unavailable", GuardianRules.thermalZone(null))
    }
}
