package com.obdreader.app.obd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * High-level ELM327 client. One command is in flight at a time (the adapter is
 * strictly request/response), so live polling and diagnostic requests can be
 * issued from different coroutines safely.
 */
class ElmClient(
    private val transport: ObdTransport,
    private val logger: (String) -> Unit = {},
) {
    private val mutex = Mutex()

    var isCan: Boolean = false
        private set

    suspend fun command(cmd: String, timeoutMs: Long = 3000): List<String> = mutex.withLock {
        if (!ReadOnlyGuard.isAllowed(cmd)) {
            logger("= Blocked '$cmd': OBD2 Reader only reads from the car")
            throw ReadOnlyException(cmd)
        }
        withContext(Dispatchers.IO) {
            val raw = try {
                transport.exchange(cmd, timeoutMs)
            } catch (e: AdapterTimeoutException) {
                logger("> $cmd  < (no reply)")
                logger("= The adapter didn't reply in time")
                throw e
            }
            val lines = ElmParser.cleanLines(raw, cmd)
            logger("> $cmd  < " + lines.joinToString(" | ").ifEmpty { "(empty)" })
            LogTranslator.describe(cmd, lines)?.let { logger("= $it") }
            lines
        }
    }

    data class SessionInfo(
        /** "ELM327 v1.5 · ISO 9141-2" style status line. */
        val summary: String,
        /** The protocol the car answered on (an [ObdProtocols] code), if known. */
        val protocolCode: String?,
    )

    /**
     * Resets and configures the adapter, then finds a protocol the car answers on.
     *
     * [protocol] is the user's choice: [ObdProtocols.AUTO] or one fixed protocol.
     * On Automatic, [lastWorking] (the protocol that worked last time) is tried first
     * for a quick reconnect, then the adapter's own search, then every protocol in turn,
     * because cheap adapters often miss J1850 and ISO 9141 cars in their auto-search.
     *
     * [carOrder] comes from the user's chosen car ([VehicleCatalog.recommend]). Its first
     * protocol is tried early and given a longer wait, since that's the likeliest one.
     */
    /** True once the adapter has answered ATI like an ELM327 or STN chip (so a later failure is the car, not the adapter). */
    var identified = false
        private set

    suspend fun initialize(
        protocol: String = ObdProtocols.AUTO,
        lastWorking: String? = null,
        carOrder: List<String>? = null,
        onProgress: (String) -> Unit = {},
    ): SessionInfo {
        command("ATZ", 6000)
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1")) {
            command(cmd)
        }
        val ati = command("ATI")
        val version = ati.firstOrNull() ?: "ELM327"
        identified = ati.any { line -> listOf("ELM", "STN", "OBD", "VLINK").any { line.uppercase().contains(it) } }

        val forced = protocol != ObdProtocols.AUTO
        val attempts: List<String> = if (forced) {
            listOf(protocol)
        } else {
            buildList {
                if (lastWorking != null && ObdProtocols.byCode(lastWorking) != null) add(lastWorking)
                carOrder?.firstOrNull()?.let { add(it) }
                add(ObdProtocols.AUTO)
                addAll(carOrder ?: ObdProtocols.ALL.map { it.code })
            }.distinct()
        }
        val expected = carOrder?.firstOrNull()

        var lastError: String? = null
        var found = false
        for (p in attempts) {
            val proto = ObdProtocols.byCode(p)
            onProgress(if (proto == null) "Searching for the car's protocol…" else "Trying ${proto.name}…")
            command("ATSP$p")
            val probe = try {
                // The first request wakes the car's ECU; K-line protocols take several seconds.
                val wait = when {
                    proto == null -> 15000L
                    p == expected -> maxOf(proto.timeoutMs * 2, 10000L)
                    else -> proto.timeoutMs
                }
                command("0100", wait)
            } catch (e: AdapterTimeoutException) {
                emptyList()
            }
            if (ElmParser.extractData(probe, "4100") != null) {
                found = true
                break
            }
            lastError = ElmParser.errorIn(probe) ?: lastError
            try {
                command("ATPC") // close the failed protocol before trying the next one
            } catch (ignored: AdapterTimeoutException) {
            }
        }

        if (!found) {
            val name = ObdProtocols.byCode(protocol)?.name
            throw ObdException(
                when {
                    forced -> "The car didn't answer on $name. Set the protocol back to Automatic on the Diagnostics tab and try again."
                    lastError?.uppercase(Locale.US)?.contains("UNABLE") == true ->
                        "The adapter is working, but the car didn't answer on any OBD2 protocol. Turn the ignition on. Cars sold in Australia before 2006 may not support standard OBD2."
                    else -> "The car didn't respond. Turn the ignition on and try again."
                }
            )
        }

        val code = command("ATDPN").firstOrNull().orEmpty()
        isCan = ElmParser.isCanProtocol(code)
        val described = ObdProtocols.byCode(code)?.name ?: command("ATDP").firstOrNull().orEmpty()
        return SessionInfo(
            // The same protocol name the Diagnostics tab shows: "ELM327 v1.5 · ISO 15765-4 CAN (11-bit, 500 kbaud)".
            summary = listOf(version, described.trim()).filter { it.isNotBlank() }.joinToString(" · "),
            protocolCode = ObdProtocols.byCode(code)?.code,
        )
    }

    /** Set of supported mode-01 PIDs, or null if the car won't say. */
    suspend fun supportedPids(): Set<Int>? {
        val result = mutableSetOf<Int>()
        var base = 0
        while (base <= 0xC0) {
            val code = String.format(Locale.US, "%02X", base)
            // Several modules can answer (engine, gearbox…), each with its own list. Use them all:
            // the first to answer isn't always the engine, and only the engine may go on to 0120.
            val answers = ElmParser.extractAll(command("01$code", 5000), "41$code").filter { it.size >= 4 }
            if (answers.isEmpty()) break
            answers.forEach { result += ElmParser.supportedFromBitmask(it, base) }
            if ((base + 0x20) !in result) break
            base += 0x20
        }
        return result.ifEmpty { null }
    }

    suspend fun readPid(pid: Pid): Double? {
        val bytes = ElmParser.extractData(command("01" + pid.hex), "41" + pid.hex) ?: return null
        if (bytes.size < pid.bytes) return null
        return pid.decode(bytes)
    }

    /**
     * Reads up to six PIDs in one request, which CAN cars support. This is much faster than
     * asking for each PID in turn. Returns the values found; PIDs the car didn't include are missing.
     */
    suspend fun readPids(pids: List<Pid>): Map<Pid, Double> {
        require(pids.size in 1..MAX_MULTI_PIDS)
        val lines = command("01" + pids.joinToString("") { it.hex })
        val raw = ElmParser.parseMultiPid(lines, pids.associate { it.number to it.bytes })
        return pids.mapNotNull { p -> raw[p.number]?.let { p to p.decode(it) } }.toMap()
    }

    suspend fun readVoltage(): Double? =
        command("ATRV").firstOrNull()
            ?.uppercase(Locale.US)
            ?.removeSuffix("V")
            ?.trim()
            ?.toDoubleOrNull()

    suspend fun readDtcs(pending: Boolean): List<String> {
        val (cmd, header) = if (pending) "07" to "47" else "03" to "43"
        return ElmParser.parseDtcs(command(cmd, 6000), header, isCan)
    }

    suspend fun readVin(): String? = ElmParser.parseVin(command("0902", 6000))

    /**
     * Reads the freeze frame (service 02, frame 0): the code that stored it, then each reading in
     * [FreezeFrame.ITEMS]. Null when the car keeps none (no code stored one, or it doesn't support it).
     * Gives up after three readings in a row that don't answer, so older cars aren't kept waiting.
     */
    suspend fun readFreezeFrame(): FreezeFrame? {
        val codeBytes = try { ElmParser.extractData(command("020200", 3000), "420200") } catch (e: AdapterTimeoutException) { null }
        val code = FreezeFrame.code(codeBytes)
        if (codeBytes == null || code == null) return null
        val fuel = try { FreezeFrame.fuelSystem(ElmParser.extractData(command("020300", 2000), "420300")) } catch (e: AdapterTimeoutException) { null }
        val readings = mutableListOf<Pair<FreezeFrame.Item, Double>>()
        var misses = 0
        for (item in FreezeFrame.ITEMS) {
            if (misses >= 3) break
            val bytes = try { ElmParser.extractData(command("02${item.pid}00", 2000), "42${item.pid}00") } catch (e: AdapterTimeoutException) { null }
            if (bytes == null || bytes.size < item.bytes) { misses++; continue }
            misses = 0
            readings += item to item.decode(bytes)
        }
        return FreezeFrame(code, readings, fuel)
    }

    /** Asks the car whether it's awake (used while waiting for the ignition). */
    suspend fun probe(): Boolean = ElmParser.extractData(command("0100", 2000), "4100") != null

    companion object {
        /** The most PIDs a CAN car answers in one mode 01 request. */
        const val MAX_MULTI_PIDS = 6
    }

    /** "ISO 15765-4 CAN (11-bit, 500 kbaud)" becomes "CAN 11-bit, 500 kbaud"; other names are already short. */
    internal fun shortProtocol(name: String): String =
        name.replace("ISO 15765-4 CAN", "CAN").replace(" (", " ").replace(")", "").trim()
}
