package com.obdreader.app.obd

import java.util.Locale
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A simulated ELM327 + car, so the whole app can be tried without hardware.
 * Speaks the same text protocol as a real adapter (CAN, ATS0/ATH0 style).
 */
class DemoTransport(
    /** The VIN the demo car reports: made from the user's chosen car, so the demo matches it. */
    private val vin: String = "1FAHP3F27CL123456",
) : ObdTransport {
    override val name = "Demo vehicle"

    private val start = System.currentTimeMillis()
    @Volatile private var open = false

    override fun open() {
        Thread.sleep(400)
        open = true
    }

    override fun close() {
        open = false
    }

    override fun exchange(command: String, timeoutMs: Long): String {
        if (!open) throw java.io.IOException("Demo disconnected")
        Thread.sleep(25)
        val cmd = command.uppercase(Locale.US).replace(" ", "")
        val body = when {
            cmd == "ATZ" -> "\r\rELM327 v1.5"
            cmd == "ATI" -> "ELM327 v1.5"
            cmd == "ATDPN" -> "A6"
            cmd == "ATDP" -> "AUTO, ISO 15765-4 (CAN 11/500)"
            cmd == "ATRV" -> String.format(Locale.US, "%.1fV", 14.1 + 0.2 * sin(seconds()))
            cmd.startsWith("AT") -> "OK"
            cmd == "03" -> "430201330420"
            cmd == "07" -> "47010171"
            cmd == "0902" -> vinFrames(vin)
            cmd.length == 4 && cmd.startsWith("01") -> pid(cmd.substring(2).toInt(16))?.let { "41${cmd.substring(2)}$it" } ?: "NO DATA"
            // Several PIDs in one request, as CAN cars allow.
            cmd.length > 4 && cmd.startsWith("01") && cmd.length % 2 == 0 -> {
                val parts = cmd.substring(2).chunked(2).mapNotNull { p -> pid(p.toInt(16))?.let { p + it } }
                if (parts.isEmpty()) "NO DATA" else "41" + parts.joinToString("")
            }
            else -> "NO DATA"
        }
        return "$body\r\r"
    }

    private fun seconds() = (System.currentTimeMillis() - start) / 1000.0

    private fun pid(number: Int): String? {
        val t = seconds()
        val drive = (0.5 + 0.5 * sin(t / 2.5)).pow(2)   // 0..1 "accelerator"
        val rpm = 850 + 2400 * drive
        return when (number) {
            0x00, 0x20 -> "FFFFFFFF"
            0x40 -> "FFFFFFFE"
            0x0C -> two((rpm * 4).toInt())
            0x0D -> one(drive * 95)
            0x11 -> one(pct(12 + drive * 70))
            0x04 -> one(pct(18 + drive * 65))
            0x05 -> one(min(90.0, 45 + t * 1.5) + 40)
            0x0F -> one(31.0 + 40)
            0x0B -> one(32 + drive * 60)
            0x10 -> two(((2.5 + drive * 30) * 100).toInt())
            0x0E -> one((12 + drive * 18 + 64) * 2)
            0x2F -> one(pct(62.0))
            0x46 -> one(24.0 + 40)
            0x5C -> one(min(102.0, 40 + t * 1.2) + 40)
            else -> null
        }
    }

    private fun pct(p: Double) = p * 255 / 100
    private fun one(v: Double) = String.format(Locale.US, "%02X", v.toInt().coerceIn(0, 255))
    private fun two(v: Int) = String.format(Locale.US, "%04X", v.coerceIn(0, 0xFFFF))

    private fun vinFrames(vin: String): String {
        val hex = "490201" + vin.map { String.format(Locale.US, "%02X", it.code) }.joinToString("")
        val lines = mutableListOf(String.format(Locale.US, "%03X", hex.length / 2), "0:" + hex.substring(0, 12))
        var i = 12
        var n = 1
        while (i < hex.length) {
            val end = min(i + 14, hex.length)
            lines += "${Integer.toHexString(n % 16).uppercase(Locale.US)}:" + hex.substring(i, end).padEnd(14, '0')
            i = end
            n++
        }
        return lines.joinToString("\r")
    }
}
