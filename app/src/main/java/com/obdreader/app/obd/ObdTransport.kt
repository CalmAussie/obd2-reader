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

class ObdException(message: String) : Exception(message)
