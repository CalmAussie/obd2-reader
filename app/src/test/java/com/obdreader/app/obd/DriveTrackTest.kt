package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
