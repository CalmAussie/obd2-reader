package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcGenericTest {

    @Test
    fun everyDefinitionIsAGenericCodeWithFullAdvice() {
        for (code in DtcGeneric.codes) {
            assertTrue(code, Regex("^(P0|P2|U0|U3)[0-9A-F]{3}$|^P34[0-9A-F]{2}$").matches(code))
            val info = DtcLibrary.lookup(code)
            assertTrue(code, info.known)
            assertTrue(code, info.meaning.isNotBlank() && info.causes.isNotEmpty() && info.checks.isNotEmpty())
            assertTrue(code, info.system.contains("generic"))
        }
    }

    @Test
    fun newDefinitionsUseTheStandardNames() {
        assertEquals("Manifold pressure (MAP) or barometric pressure sensor circuit intermittent", DtcLibrary.title("P0109"))
        assertEquals("Cylinder 12 misfire detected", DtcLibrary.title("P0312"))
        assertEquals("Fuel injector circuit high (cylinder 8)", DtcLibrary.title("P0283"))
        assertEquals("Shift solenoid E electrical", DtcLibrary.title("P0773"))
        assertEquals("Reverse incorrect ratio", DtcLibrary.title("P0736"))
        assertEquals("Lost communication with the airbag (restraints) control module", DtcLibrary.title("U0151"))
    }

    @Test
    fun faultTypeIsExplained() {
        assertTrue(DtcLibrary.lookup("P0192").meaning.startsWith("The signal is lower than"))
        assertTrue(DtcLibrary.lookup("P0193").meaning.startsWith("The signal is higher than"))
        assertTrue(DtcLibrary.lookup("P0111").meaning.startsWith("The signal is within its limits"))
        assertTrue(DtcLibrary.lookup("P0109").meaning.startsWith("The signal drops out"))
        // An oxygen sensor reading lean can be a real lean mixture, not only wiring.
        assertTrue(DtcLibrary.lookup("P2195").meaning.contains("real lean mixture"))
    }

    @Test
    fun theFullStandardListIsCovered() {
        // From the open SAE lists, cross-checked.
        assertTrue(DtcGeneric.codes.size > 2800)
        assertEquals("Exhaust gas temperature sensor circuit (bank 1 sensor 1)", DtcLibrary.title("P0544"))
        assertEquals("O2 sensor circuit range/performance (bank 1 sensor 3)", DtcLibrary.title("P2A02"))
        assertEquals("Cylinder 1 deactivation/intake valve control circuit/open", DtcLibrary.title("P3401"))
        assertEquals("Lost communication with HVAC control module - rear", DtcLibrary.title("U0165"))
        assertTrue(DtcLibrary.lookup("P3401").system.contains("generic"))
        assertTrue(DtcLibrary.lookup("P0A80").known)
        assertTrue(DtcLibrary.lookup("P0A80").checks.any { "high-voltage" in it })
        // Still manufacturer-specific: not filled in from the generic list.
        assertTrue(!DtcLibrary.lookup("P1000").known)
        assertTrue(!DtcLibrary.lookup("P3000").known)
    }

    @Test
    fun everyFamilyNameInTheListIsReal() {
        for (chunk in DtcGenericData.CHUNKS) for (line in chunk.lines().filter { it.isNotBlank() }) {
            val p = line.split('|')
            assertEquals(line, 3, p.size)
            assertTrue(line, Regex("^[PU][0-9A-F]{4}$").matches(p[0]))
        }
    }

    @Test
    fun handWrittenEntriesStillWin() {
        assertEquals("Cylinder 3 misfire detected", DtcLibrary.title("P0303"))
        assertTrue(DtcLibrary.lookup("P0303").causes.size >= 3)
    }
}
