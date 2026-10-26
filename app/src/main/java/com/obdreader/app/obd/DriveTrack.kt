package com.obdreader.app.obd

/**
 * A recorded drive (the app's CSV, a row every 0.2 seconds) read back for the video overlay.
 * [at] gives the readings at any moment, gliding between rows, so a 30 fps video moves
 * smoothly even though the car was read five times a second.
 */
class DriveTrack private constructor(
    private val t: DoubleArray,
    private val rpm: DoubleArray,
    private val speed: DoubleArray,
    private val throttle: DoubleArray,
    private val coolant: DoubleArray,
    /** Positions per row (NaN where there was no good fix), when the drive was recorded with its route. */
    private val lat: DoubleArray = DoubleArray(0),
    private val lon: DoubleArray = DoubleArray(0),
    /** The drive's outline, for the overlay's map; null without a route. */
    val route: Route? = null,
) {
    /** For each row, the nearest row with a fix at or before it, and at or after it (-1: none). */
    private val prevFix: IntArray by lazy { IntArray(lat.size).also { a -> var last = -1; for (i in lat.indices) { if (!lat[i].isNaN()) last = i; a[i] = last } } }
    private val nextFix: IntArray by lazy { IntArray(lat.size).also { a -> var next = -1; for (i in lat.indices.reversed()) { if (!lat[i].isNaN()) next = i; a[i] = next } } }

    /**
     * Where the car was [seconds] into the drive: between the fixes either side, or the last fix
     * when GPS had dropped out (a tunnel). Null before the first fix, or without a route.
     */
    fun position(seconds: Double): Pair<Double, Double>? {
        if (lat.isEmpty() || t.isEmpty()) return null
        // The row at or before [seconds], then the fixes either side of it (looked up, not searched).
        var lo = 0; var hi = t.size - 1
        if (t[0] > seconds) return null
        while (lo < hi) { val mid = (lo + hi + 1) / 2; if (t[mid] <= seconds) lo = mid else hi = mid - 1 }
        val before = prevFix[lo]
        val after = if (lo + 1 < t.size) nextFix[lo + 1] else -1
        if (before < 0) return null
        if (after < 0 || after == before || t[after] - t[before] > 20.0) return lat[before] to lon[before]
        val f = ((seconds - t[before]) / (t[after] - t[before])).coerceIn(0.0, 1.0)
        return (lat[before] + (lat[after] - lat[before]) * f) to (lon[before] + (lon[after] - lon[before]) * f)
    }

    data class Sample(val seconds: Double, val rpm: Double?, val speed: Double?, val throttle: Double?, val coolant: Double?)

    val duration: Double get() = if (t.isEmpty()) 0.0 else t.last()
    val size: Int get() = t.size

    /** Highest speed reached by [seconds] into the drive (two rows in a row, like the app's top speed). */
    fun topSpeedUntil(seconds: Double): Double? {
        var best: Double? = null
        for (i in 1 until t.size) {
            if (t[i] > seconds) break
            val a = speed[i - 1]; val b = speed[i]
            if (!a.isNaN() && !b.isNaN()) { val held = minOf(a, b); if (held > (best ?: 0.0)) best = held }
        }
        return best
    }

    fun at(seconds: Double): Sample {
        if (t.isEmpty()) return Sample(seconds, null, null, null, null)
        val s = seconds.coerceIn(t.first(), duration)
        // Binary search for the row at or before s.
        var lo = 0; var hi = t.size - 1
        while (lo < hi) { val mid = (lo + hi + 1) / 2; if (t[mid] <= s) lo = mid else hi = mid - 1 }
        val j = (lo + 1).coerceAtMost(t.size - 1)
        val f = if (j == lo || t[j] == t[lo]) 0.0 else (s - t[lo]) / (t[j] - t[lo])
        fun mix(a: DoubleArray): Double? {
            val x = a[lo]; val y = a[j]
            return when {
                x.isNaN() && y.isNaN() -> null
                x.isNaN() -> y
                y.isNaN() -> x
                else -> x + (y - x) * f
            }
        }
        return Sample(s, mix(rpm), mix(speed), mix(throttle), mix(coolant))
    }

    companion object {
        /** Reads the app's drive CSV. Columns are found by their names, so older files still work. */
        fun parse(lines: List<String>): DriveTrack? {
            if (lines.size < 2) return null
            val header = splitCsv(lines.first())
            fun col(prefix: String) = header.indexOfFirst { it.startsWith(prefix) }
            val iSec = col("Seconds"); val iRpm = col("Engine RPM"); val iSpeed = col("Speed")
            val iThr = col("Throttle"); val iCool = col("Coolant")
            val iLat = col("Latitude"); val iLon = col("Longitude"); val iAcc = col("Location accuracy")
            if (iSec < 0) return null
            val rows = lines.drop(1).filter { it.isNotBlank() }.map { splitCsv(it) }
            fun num(row: List<String>, i: Int) = if (i < 0) Double.NaN else row.getOrNull(i)?.toDoubleOrNull() ?: Double.NaN
            val ok = rows.filter { !num(it, iSec).isNaN() }
            if (ok.isEmpty()) return null
            val route = if (iLat >= 0) Route.parse(lines).takeIf { !it.isEmpty } else null
            fun good(row: List<String>): Boolean {
                val a = num(row, iLat); val o = num(row, iLon); val acc = num(row, iAcc)
                return !a.isNaN() && !o.isNaN() && !(a == 0.0 && o == 0.0) && (acc.isNaN() || acc <= 50.0)
            }
            return DriveTrack(
                ok.map { num(it, iSec) }.toDoubleArray(), ok.map { num(it, iRpm) }.toDoubleArray(), ok.map { num(it, iSpeed) }.toDoubleArray(),
                ok.map { num(it, iThr) }.toDoubleArray(), ok.map { num(it, iCool) }.toDoubleArray(),
                if (route == null) DoubleArray(0) else ok.map { if (good(it)) num(it, iLat) else Double.NaN }.toDoubleArray(),
                if (route == null) DoubleArray(0) else ok.map { if (good(it)) num(it, iLon) else Double.NaN }.toDoubleArray(),
                route,
            )
        }

        private fun splitCsv(line: String): List<String> {
            val out = mutableListOf<String>(); val sb = StringBuilder(); var quoted = false
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                    ch == '"' -> quoted = !quoted
                    ch == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                    else -> sb.append(ch)
                }
                i++
            }
            out += sb.toString()
            return out
        }
    }
}
