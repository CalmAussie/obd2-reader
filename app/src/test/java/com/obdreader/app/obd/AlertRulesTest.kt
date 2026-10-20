package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {
    private fun run(a: AlertRules, from: Long, to: Long, rpm: Double, cool: Double, volts: Double) =
        (from..to step 1000).flatMap { a.readings(it, rpm, cool, null, volts) }

    @Test fun hotCoolantWarnsOnceUntilItCools() {
        val a = AlertRules()
        assertTrue(run(a, 0, 3000, 900.0, 112.0, 14.2).isEmpty())          // not for long enough yet
        assertEquals(listOf(AlertRules.Kind.COOLANT_HOT), run(a, 4000, 20000, 900.0, 112.0, 14.2).map { it.kind })
        assertTrue(run(a, 21000, 30000, 900.0, 108.0, 14.2).isEmpty())     // still warm: no repeat
        run(a, 31000, 32000, 900.0, 95.0, 14.2)                           // properly cooled
        assertEquals(1, run(a, 33000, 40000, 900.0, 111.0, 14.2).size)    // warns again
    }

    @Test fun lowVoltageWithEngineRunningIsNotCharging() {
        val a = AlertRules()
        val got = run(a, 0, 25000, 2000.0, 90.0, 12.3)
        assertEquals(listOf(AlertRules.Kind.NOT_CHARGING), got.map { it.kind })
    }

    @Test fun normalBatteryWithEngineOffIsFine() {
        assertTrue(run(AlertRules(), 0, 60000, 0.0, 60.0, 12.4).isEmpty())
    }

    @Test fun overcharging() {
        assertEquals(listOf(AlertRules.Kind.OVERCHARGING), run(AlertRules(), 0, 15000, 2000.0, 90.0, 15.6).map { it.kind })
    }

    @Test fun onlyCodesThatAppearDuringTheDriveAreNew() {
        val a = AlertRules()
        assertTrue(a.codes(listOf("P0133")) { it }.isEmpty())              // already there when we connected
        assertTrue(a.codes(listOf("P0133")) { it }.isEmpty())
        val got = a.codes(listOf("P0133", "P0301")) { "Cylinder 1 misfire" }
        assertEquals(listOf("P0301"), got.map { it.code })
        assertEquals("Cylinder 1 misfire", got[0].text)
        assertTrue(a.codes(listOf("P0133", "P0301")) { it }.isEmpty())
    }
}
