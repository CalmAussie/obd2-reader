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

    /**
     * More standard readings (SAE J1979, service 01), polled only when the car lists them in its
     * supported-readings answer. They aren't on the Dashboard by default: they can be graphed and
     * picked for the Dashboard's readouts. Engine oil pressure and oil life aren't here because the
     * standard has no service 01 reading for them (some makers offer them as their own readings).
     * Turbo speed isn't either: the standard lists it, but its scaling isn't published openly, and
     * the app doesn't guess.
     *
     * Fuel system status (03) and fuel type (51) are codes, not amounts: they're read like the others
     * and shown as words (see [FuelInfo]), never graphed.
     */
    val extra: List<Pid> = listOf(
        Pid(0x03, "Fuel system status", "", 0, 2, false, 0.0, 16.0, null) { it[0].toDouble() },
        Pid(0x06, "Short-term fuel trim, bank 1", "%", 1, 1, false, -25.0, 25.0, null) { trim(it[0]) },
        Pid(0x07, "Long-term fuel trim, bank 1", "%", 1, 1, false, -25.0, 25.0, null) { trim(it[0]) },
        Pid(0x08, "Short-term fuel trim, bank 2", "%", 1, 1, false, -25.0, 25.0, null) { trim(it[0]) },
        Pid(0x09, "Long-term fuel trim, bank 2", "%", 1, 1, false, -25.0, 25.0, null) { trim(it[0]) },
        Pid(0x0A, "Fuel pressure", "kPa", 0, 1, false, 0.0, 765.0, null) { it[0] * 3.0 },
        Pid(0x1F, "Run time since start", "min", 1, 2, false, 0.0, 120.0, null) { (it[0] * 256 + it[1]) / 60.0 },
    ) + (0..7).map { i ->
        // Narrow-band oxygen sensors: A is the voltage; B (the sensor's own trim) isn't shown.
        Pid(0x14 + i, o2Name(i, false) + " voltage", "V", 2, 2, false, 0.0, 1.0, null) { it[0] / 200.0 }
    } + listOf(
        Pid(0x22, "Fuel rail pressure (relative)", "kPa", 0, 2, false, 0.0, 5177.0, null) { (it[0] * 256 + it[1]) * 0.079 },
        Pid(0x23, "Fuel rail pressure", "kPa", 0, 2, false, 0.0, 200_000.0, null) { (it[0] * 256 + it[1]) * 10.0 },
    ) + (0..7).map { i ->
        // Wide-band oxygen sensors: AB is lambda (the air-fuel equivalence ratio); CD, the voltage, isn't shown.
        Pid(0x24 + i, o2Name(i, false) + " lambda", "λ", 2, 4, false, 0.7, 1.3, null) { (it[0] * 256 + it[1]) * 2.0 / 65536.0 }
    } + listOf(
        Pid(0x2C, "Commanded EGR", "%", 0, 1, false, 0.0, 100.0, null) { it[0] * 100.0 / 255.0 },
        Pid(0x2D, "EGR error", "%", 1, 1, false, -50.0, 50.0, null) { trim(it[0]) },
        Pid(0x32, "Evap vapour pressure", "Pa", 0, 2, false, -2000.0, 2000.0, null) { b ->
            // Two's complement: the top bit makes it negative.
            val raw = b[0] * 256 + b[1]
            (if (raw >= 0x8000) raw - 0x10000 else raw) / 4.0
        },
        Pid(0x3C, "Catalyst temp, bank 1 sensor 1", "°C", 0, 2, false, 0.0, 1000.0, null) { catalyst(it) },
        Pid(0x3D, "Catalyst temp, bank 2 sensor 1", "°C", 0, 2, false, 0.0, 1000.0, null) { catalyst(it) },
        Pid(0x3E, "Catalyst temp, bank 1 sensor 2", "°C", 0, 2, false, 0.0, 1000.0, null) { catalyst(it) },
        Pid(0x3F, "Catalyst temp, bank 2 sensor 2", "°C", 0, 2, false, 0.0, 1000.0, null) { catalyst(it) },
        Pid(0x42, "Module voltage", "V", 2, 2, false, 10.0, 16.0, null) { (it[0] * 256 + it[1]) / 1000.0 },
        Pid(0x51, "Fuel type", "", 0, 1, false, 0.0, 23.0, null) { it[0].toDouble() },
        Pid(0x52, "Ethanol", "%", 0, 1, false, 0.0, 100.0, null) { it[0] * 100.0 / 255.0 },
        Pid(0x59, "Fuel rail pressure (absolute)", "kPa", 0, 2, false, 0.0, 200_000.0, null) { (it[0] * 256 + it[1]) * 10.0 },
        Pid(0x5E, "Fuel rate", "L/h", 1, 2, false, 0.0, 50.0, null) { (it[0] * 256 + it[1]) / 20.0 },
        Pid(0x61, "Driver demand torque", "%", 0, 1, false, -25.0, 100.0, null) { it[0] - 125.0 },
        Pid(0x62, "Actual torque", "%", 0, 1, false, -25.0, 100.0, null) { it[0] - 125.0 },
        Pid(0x63, "Reference torque", "Nm", 0, 2, false, 0.0, 1000.0, null) { (it[0] * 256 + it[1]).toDouble() },
        Pid(0x78, "Exhaust gas temp, bank 1", "°C", 0, 9, false, 0.0, 1000.0, null) { egt(it) },
        Pid(0x79, "Exhaust gas temp, bank 2", "°C", 0, 9, false, 0.0, 1000.0, null) { egt(it) },
        Pid(0x7F, "Total engine run time", "h", 1, 13, false, 0.0, 10_000.0, null) { b ->
            // B to E: total run time in seconds (A says which of the three totals are given).
            ((b[1].toLong() shl 24) or (b[2].toLong() shl 16) or (b[3].toLong() shl 8) or b[4].toLong()) / 3600.0
        },
    )

    /** Every reading the app polls: the Dashboard's, then [extra]. */
    val all: List<Pid> = dashboard + extra

    /** Readings that are codes shown as words, so they're never graphed or put in a readout. */
    val TEXT_ONLY: Set<String> = setOf("03", "51")

    /** The readings that can be graphed or put in a readout, beyond the Dashboard's. */
    val extraNumbers: List<Pid> = extra.filter { it.hex !in TEXT_ONLY }

    fun byHex(hex: String): Pid? = all.firstOrNull { it.hex == hex }

    /**
     * Oxygen sensor names by position in service 01 (14 to 1B, and 24 to 2B). Most cars list their
     * sensors with PID 13: bank 1 sensors 1 to 4, then bank 2 sensors 1 to 4. A few (large engines
     * with four banks) use PID 1D instead: bank 1 sensors 1 and 2, bank 2 sensors 1 and 2, and so on.
     */
    fun o2Name(index: Int, fourBanks: Boolean): String {
        val (bank, sensor) = if (fourBanks) (index / 2 + 1) to (index % 2 + 1) else (index / 4 + 1) to (index % 4 + 1)
        return "O2 bank $bank sensor $sensor"
    }

    /**
     * Names to use instead of the usual ones for a car whose oxygen sensors are laid out in four
     * banks (it lists PID 1D and not 13). Empty for every other car.
     */
    fun o2Relabels(supported: Set<Int>?): Map<String, String> {
        if (supported == null || 0x1D !in supported || 0x13 in supported) return emptyMap()
        return (0..7).flatMap { i ->
            listOf(
                hex(0x14 + i) to o2Name(i, true) + " voltage",
                hex(0x24 + i) to o2Name(i, true) + " lambda",
            )
        }.toMap()
    }

    /**
     * A short plain-English line on what a reading tells you, for the ones that need it: fuel trims
     * and oxygen sensors. Null for the rest.
     */
    fun about(hex: String): String? {
        val n = hex.toIntOrNull(16) ?: return null
        return when (n) {
            0x06, 0x08 -> "What the engine computer is adding (+) or taking away (−) right now to keep the mixture right. Within about ±10% is normal."
            0x07, 0x09 -> "The correction learnt over time; within about ±10% is normal. Well above: extra air (a leak) or weak fuel supply. Well below: too much fuel."
            in 0x14..0x1B -> if ((n - 0x14) % 4 == 0)
                "When warm, it should flick between about 0.1 V (lean) and 0.9 V (rich) several times a second. A slow or stuck one is worth checking."
            else
                "After the catalytic converter, it should stay fairly steady. If it flicks like the first sensor, the converter may be tired."
            in 0x24..0x2B -> "1.00 is the ideal mixture: below 1 is rich (extra fuel), above 1 lean (extra air). Diesels, and some petrol engines cruising, run lean on purpose."
            else -> null
        }
    }

    private fun hex(n: Int) = String.format(Locale.US, "%02X", n)

    /** Fuel trim and EGR error: 0 is 128, each step 100/128 %. */
    private fun trim(a: Int): Double = a * 100.0 / 128.0 - 100.0

    private fun catalyst(b: IntArray): Double = (b[0] * 256 + b[1]) / 10.0 - 40.0

    /**
     * Exhaust gas temperature: A says which of the bank's four sensors are fitted, then two bytes
     * per sensor. The first fitted sensor (the one nearest the engine) is used; NaN if none is.
     */
    private fun egt(b: IntArray): Double {
        val i = (0..3).firstOrNull { b[0] and (1 shl it) != 0 } ?: return Double.NaN
        return (b[1 + 2 * i] * 256 + b[2 + 2 * i]) / 10.0 - 40.0
    }
}

