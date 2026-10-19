package com.obdreader.app.obd

import java.util.Locale

data class Pid(
    val number: Int,
    val label: String,
    val unit: String,
    val decimals: Int,
    val bytes: Int,
    /** Fast PIDs are polled every cycle; slow ones every few cycles. */
    val fast: Boolean,
    /** Display range for gauges and bars. */
    val min: Double,
    val max: Double,
    /** Values at or above this are highlighted as a warning (null = never). */
    val warnAbove: Double?,
    val decode: (IntArray) -> Double,
) {
    val hex: String get() = String.format(Locale.US, "%02X", number)
}

object Pids {
    const val RPM = 0x0C
    const val SPEED = 0x0D

    val dashboard: List<Pid> = listOf(
        Pid(RPM, "Engine RPM", "rpm", 0, 2, true, 0.0, 8000.0, 6500.0) { (it[0] * 256 + it[1]) / 4.0 },
        Pid(SPEED, "Speed", "km/h", 0, 1, true, 0.0, 200.0, null) { it[0].toDouble() },
        Pid(0x11, "Throttle", "%", 0, 1, true, 0.0, 100.0, null) { it[0] * 100.0 / 255.0 },
        Pid(0x04, "Engine load", "%", 0, 1, true, 0.0, 100.0, 90.0) { it[0] * 100.0 / 255.0 },
        Pid(0x05, "Coolant", "°C", 0, 1, false, 40.0, 120.0, 108.0) { it[0] - 40.0 },
        Pid(0x0F, "Intake air", "°C", 0, 1, false, -20.0, 80.0, 65.0) { it[0] - 40.0 },
        Pid(0x0B, "Manifold pressure", "kPa", 0, 1, true, 0.0, 250.0, null) { it[0].toDouble() },
        Pid(0x10, "MAF airflow", "g/s", 1, 2, true, 0.0, 100.0, null) { (it[0] * 256 + it[1]) / 100.0 },
        Pid(0x0E, "Timing advance", "°", 1, 1, true, -10.0, 50.0, null) { it[0] / 2.0 - 64.0 },
        Pid(0x2F, "Fuel level", "%", 0, 1, false, 0.0, 100.0, null) { it[0] * 100.0 / 255.0 },
        Pid(0x46, "Ambient air", "°C", 0, 1, false, -10.0, 50.0, null) { it[0] - 40.0 },
        Pid(0x5C, "Oil temp", "°C", 0, 1, false, 40.0, 150.0, 130.0) { it[0] - 40.0 },
    )
}
