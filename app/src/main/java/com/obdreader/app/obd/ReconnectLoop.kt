package com.obdreader.app.obd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * The connection's outer loop: runs one connection to the car, and when it ends decides whether to
 * try again, waits, builds a fresh link and gives up after a while. What it needs from the phone
 * comes in as functions, so its decisions can be tested on a fake clock.
 *
 * @param now the phone's clock (time since boot), in milliseconds.
 * @param mayRetry whether reconnecting is allowed at all (not for the demo car, and only with
 *   "Reconnect automatically" on). Asked each time, so changing the setting takes effect.
 * @param startedByApp a connection the app started by itself (on launch or with Android Auto),
 *   which keeps trying even before it ever reached the car, for a shorter time.
 * @param neverWorks errors not worth retrying, such as a device that isn't an OBD2 adapter.
 * @param freshLink a new link to the last adapter (a closed Bluetooth or Wi-Fi link can't be
 *   reopened), or null while Bluetooth is off.
 * @param waiting called before each wait: false while waiting for the car, true while waiting for
 *   Bluetooth to come back on.
 */
class ReconnectLoop<L>(
    private val now: () -> Long,
    private val mayRetry: () -> Boolean,
    private val startedByApp: Boolean,
    private val neverWorks: (Exception) -> Boolean,
    private val freshLink: () -> L?,
    private val waiting: (bluetoothOff: Boolean) -> Unit,
) {
    sealed interface End {
        val everConnected: Boolean

        /** The connection ended with [error] (null if it simply closed) and isn't tried again. */
        data class Stopped(override val everConnected: Boolean, val error: Exception?) : End

        /** Tried for too long without reaching the car. */
        data class GaveUp(override val everConnected: Boolean) : End
    }

    /**
     * Connects through [first] with [session], which runs until the link drops and calls its
     * `connected` argument once the car answers. [close] is called on every link when its
     * session ends. Cancelling the coroutine (Disconnect) stops it at once.
     */
    suspend fun run(first: L, session: suspend (link: L, connected: () -> Unit) -> Unit, close: (L) -> Unit): End {
        var current = first
        var everConnected = false
        var attempt = 0
        var waitingSince = now()
        while (true) {
            var lastError: Exception? = null
            try {
                session(current) {
                    everConnected = true
                    attempt = 0
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            } finally {
                close(current)
            }
            // Disconnected by the user while the link was closing: stop here, quietly.
            currentCoroutineContext().ensureActive()

            val retry = mayRetry() && (everConnected || startedByApp) && (lastError == null || !neverWorks(lastError))
            if (!retry) return End.Stopped(everConnected, lastError)
            if (attempt == 0) waitingSince = now()
            val limit = if (everConnected) ReconnectPolicy.GIVE_UP_AFTER_MS else ReconnectPolicy.AUTO_START_GIVE_UP_MS
            if (ReconnectPolicy.shouldGiveUp(waitingSince, now(), limit)) return End.GaveUp(everConnected)
            waiting(false)
            delay(ReconnectPolicy.delayFor(attempt++))
            var next = freshLink()
            while (next == null) {
                if (ReconnectPolicy.shouldGiveUp(waitingSince, now(), limit)) break
                waiting(true)
                delay(ReconnectPolicy.delayFor(Int.MAX_VALUE))
                next = freshLink()
            }
            current = next ?: current
        }
    }
}
