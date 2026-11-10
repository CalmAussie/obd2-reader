package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarReadingsTest {
    @Test fun defaultsAreTheOriginalFour() {
        assertEquals(listOf("05", "11", "2F", "VOLT"), CarReadings.decode(null))
        assertEquals(listOf("05", "11", "2F", "VOLT"), CarReadings.decode(""))
    }

    @Test fun choicesLeaveOutSpeedAndEngineSpeed() {
        val keys = CarReadings.CHOICES.map { it.key }
        assertFalse("0C" in keys)
        assertFalse("0D" in keys)
        assertTrue(Boost.KEY in keys)
        assertTrue("VOLT" in keys)
    }

    @Test fun savedChoicesRoundTrip() {
        val pick = listOf(Boost.KEY, "0F", "05", "VOLT")
        assertEquals(pick, CarReadings.decode(CarReadings.encode(pick)))
    }

    @Test fun alwaysFourWithNoRepeats() {
        val got = CarReadings.decode("BOOST,BOOST,0D,junk")
        assertEquals(4, got.size)
        assertEquals("BOOST", got[0])
        assertEquals(got.distinct(), got)
        assertFalse("0D" in got)
    }
}
