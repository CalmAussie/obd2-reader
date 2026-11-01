package com.obdreader.app.obd

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Spots a recorded drive that followed the same route as an earlier recorded drive (same start,
 * same finish, same roads, same direction), and compares how the two went.
 *
 * It reads the routes straight from the two drives' own recordings, the only place location is
 * kept, and keeps nothing itself: the comparison is worked out each time the summary opens.
 */
object RouteCompare {

    /** How far apart the two starts (and the two finishes) may be. */
    const val END_M = 300.0

    /** How close each part of one route must come to the other. */
    const val NEAR_M = 150.0

    /** The share of each route that has to lie along the other. */
    const val SHARE = 0.8

    /** Routes this short (a lap of the car park) aren't compared. */
    const val MIN_LENGTH_M = 500.0

    /** True when [a] and [b] are the same journey, driven the same way round. */
    fun sameRoute(a: Route, b: Route): Boolean {
        if (a.isEmpty || b.isEmpty) return false
        val midLat = (a.points + b.points).map { it.first }.average()
        val k = cos(midLat * PI / 180) * M_PER_DEG
        fun xy(p: Pair<Double, Double>) = p.second * k to p.first * M_PER_DEG
        val pa = a.points.map(::xy)
        val pb = b.points.map(::xy)
        val la = length(pa); val lb = length(pb)
        if (la < MIN_LENGTH_M || lb < MIN_LENGTH_M) return false
        if (la / lb !in 0.75..1.33) return false
        if (dist(pa.first(), pb.first()) > END_M || dist(pa.last(), pb.last()) > END_M) return false
        return along(thin(pa, 150), thin(pb, 2000)) >= SHARE && along(thin(pb, 150), thin(pa, 2000)) >= SHARE
    }

    /** One line of the comparison: this drive's figure, and how it differs from last time. */
    data class Line(val label: String, val now: String, val change: String, val better: Boolean?)

    /** What one recording adds up to, read from its own rows (so it covers exactly the recorded stretch). */
    data class Stats(val seconds: Int, val drivingSeconds: Int, val idleSeconds: Int, val averageKmh: Double?, val topKmh: Double?) {
        companion object {
            /** Several recordings of one drive (recorded in parts) added together. */
            fun join(all: List<Stats>): Stats {
                val driving = all.sumOf { it.drivingSeconds }
                val km = all.sumOf { (it.averageKmh ?: 0.0) * it.drivingSeconds / 3600.0 }
                return Stats(all.sumOf { it.seconds }, driving, all.sumOf { it.idleSeconds },
                    if (driving > 0) km / (driving / 3600.0) else null, all.mapNotNull { it.topKmh }.maxOrNull())
            }

            fun parse(lines: List<String>): Stats? {
                if (lines.size < 3) return null
                val header = lines.first().split(",").map { it.trim('"') }
                val iSec = header.indexOf("Seconds")
                val iSpeed = header.indexOfFirst { it.startsWith("Speed (") }
                val iRpm = header.indexOfFirst { it.startsWith("Engine RPM") }
                if (iSec < 0 || iSpeed < 0) return null
                var last: Double? = null
                var first: Double? = null
                var driving = 0.0; var idle = 0.0; var km = 0.0; var top: Double? = null
                for (line in lines.drop(1)) {
                    val f = line.split(",")
                    val t = f.getOrNull(iSec)?.toDoubleOrNull() ?: continue
                    val v = f.getOrNull(iSpeed)?.toDoubleOrNull()
                    val rpm = if (iRpm >= 0) f.getOrNull(iRpm)?.toDoubleOrNull() else null
                    if (first == null) first = t
                    val dt = last?.let { (t - it).coerceIn(0.0, 5.0) } ?: 0.0
                    last = t
                    if (v == null) continue
                    if (v > 2) { driving += dt; km += v * dt / 3600.0 } else if ((rpm ?: 0.0) > 300) idle += dt
                    if (top == null || v > top) top = v
                }
                if (first == null || last == null) return null
                return Stats((last - first).toInt(), driving.toInt(), idle.toInt(), if (driving >= 1) km / (driving / 3600.0) else null, top)
            }
        }
    }

