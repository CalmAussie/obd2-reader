package com.obdreader.app.obd

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * The route of a recorded drive, read from the Latitude and Longitude columns of its CSV.
 * Location is only ever kept inside a drive the user chose to record; this reads it back from
 * there, for the outline on the drive card.
 */
class Route private constructor(
    val points: List<Pair<Double, Double>>,
    /** The car's speed (km/h) at each point, from the same CSV row, or null where it wasn't read. */
    val speeds: List<Double?> = points.map { null },
) {

    val isEmpty: Boolean get() = points.size < 2

    /**
     * The route as x/y positions fitting a [width] x [height] box with [pad] around it, north up,
     * keeping its shape (a degree of longitude is shorter than one of latitude away from the equator).
     */
    fun fit(width: Float, height: Float, pad: Float): List<Pair<Float, Float>> {
        if (isEmpty) return emptyList()
        val midLat = points.map { it.first }.average()
        val k = cos(midLat * PI / 180)
        val xs = points.map { it.second * k }
        val ys = points.map { it.first }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).coerceAtLeast(1e-9); val spanY = (maxY - minY).coerceAtLeast(1e-9)
        val scale = minOf((width - 2 * pad) / spanX, (height - 2 * pad) / spanY)
        // Centre it in the box.
        val offX = pad + ((width - 2 * pad) - spanX * scale) / 2
        val offY = pad + ((height - 2 * pad) - spanY * scale) / 2
        return xs.indices.map { i ->
            (offX + (xs[i] - minX) * scale).toFloat() to (height - (offY + (ys[i] - minY) * scale)).toFloat()
        }
    }

    /**
     * How fast each stretch between two points was driven, from 0 (the slowest on this drive) to 1
     * (the fastest), for shading the outline. Null for a stretch with no speed reading.
     */
    fun segmentSpeedShares(): List<Double?> {
        val seg = (1 until points.size).map { i ->
            val a = speeds.getOrNull(i - 1); val b = speeds.getOrNull(i)
            when { a != null && b != null -> (a + b) / 2; else -> a ?: b }
        }
        val known = seg.filterNotNull()
        if (known.isEmpty()) return seg
        val lo = known.min(); val hi = known.max()
        if (hi - lo < 1.0) return seg.map { it?.let { 1.0 } }
        return seg.map { v -> v?.let { ((it - lo) / (hi - lo)).coerceIn(0.0, 1.0) } }
    }

    companion object {
        /** Largest fix error to use; worse fixes would draw spikes. */
        private const val MAX_ACCURACY_M = 50.0

        /** Several recordings' routes joined in order (a drive recorded in parts). */
        fun join(routes: List<Route>): Route = Route(routes.flatMap { it.points }, routes.flatMap { it.speeds })

        /**
         * Reads the route from the app's drive CSV, or an empty route when it has no location.
         *
         * Where GPS drops out (a tunnel, an underground car park) the rows have no position. In the
         * middle of a drive the outline simply joins the fixes either side. At the start or end of a
         * drive there's no fix on one side, so the car's own speed readings give how far it went,
         * and the outline is carried on that far in the direction it was heading. That's an estimate
         * of where the car got to, never a road shape: the app has no maps.
         */
        fun parse(lines: List<String>): Route {
            if (lines.size < 2) return Route(emptyList())
            val header = lines.first().split(",").map { it.trim('"') }
            val iLat = header.indexOfFirst { it.startsWith("Latitude") }
            val iLon = header.indexOfFirst { it.startsWith("Longitude") }
            val iAcc = header.indexOfFirst { it.startsWith("Location accuracy") }
            val iSec = header.indexOfFirst { it == "Seconds" }
            val iSpeed = header.indexOfFirst { it.startsWith("Speed (") }
            if (iLat < 0 || iLon < 0) return Route(emptyList())
            class Row(val sec: Double?, val kmh: Double?, val fix: Pair<Double, Double>?)
            val rows = lines.drop(1).map { line ->
                val f = line.split(",")
                val lat = f.getOrNull(iLat)?.toDoubleOrNull()
                val lon = f.getOrNull(iLon)?.toDoubleOrNull()
                val acc = if (iAcc >= 0) f.getOrNull(iAcc)?.toDoubleOrNull() else null
                val good = lat != null && lon != null && abs(lat) <= 90 && abs(lon) <= 180 && !(lat == 0.0 && lon == 0.0) &&
                    (acc == null || acc <= MAX_ACCURACY_M)
                Row(f.getOrNull(iSec)?.toDoubleOrNull(), if (iSpeed >= 0) f.getOrNull(iSpeed)?.toDoubleOrNull() else null, if (good) lat!! to lon!! else null)
            }
            val pts = mutableListOf<Pair<Double, Double>>()
            val kmh = mutableListOf<Double?>()
            // Rows repeat the last fix between GPS updates; keep each place once.
            for (r in rows) r.fix?.let { if (pts.lastOrNull() != it) { pts += it; kmh += r.kmh } }
            if (pts.size < 2) return Route(pts, kmh)
            val first = rows.indexOfFirst { it.fix != null }
            val last = rows.indexOfLast { it.fix != null }
            // Before the first fix: back from it, against the direction it set off in.
            val before = metresDriven(rows.subList(0, first + 1).map { it.sec to it.kmh })
            if (before >= MIN_ESTIMATE_M) heading(pts, fromStart = true)?.let { h ->
                pts.add(0, move(pts.first(), h + 180.0, before)); kmh.add(0, averageSpeed(rows.subList(0, first + 1).map { it.kmh }))
            }
            // After the last fix: on from it, the way it was going.
            val after = metresDriven(rows.subList(last, rows.size).map { it.sec to it.kmh })
            if (after >= MIN_ESTIMATE_M) heading(pts, fromStart = false)?.let { h ->
                pts.add(move(pts.last(), h, after)); kmh.add(averageSpeed(rows.subList(last, rows.size).map { it.kmh }))
            }
            return Route(pts, kmh)
        }

        private fun averageSpeed(v: List<Double?>): Double? = v.filterNotNull().takeIf { it.isNotEmpty() }?.average()

        /** Shortest stretch worth adding to the outline, and the most that will be estimated. */
        private const val MIN_ESTIMATE_M = 30.0
        private const val MAX_ESTIMATE_M = 20_000.0
        /** Ignore a speed reading that's this long after the one before (the connection was down). */
        private const val MAX_ROW_GAP_S = 10.0

        /** Distance covered over (seconds, km/h) rows, from the car's own speed. */
        internal fun metresDriven(rows: List<Pair<Double?, Double?>>): Double {
            var m = 0.0
            for (i in 1 until rows.size) {
                val t0 = rows[i - 1].first ?: continue; val t1 = rows[i].first ?: continue
                val v = rows[i - 1].second ?: continue
                val dt = t1 - t0
                if (dt <= 0 || dt > MAX_ROW_GAP_S) continue
                m += v / 3.6 * dt
            }
            return m.coerceAtMost(MAX_ESTIMATE_M)
        }

        /** Compass heading of the first (or last) 20 m or more of [pts], or null if it hardly moved. */
        private fun heading(pts: List<Pair<Double, Double>>, fromStart: Boolean): Double? {
            val ordered = if (fromStart) pts else pts.asReversed()
            val a = ordered.first()
            val b = ordered.drop(1).firstOrNull { metres(a, it) >= 20.0 } ?: return null
            val h = bearing(a, b)
            return if (fromStart) h else (h + 180.0) % 360.0
        }

        private fun metres(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val dy = (b.first - a.first) * 111_320.0
            val dx = (b.second - a.second) * 111_320.0 * cos(a.first * PI / 180)
            return kotlin.math.hypot(dx, dy)
        }

        private fun bearing(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val dy = b.first - a.first
            val dx = (b.second - a.second) * cos(a.first * PI / 180)
            return (Math.toDegrees(kotlin.math.atan2(dx, dy)) + 360.0) % 360.0
        }

        /** [from] moved [m] metres on compass heading [deg]. */
        private fun move(from: Pair<Double, Double>, deg: Double, m: Double): Pair<Double, Double> {
            val r = Math.toRadians(deg)
            val lat = from.first + m * cos(r) / 111_320.0
            val lon = from.second + m * kotlin.math.sin(r) / (111_320.0 * cos(from.first * PI / 180))
            return lat to lon
        }
    }
}
