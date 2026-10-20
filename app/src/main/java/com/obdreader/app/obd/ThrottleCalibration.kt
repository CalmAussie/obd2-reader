package com.obdreader.app.obd

/**
 * Turns the car's throttle reading into how far the throttle is actually open.
 *
 * OBD2's throttle position (PID 11) is the raw sensor, and almost no car reads 0% with the
 * throttle closed: most sit somewhere around 10–20% at idle, and a few never reach 100% even flat out. So with your foot off the pedal at a red light,
 * the raw value still "shows throttle".
 *
 * This learns the closed position as the lowest reading seen (remembered between drives), and
 * shows the reading measured from there: 0% with your foot off, rising to 100% wide open.
 */
class ThrottleCalibration(saved: Double? = null) {

    /** The raw reading with the throttle closed, once one has been seen. */
    var closed: Double? = saved?.takeIf { it in 0.0..MAX_CLOSED }
        private set

    /** True when [closed] went down this time, so the caller can save it. */
    var changed = false
        private set

    fun adjust(raw: Double): Double {
        changed = false
        // Anything above MAX_CLOSED can't be a closed throttle (the pedal must be pressed), so it never becomes the baseline.
        if (raw in 0.0..MAX_CLOSED && (closed == null || raw < closed!!)) {
            closed = raw
            changed = true
        }
        val base = closed ?: return raw.coerceIn(0.0, 100.0)
        if (base >= 100.0) return 0.0
        return ((raw - base) / (100.0 - base) * 100.0).coerceIn(0.0, 100.0)
    }

    companion object {
        /** No real throttle sensor reads more than this when closed. */
        const val MAX_CLOSED = 35.0
        const val KEY = "11"
    }
}
