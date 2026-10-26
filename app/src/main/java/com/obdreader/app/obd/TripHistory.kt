package com.obdreader.app.obd

/**
 * Every drive's summary, newest first, kept on the phone as one line per drive (no location: just
 * times, distances and scores). Older drives drop off past [MAX] so it never grows without end.
 */
object TripHistory {
    const val MAX = 200

    fun parse(text: String?): List<TripSummary> =
        text.orEmpty().lines().mapNotNull { TripSummary.decode(it) }.sortedByDescending { it.startedAt }

    /** [trip] added to the stored [text] (replacing a drive with the same start, if any). */
    fun add(text: String?, trip: TripSummary): String =
        encode(listOf(trip) + parse(text).filter { it.startedAt != trip.startedAt })

    /** The stored [text] without the drive that started at [startedAt]. */
    fun remove(text: String?, startedAt: Long): String = encode(parse(text).filter { it.startedAt != startedAt })

    fun encode(trips: List<TripSummary>): String =
        trips.sortedByDescending { it.startedAt }.take(MAX).joinToString("\n") { it.encode() }

    /** The drive score (average of Everyday and Efficiency) of each drive, oldest first, for a trend line. */
    fun scoreTrend(trips: List<TripSummary>, count: Int = 20): List<Int> =
        trips.take(count).reversed().mapNotNull { ShareScore.of(it.scores, raceTelemetry = false).score }

    /** Total distance and driving time over [trips]. */
    fun totals(trips: List<TripSummary>): Pair<Double, Int> = trips.sumOf { it.distanceKm } to trips.sumOf { it.drivingSeconds }
}
