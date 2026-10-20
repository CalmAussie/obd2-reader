package com.obdreader.app.obd

/**
 * Whether the battery looks like it's charging, estimated from the battery voltage and whether
 * the engine is running. Standard OBD2 has no battery-current reading, so this is an estimate,
 * and it's always labelled as one. The thresholds match [AlertRules], so the status and the
 * battery alerts never disagree.
 *
 * Cars with "smart" charging lower the charging voltage on purpose while cruising (to about
 * 12.8–13.2 V), which shows as "charging weakly" rather than a fault.
 */
object ChargingStatus {

    enum class State(val short: String) {
        CHARGING("charging"),
        WEAK("charging weakly"),
        NOT_CHARGING("not charging"),
        OVERCHARGING("overcharging"),
        ENGINE_OFF("engine off"),
        ENGINE_OFF_LOW("engine off, low"),
    }

    /** Engine speed above which the engine counts as running (the same as the alerts). */
    const val RUNNING_RPM = 500.0
    const val CHARGING_V = 13.2
    const val NOT_CHARGING_V = AlertRules.NOT_CHARGING
    const val OVERCHARGING_V = AlertRules.OVERCHARGING
    /** Resting voltage below which a battery is about half charged or less. */
    const val LOW_RESTING_V = 12.2

    /** The status for one pair of readings, or null without both (no guessing). */
    fun of(volts: Double?, rpm: Double?): State? {
        if (volts == null || rpm == null) return null
        return if (rpm > RUNNING_RPM) when {
            volts > OVERCHARGING_V -> State.OVERCHARGING
            volts >= CHARGING_V -> State.CHARGING
            volts >= NOT_CHARGING_V -> State.WEAK
            else -> State.NOT_CHARGING
        } else if (volts >= LOW_RESTING_V) State.ENGINE_OFF else State.ENGINE_OFF_LOW
    }

    /** "Battery · charging (est.)", or without "(est.)" where there's little room. */
    fun label(state: State?, roomy: Boolean = true): String =
        if (state == null) "Battery" else "Battery · ${state.short}" + if (roomy) " (est.)" else ""

    /**
     * Holds the shown status steady: a new status only shows once it has lasted a few seconds,
     * and "not charging" or "charging weakly" only after 20 seconds (like the alert), so a
     * moment's dip with the fans or lights coming on doesn't flip it.
     */
    class Watch {
        var shown: State? = null
            private set
        private var candidate: State? = null
        private var since = 0L

        fun reset() { shown = null; candidate = null; since = 0L }

        fun update(nowMs: Long, volts: Double?, rpm: Double?): State? {
            val now = of(volts, rpm)
            if (now == null) { reset(); return null }
            if (now == shown) { candidate = null; return shown }
            if (now != candidate) { candidate = now; since = nowMs }
            if (nowMs - since >= holdMs(now)) { shown = now; candidate = null }
            return shown
        }

        private fun holdMs(s: State) = when (s) {
            State.NOT_CHARGING, State.WEAK -> 20_000L
            State.OVERCHARGING -> 10_000L
            else -> 5_000L
        }
    }
}
