package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MakerCodesTest {

    @Test
    fun fordDefinitionOnlyForTheYearsFordPublished() {
        val fit = DtcLibrary.lookup("P1450", "Ford", 2021)
        assertTrue(fit.known)
        assertEquals("Unable to bleed up fuel tank vacuum (excessive vacuum)", fit.title)
        assertTrue(fit.system.contains("Ford's definition for 2015–2025 petrol engines"))
        assertTrue(DtcLibrary.lookup("P1646", "Lincoln", 2024).known)

        // Ford's 2015, 2016 and 2017 summaries define P1336 and P1450 the same way as later years.
        for (y in 2015..2017) {
            assertTrue("P1450 $y", DtcLibrary.lookup("P1450", "Ford", y).known)
            assertTrue("P1336 $y", DtcLibrary.lookup("P1336", "Lincoln", y).known)
        }
        // P1336 left Ford's summaries after 2023, and the newer codes start in 2020.
        assertFalse(DtcLibrary.lookup("P1336", "Ford", 2024).known)
        assertFalse(DtcLibrary.lookup("P144A", "Ford", 2019).known)

        // Before the years Ford's documents cover: not guessed.
        assertFalse(DtcLibrary.lookup("P1450", "Ford", 2014).known)
        assertEquals("Ford-specific code", DtcLibrary.title("P1450", "Ford", 2010))
        // Without a year, or for another make, it isn't used either.
        assertFalse(DtcLibrary.lookup("P1450", "Ford").known)
        assertFalse(DtcLibrary.lookup("P1450", "Mazda", 2021).known)
        assertEquals("Mazda-specific code", DtcLibrary.title("P1450", "Mazda", 2021))
    }

    /** A car each of [c]'s fits covers, as (make, year, model, engine). */
    private fun carsFor(c: MakerCodes.Coverage) = c.fits.map { fit ->
        Triple(c.makes.first(), fit.years.first, fit.models.firstOrNull() to fit.engines.firstOrNull())
    }

    @Test
    fun everyEntryIsManufacturerSpecificWithFullAdvice() {
        assertTrue(MakerCodes.coverage.size > 100)
        for (c in MakerCodes.coverage) {
            assertTrue(c.code, DtcLibrary.systemOf(c.code).endsWith("manufacturer-specific code"))
            assertTrue(c.code, c.source.isNotBlank() && c.title.isNotBlank() && c.fits.isNotEmpty())
            for ((make, year, car) in carsFor(c)) {
                val info = DtcLibrary.lookup(c.code, make, year, car.first, car.second)
                val what = "${c.code} $make $year ${car.first} ${car.second}"
                assertTrue(what, info.known && info.causes.isNotEmpty() && info.symptoms.isNotEmpty() && info.checks.isNotEmpty())
                assertTrue(what, Regex("^[A-Z][A-Za-z -]*'s definition: .+").matches(info.meaning))
                assertTrue(what, info.system.contains("manufacturer-specific code: $make's definition for "))
                assertTrue(what, info.severity != DtcLibrary.Severity.UNKNOWN)
                // Read-only app: the advice never tells the user to clear codes.
                assertFalse(what, (info.checks + info.causes + info.symptoms + info.meaning).any { it.contains("clear the code", ignoreCase = true) } && make != "Ford")
            }
        }
    }

    @Test
    fun everyLimitedFitNamesCarsInTheCarList() {
        for (c in MakerCodes.coverage) for (make in c.makes) for (fit in c.fits) for (name in fit.models) {
            val what = "${c.code} $make $name ${fit.years}"
            val models = VehicleCatalog.MAKES.firstOrNull { it.name == make }?.models.orEmpty()
            val model = models.firstOrNull { m -> m.name.split(" / ").any { it.trim().equals(name, ignoreCase = true) } }
            assertTrue("$what: model not in the car list", model != null)
            assertTrue("$what: years outside the model's", fit.years.any { it in model!!.from..model.to })
            if (fit.engines.isEmpty()) continue
            for (engine in fit.engines) {
                assertTrue("$what: no engine $engine", model!!.engines.any { it.name.equals(engine, ignoreCase = true) })
            }
            assertTrue("$what: none of its engines offered in those years", fit.years.any { y ->
                VehicleCatalog.engines(make, y, model!!.name).any { e -> fit.engines.any { it.equals(e.name, ignoreCase = true) } }
            })
        }
    }

    @Test
    fun noCarGetsTwoDefinitionsOfOneCode() {
        val shared = MakerCodes.coverage.groupBy { it.code }.filterValues { it.size > 1 }
        for ((code, list) in shared) for (make in list.flatMap { it.makes }.toSet()) {
            val mine = list.filter { make in it.makes }
            if (mine.size < 2) continue
            for (model in VehicleCatalog.MAKES.first { it.name == make }.models) for (year in model.from..model.to) {
                for (engine in VehicleCatalog.engines(make, year, model.name).map { it.name } + listOf(null)) {
                    val hits = mine.count { c -> c.fits.any { it.matches(year, model.name, engine) } }
                    assertTrue("$code $make ${model.name} $year $engine", hits <= 1)
                }
            }
        }
    }

    @Test
    fun aMakersDefinitionNeedsTheCarItsBulletinNames() {
        // Kia's knock sensor code: only the engines Kia's bulletins list.
        val soul = DtcLibrary.lookup("P1326", "Kia", 2014, "Soul", "1.6 I4")
        assertTrue(soul.known)
        assertEquals("Knock signal range/performance", soul.title)
        assertEquals(DtcLibrary.Severity.HIGH, soul.severity)
        assertTrue(soul.system, soul.system.contains("Kia's definition for 2014–2016 Soul with 1.6 I4, 2.0 I4 engines"))
        assertFalse(DtcLibrary.lookup("P1326", "Kia", 2014, "Soul", null).known)
        assertFalse(DtcLibrary.lookup("P1326", "Kia", 2014, "Soul", "1.6 diesel").known)
        assertFalse(DtcLibrary.lookup("P1326", "Kia", 2014, "Rio", "1.6 I4").known)
        assertFalse(DtcLibrary.lookup("P1326", "Kia", 2019, "Soul", "1.6 I4").known)
        // Hyundai's bulletins never word the code, so it isn't explained for Hyundai.
        assertFalse(DtcLibrary.lookup("P1326", "Hyundai", 2014, "Sonata / i45", "2.4 I4").known)
        assertEquals("Hyundai-specific code", DtcLibrary.title("P1326", "Hyundai", 2014, "Sonata / i45", "2.4 I4"))
        // The catalogue's joined names work as chosen.
        assertTrue(DtcLibrary.lookup("P1326", "Kia", 2015, "Cerato / Forte / K3", "2.0 I4").known)
        assertTrue(DtcLibrary.lookup("P1326", "Kia", 2016, "Magentis / Optima / K5", "2.0 turbo").known)
        // A code limited to models only doesn't need the engine.
        assertTrue(DtcLibrary.lookup("P1260", "Mazda", 2021, "CX-5", null).known)
        assertFalse(DtcLibrary.lookup("P1260", "Mazda", 2018, "CX-5", null).known)
        assertTrue(DtcLibrary.lookup("P1456", "Honda", 1999, "Civic", null).known)
        assertFalse(DtcLibrary.lookup("P1456", "Honda", 2005, "Civic", null).known)
    }

    @Test
    fun theSameCodeMeansDifferentThingsOnDifferentCars() {
        val tundra = DtcLibrary.lookup("P1441", "Toyota", 2006, "Tundra", "4.7 V8")
        val santaFe = DtcLibrary.lookup("P1441", "Hyundai", 2021, "Santa Fe", "1.6 turbo hybrid")
        assertTrue(tundra.title, tundra.title.startsWith("Secondary air injection"))
        assertEquals("Purge valve rear hose missing", santaFe.title)
        assertTrue(tundra.meaning.startsWith("Toyota's definition"))
        assertTrue(santaFe.meaning.startsWith("Hyundai's definition"))
        // One make, two definitions: GM's 2006 files and its 2022 bulletin name P1101 differently.
        assertEquals("Intake rationality crosscheck out of range", DtcLibrary.title("P1101", "Chevrolet", 2006, "Cobalt", "2.2 I4"))
        assertEquals("Intake air flow system performance", DtcLibrary.title("P1101", "Chevrolet", 2018, "Equinox", "1.5 turbo"))
        assertEquals("Chevrolet-specific code", DtcLibrary.title("P1101", "Chevrolet", 2012, "Cruze", "1.4 turbo"))
        // Nissan and Porsche both use P17F0, for different gearbox faults.
        assertTrue(DtcLibrary.title("P17F0", "Nissan", 2015, "Altima", "2.5 I4").startsWith("CVT judder"))
        assertEquals("Transmission overtemperature", DtcLibrary.title("P17F0", "Porsche", 2011, "Panamera", null))
    }

    @Test
    fun dieselPetrolAndElectricCodesStayWithTheirCars() {
        // Volkswagen's glow plug pressure sensor codes: the 2.0 TDI only, cylinder by cylinder.
        assertEquals("Cylinder pressure sensor: electrical fault", DtcLibrary.title("P13CE", "Volkswagen", 2012, "Golf", "2.0 TDI diesel"))
        assertEquals("Cylinder pressure sensor: malfunction", DtcLibrary.title("P13E3", "Volkswagen", 2013, "Passat", "2.0 TDI diesel"))
        assertTrue(DtcLibrary.lookup("P13D5", "Volkswagen", 2010, "Jetta", "2.0 diesel").known)
        assertFalse(DtcLibrary.lookup("P13CE", "Volkswagen", 2012, "Golf", "1.4 TSI").known)
        assertFalse(DtcLibrary.lookup("P13CE", "Volkswagen", 2009, "Golf", "2.0 TDI diesel").known)
        // Volkswagen's misfire bulletin: petrol engines of the US model lines, 2008 to 2017.
        assertEquals("Cylinder switched off after misfires", DtcLibrary.title("P130A", "Volkswagen", 2012, "Jetta", "2.5 I5"))
        assertEquals("Fuel level too low", DtcLibrary.title("P1250", "Volkswagen", 2016, "Tiguan", "2.0 TSI turbo"))
        assertFalse(DtcLibrary.lookup("P130A", "Volkswagen", 2012, "Jetta", "2.0 diesel").known)
        assertFalse(DtcLibrary.lookup("P130A", "Volkswagen", 2019, "Jetta", "1.4 turbo").known)
        assertFalse(DtcLibrary.lookup("P130A", "Volkswagen", 2012, "Polo", "1.2 TSI").known)
        // One software bulletin, one engine and two model years.
        assertTrue(DtcLibrary.lookup("P30DC", "Volkswagen", 2021, "Jetta", "1.4 turbo").known)
        assertFalse(DtcLibrary.lookup("P30DC", "Volkswagen", 2021, "Jetta", "2.0 turbo").known)
        assertTrue(DtcLibrary.lookup("P30C0", "Volkswagen", 2022, "Taos", null).known)
        // The electric ID.4: the 2023 cars the bulletin covers, and the text says which ones.
        val id4 = DtcLibrary.lookup("P1CC3", "Volkswagen", 2023, "ID.4", null)
        assertEquals("External charger malfunction", id4.title)
        assertTrue(id4.meaning, id4.meaning.contains("built in Chattanooga"))
        assertFalse(DtcLibrary.lookup("P1CC3", "Volkswagen", 2022, "ID.4", null).known)
        // Holden: GM names the Equinox 1.5 turbo in one bulletin and words the code in another.
        val equinox = DtcLibrary.lookup("P1101", "Holden", 2019, "Equinox", "1.5 turbo")
        assertEquals("Intake air flow system performance", equinox.title)
        assertTrue(equinox.meaning.startsWith("GM's definition"))
        assertFalse(DtcLibrary.lookup("P1101", "Holden", 2019, "Equinox", "2.0 turbo").known)
        assertFalse(DtcLibrary.lookup("P1101", "Holden", 2019, "Acadia", null).known)
    }

    @Test
    fun chassisBodyAndNetworkCodesKeepTheirOwnSystem() {
        val net = DtcLibrary.lookup("U1400", "Chrysler", 2017, "Pacifica", "3.6 V6")
        assertTrue(net.known)
        assertTrue(net.system, net.system.startsWith("Network: communication between the car's computers, manufacturer-specific code: Chrysler's definition for 2017 Pacifica"))
        assertTrue(DtcLibrary.lookup("C1252", "Jeep", 2019, "Cherokee / Liberty", "2.0 turbo").system.startsWith("Chassis"))
        assertTrue(DtcLibrary.lookup("B1572", "Subaru", 2018, "Outback", null).system.startsWith("Body"))
        // No airbag codes: the app never talks to airbag modules.
        assertTrue(MakerCodes.coverage.none { it.title.contains("airbag", ignoreCase = true) || it.title.contains("SRS") })
    }

    @Test
    fun aRangeOfCodesFromOneBulletinIsListedCodeByCode() {
        for (code in listOf("P1731", "P1734", "P17BB", "P17BE", "P18A0", "P18B5", "P177A", "P177F")) {
            assertTrue(code, DtcLibrary.lookup(code, "Porsche", 2011, "Cayman", null).known)
        }
        assertFalse(DtcLibrary.lookup("P1735", "Porsche", 2011, "Cayman", null).known)
        // Porsche's Panamera bulletin lists P189A, P189C and P189D but not P189B.
        assertTrue(DtcLibrary.lookup("P189B", "Porsche", 2011, "Boxster", null).known)
        assertFalse(DtcLibrary.lookup("P189B", "Porsche", 2011, "Panamera", null).known)
        assertTrue(DtcLibrary.lookup("P189C", "Porsche", 2011, "Panamera", null).known)
    }

    @Test
    fun toyotaLexusAndMazdaCodesOnlyForTheCarsTheirBulletinsName() {
        // Toyota T-SB-0041-15: 2010-2013 Prius.
        assertEquals("Poor engine power", DtcLibrary.lookup("P3190", "Toyota", 2012, "Prius", "1.8 hybrid").title)
        assertFalse(DtcLibrary.lookup("P3190", "Toyota", 2015, "Prius", "1.8 hybrid").known)
        assertFalse(DtcLibrary.lookup("P3190", "Toyota", 2012, "Camry", "2.5 hybrid").known)
        // Toyota T-SB-0026-22: the RAV4 hybrid only, not the petrol RAV4.
        assertTrue(DtcLibrary.lookup("P1C80", "Toyota", 2020, "RAV4", "2.5 hybrid").known)
        assertFalse(DtcLibrary.lookup("P1C80", "Toyota", 2020, "RAV4", "2.5").known)
        // P107A: the petrol RAV4 (T-SB-0058-22), and both Highlander engines (T-SB-0049-21).
        assertTrue(DtcLibrary.lookup("P107A", "Toyota", 2021, "RAV4", "2.5").known)
        assertTrue(DtcLibrary.lookup("P107A", "Toyota", 2021, "Kluger / Highlander", "3.5 V6").known)
        assertFalse(DtcLibrary.lookup("P107A", "Toyota", 2021, "RAV4", "2.5 hybrid").known)
        // Toyota and Ford both use P1450, for different faults.
        assertEquals("Fuel tank pressure sensor fault", DtcLibrary.lookup("P1450", "Toyota", 2022, "RAV4", "2.5 hybrid").title)
        assertEquals("Unable to bleed up fuel tank vacuum (excessive vacuum)", DtcLibrary.lookup("P1450", "Ford", 2022).title)
        // Lexus L-SB-0159-17: the IS 200t (2.0 turbo), not the IS's other engines.
        assertTrue(DtcLibrary.lookup("P1378", "Lexus", 2016, "IS", "2.0 turbo").known)
        assertFalse(DtcLibrary.lookup("P1378", "Lexus", 2016, "IS", "3.5 V6").known)
        assertFalse(DtcLibrary.lookup("P1378", "Toyota", 2016, "Camry", "2.5").known)
        // Mazda 05-004/19: SKYACTIV 6-speed automatic cars, petrol engines only.
        assertTrue(DtcLibrary.lookup("P1738", "Mazda", 2019, "CX-5", "2.5").known)
        assertFalse(DtcLibrary.lookup("P1738", "Mazda", 2019, "CX-5", "2.2 diesel").known)
        assertFalse(DtcLibrary.lookup("P1738", "Mazda", 2013, "Mazda3", "2.0").known)
        assertTrue(DtcLibrary.lookup("P130C", "Mazda", 2016, "CX-9", null).known)
        assertFalse(DtcLibrary.lookup("P130C", "Mazda", 2018, "CX-9", null).known)
    }

    @Test
    fun hyundaiGenesisAndKiaCodesOnlyForTheCarsTheirBulletinsName() {
        // Hyundai 25-01-010H: the hybrids only, not the petrol cars of the same model.
        assertTrue(DtcLibrary.lookup("P1BB2", "Hyundai", 2022, "Elantra", "1.6 hybrid").known)
        assertTrue(DtcLibrary.lookup("P1BB2", "Hyundai", 2023, "Tucson", "1.6 turbo hybrid").known)
        assertFalse(DtcLibrary.lookup("P1BB2", "Hyundai", 2022, "Elantra", "2.0 I4").known)
        assertFalse(DtcLibrary.lookup("P1BB2", "Hyundai", 2025, "Tucson", "1.6 turbo hybrid").known)
        // Hyundai 22-AT-009H: the 8-speed dual-clutch cars (2.5 turbo and the N models), up to 2022.
        assertTrue(DtcLibrary.lookup("P193D", "Hyundai", 2022, "Santa Cruz", "2.5 turbo").known)
        assertTrue(DtcLibrary.lookup("U1135", "Hyundai", 2020, "Veloster", "2.0 turbo (N)").known)
        assertFalse(DtcLibrary.lookup("P193D", "Hyundai", 2022, "Santa Cruz", "2.5 I4").known)
        assertFalse(DtcLibrary.lookup("P193D", "Hyundai", 2022, "Veloster", "1.6 turbo").known)
        // The charging unit (ICCU) codes: Ioniq 5 2022-2024, Ioniq 6 2023-2025, GV60 2023-2024, EV6 2022-2024.
        assertEquals("DC/DC converter input voltage sensor fault", DtcLibrary.lookup("P1A90", "Hyundai", 2023, "Ioniq 5", null).title)
        assertTrue(DtcLibrary.lookup("P1A90", "Genesis", 2023, "GV60", null).known)
        assertTrue(DtcLibrary.lookup("P1A90", "Kia", 2022, "EV6", null).known)
        assertFalse(DtcLibrary.lookup("P1A90", "Hyundai", 2023, "Kona Electric", null).known)
        assertFalse(DtcLibrary.lookup("P1A90", "Genesis", 2023, "G80", "2.5 turbo").known)
        // Hyundai and Kia print different names for P1E13.
        assertEquals("Inverter charging relay fault", DtcLibrary.lookup("P1E13", "Hyundai", 2024, "Ioniq 6", null).title)
        assertEquals("Charger power module voltage fault", DtcLibrary.lookup("P1E13", "Kia", 2024, "EV9", null).title)
        assertFalse(DtcLibrary.lookup("P1E14", "Kia", 2024, "EV9", null).known)
        // Hyundai 23-01-073H-1: Kona Electric 2019-2023, not the petrol Kona.
        assertTrue(DtcLibrary.lookup("P1AA8", "Hyundai", 2020, "Kona Electric", null).known)
        assertFalse(DtcLibrary.lookup("P1AA8", "Hyundai", 2020, "Kona", "2.0 I4").known)
        // Kia SC199: the 2.0 with the IVT; Kia TRA111: Sorento and K5 2.5 turbo to 2023.
        assertTrue(DtcLibrary.lookup("P1603", "Kia", 2020, "Cerato / Forte / K3", "2.0 I4").known)
        assertFalse(DtcLibrary.lookup("P1603", "Kia", 2020, "Seltos", "2.0 I4").known)
        assertTrue(DtcLibrary.lookup("P1C2E", "Kia", 2023, "Sorento", "2.5 turbo").known)
        assertFalse(DtcLibrary.lookup("P1C2E", "Kia", 2023, "Sorento", "1.6 turbo hybrid").known)
        // P1441 means a hose fault on both makes, for their own hybrids. Hyundai 23-FL-001H took the Tucson
        // hybrid back out of 22-FL-003H, so Hyundai's stays on the Santa Fe hybrid.
        assertTrue(DtcLibrary.lookup("P1441", "Hyundai", 2022, "Santa Fe", "1.6 turbo hybrid").known)
        assertFalse(DtcLibrary.lookup("P1441", "Hyundai", 2022, "Tucson / ix35", "1.6 turbo hybrid").known)
        assertEquals("Purge valve downstream hose off", DtcLibrary.lookup("P1441", "Kia", 2022, "Sorento", "1.6 turbo hybrid").title)
    }

    @Test
    fun subaruCodesOnlyForTheCarsTheirDocumentsName() {
        // Subaru 07-132-18R: the 2014 Crosstrek hybrid only.
        assertEquals("Generator performance", DtcLibrary.lookup("P1C14", "Subaru", 2014, "Crosstrek / XV", "2.0 hybrid").title)
        assertFalse(DtcLibrary.lookup("P1C14", "Subaru", 2014, "Crosstrek / XV", "2.0 H4").known)
        assertFalse(DtcLibrary.lookup("P1C14", "Subaru", 2020, "Crosstrek / XV", "2.0 hybrid").known)
        // Subaru 11-205-25: petrol engines only, no hybrids.
        assertTrue(DtcLibrary.lookup("P1449", "Subaru", 2020, "Forester", "2.5").known)
        assertTrue(DtcLibrary.lookup("P1449", "Subaru", 2023, "WRX / STI", "2.4 turbo").known)
        assertTrue(DtcLibrary.lookup("P1449", "Subaru", 2021, "Ascent", null).known)
        assertFalse(DtcLibrary.lookup("P1449", "Subaru", 2020, "Forester", "2.0 hybrid").known)
        assertFalse(DtcLibrary.lookup("P1449", "Subaru", 2020, "Crosstrek / XV", "2.0 hybrid").known)
        assertFalse(DtcLibrary.lookup("P1449", "Subaru", 2020, "Outback", "2.5").known)
        // Subaru 11-184-18: BRZ 2017-2019.
        assertTrue(DtcLibrary.lookup("P119F", "Subaru", 2018, "BRZ", "2.0").known)
        assertFalse(DtcLibrary.lookup("P119F", "Subaru", 2022, "BRZ", "2.4").known)
        // TechTIPS articles: 2020 Legacy/Outback mirrors and air mix door, 2020 Impreza/Ascent EyeSight, Solterra.
        assertTrue(DtcLibrary.lookup("U122E", "Subaru", 2020, "Liberty / Legacy", null).known)
        assertTrue(DtcLibrary.lookup("U122F", "Subaru", 2020, "Outback", null).known)
        assertFalse(DtcLibrary.lookup("U122E", "Subaru", 2021, "Outback", null).known)
        assertTrue(DtcLibrary.lookup("B1441", "Subaru", 2021, "Outback", null).known)
        assertTrue(DtcLibrary.lookup("U1235", "Subaru", 2020, "Impreza", "2.0").known)
        assertFalse(DtcLibrary.lookup("U1235", "Subaru", 2020, "Forester", "2.5").known)
        assertTrue(DtcLibrary.lookup("U113B", "Subaru", 2023, "Solterra", null).known)
        // Other makes don't pick up Subaru's meanings.
        assertFalse(DtcLibrary.lookup("P1449", "Toyota", 2020, "Corolla", null).title == "EVAP system clog detected")
    }

    @Test
    fun fordCodesDontDependOnModelOrEngine() {
        assertTrue(DtcLibrary.lookup("P1450", "Ford", 2016, "Escape", "2.0 EcoBoost").known)
        assertTrue(DtcLibrary.lookup("P1450", "Ford", 2016, "Mustang", null).known)
    }

    @Test
    fun aFitLimitedToSomeModelsAndEnginesNeedsTheChosenCar() {
        val fit = MakerCodes.Fit(2014..2016, models = setOf("Soul"), engines = setOf("1.6 GDI", "2.0 GDI"))
        assertTrue(fit.matches(2015, "Soul", "1.6 GDI"))
        assertTrue(fit.matches(2015, "Soul", "2.0 gdi "))
        // Wrong year, model or engine.
        assertFalse(fit.matches(2013, "Soul", "1.6 GDI"))
        assertFalse(fit.matches(2015, "Sportage", "1.6 GDI"))
        assertFalse(fit.matches(2015, "Soul", "2.0 MPI"))
        // Not chosen: never assumed.
        assertFalse(fit.matches(2015, null, "1.6 GDI"))
        assertFalse(fit.matches(2015, "Soul", null))
        assertFalse(fit.matches(null, "Soul", "1.6 GDI"))
        // The support table counts the model whatever its engine.
        assertTrue(fit.covers(2015, "Soul"))
        assertFalse(fit.covers(2015, "Sportage"))
    }

    @Test
    fun aModelMatchesAnyOfItsNames() {
        val fit = MakerCodes.Fit(2014..2015, models = setOf("Forte"))
        assertTrue(fit.matches(2014, "Cerato / Forte / K3", null))
        assertTrue(MakerCodes.Fit(2014..2015, models = setOf("Cerato / Forte / K3")).matches(2015, "Forte", null))
        assertFalse(fit.matches(2014, "Forte Koup", null))
    }

    @Test
    fun anUnlimitedFitIgnoresModelAndEngine() {
        val fit = MakerCodes.Fit(2015..2025)
        assertTrue(fit.matches(2020, null, null))
        assertTrue(fit.matches(2020, "Escape", "1.5 EcoBoost"))
    }

    @Test
    fun genericCodesAreUnchangedForFord() {
        assertEquals(DtcLibrary.title("P0420"), DtcLibrary.title("P0420", "Ford", 2021))
    }
}
