package com.obdreader.app.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeProbeTest {
    @Test
    fun asksTheCarEveryFewSecondsOrWhenTheVoltageMoves() {
        assertFalse(ReconnectPolicy.shouldProbe(1000, 12.55, 12.6))   // quiet: leave the car asleep
        assertTrue(ReconnectPolicy.shouldProbe(5000, 12.6, 12.6))     // time for the regular check
        assertTrue(ReconnectPolicy.shouldProbe(1000, 12.3, 12.6))     // the key turned: things switched on
        assertFalse(ReconnectPolicy.shouldProbe(1000, null, 12.6))
    }
}
