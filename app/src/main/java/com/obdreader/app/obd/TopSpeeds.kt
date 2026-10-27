package com.obdreader.app.obd

/**
 * Each car's approximate top speed, for the widget's "near the maximum" highlight. The figures are
 * published specs (see topspeeds.txt), matched to the car list's engines; for a car whose engine
 * isn't chosen, the fastest version of that model's generation. Null when the car isn't covered,
 * and then nothing is highlighted for speed rather than guessing.
 */
object TopSpeeds {

    private data class Entry(val make: String, val model: String, val engine: String?, val from: Int, val to: Int, val kmh: Int)

    private val entries: List<Entry> by lazy {
        val text = TopSpeeds::class.java.getResourceAsStream("topspeeds.txt")?.bufferedReader()?.readText().orEmpty()
        val generation = Regex("""^(.+?) \| (.+?) \| \* (\d{4})-(\d{4}) (\d+)$""")
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { line ->
            generation.find(line)?.let { m ->
                // "Make | Model | * 2001-2006 190": a model generation's fastest version.
                return@mapNotNull Entry(m.groupValues[1], m.groupValues[2], null, m.groupValues[3].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt())
            }
            // "Make | Model | engine | 171": one engine.
            val p = line.split(" | ")
            if (p.size != 4) null else p[3].toIntOrNull()?.let { Entry(p[0], p[1], p[2], 0, 9999, it) }
        }
    }

    private fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    /** The top speed in km/h for [car] and whether it's for its engine (true) or the model's fastest (false); null if not covered. */
    fun forCar(car: VehicleCatalog.Car): Pair<Int, Boolean>? {
        val model = car.model ?: return null
        val engine = VehicleCatalog.engineFor(car) ?: VehicleCatalog.engineLike(car)
        if (engine != null) {
            entries.firstOrNull { it.engine != null && same(it.make, car.make) && same(it.model, model) && same(it.engine, engine.name) }?.let { return it.kmh to true }
        }
        return entries.filter { it.engine == null && same(it.make, car.make) && same(it.model, model) && car.year in it.from..it.to }
            .maxOfOrNull { it.kmh }?.let { it to false }
    }

    /** How many engines have a top speed, for tests. */
    internal val engineCount: Int get() = entries.count { it.engine != null }
}
