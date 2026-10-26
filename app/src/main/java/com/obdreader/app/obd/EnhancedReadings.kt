package com.obdreader.app.obd

import java.util.Locale

/**
 * Enhanced diagnostics: readings the car's makers put beyond standard OBD2, read with the two
 * "read data" services (22 ReadDataByIdentifier, 21 ReadDataByLocalIdentifier), and GM's 1A on
 * GM-family cars. All of them only read.
 *
 * Makers don't publish these, so each reading here comes from owners testing them on their own
 * cars (community vehicle profiles); a car only shows the ones it answers, and the app says that
 * they're community-sourced. Each is tied to the makes, and where it's known only on some, the
 * models, it was found on, so a request is never sent to a car whose maker uses that number for
 * something else.
 */
object EnhancedReadings {

    data class Reading(
        val id: String,
        val name: String,
        /** The module that answers, as the user sees it ("Engine computer", "Body module"). */
        val module: String,
        /** Its address (null: the car's default OBD2 address) and the address it answers from. */
        val header: String?,
        val reply: String?,
        /** The request, e.g. "221E1C". */
        val request: String,
        val unit: String,
        val decimals: Int,
        /** The value from the answer's data bytes (after the reply's service and identifier). */
        val decode: (IntArray) -> Double?,
    ) {
        /** The answer starts with the service + 0x40 and the identifier: "221E1C" → "621E1C". */
        val replyHeader: String get() = String.format(Locale.US, "%02X", request.substring(0, 2).toInt(16) + 0x40) + request.substring(2)

        fun format(v: Double): String =
            String.format(Locale.US, "%,.${decimals}f", v) + if (unit.isEmpty()) "" else if (unit == "%") "%" else " $unit"
    }

    // Byte helpers: big-endian unsigned and signed values from the answer's data bytes.
    private fun u(b: IntArray, i: Int, n: Int): Double? {
        if (i < 0 || i + n > b.size) return null
        var v = 0L
        for (k in 0 until n) v = (v shl 8) or (b[i + k].toLong() and 0xFF)
        return v.toDouble()
    }
    private fun s(b: IntArray, i: Int, n: Int): Double? = u(b, i, n)?.let { v ->
        val full = Math.pow(2.0, 8.0 * n); if (v >= full / 2) v - full else v
    }

    private fun r(id: String, name: String, module: String, header: String?, request: String, unit: String, decimals: Int, decode: (IntArray) -> Double?) =
        Reading(id, name, module, header, header?.let { replyFor(it) }, request, unit, decimals, decode)

    /** The usual answer address: + 8 for 11-bit addresses. */
    private fun replyFor(h: String): String = String.format(Locale.US, "%03X", h.toInt(16) + 8)

    private const val PCM = "Engine computer"

    /** Ford's (and the Ford-built Mazdas') engine computer and body module. */
    private val FORD = listOf(
        r("ford.trans_temp", "Transmission fluid temperature", PCM, "7E0", "221E1C", "°C", 1) { b -> s(b, 0, 2)?.div(16) },
        r("ford.gear", "Gear engaged", PCM, "7E0", "221E12", "", 0) { b -> u(b, 0, 1) },
        r("ford.oil_life", "Oil life remaining", PCM, "7E0", "22054B", "%", 0) { b -> u(b, 0, 1) },
        r("ford.fuel_pump", "Fuel pump duty", PCM, "7E0", "220307", "%", 0) { b -> u(b, 0, 1)?.times(100)?.div(255) },
        r("ford.wastegate", "Turbo wastegate", PCM, "7E0", "220462", "%", 0) { b -> u(b, 0, 1)?.times(100)?.div(128) },
        r("ford.odometer", "Odometer", "Body module", "726", "22DD01", "km", 0) { b -> u(b, 0, 3) },
        r("ford.batt_charge", "12 V battery charge", "Body module", "726", "224028", "%", 0) { b -> u(b, 0, 1) },
        r("ford.batt_temp", "12 V battery temperature", "Body module", "726", "224029", "°C", 0) { b -> u(b, 0, 1)?.minus(40) },
    )

    private val TOYOTA = listOf(
        r("toyota.odometer", "Odometer", PCM, "7E0", "2128", "km", 0) { b -> u(b, 0, 3) },
        r("toyota.oil_temp", "Engine oil temperature", PCM, "7E0", "2151", "°C", 0) { b -> u(b, 11, 1)?.minus(40) },
        r("toyota.fuel_litres", "Fuel in the tank", "Instrument cluster", "7C0", "2129", "L", 1) { b -> u(b, 0, 1)?.div(2) },
    )

    private val SUBARU = listOf(
        r("subaru.trans_temp", "Transmission fluid temperature", "Transmission", "7A3", "2210D2", "°C", 0) { b -> u(b, 0, 1)?.minus(50) },
        r("subaru.maf", "Airflow (engine computer)", PCM, "7A2", "2212ED", "g/s", 2) { b -> u(b, 0, 2)?.div(100) },
    )

