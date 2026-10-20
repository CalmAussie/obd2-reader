package com.obdreader.app.obd

import java.util.Locale

/**
 * Getting more out of cars that leave standard readings out.
 *
 * - **Stand-ins:** when a car doesn't report a dashboard reading but reports a close standard
 *   equivalent, the app uses that instead: accelerator pedal position for throttle (common on
 *   diesels and some newer petrol cars) and the newer coolant sensor reading for coolant.
 * - **Car scan:** every standard reading the car says it supports, by name, and a read-only
 *   probe of known manufacturer (enhanced) readings, so missing ones can be found and added.
 */
object CarCoverage {

    /** A standard reading that can stand in for a dashboard reading the car doesn't report. */
    data class StandIn(val forHex: String, val pid: Pid, val note: String)

    val STAND_INS: List<StandIn> = listOf(
        StandIn("11", Pid(0x49, "Accelerator pedal", "%", 0, 1, true, 0.0, 100.0, null) { it[0] * 100.0 / 255.0 }, "accelerator pedal position, in place of throttle"),
        StandIn("05", Pid(0x67, "Coolant (sensor)", "°C", 0, 3, false, 40.0, 120.0, 108.0) { b ->
            // Byte A says which sensors are fitted; B is sensor 1, C sensor 2.
            if (b[0] and 1 != 0) b[1] - 40.0 else b[2] - 40.0
        }, "the newer coolant sensor reading, in place of coolant temperature"),
    )

    /** Stand-ins to use, given what the car supports. */
    fun standInsFor(supported: Set<Int>): List<StandIn> = STAND_INS.filter { s ->
        Pids.dashboard.first { it.hex == s.forHex }.number !in supported && s.pid.number in supported
    }

    /** A manufacturer reading worth trying, read-only (service 22). */
    data class Enhanced(val name: String, val request: String, val header: String?, val note: String)

    /**
     * Manufacturer readings to probe, by make. These come from enthusiast PID lists, differ
     * between models and years, and many cars won't answer them; the scan only reports which
     * ones answered and what they sent, so the right ones can be added for a car.
     */
    fun enhancedFor(make: String?, model: String?, protocolCode: String?): List<Enhanced> {
        // Ford's own makes, plus the Mazdas Ford built (Tribute, B-Series); other Mazdas have Mazda electronics.
        val m = model?.lowercase(java.util.Locale.US).orEmpty()
        val ford = make in setOf("Ford", "Lincoln", "Mercury") || (make == "Mazda" && (m.startsWith("tribute") || m.startsWith("b-series")))
        if (!ford) return emptyList()
        val pwm = protocolCode == "1"
        val pcm = if (pwm) "C410F1" else if (protocolCode in setOf("6", "8")) "7E0" else null
        return listOf(
            Enhanced("Engine oil temperature", "221310", pcm, "Ford engine computer"),
            Enhanced("Transmission fluid temperature", "221674", pcm, "Ford engine computer, later models"),
            Enhanced("Transmission fluid temperature", "2211BD", pcm, "Ford engine computer, older models"),
            Enhanced("Transmission fluid temperature", "221E1C", if (pwm) null else "7E0", "Ford, CAN models"),
            Enhanced("Fuel level", "22F42F", if (pwm) null else "7E0", "Standard reading through the manufacturer service (CAN cars)"),
        ).filter { it.header != null || !pwm }
    }

    /** The adapter's normal header for a protocol, to put back after a probe. */
    fun defaultHeader(protocolCode: String?): String? = when (protocolCode) {
        "1" -> "616AF1"
        "2" -> "686AF1"
        "3" -> "686AF1"
        "4", "5" -> "C133F1"
        "6", "8" -> "7DF"
        "7", "9" -> "18DB33F1"
        else -> null
    }

