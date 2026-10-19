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

    override fun exchange(command: String, timeoutMs: Long): String {
        val inp = input ?: throw IOException("Not connected")
        val out = output ?: throw IOException("Not connected")

        // Discard anything left over from a previous, timed-out command.
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
        throw AdapterTimeoutException("No reply to '$command'")
    }
}
