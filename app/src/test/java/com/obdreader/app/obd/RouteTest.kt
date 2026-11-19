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
        // 11 km north, 200 s apart (a car can do that; in 1 s it would be a stray fix and left out).
        val r = Route.parse(listOf(header, "t,0,0,0,-33.0,151.0,5", "t,200,0,0,-32.9,151.0,5"))
        val pts = r.fit(200f, 200f, 10f)
        // Heading north: the second point is higher up the box (smaller y).
        assertTrue(pts[1].second < pts[0].second)
        for ((x, y) in pts) assertTrue(x in 0f..200f && y in 0f..200f)
    }

    @Test
    fun aDriveThatEndsInATunnelCarriesOnByTheCarsSpeed() {
        val lines = mutableListOf(header)
        // Heading north at 72 km/h (20 m/s) with GPS, one row a second...
        for (i in 0..10) lines += "t,$i,2000,72,${-33.9000 + i * 0.00018},151.2,5"
        // ...then 60 s underground with no fix, still at 72 km/h: about 1.2 km more.
        for (i in 11..70) lines += "t,$i,2000,72,,,"
        val r = Route.parse(lines)
        val end = r.points.last()
        val lastFix = -33.9000 + 10 * 0.00018
        val metresNorth = (end.first - lastFix) * 111_320.0
        assertTrue("went on about 1.2 km, not $metresNorth m", metresNorth in 1100.0..1300.0)
        assertEquals(151.2, end.second, 1e-4)
    }

    @Test
    fun aGapInTheMiddleJustJoinsTheFixes() {
        val lines = mutableListOf(header)
        for (i in 0..5) lines += "t,$i,2000,72,${-33.9 + i * 0.00018},151.2,5"
        for (i in 6..40) lines += "t,$i,2000,72,,,"
        for (i in 41..45) lines += "t,$i,2000,72,${-33.89 + i * 0.00018},151.2,5"
        val r = Route.parse(lines)
        assertEquals(11, r.points.size) // only real fixes, nothing estimated
    }

    @Test
    fun standingStillAddsNothing() {
        val lines = mutableListOf(header)
        for (i in 0..5) lines += "t,$i,2000,40,${-33.9 + i * 0.0001},151.2,5"
        for (i in 6..60) lines += "t,$i,800,0,,,"
        assertEquals(6, Route.parse(lines).points.size)
    }

    @Test
    fun stretchesAreRankedBySpeedForShading() {
        val r = Route.parse(listOf(header,
            "t,0,0,20,-33.9000,151.2,5",
            "t,1,0,20,-33.9002,151.2,5",
            "t,2,0,100,-33.9004,151.2,5",
            "t,3,0,100,-33.9006,151.2,5"))
        val shares = r.segmentSpeedShares()
        assertEquals(3, shares.size)
        assertEquals(0.0, shares[0]!!, 1e-9)   // slowest stretch
        assertEquals(1.0, shares[2]!!, 1e-9)   // fastest stretch
        assertTrue(shares[1]!! > 0.0 && shares[1]!! < 1.0)
    }

    /** A drive heading north from Sydney at 72 km/h, one row a second from [from] to [to] seconds. */
    private fun sydney(from: Int, to: Int) = (from..to).map { "t,$it,2000,72,${-33.9 + it * 0.00018},151.2,5" }

    @Test
    fun aStrayFirstFixFarAwayIsLeftOut() {
        // The emulator (and sometimes a phone) gives an old, far-away place with a good accuracy
        // for a second or two before the real fix. Here: California, then the real drive in Sydney.
        val lines = mutableListOf(header)
        lines += listOf("t,0.0,800,0,37.421998,-122.084000,5", "t,0.5,800,0,37.421998,-122.084000,5", "t,1.0,800,0,37.421998,-122.084000,5")
        lines += sydney(2, 40)
        val r = Route.parse(lines)
        assertTrue("every point is in Sydney", r.points.all { it.first < -33.0 && it.second > 151.0 })
        assertEquals(-33.9 + 2 * 0.00018, r.points.first().first, 1e-9)
    }

    @Test
    fun aStrayFixAtTheEndIsLeftOutToo() {
        val lines = mutableListOf(header)
        lines += sydney(0, 30)
        lines += "t,31,2000,72,37.421998,-122.084000,5"
        assertTrue(Route.parse(lines).points.all { it.first < -33.0 })
    }

    @Test
    fun oneJumpMidDriveIsLeftOut() {
        val lines = mutableListOf(header)
        lines += sydney(0, 15)
        lines += "t,16,2000,72,-33.5,151.6,8"   // about 57 km away for one second
        lines += sydney(17, 30)
        val r = Route.parse(lines)
        assertEquals(30, r.points.size)          // every real fix kept (16 + 14), the jump gone
        assertTrue(r.points.none { it.first > -33.6 })
    }

    @Test
    fun aLongTunnelIsNotAJump() {
        // 5 minutes with no fix, then 6 km further on: 20 m/s, a normal speed, so both halves stay.
        val lines = mutableListOf(header)
        lines += sydney(0, 10)
        for (i in 11..309) lines += "t,$i,2000,72,,,"
        lines += (310..320).map { "t,$it,2000,72,${-33.9 + 10 * 0.00018 + (6000.0 + (it - 310) * 20) / 111_320.0},151.2,5" }
        assertEquals(22, Route.parse(lines).points.size)
    }

    @Test
    fun anOrdinaryDriveIsUnchanged() {
        val lines = mutableListOf(header)
        lines += sydney(0, 60)
        assertEquals(61, Route.parse(lines).points.size)
        assertEquals(61, Route.usableFixes(lines).count { it != null })
    }

    @Test
    fun usableFixesLineUpWithTheRows() {
        val lines = mutableListOf(header, "t,0.0,800,0,37.421998,-122.084000,5", "t,0.5,800,0,,,")
        lines += sydney(1, 20)
        val fixes = Route.usableFixes(lines)
        assertEquals(lines.size - 1, fixes.size)
        assertEquals(null, fixes[0])   // the stray
        assertEquals(null, fixes[1])   // no fix
        assertEquals(-33.9 + 0.00018, fixes[2]!!.first, 1e-9)
    }
}
