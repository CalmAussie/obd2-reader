package com.obdreader.app.obd

import java.util.Locale

/**
 * A battery check each time the engine starts: the battery's resting voltage just before, and the
 * lowest it dropped to while the starter motor turned the engine. A healthy battery stays above
 * about 10 V while cranking; one that drops much below 9.6 V may soon struggle to start the car.
 * The voltage is the adapter's own reading at the OBD2 port.
 */
object BatteryCheck {

    /** [atMs]: when it was measured (wall-clock time once stored). */
    data class Result(val atMs: Long, val restingV: Double, val crankingV: Double) {
        val rating: Rating get() = when {
            crankingV < WEAK_V -> Rating.WEAK
            crankingV < GOOD_V || restingV < LOW_REST_V -> Rating.FAIR
            else -> Rating.GOOD
        }

        fun encode(): String = String.format(Locale.US, "t=%d;rest=%.2f;crank=%.2f", atMs, restingV, crankingV)

        /** "Lowest while starting 10.4 V (resting 12.6 V)". */
        fun summary(): String = String.format(Locale.US, "Lowest while starting %.1f V (resting %.1f V)", crankingV, restingV)
    }

    enum class Rating(val label: String, val advice: String) {
        GOOD("Healthy", "The battery held its voltage well while starting."),
        FAIR("Getting weaker", "The battery dipped more than a healthy one would. Keep an eye on it, especially in cold weather."),
        WEAK("Weak", "The battery dropped low while starting, so it may soon fail to start the car. Have it tested (most battery shops do it free)."),
    }

    const val GOOD_V = 10.0
    const val WEAK_V = 9.6
    const val LOW_REST_V = 12.2
    /** Engine speeds: below this it's off (or just the starter turning it), above this it's running. */
    private const val OFF_RPM = 300.0
    private const val RUNNING_RPM = 500.0

    fun decode(line: String): Result? {
        val m = line.split(";").mapNotNull { p -> p.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        return try { Result(m.getValue("t").toLong(), m.getValue("rest").toDouble(), m.getValue("crank").toDouble()) } catch (e: Exception) { null }
    }

    fun parseHistory(text: String?): List<Result> = text.orEmpty().lines().mapNotNull { decode(it) }.sortedByDescending { it.atMs }

    fun addToHistory(text: String?, r: Result, max: Int = 50): String =
        (listOf(r) + parseHistory(text).filter { it.atMs != r.atMs }).take(max).joinToString("\n") { it.encode() }

    /**
     * Watches voltage and engine speed while connected. Fed every reading; when the engine goes
     * from off to running, [update] returns the check for that start. Needs the engine to have been
     * seen off for a couple of seconds first (so connecting to a running engine gives nothing).
     */
    class Watch {
        private val volts = ArrayDeque<Pair<Long, Double>>()
        private var offSinceMs: Long? = null
        private var rpm: Double? = null

        val engineOff: Boolean get() = offSinceMs != null

        fun reset() { volts.clear(); offSinceMs = null; rpm = null }

        fun update(nowMs: Long, voltage: Double?, engineRpm: Double?): Result? {
            if (voltage != null && voltage in 5.0..17.0) {
                volts.addLast(nowMs to voltage)
                while (volts.isNotEmpty() && nowMs - volts.first().first > 20_000) volts.removeFirst()
            }
            if (engineRpm == null) return null
            val was = rpm; rpm = engineRpm
            if (engineRpm < OFF_RPM) {
                if (offSinceMs == null) offSinceMs = nowMs
                return null
            }
            if (engineRpm < RUNNING_RPM) return null // the starter turning it, not running yet
            val off = offSinceMs ?: return null
            offSinceMs = null
            if (was == null || nowMs - off < 2_000) return null
            // Resting: the readings from before the last 4 s (the crank and the moment before it).
            val rest = volts.filter { it.first in off..(nowMs - 4_000) }.map { it.second }
            val crank = volts.filter { it.first > nowMs - 6_000 }.map { it.second }
            if (rest.isEmpty() || crank.size < 2) return null
            val restV = rest.sorted()[rest.size / 2]
            val crankV = crank.min()
            // A dip of under 0.3 V means the start wasn't caught (readings too slow): no result.
            if (restV - crankV < 0.3) return null
            return Result(nowMs, restV, crankV)
        }
    }
}
