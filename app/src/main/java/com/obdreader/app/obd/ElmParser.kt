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
    /** Like [extractData], but every module's answer, for requests several modules reply to (such as 0100). */
    fun extractAll(lines: List<String>, header: String): List<IntArray> =
        payloads(lines).mapNotNull { p -> dataAfter(p, header)?.let { toBytes(it) } }

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
        val candidates = vinMessages(lines).map { vinChars(it) }.filter { it.isNotEmpty() }
        // Prefer a full VIN that passes its check digit, then any full VIN, then whatever came back.
        return candidates.firstOrNull { it.length == 17 && VinDecoder.isValid(it) }
            ?: candidates.firstOrNull { it.length == 17 }
            ?: candidates.maxByOrNull { it.length }
    }

    /**
     * Calibration IDs (service 09 PID 04): the engine computer's software versions, up to 16
     * characters each, one or more per module. Empty when the car doesn't report them.
     */
    fun parseCalibrationIds(lines: List<String>): List<String> =
        vinMessages(lines, "4904").flatMap { bytes ->
            bytes.toList().chunked(16).map { chunk -> chunk.map { Char(it) }.filter { it.code in 0x21..0x7E }.joinToString("") }
        }.filter { it.isNotEmpty() }.distinct()

    /** VIN characters only. I, O and Q are never used in a VIN, so they mark padding or noise. */
    private fun vinChars(bytes: IntArray): String {
        val s = bytes.map { Char(it) }.filter { (it in 'A'..'Z' || it in '0'..'9') && it !in "IOQ" }.joinToString("")
        return if (s.length > 17) s.takeLast(17) else s
    }

    /**
     * The VIN bytes from each module that answered 0902. CAN replies come as numbered frames
     * ("0:", "1:", …); a new "0:" means a second module is answering, so it starts a new message.
     * Older protocols send one line per part with a part number; the parts are put back in order
     * and a repeated part number means a second module.
     */
    private fun vinMessages(lines: List<String>, header: String = "4902"): List<IntArray> {
        val compact = lines.map { it.replace(" ", "").uppercase(Locale.US) }
        if (compact.any { FRAME.containsMatchIn(it) }) {
            val messages = mutableListOf<StringBuilder>()
            for (line in compact) {
                if (!FRAME.containsMatchIn(line)) continue
                val hex = hexOnly(line) ?: continue
                if (line[0] == '0' || messages.isEmpty()) messages.add(StringBuilder())
                messages.last().append(hex)
            }
            return messages.mapNotNull { m -> dataAfter(m.toString(), header)?.let { toBytes(it).drop(1).toIntArray() } }
        }
        val parts = compact.mapNotNull { hexOnly(it) }.mapNotNull { p ->
            val data = dataAfter(p, header) ?: return@mapNotNull null
            val b = toBytes(data)
            if (b.isEmpty()) null else b[0] to b.drop(1)
        }
        if (parts.isEmpty()) return emptyList()
        // Single-line replies (one line holding the whole VIN) have no part numbers to sort by.
        if (parts.size == 1) return listOf(parts[0].second.toIntArray())
        val modules = mutableListOf<MutableMap<Int, List<Int>>>()
        for ((seq, bytes) in parts) {
            val m = modules.firstOrNull { seq !in it } ?: mutableMapOf<Int, List<Int>>().also { modules.add(it) }
            m[seq] = bytes
        }
        return modules.map { m -> m.toSortedMap().values.flatten().toIntArray() }
    }

    /** ATDPN returns e.g. "A6" or "6"; protocols 6-9 and A-C are CAN. */
    fun isCanProtocol(dpn: String): Boolean {
        val code = dpn.trim().uppercase(Locale.US).removePrefix("A")
        return code in setOf("6", "7", "8", "9", "A", "B", "C")
    }
}
