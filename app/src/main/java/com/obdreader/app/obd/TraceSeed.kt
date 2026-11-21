package com.obdreader.app.obd

/**
 * Rebuilds a dashboard trace from the last minute of readings kept outside the screen, so a trace
 * picks up where it was when the screen is drawn again (another style, or back from another tab)
 * instead of starting empty.
 */
object TraceSeed {

    /** A reading older than this at a point on the trace leaves a gap there, as when the car stopped answering. */
    const val STALE_MS = 2_000L

    /**
     * The trace's points, oldest first: [count] points [stepMs] apart, the last one at [nowMs]. Each is
     * the newest reading at or before its moment ([times] ascending), or null where there's none that recent.
     */
    fun steps(times: List<Long>, values: List<Double>, nowMs: Long, stepMs: Long, count: Int, staleMs: Long = STALE_MS): List<Double?> {
        val out = ArrayList<Double?>(count)
        var j = 0
        for (i in 0 until count) {
            val at = nowMs - (count - 1 - i) * stepMs
            while (j < times.size && times[j] <= at) j++
            val k = j - 1
            out += if (k >= 0 && at - times[k] <= staleMs) values[k] else null
        }
        return out
    }

    /** Only the newest unbroken run: a trace starts at its first point and breaks where readings stopped. */
    fun <T> latestRun(points: List<T?>): List<T> {
        val start = points.indexOfLast { it == null } + 1
        @Suppress("UNCHECKED_CAST")
        return points.subList(start, points.size) as List<T>
    }
}
