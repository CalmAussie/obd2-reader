package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCompareTest {

    private val header = "Time,Seconds,Engine RPM (rpm),Speed (km/h),Latitude,Longitude,Location accuracy (m)"

    /** A recorded route through [ways] (lat, lon), with a fix about every 20 m, nudged sideways by [offsetDeg]. */
    private fun route(ways: List<Pair<Double, Double>>, offsetDeg: Double = 0.0): Route {
        val rows = mutableListOf(header)
        var t = 0.0
        for (i in 1 until ways.size) {
            val (a, b) = ways[i - 1] to ways[i]
            val steps = 60
            for (s in 0 until steps) {
                val f = s / steps.toDouble()
                rows += String.format(java.util.Locale.US, "t,%.1f,1500,40,%.6f,%.6f,5", t, a.first + (b.first - a.first) * f + offsetDeg, a.second + (b.second - a.second) * f)
                t += 1.0
            }
        }
        val last = ways.last()
        rows += String.format(java.util.Locale.US, "t,%.1f,800,0,%.6f,%.6f,5", t, last.first + offsetDeg, last.second)
        return Route.parse(rows)
    }

    // Roughly Parramatta Rd style: west, then north, then west again (each leg about 1.1 km).
    private val commute = listOf(-33.8800 to 151.2000, -33.8800 to 151.1880, -33.8700 to 151.1880, -33.8700 to 151.1760)

    @Test
    fun theSameJourneyAgainMatches() {
        assertTrue(RouteCompare.sameRoute(route(commute), route(commute, offsetDeg = 0.0004))) // ~45 m off: GPS wobble, other lane
    }

    @Test
    fun theWayBackIsADifferentJourney() {
        assertFalse(RouteCompare.sameRoute(route(commute), route(commute.reversed())))
    }

    @Test
    fun aDifferentRouteToTheSamePlaceDoesntMatch() {
        // Same start and finish, but north first, then west: other roads for most of it.
        val other = listOf(-33.8800 to 151.2000, -33.8700 to 151.2000, -33.8700 to 151.1760)
        assertFalse(RouteCompare.sameRoute(route(commute), route(other)))
    }

    @Test
    fun aShortHopOrNoRouteIsntCompared() {
        val hop = listOf(-33.8800 to 151.2000, -33.8800 to 151.1990)
        assertFalse(RouteCompare.sameRoute(route(hop), route(hop)))
        assertFalse(RouteCompare.sameRoute(Route.parse(listOf("Time,Seconds", "t,0")), route(commute)))
    }

    private fun stats(secs: Int, idle: Int, avg: Double, top: Double) = RouteCompare.Stats(secs, secs - idle, idle, avg, top)

    @Test
    fun comparesTimeSpeedIdlingAndScore() {
        Units.mph = false
        val by = RouteCompare.compare(stats(600, 60, 32.0, 70.0), stats(720, 180, 27.0, 64.0), 75, 65).associateBy { it.label }
        assertEquals("2 min quicker", by.getValue("Time").change)
        assertEquals(true, by.getValue("Time").better)
        assertEquals("+5 km/h", by.getValue("Average speed").change)
        assertEquals("2 min less", by.getValue("Idling").change)
        assertEquals("+6 km/h", by.getValue("Top speed").change)
        assertEquals(null, by.getValue("Top speed").better)
        assertEquals("+10 points", by.getValue("Drive score").change)
        assertEquals(true, by.getValue("Drive score").better)
    }

    @Test
    fun noScoreLineWithoutBothScores() {
        assertFalse(RouteCompare.compare(stats(600, 60, 32.0, 70.0), stats(600, 60, 32.0, 70.0), 75, null).any { it.label == "Drive score" })
    }

    @Test
    fun smallDifferencesAreAboutTheSame() {
        Units.mph = false
        val lines = RouteCompare.compare(stats(610, 60, 32.0, 70.0), stats(600, 70, 32.2, 70.0), 80, 80)
        assertTrue(lines.all { it.change == "About the same" && it.better == null })
    }

    @Test
    fun slowerAndWorseShowsSo() {
        Units.mph = false
        val by = RouteCompare.compare(stats(900, 300, 22.0, 60.0), stats(600, 60, 32.0, 70.0), 50, 75).associateBy { it.label }
        assertEquals("5 min slower", by.getValue("Time").change)
        assertEquals(false, by.getValue("Time").better)
        assertEquals("\u221210 km/h", by.getValue("Average speed").change)
        assertEquals("4 min more", by.getValue("Idling").change)
        assertEquals(false, by.getValue("Drive score").better)
    }

    @Test
    fun readsTheRecordingsOwnFigures() {
        // One row a second: 10 s idling, then 20 s driving at 36 km/h, then one row at 72.
        val rows = listOf("Time,Seconds,Engine RPM (rpm),Speed (km/h)") +
            (0..10).map { "t,$it.0,800,0" } + (11..30).map { "t,$it.0,2000,36" } + listOf("t,31.0,2500,72", "t,60.0,2500,72")
        val st = RouteCompare.Stats.parse(rows)!!
        assertEquals(60, st.seconds)
        assertEquals(10, st.idleSeconds)
        assertEquals(72.0, st.topKmh!!, 0.01)
        // The 29 s gap (no readings) counts as 5 s at most.
        assertEquals(26, st.drivingSeconds)
    }
}
