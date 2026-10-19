package com.obdreader.app.obd

import java.util.Locale

/**
 * Turns one adapter exchange (the command sent and the reply lines) into plain English for the
 * adapter log, so "> 010C  < 410C319E" also reads "Engine RPM: 3,176 rpm".
 * Returns null when there's nothing useful to add.
 */
object LogTranslator {

    private val pidsByNumber = Pids.dashboard.associateBy { it.number }
    private val lengths = Pids.dashboard.associate { it.number to it.bytes }

    private val SETUP = mapOf(
        "ATE0" to "Turned off echo",
        "ATE1" to "Turned on echo",
        "ATL0" to "Turned off line feeds",
        "ATS0" to "Turned off spaces in replies",
        "ATH0" to "Turned off message headers",
        "ATH1" to "Turned on message headers",
        "ATAT1" to "Turned on adaptive timing, so replies come back faster",
        "ATPC" to "Closed the protocol before trying the next one",
    )

    fun describe(command: String, lines: List<String>): String? {
        val cmd = command.replace(" ", "").uppercase(Locale.US)
        ElmParser.errorIn(lines)?.let { return error(it.trim().uppercase(Locale.US), cmd) }
        val reply = lines.firstOrNull()?.trim().orEmpty()
        return when {
            cmd == "ATZ" -> "Reset the adapter" + if (reply.isNotEmpty()) ". It reports $reply" else ""
            cmd == "ATI" -> "Adapter version: ${reply.ifEmpty { "not given" }}"
            cmd == "ATRV" -> "Battery voltage: ${reply.ifEmpty { "not given" }}"
            cmd == "ATSP0" -> "Set the protocol to automatic search"
            cmd.startsWith("ATSP") -> "Set the protocol to " + (ObdProtocols.byCode(cmd.removePrefix("ATSP"))?.name ?: cmd.removePrefix("ATSP"))
            cmd == "ATDPN" -> "The car uses " + (ObdProtocols.byCode(reply)?.name ?: "protocol $reply")
            cmd == "ATDP" -> "The car uses $reply"
            cmd in SETUP -> SETUP[cmd]
            cmd.startsWith("AT") -> null
            cmd == "04" -> if (ElmParser.extractData(lines, "44") != null) "Cleared the trouble codes and turned off the check-engine light" else null
            cmd == "0902" -> ElmParser.parseVin(lines)?.let { "VIN: $it" }
            cmd == "03" || cmd == "07" -> null // decoded codes follow, with their definitions
            cmd.startsWith("01") && cmd.length >= 4 -> mode01(cmd.substring(2), lines)
            else -> null
        }
    }

    private fun mode01(pidHex: String, lines: List<String>): String? {
        val first = pidHex.substring(0, 2).toIntOrNull(16) ?: return null
        // 0100, 0120, 0140…: which readings the car supports.
        if (pidHex.length == 2 && first % 0x20 == 0) {
            val bytes = ElmParser.extractData(lines, "41$pidHex") ?: return null
            val count = ElmParser.supportedFromBitmask(bytes, first).count { it % 0x20 != 0 }
            val from = String.format(Locale.US, "%02X", first + 1)
            val to = String.format(Locale.US, "%02X", first + 0x1F)
            return "The car answered. It supports $count readings in the $from–$to range"
        }
        val values = ElmParser.parseMultiPid(lines, lengths)
        if (values.isEmpty()) return null
        return pidHex.chunked(2).mapNotNull { h ->
            val n = h.toIntOrNull(16) ?: return@mapNotNull null
            val pid = pidsByNumber[n] ?: return@mapNotNull null
            val data = values[n] ?: return@mapNotNull null
            "${pid.label}: ${format(pid, pid.decode(data))}"
        }.joinToString(", ").ifEmpty { null }
    }

    fun format(pid: Pid, value: Double): String {
        val number = String.format(Locale.US, "%,.${pid.decimals}f", value)
        val tight = pid.unit.startsWith("%") || pid.unit.startsWith("°")
        return if (tight) number + pid.unit else "$number ${pid.unit}"
    }

    private fun error(e: String, cmd: String): String = when {
        e == "?" -> "The adapter didn't understand this command"
        e.contains("NO DATA") -> if (cmd.startsWith("01") || cmd.startsWith("09")) "No answer from the car. The ignition may be off, or the car doesn't support this reading" else "No answer from the car"
        e.contains("UNABLE TO CONNECT") -> "The adapter couldn't reach the car on this protocol"
        e.contains("CAN ERROR") || e.contains("BUS ERROR") -> "Error on the car's data bus. Check the adapter is pushed fully into the port"
        e.contains("BUS BUSY") -> "The car's data bus is busy. The app will try again"
        e.contains("BUFFER FULL") -> "The adapter's buffer filled up. The app will slow down and retry"
        e.contains("STOPPED") -> "The request was interrupted"
        e.contains("LV RESET") -> "The adapter reset itself because the battery voltage dropped (often while cranking)"
        else -> "Adapter error: $e"
    }
}
