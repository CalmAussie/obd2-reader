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
        assertTrue(fit.system.contains("Ford's definition for 2018–2025"))
        assertTrue(DtcLibrary.lookup("P1646", "Lincoln", 2024).known)

        // Before the years Ford's documents cover: not guessed.
        assertFalse(DtcLibrary.lookup("P1450", "Ford", 2010).known)
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
    fun genericCodesAreUnchangedForFord() {
        assertEquals(DtcLibrary.title("P0420"), DtcLibrary.title("P0420", "Ford", 2021))
    }
}
