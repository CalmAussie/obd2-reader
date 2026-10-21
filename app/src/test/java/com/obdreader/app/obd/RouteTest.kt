package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {

    private val header = "Time,Seconds,Engine RPM (rpm),Speed (km/h),Latitude,Longitude,Location accuracy (m)"

    @Test
    fun readsTheRouteAndSkipsRepeatsAndBadFixes() {
        val r = Route.parse(listOf(
            header,
            "t,0.0,800,0,-33.8688,151.2093,5",
            "t,0.2,820,1,-33.8688,151.2093,5", // same fix repeated between GPS updates
            "t,0.4,900,5,-33.8690,151.2100,6",
            "t,0.6,950,8,-33.9000,151.3000,400", // a fix 400 m out: skipped
            "t,0.8,1000,10,,,", // no fix yet
            "t,1.0,1100,12,-33.8700,151.2110,4",
        ))
        assertEquals(3, r.points.size)
        assertEquals(-33.8688 to 151.2093, r.points.first())
    }

    @Test
    fun noLocationColumnsMeansNoRoute() {
        assertTrue(Route.parse(listOf("Time,Seconds,Engine RPM (rpm)", "t,0.0,800")).isEmpty)
    }

    @Test
    fun fitsTheBoxNorthUp() {
        val r = Route.parse(listOf(header, "t,0,0,0,-33.0,151.0,5", "t,1,0,0,-32.9,151.0,5"))
        val pts = r.fit(200f, 200f, 10f)
        // Heading north: the second point is higher up the box (smaller y).
        assertTrue(pts[1].second < pts[0].second)
        for ((x, y) in pts) assertTrue(x in 0f..200f && y in 0f..200f)
    }
}
