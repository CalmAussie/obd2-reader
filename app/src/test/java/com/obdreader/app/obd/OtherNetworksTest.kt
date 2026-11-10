package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherNetworksTest {
    @Test fun pwmSearchNeverAsksTheAirbagRange() {
        assertTrue(ModuleScan.Pwm.SEARCH.none { it in 0x58..0x5F })
        assertTrue(0x28 in ModuleScan.Pwm.SEARCH)
        assertTrue(ModuleScan.Pwm.KNOWN.none { ModuleScan.isAirbag(it) })
    }

    @Test fun pwmModulesAreNamedByRange() {
        assertEquals("ABS and traction control", ModuleScan.Pwm.nameFor(0x28))
        assertEquals("Instrument cluster", ModuleScan.Pwm.nameFor(0x60))
        assertEquals("C428F1", ModuleScan.Pwm.header(0x28))
    }

    @Test fun pwmCodesFromRequest13() {
        val a = ModuleScan.Pwm.parse(listOf("53 51 45 51 48 00 00"))
        assertEquals(listOf("C1145", "C1148"), (a as ModuleScan.Answer.Codes).codes.map { it.label })
        assertEquals(ModuleScan.Answer.Codes(emptyList()), ModuleScan.Pwm.parse(listOf("53 00 00 00 00 00 00")))
        assertEquals(ModuleScan.Answer.Refused, ModuleScan.Pwm.parse(listOf("7F 13 11")))
        assertEquals(ModuleScan.Answer.Silent, ModuleScan.Pwm.parse(listOf("NO DATA")))
    }

    @Test fun pwmCodesFromRequest18() {
        val a = ModuleScan.Pwm.parse(listOf("58 01 51 85 20"))
        assertEquals(listOf("C1185"), (a as ModuleScan.Answer.Codes).codes.map { it.label })
    }

    @Test fun pwmAnsweredSeesAnyAnswer() {
        assertTrue(ModuleScan.Pwm.answered(listOf("53 00 00")))
        assertTrue(ModuleScan.Pwm.answered(listOf("7F 13 11")))
        assertFalse(ModuleScan.Pwm.answered(listOf("NO DATA")))
    }

    @Test fun onlyFordsAndMazdasOnPwmUseIt() {
        assertTrue(ModuleScan.isFordPwm("Mazda", "A1"))
        assertTrue(ModuleScan.isFordPwm("Ford", "1"))
        assertFalse(ModuleScan.isFordPwm("Ford", "6"))
        assertFalse(ModuleScan.isFordPwm("Toyota", "1"))
    }

    @Test fun secondNetwork() {
        val net = ModuleScan.SecondNetwork
        assertTrue(net.offeredFor("Ford", "A6"))
        assertTrue(net.offeredFor("Mazda", "1"))
        assertFalse(net.offeredFor("Toyota", "6"))
        assertFalse(net.offeredFor("Ford", "3"))
        assertTrue(net.isStn(listOf("STN2120 v5.6.19")))
        assertFalse(net.isStn(listOf("?")))
        assertEquals("31", net.protocolFrom(listOf("31")))
        assertTrue(net.refused(listOf("?")))
        assertFalse(net.refused(listOf("OK")))
        assertTrue("737" !in net.SWEEP.requests && "765" !in net.SWEEP.requests)
        assertTrue("720" in net.SWEEP.requests && "7DF" !in net.SWEEP.requests)
        assertEquals("Instrument cluster", net.nameFor("720"))
    }

    @Test fun guardLetsOnlySafeStCommandsThrough() {
        assertTrue(ReadOnlyGuard.isAllowed("STI"))
        assertTrue(ReadOnlyGuard.isAllowed("STPR"))
        assertTrue(ReadOnlyGuard.isAllowed("STP 53"))
        assertTrue(ReadOnlyGuard.isAllowed("STP 31"))
        assertFalse(ReadOnlyGuard.isAllowed("STPX h:7E0, d:1401"))
        assertFalse(ReadOnlyGuard.isAllowed("STSBR 115200"))
        assertTrue(ReadOnlyGuard.isAllowed("13FF00"))
        assertFalse(ReadOnlyGuard.isAllowed("14FF00"))
    }

    @Test fun airbagModulesAreRecognised() {
        assertTrue(ModuleScan.isAirbag(ModuleScan.Module("Airbags (restraints)", "737", "73F")))
        assertFalse(ModuleScan.isAirbag(ModuleScan.Module("Instrument cluster", "720", "728")))
    }
}
