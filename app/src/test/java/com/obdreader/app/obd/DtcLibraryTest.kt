package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcLibraryTest {

    @Test
    fun misfiresAreUrgentAndNameTheCylinder() {
        val info = DtcLibrary.lookup("P0303")
        assertEquals("Cylinder 3 misfire detected", info.title)
        assertEquals(DtcLibrary.Severity.HIGH, info.severity)
        assertTrue(info.causes.isNotEmpty() && info.checks.isNotEmpty() && info.symptoms.isNotEmpty())
    }

    @Test
    fun oxygenSensorFamilyUsesStandardNumbering() {
        assertEquals("Oxygen sensor circuit low voltage (bank 1, sensor 1)", DtcLibrary.title("P0131"))
        assertEquals("Oxygen sensor slow response (bank 1, sensor 1)", DtcLibrary.title("P0133"))
        assertEquals("Oxygen sensor heater circuit fault (bank 1, sensor 2)", DtcLibrary.title("P0141"))
        assertEquals("Oxygen sensor slow response (bank 2, sensor 1)", DtcLibrary.title("P0153"))
        assertEquals("Oxygen sensor heater circuit fault (bank 2, sensor 2)", DtcLibrary.title("P0161"))
    }

    @Test
    fun familiesCoverEveryCylinder() {
        assertEquals("Fuel injector circuit fault (cylinder 12)", DtcLibrary.title("P0212"))
        assertEquals("Ignition coil A circuit fault (usually cylinder 1)", DtcLibrary.title("P0351"))
        assertEquals("Ignition coil L circuit fault (usually cylinder 12)", DtcLibrary.title("P0362"))
    }

    @Test
    fun commonCodesAreKnown() {
        for (code in listOf("P0420", "P0171", "P0128", "P0442", "P0455", "P0300", "P0335", "P0700", "U0100", "P2135", "P0299", "P0087")) {
            assertTrue(code, DtcLibrary.lookup(code).known)
        }
        assertEquals(DtcLibrary.Severity.LOW, DtcLibrary.lookup("P0442").severity)
        assertTrue(DtcLibrary.lookup("P0420").system.contains("generic"))
    }

    @Test
    fun unknownCodesAreExplainedFromTheirStructure() {
        val maker = DtcLibrary.lookup("P1456")
        assertFalse(maker.known)
        assertTrue(maker.system.endsWith("manufacturer-specific code"))
        assertTrue(maker.meaning.contains("P1456"))

        val generic = DtcLibrary.lookup("p0699")
        assertEquals("P0699", generic.code)
        assertTrue(generic.system.contains("generic"))
        assertTrue(generic.meaning.contains("engine computer"))

        assertTrue(DtcLibrary.lookup("B0001").system.startsWith("Body"))
        assertTrue(DtcLibrary.lookup("C1234").system.endsWith("manufacturer-specific code"))
        assertTrue(DtcLibrary.lookup("U3000").system.contains("generic"))
        assertEquals("Unrecognised code", DtcLibrary.lookup("XYZ").title)
    }
}