    /** Standard OBD2 reading names (SAE J1979, service 01), for the car scan. */
    private val NAMES = mapOf(
        0x01 to "Monitor status since codes cleared", 0x02 to "Freeze frame code", 0x03 to "Fuel system status",
        0x04 to "Engine load", 0x05 to "Coolant temperature", 0x06 to "Short-term fuel trim, bank 1",
        0x07 to "Long-term fuel trim, bank 1", 0x08 to "Short-term fuel trim, bank 2", 0x09 to "Long-term fuel trim, bank 2",
        0x0A to "Fuel pressure", 0x0B to "Manifold pressure", 0x0C to "Engine RPM", 0x0D to "Speed",
        0x0E to "Timing advance", 0x0F to "Intake air temperature", 0x10 to "MAF airflow", 0x11 to "Throttle position",
        0x12 to "Secondary air status", 0x13 to "Oxygen sensors present", 0x14 to "Oxygen sensor 1", 0x15 to "Oxygen sensor 2",
        0x16 to "Oxygen sensor 3", 0x17 to "Oxygen sensor 4", 0x18 to "Oxygen sensor 5", 0x19 to "Oxygen sensor 6",
        0x1A to "Oxygen sensor 7", 0x1B to "Oxygen sensor 8", 0x1C to "OBD standard", 0x1D to "Oxygen sensors present (4 banks)",
        0x1E to "Auxiliary input status", 0x1F to "Run time since engine start", 0x21 to "Distance with check-engine light on",
        0x22 to "Fuel rail pressure (relative)", 0x23 to "Fuel rail pressure", 0x24 to "Oxygen sensor 1 (wide range)",
        0x25 to "Oxygen sensor 2 (wide range)", 0x26 to "Oxygen sensor 3 (wide range)", 0x27 to "Oxygen sensor 4 (wide range)",
        0x28 to "Oxygen sensor 5 (wide range)", 0x29 to "Oxygen sensor 6 (wide range)", 0x2A to "Oxygen sensor 7 (wide range)",
        0x2B to "Oxygen sensor 8 (wide range)", 0x2C to "Commanded EGR", 0x2D to "EGR error", 0x2E to "Commanded evaporative purge",
        0x2F to "Fuel level", 0x30 to "Warm-ups since codes cleared", 0x31 to "Distance since codes cleared",
        0x32 to "Evap system vapour pressure", 0x33 to "Barometric pressure", 0x34 to "Oxygen sensor 1 (current)",
        0x35 to "Oxygen sensor 2 (current)", 0x36 to "Oxygen sensor 3 (current)", 0x37 to "Oxygen sensor 4 (current)",
        0x38 to "Oxygen sensor 5 (current)", 0x39 to "Oxygen sensor 6 (current)", 0x3A to "Oxygen sensor 7 (current)",
        0x3B to "Oxygen sensor 8 (current)", 0x3C to "Catalyst temperature, bank 1 sensor 1", 0x3D to "Catalyst temperature, bank 2 sensor 1",
        0x3E to "Catalyst temperature, bank 1 sensor 2", 0x3F to "Catalyst temperature, bank 2 sensor 2", 0x41 to "Monitor status this drive cycle",
        0x42 to "Control module voltage", 0x43 to "Absolute engine load", 0x44 to "Commanded air-fuel ratio",
        0x45 to "Relative throttle position", 0x46 to "Ambient air temperature", 0x47 to "Throttle position B",
        0x48 to "Throttle position C", 0x49 to "Accelerator pedal position D", 0x4A to "Accelerator pedal position E",
        0x4B to "Accelerator pedal position F", 0x4C to "Commanded throttle", 0x4D to "Time with check-engine light on",
        0x4E to "Time since codes cleared", 0x4F to "Maximum values (air-fuel, oxygen sensor, MAP)", 0x50 to "Maximum MAF airflow",
        0x51 to "Fuel type", 0x52 to "Ethanol fuel %", 0x53 to "Evap system vapour pressure (absolute)",
        0x54 to "Evap system vapour pressure (wide)", 0x55 to "Short-term secondary oxygen trim, banks 1 and 3",
        0x56 to "Long-term secondary oxygen trim, banks 1 and 3", 0x57 to "Short-term secondary oxygen trim, banks 2 and 4",
        0x58 to "Long-term secondary oxygen trim, banks 2 and 4", 0x59 to "Fuel rail pressure (absolute)",
        0x5A to "Relative accelerator pedal position", 0x5B to "Hybrid battery remaining life", 0x5C to "Engine oil temperature",
        0x5D to "Fuel injection timing", 0x5E to "Engine fuel rate", 0x5F to "Emission requirements",
        0x61 to "Driver's demanded torque", 0x62 to "Actual engine torque", 0x63 to "Reference engine torque",
        0x64 to "Engine torque data", 0x65 to "Auxiliary inputs and outputs", 0x66 to "MAF sensor",
        0x67 to "Coolant temperature (sensors)", 0x68 to "Intake air temperature (sensors)", 0x6B to "EGR temperature",
        0x6C to "Commanded throttle and position", 0x6D to "Fuel pressure control", 0x6E to "Injection pressure control",
        0x6F to "Turbo compressor inlet pressure", 0x70 to "Boost pressure control", 0x71 to "Variable geometry turbo control",
        0x72 to "Wastegate control", 0x73 to "Exhaust pressure", 0x74 to "Turbo speed", 0x75 to "Turbo temperature (A)",
        0x76 to "Turbo temperature (B)", 0x77 to "Charge air cooler temperature", 0x78 to "Exhaust gas temperature, bank 1",
        0x79 to "Exhaust gas temperature, bank 2", 0x7A to "Diesel particulate filter pressure (bank 1)",
        0x7B to "Diesel particulate filter pressure (bank 2)", 0x7C to "Diesel particulate filter temperature",
        0x7D to "NOx emission limit status", 0x7E to "PM emission limit status", 0x7F to "Engine run time",
        0x83 to "NOx sensor", 0x84 to "Manifold surface temperature", 0x85 to "NOx reagent system", 0x86 to "Particulate matter sensor",
        0x87 to "Intake manifold pressure (absolute)", 0x8D to "Throttle position G", 0x8E to "Engine friction torque",
        0x9D to "Engine fuel rate (multiple)", 0x9E to "Engine exhaust flow rate", 0xA4 to "Transmission gear", 0xA6 to "Odometer",
    )

    fun name(pid: Int): String = NAMES[pid] ?: String.format(Locale.US, "Reading %02X", pid)

    /** Whether the app shows this reading (directly or through a stand-in). */
    fun shownByApp(pid: Int): Boolean = Pids.dashboard.any { it.number == pid } || STAND_INS.any { it.pid.number == pid }
}
