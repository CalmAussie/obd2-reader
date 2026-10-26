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
    const val WAKE_PROBE_MS = 5000L

    /**
     * How often to read the battery (the adapter's own voltage reading, which doesn't involve the
     * car) while the ignition is off. It also tells when someone turns the key: see [shouldProbe].
     */
    const val VOLT_READ_MS = 1000L

    /** With "watch the battery while the car is off" on, the app waits this long for the car. */
    const val WATCH_GIVE_UP_AFTER_MS = 24 * 3600_000L

    /**
     * While the ignition is off: ask the car whether it's awake now? Every [WAKE_PROBE_MS], or at
     * once when the battery voltage moves by 0.15 V or more from where it sat (turning the key on
     * switches things on, which shows in the voltage), so live data comes back quickly without
     * asking the car every second and keeping it from going to sleep.
     */
    fun shouldProbe(sinceLastProbeMs: Long, volts: Double?, restingVolts: Double?): Boolean =
        sinceLastProbeMs >= WAKE_PROBE_MS ||
            (volts != null && restingVolts != null && kotlin.math.abs(volts - restingVolts) >= 0.15)

    fun delayFor(attempt: Int): Long = if (attempt < DELAYS_MS.size) DELAYS_MS[attempt] else STEADY_MS

    fun shouldGiveUp(waitingSinceMs: Long, nowMs: Long, limitMs: Long = GIVE_UP_AFTER_MS): Boolean = nowMs - waitingSinceMs >= limitMs
}

/** Thrown by the polling loop when the car stops answering but the adapter is still there. */
class IgnitionOffException : Exception("The ignition looks off")
