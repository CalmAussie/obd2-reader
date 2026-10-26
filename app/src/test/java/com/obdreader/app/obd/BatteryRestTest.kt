package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryRestTest {
    @Test
    fun chargeFromRestingVoltage() {
        assertEquals(100, BatteryRest.charge(12.8))
        assertEquals(80, BatteryRest.charge(12.50))
        assertEquals(50, BatteryRest.charge(12.10))
        assertNull(BatteryRest.charge(14.1)) // charging, not resting
        assertTrue(BatteryRest.describe(12.5).endsWith("about 80% charged"))
    }

    private fun night(startV: Double, perHour: Double, hours: Int): List<BatteryRest.Sample> =
        (0..hours * 12).map { i -> BatteryRest.Sample(i * 300_000L, startV - perHour * i / 12.0 - if (i < 24) 0.1 * (24 - i) / 24 else 0.0) }

    @Test
    fun aHealthyNight() {
        val d = BatteryRest.drain(night(12.6, 0.005, 10), 0L)!!
        assertTrue(!d.draining)
        assertEquals(8.0, d.hours, 0.2) // the first two hours are left out
    }

    @Test
    fun aBatteryBeingDrained() {
        assertTrue(BatteryRest.drain(night(12.6, 0.05, 10), 0L)!!.draining)
    }

    @Test
    fun tooSoonToTell() {
        assertNull(BatteryRest.drain(night(12.6, 0.05, 4), 0L))
    }

    @Test
    fun samplesRoundTrip() {
        val s = listOf(BatteryRest.Sample(1, 12.61), BatteryRest.Sample(2, 12.59))
        assertEquals(s, BatteryRest.decode(BatteryRest.encode(s)))
    }
}
