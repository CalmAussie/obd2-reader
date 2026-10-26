package com.obdreader.app.obd

import java.util.Locale

/**
 * OBD2 Reader is read-only: it never changes anything in the car. Every command passes through
 * here before it reaches the adapter, and only the adapter's own settings (AT commands) and
 * OBD2 *read* services are let through.
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
        if (cmd.startsWith("AT")) return !cmd.startsWith("ATWM")
        if (cmd.length < 2 || !cmd.all { it in '0'..'9' || it in 'A'..'F' }) return false
        return cmd.substring(0, 2) in READ_SERVICES
    }
}

/** Thrown when something tries to send a command that could change the car. */
class ReadOnlyException(command: String) :
    IllegalArgumentException("OBD2 Reader is read-only, so it won't send '$command' to the car.")
