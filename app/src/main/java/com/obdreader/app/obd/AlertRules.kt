package com.obdreader.app.obd

/**
 * Decides when to warn the driver: engine running hot, the battery not charging (or charging
 * too hard), and a new trouble code appearing during the drive.
 *
 * Each warning needs the problem to last a few seconds, so one odd reading doesn't set it off,
 * and it only goes off again after things have properly recovered (hysteresis), so a reading
 * hovering at the limit doesn't nag.
 */
class AlertRules {

    enum class Kind { COOLANT_HOT, OIL_HOT, NOT_CHARGING, BATTERY_LOW, OVERCHARGING, NEW_CODE }

    data class Alert(val kind: Kind, val title: String, val text: String, val code: String? = null)

    private class Watch(val holdMs: Long) {
        var since: Long? = null
        var fired = false
        /** True the moment the problem has lasted [holdMs]. */
        fun check(now: Long, bad: Boolean, recovered: Boolean): Boolean {
            if (fired) { if (recovered) { fired = false; since = null }; return false }
            if (!bad) { since = null; return false }
            val start = since ?: now.also { since = it }
            if (now - start >= holdMs) { fired = true; return true }
            return false
        }
    }

    private val coolant = Watch(5_000)
    private val oil = Watch(5_000)
    private val notCharging = Watch(20_000)
    private val low = Watch(20_000)
    private val high = Watch(10_000)
    private var knownCodes: Set<String>? = null

    fun reset() {
        listOf(coolant, oil, notCharging, low, high).forEach { it.since = null; it.fired = false }
        knownCodes = null
    }

    /** Checks the latest readings at [nowMs]. Values the car doesn't report are null. */
    fun readings(nowMs: Long, rpm: Double?, coolantC: Double?, oilC: Double?, volts: Double?): List<Alert> {
        val out = mutableListOf<Alert>()
        if (coolantC != null && coolant.check(nowMs, coolantC >= COOLANT_HOT, coolantC < COOLANT_HOT - 6)) {
            out += Alert(Kind.COOLANT_HOT, "Engine running hot", "Coolant is at ${Units.temperatureText(coolantC)}. Pull over safely, let the engine idle or switch it off, and check the coolant once it's cool.")
        }
        if (oilC != null && oil.check(nowMs, oilC >= OIL_HOT, oilC < OIL_HOT - 8)) {
            out += Alert(Kind.OIL_HOT, "Oil running hot", "Oil is at ${Units.temperatureText(oilC)}. Ease off and let it cool down.")
        }
        val running = (rpm ?: 0.0) > 500
        if (volts != null) {
            if (notCharging.check(nowMs, running && volts < NOT_CHARGING, !running || volts > NOT_CHARGING + 0.4)) {
                out += Alert(Kind.NOT_CHARGING, "Battery not charging", "Only ${fmt(volts)} V with the engine running (normally 13.5–14.7 V). The alternator or its belt may be failing; get it checked soon.")
            }
            if (low.check(nowMs, !running && volts < BATTERY_LOW, running || volts > BATTERY_LOW + 0.4)) {
                out += Alert(Kind.BATTERY_LOW, "Battery low", "Only ${fmt(volts)} V with the engine off. The car may not start; charge the battery or start the engine.")
            }
            if (high.check(nowMs, volts > OVERCHARGING, volts < OVERCHARGING - 0.4)) {
                out += Alert(Kind.OVERCHARGING, "Charging voltage high", "${fmt(volts)} V is more than a battery should get (normally up to 14.7 V). The voltage regulator may be faulty; get it checked.")
            }
        }
        return out
    }

    /**
     * Checks the car's trouble codes. The first check of a drive is what the car already had;
     * after that, each code not seen before is a new alert. [describe] gives a code's title.
     */
    fun codes(codes: Collection<String>, describe: (String) -> String): List<Alert> {
        val known = knownCodes
        knownCodes = (known ?: emptySet()) + codes
        if (known == null) return emptyList()
        return codes.filter { it !in known }.distinct().map { Alert(Kind.NEW_CODE, "New trouble code $it", describe(it), code = it) }
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.1f", v)

    companion object {
        const val COOLANT_HOT = 110.0
        const val OIL_HOT = 130.0
        const val NOT_CHARGING = 12.8
        const val BATTERY_LOW = 11.8
        const val OVERCHARGING = 15.2
    }
}
