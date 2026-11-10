package com.obdreader.app.obd

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @After fun metric() { Units.mph = false; Units.fahrenheit = false; Units.pressure = Units.Pressure.KPA }

    @Test fun metricPassesThrough() {
        assertEquals(100.0, Units.value("0D", 100.0), 1e-9)
        assertEquals(90.0, Units.value("05", 90.0), 1e-9)
        assertEquals("km/h", Units.unit("0D", "km/h"))
    }

    @Test fun speedAndTemperatureAreSeparate() {
        Units.mph = true
        assertEquals(62.137, Units.value("0D", 100.0), 1e-3)
        assertEquals(90.0, Units.value("05", 90.0), 1e-9)   // temperature still °C
        assertEquals("mph", Units.unit("0D", "km/h"))
        assertEquals("°C", Units.unit("05", "°C"))
        Units.mph = false; Units.fahrenheit = true
        assertEquals(194.0, Units.value("05", 90.0), 1e-9)
        assertEquals(100.0, Units.value("0D", 100.0), 1e-9)
        assertEquals("°F", Units.unit("5C", "°C"))
    }

    @Test fun otherReadingsAreUntouched() {
        Units.mph = true; Units.fahrenheit = true
        assertEquals(14.2, Units.value("VOLT", 14.2), 1e-9)
        assertEquals("kPa", Units.unit("0B", "kPa"))
        assertEquals("70 mph", Units.speedText(112.65))
        assertEquals("230 °F", Units.temperatureText(110.0))
    }

    @Test fun pressureConvertsBoostAndManifold() {
        assertEquals(50.0, Units.value(Boost.KEY, 50.0), 1e-9)
        assertEquals(0, Units.decimals(Boost.KEY, 0))
        Units.pressure = Units.Pressure.PSI
        assertEquals(14.504, Units.value(Boost.KEY, 100.0), 1e-3)
        assertEquals("psi", Units.unit("0B", "kPa"))
        assertEquals(1, Units.decimals("0B", 0))
        Units.pressure = Units.Pressure.BAR
        assertEquals(0.6, Units.value(Boost.KEY, 60.0), 1e-9)
        assertEquals(2, Units.decimals(Boost.KEY, 0))
        assertEquals(14.2, Units.value("VOLT", 14.2), 1e-9)   // battery untouched
        assertEquals(Units.Pressure.KPA, Units.Pressure.from("nonsense"))
        assertEquals(Units.Pressure.BAR, Units.Pressure.from("BAR"))
    }
}
