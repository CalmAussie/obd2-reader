package com.obdreader.app.obd

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The high-voltage battery of a hybrid or electric car, for the EV battery sheet: the standard
 * reading every make can report (service 01 PID 5B, remaining charge), plus the maker readings
 * owners found on their own cars ([EnhancedReadings], shown as "Unofficial"). Everything here only
 * reads; the app never wakes or commands the high-voltage system.
 *
 * PID 9A (hybrid/EV system data) is listed by some cars, but its scaling isn't published, so the
 * app doesn't guess at it.
 */
object EvBattery {

    /** The standard reading's key in the session's readings. */
    const val CHARGE_KEY = "5B"

    /** Maker readings that are about the high-voltage battery (others, like odometer, stay in Enhanced diagnostics). */
    private val BATTERY_IDS = setOf(
        "leaf.soc", "leaf.capacity", "leaf.hv_volts", "leaf.temp1",
        "zoe.soc", "zoe.soh", "zoe.range", "zoe.batt_temp",
    )

    /** The maker readings that state the battery's charge, for the charging graph when 5B isn't there. */
    private val CHARGE_IDS = setOf("leaf.soc", "zoe.soc")

    fun makerReadings(make: String?, model: String?, year: Int?): List<EnhancedReadings.Reading> =
        EnhancedReadings.forCar(make, model, year).filter { it.id in BATTERY_IDS }

    fun isCharge(r: EnhancedReadings.Reading) = r.id in CHARGE_IDS

    /** The battery's charge to graph: the standard reading if the car gives it, else a maker one. */
    fun charge(standard: Double?, maker: Map<EnhancedReadings.Reading, Double?>): Double? =
        standard?.takeIf { it in 0.0..100.0 }
            ?: maker.entries.firstOrNull { isCharge(it.key) && it.value != null }?.value?.takeIf { it in 0.0..100.0 }

    /** The pack's stored energy in kWh from a capacity (Ah) and voltage, where the car gives both. */
    fun packKwh(capacityAh: Double?, volts: Double?): Double? =
        if (capacityAh == null || volts == null || capacityAh <= 0 || volts <= 0) null else capacityAh * volts / 1000.0

    /**
     * The charge over time while the sheet is open, and how fast it's changing. A car's charge
     * reading moves in steps (often 0.5–1 %), so the rate is a straight-line fit over the last
     * [WINDOW_MS], and only once there's [MIN_SPAN_MS] of it.
     */
    class ChargeLog(private val max: Int = 2000) {
        data class Point(val timeMs: Long, val percent: Double)

        private val points = ArrayList<Point>()
        val all: List<Point> get() = points.toList()

        fun add(timeMs: Long, percent: Double?) {
            if (percent == null || !percent.isFinite() || percent !in 0.0..100.0) return
            if (points.isNotEmpty() && timeMs <= points.last().timeMs) return
            // A gap of over 10 minutes (the car was off, or the sheet closed): start again.
            if (points.isNotEmpty() && timeMs - points.last().timeMs > GAP_MS) points.clear()
            points += Point(timeMs, percent)
            if (points.size > max) points.subList(0, points.size - max).clear()
        }

        fun clear() = points.clear()

        /** Percent per hour (positive while charging), or null until there's enough to tell. */
        fun ratePerHour(): Double? {
            val last = points.lastOrNull() ?: return null
            val recent = points.filter { last.timeMs - it.timeMs <= WINDOW_MS }
            if (recent.size < 3 || last.timeMs - recent.first().timeMs < MIN_SPAN_MS) return null
            val t0 = recent.first().timeMs
            val xs = recent.map { (it.timeMs - t0) / 3_600_000.0 }
            val ys = recent.map { it.percent }
            val mx = xs.average(); val my = ys.average()
            val sxx = xs.sumOf { (it - mx) * (it - mx) }
            if (sxx <= 0) return null
            return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx
        }

        fun state(): State? {
            val r = ratePerHour() ?: return null
            return when {
                r >= STEADY_RATE -> State.CHARGING
                r <= -STEADY_RATE -> State.USING
                else -> State.STEADY
            }
        }

        /** Minutes until [target] % at the current rate, while charging below it. */
        fun minutesTo(target: Double): Int? {
            val r = ratePerHour() ?: return null
            val now = points.lastOrNull()?.percent ?: return null
            if (r < STEADY_RATE || now >= target) return null
            return ((target - now) / r * 60).roundToInt()
        }
    }

    enum class State { CHARGING, USING, STEADY }

    const val WINDOW_MS = 10 * 60_000L
    const val MIN_SPAN_MS = 3 * 60_000L
    const val GAP_MS = 10 * 60_000L
    /** Below this many % per hour either way, the charge counts as steady. */
    const val STEADY_RATE = 2.0

    /**
     * Charging power, worked out from how fast the charge rises and the pack's size: an estimate
     * (the charger's own losses aren't counted), and only where the car gives the pack's size.
     */
    fun powerKw(ratePerHour: Double?, packKwh: Double?): Double? =
        if (ratePerHour == null || packKwh == null) null else ratePerHour / 100.0 * packKwh

    /** "Charging: +18 % an hour", "Using charge: −6 % an hour", "Steady". */
    fun describe(log: ChargeLog): String? {
        val r = log.ratePerHour() ?: return null
        val pace = String.format(Locale.US, "%.0f %% an hour", abs(r))
        return when (log.state()) {
            State.CHARGING -> "Charging: +$pace"
            State.USING -> "Using charge: −$pace"
            else -> "Steady"
        }
    }

    /** "1 h 25 min", "40 min". */
    fun duration(minutes: Int): String = when {
        minutes >= 60 -> "${minutes / 60} h" + (minutes % 60).let { if (it == 0) "" else " $it min" }
        else -> "$minutes min"
    }
}
