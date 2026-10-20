package com.obdreader.app.obd

import java.util.Locale

/**
 * What may not work with a given car, and why, shown after the user picks their car.
 *
 * OBD2 only makes a few readings compulsory; the rest are optional, and what a car reports
 * depends on its age, maker, protocol and engine type. These are expectations from cars like
 * it, not a test: the app still asks the car itself when it connects, and anything the car
 * does report is shown.
 */
object CarSupport {

    enum class Level(val label: String) {
        /** Nearly every car like this one leaves it out. */
        UNLIKELY("Probably won't work"),
        /** Often missing on cars like this. */
        MAYBE("May not work"),
        /** Works, but not as well as on newer cars. */
        LIMITED("Works, but slower"),
    }

    data class Item(val name: String, val level: Level, val reason: String)

    fun expectations(car: VehicleCatalog.Car): List<Item> {
        val model = car.model?.lowercase(Locale.US).orEmpty()
        val y = car.year
        val fuel = VehicleCatalog.engineFor(car)?.fuel
        val rec = VehicleCatalog.recommend(car)
        val protocol = rec.likely
        val can = protocol?.code in setOf("6", "7", "8", "9")
        val items = mutableListOf<Item>()

        if (VehicleCatalog.isElectric(car, model)) {
            items += Item(
                "Engine readings", Level.UNLIKELY,
                "Electric cars have no engine computer, so engine speed, coolant, throttle, load, airflow, timing, fuel level and oil temperature aren't reported. Speed and battery voltage usually still work.",
            )
            items += Item("Trouble codes", Level.MAYBE, "Many electric cars keep their fault codes in their own systems rather than standard OBD2, so few or none may show.")
            return items
        }

        // Fuel level.
        if (fuel == VehicleCatalog.Fuel.LPG) {
            items += Item("Fuel level", Level.UNLIKELY, "The LPG tank's level isn't sent over OBD2. If a fuel level shows at all, it's the petrol tank.")
        }
        val fordBuilt = car.make in VehicleCatalog.FORD_GROUP ||
            (car.make == "Mazda" && (model.startsWith("tribute") || model.startsWith("b-series")) && y <= 2006)
        when {
            fordBuilt && y < 2008 -> items += Item(
                "Fuel level", Level.UNLIKELY,
                "Ford-built cars from before about 2008 usually don't send fuel level over OBD2. The fuel gauge gets it from the tank sender through the instrument cluster, which the OBD2 port can't read.",
            )
            !can -> items += Item(
                "Fuel level", Level.MAYBE,
                "Fuel level is optional in OBD2, and many cars on the older protocols (before CAN, about 2008) don't report it.",
            )
        }

        // Oil temperature: a late addition to OBD2.
        items += if (y < 2010) Item(
            "Oil temp", Level.UNLIKELY,
            "Oil temperature was only added to OBD2 in the late 2000s, and almost no cars from before about 2010 report it.",
        ) else Item("Oil temp", Level.MAYBE, "Many cars don't report oil temperature over OBD2, even newer ones.")

        // Outside temperature.
        if (!can) items += Item("Ambient air", Level.MAYBE, "Outside temperature is optional, and older cars often only send it to the dashboard display.")

        // Airflow: most engines measure one or the other.
        items += Item(
            "Manifold pressure or MAF airflow", Level.MAYBE,
            "Most engines have either a manifold pressure (MAP) sensor or a mass airflow (MAF) sensor, not both, so one of these usually shows n/a.",
        )

        if (fuel == VehicleCatalog.Fuel.DIESEL) {
            items += Item("Timing advance", Level.UNLIKELY, "Diesel engines have no spark plugs, so there's no ignition timing to report.")
            items += Item(
                "Throttle", Level.MAYBE,
                "Many diesels report the accelerator pedal position instead of a throttle, so Throttle may be missing. The driving score then leans on speed and revs.",
            )
        }
        if (fuel == VehicleCatalog.Fuel.HYBRID) {
            items += Item(
                "Engine speed, throttle and load", Level.LIMITED,
                "The petrol engine switches off often in a hybrid, so these drop to zero whenever it does. That's normal, not a fault.",
            )
        }

        // VIN: mode 09 only became common in the mid-2000s.
        if (y < 2005) items += Item("VIN", Level.MAYBE, "Many cars from before about 2005 don't report their VIN over OBD2. Your chosen car is used instead.")

        // Older, slower protocols.
        if (!can && protocol != null) items += Item(
            "Update speed", Level.LIMITED,
            "This car most likely uses ${protocol.name}, an older protocol that answers one reading at a time, so numbers update a few times a second instead of 20 or more. They still glide; they just react a little later.",
        )

        // Notes the catalogue already has about partial OBD2 support.
        rec.note?.takeIf { it.contains("may not") || it.contains("only partly") || it.contains("missing") }?.let {
            items += Item("Some readings and trouble codes", Level.MAYBE, it)
        }
        return items.distinctBy { it.name }
    }
}
