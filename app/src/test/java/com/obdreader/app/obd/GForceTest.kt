package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class GForceTest {

    /**
     * A phone in a mount at an odd angle: the car's forward, left and up (x, y, z) turned into the
     * phone's own axes by a fixed rotation (tilted back 70°, turned 30° and rolled 10°).
     */
    private class Mount(yawDeg: Double = 30.0, pitchDeg: Double = 70.0, rollDeg: Double = 10.0) {
        private val m: Array<DoubleArray>
        init {
            val (a, b, c) = listOf(yawDeg, pitchDeg, rollDeg).map { Math.toRadians(it) }
            val rz = arrayOf(doubleArrayOf(cos(a), -sin(a), 0.0), doubleArrayOf(sin(a), cos(a), 0.0), doubleArrayOf(0.0, 0.0, 1.0))
            val ry = arrayOf(doubleArrayOf(cos(b), 0.0, sin(b)), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(-sin(b), 0.0, cos(b)))
            val rx = arrayOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, cos(c), -sin(c)), doubleArrayOf(0.0, sin(c), cos(c)))
            m = mul(rx, mul(ry, rz))
        }
        private fun mul(p: Array<DoubleArray>, q: Array<DoubleArray>) = Array(3) { i -> DoubleArray(3) { j -> (0..2).sumOf { p[i][it] * q[it][j] } } }
        /** What the phone's sensor reads for a car pushing [fwd] forward and [left] to the left (m/s²), gravity included. */
        fun read(fwd: Double, left: Double): DoubleArray {
            val car = doubleArrayOf(fwd, left, GForce.G)
            return DoubleArray(3) { i -> (0..2).sumOf { m[i][it] * car[it] } }
        }
    }

    private fun feed(g: GForce, mount: Mount, fromMs: Long, toMs: Long, speedAt: (Long) -> Double, fwd: Double, left: Double, noise: Double = 0.0) {
        var t = fromMs
        var i = 0
        while (t < toMs) {
            // Speed arrives about 5 times a second, the sensor about 50.
            if (i % 10 == 0) g.speed(speedAt(t), t)
            val r = mount.read(fwd + noise * sin(i * 1.7), left + noise * cos(i * 2.3))
            g.sensor(r[0], r[1], r[2], t)
            t += 20; i++
        }
    }

    @Test fun learnsTheMountThenReadsForwardAndSideways() {
        val g = GForce()
        val mount = Mount()
        // Parked for 3 s: learns down. Nothing to show yet.
        feed(g, mount, 0, 3_000, { 0.0 }, 0.0, 0.0)
        assertFalse(g.calibrated)
        assertNull(g.forwardG)
        // Pulls away at 2 m/s² for 8 s (0 to 57.6 km/h): learns forward.
        feed(g, mount, 3_000, 11_000, { t -> (t - 3_000) / 1000.0 * 2.0 * 3.6 }, 2.0, 0.0, noise = 0.3)
        assertTrue(g.calibrated)
        assertEquals(2.0 / GForce.G, g.forwardG!!, 0.03)
        assertEquals(0.0, g.sideG!!, 0.03)
        // A right-hand bend at a steady 57.6 km/h: 4 m/s² to the right (negative left).
        feed(g, mount, 11_000, 14_000, { 57.6 }, 0.0, -4.0)
        assertEquals(4.0 / GForce.G, g.sideG!!, 0.03)
        assertEquals(0.0, g.forwardG!!, 0.03)
        // Hard braking: 5 m/s².
        feed(g, mount, 14_000, 16_000, { t -> 57.6 - (t - 14_000) / 1000.0 * 5.0 * 3.6 }, -5.0, 0.0)
        assertEquals(-5.0 / GForce.G, g.forwardG!!, 0.03)
        assertEquals(5.0 / GForce.G, g.peakBrake, 0.03)
        assertEquals(4.0 / GForce.G, g.peakSide, 0.03)
        assertEquals(1, g.harshBrakes)     // 0.51 g for 2 s: once
        assertEquals(0, g.harshCorners)    // 0.41 g: under the line
        assertEquals(0, g.harshAccels)
    }

    @Test fun movingThePhoneInItsMountStartsAgain() {
        val g = GForce()
        val first = Mount()
        feed(g, first, 0, 3_000, { 0.0 }, 0.0, 0.0)
        feed(g, first, 3_000, 11_000, { t -> (t - 3_000) / 1000.0 * 2.0 * 3.6 }, 2.0, 0.0)
        assertTrue(g.calibrated)
        // Stopped, and the phone turned round in its mount.
        feed(g, Mount(yawDeg = 120.0, pitchDeg = 20.0), 11_000, 14_000, { 0.0 }, 0.0, 0.0)
        assertFalse(g.calibrated)
        assertNull(g.forwardG)
    }

    @Test fun noSpeedNoReading() {
        val g = GForce()
        feed(g, Mount(), 0, 10_000, { 0.0 }, 0.0, 0.0)
        // Parked all along: never learns forward.
        assertFalse(g.calibrated)
        assertNull(g.sideG)
    }

    @Test fun aDrivesPeaksGoInItsSummary() {
        val g = GForce()
        assertNull(g.drive()) // nothing measured yet
        // The demo car's readings: already in the car's directions.
        var t = 0L
        repeat(100) { g.direct(-0.5, 0.0, t); t += 20 }   // 2 s of hard braking
        repeat(100) { g.direct(0.0, -0.2, t); t += 20 }   // a gentle left bend
        val m = g.drive()!!
        assertEquals(0.5, m.peakBrake, 0.01)
        assertEquals(0.2, m.peakSide, 0.01)
        assertEquals(1, m.harshBrakes)
        assertEquals(0, m.harshCorners)
        assertEquals("1 harsh brake", m.harshText())
        // Saved with the drive's summary and read back.
        val trip = TripSummary(1_000, 2_000, 12.3, 900, 1000, 60, 80.0, 40.0, null, emptyMap(), m)
        val back = TripSummary.decode(trip.encode())!!
        assertEquals(0.5, back.motion!!.peakBrake, 0.001)
        assertEquals(1, back.motion!!.harshBrakes)
        // A summary saved before g-force existed still reads, without it.
        assertNull(TripSummary.decode("start=1;end=2;km=1.0;drive=60;conn=60;idle=0")!!.motion)
        // A new drive starts its peaks again.
        g.resetDrive()
        assertNull(g.drive())
    }

    @Test fun driveTrackReadsTheGColumns() {
        val csv = listOf(
            "Time,Seconds,Engine RPM (rpm),Speed (km/h),Forward g (g),Sideways g (g)",
            "x,0.0,800,0,0.00,0.00",
            "x,1.0,2000,20,0.30,-0.10",
        )
        val d = DriveTrack.parse(csv)!!
        assertTrue(d.hasG)
        assertEquals(0.15, d.at(0.5).gForward!!, 0.001)
        assertEquals(-0.05, d.at(0.5).gSide!!, 0.001)
        // An older drive without them: no g-meter.
        val old = DriveTrack.parse(listOf("Time,Seconds,Engine RPM (rpm)", "x,0.0,800", "x,1.0,900"))!!
        assertFalse(old.hasG)
        assertNull(old.at(0.5).gForward)
    }

    @Test fun aKnockIsKeptAtFiveG() {
        val g = GForce()
        g.direct(0.0, 0.0, 0)
        g.direct(-30.0, 12.0, 1000)   // the phone knocked out of its mount
        val m = g.drive()!!
        org.junit.Assert.assertTrue(m.peakBrake <= GForce.MAX_G && m.peakSide <= GForce.MAX_G)
        // The summary still reads back with its g-force.
        val t = TripSummary.decode(TripSummary(1_000, 2_000, 1.0, 60, 60, 0, 80.0, 40.0, null, emptyMap(), m).encode())
        org.junit.Assert.assertNotNull(t?.motion)
    }
}
