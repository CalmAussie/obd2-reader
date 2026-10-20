package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingScoreTest {

    /** Feeds [seconds] of driving at 10 readings a second, with speed and other readings from [at]. */
    private fun DrivingScore.drive(startMs: Long, seconds: Int, at: (Double) -> List<Double?>): Long {
        var t = startMs
        repeat(seconds * 10) { i ->
            val s = i / 10.0
            val (speed, rpm, throttle) = at(s)
            update(t, speed, rpm, throttle, null, 90.0)
            t += 100
        }
        return t
    }

    @Test
    fun noScoreUntilHalfAMinuteOfDriving() {
        val d = DrivingScore()
        d.drive(0, 20) { listOf(60.0, 2000.0, 20.0) }
        assertNull(d.result(DrivingScore.Style.EVERYDAY).score)
        d.drive(20_000, 20) { listOf(60.0, 2000.0, 20.0) }
        assertNotNull(d.result(DrivingScore.Style.EVERYDAY).score)
    }

    @Test
    fun steadyGentleCruisingScoresHighForEverydayAndEfficiency() {
        val d = DrivingScore()
        d.drive(0, 120) { listOf(60.0, 1900.0, 18.0) }
        assertEquals(100, d.result(DrivingScore.Style.EVERYDAY).score)
        assertEquals(100, d.result(DrivingScore.Style.EFFICIENCY).score)
    }

    @Test
    fun harshStopAndGoScoresLowerThanSmoothDriving() {
        val smooth = DrivingScore()
        smooth.drive(0, 120) { s -> listOf(50.0 + 10 * kotlin.math.sin(s / 10), 2000.0, 25.0) }
        val harsh = DrivingScore()
        // 0 to 80 km/h in 4 s, then back to 5 km/h in 3 s, repeatedly.
        harsh.drive(0, 120) { s -> val c = s % 7; listOf(if (c < 4) 5 + c * 19 else 81 - (c - 4) * 25, 4500.0, 90.0) }
        val e1 = smooth.result(DrivingScore.Style.EVERYDAY).score!!
        val e2 = harsh.result(DrivingScore.Style.EVERYDAY).score!!
        assertTrue("smooth $e1 should beat harsh $e2", e1 > e2 + 30)
        assertTrue(smooth.result(DrivingScore.Style.EFFICIENCY).score!! > harsh.result(DrivingScore.Style.EFFICIENCY).score!!)
    }

    @Test
    fun racingRewardsFullThrottleHighInTheRevsAndPunishesTheLimiter() {
        val good = DrivingScore(redline = 7000f)
        good.drive(0, 60) { s -> listOf(40 + (s % 10) * 8, 5600.0, 100.0) }
        val limiter = DrivingScore(redline = 7000f)
        limiter.drive(0, 60) { s -> listOf(40 + (s % 10) * 8, 7300.0, 100.0) }
        val g = good.result(DrivingScore.Style.RACING)
        assertTrue(g.score!! > limiter.result(DrivingScore.Style.RACING).score!!)
        assertEquals(100, g.parts.first { it.name == "Rev range" }.score)
    }

    @Test
    fun racingPartsWithoutDataAreLeftOut() {
        val d = DrivingScore()
        d.drive(0, 60) { listOf(60.0, 2000.0, 20.0) } // never full throttle, never accelerating
        val r = d.result(DrivingScore.Style.RACING)
        assertNull(r.parts.first { it.name == "Rev range" }.score)
        assertNotNull(r.score)
    }

    @Test
    fun idlingCountsAgainstEfficiency() {
        val d = DrivingScore()
        val t = d.drive(0, 60) { listOf(60.0, 1900.0, 18.0) }
        d.drive(t, 60) { listOf(0.0, 800.0, 0.0) }
        val idle = d.result(DrivingScore.Style.EFFICIENCY).parts.first { it.name == "Idling" }.score!!
        assertTrue("idling half the time should cost points, got $idle", idle < 80)
    }

    @Test
    fun timesZeroToHundred() {
        val d = DrivingScore()
        val t = d.drive(0, 5) { listOf(0.0, 900.0, 0.0) }
        d.drive(t, 9) { s -> listOf(s * 12.5, 4000.0, 90.0) } // 100 km/h after 8 s
        assertEquals(8.0, d.best0to100!!, 0.3)
    }

    @Test
    fun topSpeedIsTheHighestHeldSpeed() {
        val d = DrivingScore()
        var t = 0L
        for (v in listOf(0.0, 40.0, 80.0, 112.0, 113.0, 90.0, 250.0, 60.0)) { d.update(t, v, 2000.0, 20.0, 30.0, 90.0); t += 200 }
        // 250 was a single glitched reading; 112–113 was really driven.
        assertEquals(112.0, d.topSpeed!!, 0.001)
        assertEquals(112.0, d.result(DrivingScore.Style.EVERYDAY).topSpeed!!, 0.001)
        d.reset()
        assertEquals(null, d.topSpeed)
    }
}
