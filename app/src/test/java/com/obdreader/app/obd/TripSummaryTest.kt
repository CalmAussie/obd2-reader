package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSummaryTest {
    private val trip = TripSummary(
        1000L, 2000L, 12.4, 1680, 1900, 220, 112.0, 46.5, 9.8,
        mapOf(DrivingScore.Style.EFFICIENCY to 72, DrivingScore.Style.EVERYDAY to 81, DrivingScore.Style.RACING to null),
    )

    @Test fun savesAndLoads() {
        assertEquals(trip, TripSummary.decode(trip.encode()))
    }

    @Test fun badTextLoadsAsNothing() {
        assertNull(TripSummary.decode("start=1;end=oops"))
        assertNull(TripSummary.decode(null))
    }

    @Test fun shortStopIsNotADrive() {
        assertTrue(trip.isDrive)
        assertFalse(trip.copy(distanceKm = 0.05, drivingSeconds = 10).isDrive)
    }

    @Test fun formats() {
        assertEquals("45 s", TripSummary.duration(45))
        assertEquals("28 min", TripSummary.duration(1680))
        assertEquals("1 h 12 min", TripSummary.duration(4320))
        assertEquals("12.4 km", TripSummary.distance(12.43))
        assertEquals("128 km", TripSummary.distance(128.2))
    }
}
