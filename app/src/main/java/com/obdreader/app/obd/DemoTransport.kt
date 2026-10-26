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
            cmd == "ATRV" -> String.format(Locale.US, "%.1fV", demoVoltage())
            cmd.startsWith("ATSH") -> { header = cmd.substring(4); "OK" }
            cmd == "ATH1" || cmd == "ATH0" -> { headers = cmd == "ATH1"; "OK" }
            cmd.startsWith("AT") -> "OK"
            // Readiness: check-engine light on with 2 codes; all monitors done except the evaporative system.
            cmd == "0101" -> "410182076504"
            cmd == "0904" -> "490401" + "1L8A-14C204-AAA".padEnd(16, '\u0000').map { String.format(Locale.US, "%02X", it.code) }.joinToString("")
            cmd.length == 4 && cmd.startsWith("06") -> mode06(cmd.substring(2).toInt(16))?.let { "46$it" } ?: "NO DATA"
            // Other modules: the ABS has a stored code, the airbag module has none, the rest don't answer.
            // With headers on (the module search), each answer comes as CAN frames from its address.
            cmd == "1902FF" -> moduleCodes(header)?.let { if (headers) frames(header, it) else it } ?: "NO DATA"
            cmd == "03" -> "430201330420"
            cmd == "07" -> "47010171"
            cmd == "0902" -> vinFrames(vin)
            // Freeze frame: stored with P0133 on a warm engine at a steady 64 km/h, a little lean.
            cmd.length == 6 && cmd.startsWith("02") && cmd.endsWith("00") -> freeze(cmd.substring(2, 4))?.let { "42${cmd.substring(2, 4)}00$it" } ?: "NO DATA"
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

    /**
     * The demo car starts like a real one: ignition on with the engine off for a few seconds
     * (battery resting at 12.6 V), a second of cranking (dipping to about 10.4 V), then running
     * and charging at about 14.1 V. That gives the battery check something to measure.
     */
    private fun phase(): Int = seconds().let { if (it < ENGINE_OFF_S) 0 else if (it < ENGINE_OFF_S + CRANK_S) 1 else 2 }

    private fun demoVoltage(): Double = when (phase()) {
        0 -> 12.6
        1 -> 10.4 + 0.3 * sin(seconds() * 9)
        else -> 14.1 + 0.2 * sin(seconds())
    }

    private fun pid(number: Int): String? {
        val ph = phase()
        // Engine off or cranking: every reading still answers (as a real car's does), at rest.
        if (ph < 2) return when (number) {
            0x0C -> two(if (ph == 0) 0 else 220 * 4)
            0x0D, 0x11, 0x04, 0x10, 0x0E -> if (number == 0x10) two(0) else if (number == 0x0E) one(64.0 * 2) else one(0.0)
            0x0B -> one(100.0)
            else -> pidRunning(number, 0.0)
        }
        return pidRunning(number, seconds() - ENGINE_OFF_S - CRANK_S)
    }

    private fun pidRunning(number: Int, tRun: Double): String? {
        val t = tRun.coerceAtLeast(0.0)
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

    private var header = "7DF"
    @Volatile private var headers = false

    /**
     * Other modules: the ABS has a stored code, the airbag module and the transmission have none,
     * and one module the car's make isn't known for (at 7A0) answers the search with none.
     */
    private fun moduleCodes(h: String): String? = when (h) {
        "760" -> "5902FF51451C0A"
        "737", "7E1", "7A0" -> "5902FF"
        "7E0", "7DF" -> "5902FF0133000801710004"
        else -> null
    }

    /** [payload] as the CAN frames an 11-bit module at [request] sends back, headers on and no spaces. */
    private fun frames(request: String, payload: String): String {
        val id = String.format(Locale.US, "%03X", (request.toIntOrNull(16) ?: 0x7DF).let { if (it == 0x7DF) 0x7E0 else it } + 8)
        val bytes = payload.chunked(2)
        if (bytes.size <= 7) return id + String.format(Locale.US, "%02X", bytes.size) + payload
        val out = mutableListOf(id + String.format(Locale.US, "1%03X", bytes.size) + bytes.take(6).joinToString(""))
        bytes.drop(6).chunked(7).forEachIndexed { i, part -> out += id + String.format(Locale.US, "2%X", (i + 1) % 16) + part.joinToString("") }
        return out.joinToString("\r")
    }

    /** Self-test results: two oxygen sensor tests, the catalyst, and misfire counts for four cylinders. */
    private fun mode06(mid: Int): String? = when (mid) {
        0x00 -> "00C0000001"
        0x20 -> "2080000001"
        0x40, 0x60, 0x80 -> String.format(Locale.US, "%02X00000001", mid)
        0xA0 -> "A078000000"
        0x01 -> "01010B01C400C80384" + "01020B004B0000012C"
        0x02 -> "02010B02260190044C"
        0x21 -> "21012400600000012C"
        in 0xA2..0xA5 -> String.format(Locale.US, "%02X0B24%04X000000C8", mid, if (mid == 0xA3) 3 else 0)
        else -> null
    }

    private fun freeze(pid: String): String? = when (pid) {
        "02" -> "0133"
        "03" -> "0200"
        "0C" -> two(2140 * 4)
        "0D" -> one(64.0)
        "05" -> one(88.0 + 40)
        "04" -> one(pct(41.0))
        "06" -> one(128 + 9.4 * 128 / 100)
        "07" -> one(128 + 12.5 * 128 / 100)
        "0B" -> one(46.0)
        "0F" -> one(29.0 + 40)
        "11" -> one(pct(18.0))
        "10" -> two(1210)
        else -> null
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

private const val ENGINE_OFF_S = 4.0
private const val CRANK_S = 1.2
