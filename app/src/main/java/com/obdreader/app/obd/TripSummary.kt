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
    /** G-force from the phone's motion sensor, or null when there was none (no sensor, or the mount was still being learned). */
    val motion: Motion? = null,
) {
    /**
     * The drive's hardest moments in g, and how many times it braked, pulled away or cornered harshly
     * (see [GForce]). Shown beside the scores; they don't change them.
     */
    data class Motion(
        val peakBrake: Double, val peakAccel: Double, val peakSide: Double,
        val harshBrakes: Int, val harshAccels: Int, val harshCorners: Int,
    ) {
        /** The hardest of the three, in g. */
        val peak: Double get() = maxOf(peakBrake, peakAccel, peakSide)
        val harsh: Int get() = harshBrakes + harshAccels + harshCorners

        /** "1 harsh brake, 2 harsh corners" or "No harsh moments". */
        fun harshText(): String {
            fun n(count: Int, what: String) = if (count == 0) null else "$count harsh $what" + if (count == 1) "" else "s"
            return listOfNotNull(n(harshBrakes, "brake"), n(harshAccels, "start"), n(harshCorners, "corner")).joinToString(", ").ifEmpty { "No harsh moments" }
        }

        companion object {
            fun g(value: Double): String = String.format(Locale.US, "%.2f g", value)
        }
    }

    /** Worth showing: the car actually went somewhere. */
    val isDrive: Boolean get() = drivingSeconds >= 30 || distanceKm >= 0.2

    /** Saved as one line of "key=value" pairs, so it needs nothing beyond plain Kotlin. */
    fun encode(): String = buildList {
        add("start=$startedAt"); add("end=$endedAt"); add("km=$distanceKm")
        add("drive=$drivingSeconds"); add("conn=$connectedSeconds"); add("idle=$idleSeconds")
        topSpeed?.let { add("top=$it") }; averageSpeed?.let { add("avg=$it") }; best0to100?.let { add("best=$it") }
        scores.forEach { (style, s) -> if (s != null) add("score_${style.name}=$s") }
        motion?.let { m ->
            add(String.format(Locale.US, "gbrake=%.3f", m.peakBrake)); add(String.format(Locale.US, "gaccel=%.3f", m.peakAccel))
            add(String.format(Locale.US, "gside=%.3f", m.peakSide))
            add("hbrake=${m.harshBrakes}"); add("haccel=${m.harshAccels}"); add("hcorner=${m.harshCorners}")
        }
    }.joinToString(";")

    companion object {
        fun from(result: DrivingScore.Result, all: Map<DrivingScore.Style, DrivingScore.Result>, startedAt: Long, endedAt: Long, motion: Motion? = null) = TripSummary(
            startedAt, endedAt, result.distanceKm, result.drivingSeconds, result.connectedSeconds, result.idleSeconds,
            result.topSpeed, result.averageSpeed, result.best0to100, all.mapValues { it.value.score }, motion,
        )

        /** A saved drive's g-force, when it has one; numbers outside what a car can do are left out. */
        private fun motion(m: Map<String, String>): Motion? {
            fun g(k: String) = m[k]?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..5.0 }
            fun n(k: String) = m[k]?.toIntOrNull()?.takeIf { it in 0..100_000 }
            return Motion(g("gbrake") ?: return null, g("gaccel") ?: return null, g("gside") ?: return null,
                n("hbrake") ?: return null, n("haccel") ?: return null, n("hcorner") ?: return null)
        }

        fun decode(text: String?): TripSummary? {
            if (text.isNullOrBlank()) return null
            val m = text.split(";").mapNotNull { p -> p.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return try {
                TripSummary(
                    m.getValue("start").toLong(), m.getValue("end").toLong(), m.getValue("km").toDouble(),
                    m.getValue("drive").toInt(), m.getValue("conn").toInt(), m.getValue("idle").toInt(),
                    m["top"]?.toDouble(), m["avg"]?.toDouble(), m["best"]?.toDouble(),
                    DrivingScore.Style.values().associateWith { m["score_${it.name}"]?.toIntOrNull() },
                    motion(m),
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
