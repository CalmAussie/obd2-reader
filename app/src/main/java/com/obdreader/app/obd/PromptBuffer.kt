package com.obdreader.app.obd

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Collects an adapter's reply bytes as they arrive (on another thread) and hands back one reply at
 * a time, up to the adapter's '>' prompt. Used by links that are told about new bytes rather than
 * reading them, such as Bluetooth LE.
 *
 * After a command times out its reply may still turn up. The next command first waits up to
 * [lateReplyWaitMs] for that late reply and throws it away, so it isn't taken for its own answer.
 *
 * It holds at most [maxChars]: past that, what's waiting is dropped and the command waiting for it
 * (or the next one) fails with [TOO_MUCH_FROM_ADAPTER].
 */
class PromptBuffer(
    private val lateReplyWaitMs: Long = LATE_REPLY_WAIT_MS,
    private val maxChars: Int = MAX_REPLY_CHARS,
) {
    private val lock = ReentrantLock()
    private val arrived = lock.newCondition()
    private val rx = StringBuilder()
    private var lateReplyOwed = false
    private var tooMuch = false

    /** Adds bytes the adapter sent. */
    fun append(bytes: ByteArray) = lock.withLock {
        for (b in bytes) {
            if (rx.length >= maxChars) {
                rx.setLength(0)
                tooMuch = true
            }
            rx.append((b.toInt() and 0xFF).toChar())
        }
        arrived.signalAll()
    }

    /** Throws [TOO_MUCH_FROM_ADAPTER] once if the buffer overflowed. Call with the lock held. */
    private fun checkTooMuch() {
        if (tooMuch) {
            tooMuch = false
            rx.setLength(0)
            throw IOException(TOO_MUCH_FROM_ADAPTER)
        }
    }

    /** Wakes a waiting command, for instance because the link dropped ([failure] then says so). */
    fun wake() = lock.withLock { arrived.signalAll() }

    /** Marks a reply as owed by a command that gave up before its reply came. */
    fun replyIsLate() = lock.withLock { lateReplyOwed = true }

    /**
     * Call before sending a command: drops a late reply (waiting a moment for it if one is owed) and
     * anything else left over. [failure] is checked while waiting and thrown as an [IOException].
     */
    fun prepare(failure: () -> String?) = lock.withLock {
        if (lateReplyOwed) {
            lateReplyOwed = false
            val deadline = System.currentTimeMillis() + lateReplyWaitMs
            while (rx.indexOf(">") < 0) {
                failure()?.let { throw IOException(it) }
                checkTooMuch()
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                arrived.await(remaining, TimeUnit.MILLISECONDS)
            }
        }
        checkTooMuch()
        rx.setLength(0)
    }

    /** Waits up to [timeoutMs] for the reply to [command] and returns it without the prompt. */
    fun awaitReply(command: String, timeoutMs: Long, failure: () -> String?): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        lock.withLock {
            while (true) {
                failure()?.let { throw IOException(it) }
                checkTooMuch()
                val idx = rx.indexOf(">")
                if (idx >= 0) {
                    val out = rx.substring(0, idx)
                    rx.delete(0, idx + 1)
                    return out
                }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    lateReplyOwed = true
                    throw AdapterTimeoutException("No reply to '$command'")
                }
                arrived.await(remaining, TimeUnit.MILLISECONDS)
            }
        }
    }
}
