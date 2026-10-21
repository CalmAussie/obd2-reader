package com.obdreader.app.obd

import com.obdreader.app.obd.DrivingScore.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareScoreTest {

    private val scores = mapOf(Style.EFFICIENCY to 71, Style.EVERYDAY to 62, Style.RACING to 48)

    @Test
    fun raceTelemetryShowsOnlyRacing() {
        val s = ShareScore.of(scores, raceTelemetry = true)
        assertEquals(ShareScore.Kind.RACING, s.kind)
        assertEquals(48, s.score)
        assertTrue(s.parts.isEmpty())
    }

    @Test
    fun otherLayoutsAverageEverydayAndEfficiency() {
        val s = ShareScore.of(scores, raceTelemetry = false)
        assertEquals(ShareScore.Kind.DRIVE, s.kind)
        assertEquals(67, s.score) // (62 + 71) / 2 = 66.5, rounded
        assertEquals(listOf(Style.EVERYDAY to 62, Style.EFFICIENCY to 71), s.parts)
    }

    @Test
    fun missingScoresAreLeftOut() {
        assertEquals(62, ShareScore.of(mapOf(Style.EVERYDAY to 62, Style.EFFICIENCY to null), false).score)
        assertNull(ShareScore.of(mapOf(Style.EVERYDAY to null, Style.EFFICIENCY to null, Style.RACING to 90), false).score)
        assertNull(ShareScore.of(mapOf(Style.RACING to null), true).score)
    }

    @Test
    fun theRoastFollowsTheSharedScore() {
        val racing = ShareScore.of(scores, true)
        assertEquals(Roast.line(Style.RACING, 48, 5L), Roast.line(racing, 5L))
        // The drive score's roast is picked for the average, not for either part.
        val great = ShareScore.of(mapOf(Style.EVERYDAY to 96, Style.EFFICIENCY to 94, Style.RACING to 10), false)
        val poor = ShareScore.of(mapOf(Style.EVERYDAY to 20, Style.EFFICIENCY to 22, Style.RACING to 99), false)
        assertTrue(Roast.line(great, 5L) != Roast.line(poor, 5L))
        assertTrue(Roast.line(great, 5L) !in listOf(Roast.line(Style.EVERYDAY, 96, 5L), Roast.line(Style.EFFICIENCY, 94, 5L)))
    }
}
