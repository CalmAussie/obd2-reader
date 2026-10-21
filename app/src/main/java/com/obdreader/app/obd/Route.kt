package com.obdreader.app.obd

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * The route of a recorded drive, read from the Latitude and Longitude columns of its CSV.
 * Location is only ever kept inside a drive the user chose to record; this reads it back from
 * there, for the outline on the drive card.
 */
class Route private constructor(val points: List<Pair<Double, Double>>) {

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

    companion object {
        /** Largest fix error to use; worse fixes would draw spikes. */
        private const val MAX_ACCURACY_M = 50.0

        /** Several recordings' routes joined in order (a drive recorded in parts). */
        fun join(routes: List<Route>): Route = Route(routes.flatMap { it.points })

        /** Reads the route from the app's drive CSV, or an empty route when it has no location. */
        fun parse(lines: List<String>): Route {
            if (lines.size < 2) return Route(emptyList())
            val header = lines.first().split(",").map { it.trim('"') }
            val iLat = header.indexOfFirst { it.startsWith("Latitude") }
            val iLon = header.indexOfFirst { it.startsWith("Longitude") }
            val iAcc = header.indexOfFirst { it.startsWith("Location accuracy") }
            if (iLat < 0 || iLon < 0) return Route(emptyList())
            val pts = mutableListOf<Pair<Double, Double>>()
            for (line in lines.drop(1)) {
                val f = line.split(",")
                val lat = f.getOrNull(iLat)?.toDoubleOrNull() ?: continue
                val lon = f.getOrNull(iLon)?.toDoubleOrNull() ?: continue
                if (abs(lat) > 90 || abs(lon) > 180 || (lat == 0.0 && lon == 0.0)) continue
                val acc = if (iAcc >= 0) f.getOrNull(iAcc)?.toDoubleOrNull() else null
                if (acc != null && acc > MAX_ACCURACY_M) continue
                // Rows repeat the last fix between GPS updates; keep each place once.
                if (pts.lastOrNull() != lat to lon) pts += lat to lon
            }
            return Route(pts)
        }
    }
}
