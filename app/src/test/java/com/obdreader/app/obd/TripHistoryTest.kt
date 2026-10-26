package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class TripHistoryTest {
    private fun trip(start: Long, everyday: Int?, efficiency: Int?, km: Double = 5.0) = TripSummary(
        start, start + 600_000, km, 500, 600, 60, 80.0, 40.0, null,
        mapOf(DrivingScore.Style.EVERYDAY to everyday, DrivingScore.Style.EFFICIENCY to efficiency, DrivingScore.Style.RACING to null),
    )

    @Test
    fun keepsDrivesNewestFirstWithoutDuplicates() {
        var h = TripHistory.add(null, trip(1_000, 70, 60))
        h = TripHistory.add(h, trip(3_000, 80, 90))
        h = TripHistory.add(h, trip(2_000, 50, 50))
        h = TripHistory.add(h, trip(3_000, 81, 91)) // the same drive again: replaced, not doubled
        val all = TripHistory.parse(h)
        assertEquals(listOf(3_000L, 2_000L, 1_000L), all.map { it.startedAt })
        assertEquals(81, all.first().scores[DrivingScore.Style.EVERYDAY])
    }

    @Test
    fun trendIsTheDriveScoreOldestFirst() {
        val trips = TripHistory.parse(TripHistory.encode(listOf(trip(1, 70, 60), trip(2, 80, 90), trip(3, null, null))))
        assertEquals(listOf(65, 85), TripHistory.scoreTrend(trips))
    }

    @Test
    fun removesOneDriveAndCapsTheList() {
        val many = (1..(TripHistory.MAX + 10)).map { trip(it.toLong() * 1000, 50, 50) }
        val h = TripHistory.encode(many)
        assertEquals(TripHistory.MAX, TripHistory.parse(h).size)
        val fewer = TripHistory.parse(TripHistory.remove(h, many.last().startedAt))
        assertEquals(TripHistory.MAX - 1, fewer.size)
    }
}
