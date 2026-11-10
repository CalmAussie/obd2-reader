package com.obdreader.app.obd

import java.util.Locale

/**
 * Volvo's "keyword D3 B0" diagnostics on the 1996–2000 P80 cars (850, S70, V70, C70, XC70),
 * spoken on the OBD2 port's K-line (ISO 9141, 10400 baud). Each module is woken with a 5-baud
 * init on its own address, then asked over a 3-byte header (83 <module> 13, from the tester at
 * 13). The ELM327 keeps the session alive with Volvo's tester-present message (82 <module> 13 A1).
 *
 * Only reads are ever sent: AE 01 (the module's stored codes) and B9 03 (the instrument
 * cluster's mileage). Clearing (AF), service-light resets (B0, B2), actuator tests and every
 * other request are refused by [ReadOnlyGuard]. The airbag module (SRS, 58) is never asked.
 *
 * Addresses, requests and code meanings come from Richard H. Jones's Volvo 850 OBDII work
 * (jonesrh.info, public domain).
 */
object VolvoD3B0 {

    /** A module: its name, its address, and the address its 5-baud init goes to (some set the parity bit). */
    data class Module(val name: String, val address: String, val init: String = address)

    val COMBI = Module("Instrument cluster (COMBI)", "51")

    /** Every module these cars can have, except the airbag module. */
    val MODULES = listOf(
        Module("Engine (Motronic 4.4)", "7A"),
        Module("Engine (MSA 15.7 diesel)", "11", "91"),
        Module("ABS", "01"),
        Module("Automatic transmission (AW 50-42)", "6E"),
        COMBI,
        Module("Immobiliser", "41", "C1"),
        Module("Climate control (ECC)", "29"),
        Module("Alarm (VGLA)", "2D", "AD"),
        Module("Power seat, left", "2E", "AE"),
        Module("Power seat, right", "2F"),
        Module("Auxiliary heater (912-D)", "18", "98"),
    )

    /** The airbag module's address: never asked. */
    const val AIRBAGS = "58"

    const val READ_CODES = "AE01"
    const val READ_MILEAGE = "B903"

    /** The only Volvo requests that may be sent: both only read. */
    val ALLOWED_REQUESTS = setOf(READ_CODES, READ_MILEAGE)

    /** The tester-present message the ELM327 sends every few seconds to keep a module awake. */
    fun keepAlive(address: String) = "ATWM 82 $address 13 A1"

    /** True for the cars that speak D3 B0: P80 Volvos from 1996 to 2000 (C70 to 2005). */
    fun appliesTo(make: String?, model: String?, year: Int?): Boolean {
        if (make?.trim()?.equals("Volvo", ignoreCase = true) != true || year == null) return false
        val m = model?.trim()?.uppercase(Locale.US) ?: return false
        return when {
            m == "850" -> year >= 1996
            m == "S70" -> year in 1997..2000
            m == "C70" -> year in 1997..2005
            m == "V70" || m.startsWith("V70 ") || m == "XC70" -> year in 1997..2000
            else -> false
        }
    }

    /**
     * The adapter commands that point it at [m] (the K-line, the module's init address, its
     * keep-alive and header) and back to ordinary OBD2 afterwards. Headers are on while a module
     * is asked, so the answer's sender can be checked.
     */
    fun pointAt(m: Module): List<String> = listOf(
        "ATPC", "ATSP 3", "ATH1", "ATAL", "ATKW0", "ATSR 13", "ATST FF", "ATIIA ${m.init}", keepAlive(m.address), "ATSH 83 ${m.address} 13",
    )
    val POINT_BACK = listOf("ATPC", "ATIIA 33", "ATWM 68 6A F1 01 00", "ATSH 68 6A F1", "ATAR", "ATKW1", "ATNL", "ATST 32", "ATH0")

    /** One message from a module: who sent it and its data (without header and checksum). */
    data class Message(val from: String, val data: List<Int>)

    /**
     * Messages in [lines] (headers on, with or without spaces). Each is a length byte (low six bits:
     * bytes after the 3-byte header, checksum included), the tester (13), the sender, then data.
     * The ELM327 sometimes runs several messages together on one line; they're split by length.
     */
    fun messages(lines: List<String>): List<Message> {
        val out = mutableListOf<Message>()
        for (raw in lines) {
            val hex = raw.uppercase(Locale.US).substringBefore("<").replace(" ", "")
            if (hex.length < 8 || hex.length % 2 != 0 || !hex.all { it in '0'..'9' || it in 'A'..'F' }) continue
            val b = ElmParser.toBytes(hex)
            var i = 0
            while (i + 3 < b.size) {
                val len = b[i] and 0x3F
                if (b[i + 1] != 0x13 || len < 2 || i + 3 + len > b.size) break
                out += Message(String.format(Locale.US, "%02X", b[i + 2]), (i + 3 until i + 3 + len - 1).map { b[it] })
                i += 3 + len
            }
        }
        return out
    }

    /** A module's answer to [READ_CODES]: its raw one-byte codes, or Refused (7F AE) / Silent. */
    fun parseCodes(lines: List<String>, address: String): ModuleScan.Answer {
        val mine = messages(lines).filter { it.from == address }
        val positive = mine.filter { it.data.size >= 2 && it.data[0] == 0xEE && it.data[1] == 0x01 }
        if (positive.isNotEmpty()) {
            val raws = positive.flatMap { it.data.drop(2) }.distinct()
            return ModuleScan.Answer.Codes(raws.map { codeFor(address, it) })
        }
        return if (mine.any { it.data.size >= 2 && it.data[0] == 0x7F && it.data[1] == 0xAE }) ModuleScan.Answer.Refused else ModuleScan.Answer.Silent
    }

    /**
     * The instrument cluster's mileage, in km, from its answer to [READ_MILEAGE] (F9 03, then a
     * little-endian count of 10-mile steps), or null. Volvo's clusters count in 10-mile steps, so
     * this is the true distance to within 10 miles (16 km), whatever the dashboard odometer shows.
     */
    fun mileageKm(lines: List<String>): Double? {
        val m = messages(lines).firstOrNull { it.from == COMBI.address && it.data.size >= 4 && it.data[0] == 0xF9 && it.data[1] == 0x03 } ?: return null
        val steps = m.data[2] or (m.data[3] shl 8)
        return steps * 10 * 1.609344
    }

    /** Volvo's own code and meaning for a module's raw code, from the table. */
    private val table: Map<Pair<String, Int>, Pair<String, String>> by lazy {
        val text = VolvoD3B0::class.java.getResourceAsStream("volvo_d3b0_dtcs.tsv")?.bufferedReader()?.readText().orEmpty()
        text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 4) null else (f[0] to f[1].toInt(16)) to (f[2] to f[3])
        }.toMap()
    }

    fun codeFor(address: String, raw: Int): ModuleScan.Code {
        val hex = String.format(Locale.US, "%02X", raw)
        val known = table[address to raw]
        return ModuleScan.Code(
            code = known?.first ?: "$address-$hex", confirmed = true, pending = false, subtype = null,
            text = known?.second ?: "Code $hex from this module (its meaning isn't known yet)",
        )
    }
}
