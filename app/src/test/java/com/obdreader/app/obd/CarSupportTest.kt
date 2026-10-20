package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarSupportTest {
    private fun names(car: VehicleCatalog.Car) = CarSupport.expectations(car).associate { it.name to it.level }

    @Test fun fordBuiltTributeHasNoFuelLevel() {
        val n = names(VehicleCatalog.Car("Mazda", 2006, "Tribute", "2.3 I4"))
        assertEquals(CarSupport.Level.UNLIKELY, n["Fuel level"])
        assertEquals(CarSupport.Level.LIMITED, n["Update speed"])
    }

    @Test fun modernCanCarHasShortList() {
        val n = names(VehicleCatalog.Car("Mazda", 2020, "CX-5", "2.5"))
        assertTrue("Fuel level" !in n)
        assertTrue("Update speed" !in n)
        assertEquals(CarSupport.Level.MAYBE, n["Oil temp"])
    }

    @Test fun dieselHasNoTiming() {
        val n = names(VehicleCatalog.Car("Ford", 2015, "Ranger", "3.2 diesel"))
        assertEquals(CarSupport.Level.UNLIKELY, n["Timing advance"])
    }

    @Test fun electricCarSaysEngineReadingsAreMissing() {
        val n = names(VehicleCatalog.Car("Tesla", 2022, "Model 3"))
        assertEquals(CarSupport.Level.UNLIKELY, n["Engine readings"])
    }
}