/** Fuel system status (service 01 PID 03) and fuel type (PID 51), in words. */
object FuelInfo {

    /** The fuel system status, from the first fuel system's code (byte A), in the freeze frame's words. */
    fun status(code: Int): String? = if (code == 0) "Engine off" else FreezeFrame.fuelSystem(intArrayOf(code))

    private val TYPES = listOf(
        null, "Petrol", "Methanol", "Ethanol", "Diesel", "LPG", "CNG", "Propane", "Electric",
        "Bi-fuel, running on petrol", "Bi-fuel, running on methanol", "Bi-fuel, running on ethanol",
        "Bi-fuel, running on LPG", "Bi-fuel, running on CNG", "Bi-fuel, running on propane",
        "Bi-fuel, running on electricity", "Bi-fuel, running on electricity and engine",
        "Hybrid petrol", "Hybrid ethanol", "Hybrid diesel", "Hybrid electric",
        "Hybrid, running on electricity and engine", "Hybrid, regenerating", "Bi-fuel, running on diesel",
    )

    /** The fuel type, or null for "not available" (0) and codes the standard doesn't define here. */
    fun type(code: Int): String? = TYPES.getOrNull(code)

    /** Whether fuel trims mean much right now: only in closed loop does the engine computer adjust them from the sensors. */
    fun trimsActive(code: Int): Boolean = code == 2 || code == 16
}
