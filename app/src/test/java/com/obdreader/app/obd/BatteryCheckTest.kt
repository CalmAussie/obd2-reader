package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryCheckTest {
    /** Ignition on for [offS] seconds at [rest] V, a 1 s crank dipping to [dip] V, then running at 14.2 V. */
    private fun start(w: BatteryCheck.Watch, rest: Double, dip: Double, offS: Int = 8): BatteryCheck.Result? {
        var t = 0L; var r: BatteryCheck.Result? = null
        repeat(offS * 4) { w.update(t, rest, 0.0); t += 250 }
        listOf(dip + 0.4, dip, dip + 0.2, dip + 0.6).forEach { v -> w.update(t, v, 220.0); t += 250 }
        repeat(8) { r = r ?: w.update(t, 14.2, 900.0); t += 250 }
        return r
    }

    @Test
    fun aHealthyStart() {
        val r = start(BatteryCheck.Watch(), 12.6, 10.4)!!
        assertEquals(12.6, r.restingV, 0.01)
        assertEquals(10.4, r.crankingV, 0.01)
        assertEquals(BatteryCheck.Rating.GOOD, r.rating)
    }

    @Test
    fun aWeakBattery() {
        assertEquals(BatteryCheck.Rating.WEAK, start(BatteryCheck.Watch(), 12.3, 9.1)!!.rating)
        assertEquals(BatteryCheck.Rating.FAIR, start(BatteryCheck.Watch(), 12.4, 9.8)!!.rating)
    }

    @Test
    fun connectingToARunningEngineGivesNothing() {
        val w = BatteryCheck.Watch()
        var t = 0L
        repeat(20) { assertNull(w.update(t, 14.1, 850.0)); t += 250 }
    }

    @Test
    fun keepsAHistory() {
        val a = BatteryCheck.Result(1, 12.6, 10.4); val b = BatteryCheck.Result(2, 12.5, 10.1)
        val h = BatteryCheck.addToHistory(BatteryCheck.addToHistory(null, a), b)
        assertEquals(listOf(2L, 1L), BatteryCheck.parseHistory(h).map { it.atMs })
        assertNotNull(BatteryCheck.decode(a.encode()))
    }
}
