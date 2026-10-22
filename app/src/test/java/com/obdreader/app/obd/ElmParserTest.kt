package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmParserTest {

    @Test
    fun cleanLinesDropsEchoPromptAndSearching() {
        val lines = ElmParser.cleanLines("010C\rSEARCHING...\r410C1AF8\r\r>", "010C")
        assertEquals(listOf("410C1AF8"), lines)
    }

    @Test
    fun rpmIsDecoded() {
        val bytes = ElmParser.extractData(listOf("410C1AF8"), "410C")!!
        val rpm = Pids.dashboard.first { it.number == 0x0C }.decode(bytes)
        assertEquals(1726.0, rpm, 0.001)
    }

    @Test
    fun spacedResponseStillParses() {
        val bytes = ElmParser.extractData(listOf("41 05 7B"), "4105")!!
        assertEquals(83.0, Pids.dashboard.first { it.number == 0x05 }.decode(bytes), 0.001)
    }

    @Test
    fun noDataReturnsNull() {
        assertNull(ElmParser.extractData(listOf("NO DATA"), "410C"))
        assertEquals("NO DATA", ElmParser.errorIn(listOf("NO DATA")))
    }

    @Test
    fun everyModuleAnswersSupportedList() {
        // J1850 car, headers off: the gearbox module answers first and doesn't list 0120;
        // the engine module answers second and does.
        val lines = listOf("41 00 98 18 00 00", "41 00 BE 3E B8 11")
        val all = ElmParser.extractAll(lines, "4100")
        assertEquals(2, all.size)
        val set = all.flatMap { ElmParser.supportedFromBitmask(it, 0) }.toSet()
        assertTrue(0x20 in set)
        assertTrue(0x0C in set)
    }

    @Test
    fun supportedBitmask() {
        val set = ElmParser.supportedFromBitmask(intArrayOf(0xBE, 0x1F, 0xA8, 0x13), 0)
        assertTrue(0x01 in set)
        assertTrue(0x0C in set)
        assertTrue(0x0D in set)
        assertTrue(0x20 in set)
        assertTrue(0x02 !in set)
    }

    @Test
    fun dtcsOnCanSingleFrame() {
        assertEquals(listOf("P0133", "P0420"), ElmParser.parseDtcs(listOf("430201330420"), "43", isCan = true))
    }

    @Test
    fun dtcsOnCanMultiFrameRespectsCount() {
        val lines = listOf("00A", "0:430401330420", "1:C1230171AAAAAA")
        assertEquals(listOf("P0133", "P0420", "U0123", "P0171"), ElmParser.parseDtcs(lines, "43", isCan = true))
    }

    @Test
    fun dtcsOnLegacyProtocolSkipPadding() {
        assertEquals(listOf("P0133", "P0420"), ElmParser.parseDtcs(listOf("43013304200000"), "43", isCan = false))
    }

    @Test
    fun noDtcs() {
        assertEquals(emptyList<String>(), ElmParser.parseDtcs(listOf("4300"), "43", isCan = true))
    }

    @Test
    fun vinFromCanMultiFrame() {
        val vin = "1HGCM82633A004352"
        val hex = "490201" + vin.map { "%02X".format(it.code) }.joinToString("")
        val lines = mutableListOf("014", "0:" + hex.substring(0, 12))
        var i = 12
        var n = 1
        while (i < hex.length) {
            val end = minOf(i + 14, hex.length)
            lines += "$n:" + hex.substring(i, end)
            i = end
            n++
        }
        assertEquals(vin, ElmParser.parseVin(lines))
    }

    @Test
    fun vinFromLegacyProtocol() {
        val vin = "1HGCM82633A004352"
        val bytes = listOf(0, 0, 0) + vin.map { it.code }
        val lines = bytes.chunked(4).mapIndexed { idx, chunk ->
            "4902" + "%02X".format(idx + 1) + chunk.joinToString("") { "%02X".format(it) }
        }
        assertEquals(vin, ElmParser.parseVin(lines))
    }

    private fun legacyLines(vin: String) = (listOf(0, 0, 0) + vin.map { it.code }).chunked(4).mapIndexed { idx, chunk ->
        "4902" + "%02X".format(idx + 1) + chunk.joinToString("") { "%02X".format(it) }
    }

    private fun canLines(vin: String): List<String> {
        val hex = "490201" + vin.map { "%02X".format(it.code) }.joinToString("")
        return listOf("0:" + hex.substring(0, 12), "1:" + hex.substring(12, 26), "2:" + hex.substring(26))
    }

    @Test
    fun vinFromLegacyPartsOutOfOrder() {
        val vin = "4F2YZ04136KM12345".let { it.substring(0, 8) + VinDecoder.checkDigit(it) + it.substring(9) }
        assertEquals(vin, ElmParser.parseVin(legacyLines(vin).reversed()))
    }

    @Test
    fun vinWhenTwoModulesAnswer() {
        val vin = "4F2YZ04136KM12345".let { it.substring(0, 8) + VinDecoder.checkDigit(it) + it.substring(9) }
        // A second module sending a different (bad) VIN must not get mixed into the first.
        val other = "4F2YZ04130KM99999"
        assertEquals(vin, ElmParser.parseVin(canLines(other) + canLines(vin)))
        val legacyA = legacyLines(other); val legacyB = legacyLines(vin)
        assertEquals(vin, ElmParser.parseVin(legacyA.zip(legacyB).flatMap { listOf(it.first, it.second) }))
    }

    @Test
    fun vinValidity() {
        assertTrue(VinDecoder.isValid("1HGCM82633A004352"))
        assertTrue(!VinDecoder.isValid("1HGCM82643A004352"))
        assertTrue(VinDecoder.isValid("JM0EP10A200123456")) // check digit optional outside North America
        assertTrue(!VinDecoder.isValid("JM0EP10A2O0123456"))
    }

    @Test
    fun protocolDetection() {
        assertTrue(ElmParser.isCanProtocol("A6"))
        assertTrue(ElmParser.isCanProtocol("8"))
        assertTrue(!ElmParser.isCanProtocol("A3"))
    }

    @Test
    fun demoTransportRoundTrip() {
        val t = DemoTransport().apply { open() }
        val vin = ElmParser.parseVin(ElmParser.cleanLines(t.exchange("0902", 1000), "0902"))
        assertEquals("1FAHP3F27CL123456", vin)
        val dtcs = ElmParser.parseDtcs(ElmParser.cleanLines(t.exchange("03", 1000), "03"), "43", isCan = true)
        assertEquals(listOf("P0133", "P0420"), dtcs)
    }
}
