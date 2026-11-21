package com.obdreader.app.obd

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Shared request/response logic for adapters that expose a plain byte stream
 * (Bluetooth Classic RFCOMM and Wi-Fi TCP).
 */
abstract class StreamTransport : ObdTransport {

    @Volatile protected var input: InputStream? = null
    @Volatile protected var output: OutputStream? = null

    /** How long the next command waits for a timed-out command's late reply (see [LATE_REPLY_WAIT_MS]). */
    protected open val lateReplyWaitMs: Long = LATE_REPLY_WAIT_MS

    /** Set when a command timed out: its reply may still be on the way. */
    @Volatile private var lateReplyOwed = false

    override fun exchange(command: String, timeoutMs: Long): String {
        val inp = input ?: throw IOException("Not connected")
        val out = output ?: throw IOException("Not connected")

        if (lateReplyOwed) {
            lateReplyOwed = false
            skipLateReply(inp)
        }
        // Discard anything else left over from a previous, timed-out command.
        while (inp.available() > 0) inp.read()

        out.write((command + "\r").toByteArray(Charsets.US_ASCII))
        out.flush()

        val response = StringBuilder()
        val buffer = ByteArray(256)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (input == null) throw IOException("Disconnected")
            if (inp.available() > 0) {
                val n = inp.read(buffer)
                if (n < 0) throw IOException("Adapter closed the connection")
                for (i in 0 until n) {
                    val c = (buffer[i].toInt() and 0xFF).toChar()
                    if (c == '>') return response.toString()
                    response.append(c)
                }
            } else {
                Thread.sleep(8)
            }
        }
        lateReplyOwed = true
        throw AdapterTimeoutException("No reply to '$command'")
    }

    /** Reads and drops a late reply, up to and including its prompt, waiting at most [lateReplyWaitMs]. */
    private fun skipLateReply(inp: InputStream) {
        val deadline = System.currentTimeMillis() + lateReplyWaitMs
        while (System.currentTimeMillis() < deadline) {
            if (input == null) throw IOException("Disconnected")
            if (inp.available() > 0) {
                val c = inp.read()
                if (c < 0) throw IOException("Adapter closed the connection")
                if (c == '>'.code) return
            } else {
                Thread.sleep(8)
            }
        }
    }
}
