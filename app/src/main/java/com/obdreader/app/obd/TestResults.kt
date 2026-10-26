package com.obdreader.app.obd

import java.util.Locale

/**
 * The engine computer's own self-test results (service 06, "Mode 06"): for each monitor it ran,
 * the measured value and the limits it has to fall between. A result near a limit can show a part
 * on its way out before it sets a trouble code. Read on CAN cars (most from 2008, many earlier),
 * where the format is standard.
 */
object TestResults {

    data class Result(val mid: Int, val tid: Int, val unitId: Int, val value: Int, val min: Int, val max: Int) {
        val passed: Boolean get() {
            val signed = UNITS[unitId]?.signed ?: (unitId >= 0x80)
            fun s(v: Int) = if (signed && v > 0x7FFF) v - 0x10000 else v
            return s(value) in s(min)..s(max)
        }
        val monitor: String get() = monitorName(mid)
        /** "0.452 V (0.200 to 0.900)" when the unit is known, else the raw numbers. */
        fun text(): String {
            val u = UNITS[unitId]
            return if (u == null) "$value (limits $min to $max)"
            else "${u.format(value)} (limits ${u.number(min)} to ${u.format(max)})"
        }
    }

    /** How a test's numbers convert: multiply (and offset), a unit, and whether they're signed. */
    private class Scale(val scale: Double, val unit: String, val signed: Boolean = false, val offset: Double = 0.0, val decimals: Int = 0) {
        fun number(raw: Int): String {
            val v = (if (signed && raw > 0x7FFF) raw - 0x10000 else raw) * scale + offset
            return String.format(Locale.US, "%.${decimals}f", v)
        }
        fun format(raw: Int): String = number(raw) + if (unit.isEmpty()) "" else " $unit"
    }

    // The unit and scaling IDs from SAE J1979 that are most used. Others show their raw numbers.
    private val UNITS: Map<Int, Scale> = mapOf(
        0x01 to Scale(1.0, ""), 0x02 to Scale(0.1, "", decimals = 1), 0x03 to Scale(0.01, "", decimals = 2), 0x04 to Scale(0.001, "", decimals = 3),
        0x07 to Scale(0.25, "rpm", decimals = 0), 0x09 to Scale(1.0, "km/h"),
        0x0A to Scale(0.122, "mV", decimals = 0), 0x0B to Scale(0.001, "V", decimals = 3), 0x0C to Scale(0.01, "V", decimals = 2),
        0x10 to Scale(1.0, "ms"), 0x12 to Scale(1.0, "s"), 0x14 to Scale(1.0, "Ω"),
        0x16 to Scale(0.1, "°C", offset = -40.0, decimals = 1), 0x1A to Scale(1.0, "kPa"),
        0x24 to Scale(1.0, "counts"), 0x25 to Scale(1.0, "km"), 0x27 to Scale(0.01, "g/s", decimals = 2), 0x2F to Scale(0.01, "%", decimals = 2),
        0x81 to Scale(1.0, "", signed = true), 0x82 to Scale(0.1, "", signed = true, decimals = 1), 0x83 to Scale(0.01, "", signed = true, decimals = 2),
        0x84 to Scale(0.001, "", signed = true, decimals = 3), 0x8A to Scale(0.122, "mV", signed = true), 0x8B to Scale(0.001, "V", signed = true, decimals = 3),
        0x8C to Scale(0.01, "V", signed = true, decimals = 2), 0x96 to Scale(0.1, "°C", signed = true, decimals = 1), 0xAF to Scale(0.01, "%", signed = true, decimals = 2),
    )

    fun monitorName(mid: Int): String = when (mid) {
        in 0x01..0x10 -> "Oxygen sensor, bank ${(mid - 1) / 4 + 1} sensor ${(mid - 1) % 4 + 1}"
        in 0x21..0x24 -> "Catalytic converter, bank ${mid - 0x20}"
        in 0x31..0x34 -> "EGR, bank ${mid - 0x30}"
        in 0x35..0x38 -> "Variable valve timing, bank ${mid - 0x34}"
        0x39 -> "Evaporative system (cap off / big leak)"
        0x3A -> "Evaporative system (0.090\" leak)"
        0x3B -> "Evaporative system (0.040\" leak)"
        0x3C -> "Evaporative system (0.020\" leak)"
        0x3D -> "Purge flow"
        in 0x41..0x50 -> "Oxygen sensor heater, bank ${(mid - 0x41) / 4 + 1} sensor ${(mid - 0x41) % 4 + 1}"
        in 0x61..0x64 -> "Heated catalyst, bank ${mid - 0x60}"
        in 0x71..0x74 -> "Secondary air ${mid - 0x70}"
        in 0x81..0x84 -> "Fuel system, bank ${mid - 0x80}"
        in 0x85..0x88 -> "Boost pressure, bank ${mid - 0x84}"
        in 0x90..0x91 -> "NOx adsorber, bank ${mid - 0x8F}"
        in 0x98..0x99 -> "NOx catalyst, bank ${mid - 0x97}"
        0xA1 -> "Misfire, all cylinders"
        in 0xA2..0xAD -> "Misfire, cylinder ${mid - 0xA1}"
        in 0xB0..0xB1 -> "Particulate filter, bank ${mid - 0xAF}"
        else -> String.format(Locale.US, "Monitor %02X", mid)
    }

    /** The supported monitor IDs from a "supported" answer (service 06 MID 00, 20, 40…). */
    fun supported(base: Int, bitmap: IntArray?): List<Int> {
        if (bitmap == null || bitmap.size < 4) return emptyList()
        val out = mutableListOf<Int>()
        for (i in 0 until 32) if (bitmap[i / 8] and (0x80 shr (i % 8)) != 0) out += base + i + 1
        return out
    }

    /** The test results in one service 06 answer (the bytes after "46"), in 9-byte records. */
    fun parse(bytes: IntArray): List<Result> =
        bytes.toList().chunked(9).filter { it.size == 9 }.map { r ->
            Result(r[0], r[1], r[2], r[3] * 256 + r[4], r[5] * 256 + r[6], r[7] * 256 + r[8])
        }
}
