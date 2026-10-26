package com.obdreader.app.obd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedReadingsTest {

    private fun reading(id: String) = EnhancedReadings.ALL.first { it.id == id }

    @Test
    fun readingsOnlyGoToTheMakesTheyWereFoundOn() {
        assertTrue(EnhancedReadings.forCar("Ford", "Focus", 2012).any { it.id == "ford.trans_temp" })
        // The Tribute is Ford-built, so it gets Ford's readings; a Mazda3 doesn't.
        assertTrue(EnhancedReadings.forCar("Mazda", "Tribute", 2006).any { it.id == "ford.trans_temp" })
        assertTrue(EnhancedReadings.forCar("Mazda", "Mazda3", 2015).isEmpty())
        assertTrue(EnhancedReadings.forCar("Nissan", "Leaf", 2015).any { it.id == "leaf.soc" })
        assertTrue(EnhancedReadings.forCar("Nissan", "Leaf", 2020).none { it.id.startsWith("leaf") })
        assertTrue(EnhancedReadings.forCar("Peugeot", "308", 2018).isEmpty())
        // Every request is a read the guard lets through, and every id is unique.
        EnhancedReadings.ALL.forEach { assertTrue(it.request, ReadOnlyGuard.isAllowed(it.request)) }
        assertEquals(EnhancedReadings.ALL.size, EnhancedReadings.ALL.map { it.id }.toSet().size)
        EnhancedReadings.IDENTIFIERS.forEach { assertTrue(ReadOnlyGuard.isAllowed("22" + it.did)) }
        assertTrue(ReadOnlyGuard.isAllowed("1A6D"))
    }

    @Test
    fun decodesValues() {
        assertEquals(78.0, reading("ford.trans_temp").decode(intArrayOf(0x04, 0xE0))!!, 0.01)
        assertEquals(-10.0, reading("ford.trans_temp").decode(intArrayOf(0xFF, 0x60))!!, 0.01)   // signed: below zero
        assertEquals(123456.0, reading("ford.odometer").decode(intArrayOf(0x01, 0xE2, 0x40))!!, 0.01)
        assertEquals(25.0, reading("ford.batt_temp").decode(intArrayOf(0x41))!!, 0.01)
        assertEquals(51.0, reading("opel.oil_life").decode(intArrayOf(0x82))!!, 0.2)
        assertNull(reading("ford.odometer").decode(intArrayOf(0x01)))   // too short: no value, not a guess
        assertEquals("78.0 °C", reading("ford.trans_temp").format(78.0))
        assertEquals("65%", reading("ford.oil_life").format(65.0))
        assertEquals("621E1C", reading("ford.trans_temp").replyHeader)
        assertEquals("5A6D", reading("opel.oil_life").replyHeader)
        assertEquals("7BB", reading("leaf.soc").reply)
        assertEquals("72E", reading("ford.odometer").reply)
    }

    @Test
    fun moduleIdentifiers() {
        assertEquals("1L8T-2C219-AB", EnhancedReadings.identifierText("1L8T-2C219-AB".map { it.code }.toIntArray()))
        assertEquals("0102A0FF", EnhancedReadings.identifierText(intArrayOf(1, 2, 0xA0, 0xFF)))
        assertNull(EnhancedReadings.identifierText(intArrayOf()))
        assertEquals(listOf("ECM-EngineControl"), ElmParser.parseEcuNames(listOf("490A01" + "ECM-EngineControl".padEnd(20, '\u0000').map { String.format("%02X", it.code) }.joinToString(""))))
    }

    @Test
    fun theDemoCarAnswersFordReadings() = runBlocking {
        val c = ElmClient(DemoTransport().apply { open() })
        val r = reading("ford.odometer")
        val lines = c.sequence(listOf("ATSH 726" to 1000L, "ATCRA 72E" to 1000L, r.request to 2000L), listOf("ATCRA", "ATSH 7DF")).last()
        assertEquals(123456.0, r.decode(ElmParser.extractData(lines, r.replyHeader)!!)!!, 0.01)
        val abs = c.sequence(listOf("ATSH 760" to 1000L, "22F187" to 2000L), listOf("ATSH 7DF")).last()
        assertEquals("1L8T-2C219-AB", EnhancedReadings.identifierText(ElmParser.extractData(abs, "62F187")))
        assertTrue(ElmParser.errorIn(c.sequence(listOf("ATSH 7E0" to 1000L, "220462" to 2000L), listOf("ATSH 7DF")).last()) != null)
    }
}
