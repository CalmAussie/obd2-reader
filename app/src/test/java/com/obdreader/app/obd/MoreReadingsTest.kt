package com.obdreader.app.obd

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The extra standard readings (service 01): decoding, names, notes and the demo car's answers. */
class MoreReadingsTest {

    @After fun metric() { Units.mph = false; Units.fahrenheit = false; Units.pressure = Units.Pressure.KPA }

    private fun pid(hex: String) = Pids.byHex(hex)!!
    private fun decode(hex: String, vararg bytes: Int) = pid(hex).decode(bytes)

    @Test fun noReadingIsListedTwice() {
        val numbers = Pids.all.map { it.number }
        assertEquals(numbers.size, numbers.toSet().size)
    }

    @Test fun dashboardIsUnchanged() {
        // The extras don't change what the Dashboard shows by default.
        assertEquals(listOf("0C", "0D", "11", "04", "05", "0F", "0B", "10", "0E", "2F", "46", "5C"), Pids.dashboard.map { it.hex })
    }

    @Test fun fuelTrimsAreCentredOn128() {
        assertEquals(0.0, decode("06", 128), 1e-9)
        assertEquals(-100.0, decode("07", 0), 1e-9)
        assertEquals(99.2, decode("08", 255), 0.05)
        assertEquals(10.16, decode("09", 141), 0.01)
    }

    @Test fun oxygenSensors() {
        assertEquals(0.45, decode("14", 90, 0x80), 1e-9)
        assertEquals(1.275, decode("1B", 255, 0xFF), 1e-9)
        // Wide-band: lambda from AB, 1.0 at 32768.
        assertEquals(1.0, decode("24", 0x80, 0x00, 0x66, 0x66), 1e-9)
        assertEquals(0.9, decode("2B", 0x73, 0x33, 0, 0), 0.001)
    }

    @Test fun oxygenSensorNamesFollowTheCarsLayout() {
        assertEquals("O2 bank 1 sensor 1 voltage", pid("14").label)
        assertEquals("O2 bank 2 sensor 1 voltage", pid("18").label)
        assertEquals("O2 bank 1 sensor 2 lambda", pid("25").label)
        // Most cars list PID 13: no renaming.
        assertTrue(Pids.o2Relabels(setOf(0x13, 0x14)).isEmpty())
        assertTrue(Pids.o2Relabels(null).isEmpty())
        // Four-bank cars (PID 1D only): 16 is bank 2 sensor 1, 1A bank 4 sensor 1.
        val four = Pids.o2Relabels(setOf(0x1D, 0x14))
        assertEquals("O2 bank 2 sensor 1 voltage", four["16"])
        assertEquals("O2 bank 4 sensor 1 lambda", four["2A"])
    }

    @Test fun pressures() {
        assertEquals(381.0, decode("0A", 127), 1e-9)
        assertEquals(300.0, decode("22", 0x0E, 0xD5), 0.1)
        assertEquals(120_000.0, decode("23", 0x2E, 0xE0), 1e-9)
        assertEquals(655_350.0, decode("59", 0xFF, 0xFF), 1e-9)
        // Evap pressure is signed.
        assertEquals(-50.0, decode("32", 0xFF, 0x38), 1e-9)
        assertEquals(8191.75, decode("32", 0x7F, 0xFF), 1e-9)
    }

    @Test fun temperatures() {
        assertEquals(560.0, decode("3C", 0x17, 0x70), 1e-9)
        // Exhaust gas: the first fitted sensor (byte A says which); NaN when none is.
        assertEquals(410.0, decode("78", 0x01, 0x11, 0x94, 0, 0, 0, 0, 0, 0), 1e-9)
        assertEquals(410.0, decode("79", 0x04, 0, 0, 0, 0, 0x11, 0x94, 0, 0), 1e-9)
        assertTrue(decode("78", 0x00, 0x11, 0x94, 0, 0, 0, 0, 0, 0).isNaN())
    }

