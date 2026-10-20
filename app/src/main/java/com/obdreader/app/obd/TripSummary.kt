package com.obdreader.app.obd

import java.util.Locale
import kotlin.math.roundToInt

/**
 * What one drive added up to, shown when it ends: distance, time, speeds, idling and the score
 * in each style. Kept on the phone only (the last drive), never uploaded.
 */
data class TripSummary(
    val startedAt: Long,
    val endedAt: Long,
    val distanceKm: Double,
    val drivingSeconds: Int,
    val connectedSeconds: Int,
    val idleSeconds: Int,
    val topSpeed: Double?,
    val averageSpeed: Double?,
    val best0to100: Double?,
    /** Score out of 100 in each style, or null where the drive was too short to score. */
    val scores: Map<DrivingScore.Style, Int?>,
) {
    /** Worth showing: the car actually went somewhere. */
    val isDrive: Boolean get() = drivingSeconds >= 30 || distanceKm >= 0.2

    /** Saved as one line of "key=value" pairs, so it needs nothing beyond plain Kotlin. */
    fun encode(): String = buildList {
        add("start=$startedAt"); add("end=$endedAt"); add("km=$distanceKm")
        add("drive=$drivingSeconds"); add("conn=$connectedSeconds"); add("idle=$idleSeconds")
        topSpeed?.let { add("top=$it") }; averageSpeed?.let { add("avg=$it") }; best0to100?.let { add("best=$it") }
        scores.forEach { (style, s) -> if (s != null) add("score_${style.name}=$s") }
    }.joinToString(";")

    companion object {
        fun from(result: DrivingScore.Result, all: Map<DrivingScore.Style, DrivingScore.Result>, startedAt: Long, endedAt: Long) = TripSummary(
            startedAt, endedAt, result.distanceKm, result.drivingSeconds, result.connectedSeconds, result.idleSeconds,
            result.topSpeed, result.averageSpeed, result.best0to100, all.mapValues { it.value.score },
        )

        fun decode(text: String?): TripSummary? {
            if (text.isNullOrBlank()) return null
            val m = text.split(";").mapNotNull { p -> p.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return try {
                TripSummary(
                    m.getValue("start").toLong(), m.getValue("end").toLong(), m.getValue("km").toDouble(),
                    m.getValue("drive").toInt(), m.getValue("conn").toInt(), m.getValue("idle").toInt(),
                    m["top"]?.toDouble(), m["avg"]?.toDouble(), m["best"]?.toDouble(),
                    DrivingScore.Style.values().associateWith { m["score_${it.name}"]?.toIntOrNull() },
                )
            } catch (e: Exception) {
                null
            }
        }

        /** "45 s", "28 min" or "1 h 12 min". */
        fun duration(seconds: Int): String = when {
            seconds < 60 -> "$seconds s"
            seconds < 3600 -> "${(seconds / 60.0).roundToInt()} min"
            else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
        }

        /** "0.4 km", "12.4 km" or "128 km". */
        fun distance(km: Double): String =
            if (km >= 100) "${km.roundToInt()} km" else String.format(Locale.US, "%.1f km", km)
    }
}
