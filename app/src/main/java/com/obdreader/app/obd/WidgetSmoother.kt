package com.obdreader.app.obd

import kotlin.math.abs
import kotlin.math.exp

/**
 * Eases the widget's live readings towards each new value, so the numbers roll up and down a step
 * at a time instead of jumping between the car's readings. A reading the car stops giving goes
 * blank (N/A) at once; a reading seen for the first time shows straight away.
 */
class WidgetSmoother(private val tauMs: Double = 500.0) {
    private val shown = mutableMapOf<String, Double>()

    /** The readings to show after [dtMs] milliseconds, easing each towards its latest value in [targets]. */
    fun step(targets: Map<String, Double?>, dtMs: Long): Map<String, Double?> {
        val a = 1 - exp(-dtMs.coerceAtLeast(0) / tauMs)
        val out = mutableMapOf<String, Double?>()
        for ((k, t) in targets) {
            if (t == null) { shown.remove(k); out[k] = null; continue }
            val prev = shown[k]
            val next = if (prev == null) t else prev + (t - prev) * a
            // Close enough: land on the reading rather than creep towards it.
            val v = if (abs(t - next) < abs(t) * 0.01 + 0.05) t else next
            shown[k] = v
            out[k] = v
        }
        shown.keys.retainAll(targets.keys)
        return out
    }

    fun reset() = shown.clear()
}
