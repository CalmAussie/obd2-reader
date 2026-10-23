package com.obdreader.app.obd

import java.util.Locale

/** Reads the make and (where the VIN encodes it) the model year from a 17-character VIN. */
object VinDecoder {

    data class Info(val make: String?, val modelYear: Int?)

    private const val FORD = "Ford"
    private const val LINCOLN = "Lincoln (Ford)"
    private const val MERCURY = "Mercury (Ford)"
    private const val MAZDA = "Mazda"
    private const val HONDA = "Honda"
    private const val ACURA = "Acura (Honda)"
    private const val TOYOTA = "Toyota"
    private const val LEXUS = "Lexus (Toyota)"
    private const val BMW = "BMW"
    private const val MERCEDES = "Mercedes-Benz"

    /** World manufacturer identifiers (first three VIN characters). */
    private val WMI: Map<String, String> = buildMap {
        listOf(
            "1FA", "1FB", "1FC", "1FD", "1FM", "1FT", "1ZV", "2FA", "2FM", "2FT", "3FA", "3FM", "3FT",
            "6FP", "WF0", "SFA", "MAJ", "MNB", "NM0", "8AF", "9BF", "LVS",
        ).forEach { put(it, FORD) }
        listOf("1LN", "2LM", "3LN", "5LM").forEach { put(it, LINCOLN) }
        listOf("1ME", "2ME", "4M2").forEach { put(it, MERCURY) }
        listOf("JM0", "JM1", "JM3", "JMZ", "4F2", "4F4", "MM0", "MM6", "MM7", "MM8").forEach { put(it, MAZDA) }
        listOf("1HG", "2HG", "2HJ", "2HK", "3HG", "5FN", "5J6", "JHM", "JHL", "SHH", "SHS", "MRH", "93H", "19X").forEach { put(it, HONDA) }
        listOf("JH4", "19U", "2HN", "5J8").forEach { put(it, ACURA) }
        listOf(
            "JT2", "JT3", "JT4", "JTD", "JTE", "JTK", "JTL", "JTM", "JTN", "4T1", "4T3", "4T4", "5TD", "5TE", "5TF",
            "2T1", "2T3", "MR0", "MR1", "MR2", "6T1", "SB1", "VNK", "AHT", "NMT",
        ).forEach { put(it, TOYOTA) }
        listOf("JTH", "JTJ", "2T2").forEach { put(it, LEXUS) }
        listOf("WBA", "WBS", "WBX", "WBY", "4US", "5UX", "5YM").forEach { put(it, BMW) }
        mapOf(
            "Chevrolet" to listOf("1G1", "1GC", "1GN", "1GB", "2G1", "3G1", "3GN", "3GC", "KL1", "6G3"),
            "GMC" to listOf("1GT", "1GK", "1GD", "2GT", "3GT", "3GK"),
            "Cadillac" to listOf("1G6", "1GY"),
            "Buick" to listOf("1G4", "2G4", "5GA", "KL4"),
            "Pontiac" to listOf("1G2", "2G2", "5Y2", "6G2"),
            "Saturn" to listOf("1G8"),
            "Hummer" to listOf("5GR", "5GT"),
            "Holden" to listOf("6H8", "6G1"),
            "Chrysler" to listOf("1C3", "2C3"),
            "Dodge" to listOf("1B3", "2B3", "1B4", "1B7", "1D7", "3D7", "1D4", "2D4", "3D4"),
            "Jeep" to listOf("1J4", "1J8", "ZAC"),
            "Chrysler, Dodge or Jeep" to listOf("1C4", "2C4"),
            "Ram" to listOf("1C6", "3C6", "3C7"),
            "Fiat" to listOf("ZFA"),
            "Alfa Romeo" to listOf("ZAR"),
            "Maserati" to listOf("ZAM"),
            "Lancia" to listOf("ZLA"),
            "Peugeot" to listOf("VF3"),
            "Citroën" to listOf("VF7"),
            "DS" to listOf("VR1"),
            "Opel / Vauxhall" to listOf("W0L", "W0V"),
            "Saab" to listOf("YS3"),
            "Volkswagen" to listOf("WVW", "WVG", "WV1", "WV2", "1VW", "3VW", "3VV", "1V2", "9BW", "AAV"),
            "Audi" to listOf("WAU", "WA1", "WUA", "TRU"),
            "Skoda" to listOf("TMB"),
            "SEAT / Cupra" to listOf("VSS"),
            "Porsche" to listOf("WP0", "WP1"),
            "Lamborghini" to listOf("ZHW"),
            "Bentley" to listOf("SCB"),
            "Nissan" to listOf("JN1", "JN8", "1N4", "1N6", "3N1", "5N1", "SJN", "VSK"),
            "Infiniti" to listOf("JNK", "JNR", "5N3"),
            "Subaru" to listOf("JF1", "JF2", "4S3", "4S4"),
            "Mitsubishi" to listOf("JA3", "JA4", "JMB", "JMY", "4A3", "4A4", "6MM", "MMB"),
            "Suzuki" to listOf("JS2", "JS3", "JSA", "TSM", "MA3"),
            "Isuzu" to listOf("JAA", "JAC", "4S2", "MPA"),
            "Daihatsu" to listOf("JDA"),
            "Oldsmobile" to listOf("1G3"),
            "Plymouth" to listOf("1P3", "1P4", "2P4"),
            "Tesla" to listOf("5YJ", "7SA", "LRW", "XP7"),
            "Rivian" to listOf("7FC"),
            "Lucid" to listOf("50E"),
            "Mini" to listOf("WMW"),
            "Rolls-Royce" to listOf("SCA"),
            "Smart" to listOf("WME"),
            "Volvo" to listOf("YV1", "YV4", "7JR", "LVY"),
            "Polestar" to listOf("LPS"),
            "Jaguar" to listOf("SAJ"),
            "Land Rover" to listOf("SAL"),
            "Renault" to listOf("VF1"),
            "Dacia" to listOf("UU1"),
            "Aston Martin" to listOf("SCF"),
            "Ferrari" to listOf("ZFF"),
            "McLaren" to listOf("SBM"),
            "Lotus" to listOf("SCC"),
            "Bugatti" to listOf("VF9"),
            "Rover / MG Rover" to listOf("SAR"),
            "MG" to listOf("LSJ"),
            "Hyundai" to listOf("KMH", "KMF", "5NP", "5NM", "MAL", "TMA", "NLH"),
            "Kia" to listOf("KNA", "KNC", "KND", "KNE", "5XX", "5XY", "U5Y"),
            "Genesis" to listOf("KMT"),
            "SsangYong / KGM" to listOf("KPT"),
            "BYD" to listOf("LGX", "LC0"),
            "GWM / Haval" to listOf("LGW"),
        ).forEach { (make, codes) -> codes.forEach { put(it, make) } }
        listOf("WDB", "WDC", "WDD", "WDF", "WD3", "WD4", "4JG", "55S", "W1K", "W1N", "W1V").forEach { put(it, MERCEDES) }
    }

