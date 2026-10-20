package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTimerTest {
    /** A steady launch at [accel] km/h per second, read every [stepMs]. */
    private fun launch(p: PerformanceTimer, accel: Double, stepMs: Long = 200, from: Double = 0.0, holdMs: Long = 1500, until: Double = 300.0): PerformanceTimer.State {
        var t = 1000L
        if (from == 0.0) { while (t < 1000 + holdMs) { p.update(t, 0.0); t += stepMs } }
        else { p.update(t, 70.0); t += stepMs }
        var v: Double
        val start = t - stepMs
        while (p.state !is PerformanceTimer.State.Done && p.state !is PerformanceTimer.State.Aborted && (t - start) < 70_000) {
            v = from + accel * (t - start) / 1000.0
            p.update(t, minOf(v, until)); t += stepMs
        }
        return p.state
    }

    @Test fun zeroTo100() {
        val p = PerformanceTimer().apply { arm(PerformanceTimer.Mode.ZERO_100) }
        val s = launch(p, accel = 10.0) as PerformanceTimer.State.Done
        assertEquals(10.0, s.result.seconds, 0.05)   // 100 km/h at 10 km/h per second
    }

    @Test fun eightyTo120() {
        val p = PerformanceTimer().apply { arm(PerformanceTimer.Mode.MID_80_120) }
        val s = launch(p, accel = 8.0, from = 70.0) as PerformanceTimer.State.Done
        assertEquals(5.0, s.result.seconds, 0.05)    // 40 km/h at 8 km/h per second
    }

    @Test fun quarterMile() {
        val p = PerformanceTimer().apply { arm(PerformanceTimer.Mode.QUARTER) }
        val s = launch(p, accel = 10.0) as PerformanceTimer.State.Done
        // d = a t² / 2 with a = 10/3.6 m/s²: 402.336 m takes about 17.0 s, at about 170 km/h.
        assertEquals(17.02, s.result.seconds, 0.1)
        assertEquals(170.2, s.result.trapSpeed!!, 1.0)
    }

    @Test fun mustBeStillFirst() {
        val p = PerformanceTimer().apply { arm(PerformanceTimer.Mode.ZERO_100) }
        p.update(0, 30.0)
        assertTrue(p.state is PerformanceTimer.State.Staging)
        p.update(200, 0.0); p.update(600, 0.0)
        assertTrue(p.state is PerformanceTimer.State.Staging)   // not for a second yet
        p.update(1300, 0.0)
        assertEquals(PerformanceTimer.State.Ready, p.state)
    }

    @Test fun stoppingAbortsTheRun() {
        val p = PerformanceTimer().apply { arm(PerformanceTimer.Mode.ZERO_100) }
        var t = 0L
        repeat(8) { p.update(t, 0.0); t += 200 }
        p.update(t, 10.0); t += 200
        p.update(t, 20.0); t += 200
        p.update(t, 0.0)
        assertTrue(p.state is PerformanceTimer.State.Aborted)
    }

    @Test fun savesHistory() {
        val r = listOf(PerformanceTimer.Result(PerformanceTimer.Mode.QUARTER, 15.2, 140.5, 99L), PerformanceTimer.Result(PerformanceTimer.Mode.ZERO_100, 8.4, null, 100L))
        assertEquals(r, PerformanceTimer.decode(PerformanceTimer.encode(r)))
        assertEquals(emptyList<PerformanceTimer.Result>(), PerformanceTimer.decode(null))
    }

    @Test fun zeroTo60Mph() {
        val p = PerformanceTimer().apply { arm(PerformanceTimer.Mode.ZERO_60) }
        val s = launch(p, accel = 10.0) as PerformanceTimer.State.Done
        assertEquals(9.656, s.result.seconds, 0.05)   // 96.56 km/h at 10 km/h per second
    }

    @Test fun offeredRunsFollowTheUnits() {
        assertEquals(listOf("ZERO_100", "MID_80_120", "QUARTER"), PerformanceTimer.Mode.offered(false).map { it.name })
        assertEquals(listOf("ZERO_60", "MID_50_70", "QUARTER"), PerformanceTimer.Mode.offered(true).map { it.name })
    }
}
