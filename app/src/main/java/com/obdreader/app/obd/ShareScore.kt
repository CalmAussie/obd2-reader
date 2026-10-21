package com.obdreader.app.obd

/**
 * The one score a shared drive card shows. Race telemetry is for track days, so its card shows the
 * Racing score only. Every other layout shows the drive score: the average of the Everyday and
 * Efficiency scores, so a card rewards driving that's both smooth and easy on fuel.
 */
data class ShareScore(val kind: Kind, val score: Int?, val parts: List<Pair<DrivingScore.Style, Int?>>) {

    enum class Kind(val label: String) { RACING("Racing"), DRIVE("Drive") }

    companion object {
        /** The scores that make up the drive score, in the order they're shown. */
        val DRIVE_PARTS = listOf(DrivingScore.Style.EVERYDAY, DrivingScore.Style.EFFICIENCY)

        /**
         * [raceTelemetry]: the card is shared from the Race telemetry layout. A score that couldn't
         * be worked out (too short a drive) is left out of the average; if neither could, there's no score.
         */
        fun of(scores: Map<DrivingScore.Style, Int?>, raceTelemetry: Boolean): ShareScore {
            if (raceTelemetry) return ShareScore(Kind.RACING, scores[DrivingScore.Style.RACING], emptyList())
            val parts = DRIVE_PARTS.map { it to scores[it] }
            val known = parts.mapNotNull { it.second }
            val avg = if (known.isEmpty()) null else Math.round(known.sum().toDouble() / known.size).toInt()
            return ShareScore(Kind.DRIVE, avg, parts)
        }
    }
}
