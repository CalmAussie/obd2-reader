package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class TraceSeedTest {

    @Test fun eachPointIsTheNewestReadingAtItsMoment() {
        // Readings at 0, 250, 600 and 900 ms; points every 300 ms ending at 900.
        val p = TraceSeed.steps(listOf(0L, 250L, 600L, 900L), listOf(1.0, 2.0, 3.0, 4.0), nowMs = 900, stepMs = 300, count = 4)
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), p)
    }

    @Test fun beforeTheFirstReadingThereIsNothing() {
        val p = TraceSeed.steps(listOf(1_000L), listOf(5.0), nowMs = 1_300, stepMs = 300, count = 4)
        assertEquals(listOf(null, null, 5.0, 5.0), p)
    }

    @Test fun aStaleReadingLeavesAGap() {
        // Nothing between 0 and 5 s: the trace breaks rather than drawing a flat line from an old reading.
        val p = TraceSeed.steps(listOf(0L, 5_000L), listOf(1.0, 2.0), nowMs = 5_000, stepMs = 1_000, count = 6)
        assertEquals(listOf(1.0, 1.0, 1.0, null, null, 2.0), p)
        assertEquals(listOf(2.0), TraceSeed.latestRun(p))
    }

    @Test fun noReadingsNoTrace() {
        val p = TraceSeed.steps(emptyList(), emptyList(), nowMs = 10_000, stepMs = 320, count = 94)
        assertEquals(94, p.size)
        assertEquals(emptyList<Double>(), TraceSeed.latestRun(p))
    }

    @Test fun thirtySecondsOfFiveReadingsASecond() {
        val times = (0 until 300).map { it * 200L }            // 60 s of readings
        val values = times.map { it / 1000.0 }
        val p = TraceSeed.steps(times, values, nowMs = times.last(), stepMs = 320, count = 94)
        assertEquals(94, TraceSeed.latestRun(p).size)
        assertEquals(59.8, p.last()!!, 0.001)
        assertEquals(59.8 - 93 * 0.32, p.first()!!, 0.2)
    }
}
