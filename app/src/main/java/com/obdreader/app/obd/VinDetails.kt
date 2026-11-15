package com.obdreader.app.obd

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * What a VIN says about the car (model, trim, body, engine, where it was built), read from the
 * offline copy of NHTSA's vPIC data that ships inside the app (assets/vpic/vin-data.txt.gz,
 * made by tools/vpic/build_subset.py). The VIN never leaves the phone.
 *
 * It follows vPIC's own decoding: VIN positions 4–8 and 10–17 are matched against the patterns
 * of each VIN schema the manufacturer code (WMI) uses for the model year, and for each detail the
 * newest schema and newest pattern win. Anything the data doesn't cover is left out (shown as
 * N/A), never guessed. Coverage is best for cars sold in North America, which is what vPIC is for.
 */
object VinDetails {

    /** A VIN's details. [values] holds the detail names below, only those the data covers. */
    data class Details(
        val vin: String,
        val make: String?,
        val manufacturer: String?,
        val country: String?,
        val vehicleType: String?,
        val modelYear: Int?,
        val values: Map<String, String>,
    ) {
        val model: String? get() = values[MODEL]
        val trim: String? get() = listOfNotNull(values[TRIM], values[SERIES]).distinct().joinToString(" ").ifEmpty { null }

        /** "2012 Ford Focus SE", from what's known. */
        val title: String get() = listOfNotNull(modelYear?.toString(), make, model, values[TRIM]).joinToString(" ")

        /** "Sedan/Saloon, 4 doors". */
        val body: String? get() = listOfNotNull(values[BODY], values[DOORS]?.let { "$it doors" }).joinToString(", ").ifEmpty { null }

        /** "2.0 L 4-cylinder petrol, turbo, 252 hp" (the engine code is [engineCode]). */
        val engine: String? get() {
            val litres = values[LITRES]?.toDoubleOrNull()?.let { String.format(Locale.US, "%.1f L", it) }
            val cyl = values[CYLINDERS]?.let { c -> if (values[LAYOUT]?.startsWith("V") == true) "V$c" else "$c-cylinder" }
            val size = listOfNotNull(litres, cyl, fuelName(values[FUEL])?.lowercase(Locale.US)?.takeIf { it != "electric" }).joinToString(" ")
            val parts = listOfNotNull(
                size.ifEmpty { null },
                values[ELECTRIFICATION]?.let { electrificationName(it) },
                "turbo".takeIf { values[TURBO] == "Yes" },
                values[HP]?.toDoubleOrNull()?.let { "${it.toInt()} hp" },
            )
            return parts.joinToString(", ").ifEmpty { null }
        }

        /** "Wayne, Michigan, United States". */
        val plant: String? get() = listOfNotNull(values[PLANT_CITY]?.let(::titleCase), values[PLANT_STATE]?.let(::titleCase), values[PLANT_COUNTRY])
            .distinct().joinToString(", ").ifEmpty { null }

        /** The maker's engine (or motor) code, such as 2AZ-FE. */
        val engineCode: String? get() = values[ENGINE]
        val drive: String? get() = values[DRIVE]
        val transmission: String? get() = values[TRANSMISSION]
        val fuel: String? get() = fuelName(values[FUEL])
    }

    const val MODEL = "model"; const val SERIES = "series"; const val TRIM = "trim"; const val BODY = "body"
    const val DOORS = "doors"; const val DRIVE = "drive"; const val CYLINDERS = "cylinders"; const val LITRES = "litres"
    const val LAYOUT = "layout"; const val ENGINE = "engine"; const val FUEL = "fuel"; const val ELECTRIFICATION = "electrification"
    const val TURBO = "turbo"; const val HP = "hp"; const val TRANSMISSION = "transmission"
    const val PLANT_CITY = "plant_city"; const val PLANT_STATE = "plant_state"; const val PLANT_COUNTRY = "plant_country"

    /** vPIC says "Gasoline"; the app says petrol. */
    fun fuelName(v: String?): String? = when {
        v == null -> null
        v.equals("Gasoline", true) -> "Petrol"
        v.startsWith("Flexible Fuel", true) -> "Flex fuel (petrol or ethanol)"
        else -> v
    }

