package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvBatteryTest {

    @Test fun standardChargeReading() {
        val p = Pids.byHex("5B")!!
        assertEquals(100.0, p.decode(intArrayOf(255)), 1e-9)
        assertEquals(50.2, p.decode(intArrayOf(128)), 0.1)
    }

    @Test fun makerReadingsOnlyForTheirCars() {
        val leaf = EvBattery.makerReadings("Nissan", "Leaf", 2015).map { it.id }
        assertTrue("leaf.soc" in leaf && "leaf.hv_volts" in leaf)
        assertTrue(leaf.none { it.startsWith("zoe.") })
        assertTrue(EvBattery.makerReadings("Nissan", "Leaf", 2019).isEmpty())   // a newer pack, layout not known
        assertTrue(EvBattery.makerReadings("Renault", "Zoe", 2018).any { it.id == "zoe.soh" })
        assertTrue(EvBattery.makerReadings("Ford", "Focus", 2015).isEmpty())   // Ford's readings aren't battery ones
        assertTrue(EvBattery.makerReadings("Toyota", "Prius", 2015).isEmpty())
        // Every request is a read (21 or 22), never anything that could wake or command the battery.
        EvBattery.makerReadings("Nissan", "Leaf", 2015).plus(EvBattery.makerReadings("Renault", "Zoe", 2018)).forEach {
            assertTrue(it.request, it.request.take(2) in setOf("21", "22"))
            assertTrue(ReadOnlyGuard.isAllowed(it.request))
        }
    }

    @Test fun chargePrefersTheStandardReading() {
        val soc = EvBattery.makerReadings("Nissan", "Leaf", 2015).first { it.id == "leaf.soc" }
        val volts = EvBattery.makerReadings("Nissan", "Leaf", 2015).first { it.id == "leaf.hv_volts" }
        assertEquals(64.0, EvBattery.charge(64.0, mapOf(soc to 70.0))!!, 1e-9)
        assertEquals(70.0, EvBattery.charge(null, mapOf(volts to 380.0, soc to 70.0))!!, 1e-9)
        assertNull(EvBattery.charge(null, mapOf(volts to 380.0)))
        assertNull(EvBattery.charge(140.0, emptyMap()))
    }

    private fun log(vararg pts: Pair<Double, Double>) = EvBattery.ChargeLog().apply { pts.forEach { (min, pct) -> add((min * 60_000).toLong(), pct) } }

    @Test fun chargingRateFromTheTrend() {
        // 1 % every 2 minutes = 30 % an hour, read in 0.5 % steps every 10 seconds.
        val l = EvBattery.ChargeLog()
        for (s in 0..600 step 10) l.add(s * 1000L, 40.0 + Math.floor(s / 60.0) * 0.5)
        assertEquals(30.0, l.ratePerHour()!!, 2.0)
        assertEquals(EvBattery.State.CHARGING, l.state())
        assertTrue(EvBattery.describe(l)!!.startsWith("Charging: +"))
        // 80 % from about 45 % at 30 % an hour: about 70 minutes.
        assertEquals(70.0, l.minutesTo(80.0)!!.toDouble(), 6.0)
        assertNull(l.minutesTo(40.0))
    }

    @Test fun notEnoughToTellYet() {
        assertNull(log(0.0 to 50.0, 1.0 to 50.5).ratePerHour())             // two points
        assertNull(log(0.0 to 50.0, 1.0 to 50.5, 2.0 to 51.0).ratePerHour()) // two minutes only
        assertEquals(EvBattery.State.STEADY, log(0.0 to 50.0, 2.0 to 50.0, 4.0 to 50.0).state())
        assertEquals(EvBattery.State.USING, log(0.0 to 50.0, 2.0 to 49.0, 4.0 to 48.0).state())
    }

    @Test fun aLongGapStartsAgain() {
        val l = log(0.0 to 20.0, 2.0 to 21.0, 4.0 to 22.0)
        l.add(30 * 60_000L, 60.0)
        assertEquals(1, l.all.size)
        assertNull(l.ratePerHour())
        l.add(31 * 60_000L, 200.0)    // out of range: ignored
        l.add(29 * 60_000L, 61.0)     // older than the last: ignored
        assertEquals(1, l.all.size)
    }

    @Test fun powerFromThePackSize() {
        // A 24 kWh pack charging at 25 % an hour takes in about 6 kW.
        val kwh = EvBattery.packKwh(66.2, 362.0)!!
        assertEquals(24.0, kwh, 0.1)
        assertEquals(6.0, EvBattery.powerKw(25.0, kwh)!!, 0.1)
        assertNull(EvBattery.powerKw(25.0, null))
        assertNull(EvBattery.packKwh(null, 362.0))
        assertEquals("1 h 25 min", EvBattery.duration(85))
        assertEquals("2 h", EvBattery.duration(120))
        assertEquals("40 min", EvBattery.duration(40))
    }
}
