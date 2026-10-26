package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DeepDiagnosticsTest {
    @Test
    fun readinessForAPetrolCar() {
        // MIL off, no codes; misfire/fuel/components supported, fuel incomplete; catalyst, evap,
        // O2 sensor, O2 heater supported; evap and O2 heater incomplete.
        val r = Readiness.decode(intArrayOf(0x00, 0x27, 0x65, 0x44))!!
        assertFalse(r.diesel); assertFalse(r.milOn)
        assertEquals(listOf("Misfire", "Fuel system", "Components", "Catalytic converter", "Evaporative system", "Oxygen sensors", "Oxygen sensor heaters"), r.monitors.map { it.name })
        assertEquals(listOf("Fuel system", "Evaporative system", "Oxygen sensor heaters"), r.incomplete.map { it.name })
        assertEquals("3 of 7 not complete yet", r.summary())
        assertNull(Readiness.decode(intArrayOf(1, 2)))
    }

    @Test
    fun mode06ResultsAndLimits() {
        assertEquals(listOf(0x01, 0x02, 0x11, 0x20), TestResults.supported(0x00, intArrayOf(0xC0, 0x00, 0x80, 0x01)))
        // O2 sensor B1S1, test 1, unit 0x0B (0.001 V): 0.452 V between 0.200 and 0.900.
        val r = TestResults.parse(intArrayOf(0x01, 0x01, 0x0B, 0x01, 0xC4, 0x00, 0xC8, 0x03, 0x84)).single()
        assertTrue(r.passed)
        assertEquals("Oxygen sensor, bank 1 sensor 1", r.monitor)
        assertEquals("0.452 V (limits 0.200 to 0.900 V)", r.text())
        val failing = TestResults.parse(intArrayOf(0xA2, 0x0B, 0x24, 0x00, 0x30, 0x00, 0x00, 0x00, 0x10)).single()
        assertFalse(failing.passed); assertEquals("Misfire, cylinder 1", failing.monitor)
    }

    @Test
    fun moduleCodesFromUdsAndKwp() {
        // ABS: C1145 confirmed with failure type 0x1C, and U0121 pending.
        val uds = ModuleScan.parseUds(listOf("00B", "0:5902FF51451C", "1:0AC1210104")) as ModuleScan.Answer.Codes
        assertEquals(listOf("C1145-1C", "U0121-01"), uds.codes.map { it.label })
        assertTrue(uds.codes[0].confirmed); assertTrue(uds.codes[1].pending)
        assertTrue(ModuleScan.parseUds(listOf("7F1911")) is ModuleScan.Answer.Refused)
        assertTrue(ModuleScan.parseUds(listOf("NO DATA")) is ModuleScan.Answer.Silent)
        val kwp = ModuleScan.parseKwp(listOf("5801934220")) as ModuleScan.Answer.Codes
        assertEquals(listOf("B1342"), kwp.codes.map { it.label })
        assertTrue(ModuleScan.modulesFor("Mazda", "Tribute").any { it.name.startsWith("ABS") && it.request == "760" && it.reply == "768" })
        assertEquals(2, ModuleScan.modulesFor("Toyota", "Corolla").size)
    }

    @Test
    fun readOnlyStillHolds() {
        assertTrue(ReadOnlyGuard.isAllowed("1902FF")); assertTrue(ReadOnlyGuard.isAllowed("1800FF00"))
        assertFalse(ReadOnlyGuard.isAllowed("14FFFFFF")); assertFalse(ReadOnlyGuard.isAllowed("04"))
    }

    @Test
    fun calibrationIds() {
        val hex = "490401" + "1L8A-14C204-AAA".padEnd(16, '\u0000').map { String.format("%02X", it.code) }.joinToString("")
        val lines = listOf("0:" + hex.substring(0, 12), "1:" + hex.substring(12, 26), "2:" + hex.substring(26))
        assertEquals(listOf("1L8A-14C204-AAA"), ElmParser.parseCalibrationIds(lines))
    }

    @Test
    fun customReadings() {
        val r = CustomReading.validate("Trans temp", "22 1e1c", "7e0", "(A*256+B)/16", "°C")!!
        assertEquals("621E1C", r.replyHeader)
        assertEquals(80.0, r.value(intArrayOf(0x05, 0x00))!!, 1e-9)
        assertEquals(r, CustomReading.decode(r.encode()))
        assertNotNull(CustomReading.problem("x", "3101FF", "", "A"))  // a routine, not a read: refused
        assertNotNull(CustomReading.problem("x", "010C", "", "A+"))
        assertNull(Formula.evaluate("A/0+", intArrayOf(1)))
        assertEquals(-40.0, Formula.evaluate("A-40", intArrayOf(0))!!, 1e-9)
    }

    @Test
    fun theDemoCarAnswersEveryDeeperCheck() = runBlocking {
        val c = ElmClient(DemoTransport().apply { open() })
        val r = Readiness.decode(ElmParser.extractData(c.command("0101"), "4101"))!!
        assertTrue(r.milOn); assertEquals(2, r.codeCount); assertEquals(listOf("Evaporative system"), r.incomplete.map { it.name })
        assertEquals(listOf("1L8A-14C204-AAA"), ElmParser.parseCalibrationIds(c.command("0904")))
        assertEquals(listOf(0x01, 0x02, 0x20), TestResults.supported(0, ElmParser.extractData(c.command("0600"), "4600")))
        assertEquals(2, TestResults.parse(ElmParser.extractData(c.command("0601"), "46")!!).size)
        val abs = c.sequence(listOf("ATSH 760" to 1000L, "ATCRA 768" to 1000L, ModuleScan.UDS_REQUEST to 3000L), listOf("ATCRA", "ATSH 7DF")).last()
        assertEquals(listOf("C1145-1C"), (ModuleScan.parseUds(abs) as ModuleScan.Answer.Codes).codes.map { it.label })
        val none = c.sequence(listOf("ATSH 726" to 1000L, ModuleScan.UDS_REQUEST to 3000L), listOf("ATSH 7DF")).last()
        assertTrue(ModuleScan.parseUds(none) is ModuleScan.Answer.Silent)
    }
}
