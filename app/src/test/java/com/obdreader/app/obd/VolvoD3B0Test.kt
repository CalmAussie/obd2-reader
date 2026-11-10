package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolvoD3B0Test {
    @Test fun onlyP80VolvosUseIt() {
        assertTrue(VolvoD3B0.appliesTo("Volvo", "850", 1996))
        assertFalse(VolvoD3B0.appliesTo("Volvo", "850", 1995))
        assertTrue(VolvoD3B0.appliesTo("Volvo", "V70", 1999))
        assertFalse(VolvoD3B0.appliesTo("Volvo", "V70", 2001))
        assertTrue(VolvoD3B0.appliesTo("Volvo", "C70", 2004))
        assertFalse(VolvoD3B0.appliesTo("Volvo", "S60", 2001))
        assertFalse(VolvoD3B0.appliesTo("Ford", "850", 1996))
    }

    @Test fun theAirbagModuleIsNeverAsked() {
        assertTrue(VolvoD3B0.MODULES.none { it.address == VolvoD3B0.AIRBAGS || it.init == VolvoD3B0.AIRBAGS })
    }

    @Test fun codesFromOneMessage() {
        val a = VolvoD3B0.parseCodes(listOf("84 13 51 EE 01 02 D9"), "51") as ModuleScan.Answer.Codes
        assertEquals(listOf("CI-221"), a.codes.map { it.code })
        assertTrue(a.codes[0].text!!.contains("Vehicle Speed Signal missing"))
    }

    @Test fun codesWithoutSpacesAndAcrossMessages() {
        val a = VolvoD3B0.parseCodes(listOf("851351EE010205DF", "841351EE0110AA"), "51") as ModuleScan.Answer.Codes
        assertEquals(listOf("CI-221", "CI-113", "CI-231"), a.codes.map { it.code })
    }

    @Test fun messagesRunTogetherAreSplit() {
        val a = VolvoD3B0.parseCodes(listOf("841301EE0110FF841301EE0120FF<DATA ERROR"), "01") as ModuleScan.Answer.Codes
        assertEquals(listOf("ABS-311", "ABS-312"), a.codes.map { it.code })
    }

    @Test fun noCodesAndNoAnswer() {
        assertEquals(ModuleScan.Answer.Codes(emptyList()), VolvoD3B0.parseCodes(listOf("83 13 6E EE 01 FF"), "6E"))
        assertEquals(ModuleScan.Answer.Silent, VolvoD3B0.parseCodes(listOf("NO DATA"), "6E"))
        assertEquals(ModuleScan.Answer.Silent, VolvoD3B0.parseCodes(listOf("BUS INIT: ...ERROR"), "6E"))
        assertEquals(ModuleScan.Answer.Refused, VolvoD3B0.parseCodes(listOf("84 13 6E 7F AE 11 FF"), "6E"))
        // Another module's answer doesn't count.
        assertEquals(ModuleScan.Answer.Silent, VolvoD3B0.parseCodes(listOf("84 13 51 EE 01 02 D9"), "6E"))
    }

    @Test fun unknownCodesStillShow() {
        val c = VolvoD3B0.codeFor("2D", 0x77)
        assertEquals("2D-77", c.code)
        assertTrue(c.text!!.contains("isn't known"))
    }

    @Test fun mileageFromTheCluster() {
        // jonesrh.info's examples: 3034 hex = 12340 steps of 10 miles; 4D83 hex = 19843.
        assertEquals(123_400 * 1.609344, VolvoD3B0.mileageKm(listOf("85 13 51 F9 03 34 30 49"))!!, 0.01)
        assertEquals(198_430 * 1.609344, VolvoD3B0.mileageKm(listOf("851351F903834DB5"))!!, 0.01)
        assertNull(VolvoD3B0.mileageKm(listOf("NO DATA")))
    }

    @Test fun guardLetsOnlyVolvoReadsThrough() {
        assertTrue(ReadOnlyGuard.isAllowed("AE01"))
        assertTrue(ReadOnlyGuard.isAllowed("B903"))
        assertTrue(ReadOnlyGuard.isAllowed("B90300"))
        assertTrue(ReadOnlyGuard.isAllowed("ATWM 82 51 13 A1"))
        assertTrue(ReadOnlyGuard.isAllowed("ATWM 68 6A F1 01 00"))
        assertTrue(ReadOnlyGuard.isAllowed("ATIIA 51"))
        assertFalse(ReadOnlyGuard.isAllowed("AF01"))   // clear codes
        assertFalse(ReadOnlyGuard.isAllowed("B030"))   // service light reset
        assertFalse(ReadOnlyGuard.isAllowed("B230"))
        assertFalse(ReadOnlyGuard.isAllowed("A0"))
        assertFalse(ReadOnlyGuard.isAllowed("ATWM 82 51 13 AF 01"))
        assertFalse(ReadOnlyGuard.isAllowed("ATWM 81 51 13 B0"))
    }

    @Test fun adapterThatCantWakeVolvoModules() {
        assertTrue(VolvoD3B0.adapterRefused(listOf("?")))
        assertFalse(VolvoD3B0.adapterRefused(listOf("OK")))
    }
}
