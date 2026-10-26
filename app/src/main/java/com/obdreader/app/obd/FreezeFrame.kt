package com.obdreader.app.obd

import java.util.Locale

/**
 * The snapshot a car keeps of its engine at the moment a trouble code was set (OBD2 service 02,
 * frame 0): which code caused it, and readings like speed, RPM, coolant and the fuel trims. A
 * mechanic uses it to see the conditions the fault happened in.
 */
data class FreezeFrame(val code: String?, val readings: List<Pair<Item, Double>>, val fuelSystem: String?) {

    enum class Kind { TEMPERATURE, SPEED, PLAIN }

    /** One reading a freeze frame can hold: its PID, name, unit and how to decode it. */
    data class Item(val pid: String, val label: String, val unit: String, val kind: Kind, val bytes: Int, val decode: (IntArray) -> Double)

    val isEmpty: Boolean get() = code == null && readings.isEmpty() && fuelSystem == null

    /** "Engine RPM 2,140 rpm · Speed 64 km/h · …", with the user's units. */
    fun lines(): List<String> = buildList {
        fuelSystem?.let { add("Fuel system: $it") }
        readings.forEach { (item, v) -> add("${item.label}: ${format(item, v)}") }
    }

    companion object {
        private fun trim(b: IntArray) = (b[0] - 128) * 100.0 / 128

        /** Asked for in this order: what matters most for working out a fault first. */
        val ITEMS = listOf(
            Item("0C", "Engine RPM", "rpm", Kind.PLAIN, 2) { (it[0] * 256 + it[1]) / 4.0 },
            Item("0D", "Speed", "km/h", Kind.SPEED, 1) { it[0].toDouble() },
            Item("05", "Coolant", "°C", Kind.TEMPERATURE, 1) { it[0] - 40.0 },
            Item("04", "Engine load", "%", Kind.PLAIN, 1) { it[0] * 100.0 / 255 },
            Item("06", "Short-term fuel trim (bank 1)", "%", Kind.PLAIN, 1, ::trim),
            Item("07", "Long-term fuel trim (bank 1)", "%", Kind.PLAIN, 1, ::trim),
            Item("08", "Short-term fuel trim (bank 2)", "%", Kind.PLAIN, 1, ::trim),
            Item("09", "Long-term fuel trim (bank 2)", "%", Kind.PLAIN, 1, ::trim),
            Item("0B", "Manifold pressure", "kPa", Kind.PLAIN, 1) { it[0].toDouble() },
            Item("0F", "Intake air", "°C", Kind.TEMPERATURE, 1) { it[0] - 40.0 },
            Item("11", "Throttle", "%", Kind.PLAIN, 1) { it[0] * 100.0 / 255 },
            Item("10", "MAF airflow", "g/s", Kind.PLAIN, 2) { (it[0] * 256 + it[1]) / 100.0 },
        )

        /** The code that stored the freeze frame, from a service 02 PID 02 answer (two bytes), or null if none. */
        fun code(bytes: IntArray?): String? {
            if (bytes == null || bytes.size < 2 || (bytes[0] == 0 && bytes[1] == 0)) return null
            return ElmParser.decodeDtc(String.format(Locale.US, "%02X%02X", bytes[0], bytes[1]))
        }

        /** Service 02 PID 03: how the fuel system was running (system 1). */
        fun fuelSystem(bytes: IntArray?): String? = when (bytes?.firstOrNull()) {
            null, 0 -> null
            1 -> "Open loop, engine still warming up"
            2 -> "Closed loop, using the oxygen sensors (normal)"
            4 -> "Open loop, under load or slowing down"
            8 -> "Open loop, a fault in the system"
            16 -> "Closed loop, with a fault in a sensor"
            else -> null
        }

        fun format(item: Item, v: Double): String = when (item.kind) {
            Kind.TEMPERATURE -> "${Math.round(Units.temperature(v))} ${Units.temperatureUnit}"
            Kind.SPEED -> Units.speedText(v)
            Kind.PLAIN -> when {
                item.pid == "0C" -> String.format(Locale.US, "%,d rpm", Math.round(v))
                item.pid in setOf("06", "07", "08", "09") -> String.format(Locale.US, "%+.1f%%", v)
                item.unit == "%" -> "${Math.round(v)}%"
                item.unit == "g/s" -> String.format(Locale.US, "%.1f g/s", v)
                else -> "${Math.round(v)} ${item.unit}"
            }
        }
    }
}
