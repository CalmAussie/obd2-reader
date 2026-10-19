package com.obdreader.app.obd

/** Plain-English meanings for common generic (SAE) trouble codes. */
object DtcDescriptions {
    private val known = mapOf(
        "P0100" to "Mass air flow circuit malfunction",
        "P0101" to "Mass air flow sensor range/performance",
        "P0113" to "Intake air temp sensor high input",
        "P0117" to "Coolant temp sensor low input",
        "P0118" to "Coolant temp sensor high input",
        "P0128" to "Coolant thermostat below regulating temperature",
        "P0130" to "O2 sensor circuit (bank 1, sensor 1)",
        "P0133" to "O2 sensor slow response (bank 1, sensor 1)",
        "P0135" to "O2 sensor heater circuit (bank 1, sensor 1)",
        "P0141" to "O2 sensor heater circuit (bank 1, sensor 2)",
        "P0171" to "System too lean (bank 1)",
        "P0172" to "System too rich (bank 1)",
        "P0174" to "System too lean (bank 2)",
        "P0175" to "System too rich (bank 2)",
        "P0300" to "Random/multiple cylinder misfire",
        "P0301" to "Cylinder 1 misfire",
        "P0302" to "Cylinder 2 misfire",
        "P0303" to "Cylinder 3 misfire",
        "P0304" to "Cylinder 4 misfire",
        "P0305" to "Cylinder 5 misfire",
        "P0306" to "Cylinder 6 misfire",
        "P0325" to "Knock sensor circuit (bank 1)",
        "P0335" to "Crankshaft position sensor circuit",
        "P0340" to "Camshaft position sensor circuit",
        "P0401" to "EGR flow insufficient",
        "P0420" to "Catalyst efficiency below threshold (bank 1)",
        "P0430" to "Catalyst efficiency below threshold (bank 2)",
        "P0440" to "EVAP system malfunction",
        "P0442" to "EVAP small leak detected",
        "P0455" to "EVAP large leak detected",
        "P0456" to "EVAP very small leak detected",
        "P0500" to "Vehicle speed sensor",
        "P0505" to "Idle air control system",
        "P0562" to "System voltage low",
        "P0563" to "System voltage high",
        "P0700" to "Transmission control system malfunction",
        "U0100" to "Lost communication with engine control module",
    )

    fun describe(code: String): String = known[code] ?: when (code.firstOrNull()) {
        'P' -> if (code.getOrNull(1) == '0') "Powertrain (generic)" else "Powertrain (manufacturer-specific)"
        'C' -> "Chassis"
        'B' -> "Body"
        'U' -> "Network / communication"
        else -> "Unknown"
    }
}
