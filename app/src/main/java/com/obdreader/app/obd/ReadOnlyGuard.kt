package com.obdreader.app.obd

import java.util.Locale

/**
 * OBD2 Reader is read-only: it never changes anything in the car. Every command passes through
 * here before it reaches the adapter, and only the adapter's own settings (AT commands) and
 * OBD2 *read* services are let through (and on STN adapters, the few ST commands that identify
 * the adapter or switch which of the car's networks it listens to).
 *
 * On 1996–2000 Volvos, two of Volvo's own keyword D3 B0 requests are let through, both reads:
 * AE 01 (a module's codes) and B9 03 (the instrument cluster's mileage). Its clear (AF) and
 * service-light reset (B0, B2) stay blocked, like every other request.
 *
 * Blocked, among everything else: service 04 (clear trouble codes and turn off the check-engine
 * light), service 08 (control on-board systems) and the UDS services workshop tools use to clear
 * memory (14), write data (2E, 3D), run routines (31), reset modules (11) or change sessions (10).
 */
object ReadOnlyGuard {

    /**
     * Services that only read: OBD2's live data, freeze frame, stored codes, test results, pending
     * codes, vehicle info and permanent codes, plus the two manufacturer "read data" services
     * (21, ReadDataByLocalIdentifier, and 22, ReadDataByIdentifier) used for enhanced readings, and
     * the three that read other modules' trouble codes: 13 (readDiagnosticTroubleCodes), 18
     * (readDTCByStatus) and 19 (UDS ReadDTCInformation, whose every sub-function only reads).
     * Clearing them (14 and 04) stays blocked. 1A reads too: GM's ReadDataByIdentifier, and
     * KWP2000's readEcuIdentification.
     */
    private val READ_SERVICES = setOf("01", "02", "03", "06", "07", "09", "0A", "21", "22", "13", "18", "19", "1A")

    fun isAllowed(command: String): Boolean {
        val cmd = command.replace(" ", "").uppercase(Locale.US)
        if (cmd.isEmpty()) return false
        // AT commands configure the ELM327 adapter itself and send nothing to the car, except
        // ATWM, which sets a message the adapter sends to the car on its own, so it's blocked.
        // ATWM sets the message the adapter sends to the car on its own to keep a session awake, so
        // only two are allowed: Volvo's tester-present (82 <module> 13 A1) and the ELM327's own
        // default (68 6A F1 01 00, an OBD2 "which readings do you support" request).
        if (cmd.startsWith("ATWM")) return Regex("ATWM82[0-9A-F]{2}13A1").matches(cmd) || cmd == "ATWM686AF10100"
        if (cmd.startsWith("AT")) return true
        // STN adapters (OBDLink and others) add ST commands. Only these are let through: identify
        // (STI, STDI), report the protocol (STPR, STPRS) and set it (STP 53 for the second network,
        // then back). Every other ST command is blocked, including the ones that send raw messages.
        if (cmd.startsWith("ST")) return cmd in setOf("STI", "STDI", "STPR", "STPRS") || Regex("STP[0-9]{1,2}").matches(cmd)
        if (cmd.length < 2 || !cmd.all { it in '0'..'9' || it in 'A'..'F' }) return false
        // Volvo's keyword D3 B0 (1996–2000 850, S70, V70, C70): only "read codes" and "read mileage".
        if (cmd in VolvoD3B0.ALLOWED_REQUESTS) return true
        return cmd.substring(0, 2) in READ_SERVICES
    }
}

/** Thrown when something tries to send a command that could change the car. */
class ReadOnlyException(command: String) :
    IllegalArgumentException("OBD2 Reader is read-only, so it won't send '$command' to the car.")
