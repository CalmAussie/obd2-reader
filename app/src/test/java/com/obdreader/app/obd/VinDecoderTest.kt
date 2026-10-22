package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VinDecoderTest {

    @Test
    fun fordNorthAmericaWithYear() {
        val info = VinDecoder.decode("1FAHP3F27CL123456")
        assertEquals("Ford", info.make)
        assertEquals(2012, info.modelYear)
    }

    @Test
    fun earlyTwoThousandsUsesDigitCycle() {
        // Position 7 is a digit, so "4" means 2004, not 2034.
        assertEquals(2004, VinDecoder.decode("1FAFP34PX4W123456").modelYear)
        assertEquals(2000, VinDecoder.decode("1HGCG1658YA123456").modelYear)
    }

    @Test
    fun hondaAndMazdaMakes() {
        assertEquals("Honda", VinDecoder.decode("JHMGE88409S123456").make)
        assertEquals("Honda", VinDecoder.decode("MRHGE8840AP123456").make)
        assertEquals("Mazda", VinDecoder.decode("JM0BL10F200123456").make)
        assertEquals("Ford", VinDecoder.decode("6FPAAAJGCM8D12345").make)
    }

    @Test
    fun nonNorthAmericanVinHasNoYear() {
        assertNull(VinDecoder.decode("JM0BL10F200123456").modelYear)
    }

    @Test
    fun unknownOrInvalid() {
        assertNull(VinDecoder.decode("ZZZCT41DAEU123456").make) // a maker code the app doesn't list
        assertNull(VinDecoder.decode("SHORT").make)
    }

    @Test
    fun protocolLookup() {
        assertEquals("SAE J1850 PWM", ObdProtocols.byCode("A1")?.name)
        assertEquals("ISO 9141-2", ObdProtocols.byCode("3")?.name)
        assertNull(ObdProtocols.byCode("0"))
    }

    @Test
    fun koreanMakesAreRecognised() {
        assertEquals("Hyundai", VinDecoder.decode("KMHD841CBJU123456").make)
        assertEquals("Kia", VinDecoder.decode("KNADN412BK6123456").make)
    }

    @Test
    fun sampleVinsDecodeToTheChosenCar() {
        val mazda = VinDecoder.decode(VinDecoder.sampleVin("Mazda", 2006))
        assertEquals("Mazda", mazda.make)
        assertEquals(2006, mazda.modelYear)
        val toyota = VinDecoder.decode(VinDecoder.sampleVin("Toyota", 2018))
        assertEquals("Toyota", toyota.make)
        assertEquals("1FAHP3F27CL123456".length, VinDecoder.sampleVin(null, null).length)
        assertEquals("Ford", VinDecoder.decode(VinDecoder.sampleVin(null, null)).make)
    }

    @Test
    fun brandsMatchAcrossNameStyles() {
        org.junit.Assert.assertTrue(VinDecoder.sameMake("Lexus (Toyota)", "Lexus"))
        org.junit.Assert.assertTrue(VinDecoder.sameMake("SEAT / Cupra", "Cupra"))
        org.junit.Assert.assertFalse(VinDecoder.sameMake("Ford", "Mazda"))
    }

    @Test
    fun checkDigitFollowsTheStandard() {
        assertEquals('X', VinDecoder.checkDigit("1M8GDM9AXKP042788")) // the textbook example
        assertEquals('1', VinDecoder.checkDigit("11111111111111111"))
        val v = VinDecoder.sampleVin("Ford", 2012)
        assertEquals(VinDecoder.checkDigit(v), v[8])
    }
}
