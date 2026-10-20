package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThrottleCalibrationTest {
    @Test fun footOffAtIdleReadsZero() {
        val t = ThrottleCalibration()
        assertEquals(0.0, t.adjust(15.3), 0.001)
        assertEquals(0.0, t.adjust(15.3), 0.001)
    }

    @Test fun pressingThePedalRisesFromZero() {
        val t = ThrottleCalibration()
        t.adjust(15.0)
        assertEquals(50.0, t.adjust(57.5), 0.001)
        assertEquals(100.0, t.adjust(100.0), 0.001)
    }

    @Test fun learnsALowerClosedReading() {
        val t = ThrottleCalibration(saved = 20.0)
        assertTrue(t.adjust(16.0) == 0.0 && t.changed)
        assertEquals(16.0, t.closed!!, 0.001)
    }

    @Test fun startingWithThePedalDownDoesNotBecomeTheBaseline() {
        val t = ThrottleCalibration()
        assertEquals(60.0, t.adjust(60.0), 0.001) // can't be closed, shown as it is until idle is seen
        assertEquals(0.0, t.adjust(14.0), 0.001)
    }

    @Test fun ignoresAnImpossibleSavedValue() {
        assertEquals(null, ThrottleCalibration(saved = 80.0).closed)
    }
}
