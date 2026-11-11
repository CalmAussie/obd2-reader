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
        // By make: each family's own addresses, whatever the module is called.
        assertTrue(ModuleScan.isAirbag("Toyota", ModuleScan.Module("Module at 780", "780", "788")))
        assertTrue(ModuleScan.isAirbag("Kia", ModuleScan.Module("Module at 7D2", "7D2", "7DA")))
        assertTrue(ModuleScan.isAirbag("Honda", ModuleScan.Module("Module at 18DA53F1", "18DA53F1", "18DAF153")))
        assertTrue(ModuleScan.isAirbag("Peugeot", ModuleScan.Module("Module at 744", "744", "74C")))
        // Another maker's airbag address is an ordinary module on a known make.
        assertFalse(ModuleScan.isAirbag("Toyota", ModuleScan.Module("Module at 744", "744", "74C")))
    }

    /** The makes in each family, and the airbag addresses that family's modules use. */
    private val airbagsByMake = mapOf(
        "Ford" to setOf("737", "765"), "Mazda" to setOf("737", "765"), "Lincoln" to setOf("737", "765"),
        "Toyota" to setOf("780"), "Lexus" to setOf("780"),
        "Hyundai" to setOf("7D2"), "Kia" to setOf("7D2"), "Genesis" to setOf("7D2"),
        "Nissan" to setOf("752"), "Infiniti" to setOf("752"),
        "Volkswagen" to setOf("715"), "Audi" to setOf("715"), "Skoda" to setOf("715"), "SEAT / Cupra" to setOf("715"),
        "Chrysler" to setOf("744"), "Dodge" to setOf("744"), "Jeep" to setOf("744"), "Ram" to setOf("744"),
        "Chevrolet" to setOf("247"), "Holden" to setOf("247"), "Cadillac" to setOf("247"),
        "Honda" to setOf("18DA53F1"), "Acura" to setOf("18DA53F1"),
        // Makes whose own addresses aren't known skip every family's.
        "Subaru" to setOf("737", "765", "780", "7D2", "752", "715", "744", "247"),
        "MG" to setOf("737", "765", "780", "7D2", "752", "715", "744", "247"),
        "Peugeot" to setOf("737", "765", "780", "7D2", "752", "715", "744", "247"),
    )

    @Test fun noScanListsAnAirbagModule() {
        for ((make, airbags) in airbagsByMake) for (bit29 in listOf(false, true)) {
            val modules = ModuleScan.modulesFor(make, null, bit29)
            assertTrue("$make $bit29", modules.none { ModuleScan.isAirbag(make, it) })
            assertTrue("$make $bit29", modules.none { it.request in airbags || it.request == "18DA53F1" })
        }
        // Every make in the catalogue, too.
        for (make in VehicleCatalog.MAKES.map { it.name }) for (bit29 in listOf(false, true)) {
            assertTrue(make, ModuleScan.modulesFor(make, null, bit29).none { ModuleScan.isAirbag(make, it) })
        }
    }

    @Test fun noSearchAsksAnAirbagAddress() {
        for ((make, airbags) in airbagsByMake) for (bit29 in listOf(false, true)) {
            val asked = ModuleScan.sweepsFor(make, bit29).flatMap { it.requests }
            assertTrue("$make $bit29", asked.isNotEmpty())
            assertTrue("$make $bit29", asked.none { it in airbags })
            assertTrue("$make $bit29", "18DA53F1" !in asked)
        }
        for (make in VehicleCatalog.MAKES.map { it.name }) for (bit29 in listOf(false, true)) {
            val skip = ModuleScan.airbagAddresses(make)
            assertTrue(make, ModuleScan.sweepsFor(make, bit29).flatMap { it.requests }.none { it in skip })
        }
        // The rest of each block is still searched.
        assertTrue("7B0" in ModuleScan.sweepsFor("Toyota", false).single().requests)
        assertTrue("713" in ModuleScan.sweepsFor("Audi", false).single().requests)
        assertTrue("18DA28F1" in ModuleScan.sweepsFor("Honda", false).first { it.is29Bit }.requests)
    }
}