    @Test fun otherReadings() {
        assertEquals(10.2, decode("2C", 0x1A), 0.01)
        assertEquals(14.2, decode("42", 0x37, 0x78), 1e-9)
        assertEquals(10.2, decode("52", 0x1A), 0.01)
        assertEquals(12.5, decode("5E", 0x00, 0xFA), 1e-9)
        assertEquals(-25.0, decode("61", 100), 1e-9)
        assertEquals(130.0, decode("62", 255), 1e-9)
        assertEquals(400.0, decode("63", 0x01, 0x90), 1e-9)
        assertEquals(2.0, decode("1F", 0x00, 0x78), 1e-9)
        // Total run time: B to E in seconds, shown in hours.
        assertEquals(1000.0, decode("7F", 0x07, 0x00, 0x36, 0xEE, 0x80, 0, 0, 0, 0, 0, 0, 0, 0), 1e-9)
    }

    @Test fun newTemperaturesAndPressuresFollowTheUnits() {
        Units.fahrenheit = true
        assertEquals(1112.0, Units.value("3C", 600.0), 1e-9)
        assertEquals("°F", Units.unit("78", "°C"))
        Units.pressure = Units.Pressure.BAR
        assertEquals(1200.0, Units.value("23", 120_000.0), 1e-9)
        assertEquals("bar", Units.unit("0A", "kPa"))
        // Evap pressure is in Pa and stays so.
        assertEquals(-50.0, Units.value("32", -50.0), 1e-9)
    }

    @Test fun fuelSystemStatusAndTypeAreWords() {
        assertEquals("Closed loop, using the oxygen sensors (normal)", FuelInfo.status(2))
        assertEquals("Open loop, engine still warming up", FuelInfo.status(1))
        assertNull(FuelInfo.status(3))
        assertTrue(FuelInfo.trimsActive(2) && FuelInfo.trimsActive(16) && !FuelInfo.trimsActive(4))
        assertEquals("Petrol", FuelInfo.type(1))
        assertEquals("Diesel", FuelInfo.type(4))
        assertEquals("Bi-fuel, running on diesel", FuelInfo.type(23))
        assertNull(FuelInfo.type(0))
        assertNull(FuelInfo.type(99))
        // Never graphed or put in a readout.
        assertFalse(Pids.extraNumbers.any { it.hex in Pids.TEXT_ONLY })
    }

    @Test fun trimsAndOxygenSensorsSayWhatTheyTellYou() {
        listOf("06", "07", "08", "09", "14", "15", "1B", "24", "2B").forEach { assertNotNull(it, Pids.about(it)) }
        assertTrue(Pids.about("14")!!.contains("0.1 V"))
        assertTrue(Pids.about("15")!!.contains("catalytic converter"))
        assertNull(Pids.about("0C"))
        assertNull(Pids.about("3C"))
    }

    @Test fun logReadsTheNewReadings() {
        assertEquals("Short-term fuel trim, bank 1: 10.2%", LogTranslator.describe("0106", listOf("41068D")))
        assertEquals("Fuel system status: Closed loop, using the oxygen sensors (normal)", LogTranslator.describe("0103", listOf("41030200")))
        assertEquals("Fuel type: Petrol", LogTranslator.describe("0151", listOf("415101")))
    }

    @Test fun demoCarAnswersEveryExtraItLists() = runBlocking {
        val c = ElmClient(DemoTransport().apply { open() })
        val supported = c.supportedPids()!!
        val listed = Pids.extra.filter { it.number in supported }
        assertEquals(listOf("03", "06", "07", "15", "1F", "23", "24", "32", "3C", "3E", "42", "51", "52", "5E"), listed.map { it.hex }.sorted())
        for (chunk in listed.chunked(ElmClient.MAX_MULTI_PIDS)) {
            val got = c.readPids(chunk)
            chunk.forEach { p -> assertNotNull("demo answers ${p.hex}", got[p]) }
        }
        // Every request was a read: nothing the guard would block.
        listed.forEach { assertTrue(ReadOnlyGuard.isAllowed("01" + it.hex)) }
    }
}