    /**
     * How the drive [now] compares with [before] on the same route: time, average speed, idling and
     * top speed, from the two recordings; and the drive score when each recording covered its whole
     * drive ([nowScore] and [beforeScore] are null otherwise). Lines with nothing to compare are left out.
     */
    fun compare(now: Stats, before: Stats, nowScore: Int? = null, beforeScore: Int? = null): List<Line> = buildList {
        val dt = now.seconds - before.seconds
        add(Line("Time", TripSummary.duration(now.seconds), timeChange(dt, "quicker", "slower"), better(-dt, 30)))
        if (now.averageKmh != null && before.averageKmh != null) {
            val d = Units.speed(now.averageKmh) - Units.speed(before.averageKmh)
            add(Line("Average speed", Units.speedText(now.averageKmh), signed(d, Units.speedUnit), better(d, 1.0)))
        }
        val di = now.idleSeconds - before.idleSeconds
        add(Line("Idling", TripSummary.duration(now.idleSeconds), timeChange(di, "less", "more"), better(-di, 30)))
        if (now.topKmh != null && before.topKmh != null) {
            // Faster isn't better or worse here, so it isn't coloured.
            add(Line("Top speed", Units.speedText(now.topKmh), signed(Units.speed(now.topKmh) - Units.speed(before.topKmh), Units.speedUnit), null))
        }
        if (nowScore != null && beforeScore != null) {
            add(Line("Drive score", "$nowScore", signed((nowScore - beforeScore).toDouble(), "points"), better((nowScore - beforeScore).toDouble(), 1.0)))
        }
    }

    private fun better(d: Number, deadband: Number): Boolean? = when {
        abs(d.toDouble()) < deadband.toDouble() -> null
        d.toDouble() > 0 -> true
        else -> false
    }

    /** "2 min quicker", "45 s slower", or "Same" within half a minute. */
    private fun timeChange(seconds: Int, less: String, more: String): String = when {
        abs(seconds) < 30 -> "About the same"
        seconds < 0 -> "${TripSummary.duration(-seconds)} $less"
        else -> "${TripSummary.duration(seconds)} $more"
    }

    /** "+4 km/h", "−3 points" or "Same". */
    private fun signed(d: Double, unit: String): String {
        val r = d.roundToInt()
        return when {
            r == 0 -> "About the same"
            r > 0 -> "+$r $unit"
            else -> "−${-r} $unit"
        }.let { if (unit == "points" && abs(r) == 1) it.replace("points", "point") else it }
    }

    // ---------- Geometry (metres on a flat projection: fine at city scale) ----------

    private const val M_PER_DEG = 111_320.0

    private fun dist(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = a.first - b.first; val dy = a.second - b.second
        return sqrt(dx * dx + dy * dy)
    }

    private fun length(p: List<Pair<Double, Double>>): Double = (1 until p.size).sumOf { dist(p[it - 1], p[it]) }

    /** At most [n] points, evenly spread through the route. */
    private fun thin(p: List<Pair<Double, Double>>, n: Int): List<Pair<Double, Double>> =
        if (p.size <= n) p else (0 until n).map { p[(it.toLong() * (p.size - 1) / (n - 1)).toInt()] }

    /** The share of [pts] lying within [NEAR_M] of the line through [line]. */
    private fun along(pts: List<Pair<Double, Double>>, line: List<Pair<Double, Double>>): Double {
        if (pts.isEmpty() || line.size < 2) return 0.0
        val near = pts.count { p -> (1 until line.size).any { segDist(p, line[it - 1], line[it]) <= NEAR_M } }
        return near.toDouble() / pts.size
    }

    private fun segDist(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val vx = b.first - a.first; val vy = b.second - a.second
        val len2 = vx * vx + vy * vy
        val t = if (len2 == 0.0) 0.0 else (((p.first - a.first) * vx + (p.second - a.second) * vy) / len2).coerceIn(0.0, 1.0)
        val x = a.first + t * vx; val y = a.second + t * vy
        val dx = p.first - x; val dy = p.second - y
        return sqrt(dx * dx + dy * dy)
    }
}
