package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineDataTest {

    @Test
    fun everyEntryParses() {
        assertEquals(emptyList<String>(), EngineData.malformed)
    }

    @Test
    fun noEngineListedTwice() {
        assertEquals(emptyList<String>(), EngineData.repeated)
    }

    @Test
    fun everyRowNamesARealModel() {
        val models = VehicleCatalog.MAKES.flatMap { mk -> mk.models.map { mk.name to it.name } }.toSet()
        val unknown = EngineData.byModel.keys.filter { it !in models }
        assertEquals(emptyList<Pair<String, String>>(), unknown)
    }

    /**
     * Years a catalogue model wasn't actually on sale (between generations, or sold only as an
     * electric car), so it has no engine to list then.
     */
    private val notSold: Map<String, IntRange> = mapOf(
        "Honda Insight" to 2007..2008, "BMW M5" to 2004..2004, "Mercedes-Benz SL" to 2021..2021,
        "Holden Astra" to 2010..2014, "Chevrolet Blazer" to 2006..2018, "GMC Canyon" to 2013..2014,
        "Buick Regal" to 2005..2010, "Audi S3" to 2004..2005, "SEAT / Cupra Toledo" to 2010..2011,
        "Chrysler Pacifica" to 2009..2016, "Maserati GranTurismo" to 2020..2022, "Maserati Quattroporte" to 2002..2003,
        "DS DS4" to 2019..2020, "Infiniti Q60" to 2016..2016, "Acura NSX" to 2006..2016, "Acura ZDX" to 2014..2023,
        "Scion xB" to 2007..2007, "Lincoln Aviator" to 2006..2019, "Lincoln Continental" to 2003..2016,
        "Mercury Sable" to 2006..2007, "Smart ForFour" to 2007..2013, "Smart ForTwo" to 2020..2024,
        "Volvo C70" to 2005..2005, "Land Rover Defender" to 2017..2019, "Renault Mégane" to 2024..2026,
        "Aston Martin DBS" to 2013..2017, "Aston Martin Vanquish" to 2019..2023,
    )
    private val notSoldMore: Map<String, List<IntRange>> = mapOf(
        "Audi RS4" to listOf(2002..2005, 2009..2011, 2016..2017), "Audi RS6" to listOf(2005..2007, 2011..2012),
        "Jeep Cherokee / Liberty" to listOf(2013..2013, 2024..2026),
    )

    @Test
    fun everyCombustionModelHasAnEngineEveryYear() {
        val missing = mutableListOf<String>()
        for (mk in VehicleCatalog.MAKES) for (m in mk.models) {
            for (year in m.from..m.to) {
                val key = "${mk.name} ${m.name}"
                if (notSold[key]?.contains(year) == true || notSoldMore[key].orEmpty().any { year in it }) continue
                val car = VehicleCatalog.Car(mk.name, year, m.name)
                if (VehicleCatalog.isElectric(car, m.name.lowercase())) continue
                if (VehicleCatalog.engines(mk.name, year, m.name).isEmpty()) missing += "${mk.name} ${m.name} $year"
            }
        }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun enginesAreSensible() {
        for (mk in VehicleCatalog.MAKES) for (m in mk.models) {
            val names = m.engines.map { it.name }
            assertEquals("${mk.name} ${m.name} has a repeated engine", names.size, names.toSet().size)
            for (e in m.engines) {
                assertTrue("${mk.name} ${m.name} ${e.name} years", e.from in m.from..m.to && e.to in m.from..m.to && e.from <= e.to)
                assertTrue("${mk.name} ${m.name} ${e.name} redline", e.redline in 3000..9500)
            }
        }
    }

    @Test
    fun tributeAndOthersOffered() {
        assertEquals(listOf("2.3 I4", "3.0 V6"), VehicleCatalog.engines("Mazda", 2006, "Tribute").map { it.name })
        assertTrue(VehicleCatalog.engines("Ford", 2012, "Kuga").any { it.fuel == VehicleCatalog.Fuel.DIESEL })
        assertTrue(VehicleCatalog.engines("Toyota", 2020, "C-HR").any { it.fuel == VehicleCatalog.Fuel.HYBRID })
    }
}
