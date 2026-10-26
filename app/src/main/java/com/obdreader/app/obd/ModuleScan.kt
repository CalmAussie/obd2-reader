package com.obdreader.app.obd

import java.util.Locale

/**
 * Trouble codes from the car's other computers (ABS, airbags, transmission, body and so on),
 * read with the standard "read trouble codes" services: UDS ReadDTCInformation (service 19) or,
 * on older modules, KWP2000 readDTCByStatus (service 18). Both only read; clearing (service 14)
 * is never sent. Each module is reached by its own CAN address, so this works on CAN cars, and
 * only for modules on the same bus as the OBD2 port's CAN pins. Some modules sit on another bus
 * an ELM327 adapter can't reach, and those simply don't answer.
 */
object ModuleScan {

    /** One module: its name, the CAN address to ask, and the address it answers from. */
    data class Module(val name: String, val request: String, val reply: String)

    data class Code(val code: String, val confirmed: Boolean, val pending: Boolean, val subtype: Int?) {
        /** "C1145", or "C1145-1C" with Ford's failure type when there is one. */
        val label: String get() = if (subtype == null || subtype == 0) code else String.format(Locale.US, "%s-%02X", code, subtype)
    }

    sealed class Answer {
        data class Codes(val codes: List<Code>) : Answer()
        /** The module answered but said no to the request (it doesn't support that service). */
        object Refused : Answer()
        /** Nothing came back: not fitted, or on a bus the adapter can't reach. */
        object Silent : Answer()
    }

    private fun m(name: String, req: Int) = Module(name, String.format(Locale.US, "%03X", req), String.format(Locale.US, "%03X", req + 8))

    /**
     * The modules to ask. Ford (and the Mazdas Ford built, like the Tribute) and other Mazdas use
     * the same addresses for their main modules; other makes get the engine and transmission
     * computers on the standard addresses, the ones every CAN car has.
     */
    fun modulesFor(make: String?, model: String?): List<Module> {
        val mk = make?.substringBefore(" (")?.lowercase(Locale.US)
        val fordFamily = mk in setOf("ford", "lincoln", "mercury", "mazda")
        val standard = listOf(m("Engine computer (PCM)", 0x7E0), m("Transmission (TCM)", 0x7E1))
        if (!fordFamily) return standard
        return standard + listOf(
            m("ABS and stability control", 0x760),
            m("Airbags (restraints)", 0x737),
            m("Body / general electronics", 0x726),
            m("Instrument cluster", 0x720),
            m("Power steering", 0x730),
            m("Parking sensors", 0x736),
            m("All-wheel drive", 0x703),
            m("Climate control", 0x733),
        )
    }

    /** Requests, in the order to try them: UDS first, then KWP2000. */
    const val UDS_REQUEST = "1902FF"
    const val KWP_REQUEST = "1800FF00"

    /** A code from the two bytes every OBD-style code is packed into: "P0133", "C1145", "B1342", "U0100". */
    fun decode(hi: Int, lo: Int): String = ElmParser.decodeDtc(String.format(Locale.US, "%02X%02X", hi, lo))

    /**
     * Reads a module's reply to [UDS_REQUEST]. Records are four bytes (two-byte code, failure type,
     * status). Status bit 3: confirmed (stored); bit 2: pending.
     */
    fun parseUds(lines: List<String>): Answer {
        if (lines.isEmpty() || ElmParser.errorIn(lines) != null) return Answer.Silent
        if (ElmParser.extractData(lines, "7F19") != null) return Answer.Refused
        val data = ElmParser.extractData(lines, "5902") ?: return Answer.Silent
        val records = data.drop(1).chunked(4).filter { it.size == 4 } // the first byte is the status mask the module supports
        val codes = records.filter { it[3] and 0x0C != 0 || it[3] and 0x01 != 0 }.map {
            Code(decode(it[0], it[1]), confirmed = it[3] and 0x08 != 0, pending = it[3] and 0x04 != 0, subtype = it[2])
        }
        return Answer.Codes(codes.distinctBy { it.label })
    }

    /** Reads a module's reply to [KWP_REQUEST]: a count, then three bytes per code (code, status). */
    fun parseKwp(lines: List<String>): Answer {
        if (lines.isEmpty() || ElmParser.errorIn(lines) != null) return Answer.Silent
        if (ElmParser.extractData(lines, "7F18") != null) return Answer.Refused
        val data = ElmParser.extractData(lines, "58") ?: return Answer.Silent
        val records = data.drop(1).chunked(3).filter { it.size == 3 && !(it[0] == 0 && it[1] == 0) }
        // KWP2000 status bytes vary by maker, so every code it lists is shown as stored.
        return Answer.Codes(records.map { Code(decode(it[0], it[1]), confirmed = true, pending = false, subtype = null) }.distinctBy { it.label })
    }

    /** The adapter commands that point it at [m] and back again afterwards. */
    fun pointAt(m: Module): List<String> = listOf("ATSH ${m.request}", "ATCRA ${m.reply}")
    fun pointBack(defaultHeader: String?): List<String> = listOfNotNull("ATCRA", defaultHeader?.let { "ATSH $it" })
}
