package com.obdreader.app.obd

import java.io.IOException

/**
 * A byte pipe to an ELM327-compatible adapter. All methods are blocking and
 * must be called off the main thread.
 */
interface ObdTransport {
    val name: String

    fun open()

    /** Sends [command] followed by CR and returns everything up to the '>' prompt. */
    fun exchange(command: String, timeoutMs: Long): String

    fun close()
}

class AdapterTimeoutException(message: String) : IOException(message)

/**
 * After a command times out, its reply can still turn up a moment later. The next command first
 * waits up to this long for that late reply and throws it away, so it isn't taken for its own answer.
 */
const val LATE_REPLY_WAIT_MS = 1000L

/**
 * The most an adapter may send for one command. A real ELM327 answer is a few hundred bytes at
 * most (a few KB for the longest multi-frame replies), so anything bigger is a broken or hostile
 * adapter: the command fails with [TOO_MUCH_FROM_ADAPTER] instead of the app filling its memory.
 */
const val MAX_REPLY_CHARS = 64 * 1024

/** The error for a reply longer than [MAX_REPLY_CHARS], or an adapter that won't stop sending. */
const val TOO_MUCH_FROM_ADAPTER = "Adapter sent too much"

class ObdException(message: String) : Exception(message)
