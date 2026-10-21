package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoastTest {

    @Test
    fun sameDriveSameLine() {
        assertEquals(Roast.line(DrivingScore.Style.EVERYDAY, 62, 1234L), Roast.line(DrivingScore.Style.EVERYDAY, 62, 1234L))
    }

    @Test
    fun bandsAndStylesDiffer() {
        assertNotEquals(Roast.line(DrivingScore.Style.EVERYDAY, 95, 7L), Roast.line(DrivingScore.Style.EVERYDAY, 20, 7L))
        assertNotEquals(Roast.line(DrivingScore.Style.RACING, 95, 7L), Roast.line(DrivingScore.Style.EFFICIENCY, 95, 7L))
        assertTrue(Roast.line(DrivingScore.Style.EVERYDAY, null, 7L).isNotBlank())
        // Negative seeds (any timestamp) still pick a line.
        assertTrue(Roast.line(DrivingScore.Style.RACING, 50, -3L).isNotBlank())
    }

    @Test
    fun linesAreShortEnoughForTheCard() {
        for (l in Roast.all) assertTrue(l, l.length <= 75)
    }
}