    /** Model-year codes for VIN position 10, starting at 1980. I, O, Q, U, Z and 0 are never used. */
    private const val YEAR_CODES = "ABCDEFGHJKLMNPRSTVWXY123456789"

    /** True if a make from the VIN and a make from the car list are the same brand ("Lexus (Toyota)" and "Lexus"). */
    fun sameMake(a: String, b: String): Boolean {
        fun names(s: String) = s.substringBefore(" (").split("/").map { it.trim().lowercase(Locale.US) }.filter { it.isNotEmpty() }.toSet()
        return names(a).intersect(names(b)).isNotEmpty()
    }

    /**
     * A believable VIN for [make] and [year], with a valid check digit. The demo car reports it,
     * so the demo matches the car the user chose.
     */
    fun sampleVin(make: String?, year: Int?): String {
        val wmi = make?.let { m ->
            WMI.entries.filter { sameMake(it.value, m) }.map { it.key }.sortedBy { if (it[0] in '1'..'5') 0 else 1 }.firstOrNull()
        } ?: "1FA"
        val y = (year ?: 2012).coerceIn(1980, 2039)
        val yearCode = YEAR_CODES[(y - 1980) % 30]
        // Position 7 is a letter for 2010 to 2039 and a digit for 1980 to 2009 (North American rule).
        val pos7 = if (y >= 2010) 'F' else '2'
        val chars = (wmi + "HP3" + pos7 + "2" + "0" + yearCode + "L123456").toCharArray()
        chars[8] = checkDigit(String(chars))
        return String(chars)
    }

    /** The standard VIN check digit (position 9). */
    internal fun checkDigit(vin: String): Char {
        val weights = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)
        fun value(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in "AJ" -> 1; in "BKS" -> 2; in "CLT" -> 3; in "DMU" -> 4; in "ENV" -> 5
            in "FW" -> 6; in "GPX" -> 7; in "HY" -> 8; in "RZ" -> 9
            else -> 0
        }
        val sum = vin.indices.sumOf { value(vin[it]) * weights[it] }
        val r = sum % 11
        return if (r == 10) 'X' else ('0' + r)
    }

    /**
     * True if [vin] is 17 VIN characters and, for North American VINs (starting 1 to 5), its check
     * digit matches. Only North America requires the check digit on every car: cars built for
     * other markets, including many built in China (starting L), may have any character there,
     * so they pass on the characters alone.
     */
    fun isValid(vin: String): Boolean {
        val v = normalise(vin) ?: return false
        return v[0] !in '1'..'5' || checkDigit(v) == v[8]
    }

    /** A VIN typed in by the user, cleaned up (capitals, no spaces), or null if it can't be a VIN. */
    fun normalise(input: String): String? {
        val v = input.uppercase(Locale.US).filter { !it.isWhitespace() && it != '-' }
        return if (v.length == 17 && v.all { (it in 'A'..'Z' || it in '0'..'9') && it !in "IOQ" }) v else null
    }

    fun decode(vin: String): Info {
        val v = vin.trim().uppercase(Locale.US)
        // A VIN that fails its check is likely misread, so no make or year is taken from it.
        if (!isValid(v)) return Info(null, null)
        return Info(WMI[v.substring(0, 3)], modelYear(v))
    }

    /**
     * Only North American VINs (starting 1–5) reliably encode the year. There a
     * letter in position 7 means the 2010–2039 cycle, a digit means 1980–2009.
     */
    private fun modelYear(v: String): Int? {
        if (v[0] !in '1'..'5') return null
        val i = YEAR_CODES.indexOf(v[9])
        if (i < 0) return null
        return 1980 + i + if (v[6].isLetter()) 30 else 0
    }
}
