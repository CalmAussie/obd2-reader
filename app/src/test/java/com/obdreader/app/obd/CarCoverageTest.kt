package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarCoverageTest {
    @Test fun pedalStandsInForMissingThrottle() {
        val got = CarCoverage.standInsFor(setOf(0x0C, 0x0D, 0x49))
        assertEquals(listOf("11"), got.map { it.forHex })
    }

    @Test fun noStandInWhenTheRealOneIsThere() {
        assertTrue(CarCoverage.standInsFor(setOf(0x11, 0x49, 0x05, 0x67)).isEmpty())
    }

    @Test fun coolantSensorDecodes() {
        val s = CarCoverage.STAND_INS.first { it.forHex == "05" }
        assertEquals(90.0, s.pid.decode(intArrayOf(0x01, 130, 0)), 1e-9)
        assertEquals(85.0, s.pid.decode(intArrayOf(0x02, 0, 125)), 1e-9)
    }

    @Test fun fordProbesUsePcmHeaderOnPwm() {
        val list = CarCoverage.enhancedFor("Ford", "Falcon", "1")
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.header == "C410F1" && it.request.startsWith("22") })
        assertTrue(CarCoverage.enhancedFor("Mazda", "Tribute", "1").isNotEmpty())   // Ford-built
        assertTrue(CarCoverage.enhancedFor("Mazda", "CX-5", "6").isEmpty())         // Mazda's own electronics
        assertTrue(CarCoverage.enhancedFor("Toyota", "Corolla", "6").isEmpty())
    }

    @Test fun names() {
        assertEquals("Fuel level", CarCoverage.name(0x2F))
        assertEquals("Reading 99", CarCoverage.name(0x99))
    }
}
