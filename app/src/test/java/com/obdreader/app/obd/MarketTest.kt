package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketTest {
    @Test fun countriesMapToMarkets() {
        assertEquals(Market.NORTH_AMERICA, Market.fromCountry("US"))
        assertEquals(Market.EUROPE, Market.fromCountry("gb"))
        assertEquals(Market.EUROPE, Market.fromCountry("DE"))
        assertEquals(Market.AUSTRALIA, Market.fromCountry("AU"))
        assertEquals(Market.INDIA, Market.fromCountry("IN"))
        assertEquals(Market.OTHER, Market.fromCountry("BR"))
        assertEquals(Market.OTHER, Market.fromCountry(null))
    }

    @Test fun notesFollowEachMarketsDates() {
        assertNull(Market.EUROPE.note(2001, diesel = false))
        assertTrue(Market.EUROPE.note(2002, diesel = true)!!.contains("2004 for diesel"))
        assertTrue(Market.AUSTRALIA.note(2005, diesel = false)!!.contains("Australia"))
        assertNull(Market.AUSTRALIA.note(2006, diesel = false))
        assertNull(Market.NORTH_AMERICA.note(2000, diesel = false))
        assertTrue(Market.OTHER.note(2005, diesel = false)!!.contains("varies by country"))
        assertNull(Market.OTHER.note(2010, diesel = false))
    }

    @Test fun recommendationUsesTheMarket() {
        val car = VehicleCatalog.Car("Toyota", 2004, "Corolla")
        assertTrue(VehicleCatalog.recommend(car, Market.AUSTRALIA).note!!.contains("Australia"))
        assertFalse(VehicleCatalog.recommend(car, Market.NORTH_AMERICA).note?.contains("Australia") == true)
        assertNull(VehicleCatalog.recommend(car, Market.NORTH_AMERICA).note)
        // Sold only in North America: its dates apply wherever the user is.
        assertFalse(VehicleCatalog.recommend(VehicleCatalog.Car("Mercury", 2005, "Mariner"), Market.AUSTRALIA).note?.contains("Australia") == true)
    }

    @Test fun hyundaiAndKiaAreListedWithEngines() {
        assertTrue("Hyundai" in VehicleCatalog.makeNames)
        assertTrue("Kia" in VehicleCatalog.makeNames)
        assertTrue(VehicleCatalog.engines("Hyundai", 2015, "i30").any { it.name == "2.0 I4" })
        assertTrue(VehicleCatalog.engines("Kia", 2020, "Sportage").isNotEmpty())
        assertEquals("6", VehicleCatalog.recommend(VehicleCatalog.Car("Kia", 2015, "Rio")).order.first())
        assertTrue(VehicleCatalog.recommend(VehicleCatalog.Car("Hyundai", 2015, "i30")).note!!.contains("Hyundai"))
        assertTrue(VehicleCatalog.isElectric(VehicleCatalog.Car("Kia", 2023, "EV6"), "ev6"))
        assertFalse(VehicleCatalog.isElectric(VehicleCatalog.Car("BYD", 2023, "Song Plus DM-i / Sealion 6"), "song plus dm-i / sealion 6"))
    }

    @Test fun unitsStartFromThePhonesCountry() {
        assertTrue(Units.defaultMph("US")); assertTrue(Units.defaultFahrenheit("US")); assertEquals(Units.Pressure.PSI, Units.defaultPressure("US"))
        assertTrue(Units.defaultMph("GB")); assertFalse(Units.defaultFahrenheit("GB"))
        assertFalse(Units.defaultMph("AU")); assertEquals(Units.Pressure.KPA, Units.defaultPressure("AU"))
    }
}
