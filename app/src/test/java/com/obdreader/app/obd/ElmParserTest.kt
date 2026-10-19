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
        assertEquals("1FAHP3F20CL123456", vin)
        val dtcs = ElmParser.parseDtcs(ElmParser.cleanLines(t.exchange("03", 1000), "03"), "43", isCan = true)
        assertEquals(listOf("P0133", "P0420"), dtcs)
    }
}
