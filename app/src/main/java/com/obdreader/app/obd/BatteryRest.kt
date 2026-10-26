package com.obdreader.app.obd

import java.util.Locale
import kotlin.math.roundToInt

/**
 * The battery while the car is off: the adapter's own voltage reading at the OBD2 port (the port's
 * power pin stays live with the ignition off on most cars), a rough charge level from it, and an
 * overnight drain check. Nothing here asks the car anything, so it doesn't wake the car up.
 */
object BatteryRest {

    /** Resting voltage of a 12 V lead-acid battery against its charge (typical figures). */
    private val TABLE = listOf(11.30 to 0, 11.51 to 10, 11.66 to 20, 11.81 to 30, 11.96 to 40, 12.10 to 50,
        12.24 to 60, 12.37 to 70, 12.50 to 80, 12.62 to 90, 12.73 to 100)

    /** Rough charge (0–100%) from a resting voltage, or null if it's out of range for a 12 V battery. */
    fun charge(volts: Double): Int? {
        if (volts < 10.5 || volts > 13.2) return null
        if (volts <= TABLE.first().first) return 0
        if (volts >= TABLE.last().first) return 100
        val i = TABLE.indexOfFirst { it.first >= volts }
        val (v0, c0) = TABLE[i - 1]; val (v1, c1) = TABLE[i]
        return (c0 + (c1 - c0) * (volts - v0) / (v1 - v0)).roundToInt()
    }

    /** "12.5 V, about 80% charged" (or just the voltage when it can't be read as a charge level). */
    fun describe(volts: Double): String {
        val c = charge(volts)
        return String.format(Locale.US, "%.1f V", volts) + (c?.let { ", about $it% charged" } ?: "")
    }

    // ---- Overnight drain check ----

    /** One reading while the car was off. */
    data class Sample(val atMs: Long, val volts: Double)

    /** How long after the engine stops before readings count: the battery settles first. */
    const val SETTLE_MS = 2 * 3600_000L
    /** Readings needed after settling before saying anything. */
    const val MIN_SPAN_MS = 3 * 3600_000L
    /** Falling faster than this (in volts an hour) after settling suggests something is draining it. */
    const val DRAIN_V_PER_HOUR = 0.02
    /** A reading every this often while the car is off. */
    const val SAMPLE_EVERY_MS = 5 * 60_000L

    data class Drain(val fromV: Double, val toV: Double, val hours: Double, val voltsPerHour: Double) {
        val draining: Boolean get() = voltsPerHour > DRAIN_V_PER_HOUR
        fun summary(): String = String.format(Locale.US, "%.2f V to %.2f V over %.0f h", fromV, toV, hours) +
            if (draining) ": dropping faster than it should, so something may be draining the battery while the car is off."
            else ": holding its charge as it should."
    }

    /**
     * The drain over one night from [samples] (oldest first), with [engineOffMs] the moment the car
     * was switched off. Readings in the first two hours are left out (a battery settles after a
     * drive). Null until there are three hours of settled readings.
     */
    fun drain(samples: List<Sample>, engineOffMs: Long): Drain? {
        val settled = samples.filter { it.atMs >= engineOffMs + SETTLE_MS }.sortedBy { it.atMs }
        if (settled.size < 3) return null
        val span = settled.last().atMs - settled.first().atMs
        if (span < MIN_SPAN_MS) return null
        // Averages of the first and last few readings, so one jumpy reading doesn't decide it.
        val n = minOf(3, settled.size / 3).coerceAtLeast(1)
        val from = settled.take(n).map { it.volts }.average()
        val to = settled.takeLast(n).map { it.volts }.average()
        val hours = span / 3600_000.0
        return Drain(from, to, hours, (from - to) / hours)
    }

    fun encode(samples: List<Sample>): String = samples.joinToString("\n") { "${it.atMs};${String.format(Locale.US, "%.2f", it.volts)}" }

    fun decode(text: String?): List<Sample> = text.orEmpty().lines().mapNotNull { l ->
        val p = l.split(";"); if (p.size != 2) null else p[0].toLongOrNull()?.let { t -> p[1].toDoubleOrNull()?.let { Sample(t, it) } }
    }
}
