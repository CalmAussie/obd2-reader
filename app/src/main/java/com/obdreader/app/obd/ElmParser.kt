package com.obdreader.app.obd

import java.util.Locale

/**
 * Pure parsing helpers for ELM327 responses. Assumes the adapter has been
 * configured with ATE0 (no echo), ATS0 (no spaces) and ATH0 (no headers),
 * but tolerates spaces and echo in case a clone ignores those settings.
 */
object ElmParser {

    private val ERRORS = listOf(
        "NO DATA", "UNABLE TO CONNECT", "CAN ERROR", "BUS ERROR", "BUS BUSY",
        "DATA ERROR", "STOPPED", "BUFFER FULL", "FB ERROR", "LV RESET", "ACT ALERT", "ERROR",
    )

    /** "0:" .. "F:" prefixes on ISO-TP multi-frame lines. */
    private val FRAME = Regex("^[0-9A-Fa-f]:")

    fun cleanLines(raw: String, command: String): List<String> {
        val cmd = command.replace(" ", "").uppercase(Locale.US)
        return raw.replace(">", "")
            .split('\r', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.replace(" ", "").uppercase(Locale.US) == cmd }
            .filterNot {
                val u = it.uppercase(Locale.US)
                u.startsWith("SEARCHING") || u.startsWith("BUS INIT")
            }
    }

    fun errorIn(lines: List<String>): String? = lines.firstOrNull { line ->
        val u = line.trim().uppercase(Locale.US)
        u == "?" || ERRORS.any { u.contains(it) }
    }

    private fun hexOnly(line: String): String? {
        var s = line.replace(" ", "").uppercase(Locale.US)
        if (FRAME.containsMatchIn(s)) s = s.substring(2)
        return if (s.isNotEmpty() && s.all { it in '0'..'9' || it in 'A'..'F' }) s else null
    }

    /**
     * Groups response lines into payloads. A CAN multi-frame reply becomes one
     * joined payload; otherwise each line (e.g. one per ECU) is its own payload.
     */
    private fun payloads(lines: List<String>): List<String> {
        val compact = lines.map { it.replace(" ", "") }
        val framed = compact.filter { FRAME.containsMatchIn(it) }
        return if (framed.isNotEmpty()) {
            listOf(framed.mapNotNull { hexOnly(it) }.joinToString(""))
        } else {
            compact.mapNotNull { hexOnly(it) }
        }
    }

    private fun dataAfter(payload: String, header: String): String? {
        var idx = payload.indexOf(header)
        while (idx >= 0 && idx % 2 != 0) idx = payload.indexOf(header, idx + 1)
        return if (idx >= 0) payload.substring(idx + header.length) else null
    }

    fun toBytes(hex: String): IntArray =
        hex.chunked(2).filter { it.length == 2 }.map { it.toInt(16) }.toIntArray()

    /** Returns the data bytes following [header] (e.g. "410C"), or null if absent. */
    fun extractData(lines: List<String>, header: String): IntArray? {
        for (p in payloads(lines)) {
            dataAfter(p, header)?.let { return toBytes(it) }
        }
        return null
    }

    /**
     * Parses a reply to a multi-PID request such as "010C0D11" (CAN cars answer up to six PIDs
     * in one message). [lengths] gives each PID's data length in bytes, so the reply can be split
     * into "PID, data" pairs. Returns the data bytes for every PID found.
     */
    fun parseMultiPid(lines: List<String>, lengths: Map<Int, Int>): Map<Int, IntArray> {
        val result = mutableMapOf<Int, IntArray>()
        for (p in payloads(lines)) {
            val bytes = toBytes(dataAfter(p, "41") ?: continue)
            var i = 0
            while (i < bytes.size) {
                val pid = bytes[i]
                val len = lengths[pid] ?: break
                if (i + 1 + len > bytes.size) break
                result[pid] = bytes.copyOfRange(i + 1, i + 1 + len)
                i += 1 + len
            }
        }
        return result
    }

    /** Decodes a PID-support bitmask (reply to 0100 / 0120 / 0140...). */
    fun supportedFromBitmask(bytes: IntArray, base: Int): Set<Int> {
        val result = mutableSetOf<Int>()
        for (i in 0 until 32) {
            if (i / 8 >= bytes.size) break
            val bit = (bytes[i / 8] shr (7 - i % 8)) and 1
            if (bit == 1) result += base + i + 1
        }
        return result
    }

    /**
     * Parses a mode 03 / 07 / 0A reply. [header] is "43", "47" or "4A".
     * On CAN the first data byte is the number of codes.
     */
    fun parseDtcs(lines: List<String>, header: String, isCan: Boolean): List<String> {
        val codes = LinkedHashSet<String>()
        for (p in payloads(lines)) {
            var data = dataAfter(p, header) ?: continue
            var limit = Int.MAX_VALUE
            if (isCan && data.length >= 2) {
                limit = data.substring(0, 2).toInt(16)
                data = data.substring(2)
            }
            data.chunked(4)
                .filter { it.length == 4 }
                .take(limit)
                .filter { it != "0000" }
                .forEach { codes += decodeDtc(it) }
        }
        return codes.toList()
    }

    fun decodeDtc(hex: String): String {
        val v = hex.toInt(16)
        val letter = "PCBU"[(v shr 14) and 0x3]
        val digit = (v shr 12) and 0x3
        val rest = String.format(Locale.US, "%03X", v and 0xFFF)
        return "$letter$digit$rest"
    }

    /** Parses a mode 09 PID 02 reply into a 17-character VIN. */
    fun parseVin(lines: List<String>): String? {
        val sb = StringBuilder()
        for (p in payloads(lines)) {
            val data = dataAfter(p, "4902") ?: continue
            if (data.length >= 2) sb.append(data.substring(2)) // skip message-count / sequence byte
        }
        val chars = toBytes(sb.toString())
            .map { Char(it) }
            .filter { it in 'A'..'Z' || it in '0'..'9' }
            .joinToString("")
        return when {
            chars.length >= 17 -> chars.takeLast(17)
            chars.isNotEmpty() -> chars
            else -> null
        }
    }

    /** ATDPN returns e.g. "A6" or "6"; protocols 6-9 and A-C are CAN. */
    fun isCanProtocol(dpn: String): Boolean {
        val code = dpn.trim().uppercase(Locale.US).removePrefix("A")
        return code in setOf("6", "7", "8", "9", "A", "B", "C")
    }
}
