package com.obdreader.app.obd

import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Waiting out the ignition being off while the adapter stays connected: reads the battery from the
 * adapter every second (that doesn't involve the car), asks the car every few seconds whether it's
 * awake (at once if the voltage moves, as it does when the key turns) and returns as soon as it is.
 *
 * @param now the phone's clock (time since boot), in milliseconds.
 * @param limitMs how long to wait for the car before giving up with an [IOException].
 * @param readVoltage the adapter's battery reading, or null if it gave none.
 * @param carAwake asks the car whether it answers again.
 * @param onVoltage called with each battery reading and the time it was taken.
 */
class IgnitionWait(
    private val now: () -> Long,
    private val limitMs: Long,
    private val readVoltage: suspend () -> Double?,
    private val carAwake: suspend () -> Boolean,
    private val onVoltage: (volts: Double, atMs: Long) -> Unit = { _, _ -> },
) {
    /** Returns when the car answers again; throws [IOException] after [limitMs] without it. */
    suspend fun run() {
        val since = now()
        var lastProbe = now()
        var resting: Double? = null
        while (true) {
            delay(ReconnectPolicy.VOLT_READ_MS)
            val v = try { readVoltage() } catch (e: AdapterTimeoutException) { null }
            val t = now()
            if (v != null) {
                onVoltage(v, t)
                if (resting == null) resting = v
            }
            if (ReconnectPolicy.shouldProbe(t - lastProbe, v, resting)) {
                lastProbe = t
                val awake = try { carAwake() } catch (e: AdapterTimeoutException) { false }
                if (awake) return
                resting = v ?: resting
            }
            if (ReconnectPolicy.shouldGiveUp(since, t, limitMs)) {
                throw IOException("The car didn't answer for ${describe(limitMs)}.")
            }
        }
    }

    private fun describe(ms: Long): String =
        if (ms >= 3600_000L && ms % 3600_000L == 0L) "${ms / 3600_000L} hours" else "${ms / 60_000L} minutes"
}
