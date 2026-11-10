package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class BoostTest {
    @Test fun boostIsManifoldLessOutsidePressure() {
        assertEquals(60.0, Boost.from(160.0, 100.0), 1e-9)
        assertEquals(-65.0, Boost.from(35.0, 100.0), 1e-9)   // vacuum at idle
    }

    @Test fun withoutBarometricPressureUsesSeaLevel() {
        assertEquals(160.0 - 101.3, Boost.from(160.0, null), 1e-9)
    }

    @Test fun implausibleBarometricPressureIsIgnored() {
        assertEquals(160.0 - 101.3, Boost.from(160.0, 0.0), 1e-9)
        assertEquals(160.0 - 101.3, Boost.from(160.0, 255.0), 1e-9)
    }

    @Test fun barometricPidDecodes() {
        assertEquals("33", Boost.BAROMETRIC.hex)
        assertEquals(98.0, Boost.BAROMETRIC.decode(intArrayOf(98)), 1e-9)
    }
}