    /** Nissan Leaf, 2011–2017 (24 and 30 kWh): the battery controller at 79B. */
    private val LEAF = listOf(
        r("leaf.soc", "Battery charge (state of charge)", "Battery controller", "79B", "2101", "%", 1) { b -> u(b, 34, 3)?.div(10000) },
        r("leaf.capacity", "Battery capacity", "Battery controller", "79B", "2101", "Ah", 1) { b -> u(b, 39, 3)?.div(10000) },
        r("leaf.hv_volts", "Battery voltage", "Battery controller", "79B", "2101", "V", 1) { b -> u(b, 22, 2)?.div(100) },
        r("leaf.lv_volts", "12 V battery", "Battery controller", "79B", "2101", "V", 2) { b -> u(b, 24, 2)?.div(1000) },
        r("leaf.temp1", "Battery temperature", "Battery controller", "79B", "2104", "°C", 0) { b -> s(b, 3, 1) },
    ).map { it.copy(reply = "7BB") }

    /** Renault Zoe R90 and R110 (2013–2019), on the car's own OBD2 address. */
    private val ZOE = listOf(
        r("zoe.soc", "Battery charge (state of charge)", "Electric vehicle controller", null, "222002", "%", 1) { b -> u(b, 0, 2)?.times(0.02) },
        r("zoe.soh", "Battery health", "Electric vehicle controller", null, "223206", "%", 0) { b -> u(b, 0, 1) },
        r("zoe.range", "Range shown", "Electric vehicle controller", null, "223451", "km", 0) { b -> u(b, 0, 2) },
        r("zoe.odometer", "Odometer", "Electric vehicle controller", null, "222006", "km", 0) { b -> u(b, 0, 3) },
        r("zoe.batt_temp", "Battery temperature", "Electric vehicle controller", null, "222001", "°C", 0) { b -> u(b, 0, 1)?.minus(40) },
    )

    /** Opel and Vauxhall: GM's own read service, 1A, on the engine computer. */
    private val OPEL = listOf(
        r("opel.oil_life", "Oil life remaining", PCM, "7E0", "1A6D", "%", 0) { b -> u(b, 0, 1)?.div(2.55) },
    )

    private fun mk(make: String?) = make?.substringBefore(" (")?.trim()?.lowercase(Locale.US).orEmpty()
    private fun md(model: String?) = model?.trim()?.lowercase(Locale.US).orEmpty()

    /** The readings to try on this car: those found on its make (and model, where they're model-specific). */
    fun forCar(make: String?, model: String?, year: Int?): List<Reading> {
        val m = mk(make); val mo = md(model)
        val list = mutableListOf<Reading>()
        val fordBuiltMazda = m == "mazda" && (mo == "tribute" || (mo == "bt-50" && (year ?: 0) >= 2011))
        if (m in setOf("ford", "lincoln", "mercury") || fordBuiltMazda) list += FORD
        if (m in setOf("toyota", "lexus", "scion")) list += TOYOTA
        if (m == "subaru") list += SUBARU
        if (m == "nissan" && mo == "leaf" && (year == null || year <= 2017)) list += LEAF
        if (m == "renault" && mo == "zoe" && (year == null || year <= 2019)) list += ZOE
        if (m == "opel / vauxhall" || m == "opel" || m == "vauxhall") list += OPEL
        return list
    }

    /** Every reading, for tests. */
    val ALL: List<Reading> get() = FORD + TOYOTA + SUBARU + LEAF + ZOE + OPEL

    /**
     * Module information: the identifiers every UDS module is meant to answer (ISO 14229), so it
     * works whatever the make. Part numbers and software versions are what a parts counter or a
     * dealer asks for, and the VIN each module holds shows which modules were swapped or
     * programmed with another car's VIN.
     */
    data class Identifier(val did: String, val name: String)

    val IDENTIFIERS = listOf(
        Identifier("F197", "System"),
        Identifier("F187", "Part number"),
        Identifier("F18A", "Supplier"),
        Identifier("F191", "Hardware number"),
        Identifier("F189", "Software version"),
        Identifier("F18C", "Serial number"),
        Identifier("F190", "VIN stored in the module"),
    )

    /** An identifier's text from the answer bytes, printable characters only; null when it's empty. */
    fun identifierText(bytes: IntArray?): String? {
        if (bytes == null) return null
        val printable = bytes.filter { it in 0x20..0x7E }
        // Some modules answer with raw numbers rather than text: show those as hex.
        val text = if (printable.size >= bytes.size / 2 && printable.isNotEmpty()) printable.map { Char(it) }.joinToString("").trim()
        else bytes.joinToString("") { String.format(Locale.US, "%02X", it) }
        return text.ifEmpty { null }
    }
}
