package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The offline vPIC decoder. VINs here are made up for the test (valid check digits, random serials). */
class VinDetailsTest {

    private val data: File? = listOf(File("src/main/assets/vpic/vin-data.dat"), File("app/src/main/assets/vpic/vin-data.dat"))
        .firstOrNull { it.isFile }

    private fun decode(vin: String): VinDetails.Details? {
        val f = data ?: error("vin-data.dat missing")
        return VinDetails.decode(vin, 2026) { f.inputStream() }
    }

    @Test fun patternKeys() {
        assertTrue(VinDetails.matches("*B[0-2]", "XB1ZZ|9ABCDEFG"))
        assertFalse(VinDetails.matches("*B[0-2]", "XB3ZZ|9ABCDEFG"))
        assertTrue(VinDetails.matches("[A-D]**|", "CXY|Z"))
        // vPIC reads "1-A" in a set as the characters 1 and A.
        assertTrue(VinDetails.matches("[1-A]", "A"))
        assertFalse(VinDetails.matches("[1-A]", "5"))
        assertFalse(VinDetails.matches("ABCDEF", "ABC"))
    }

    @Test fun modelYears() {
        // Car with a letter in position 7: 2010 cycle, certain.
        assertEquals(listOf(2012), VinDetails.modelYears("1FADP3HG1C9PCF43E", true, 2026))
        // Car with a digit in position 7: 1980 cycle.
        assertEquals(listOf(2005), VinDetails.modelYears("1FAHP29D65RCJV3KH", true, 2026))
        // Heavy truck: position 7 says nothing, so both readings are tried.
        assertEquals(listOf(2018, 1988), VinDetails.modelYears("1FTAW4S88J7WFKGYT", false, 2026))
        // More than two years ahead goes back a cycle.
        assertEquals(listOf(2001), VinDetails.modelYears("1FTAW4S8811WFKGYT", false, 2026))
        assertEquals(emptyList<Int>(), VinDetails.modelYears("1FTAW4S880ZWFKGYT", false, 2026))
    }

    @Test fun decodesModelTrimBodyEnginePlant() {
        val d = decode("1FMVK8HB0MLH8DPVJ")!!
        assertEquals("Ford", d.make)
        assertEquals(2021, d.modelYear)
        assertEquals("Explorer", d.model)
        assertEquals("2021 Ford Explorer Platinum", d.title)
        assertEquals("SUV, 4 doors", d.body)
        assertEquals("SUV / MPV", d.vehicleTypeName)
        assertEquals("3.3 L V6 petrol", d.engine)
        assertEquals("Wayne, Michigan, United States", d.plant)
        assertEquals("4WD", d.drive)
        assertEquals("4WD/4-Wheel Drive/4x4", d.values[VinDetails.DRIVE])
        // Transmission comes from vPIC's vehicle specs for the model and trim, not the VIN patterns.
        assertEquals("Automatic", d.transmission)
    }

    @Test fun engineCodeFillsEngineDetails() {
        val d = decode("JTDBE02E096RG28LR")!!
        assertEquals("Corolla", d.model)
        assertEquals("2AZ-FE", d.engineCode)
        assertEquals("2.4 L 4-cylinder petrol, 158 hp", d.engine)
    }

    @Test fun electricTruck() {
        val d = decode("1FTBX2LKXRFT0LZR9")!!
        assertEquals("F-250", d.model)
        assertEquals("Electric", d.fuel)
        assertEquals("battery electric", d.engine)
    }

    @Test fun suggestsTheCarFromTheVin() {
        assertEquals(VehicleCatalog.Car("Ford", 2021, "Explorer", null), VinDetails.suggestedCar(decode("1FMVK8HB0MLH8DPVJ")!!))
        // The app's list has only a 1.8 for a 2009 Corolla, so the VIN's 2.4 isn't matched to it.
        assertEquals(VehicleCatalog.Car("Toyota", 2009, "Corolla", null), VinDetails.suggestedCar(decode("JTDBE02E096RG28LR")!!))
        val d = VinDetails.Details("X", "Toyota", null, null, null, 2020, mapOf(VinDetails.MODEL to "Corolla", VinDetails.LITRES to "1.8", VinDetails.ELECTRIFICATION to "Strong HEV (Hybrid Electric Vehicle)"))
        assertEquals("1.8 hybrid", VinDetails.suggestedCar(d)?.engine)
        assertNull(VinDetails.suggestedCar(d.copy(modelYear = null)))
        assertNull(VinDetails.suggestedCar(d.copy(make = "Not a make")))
    }

    @Test fun noYearOutsideNorthAmericaWithoutPatterns() {
        val d = decode("WVW6TWJU1MSXZLSXN")
        if (d != null && d.values.isEmpty()) assertNull(d.modelYear)
    }

    @Test fun unknownMakerCodeGivesNothing() {
        assertNull(decode("ZZZ00000000000000"))
    }

    @Test fun dataNamesItsSource() {
        val f = data ?: return
        val head = java.util.zip.GZIPInputStream(f.inputStream()).bufferedReader().useLines { it.take(2).toList() }
        assertTrue(head[1].contains("NHTSA vPIC"))
    }
}
