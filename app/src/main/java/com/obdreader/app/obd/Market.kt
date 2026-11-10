package com.obdreader.app.obd

/**
 * Where a car was sold new, which decides when it had to have OBD2. Worked out from the
 * phone's country to start with; the user can change it under Diagnostics → Where your car
 * was sold (an imported car follows the market it was built for).
 */
enum class Market(val label: String) {
    NORTH_AMERICA("United States and Canada"),
    EUROPE("Europe and the UK"),
    AUSTRALIA("Australia"),
    NEW_ZEALAND("New Zealand"),
    JAPAN("Japan"),
    CHINA("China"),
    INDIA("India"),
    OTHER("Somewhere else");

    /**
     * A note when a [year] car from here may predate the market's OBD2 rules, or null.
     * Dates are approximate: rules usually started with new models a year or two before all cars.
     */
    fun note(year: Int, diesel: Boolean): String? = when (this) {
        NORTH_AMERICA -> if (year in 1996..1997) "Canadian cars weren't required to have OBD2 until 1998, though most from 1996 on have it." else null
        EUROPE -> if (year < (if (diesel) 2004 else 2001)) "Cars sold new in Europe weren't required to have OBD2 (called EOBD there) until 2001 for petrol and 2004 for diesel, so earlier ones may only partly support it." else null
        AUSTRALIA -> if (year < (if (diesel) 2007 else 2006)) "Cars sold new in Australia weren't required to have full OBD2 until about 2006 for petrol and 2007 for diesel." else null
        NEW_ZEALAND -> if (year < 2008) "New Zealand has no OBD2 rule of its own: it depends on the market the car was built for. Japanese imports from before about 2008 may only partly support standard OBD2." else null
        JAPAN -> if (year < 2008) "Japanese-market cars weren't required to support standard OBD2 until about 2008, so earlier ones may only partly support it." else null
        CHINA -> if (year < 2008) "Cars sold new in China weren't required to have OBD until about 2008, so earlier ones may only partly support it." else null
        INDIA -> if (year < 2020) "India brought OBD in gradually: basic OBD from about 2010 and full OBD2 from 2020, so earlier cars may only partly support it." else null
        OTHER -> if (year < 2008) "When OBD2 became required varies by country, from 1996 in the US to 2008 or later in many others, so older cars may only partly support it." else null
    }

    companion object {
        /** The market the app uses for its notes (set from the saved choice, or the phone's country). */
        @Volatile var current: Market = OTHER

        private val EUROPE_COUNTRIES = setOf(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
            "PL", "PT", "RO", "SK", "SI", "ES", "SE", "GB", "IS", "LI", "NO", "CH", "AD", "MC", "SM", "VA", "GI", "IM", "JE", "GG",
        )

        /** The market for a two-letter country code (the phone's region). */
        fun fromCountry(code: String?): Market = when (code?.uppercase()) {
            "US", "CA", "PR" -> NORTH_AMERICA
            "AU" -> AUSTRALIA
            "NZ" -> NEW_ZEALAND
            "JP" -> JAPAN
            "CN" -> CHINA
            "IN" -> INDIA
            in EUROPE_COUNTRIES -> EUROPE
            else -> OTHER
        }

        fun byName(name: String?): Market? = values().firstOrNull { it.name == name }
    }
}
