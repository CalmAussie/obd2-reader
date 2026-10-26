package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveTrackTest {
    private val csv = listOf(
        "Time,Seconds,Engine RPM (rpm),Speed (km/h),Throttle (%),Engine load (%),Coolant (°C),Battery (V)",
        "2026-10-20 21:00:00.000,0.2,800.00,0.00,0.00,20.00,85.00,14.10",
        "2026-10-20 21:00:00.200,0.4,1200.00,10.00,30.00,40.00,85.00,14.10",
        "2026-10-20 21:00:00.400,0.6,1600.00,20.00,,40.00,86.00,14.10",
        "2026-10-20 21:00:00.600,0.8,2000.00,30.00,40.00,40.00,86.00,14.10",
    )

    @Test fun readsTheAppsCsv() {
        val t = DriveTrack.parse(csv)!!
        assertEquals(4, t.size)
        assertEquals(0.8, t.duration, 1e-9)
    }

    @Test fun glidesBetweenRows() {
        val s = DriveTrack.parse(csv)!!.at(0.3)
        assertEquals(1000.0, s.rpm!!, 1e-6)
        assertEquals(5.0, s.speed!!, 1e-6)
    }

    @Test fun blankCellsUseTheNeighbour() {
        assertEquals(30.0, DriveTrack.parse(csv)!!.at(0.5).throttle!!, 1e-6)
    }

    @Test fun clampsOutsideTheDrive() {
        val t = DriveTrack.parse(csv)!!
        assertEquals(30.0, t.at(99.0).speed!!, 1e-6)
        assertEquals(0.0, t.at(-5.0).speed!!, 1e-6)
    }

    @Test fun topSpeedSoFar() {
        val t = DriveTrack.parse(csv)!!
        assertEquals(20.0, t.topSpeedUntil(0.8)!!, 1e-6)   // 30 was only one reading long
        assertNull(t.topSpeedUntil(0.2))
    }

    @Test fun rejectsOtherFiles() {
        assertNull(DriveTrack.parse(listOf("a,b,c", "1,2,3")))
        assertNotNull(DriveTrack.parse(listOf("\"Seconds\",Speed (km/h)", "1.0,50")))
    }

    @Test
    fun positionFollowsTheRouteAndHoldsInATunnel() {
        val lines = mutableListOf("Time,Seconds,Engine RPM (rpm),Speed (km/h),Latitude,Longitude,Location accuracy (m)")
        for (i in 0..10) lines += "t,$i,2000,72,${-33.9 + i * 0.001},151.2,5"
        for (i in 11..15) lines += "t,$i,2000,72,,,"        // 5 s with no fix
        for (i in 16..20) lines += "t,$i,2000,72,${-33.9 + i * 0.001},151.2,5"
        val d = DriveTrack.parse(lines)!!
        assertNotNull(d.route)
        assertEquals(-33.9 + 5.5 * 0.001, d.position(5.5)!!.first, 1e-9)          // between two fixes
        assertEquals(-33.9 + 13.0 * 0.001, d.position(13.0)!!.first, 1e-9)        // across the short gap
        val p = d.route!!.projector(300f, 300f, 26f)!!
        val (x0, y0) = p(-33.9, 151.2); val (x1, y1) = p(-33.88, 151.2)
        assertTrue(y1 < y0 && kotlin.math.abs(x1 - x0) < 1f) // north is up
    }

    @Test
    fun noRouteNoPosition() {
        val d = DriveTrack.parse(listOf("Time,Seconds,Speed (km/h)", "t,0,10", "t,1,12"))!!
        assertNull(d.route); assertNull(d.position(0.5))
    }
}
