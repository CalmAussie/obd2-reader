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

    /** One reading: its label, value and the line under it; [unit] is set for live readings, whose line is just the unit. */
    data class Cell(val label: String, val value: String, val sub: String, val unit: String = "") {
        /** The value with its unit, for sizes with no line under it: "62 km/h", "88°C", "14.3 V". */
        val withUnit: String get() = if (unit.isEmpty() || value.endsWith("k")) value else if (unit.startsWith("°")) value + unit else "$value $unit"
    }
    data class Content(val status: String, val cells: List<Cell>, val button: String, val connected: Boolean)

    /** A reading the app saw on a real car, with when. */
    data class Seen(val value: Double, val atMs: Long)
    data class CodesSeen(val stored: Int, val pending: Int, val atMs: Long)
    data class LastDrive(val distanceKm: Double, val score: Int?, val endedAtMs: Long)

    const val NA = "N/A"
    private const val LOADING = "—"

    /** A reading the widget can show while connected. */
    data class Reading(val key: String, val label: String, val unit: String, val decimals: Int)

    /** Engine speed (shown in thousands: 0.7k, 3.2k), the Dashboard's readings and the battery. */
    val READINGS: List<Reading> = Pids.dashboard.map { Reading(it.hex, if (it.hex == "0C") "RPM" else it.label, it.unit, it.decimals) } +
        Reading("VOLT", "Battery", "V", 1)

    /** Speed, coolant and battery to start with. */
    val DEFAULT_READINGS = listOf("0D", "05", "VOLT")

    /** The chosen readings from how they're kept: one to three, known ones only, no repeats. */
    fun decodeReadings(text: String?): List<String> {
        val known = READINGS.map { it.key }.toSet()
        val keys = text.orEmpty().split(",").map { it.trim() }.filter { it in known }.distinct().take(3)
        return keys.ifEmpty { DEFAULT_READINGS }
    }
    fun encodeReadings(keys: List<String>): String = keys.distinct().take(3).joinToString(",")

    /** Engine speed in thousands with one decimal: 700 → "0.7k", 3,200 → "3.2k". */
    fun rpmText(rpm: Double): String = String.format(Locale.US, "%.1fk", rpm / 1000.0)

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
        /** The readings to show while connected (see [READINGS]); 1 to 3. */
        live: List<String> = DEFAULT_READINGS,
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
                v != null && key == "0C" -> rpmText(v)
                v != null -> format(convert(v), decimals)
                key in unsupported || key in readings -> NA
                else -> LOADING
            }
            val known = text != NA && text != LOADING
            return Cell(label, text, if (known) unit else "", if (known) unit else "")
        }
        fun liveFor(key: String): Cell {
            val r = READINGS.firstOrNull { it.key == key } ?: READINGS.first()
            return when {
                Units.isSpeed(key) -> live(key, r.label, if (mph) "mph" else "km/h", r.decimals) { if (mph) it * 0.621371 else it }
                Units.isTemperature(key) -> live(key, r.label, if (fahrenheit) "°F" else "°C", r.decimals) { if (fahrenheit) it * 9 / 5 + 32 else it }
                else -> live(key, r.label, r.unit, r.decimals)
            }
        }
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
            Phase.CONNECTED -> live.ifEmpty { DEFAULT_READINGS }.take(3).map { liveFor(it) }
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
