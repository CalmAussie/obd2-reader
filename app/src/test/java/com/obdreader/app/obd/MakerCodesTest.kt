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

    @Test
    fun everyEntryIsManufacturerSpecificWithFullAdvice() {
        for (code in MakerCodes.codes) {
            assertTrue(code, Regex("^P1[0-9A-F]{3}$").matches(code))
            val info = DtcLibrary.lookup(code, "Ford", 2020)
            assertTrue(code, info.known && info.causes.isNotEmpty() && info.checks.isNotEmpty())
            assertTrue(code, info.meaning.startsWith("Ford's definition"))
            assertTrue(code, info.system.contains("manufacturer-specific"))
        }
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