    private fun electrificationName(v: String): String = when {
        v.startsWith("BEV") -> "battery electric"
        v.startsWith("PHEV") -> "plug-in hybrid"
        v.startsWith("Strong HEV") -> "hybrid"
        v.startsWith("Mild HEV") -> "mild hybrid"
        v.startsWith("FCEV") -> "fuel cell"
        else -> v.substringBefore(" (").lowercase(Locale.US)
    }

    private fun titleCase(s: String): String =
        if (s.any { it.isLowerCase() }) s else s.lowercase(Locale.US).split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.US) } }

    /** The manufacturer code: the first 3 characters, or 6 for small makers (a 9 in position 3). */
    fun wmi(vin: String): String = if (vin[2] == '9') vin.substring(0, 3) + vin.substring(11, 14) else vin.substring(0, 3)

    /**
     * The model years position 10 can mean, as vPIC reads it: one if certain, else both. For cars,
     * MPVs and light trucks a digit in position 7 means 1980–2009 and a letter 2010–2039. A year
     * more than 2 years ahead goes back 30 years. Anything else could be either.
     */
    internal fun modelYears(vin: String, carOrLightTruck: Boolean, thisYear: Int): List<Int> {
        val c = vin[9]
        var y = when (c) {
            in 'A'..'H' -> 2010 + (c - 'A')
            in 'J'..'N' -> 2010 + (c - 'A') - 1
            'P' -> 2023
            in 'R'..'T' -> 2010 + (c - 'A') - 3
            in 'V'..'Y' -> 2010 + (c - 'A') - 4
            in '1'..'9' -> 2031 + (c - '1')
            else -> return emptyList()
        }
        var certain = false
        if (carOrLightTruck) {
            if (vin[6].isDigit()) y -= 30
            certain = vin[6].isLetterOrDigit()
        }
        if (y > thisYear + 2) { y -= 30; certain = true }
        return if (certain) listOf(y) else listOf(y, y - 30)
    }

    private class Candidate(val value: String, val yearFrom: Int, val day: Int, val literal: Int, val keys: String, val order: Int, val fromVin: String?)

    private class Wmi(
        val make: String?, val manufacturer: String?, val country: String?, val type: String?,
        val links: List<Triple<Int, Int, Int?>>, val carOrLightTruck: Boolean,
    )

    /** A vehicle spec: [details] vPIC adds when every one of [keys] matches what the VIN gave. */
    private class Spec(val keys: List<Pair<String, Int>>, val details: List<Pair<String, Int>>, val day: Int, val order: Int)

    /**
     * Decodes [vin] (17 characters, already checked) using the data [open] returns. Null if the
     * data has no entry for its manufacturer code.
     */
    fun decode(vin: String, thisYear: Int, open: () -> InputStream): Details? {
        val v = vin.uppercase(Locale.US)
        if (v.length != 17) return null
        val code = wmi(v)
        val wmi = open().use { readWmi(it, code) } ?: return null
        val years = modelYears(v, wmi.carOrLightTruck, thisYear).ifEmpty { listOf(null) }
        // Where the year isn't certain, both are tried and the one that tells more is kept, as vPIC does.
        val best = years.map { y -> open().use { decodeYear(v, y, wmi, it) } }.maxByOrNull { it.values.size } ?: return null
        // Outside North America (VINs starting 1 to 5) position 10 needn't be the year, so a year
        // is only given when the maker's VIN patterns for that year matched.
        return if (v[0] !in '1'..'5' && best.values.isEmpty()) best.copy(modelYear = null) else best
    }

    /**
     * The car to offer for the car choice, from what the VIN says: the app's own make, model and
     * engine names where they match, else the VIN's model name. Null without a make and year.
     */
    fun suggestedCar(d: Details): VehicleCatalog.Car? {
        val year = d.modelYear?.takeIf { it in VehicleCatalog.FIRST_YEAR..VehicleCatalog.LAST_YEAR } ?: return null
        val vinMake = d.make ?: return null
        val make = VehicleCatalog.makeNames.firstOrNull { it != VehicleCatalog.OTHER_MAKE && VinDecoder.sameMake(vinMake, it) } ?: return null
        val model = d.model?.let { m ->
            fun norm(s: String) = s.lowercase(Locale.US).filter { it.isLetterOrDigit() }
            val listed = VehicleCatalog.models(make, year)
            listed.firstOrNull { c -> c.name.split(" / ").any { norm(it) == norm(m) } }?.name ?: m
        }
        val litres = d.values[LITRES]?.toDoubleOrNull()?.let { String.format(Locale.US, "%.1f", it) }
        val hybrid = d.values[ELECTRIFICATION]?.contains("HEV") == true
        val fuel = d.values[FUEL].orEmpty()
        val diesel = fuel.contains("Diesel", true)
        // Only an engine of the same size, hybrid or not and diesel or not as the VIN says, and only if it's the one.
        val engine = litres?.let { size ->
            VehicleCatalog.engines(make, year, model).filter { e ->
                e.name.split(' ').first() == size && (e.fuel == VehicleCatalog.Fuel.HYBRID) == hybrid &&
                    (fuel.isEmpty() || (e.fuel == VehicleCatalog.Fuel.DIESEL) == diesel)
            }.singleOrNull()?.name
        }
        return VehicleCatalog.Car(make, year, model, engine)
    }

    private fun lines(input: InputStream): Sequence<String> =
        BufferedReader(InputStreamReader(GZIPInputStream(input), Charsets.UTF_8), 1 shl 16).lineSequence()

    private fun readWmi(input: InputStream, code: String): Wmi? {
        val prefix = "w\t$code\t"
        val line = lines(input).takeWhile { it.startsWith("w\t") || it.startsWith("#") }.firstOrNull { it.startsWith(prefix) } ?: return null
        val f = line.split('\t')
        val links = f.getOrNull(6).orEmpty().split(',').mapNotNull { l ->
            val p = l.split(':')
            val s = p.getOrNull(0)?.toIntOrNull(); val from = p.getOrNull(1)?.toIntOrNull()
            if (s == null || from == null) null else Triple(s, from, p.getOrNull(2)?.toIntOrNull())
        }
        return Wmi(f[2].ifEmpty { null }, f.getOrNull(3)?.ifEmpty { null }, f.getOrNull(4)?.ifEmpty { null }, f.getOrNull(5)?.ifEmpty { null },
            links, f.getOrNull(7) == "1")
    }

    private fun decodeYear(vin: String, year: Int?, wmi: Wmi, input: InputStream): Details {
        val keys = vin.substring(3, 8) + "|" + vin.substring(9, 17)
        val digitsHidden = keys.map { if (it.isDigit()) '#' else it }.joinToString("")
        // Schema -> the newest model year it's linked from (vPIC's priority for a pattern).
        val schemas = HashMap<Int, Int>()
        for ((s, from, to) in wmi.links) {
            if (year == null || (year >= from && year <= (to ?: 2999))) schemas[s] = maxOf(schemas[s] ?: Int.MIN_VALUE, from)
        }
        val candidates = ArrayList<Candidate>()
        val wanted = HashSet<Int>()
        var schemaFrom: Int? = null
        var day = 0; var first = true; var order = 0; var inSpec = false
        val specs = ArrayList<Spec>()
        val engines = HashMap<String, Map<String, String>>()
        val values = HashMap<Int, Pair<String, String>>()
        val makeOf = HashMap<Int, String>()
        for (line in lines(input)) {
            when {
                line.startsWith("w\t") || line.startsWith("#") -> continue
                line.startsWith("s\t") -> { schemaFrom = schemas[line.substring(2).toIntOrNull() ?: -1]; first = true }
                line.startsWith("e\t") -> {
                    schemaFrom = null
                    val f = line.split('\t')
                    engines[f[1].lowercase(Locale.US)] = (2 until f.size - 1 step 2).associate { f[it] to f[it + 1] }
                }
                line.startsWith("x\t") -> {
                    // A vehicle spec schema: kept if it's for this type, year and a model the VIN may be.
                    schemaFrom = null
                    val f = line.split('\t')
                    val years = f.getOrNull(3).orEmpty()
                    inSpec = f[1] == wmi.type && f[2].split(',').any { it.toIntOrNull() in wanted } &&
                        (years.isEmpty() || (year != null && years.split(',').contains(year.toString())))
                }
                inSpec && !line.startsWith("v\t") -> {
                    val f = line.split('\t')
                    fun pairs(t: String) = t.split(';').mapNotNull { p -> p.substringAfter('=').toIntOrNull()?.let { p.substringBefore('=') to it } }
                    val spec = Spec(pairs(f[0]), pairs(f.getOrElse(1) { "" }), f.getOrNull(2)?.toIntOrNull() ?: 0, order++)
                    specs += spec
                    spec.keys.forEach { wanted += it.second }; spec.details.forEach { wanted += it.second }
                }
                line.startsWith("v\t") -> {
                    val f = line.split('\t')
                    val i = f[1].toIntOrNull() ?: continue
                    if (i in wanted) { values[i] = f[2] to f[3]; f.getOrNull(4)?.let { makeOf[i] = it } }
                }
                schemaFrom != null -> {
                    val tab = line.indexOf('\t'); val tab2 = line.indexOf('\t', tab + 1)
                    val k = line.substring(0, tab)
                    val value = if (tab2 < 0) line.substring(tab + 1) else line.substring(tab + 1, tab2)
                    val step = if (tab2 < 0) 0 else line.substring(tab2 + 1).toIntOrNull() ?: 0
                    day = if (first) step else day - step
                    first = false
                    order++
                    val fromVin = if (value.startsWith("#")) {
                        if (!matches(k, digitsHidden)) continue
                        val a = k.indexOf('#'); val b = k.lastIndexOf('#')
                        keys.substring(a, minOf(b + 1, keys.length))
                    } else {
                        if (!matches(k, keys)) continue
                        null
                    }
                    candidates += Candidate(value, schemaFrom!!, day, k.count { it != '*' }, k, order, fromVin)
                    value.toIntOrNull()?.let { wanted += it }
                }
            }
        }
        // Per detail: newest schema, then newest pattern, then fewest fixed characters, then keys, then file order.
        val best = HashMap<String, Pair<Candidate, String>>()
        val cmp = compareByDescending<Candidate> { it.yearFrom }.thenByDescending { it.day }.thenBy { it.literal }.thenBy { it.keys }.thenBy { it.order }
        for (c in candidates) {
            val (element, text) = if (c.fromVin != null) c.value.substring(1) to c.fromVin else values[c.value.toIntOrNull()] ?: continue
            val cur = best[element]
            if (cur == null || cmp.compare(c, cur.first) < 0) best[element] = c to text
        }
        val out = HashMap(best.mapValues { it.value.second })
        // Then vehicle specs fill in details the VIN patterns didn't give, newest first, but only
        // where every key (say the trim) matches what the VIN gave.
        // What vPIC knows about the engine code fills in engine details the patterns didn't give.
        out[ENGINE]?.let { engines[it.trim().lowercase(Locale.US)] }?.forEach { (element, text) -> out.putIfAbsent(element, text) }
        val added = HashMap<String, Spec>()
        val given = HashMap(out)
        for (spec in specs) {
            val keysMatch = spec.keys.all { (element, i) -> values[i]?.second?.let { given[element].equals(it, ignoreCase = true) } == true }
            if (!keysMatch) continue
            for ((element, i) in spec.details) {
                if (element in given) continue
                val text = values[i]?.second ?: continue
                val cur = added[element]
                if (cur == null || spec.day > cur.day || (spec.day == cur.day && spec.order < cur.order)) { added[element] = spec; out[element] = text }
            }
        }
        val make = best[MODEL]?.first?.value?.toIntOrNull()?.let { makeOf[it] } ?: wmi.make
        return Details(vin, make, wmi.manufacturer, wmi.country, wmi.type, year, out)
    }

    /**
     * vPIC pattern keys against the 14-character "VDS|VIS" string, from the start: * is any
     * character, [..] a set (with ranges like A-D), anything else itself. Extra characters at
     * the end of the VIN don't matter.
     */
    internal fun matches(pattern: String, keys: String): Boolean {
        var i = 0; var pos = 0
        while (i < pattern.length) {
            if (pos >= keys.length) return false
            val c = keys[pos]
            when (val p = pattern[i]) {
                '*' -> i++
                '[' -> {
                    val end = pattern.indexOf(']', i)
                    if (end < 0) return false
                    // vPIC reads "1-A" as the two characters 1 and A, not a range.
                    val set = pattern.substring(i + 1, end).replace("1-A", "1A")
                    if (!inSet(set, c)) return false
                    i = end + 1
                }
                else -> { if (p != c) return false; i++ }
            }
            pos++
        }
        return true
    }

    private fun inSet(set: String, c: Char): Boolean {
        var j = 0
        while (j < set.length) {
            if (j + 2 < set.length && set[j + 1] == '-') {
                if (c in set[j]..set[j + 2]) return true
                j += 3
            } else {
                if (set[j] == c) return true
                j++
            }
        }
        return false
    }
}
