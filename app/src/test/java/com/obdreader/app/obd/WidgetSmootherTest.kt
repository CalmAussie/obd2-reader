package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSmootherTest {
    @Test
    fun easesTowardsEachReading() {
        val s = WidgetSmoother()
        assertEquals(800.0, s.step(mapOf("0C" to 800.0), 400)["0C"]!!, 0.0)          // first reading: straight away
        val steps = (1..6).map { s.step(mapOf("0C" to 3200.0), 400)["0C"]!! }
        // Rolls up a step at a time, never past the reading, and lands on it within a couple of seconds.
        assertTrue(steps.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(steps.first() in 1500.0..2500.0)
        assertTrue(steps.all { it <= 3200.0 })
        assertEquals(3200.0, steps.last(), 0.0)
        // The car stops giving it: N/A at once, then a fresh start.
        assertNull(s.step(mapOf("0C" to null), 400)["0C"])
        assertEquals(900.0, s.step(mapOf("0C" to 900.0), 400)["0C"]!!, 0.0)
    }
}
