package com.obdreader.app

import com.obdreader.app.ui.ShiftLights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftLightsTest {
    @Test fun offWithNoReading() {
        assertEquals(0, ShiftLights.lit(null, 6500f))
        assertEquals(0, ShiftLights.lit(0f, 6500f))
    }

    @Test fun oneLightAtIdle() {
        assertEquals(1, ShiftLights.lit(800f, 6500f))
    }

    @Test fun climbsThroughEverydayRevs() {
        val revs = listOf(800f, 1500f, 2000f, 2500f, 3000f, 4000f, 5000f, 6000f)
        val lit = revs.map { ShiftLights.lit(it, 6500f) }
        assertTrue("lights $lit", lit.zipWithNext().all { (a, b) -> b > a })
        assertTrue("2,000 rpm lights ${lit[2]}", lit[2] >= 4)
    }

    @Test fun allOnJustBelowRedline() {
        assertEquals(15, ShiftLights.lit(6310f, 6500f))
        assertEquals(15, ShiftLights.lit(7000f, 6500f))
        assertEquals(14, ShiftLights.lit(6000f, 6500f))
    }

    @Test fun dieselRedline() {
        assertTrue(ShiftLights.lit(1800f, 4500f) >= 5)
    }
}
