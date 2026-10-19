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
        withContext(Dispatchers.IO) {
            logger("> $cmd")
            val raw = transport.exchange(cmd, timeoutMs)
            val lines = ElmParser.cleanLines(raw, cmd)
            logger("< " + lines.joinToString(" | ").ifEmpty { "(empty)" })
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
        val version = command("ATI").firstOrNull() ?: "ELM327"

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
            summary = listOf(version, described).filter { it.isNotBlank() }.joinToString(" · "),
            protocolCode = ObdProtocols.byCode(code)?.code,
        )
    }

    /** Set of supported mode-01 PIDs, or null if the car won't say. */
    suspend fun supportedPids(): Set<Int>? {
        val result = mutableSetOf<Int>()
        var base = 0
        while (base <= 0xC0) {
            val code = String.format(Locale.US, "%02X", base)
            val bytes = ElmParser.extractData(command("01$code", 5000), "41$code") ?: break
            if (bytes.size < 4) break
            result += ElmParser.supportedFromBitmask(bytes, base)
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

    /** Mode 04: clears stored codes and turns off the check-engine light. */
    suspend fun clearDtcs(): Boolean =
        ElmParser.extractData(command("04", 6000), "44") != null

    suspend fun readVin(): String? = ElmParser.parseVin(command("0902", 6000))
}
