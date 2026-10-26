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

    /**
     * One module: its name, the CAN address to ask, and the address it answers from. 11-bit
     * addresses are 3 hex digits ("7B0"); 29-bit ones are 8 ("18DA28F1").
     */
    data class Module(val name: String, val request: String, val reply: String) {
        val is29Bit: Boolean get() = request.length == 8
    }

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

    /**
     * Groups of makes that share their module addresses, and how their modules answer: most add 8
     * to the address they're asked on (7B0 answers from 7B8), Nissan adds 0x20, the Volkswagen
     * group 0x6A, GM 0x400, and Chrysler's group answers 0x280 lower. Honda's modules use 29-bit
     * addresses. The addresses are the ones these makers' modules are known to use; a car may have
     * only some of them, and "Search every address" finds any others.
     */
    enum class Family(val makes: Set<String>, val offset: Int) {
        FORD(setOf("ford", "lincoln", "mercury", "mazda"), 8),
        TOYOTA(setOf("toyota", "lexus", "scion"), 8),
        HYUNDAI(setOf("hyundai", "kia", "genesis"), 8),
        NISSAN(setOf("nissan", "infiniti"), 0x20),
        SUBARU(setOf("subaru"), 8),
        VW(setOf("volkswagen", "audi", "skoda", "seat / cupra", "seat", "cupra"), 0x6A),
        CHRYSLER(setOf("chrysler", "dodge", "jeep", "ram"), -0x280),
        GM(setOf("chevrolet", "gmc", "buick", "cadillac", "holden", "pontiac", "saturn", "hummer", "oldsmobile"), 0x400),
        HONDA(setOf("honda", "acura"), 0),
        MG(setOf("mg"), 8),
        OTHER(emptySet(), 8);

        companion object {
            fun of(make: String?): Family {
                val mk = make?.substringBefore(" (")?.trim()?.lowercase(Locale.US) ?: return OTHER
                return values().firstOrNull { mk in it.makes } ?: OTHER
            }
        }
    }

    private fun hex3(v: Int) = String.format(Locale.US, "%03X", v)
    private fun m(name: String, req: Int, offset: Int = 8) = Module(name, hex3(req), hex3(req + offset))
    /** A module on a 29-bit address: asked on 18DA<target>F1, answers from 18DAF1<target>. */
    private fun h(name: String, target: Int) =
        Module(name, String.format(Locale.US, "18DA%02XF1", target), String.format(Locale.US, "18DAF1%02X", target))

    /**
     * The engine and transmission computers on the standard OBD2 addresses every CAN car uses
     * (29-bit cars use the 29-bit ones). GM's transmission computer is the second address, 7E2.
     */
    private fun standard(family: Family, carIs29Bit: Boolean): List<Module> = when {
        carIs29Bit -> listOf(h("Engine computer (PCM)", 0x10), h("Transmission (TCM)", 0x18))
        family == Family.GM -> listOf(m("Engine computer (PCM)", 0x7E0), m("Transmission (TCM)", 0x7E2))
        else -> listOf(m("Engine computer (PCM)", 0x7E0), m("Transmission (TCM)", 0x7E1))
    }

    /**
     * The modules to ask for [make]: the engine and transmission computers, plus the other modules
     * that make's cars are known to have. [carIs29Bit] when the car talks OBD2 on 29-bit addresses.
     */
    fun modulesFor(make: String?, model: String?, carIs29Bit: Boolean = false): List<Module> {
        val f = Family.of(make)
        val o = f.offset
        val extra = when (f) {
            Family.FORD -> listOf(
                m("ABS and stability control", 0x760),
                m("Airbags (restraints)", 0x737),
                m("Body / general electronics", 0x726),
                m("Instrument cluster", 0x720),
                m("Power steering", 0x730),
                m("Parking sensors", 0x736),
                m("All-wheel drive", 0x703),
                m("Climate control", 0x733),
                m("Adaptive cruise radar", 0x764),
                m("Front camera (lane keeping)", 0x706),
            )
            Family.TOYOTA -> listOf(
                m("Hybrid system", 0x7E2),
                m("ABS and stability control", 0x7B0),
                m("Airbags (SRS)", 0x780),
                m("Power steering", 0x7A1),
                m("Climate control", 0x7C4),
                m("Radar cruise control", 0x791),
            )
            Family.HYUNDAI -> listOf(
                m("ABS and stability control", 0x7D1),
                m("Power steering (MDPS)", 0x7D4),
                m("Smart cruise radar", 0x7D0),
                m("Front camera (lane keeping)", 0x7C4),
                m("Climate control", 0x7B3),
                m("Instrument cluster", 0x7C6),
                m("Parking assist", 0x7B1),
            )
            Family.NISSAN -> listOf(
                m("ABS and stability control", 0x740, o),
                m("Power steering", 0x742, o),
                m("Instrument cluster", 0x743, o),
                m("Front camera (lane keeping)", 0x707, o),
            )
            Family.SUBARU -> listOf(
                m("ABS and stability control", 0x7B0),
                m("Power steering", 0x746),
                m("EyeSight camera", 0x787),
            )
            Family.VW -> listOf(
                m("ABS and stability control", 0x713, o),
                m("Airbags", 0x715, o),
                m("Power steering", 0x712, o),
                m("Adaptive cruise radar", 0x757, o),
                m("Front camera (lane assist)", 0x74F, o),
            )
            Family.CHRYSLER -> listOf(
                m("Hybrid system", 0x7E2),
                m("ABS and stability control", 0x747, o),
                m("Airbags (occupant restraints)", 0x744, o),
                m("Instrument cluster", 0x742, o),
                m("Power steering", 0x75A, o),
                m("Adaptive cruise radar", 0x753, o),
            )
            Family.GM -> listOf(m("Front camera (lane keeping)", 0x24B, o))
            Family.HONDA -> listOf(
                h("Stability control (VSA)", 0x28),
                h("Airbags (SRS)", 0x53),
                h("Power steering (EPS)", 0x30),
                h("Instrument cluster", 0x60),
                h("Gateway", 0xEF),
                h("Adaptive cruise radar", 0xB0),
                h("Front camera (lane keeping)", 0xB5),
                h("Electric gear selector", 0x0B),
                h("Electric brake booster", 0x2B),
            ) + if (carIs29Bit) emptyList() else listOf(h("Engine computer (PGM-FI)", 0x10), h("Transmission", 0x1E))
            Family.MG -> listOf(
                m("Power steering", 0x721),
                m("Front camera (lane keeping)", 0x733),
                m("Adaptive cruise radar", 0x734),
            )
            Family.OTHER -> emptyList()
        }
        return (standard(f, carIs29Bit) + extra).distinctBy { it.request }
    }

    /**
     * "Search every address": a block of module addresses to ask in turn, and the receive filter
     * (ATCF/ATCM) that lets through every answer from that block, whatever address it comes from.
     */
    data class Sweep(val requests: List<String>, val filter: String, val mask: String) {
        val is29Bit: Boolean get() = filter.length == 8
    }

    /**
     * The addresses to search for [make]. Most makers' modules sit at 700–7F7 and answer at
     * 708–7FF; GM's at 240–25F (answering at 640–65F); Chrysler's at 740–77F (answering at
     * 4C0–4FF). Honda's, and every module on a car that talks OBD2 on 29-bit addresses, are
     * 18DA00F1–18DAFFF1, answering from 18DAF1xx. The OBD2 broadcast address 7DF and the engine
     * computers' answer addresses are never asked.
     */
    fun sweepsFor(make: String?, carIs29Bit: Boolean): List<Sweep> {
        val f = Family.of(make)
        val bit29 = Sweep((0x00..0xFF).filter { it != 0xF1 }.map { String.format(Locale.US, "18DA%02XF1", it) }, "18DAF100", "1FFFFF00")
        if (carIs29Bit) return listOf(bit29)
        val sweeps = mutableListOf<Sweep>()
        when (f) {
            Family.GM -> sweeps += Sweep((0x240..0x25F).map(::hex3), "640", "7E0")
            Family.CHRYSLER -> sweeps += Sweep((0x740..0x77F).map(::hex3), "4C0", "7C0")
            Family.VW -> sweeps += Sweep((0x700..0x77D).map(::hex3), "700", "700")
            Family.NISSAN -> sweeps += Sweep((0x700..0x7C7).filter { it !in 0x7C8..0x7CF }.map(::hex3), "700", "700")
            else -> {}
        }
        if (f != Family.VW && f != Family.NISSAN) {
            sweeps += Sweep((0x700..0x7F7).filter { it != 0x7DF && it !in 0x7E8..0x7EF }.map(::hex3), "700", "700")
        }
        if (f == Family.HONDA) sweeps += bit29
        return sweeps
    }

    /** Commands that set the adapter up for [sweep] (headers on so each answer's address shows) and back. */
    fun sweepSetup(sweep: Sweep): List<String> =
        listOf("ATH1", "ATCF ${sweep.filter}", "ATCM ${sweep.mask}", "ATST 19") + if (sweep.is29Bit) listOf("ATCP 18") else emptyList()
    fun sweepProbe(request: String): String = "ATSH " + if (request.length == 8) request.substring(2) else request
    fun sweepTeardown(defaultHeader: String?): List<String> =
        listOfNotNull("ATH0", "ATCRA", "ATAR", "ATST 32", defaultHeader?.let { "ATSH $it" })

    /**
     * The addresses that answered a sweep probe, read from header-on lines ("7B8065902..." or
     * "18DAF128065902..."). Only answers to the request count: a positive answer (59), a "no" to
     * it (7F 19), or the first frame of a longer positive answer, so the car's ordinary traffic
     * on those addresses is never mistaken for a module. Error lines and "NO DATA" give nothing.
     */
    fun answeringAddresses(lines: List<String>, is29Bit: Boolean): Set<String> {
        val len = if (is29Bit) 8 else 3
        return lines.map { it.replace(" ", "").uppercase(Locale.US) }
            .filter { it.length >= len + 4 && it.length % 2 == len % 2 && it.all { c -> c in '0'..'9' || c in 'A'..'F' } }
            .filter { line ->
                val b = ElmParser.toBytes(line.substring(len))
                when (b[0] shr 4) {
                    0 -> b.size >= 2 && (b[1] == 0x59 || (b[1] == 0x7F && b.size >= 3 && b[2] == 0x19))
                    1 -> b.size >= 3 && b[2] == 0x59
                    else -> false
                }
            }
            .map { it.substring(0, len) }
            .toSet()
    }

    /** A name for a module the search found: the maker's name for that address if known, else its address. */
    fun nameFor(make: String?, request: String, carIs29Bit: Boolean): String =
        modulesFor(make, null, carIs29Bit).firstOrNull { it.request == request }?.name ?: "Module at $request"

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

    /**
     * The adapter commands that point it at [m] and back again afterwards. Flow control (the
     * adapter's "go ahead" when a module's answer is longer than one CAN frame) is sent to the
     * module's own address, since not every maker's modules answer at the usual address + 8.
     */
    fun pointAt(m: Module): List<String> =
        if (m.is29Bit) listOf("ATCP ${m.request.substring(0, 2)}", "ATSH ${m.request.substring(2)}", "ATCRA ${m.reply}", "ATFCSH ${m.request}", "ATFCSD 300000", "ATFCSM 1")
        else listOf("ATSH ${m.request}", "ATCRA ${m.reply}", "ATFCSH ${m.request}", "ATFCSD 300000", "ATFCSM 1")
    fun pointBack(defaultHeader: String?): List<String> = listOfNotNull("ATFCSM 0", "ATCRA", defaultHeader?.let { "ATSH $it" })

    /**
     * When a 29-bit module is asked on a car that talks OBD2 on 11-bit addresses (Honda's do), the
     * adapter switches to the 29-bit form of the same CAN speed for it, then back. [protocolCode]
     * is the car's protocol (6: 11-bit 500k, 8: 11-bit 250k). Null when no switch is needed.
     */
    fun protocolSwitch(protocolCode: String?): Pair<String, String>? = when (protocolCode?.trim()?.uppercase(Locale.US)?.removePrefix("A")) {
        "6" -> "ATSP7" to "ATSP6"
        "8" -> "ATSP9" to "ATSP8"
        else -> null
    }
}
