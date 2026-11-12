package com.obdreader.app.obd

import java.util.Locale

/**
 * "Codes recently cleared?": a used-car check. When the trouble codes are cleared (or the battery is
 * disconnected, on most cars), the engine computer starts counting again from zero: the distance
 * driven, the warm-ups and the engine running time since then (service 01 PIDs 31, 30 and 4E). It
 * also counts the distance and time driven with the check-engine light on (21 and 4D). Small
 * numbers, together with readiness monitors that haven't finished, point to a recent clear.
 *
 * Every counter is optional: a car that doesn't report one shows N/A, and the verdict only uses the
 * ones it does report.
 */
data class ClearedCheck(
    /** km since the codes were cleared (PID 31); 65,535 means at least that. */
    val distanceKm: Int?,
    /** Warm-ups since the codes were cleared (PID 30); 255 means at least that. */
    val warmUps: Int?,
    /** Engine running minutes since the codes were cleared (PID 4E). */
    val minutes: Int?,
    /** km driven with the check-engine light on (PID 21). */
    val milDistanceKm: Int?,
    /** Minutes run with the check-engine light on (PID 4D). */
    val milMinutes: Int?,
    val readiness: Readiness?,
) {

    enum class Verdict(val label: String) {
        RECENT("Codes look recently cleared"),
        FAIRLY_RECENT("Codes were cleared fairly recently"),
        NOT_RECENT("No sign of a recent clear"),
        NOT_REPORTED("Can't tell"),
    }

    private enum class Signal { RECENT, FAIRLY, LONG }

    private fun signal(value: Int?, recentBelow: Int, fairlyBelow: Int): Signal? = when {
        value == null -> null
        value < recentBelow -> Signal.RECENT
        value < fairlyBelow -> Signal.FAIRLY
        else -> Signal.LONG
    }

    private val signals: List<Signal>
        get() = listOfNotNull(
            signal(distanceKm, RECENT_KM, FAIRLY_KM),
            signal(warmUps, RECENT_WARM_UPS, FAIRLY_WARM_UPS),
            signal(minutes, RECENT_MINUTES, FAIRLY_MINUTES),
        )

    /** Readiness monitors that haven't finished yet, or 0 if the car didn't report them. */
    val incompleteMonitors: Int get() = readiness?.incomplete?.size ?: 0

    /** Any one counter that says "recent" is enough: a seller can't make the warm-ups count up faster. */
    val verdict: Verdict
        get() = when {
            signals.isEmpty() -> Verdict.NOT_REPORTED
            Signal.RECENT in signals -> Verdict.RECENT
            Signal.FAIRLY in signals -> Verdict.FAIRLY_RECENT
            else -> Verdict.NOT_RECENT
        }

    /**
     * The verdict in a sentence, e.g. "Codes look recently cleared: only 12 km and 2 warm-ups
     * since." Distances in the user's units.
     */
    fun headline(): String {
        val since = sinceParts().joinToString(" and ")
        return when (verdict) {
            Verdict.RECENT -> "${verdict.label}: only $since since."
            Verdict.FAIRLY_RECENT -> "${verdict.label}: $since ago."
            Verdict.NOT_RECENT -> "${verdict.label}: $since since the codes were last cleared."
            Verdict.NOT_REPORTED -> if (incompleteMonitors > 0)
                "Your car doesn't report how long since its codes were cleared. ${monitorText()} not finished yet, which also happens after a clear."
            else "Your car doesn't report how long since its codes were cleared."
        }
    }

    /** One line per counter, N/A where the car doesn't report it. */
    fun lines(): List<Pair<String, String>> = listOf(
        "Distance since codes cleared" to (distanceKm?.let { distanceText(it) } ?: NA),
        "Warm-ups since codes cleared" to (warmUps?.let { warmUpText(it) } ?: NA),
        "Engine running time since codes cleared" to (minutes?.let { durationText(it) } ?: NA),
        "Distance with check-engine light on" to (milDistanceKm?.let { distanceText(it) } ?: NA),
        "Time with check-engine light on" to (milMinutes?.let { durationText(it) } ?: NA),
        "Readiness monitors" to (readiness?.summary() ?: NA),
    )

    /** Extra notes that go with the verdict: unfinished monitors, and time with the light on. */
    fun notes(): List<String> {
        val out = mutableListOf<String>()
        if (verdict != Verdict.NOT_REPORTED && incompleteMonitors > 0) {
            out += "${monitorText()} not finished yet. They finish over a few days of normal driving after a clear, so this fits a recent one."
        }
        val milOn = readiness?.milOn == true
        if (!milOn && ((milDistanceKm ?: 0) > 0 || (milMinutes ?: 0) > 0)) {
            val parts = listOfNotNull(milDistanceKm?.takeIf { it > 0 }?.let { distanceText(it) }, milMinutes?.takeIf { it > 0 }?.let { durationText(it) })
            out += "The check-engine light has been on for ${parts.joinToString(" and ")} since the codes were cleared, and is off now."
        }
        return out
    }

    /** The report's section, as plain lines. */
    fun reportLines(): List<String> =
        listOf("Codes recently cleared? ${headline()}") + notes() + lines().map { (k, v) -> "  $k: $v" } + listOf(CAVEAT)

    private fun sinceParts(): List<String> = listOfNotNull(
        distanceKm?.let { distanceText(it) },
        warmUps?.let { warmUpText(it) },
        // Time only when there's nothing else to go by: it says less than distance and warm-ups.
        minutes?.takeIf { distanceKm == null && warmUps == null }?.let { "${durationText(it)} of engine running" },
    )

    private fun monitorText(): String =
        if (incompleteMonitors == 1) "1 readiness monitor is" else "$incompleteMonitors readiness monitors are"

    companion object {
        const val NA = "N/A"

        /**
         * Below these, a clear is "recent": about a week of short daily drives. Below the second set,
         * "fairly recent": a few weeks. The counters top out at 65,535 km and 255 warm-ups.
         */
        const val RECENT_KM = 100
        const val FAIRLY_KM = 500
        const val RECENT_WARM_UPS = 10
        const val FAIRLY_WARM_UPS = 30
        const val RECENT_MINUTES = 300
        const val FAIRLY_MINUTES = 1500

        private const val MAX_KM = 65_535
        private const val MAX_WARM_UPS = 255

        const val CAVEAT = "Clearing codes isn't the only thing that resets these counters: disconnecting the battery or replacing the engine computer does too. A recent reset isn't proof of a hidden fault, but it's worth asking why."

        /** The five counters' requests, in the order they're read. */
        val PIDS = listOf(0x31, 0x30, 0x4E, 0x21, 0x4D)

        /** A counter from its answer: one byte for warm-ups (30), two for the rest. */
        fun counter(pid: Int, bytes: IntArray?): Int? = when {
            bytes == null -> null
            pid == 0x30 -> bytes.getOrNull(0)
            bytes.size < 2 -> null
            else -> bytes[0] * 256 + bytes[1]
        }

        fun distanceText(km: Int): String {
            val shown = Math.round(Units.distance(km.toDouble()))
            val text = String.format(Locale.US, "%,d %s", shown, Units.distanceUnit)
            return if (km >= MAX_KM) "more than $text" else text
        }

        fun warmUpText(n: Int): String = when {
            n >= MAX_WARM_UPS -> "more than $n warm-ups"
            n == 1 -> "1 warm-up"
            else -> "$n warm-ups"
        }

        fun durationText(minutes: Int): String = when {
            minutes < 60 -> "$minutes min"
            minutes < 48 * 60 -> String.format(Locale.US, "%d h %d min", minutes / 60, minutes % 60).replace(" 0 min", "")
            else -> String.format(Locale.US, "%,d h", minutes / 60)
        }
    }
}
