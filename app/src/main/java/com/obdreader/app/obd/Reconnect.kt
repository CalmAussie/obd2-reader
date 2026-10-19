package com.obdreader.app.obd

/**
 * Decides when the car has gone quiet because the ignition was turned off. The adapter stays
 * powered from the OBD2 port, so the link stays up, but the engine computer stops answering.
 * A few cycles in a row with no readings at all means the ignition is off.
 */
class IgnitionWatch(private val quietCycles: Int = 3) {
    private var quiet = 0

    /** Call once per polling cycle. Returns true when the ignition looks off. */
    fun cycle(answered: Int, asked: Int): Boolean {
        quiet = if (asked > 0 && answered == 0) quiet + 1 else 0
        return quiet >= quietCycles
    }

    fun reset() {
        quiet = 0
    }
}

/**
 * How long to wait between reconnect attempts: quick at first (the ignition is often turned
 * straight back on), then settling at a few seconds so it doesn't drain the phone's battery.
 */
object ReconnectPolicy {
    private val DELAYS_MS = longArrayOf(500, 1000, 1000, 2000, 2000, 3000, 3000)
    private const val STEADY_MS = 5000L

    /** Gives up after this long without reaching the car, so it isn't left trying all night. */
    const val GIVE_UP_AFTER_MS = 30 * 60 * 1000L

    /**
     * A connection the app started by itself (on opening, or when Android Auto starts) that has
     * never reached the car gives up sooner, so opening the app at home doesn't leave it searching.
     */
    const val AUTO_START_GIVE_UP_MS = 3 * 60 * 1000L

    /** How often to ask the car if it's awake again while the adapter is still connected. */
    const val WAKE_PROBE_MS = 1500L

    fun delayFor(attempt: Int): Long = if (attempt < DELAYS_MS.size) DELAYS_MS[attempt] else STEADY_MS

    fun shouldGiveUp(waitingSinceMs: Long, nowMs: Long, limitMs: Long = GIVE_UP_AFTER_MS): Boolean = nowMs - waitingSinceMs >= limitMs
}

/** Thrown by the polling loop when the car stops answering but the adapter is still there. */
class IgnitionOffException : Exception("The ignition looks off")
