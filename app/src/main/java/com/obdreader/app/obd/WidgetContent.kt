package com.obdreader.app.obd

import java.util.Locale

/**
 * What the home-screen widget shows, worked out from the connection's state and what was last
 * seen, so it can be tested without Android. While connected: live speed, coolant and battery.
 * While the ignition is off: the battery live, the last code check and the last drive. Otherwise
 * the last battery reading, code check and drive, each with how long ago. Anything not known yet
 * says N/A rather than guessing.
 */
object WidgetContent {

    enum class Phase { IDLE, CONNECTING, CONNECTED, IGNITION_OFF }

    data class Cell(val label: String, val value: String, val sub: String)
    data class Content(val status: String, val cells: List<Cell>, val button: String, val connected: Boolean)

    /** A reading the app saw on a real car, with when. */
    data class Seen(val value: Double, val atMs: Long)
    data class CodesSeen(val stored: Int, val pending: Int, val atMs: Long)
    data class LastDrive(val distanceKm: Double, val score: Int?, val endedAtMs: Long)

    const val NA = "N/A"
    private const val LOADING = "—"

    fun build(
        phase: Phase,
        adapterName: String?,
        isDemo: Boolean,
        /** Live readings by key ("0D", "05", "VOLT"); a key that's present with null means the car didn't answer it. */
        readings: Map<String, Double?>,
        unsupported: Set<String>,
        mph: Boolean,
        fahrenheit: Boolean,
        battery: Seen?,
        codes: CodesSeen?,
        lastDrive: LastDrive?,
        nowMs: Long,
    ): Content {
        val active = phase != Phase.IDLE
        val status = when (phase) {
            Phase.IDLE -> "Not connected"
            Phase.CONNECTING -> "Connecting…"
            Phase.CONNECTED -> if (isDemo) "Demo car" else "Connected" + (adapterName?.let { " · $it" } ?: "")
            Phase.IGNITION_OFF -> "Ignition off"
        }
        fun live(key: String, label: String, unit: String, decimals: Int, convert: (Double) -> Double = { it }): Cell {
            val v = readings[key]
            val text = when {
                v != null -> format(convert(v), decimals)
                key in unsupported || key in readings -> NA
                else -> LOADING
            }
            return Cell(label, text, if (text == NA || text == LOADING) "" else unit)
        }
        val speed = live("0D", "Speed", if (mph) "mph" else "km/h", 0) { if (mph) it * 0.621371 else it }
        val coolant = live("05", "Coolant", if (fahrenheit) "°F" else "°C", 0) { if (fahrenheit) it * 9 / 5 + 32 else it }
        val liveBattery = live("VOLT", "Battery", "V", 1)
        val lastBattery = battery?.let { Cell("Battery", format(it.value, 1) + " V", ago(it.atMs, nowMs)) } ?: Cell("Battery", NA, "Not read yet")
        val codesCell = codes?.let {
            val text = when {
                it.stored == 0 && it.pending == 0 -> "None"
                it.pending == 0 -> "${it.stored} stored"
                it.stored == 0 -> "${it.pending} pending"
                else -> "${it.stored} + ${it.pending}"
            }
            Cell("Trouble codes", text, ago(it.atMs, nowMs))
        } ?: Cell("Trouble codes", NA, "Not checked yet")
        val driveCell = lastDrive?.let {
            val d = if (mph) it.distanceKm * 0.621371 else it.distanceKm
            Cell("Last drive", format(d, 1) + if (mph) " mi" else " km", it.score?.let { s -> "Score $s · " + ago(it.endedAtMs, nowMs) } ?: ago(it.endedAtMs, nowMs))
        } ?: Cell("Last drive", NA, "No drives yet")
        val cells = when (phase) {
            Phase.CONNECTED -> listOf(speed, coolant, liveBattery)
            Phase.IGNITION_OFF -> listOf(liveBattery.takeIf { readings["VOLT"] != null } ?: lastBattery, codesCell, driveCell)
            else -> listOf(lastBattery, codesCell, driveCell)
        }
        return Content(status, cells, if (active) "Disconnect" else "Connect", phase == Phase.CONNECTED)
    }

    fun format(v: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", v)

    /** "just now", "5 min ago", "3 h ago", "2 days ago". */
    fun ago(atMs: Long, nowMs: Long): String {
        val min = ((nowMs - atMs) / 60_000).coerceAtLeast(0)
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 48 * 60 -> "${min / 60} h ago"
            else -> "${min / (24 * 60)} days ago"
        }
    }

    // How the last-seen values are kept on the phone (real cars only, never the demo).
    fun encodeSeen(s: Seen) = "${s.value}|${s.atMs}"
    fun decodeSeen(t: String?): Seen? = t?.split("|")?.takeIf { it.size == 2 }?.let { p -> p[0].toDoubleOrNull()?.let { v -> p[1].toLongOrNull()?.let { Seen(v, it) } } }
    fun encodeCodes(c: CodesSeen) = "${c.stored}|${c.pending}|${c.atMs}"
    fun decodeCodes(t: String?): CodesSeen? = t?.split("|")?.takeIf { it.size == 3 }?.let { p ->
        val a = p[0].toIntOrNull(); val b = p[1].toIntOrNull(); val c = p[2].toLongOrNull()
        if (a == null || b == null || c == null) null else CodesSeen(a, b, c)
    }
}
