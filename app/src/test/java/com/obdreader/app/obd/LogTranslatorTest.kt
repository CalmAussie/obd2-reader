package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogTranslatorTest {

    @Test
    fun singleReadingIsTranslated() {
        assertEquals("Engine RPM: 3,176 rpm", LogTranslator.describe("010C", listOf("410C319E")))
        assertEquals("Speed: 93 km/h", LogTranslator.describe("010D", listOf("410D5D")))
        assertEquals("Coolant: 90°C", LogTranslator.describe("0105", listOf("410582")))
    }

    @Test
    fun multiPidReplyListsEveryReading() {
        assertEquals("Engine RPM: 850 rpm, Speed: 0 km/h, Throttle sensor: 12%",
            LogTranslator.describe("010C0D11", listOf("410C0D480D00111F")))
    }

    @Test
    fun errorsAndSetupReadPlainly() {
        assertTrue(LogTranslator.describe("010C", listOf("NO DATA"))!!.startsWith("No answer from the car"))
        assertEquals("Turned off echo", LogTranslator.describe("ATE0", listOf("OK")))
        assertEquals("Set the protocol to ISO 15765-4 CAN (11-bit, 500 kbaud)", LogTranslator.describe("ATSP6", listOf("OK")))
        assertNull(LogTranslator.describe("03", listOf("4300")))
    }

    @Test
    fun supportedPidsAreCounted() {
        assertEquals("The car answered. It supports 31 readings in the 01–1F range", LogTranslator.describe("0100", listOf("4100FFFFFFFF")))
    }
}
