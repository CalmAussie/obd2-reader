package com.obdreader.app.obd

import java.util.Locale
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A simulated ELM327 + car, so the whole app can be tried without hardware.
 * Speaks the same text protocol as a real adapter (CAN, ATS0/ATH0 style).
 */
/**
 * Debug builds only (set by the install test): a pending code the demo car starts reporting mid-drive,
 * so the new-code notification can be checked. Always null in normal use.
 */
object DemoTestHooks {
    @Volatile var newPendingCode: String? = null

    /** A speed in km/h the demo car holds, so three-digit speeds can be checked in every style. */
    @Volatile var fixedSpeed: Int? = null
}

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
            cmd == "ATDPN" -> if (volvoP80) "A3" else "A6"
            cmd == "ATDP" -> if (volvoP80) "AUTO, ISO 9141-2" else "AUTO, ISO 15765-4 (CAN 11/500)"
            cmd.startsWith("ATIIA") -> { initAddress = cmd.substring(5); "OK" }
            // A 1996–2000 Volvo answers keyword D3 B0 on the K-line: the instrument cluster has
            // CI-221 stored and 123,400 miles; the ABS and transmission have no codes.
            volvoP80 && cmd == "AE01" -> when (initAddress) {
                "51" -> "841351EE0102D9"
                "01" -> "831301EE01FF"
                "6E" -> "83136EEE01FF"
                else -> "NO DATA"
            }
            volvoP80 && cmd == "B903" -> if (initAddress == "51") "851351F903343049" else "NO DATA"
            cmd == "ATRV" -> String.format(Locale.US, "%.1fV", demoVoltage())
            cmd.startsWith("ATSH") -> { header = cmd.substring(4); "OK" }
            cmd == "ATH1" || cmd == "ATH0" -> { headers = cmd == "ATH1"; "OK" }
            cmd.startsWith("AT") -> "OK"
            // An STN adapter (like an OBDLink), so the second-network search can be tried.
            cmd == "STI" -> "STN2120 v5.6.19"
            cmd == "STPR" -> if (msCan) "53" else "31"
            cmd.startsWith("STP") -> { msCan = cmd == "STP53"; "OK" }
            // Readiness: check-engine light on with 2 codes; all monitors done except the evaporative system.
            cmd == "0101" -> "410182076504"
            cmd == "0904" -> "490401" + "1L8A-14C204-AAA".padEnd(16, '\u0000').map { String.format(Locale.US, "%02X", it.code) }.joinToString("")
            cmd.length == 4 && cmd.startsWith("06") -> mode06(cmd.substring(2).toInt(16))?.let { "46$it" } ?: "NO DATA"
            // Other modules: the ABS has a stored code, the transmission has none, the rest don't answer.
            // With headers on (the module search), each answer comes as CAN frames from its address.
            cmd == "1902FF" -> moduleCodes(header)?.let { if (headers) frames(header, it) else it } ?: "NO DATA"
            // Enhanced readings and module information, as a Ford's modules answer them.
            cmd.startsWith("22") && cmd.length == 6 -> enhanced(header, cmd.substring(2))?.let { "62${cmd.substring(2)}$it" } ?: "NO DATA"
            cmd == "090A" -> "490A01" + "ECM-EngineControl".padEnd(20, '\u0000').map { String.format(Locale.US, "%02X", it.code) }.joinToString("")
            cmd == "03" -> "430201330420"
            cmd == "07" -> DemoTestHooks.newPendingCode?.let { "470201710" + it.removePrefix("P0").padStart(3, '0') } ?: "47010171"
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
            0x33 -> one(100.0)
            else -> pidRunning(number, 0.0)
        }
        return pidRunning(number, seconds() - ENGINE_OFF_S - CRANK_S)
    }

    private fun pidRunning(number: Int, tRun: Double): String? {
        val t = tRun.coerceAtLeast(0.0)
        val drive = (0.5 + 0.5 * sin(t / 2.5)).pow(2)   // 0..1 "accelerator"
        val rpm = 850 + 2400 * drive
        return when (number) {
            // What a 4-cylinder petrol turbo with direct injection lists: bank 1 fuel trims, a wide-band
            // first oxygen sensor and a narrow-band second one, fuel rail pressure, two catalyst
            // temperatures, the since-cleared counters and the usual dashboard readings.
            0x00 -> "BE3FA813"
            0x20 -> "B007E015"
            0x40 -> "FEDCC014"
            0x0C -> two((rpm * 4).toInt())
            0x0D -> one(DemoTestHooks.fixedSpeed?.toDouble() ?: (drive * 95))
            0x11 -> one(pct(12 + drive * 70))
            0x04 -> one(pct(18 + drive * 65))
            0x05 -> one(min(90.0, 45 + t * 1.5) + 40)
            0x0F -> one(31.0 + 40)
            // A turbocharged engine: vacuum at idle, about 0.6 bar of boost flat out.
            0x0B -> one(32 + drive * 130)
            0x33 -> one(100.0)
            0x10 -> two(((2.5 + drive * 30) * 100).toInt())
            0x0E -> one((12 + drive * 18 + 64) * 2)
            0x2F -> one(pct(62.0))
            0x46 -> one(24.0 + 40)
            0x5C -> one(min(102.0, 40 + t * 1.2) + 40)
            else -> more(number, t, drive)
        }
    }

    /**
     * The demo's other standard readings: a petrol direct-injection turbo, warmed up and in closed
     * loop after a few seconds, trims a little positive, a lively first oxygen sensor and a steady
     * second one. Its counters say the codes were cleared 18 km and 3 warm-ups ago (and two came back).
     */
    private fun more(number: Int, t: Double, drive: Double): String? {
        val warm = t > 15
        fun trim(p: Double) = one(128 + p * 1.28)
        fun lambda(l: Double) = two((l * 32768).toInt()) + two((l * 0.8 * 8192).toInt())
        // A first oxygen sensor switches rich and lean about once a second; the second stays steady.
        val flick = 0.5 + 0.4 * sin(t * 6.0)
        return when (number) {
            0x03 -> if (warm) "0200" else "0100"
            0x06 -> trim(2.0 + 3.0 * sin(t * 1.7))
            0x07 -> trim(4.7)
            0x08 -> trim(1.5 + 3.0 * sin(t * 1.5 + 1))
            0x09 -> trim(3.9)
            0x0A -> one(380 / 3.0)
            in 0x14..0x1B -> one((if ((number - 0x14) % 4 == 0) flick else 0.66) * 200) + "80"
            0x1F -> two(t.toInt())
            0x21 -> two(6)
            0x22 -> two((300 / 0.079).toInt())
            0x23, 0x59 -> two(((5_000 + drive * 10_000) / 10).toInt())
            in 0x24..0x2B -> lambda(if ((number - 0x24) % 4 == 0) 1.0 + 0.02 * sin(t * 6.0) else 1.0)
            0x2C -> one(pct(8 * drive))
            0x2D -> trim(1.0 * sin(t))
            0x30 -> one(3.0)
            0x31 -> two(18)
            0x32 -> two(0x10000 - 200) // -50 Pa
            in 0x3C..0x3F -> two(((min(620.0, 300 + t * 6) + (number - 0x3C) * -40 + 40) * 10).toInt())
            0x42 -> two((demoVoltage() * 1000).toInt())
            0x4D -> two(9)
            0x4E -> two(41)
            0x51 -> "01"
            0x52 -> one(pct(10.0))
            0x5E -> two(((0.8 + drive * 12) * 20).toInt())
            else -> null
        }
    }

    private var header = "7DF"
    /** A 1996–2000 Volvo (VIN from Volvo, model year 1996–2000): talks ISO 9141 and keyword D3 B0. */
    private val volvoP80 = vin.startsWith("YV") && vin.getOrNull(9) in setOf('T', 'V', 'W', 'X', 'Y')
    @Volatile private var initAddress = "33"
    /** On the second network (MS-CAN): only the cluster and body module answer there. */
    @Volatile private var msCan = false
    @Volatile private var headers = false

    /**
     * Other modules: the ABS has a stored code, the transmission has none,
     * and one module the car's make isn't known for (at 7A0) answers the search with none. On the
     * second network, the instrument cluster has a stored code and the body module none.
     */
    private fun moduleCodes(h: String): String? = if (msCan) when (h) {
        "720" -> "5902FF93420008" // the instrument cluster: B1342 stored
        "726" -> "5902FF"
        else -> null
    } else when (h) {
        "760" -> "5902FF51451C0A"
        "7E1", "7A0" -> "5902FF"
        "7E0", "7DF" -> "5902FF0133000801710004"
        else -> null
    }

    private fun text(t: String) = t.map { String.format(Locale.US, "%02X", it.code) }.joinToString("")

    /** The demo's enhanced readings (a warm transmission, 65% oil life, a charged battery) and module identifiers. */
    private fun enhanced(h: String, did: String): String? {
        val engine = h == "7E0" || h == "7DF"
        return when {
            engine && did == "1E1C" -> two(((70 + min(12.0, seconds() / 20)) * 16).toInt())
            engine && did == "054B" -> "41"
            h == "726" && did == "DD01" -> "01E240"
            h == "726" && did == "4028" -> "55"
            h == "726" && did == "4029" -> "41"
            did == "F190" && (engine || h == "760" || h == "726") -> text(vin)
            engine && did == "F197" -> text("PCM")
            engine && did == "F187" -> text("1L8A-12A650-AAB")
            engine && did == "F189" -> text("1L8A-14C204-AAA")
            h == "760" && did == "F197" -> text("ABS")
            h == "760" && did == "F187" -> text("1L8T-2C219-AB")
            h == "760" && did == "F18A" -> text("TRW")
            else -> null
        }
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
