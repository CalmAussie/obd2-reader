package com.obdreader.app.obd

import java.util.Locale

/**
 * Makes, models and the OBD2 protocols they typically use, so a user whose car won't
 * connect on Automatic can say what it is. The result only changes the order the app
 * tries protocols in; it still falls back to every protocol, so a wrong guess costs
 * time, not the connection.
 */
object VehicleCatalog {

    const val FIRST_YEAR = 2000
    const val LAST_YEAR = 2026
    const val OTHER_MAKE = "Other make"

    enum class Fuel { PETROL, DIESEL, LPG, HYBRID }

    /** An engine option, with the years it was offered and its approximate redline. */
    data class Engine(val name: String, val from: Int, val to: Int, val redline: Int, val fuel: Fuel = Fuel.PETROL)

    data class Model(val name: String, val from: Int, val to: Int, val engines: List<Engine> = emptyList())
    data class Make(val name: String, val models: List<Model>)

    data class Car(val make: String, val year: Int, val model: String?, val engine: String? = null) {
        val label: String get() = listOfNotNull(year.toString(), make.takeIf { it != OTHER_MAKE }, model, engine).joinToString(" ")
    }

    data class Recommendation(
        /** Protocol codes in the order to try them. Always contains every protocol. */
        val order: List<String>,
        /** Short note shown to the user about this car's OBD2 support, if any. */
        val note: String?,
        /** The engine's approximate redline in rpm, used to scale the RPM dial; null if unknown. */
        val redline: Int? = null,
        /** True when [redline] is for the chosen engine; false when it's the model's (no engine chosen, or one not in the list). */
        val redlineExact: Boolean = true,
    ) {
        /** Where the rev dial ends for this car: see [dialMax]. */
        val dialMax: Int get() = dialMax(redline)
        val likely: ObdProtocols.Protocol? get() = ObdProtocols.byCode(order.first())
    }

    private fun e(name: String, from: Int, to: Int, redline: Int, fuel: Fuel = Fuel.PETROL) = Engine(name, from, to, redline, fuel)
    private val D = Fuel.DIESEL

    internal val BASE_MAKES: List<Make> = listOf(
        Make("Ford", listOf(
            Model("Escape", 2001, 2026, listOf(
                e("2.0 I4", 2001, 2004, 6500), e("2.3 I4", 2005, 2008, 6500), e("3.0 V6", 2001, 2008, 6250),
                e("2.5 I4", 2009, 2026, 6500), e("1.5 EcoBoost", 2013, 2026, 6500), e("2.0 EcoBoost", 2013, 2026, 6500),
            )), Model("EcoSport", 2013, 2020), Model("Everest", 2015, 2026),
            Model("Explorer", 2000, 2026), Model("F-150", 2000, 2026), Model("Falcon", 2000, 2016, listOf(
                e("4.0 I6", 2000, 2016, 6000), e("4.0 I6 turbo", 2002, 2016, 6000), e("4.0 I6 LPG", 2000, 2016, 6000, Fuel.LPG),
                e("5.0 V8", 2000, 2002, 5500), e("5.4 V8", 2002, 2010, 6000), e("5.0 V8 supercharged", 2010, 2016, 6250),
                e("2.0 EcoBoost", 2012, 2016, 6500),
            )),
            Model("Fiesta", 2004, 2019), Model("Focus", 2002, 2018, listOf(
                e("1.8", 2002, 2005, 6500), e("2.0", 2002, 2018, 6500), e("2.5 turbo (XR5)", 2006, 2010, 6500),
                e("2.0 diesel", 2008, 2015, 4500, D), e("1.6 EcoBoost", 2011, 2015, 6500), e("2.0 EcoBoost (ST)", 2012, 2018, 6800),
                e("1.5 EcoBoost", 2015, 2018, 6500), e("2.3 EcoBoost (RS)", 2016, 2018, 6800),
            )), Model("Kuga", 2012, 2020),
            Model("Laser", 2000, 2002), Model("Mondeo", 2001, 2019), Model("Mustang", 2000, 2026, listOf(
                e("3.8 V6", 2000, 2004, 6000), e("4.6 V8", 2000, 2010, 6000), e("4.0 V6", 2005, 2010, 6000),
                e("3.7 V6", 2011, 2014, 7000), e("5.0 V8", 2011, 2026, 7500), e("2.3 EcoBoost", 2015, 2026, 6500),
            )),
            Model("Puma", 2020, 2026), Model("Ranger", 2000, 2026, listOf(
                e("2.6 petrol", 2000, 2006, 5500), e("2.5 diesel", 2000, 2011, 4500, D), e("3.0 diesel", 2006, 2011, 4500, D),
                e("2.2 diesel", 2011, 2022, 4500, D), e("3.2 diesel", 2011, 2022, 4500, D), e("2.0 bi-turbo diesel", 2018, 2026, 4500, D),
                e("3.0 V6 diesel", 2022, 2026, 4500, D), e("3.0 V6 EcoBoost (Raptor)", 2023, 2026, 6000),
            )),
            Model("Territory", 2004, 2016, listOf(
                e("4.0 I6", 2004, 2016, 6000), e("4.0 I6 turbo", 2006, 2011, 6000), e("2.7 V6 diesel", 2011, 2016, 4500, D),
            )),
            Model("Transit", 2000, 2026),
        )),
        Make("Mazda", listOf(
            Model("121 / Mazda2", 2000, 2026), Model("323", 2000, 2003), Model("626", 2000, 2002),
            Model("B-Series / Bravo", 2000, 2006), Model("BT-50", 2006, 2026, listOf(
                e("2.5 diesel", 2006, 2011, 4500, D), e("3.0 diesel", 2006, 2011, 4500, D), e("2.2 diesel", 2011, 2020, 4500, D),
                e("3.2 diesel", 2011, 2020, 4500, D), e("3.0 diesel (2020 on)", 2020, 2026, 4500, D),
            )), Model("CX-3", 2015, 2026),
            Model("CX-30", 2019, 2026), Model("CX-5", 2012, 2026, listOf(
                e("2.0", 2012, 2026, 6500), e("2.5", 2012, 2026, 6500), e("2.2 diesel", 2012, 2026, 5000, D), e("2.5 turbo", 2018, 2026, 6000),
            )), Model("CX-60", 2022, 2026),
            Model("CX-7", 2007, 2012), Model("CX-9", 2007, 2023), Model("CX-90", 2023, 2026),
            Model("Mazda3", 2004, 2026, listOf(
                e("2.0", 2004, 2026, 6500), e("2.3", 2004, 2009, 6500), e("2.3 turbo (MPS)", 2007, 2013, 6500), e("2.5", 2009, 2026, 6500),
            )),
            Model("Mazda6", 2002, 2026, listOf(
                e("2.3", 2002, 2007, 6500), e("2.3 turbo (MPS)", 2006, 2007, 6500), e("2.5", 2008, 2026, 6500),
                e("2.2 diesel", 2009, 2021, 5000, D), e("2.5 turbo", 2018, 2026, 6000),
            )), Model("MPV", 2000, 2006),
            Model("MX-5", 2000, 2026, listOf(e("1.8", 2000, 2005, 7000), e("2.0", 2005, 2026, 7000), e("1.5", 2015, 2026, 7500))), Model("Premacy", 2000, 2004), Model("RX-8", 2003, 2012, listOf(e("1.3 rotary", 2003, 2012, 9000))),
            Model("Tribute", 2001, 2006, listOf(e("2.0 I4", 2001, 2004, 6500), e("2.3 I4", 2004, 2006, 6500), e("3.0 V6", 2001, 2006, 6250))),
        )),
        Make("Honda", listOf(
            Model("Accord", 2000, 2026, listOf(
                e("2.3", 2000, 2002, 6800), e("2.4", 2003, 2019, 6800), e("3.0 V6", 2003, 2007, 6800), e("3.5 V6", 2008, 2017, 6800),
            )), Model("Accord Euro", 2003, 2015), Model("City", 2003, 2026),
            Model("Civic", 2000, 2026, listOf(
                e("1.7", 2000, 2005, 6800), e("2.0 Type R", 2002, 2005, 8000), e("1.3 hybrid", 2003, 2011, 6000, Fuel.HYBRID),
                e("1.8", 2006, 2015, 6800), e("2.0", 2006, 2026, 6800), e("1.5 turbo", 2016, 2026, 6500),
            )),
            Model("CR-V", 2000, 2026, listOf(
                e("2.0", 2000, 2006, 6800), e("2.4", 2002, 2017, 6800), e("1.5 turbo", 2017, 2026, 6500),
            )), Model("CR-Z", 2011, 2016),
            Model("HR-V", 2015, 2026), Model("Insight", 2000, 2014), Model("Integra", 2000, 2006),
            Model("Jazz / Fit", 2001, 2020, listOf(e("1.3", 2001, 2008, 6500), e("1.5", 2001, 2020, 6800))), Model("Legend", 2000, 2012), Model("Odyssey", 2000, 2021),
            Model("Pilot", 2003, 2026), Model("Prelude", 2000, 2001), Model("S2000", 2000, 2009, listOf(e("2.0", 2000, 2004, 9000), e("2.2", 2004, 2009, 8000))),
            Model("ZR-V", 2023, 2026),
        )),
        Make("Toyota", listOf(
            Model("86 / GR86", 2012, 2026, listOf(e("2.0", 2012, 2021, 7400), e("2.4", 2022, 2026, 7500))),
            Model("Aurion", 2006, 2017), Model("C-HR", 2017, 2026), Model("Celica", 2000, 2006),
            Model("Camry", 2000, 2026, listOf(
                e("2.2", 2000, 2001, 6000), e("2.4", 2002, 2011, 6000), e("2.5", 2011, 2026, 6200), e("3.0 V6", 2000, 2006, 6200),
                e("3.5 V6", 2006, 2024, 6300), e("2.5 hybrid", 2010, 2026, 5500, Fuel.HYBRID),
            )),
            Model("Corolla", 2000, 2026, listOf(
                e("1.8", 2000, 2018, 6400), e("2.0", 2018, 2026, 6600), e("1.8 hybrid", 2019, 2026, 5200, Fuel.HYBRID),
            )),
            Model("Echo / Yaris", 2000, 2026, listOf(e("1.3", 2000, 2011, 6400), e("1.5", 2000, 2026, 6400))),
            Model("Fortuner", 2015, 2026), Model("HiAce", 2000, 2026),
            Model("HiLux", 2000, 2026, listOf(
                e("2.7 petrol", 2000, 2026, 5600), e("4.0 V6 petrol", 2005, 2015, 6000), e("2.5 diesel", 2005, 2015, 4400, D),
                e("3.0 diesel", 2000, 2015, 4400, D), e("2.4 diesel", 2015, 2026, 4400, D), e("2.8 diesel", 2015, 2026, 4400, D),
            )),
            Model("Kluger / Highlander", 2003, 2026, listOf(
                e("3.0 V6", 2003, 2007, 6200), e("3.5 V6", 2007, 2026, 6300), e("2.5 hybrid", 2021, 2026, 5500, Fuel.HYBRID),
            )),
            Model("LandCruiser", 2000, 2026, listOf(
                e("4.7 V8 petrol", 2000, 2011, 5400), e("4.2 diesel", 2000, 2007, 4000, D), e("4.5 V8 diesel", 2007, 2021, 4000, D),
                e("4.6 V8 petrol", 2012, 2021, 5800), e("3.3 V6 diesel", 2021, 2026, 4400, D),
            )),
            Model("LandCruiser Prado", 2000, 2026, listOf(
                e("3.4 V6", 2000, 2002, 5200), e("4.0 V6", 2003, 2017, 6000), e("3.0 diesel", 2000, 2015, 4400, D),
                e("2.8 diesel", 2015, 2026, 4400, D),
            )),
            Model("Prius", 2001, 2026, listOf(
                e("1.5 hybrid", 2001, 2009, 5000, Fuel.HYBRID), e("1.8 hybrid", 2009, 2022, 5200, Fuel.HYBRID),
                e("2.0 hybrid", 2023, 2026, 5500, Fuel.HYBRID),
            )),
            Model("RAV4", 2000, 2026, listOf(
                e("2.0", 2000, 2018, 6400), e("2.4", 2005, 2012, 6000), e("2.5", 2012, 2026, 6200),
                e("2.5 hybrid", 2016, 2026, 5500, Fuel.HYBRID),
            )),
            Model("Supra", 2019, 2026, listOf(e("2.0 turbo", 2019, 2026, 6500), e("3.0 I6 turbo", 2019, 2026, 6500))),
            Model("Tacoma", 2000, 2026), Model("Tarago", 2000, 2019), Model("Tundra", 2000, 2026),
        )),
        Make("BMW", listOf(
            Model("1 Series", 2004, 2026, listOf(
                e("2.0", 2004, 2011, 6500), e("2.0 diesel", 2004, 2019, 4800, D), e("3.0 I6 turbo", 2008, 2019, 7000),
                e("2.0 turbo", 2012, 2026, 6500), e("1.5 turbo", 2015, 2026, 6500),
            )),
            Model("2 Series", 2014, 2026),
            Model("3 Series", 2000, 2026, listOf(
                e("2.0", 2000, 2012, 6500), e("2.5 I6", 2000, 2007, 6500), e("3.0 I6", 2000, 2008, 7000),
                e("3.0 I6 turbo", 2006, 2026, 7000), e("2.0 turbo", 2012, 2026, 6500), e("2.0 diesel", 2002, 2026, 4800, D),
            )),
            Model("4 Series", 2013, 2026),
            Model("5 Series", 2000, 2026, listOf(
                e("2.5 I6", 2000, 2005, 6500), e("3.0 I6", 2000, 2010, 6500), e("4.4 V8", 2000, 2010, 6500),
                e("3.0 I6 turbo", 2007, 2026, 7000), e("2.0 turbo", 2011, 2026, 6500), e("2.0 diesel", 2007, 2026, 4800, D),
                e("3.0 diesel", 2000, 2026, 4800, D),
            )),
            Model("6 Series", 2003, 2019), Model("7 Series", 2000, 2026),
            Model("M3", 2000, 2026, listOf(
                e("3.2 I6", 2000, 2006, 8000), e("4.0 V8", 2007, 2013, 8300), e("3.0 I6 twin-turbo", 2014, 2026, 7200),
            )),
            Model("M5", 2000, 2026), Model("X1", 2009, 2026),
            Model("X3", 2003, 2026, listOf(
                e("2.5 I6", 2003, 2006, 6500), e("3.0 I6", 2003, 2010, 6500), e("2.0 diesel", 2004, 2026, 4800, D),
                e("3.0 diesel", 2004, 2026, 4800, D), e("2.0 turbo", 2011, 2026, 6500), e("3.0 I6 turbo", 2011, 2026, 7000),
            )),
            Model("X5", 2000, 2026, listOf(
                e("3.0 I6", 2000, 2010, 6500), e("4.4 V8", 2000, 2006, 6000), e("4.8 V8", 2004, 2010, 6500),
                e("3.0 diesel", 2003, 2026, 4800, D), e("3.0 I6 turbo", 2010, 2026, 7000),
            )),
            Model("X6", 2008, 2026), Model("Z3", 2000, 2002),
            Model("Z4", 2003, 2026, listOf(
                e("2.5 I6", 2003, 2008, 6500), e("3.0 I6", 2003, 2016, 7000), e("3.0 I6 turbo", 2009, 2026, 7000),
                e("2.0 turbo", 2011, 2026, 6500),
            )),
        )),
        Make("Mercedes-Benz", listOf(
            Model("A-Class", 2000, 2026, listOf(
                e("2.0", 2005, 2012, 6200), e("1.6 turbo", 2012, 2018, 6000), e("2.0 turbo", 2012, 2026, 6500),
                e("1.3 turbo", 2018, 2026, 6200),
            )),
            Model("B-Class", 2005, 2026),
            Model("C-Class", 2000, 2026, listOf(
                e("2.6 V6", 2000, 2005, 6000), e("3.2 V6", 2000, 2005, 6000), e("1.8 supercharged", 2002, 2007, 6500),
                e("2.1 diesel", 2000, 2018, 4600, D), e("3.5 V6", 2005, 2014, 6500), e("1.8 turbo", 2008, 2014, 6000),
                e("6.2 V8 (AMG)", 2007, 2014, 7200), e("2.0 turbo", 2014, 2026, 6200), e("4.0 V8 twin-turbo (AMG)", 2015, 2021, 7000),
            )),
            Model("CLA", 2013, 2026), Model("CLK", 2000, 2010), Model("CLS", 2004, 2026),
            Model("E-Class", 2000, 2026, listOf(
                e("2.6 V6", 2000, 2005, 6000), e("3.2 V6", 2000, 2005, 6000), e("5.0 V8", 2000, 2006, 6000),
                e("2.1 diesel", 2000, 2016, 4600, D), e("3.0 V6 diesel", 2005, 2018, 4600, D), e("3.5 V6", 2005, 2016, 6500),
                e("5.5 V8", 2006, 2016, 6000), e("6.2 V8 (AMG)", 2006, 2011, 7200), e("2.0 turbo", 2013, 2026, 6200),
                e("3.0 V6 turbo", 2016, 2026, 6200),
            )),
            Model("G-Class", 2000, 2026), Model("GLA", 2014, 2026), Model("GLC", 2015, 2026),
            Model("GLE / M-Class", 2000, 2026, listOf(
                e("3.2 V6", 2000, 2005, 6000), e("5.0 V8", 2002, 2007, 6000), e("2.7 diesel", 2001, 2005, 4600, D),
                e("3.5 V6", 2005, 2015, 6500), e("3.0 V6 diesel", 2005, 2026, 4600, D), e("2.1 diesel", 2012, 2015, 4600, D),
            )),
            Model("GLS / GL", 2006, 2026),
            Model("S-Class", 2000, 2026, listOf(
                e("5.0 V8", 2000, 2006, 6000), e("5.5 V8", 2006, 2013, 6000), e("3.0 V6 diesel", 2006, 2017, 4600, D),
                e("4.7 V8 twin-turbo", 2013, 2020, 6200),
            )),
            Model("SL", 2000, 2026), Model("SLK / SLC", 2000, 2020),
            Model("Sprinter", 2000, 2026, listOf(
                e("2.2 diesel", 2000, 2006, 4200, D), e("2.1 diesel", 2006, 2026, 4200, D), e("3.0 V6 diesel", 2006, 2026, 4200, D),
            )),
            Model("Vito / Valente", 2000, 2026), Model("X-Class", 2017, 2020),
        )),
        Make("Holden", listOf(
            Model("Acadia", 2018, 2020), Model("Astra", 2000, 2020), Model("Barina", 2000, 2018),
            Model("Calais / Berlina", 2000, 2017, listOf(
                e("3.8 V6", 2000, 2004, 5500), e("5.7 V8", 2000, 2006, 6000), e("3.6 V6", 2004, 2020, 6500),
                e("6.0 V8", 2006, 2017, 6000), e("3.0 V6", 2009, 2017, 6800), e("6.2 V8", 2013, 2017, 6000),
                e("2.0 turbo", 2018, 2020, 6200),
            )),
            Model("Captiva", 2006, 2018, listOf(
                e("2.4", 2006, 2018, 6200), e("3.2 V6", 2006, 2010, 6200), e("2.0 diesel", 2006, 2010, 4500, D),
                e("2.2 diesel", 2011, 2018, 4500, D), e("3.0 V6", 2011, 2018, 6500),
            )),
            Model("Colorado", 2008, 2020, listOf(
                e("3.0 diesel", 2008, 2012, 4000, D), e("3.6 V6", 2008, 2012, 6000), e("2.8 diesel", 2012, 2020, 4200, D),
            )),
            Model("Commodore", 2000, 2020, listOf(
                e("3.8 V6", 2000, 2004, 5500), e("5.7 V8", 2000, 2006, 6000), e("3.6 V6", 2004, 2020, 6500),
                e("6.0 V8", 2006, 2017, 6000), e("3.0 V6", 2009, 2017, 6800), e("6.2 V8", 2013, 2017, 6000),
                e("2.0 turbo", 2018, 2020, 6200),
            )),
            Model("Cruze", 2009, 2016, listOf(e("1.8", 2009, 2016, 6500), e("1.4 turbo", 2011, 2016, 6000), e("2.0 diesel", 2009, 2016, 4500, D))),
            Model("Epica", 2007, 2011), Model("Equinox", 2017, 2020),
            Model("Monaro", 2001, 2005, listOf(e("3.8 V6", 2001, 2004, 5500), e("5.7 V8", 2001, 2005, 6000))),
            Model("Rodeo", 2000, 2008),
            Model("Statesman / Caprice", 2000, 2017, listOf(
                e("3.8 V6", 2000, 2004, 5500), e("5.7 V8", 2000, 2006, 6000), e("3.6 V6", 2004, 2017, 6500),
                e("6.0 V8", 2006, 2017, 6000), e("6.2 V8", 2013, 2017, 6000),
            )),
            Model("Trailblazer", 2012, 2020), Model("Trax", 2013, 2020),
            Model("Ute", 2000, 2017, listOf(
                e("3.8 V6", 2000, 2004, 5500), e("5.7 V8", 2000, 2006, 6000), e("3.6 V6", 2004, 2020, 6500),
                e("6.0 V8", 2006, 2017, 6000), e("3.0 V6", 2009, 2017, 6800), e("6.2 V8", 2013, 2017, 6000),
                e("2.0 turbo", 2018, 2020, 6200),
            )),
            Model("Vectra", 2000, 2006), Model("Viva", 2005, 2009), Model("Zafira", 2001, 2005),
        )),
        Make("Chevrolet", listOf(
            Model("Aveo", 2004, 2011), Model("Blazer", 2000, 2026),
            Model("Camaro", 2010, 2024, listOf(e("3.6 V6", 2010, 2024, 7000), e("6.2 V8", 2010, 2024, 6600), e("2.0 turbo", 2016, 2024, 6500))),
            Model("Cobalt", 2005, 2010), Model("Colorado", 2004, 2026),
            Model("Corvette", 2000, 2026, listOf(
                e("5.7 V8", 2000, 2004, 6000), e("6.0 V8", 2005, 2007, 6500), e("7.0 V8 (Z06)", 2006, 2013, 7000),
                e("6.2 V8", 2008, 2026, 6600),
            )),
            Model("Cruze", 2011, 2019), Model("Equinox", 2005, 2026), Model("Express", 2000, 2026),
            Model("Impala", 2000, 2020), Model("Malibu", 2000, 2026),
            Model("Silverado", 2000, 2026, listOf(
                e("4.3 V6", 2000, 2019, 5600), e("4.8 V8", 2000, 2013, 6000), e("5.3 V8", 2000, 2026, 6000),
                e("6.0 V8", 2001, 2019, 6000), e("6.2 V8", 2009, 2026, 6000), e("6.6 diesel", 2001, 2026, 3600, D),
            )),
            Model("Spark", 2013, 2022), Model("Suburban", 2000, 2026), Model("Tahoe", 2000, 2026),
            Model("TrailBlazer", 2002, 2009), Model("Trax", 2015, 2026), Model("Volt", 2011, 2019),
        )),
        Make("GMC", listOf(
            Model("Acadia", 2007, 2026), Model("Canyon", 2004, 2026), Model("Envoy", 2000, 2009),
            Model("Savana", 2000, 2026), Model("Sierra", 2000, 2026, listOf(
                e("4.3 V6", 2000, 2019, 5600), e("4.8 V8", 2000, 2013, 6000), e("5.3 V8", 2000, 2026, 6000),
                e("6.0 V8", 2001, 2019, 6000), e("6.2 V8", 2009, 2026, 6000), e("6.6 diesel", 2001, 2026, 3600, D),
            )),
            Model("Terrain", 2010, 2026), Model("Yukon", 2000, 2026),
        )),
        Make("Cadillac", listOf(
            Model("ATS", 2013, 2019), Model("CT4", 2020, 2026), Model("CT5", 2020, 2026), Model("CTS", 2003, 2019),
            Model("DeVille / DTS", 2000, 2011), Model("Escalade", 2000, 2026), Model("Seville", 2000, 2004),
            Model("SRX", 2004, 2016), Model("STS", 2005, 2011), Model("XLR", 2004, 2009), Model("XT5", 2017, 2026),
        )),
        Make("Buick", listOf(
            Model("Century", 2000, 2005), Model("Enclave", 2008, 2026), Model("Encore", 2013, 2026), Model("Envision", 2016, 2026),
            Model("LaCrosse", 2005, 2019), Model("LeSabre", 2000, 2005), Model("Lucerne", 2006, 2011),
            Model("Park Avenue", 2000, 2005), Model("Regal", 2000, 2020), Model("Rendezvous", 2002, 2007),
        )),
        Make("Pontiac", listOf(
            Model("Aztek", 2001, 2005), Model("Bonneville", 2000, 2005), Model("Firebird", 2000, 2002), Model("G6", 2005, 2010),
            Model("G8", 2008, 2009), Model("Grand Am", 2000, 2005), Model("Grand Prix", 2000, 2008),
            Model("GTO", 2004, 2006, listOf(e("5.7 V8", 2004, 2004, 6000), e("6.0 V8", 2005, 2006, 6500))),
            Model("Solstice", 2006, 2010), Model("Sunfire", 2000, 2005), Model("Torrent", 2006, 2009), Model("Vibe", 2003, 2010),
        )),
        Make("Saturn", listOf(
            Model("Aura", 2007, 2009), Model("Ion", 2003, 2007), Model("L-Series", 2000, 2005), Model("Outlook", 2007, 2010),
            Model("S-Series", 2000, 2002), Model("Sky", 2007, 2010), Model("Vue", 2002, 2010),
        )),
        Make("Hummer", listOf(Model("H1", 2000, 2006), Model("H2", 2003, 2009), Model("H3", 2006, 2010))),
        Make("Opel / Vauxhall", listOf(
            Model("Antara", 2006, 2015), Model("Astra", 2000, 2026), Model("Combo", 2000, 2026), Model("Corsa", 2000, 2026),
            Model("Insignia", 2008, 2022), Model("Meriva", 2003, 2017), Model("Mokka", 2012, 2026), Model("Omega", 2000, 2003),
            Model("Signum", 2003, 2008), Model("Tigra", 2004, 2009), Model("Vectra", 2000, 2008), Model("Vivaro", 2001, 2026),
            Model("Zafira", 2000, 2019),
        )),
        Make("Saab", listOf(Model("9-2X", 2005, 2006), Model("9-3", 2000, 2014), Model("9-5", 2000, 2011), Model("9-7X", 2005, 2009))),
        Make("Volkswagen", listOf(
            Model("Amarok", 2011, 2026, listOf(e("2.0 TDI diesel", 2011, 2026, 4500, D), e("3.0 V6 TDI diesel", 2016, 2026, 4500, D))),
            Model("Arteon", 2017, 2026), Model("Atlas", 2017, 2026), Model("Beetle", 2000, 2019), Model("Bora", 2000, 2005),
            Model("Caddy", 2004, 2026), Model("CC", 2008, 2017), Model("Eos", 2006, 2015),
            Model("Golf", 2000, 2026, listOf(
                e("1.6", 2000, 2008, 6200), e("2.0", 2000, 2008, 6200), e("1.8 turbo", 2000, 2005, 6500),
                e("1.9 TDI diesel", 2000, 2009, 4800, D), e("3.2 V6 (R32)", 2003, 2009, 6800), e("2.0 TDI diesel", 2004, 2026, 4800, D),
                e("2.0 TSI turbo (GTI/R)", 2005, 2026, 6500), e("1.4 TSI", 2007, 2020, 6000), e("1.2 TSI", 2010, 2019, 6000),
                e("1.5 TSI", 2019, 2026, 6000),
            )),
            Model("Jetta", 2000, 2026), Model("Multivan", 2003, 2026),
            Model("Passat", 2000, 2026, listOf(
                e("1.8 turbo", 2000, 2005, 6500), e("2.8 V6", 2000, 2005, 6500), e("1.9 TDI diesel", 2000, 2005, 4800, D),
                e("2.0 TSI turbo", 2005, 2026, 6500), e("2.0 TDI diesel", 2005, 2026, 4800, D), e("3.6 V6", 2006, 2017, 6500),
            )),
            Model("Polo", 2000, 2026, listOf(
                e("1.4", 2000, 2009, 6200), e("1.6", 2000, 2010, 6200), e("1.2 TSI", 2010, 2017, 6000),
                e("1.8 TSI (GTI)", 2010, 2017, 6500), e("1.0 TSI", 2017, 2026, 6000),
            )),
            Model("Scirocco", 2009, 2017), Model("T-Cross", 2019, 2026), Model("T-Roc", 2017, 2026),
            Model("Tiguan", 2007, 2026, listOf(e("1.4 TSI", 2007, 2020, 6000), e("2.0 TSI turbo", 2007, 2026, 6500), e("2.0 TDI diesel", 2007, 2026, 4800, D))),
            Model("Touareg", 2003, 2026, listOf(
                e("3.2 V6", 2003, 2006, 6500), e("2.5 TDI diesel", 2003, 2010, 4500, D), e("4.2 V8", 2004, 2010, 6500),
                e("5.0 V10 TDI diesel", 2003, 2009, 4500, D), e("3.6 V6", 2006, 2018, 6500), e("3.0 V6 TDI diesel", 2007, 2026, 4800, D),
            )),
            Model("Touran", 2003, 2026), Model("Transporter", 2000, 2026), Model("Up!", 2012, 2023),
        )),
        Make("Audi", listOf(
            Model("A1", 2010, 2026),
            Model("A3", 2000, 2026, listOf(
                e("1.8 turbo", 2000, 2003, 6500), e("2.0 TFSI turbo", 2004, 2026, 6500), e("3.2 V6", 2004, 2009, 6800),
                e("2.0 TDI diesel", 2004, 2020, 4800, D), e("1.4 TFSI", 2008, 2020, 6000),
            )),
            Model("A4", 2000, 2026, listOf(
                e("1.8 turbo", 2000, 2006, 6500), e("3.0 V6", 2002, 2006, 6500), e("2.0 TFSI turbo", 2004, 2026, 6500),
                e("3.2 V6", 2004, 2012, 6800), e("2.0 TDI diesel", 2004, 2026, 4800, D),
            )),
            Model("A5", 2007, 2026), Model("A6", 2000, 2026), Model("A8", 2000, 2026), Model("Q3", 2011, 2026),
            Model("Q5", 2008, 2026, listOf(e("2.0 TFSI turbo", 2008, 2026, 6500), e("3.2 V6", 2008, 2012, 6800), e("2.0 TDI diesel", 2008, 2026, 4800, D), e("3.0 TDI diesel", 2008, 2026, 4800, D))),
            Model("Q7", 2006, 2026, listOf(e("3.6 V6", 2006, 2010, 6500), e("4.2 V8", 2006, 2015, 6500), e("3.0 TDI diesel", 2006, 2026, 4800, D))),
            Model("R8", 2007, 2024, listOf(e("4.2 V8", 2007, 2015, 8250), e("5.2 V10", 2009, 2024, 8700))),
            Model("RS3", 2011, 2026), Model("RS4", 2000, 2026), Model("RS6", 2002, 2026), Model("S3", 2000, 2026), Model("S4", 2000, 2026),
            Model("TT", 2000, 2023, listOf(
                e("1.8 turbo", 2000, 2006, 6500), e("3.2 V6", 2003, 2010, 6800), e("2.0 TFSI turbo", 2006, 2023, 6500),
                e("2.5 TFSI (TT RS)", 2009, 2023, 6800),
            )),
        )),
        Make("Skoda", listOf(
            Model("Fabia", 2000, 2026), Model("Kamiq", 2019, 2026), Model("Karoq", 2017, 2026), Model("Kodiaq", 2016, 2026),
            Model("Octavia", 2000, 2026, listOf(
                e("1.6", 2000, 2013, 6200), e("1.8 turbo", 2000, 2004, 6500), e("1.9 TDI diesel", 2000, 2010, 4800, D),
                e("2.0 TFSI turbo (RS)", 2005, 2026, 6500), e("2.0 TDI diesel", 2004, 2026, 4800, D), e("1.4 TSI", 2008, 2020, 6000),
            )),
            Model("Rapid", 2012, 2019), Model("Roomster", 2006, 2015), Model("Scala", 2019, 2026), Model("Superb", 2002, 2026),
            Model("Yeti", 2009, 2017),
        )),
        Make("SEAT / Cupra", listOf(
            Model("Alhambra", 2000, 2020), Model("Altea", 2004, 2015), Model("Arona", 2017, 2026), Model("Ateca", 2016, 2026),
            Model("Formentor", 2020, 2026), Model("Ibiza", 2000, 2026), Model("Leon", 2000, 2026), Model("Tarraco", 2018, 2026),
            Model("Toledo", 2000, 2019),
        )),
        Make("Porsche", listOf(
            Model("911", 2000, 2026, listOf(
                e("3.4", 2000, 2001, 7300), e("3.6", 2002, 2008, 7300), e("3.8", 2005, 2016, 7400),
                e("3.0 turbo", 2016, 2026, 7500), e("4.0 (GT3)", 2015, 2026, 9000),
            )),
            Model("Boxster", 2000, 2026, listOf(
                e("2.7", 2000, 2012, 7200), e("3.2", 2000, 2004, 7200), e("3.4", 2005, 2016, 7500), e("2.0 turbo", 2016, 2026, 7500),
            )),
            Model("Cayenne", 2003, 2026, listOf(
                e("3.2 V6", 2003, 2010, 6700), e("4.5 V8", 2003, 2010, 6500), e("4.8 V8", 2008, 2017, 6700),
                e("3.0 V6 diesel", 2009, 2018, 4800, D), e("3.6 V6", 2010, 2018, 6700),
            )),
            Model("Cayman", 2006, 2026),
            Model("Macan", 2014, 2026, listOf(e("2.0 turbo", 2014, 2026, 6800), e("3.0 V6 turbo", 2014, 2026, 6800))),
            Model("Panamera", 2009, 2026),
        )),
        Make("Lamborghini", listOf(
            Model("Aventador", 2011, 2022), Model("Gallardo", 2003, 2013), Model("Huracán", 2014, 2026),
            Model("Murciélago", 2001, 2010), Model("Urus", 2018, 2026),
        )),
        Make("Bentley", listOf(
            Model("Arnage", 2000, 2009), Model("Bentayga", 2016, 2026), Model("Continental GT", 2003, 2026),
            Model("Flying Spur", 2005, 2026),
        )),
        Make("Jeep", listOf(
            Model("Cherokee / Liberty", 2000, 2026), Model("Commander", 2006, 2010), Model("Compass", 2007, 2026),
            Model("Gladiator", 2020, 2026),
            Model("Grand Cherokee", 2000, 2026, listOf(
                e("4.0 I6", 2000, 2004, 5400), e("4.7 V8", 2000, 2009, 5800), e("3.7 V6", 2005, 2010, 6000),
                e("5.7 V8", 2005, 2026, 5800), e("3.0 V6 diesel", 2005, 2026, 4500, D), e("6.1 V8 (SRT8)", 2006, 2010, 6400),
                e("3.6 V6", 2011, 2026, 6400), e("6.4 V8 (SRT)", 2012, 2026, 6400),
            )),
            Model("Patriot", 2007, 2017), Model("Renegade", 2015, 2026), Model("Wagoneer", 2022, 2026),
            Model("Wrangler", 2000, 2026, listOf(
                e("2.5 I4", 2000, 2002, 5500), e("4.0 I6", 2000, 2006, 5400), e("3.8 V6", 2007, 2011, 5800),
                e("2.8 diesel", 2007, 2018, 4200, D), e("3.6 V6", 2012, 2026, 6600), e("2.0 turbo", 2018, 2026, 6000),
            )),
        )),
        Make("Chrysler", listOf(
            Model("300 / 300C", 2005, 2023, listOf(
                e("2.7 V6", 2005, 2010, 6400), e("3.5 V6", 2005, 2010, 6400), e("5.7 V8", 2005, 2023, 5800),
                e("3.0 V6 diesel", 2006, 2015, 4500, D), e("6.1 V8 (SRT8)", 2006, 2010, 6400), e("3.6 V6", 2011, 2023, 6400),
                e("6.4 V8 (SRT)", 2012, 2023, 6400),
            )),
            Model("Crossfire", 2004, 2008), Model("Neon", 2000, 2005), Model("Pacifica", 2004, 2026),
            Model("PT Cruiser", 2001, 2010), Model("Sebring", 2001, 2010),
            Model("Voyager / Town & Country", 2000, 2016),
        )),
        Make("Dodge", listOf(
            Model("Avenger", 2008, 2014), Model("Caliber", 2006, 2012),
            Model("Challenger", 2008, 2023, listOf(
                e("3.5 V6", 2006, 2010, 6400), e("3.6 V6", 2011, 2023, 6400), e("5.7 V8", 2006, 2023, 5800),
                e("6.1 V8 (SRT8)", 2006, 2010, 6400), e("6.4 V8", 2011, 2023, 6400), e("6.2 V8 supercharged", 2015, 2023, 6200),
            )),
            Model("Charger", 2006, 2023, listOf(
                e("3.5 V6", 2006, 2010, 6400), e("3.6 V6", 2011, 2023, 6400), e("5.7 V8", 2006, 2023, 5800),
                e("6.1 V8 (SRT8)", 2006, 2010, 6400), e("6.4 V8", 2011, 2023, 6400), e("6.2 V8 supercharged", 2015, 2023, 6200),
            )),
            Model("Dakota", 2000, 2011), Model("Durango", 2000, 2026), Model("Grand Caravan", 2000, 2020),
            Model("Journey", 2009, 2020), Model("Neon", 2000, 2005), Model("Nitro", 2007, 2011),
            Model("Ram (to 2009)", 2000, 2009, listOf(
                e("4.7 V8", 2000, 2009, 5800), e("5.9 V8", 2000, 2003, 5200), e("5.7 V8", 2003, 2009, 5800),
                e("5.9 diesel", 2000, 2007, 3200, D), e("6.7 diesel", 2007, 2009, 3200, D),
            )),
            Model("Viper", 2000, 2017, listOf(e("8.0 V10", 2000, 2007, 6000), e("8.4 V10", 2008, 2017, 6200))),
        )),
        Make("Ram", listOf(
            Model("1500", 2010, 2026, listOf(
                e("3.6 V6", 2013, 2026, 6400), e("5.7 V8", 2010, 2026, 5800), e("3.0 V6 diesel", 2014, 2026, 4500, D),
            )),
            Model("2500 / 3500", 2010, 2026, listOf(
                e("5.7 V8", 2010, 2018, 5800), e("6.4 V8", 2014, 2026, 6000), e("6.7 diesel", 2010, 2026, 3200, D),
            )),
            Model("ProMaster", 2014, 2026),
        )),
        Make("Fiat", listOf(
            Model("124 Spider", 2016, 2020),
            Model("500", 2007, 2026, listOf(
                e("1.2", 2007, 2024, 6500), e("1.4", 2007, 2015, 6800), e("0.9 TwinAir turbo", 2010, 2019, 6000),
            )),
            Model("500X", 2015, 2026), Model("Bravo", 2007, 2014), Model("Doblo", 2001, 2026),
            Model("Ducato", 2000, 2026, listOf(
                e("2.8 diesel", 2000, 2006, 4200, D), e("2.3 diesel", 2006, 2026, 4500, D), e("3.0 diesel", 2006, 2026, 4200, D),
            )),
            Model("Freemont", 2011, 2016), Model("Fullback", 2016, 2019), Model("Multipla", 2000, 2010),
            Model("Panda", 2003, 2026), Model("Punto", 2000, 2018), Model("Stilo", 2001, 2007), Model("Tipo", 2016, 2026),
        )),
        Make("Abarth", listOf(
            Model("124 Spider", 2016, 2020),
            Model("500 / 595 / 695", 2008, 2026, listOf(e("1.4 turbo", 2008, 2026, 6500))),
            Model("Punto", 2008, 2015),
        )),
        Make("Alfa Romeo", listOf(
            Model("147", 2001, 2010), Model("156", 2000, 2006), Model("159", 2005, 2011), Model("166", 2000, 2007),
            Model("4C", 2013, 2020), Model("Brera / Spider", 2006, 2010),
            Model("Giulia", 2016, 2026, listOf(
                e("2.0 turbo", 2016, 2026, 6000), e("2.2 diesel", 2016, 2026, 4800, D), e("2.9 V6 twin-turbo (QV)", 2016, 2026, 7000),
            )),
            Model("Giulietta", 2010, 2020), Model("GT", 2004, 2010), Model("GTV / Spider", 2000, 2005), Model("MiTo", 2008, 2018),
            Model("Stelvio", 2017, 2026), Model("Tonale", 2022, 2026),
        )),
        Make("Maserati", listOf(
            Model("3200 GT / Coupé / Spyder", 2000, 2007), Model("Ghibli", 2013, 2024), Model("GranTurismo", 2007, 2026),
            Model("Grecale", 2022, 2026), Model("Levante", 2016, 2026), Model("MC20", 2021, 2026), Model("Quattroporte", 2000, 2026),
        )),
        Make("Lancia", listOf(
            Model("Delta", 2008, 2014), Model("Lybra", 2000, 2005), Model("Musa", 2004, 2012), Model("Thesis", 2002, 2009),
            Model("Ypsilon", 2003, 2026),
        )),
        Make("Peugeot", listOf(
            Model("107 / 108", 2005, 2021), Model("2008", 2013, 2026),
            Model("206", 2000, 2010, listOf(e("1.4", 2000, 2010, 6200), e("1.6", 2000, 2010, 6200), e("2.0 (GTi/CC)", 2000, 2007, 6500))),
            Model("207", 2006, 2014), Model("208", 2012, 2026), Model("306", 2000, 2002), Model("307", 2001, 2008),
            Model("3008", 2009, 2026, listOf(
                e("1.6 turbo", 2009, 2026, 6000), e("2.0 HDi diesel", 2009, 2026, 4500, D), e("1.2 turbo", 2017, 2026, 6000),
            )),
            Model("308", 2007, 2026, listOf(
                e("1.6 turbo", 2007, 2021, 6000), e("1.6 HDi diesel", 2007, 2021, 4500, D), e("2.0 HDi diesel", 2007, 2021, 4500, D),
                e("1.2 turbo", 2014, 2026, 6000),
            )),
            Model("4007", 2007, 2012), Model("406", 2000, 2004), Model("407", 2004, 2011), Model("5008", 2009, 2026),
            Model("508", 2011, 2026), Model("607", 2000, 2010), Model("Boxer", 2000, 2026), Model("Expert", 2000, 2026),
            Model("Partner", 2000, 2026), Model("RCZ", 2010, 2015),
        )),
        Make("Citroën", listOf(
            Model("Berlingo", 2000, 2026), Model("C-Crosser", 2007, 2012), Model("C1", 2005, 2022), Model("C3", 2002, 2026),
            Model("C4", 2004, 2026), Model("C4 Picasso", 2006, 2022), Model("C5", 2001, 2017), Model("C5 Aircross", 2019, 2026),
            Model("Jumper", 2000, 2026), Model("Jumpy", 2000, 2026), Model("Saxo", 2000, 2003), Model("Xsara", 2000, 2006),
            Model("Xsara Picasso", 2000, 2012),
        )),
        Make("DS", listOf(Model("DS3", 2010, 2026), Model("DS4", 2011, 2026), Model("DS5", 2012, 2018), Model("DS7", 2017, 2026))),

        // Japan
        Make("Nissan", listOf(
            Model("200SX / Silvia", 2000, 2002), Model("350Z", 2003, 2009, listOf(e("3.5 V6", 2003, 2009, 7000))),
            Model("370Z", 2009, 2020, listOf(e("3.7 V6", 2009, 2020, 7500))), Model("Altima", 2000, 2026),
            Model("Dualis / Qashqai", 2007, 2026), Model("Frontier", 2000, 2026),
            Model("GT-R", 2008, 2026, listOf(e("3.8 V6 twin-turbo", 2008, 2026, 7000))), Model("Juke", 2010, 2026),
            Model("Leaf", 2011, 2026), Model("Maxima", 2000, 2023), Model("Micra", 2000, 2017), Model("Murano", 2003, 2026),
            Model("Navara", 2000, 2026, listOf(
                e("2.4 petrol", 2000, 2015, 5500), e("3.3 V6 petrol", 2000, 2005, 5500), e("3.0 diesel", 2002, 2015, 4400, D),
                e("2.5 diesel", 2005, 2015, 4500, D), e("4.0 V6 petrol", 2005, 2015, 6000), e("3.0 V6 diesel", 2011, 2015, 4500, D),
                e("2.3 diesel", 2015, 2026, 4500, D),
            )),
            Model("Note", 2005, 2020), Model("Pathfinder", 2000, 2026),
            Model("Patrol", 2000, 2026, listOf(
                e("4.8 I6 petrol", 2001, 2016, 5800), e("3.0 diesel", 2000, 2016, 4400, D), e("4.2 diesel", 2000, 2016, 4000, D),
                e("5.6 V8 petrol", 2013, 2026, 6000),
            )),
            Model("Pulsar / Sentra", 2000, 2019), Model("Rogue", 2008, 2026),
            Model("Skyline", 2000, 2007, listOf(
                e("2.5 turbo (RB25)", 2000, 2002, 8000), e("2.6 twin-turbo GT-R (RB26)", 2000, 2002, 8000), e("3.5 V6", 2002, 2007, 6600),
            )),
            Model("Tiida", 2006, 2013), Model("Titan", 2004, 2024),
            Model("X-Trail", 2001, 2026, listOf(
                e("2.0", 2001, 2022, 6500), e("2.5", 2001, 2026, 6500), e("2.0 diesel", 2007, 2017, 4500, D),
            )),
        )),
        Make("Infiniti", listOf(
            Model("FX / QX70", 2003, 2017), Model("G35 / G37", 2003, 2013), Model("I30 / I35", 2000, 2004), Model("M / Q70", 2003, 2019),
            Model("Q30", 2016, 2019), Model("Q50", 2014, 2026), Model("Q60", 2014, 2022), Model("QX30", 2017, 2019),
            Model("QX50 / EX", 2008, 2026), Model("QX56 / QX80", 2004, 2026), Model("QX60 / JX", 2013, 2026),
        )),
        Make("Subaru", listOf(
            Model("Ascent", 2019, 2026), Model("Baja", 2003, 2006), Model("BRZ", 2012, 2026, listOf(e("2.0", 2012, 2021, 7400), e("2.4", 2022, 2026, 7500))),
            Model("Crosstrek / XV", 2012, 2026),
            Model("Forester", 2000, 2026, listOf(
                e("2.0", 2000, 2026, 6500), e("2.5", 2000, 2026, 6500), e("2.5 turbo (XT)", 2004, 2013, 6500),
                e("2.0 turbo (XT)", 2013, 2018, 6500), e("2.0 diesel", 2013, 2018, 4500, D), e("2.0 hybrid", 2019, 2026, 6000, Fuel.HYBRID),
            )),
            Model("Impreza", 2000, 2026, listOf(e("2.0", 2000, 2026, 6500), e("2.5", 2000, 2011, 6500))),
            Model("Levorg", 2016, 2026),
            Model("Liberty / Legacy", 2000, 2026, listOf(
                e("2.5", 2000, 2026, 6500), e("2.0 turbo (GT)", 2003, 2009, 7000), e("3.0 H6", 2001, 2009, 7000),
                e("3.6 H6", 2009, 2019, 6500), e("2.0 diesel", 2009, 2014, 4500, D),
            )),
            Model("Outback", 2000, 2026, listOf(
                e("2.5", 2000, 2026, 6500), e("3.0 H6", 2001, 2009, 7000), e("3.6 H6", 2009, 2023, 6500),
                e("2.0 diesel", 2009, 2020, 4500, D), e("2.4 turbo", 2020, 2026, 6000),
            )),
            Model("Solterra", 2023, 2026), Model("Tribeca", 2006, 2014),
            Model("WRX / STI", 2000, 2026, listOf(
                e("2.0 turbo (WRX)", 2000, 2005, 7000), e("2.0 turbo (STI)", 2000, 2005, 8000), e("2.5 turbo (WRX)", 2006, 2014, 6500),
                e("2.5 turbo (STI)", 2004, 2021, 7000), e("2.0 DIT turbo (WRX)", 2015, 2021, 6500), e("2.4 turbo", 2022, 2026, 6000),
            )),
        )),
        Make("Mitsubishi", listOf(
            Model("380", 2005, 2008), Model("ASX / Outlander Sport", 2010, 2026), Model("Colt", 2004, 2012),
            Model("Eclipse", 2000, 2012), Model("Eclipse Cross", 2017, 2026), Model("Endeavor", 2004, 2011), Model("Galant", 2000, 2012),
            Model("i-MiEV", 2009, 2016), Model("Lancer", 2000, 2017, listOf(e("2.0", 2000, 2017, 6500), e("2.4", 2003, 2017, 6500))),
            Model("Lancer Evolution", 2001, 2016, listOf(e("2.0 turbo", 2001, 2016, 7000))),
            Model("Magna / Verada", 2000, 2005), Model("Mirage", 2012, 2026),
            Model("Outlander", 2003, 2026, listOf(
                e("2.0", 2003, 2021, 6500), e("2.4", 2003, 2026, 6000), e("3.0 V6", 2007, 2021, 6000),
                e("2.2 diesel", 2007, 2018, 4500, D), e("2.0 plug-in hybrid", 2014, 2018, 6000, Fuel.HYBRID),
                e("2.4 plug-in hybrid", 2019, 2026, 6000, Fuel.HYBRID),
            )),
            Model("Pajero / Montero", 2000, 2021, listOf(
                e("3.5 V6", 2000, 2008, 6000), e("3.8 V6", 2006, 2015, 6000), e("3.2 diesel", 2000, 2021, 4400, D),
            )),
            Model("Pajero Sport / Challenger", 2000, 2026),
            Model("Triton / L200", 2000, 2026, listOf(
                e("2.4 petrol", 2000, 2026, 6000), e("3.5 V6 petrol", 2006, 2009, 6000), e("2.5 diesel", 2000, 2015, 4500, D),
                e("3.2 diesel", 2006, 2009, 4400, D), e("2.4 diesel", 2015, 2026, 4500, D),
            )),
        )),
        Make("Suzuki", listOf(
            Model("Alto", 2009, 2014), Model("Baleno", 2016, 2022), Model("Celerio", 2015, 2021), Model("Equator", 2009, 2012),
            Model("Grand Vitara", 2000, 2019), Model("Ignis", 2017, 2026),
            Model("Jimny", 2000, 2026, listOf(e("1.3", 2000, 2018, 6500), e("1.5", 2018, 2026, 6000))),
            Model("Kizashi", 2010, 2016), Model("Liana", 2002, 2007), Model("S-Cross / SX4", 2007, 2026),
            Model("Swift", 2005, 2026, listOf(
                e("1.5", 2005, 2010, 6500), e("1.6 (Sport)", 2006, 2017, 7000), e("1.4", 2010, 2017, 6500),
                e("1.2", 2010, 2026, 6500), e("1.4 turbo (Sport)", 2017, 2026, 6000),
            )),
            Model("Vitara", 2015, 2026), Model("XL-7", 2001, 2009),
        )),
        Make("Isuzu", listOf(
            Model("Ascender", 2003, 2008), Model("Axiom", 2002, 2004),
            Model("D-Max", 2008, 2026, listOf(e("3.0 diesel", 2008, 2026, 4000, D), e("1.9 diesel", 2017, 2026, 4500, D))),
            Model("i-Series", 2006, 2008), Model("MU-X", 2013, 2026, listOf(e("3.0 diesel", 2013, 2026, 4000, D), e("1.9 diesel", 2021, 2026, 4500, D))),
            Model("Rodeo", 2000, 2004), Model("Trooper", 2000, 2002),
        )),
        Make("Lexus", listOf(
            Model("CT", 2011, 2022), Model("ES", 2000, 2026), Model("GS", 2000, 2020), Model("GX", 2003, 2026),
            Model("IS", 2000, 2026, listOf(
                e("2.0 I6 (IS200)", 2000, 2005, 6800), e("3.0 I6 (IS300)", 2001, 2005, 6500), e("2.5 V6", 2006, 2015, 6500),
                e("3.5 V6", 2006, 2026, 6600), e("5.0 V8 (IS F / IS 500)", 2008, 2026, 7000), e("2.0 turbo", 2016, 2021, 6000),
                e("2.5 hybrid", 2014, 2026, 5500, Fuel.HYBRID),
            )),
            Model("LC", 2017, 2026), Model("LS", 2000, 2026), Model("LX", 2000, 2026), Model("NX", 2015, 2026), Model("RC", 2015, 2026),
            Model("RX", 2000, 2026, listOf(
                e("3.0 V6", 2000, 2003, 6200), e("3.3 V6", 2004, 2008, 6200), e("3.5 V6", 2008, 2022, 6300),
                e("3.5 V6 hybrid", 2006, 2026, 6000, Fuel.HYBRID), e("2.0 turbo", 2016, 2022, 6000), e("2.4 turbo", 2023, 2026, 6000),
            )),
            Model("RZ", 2023, 2026), Model("SC", 2002, 2010), Model("UX", 2019, 2026),
        )),
        Make("Acura", listOf(
            Model("CL", 2001, 2003), Model("ILX", 2013, 2022), Model("Integra", 2023, 2026), Model("MDX", 2001, 2026),
            Model("NSX", 2000, 2022, listOf(e("3.2 V6", 2000, 2005, 8000), e("3.5 V6 twin-turbo hybrid", 2017, 2022, 7500, Fuel.HYBRID))),
            Model("RDX", 2007, 2026), Model("RL / RLX", 2000, 2020),
            Model("RSX", 2002, 2006, listOf(e("2.0", 2002, 2006, 6800), e("2.0 Type-S", 2002, 2006, 8000))),
            Model("TL", 2000, 2014), Model("TLX", 2015, 2026), Model("TSX", 2004, 2014), Model("ZDX", 2010, 2026),
        )),
        Make("Daihatsu", listOf(
            Model("Charade", 2000, 2000), Model("Copen", 2003, 2006), Model("Cuore", 2000, 2003), Model("Pyzar", 2000, 2002),
            Model("Sirion", 2000, 2005), Model("Terios", 2000, 2006), Model("YRV", 2001, 2005),
        )),
        Make("Scion", listOf(
            Model("FR-S", 2013, 2016), Model("iA", 2016, 2016), Model("iM", 2016, 2016), Model("iQ", 2012, 2015), Model("tC", 2005, 2016),
            Model("xA", 2004, 2006), Model("xB", 2004, 2015), Model("xD", 2008, 2014),
        )),

        // America
        Make("Lincoln", listOf(
            Model("Aviator", 2003, 2026), Model("Blackwood", 2002, 2002), Model("Continental", 2000, 2020), Model("Corsair", 2020, 2026),
            Model("LS", 2000, 2006), Model("Mark LT", 2006, 2008), Model("MKC", 2015, 2019), Model("MKS", 2009, 2016), Model("MKT", 2010, 2019),
            Model("MKX / Nautilus", 2007, 2026), Model("MKZ / Zephyr", 2006, 2020), Model("Navigator", 2000, 2026), Model("Town Car", 2000, 2011),
        )),
        Make("Mercury", listOf(
            Model("Cougar", 2000, 2002), Model("Grand Marquis", 2000, 2011), Model("Mariner", 2005, 2011), Model("Milan", 2006, 2011),
            Model("Montego", 2005, 2007), Model("Monterey", 2004, 2007), Model("Mountaineer", 2000, 2010), Model("Sable", 2000, 2009),
            Model("Villager", 2000, 2002),
        )),
        Make("Oldsmobile", listOf(
            Model("Alero", 2000, 2004), Model("Aurora", 2001, 2003), Model("Bravada", 2002, 2004), Model("Intrigue", 2000, 2002),
            Model("Silhouette", 2000, 2004),
        )),
        Make("Plymouth", listOf(Model("Neon", 2000, 2001), Model("Prowler", 2001, 2001), Model("Voyager", 2000, 2000))),
        Make("Tesla", listOf(
            Model("Cybertruck", 2023, 2026), Model("Model 3", 2017, 2026), Model("Model S", 2012, 2026), Model("Model X", 2015, 2026),
            Model("Model Y", 2020, 2026), Model("Roadster", 2008, 2012),
        )),
        Make("Rivian", listOf(Model("R1S", 2022, 2026), Model("R1T", 2022, 2026))),
        Make("Lucid", listOf(Model("Air", 2021, 2026), Model("Gravity", 2025, 2026))),

        // Europe
        Make("Mini", listOf(
            Model("Clubman", 2007, 2024), Model("Convertible", 2004, 2026), Model("Countryman", 2010, 2026),
            Model("Hatch / Cooper", 2001, 2026, listOf(
                e("1.6 (Cooper)", 2001, 2013, 6750), e("1.6 supercharged (Cooper S)", 2002, 2006, 6750), e("1.6 turbo (Cooper S)", 2007, 2013, 6500),
                e("1.5 turbo (Cooper)", 2014, 2026, 6500), e("2.0 turbo (Cooper S / JCW)", 2014, 2026, 6500), e("1.6 diesel", 2007, 2013, 4800, D),
            )),
            Model("Paceman", 2013, 2016),
        )),
        Make("Rolls-Royce", listOf(
            Model("Cullinan", 2018, 2026), Model("Dawn", 2016, 2023), Model("Ghost", 2010, 2026), Model("Phantom", 2003, 2026),
            Model("Spectre", 2023, 2026), Model("Wraith", 2013, 2023),
        )),
        Make("Smart", listOf(Model("ForFour", 2004, 2021), Model("ForTwo", 2000, 2024), Model("Roadster", 2003, 2005), Model("#1", 2023, 2026), Model("#3", 2024, 2026))),
        Make("Volvo", listOf(
            Model("C30", 2007, 2013), Model("C40", 2022, 2025), Model("C70", 2000, 2013), Model("EX30", 2024, 2026), Model("S40 / V40", 2000, 2019),
            Model("S60", 2000, 2026), Model("S80 / S90", 2000, 2026), Model("V50", 2004, 2012), Model("V60", 2011, 2026), Model("V70", 2000, 2016),
            Model("XC40", 2018, 2026), Model("XC60", 2008, 2026),
            Model("XC70", 2000, 2016), Model("XC90", 2003, 2026, listOf(
                e("2.5 I5 turbo", 2003, 2010, 6000), e("2.9 I6 twin-turbo", 2003, 2006, 6000), e("3.2 I6", 2007, 2014, 6500),
                e("4.4 V8", 2005, 2011, 6500), e("2.4 diesel", 2005, 2014, 4500, D), e("2.0 diesel", 2015, 2026, 4800, D),
                e("2.0 turbo", 2015, 2026, 6200), e("2.0 plug-in hybrid", 2016, 2026, 6200, Fuel.HYBRID),
            )),
        )),
        Make("Polestar", listOf(Model("Polestar 1", 2019, 2021), Model("Polestar 2", 2020, 2026), Model("Polestar 3", 2024, 2026), Model("Polestar 4", 2024, 2026))),
        Make("Jaguar", listOf(
            Model("E-Pace", 2018, 2024), Model("F-Pace", 2016, 2026), Model("F-Type", 2013, 2024), Model("I-Pace", 2018, 2024),
            Model("S-Type", 2000, 2008), Model("X-Type", 2001, 2009), Model("XE", 2015, 2024), Model("XF", 2008, 2024),
            Model("XJ", 2000, 2019), Model("XK", 2000, 2014),
        )),
        Make("Land Rover", listOf(
            Model("Defender", 2000, 2026, listOf(
                e("2.5 Td5 diesel", 2000, 2007, 4000, D), e("2.4 diesel", 2007, 2012, 4000, D), e("2.2 diesel", 2012, 2016, 4000, D),
                e("2.0 turbo", 2020, 2026, 6500), e("3.0 I6 diesel", 2020, 2026, 4500, D), e("3.0 I6 turbo", 2020, 2026, 6500),
                e("5.0 V8 supercharged", 2021, 2026, 6500),
            )),
            Model("Discovery", 2000, 2026, listOf(
                e("2.5 Td5 diesel", 2000, 2004, 4000, D), e("4.0 V8", 2000, 2004, 5500), e("4.4 V8", 2005, 2009, 6500),
                e("2.7 V6 diesel", 2005, 2009, 4400, D), e("3.0 V6 diesel", 2009, 2020, 4500, D), e("2.0 diesel", 2017, 2026, 4500, D),
                e("3.0 I6 diesel", 2021, 2026, 4500, D),
            )),
            Model("Discovery Sport", 2015, 2026), Model("Freelander", 2000, 2015),
            Model("Range Rover", 2000, 2026), Model("Range Rover Evoque", 2011, 2026), Model("Range Rover Sport", 2005, 2026),
            Model("Range Rover Velar", 2017, 2026),
        )),
        Make("Renault", listOf(
            Model("Arkana", 2021, 2026), Model("Captur", 2013, 2026), Model("Clio", 2000, 2026, listOf(
                e("1.6", 2000, 2012, 6500), e("2.0 (RS)", 2000, 2012, 7500), e("1.6 turbo (RS)", 2013, 2019, 6500), e("0.9 turbo", 2013, 2026, 6000),
                e("1.5 diesel", 2005, 2019, 4800, D),
            )),
            Model("Kadjar", 2015, 2022), Model("Kangoo", 2000, 2026), Model("Koleos", 2008, 2026), Model("Laguna", 2000, 2015),
            Model("Master", 2000, 2026),
            Model("Mégane", 2000, 2026, listOf(
                e("1.6", 2000, 2016, 6500), e("2.0", 2000, 2016, 6500), e("2.0 turbo (RS)", 2004, 2016, 6500),
                e("1.8 turbo (RS)", 2018, 2023, 6800), e("1.5 diesel", 2003, 2020, 4800, D), e("1.9 diesel", 2002, 2009, 4500, D),
            )),
            Model("Scénic", 2000, 2022), Model("Trafic", 2001, 2026), Model("Zoe", 2013, 2024),
        )),
        Make("Dacia", listOf(
            Model("Duster", 2010, 2026), Model("Jogger", 2022, 2026), Model("Logan", 2004, 2026), Model("Sandero", 2008, 2026),
            Model("Spring", 2021, 2026),
        )),
        Make("Alpine", listOf(Model("A110", 2017, 2026, listOf(e("1.8 turbo", 2017, 2026, 6800))))),
        Make("Aston Martin", listOf(
            Model("DB7", 2000, 2004), Model("DB9", 2004, 2016), Model("DB11", 2016, 2023), Model("DB12", 2023, 2026), Model("DBS", 2007, 2026),
            Model("DBX", 2020, 2026), Model("Rapide", 2010, 2020), Model("Vanquish", 2001, 2026), Model("Vantage", 2005, 2026),
        )),
        Make("Ferrari", listOf(
            Model("360", 2000, 2005), Model("430", 2005, 2009), Model("458", 2010, 2015), Model("488", 2016, 2020),
            Model("550 / 575M", 2000, 2006), Model("599", 2007, 2012), Model("612 Scaglietti", 2004, 2011), Model("812", 2017, 2024),
            Model("California / Portofino", 2009, 2026), Model("F8", 2020, 2023), Model("F12", 2013, 2017), Model("FF / GTC4Lusso", 2012, 2020),
            Model("Purosangue", 2023, 2026), Model("Roma", 2020, 2026), Model("SF90", 2020, 2026), Model("296", 2022, 2026),
        )),
        Make("McLaren", listOf(
            Model("540C / 570S / 600LT", 2015, 2021), Model("720S / 750S", 2017, 2026), Model("Artura", 2022, 2026), Model("GT", 2019, 2026),
            Model("MP4-12C / 650S / 675LT", 2011, 2017),
        )),
        Make("Lotus", listOf(
            Model("Eletre", 2023, 2026), Model("Elise", 2000, 2021), Model("Emira", 2022, 2026), Model("Evora", 2009, 2021),
            Model("Exige", 2000, 2021),
        )),
        Make("Bugatti", listOf(Model("Chiron", 2016, 2024), Model("Veyron", 2005, 2015))),
        Make("Rover", listOf(Model("25", 2000, 2005), Model("45", 2000, 2005), Model("75", 2000, 2005), Model("Streetwise", 2003, 2005))),
        Make("MG", listOf(
            Model("MG3", 2011, 2026), Model("MG4", 2022, 2026), Model("HS", 2018, 2026), Model("TF", 2002, 2011), Model("ZR", 2001, 2005),
            Model("ZS", 2017, 2026), Model("ZS (Rover era)", 2001, 2005), Model("ZT", 2001, 2005),
        )),
    )

    /**
     * The catalogue, with the engines from [EngineData] added to each model (after its own, and
     * skipping any it already lists), trimmed to the years each model was sold.
     */
    val MAKES: List<Make> = BASE_MAKES.map { make ->
        make.copy(models = make.models.map { m ->
            val own = m.engines.map { it.name }.toSet()
            val extra = EngineData.byModel[make.name to m.name].orEmpty().filter { it.name !in own }
            m.copy(engines = (m.engines + extra)
                .map { it.copy(from = maxOf(it.from, m.from), to = minOf(it.to, m.to)) }
                .filter { it.from <= it.to })
        })
    }

    private val GM_NORTH_AMERICA = setOf("Chevrolet", "GMC", "Cadillac", "Buick", "Pontiac", "Saturn", "Hummer", "Oldsmobile")
    private val VW_GROUP = setOf("Volkswagen", "Audi", "Skoda", "SEAT / Cupra", "Porsche", "Lamborghini", "Bentley", "Bugatti")
    private val VW_DEALER_ONLY = setOf("Porsche", "Lamborghini", "Bentley", "Bugatti")
    private val FCA_US = setOf("Chrysler", "Dodge", "Jeep", "Ram", "Plymouth")
    internal val FORD_GROUP = setOf("Ford", "Lincoln", "Mercury")
    private val TOYOTA_GROUP = setOf("Toyota", "Lexus", "Scion")
    private val HONDA_GROUP = setOf("Honda", "Acura")
    private val BMW_GROUP = setOf("BMW", "Mini", "Rolls-Royce")
    private val MERCEDES_GROUP = setOf("Mercedes-Benz", "Smart")
    private val JAPANESE = setOf("Nissan", "Infiniti", "Subaru", "Mitsubishi", "Suzuki", "Isuzu", "Daihatsu")
    private val EUROPEAN = setOf(
        "Jaguar", "Land Rover", "Renault", "Dacia", "Alpine", "Aston Martin", "Ferrari", "McLaren", "Lotus", "Rover", "MG",
    )
    private val JLR = setOf("Jaguar", "Land Rover")
    private val RENAULT_GROUP = setOf("Renault", "Dacia", "Alpine")
    /** Sold new only in North America, so the Australian OBD2 note doesn't apply. */
    private val NORTH_AMERICA_ONLY = setOf("Lincoln", "Mercury", "Oldsmobile", "Plymouth", "Scion", "Acura")
    private val DEALER_ONLY = setOf("Rolls-Royce", "Aston Martin", "Ferrari", "McLaren")
    /** Fully electric makes: no engine computer, so few or no standard OBD2 readings. */
    private val EV_MAKES = setOf("Tesla", "Rivian", "Lucid", "Polestar")
    /** Electric models from makes that also sell petrol cars, as (make, model prefix). */
    private val EV_MODELS = listOf(
        "Nissan" to "leaf", "Mitsubishi" to "i-miev", "Subaru" to "solterra", "Lexus" to "rz", "Jaguar" to "i-pace",
        "Renault" to "zoe", "Dacia" to "spring", "MG" to "mg4", "Smart" to "#", "Volvo" to "ex30", "Volvo" to "c40",
        "Lotus" to "eletre", "Rolls-Royce" to "spectre",
    )

    internal fun isElectric(car: Car, model: String): Boolean =
        (car.make in EV_MAKES && !(car.make == "Polestar" && model.startsWith("polestar 1"))) ||
            EV_MODELS.any { (make, prefix) -> car.make == make && model.startsWith(prefix) } ||
            (car.make == "Acura" && model.startsWith("zdx") && car.year >= 2024)
    private val ITALIAN = setOf("Fiat", "Abarth", "Alfa Romeo", "Lancia", "Maserati")
    private val PSA = setOf("Peugeot", "Citroën", "DS")

    /** Alphabetical, with "Other make" last. */
    val makeNames: List<String> = MAKES.map { it.name }.sortedBy { it.lowercase(Locale.US) } + OTHER_MAKE

    /** Models of [make] sold in [year] whose name contains [query] (case-insensitive). */
    fun models(make: String, year: Int, query: String = ""): List<Model> {
        val q = query.trim().lowercase(Locale.US)
        return MAKES.firstOrNull { it.name == make }?.models.orEmpty()
            .filter { year in it.from..it.to }
            .filter { q.isEmpty() || it.name.lowercase(Locale.US).contains(q) }
    }

    private fun isCommodoreFamily(model: String) =
        listOf("commodore", "calais", "ute", "statesman", "monaro").any { model.startsWith(it) }

    /** Engines offered for [model] of [make] in [year]. Empty when the catalogue has no engine data. */
    fun engines(make: String, year: Int, model: String?): List<Engine> {
        val m = model?.trim()?.lowercase(Locale.US) ?: return emptyList()
        return MAKES.firstOrNull { it.name == make }?.models
            ?.firstOrNull { it.name.lowercase(Locale.US) == m }?.engines.orEmpty()
            .filter { year in it.from..it.to }
    }

    fun engineFor(car: Car): Engine? =
        engines(car.make, car.year, car.model).firstOrNull { it.name.equals(car.engine?.trim(), ignoreCase = true) }

    /**
     * The engine the user meant when what they typed isn't exactly one of the list's names: the
     * only engine that year with the same size ("2.3" for "2.3 I4"), and the same layout or
     * fuel where they typed one ("V6", "turbo", "diesel"). Null if that doesn't narrow it to one.
     */
    fun engineLike(car: Car): Engine? {
        val typed = car.engine?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return null
        val size = Regex("""\d\.\d""").find(typed)?.value ?: return null
        val list = engines(car.make, car.year, car.model).filter { Regex("""(^|\D)${Regex.escape(size)}(\D|$)""").containsMatchIn(it.name) }
        if (list.size == 1) return list.single()
        val words = typed.split(Regex("""[^a-z0-9.]+""")).filter { it.isNotEmpty() && it != size }
        val narrowed = list.filter { e -> words.all { w -> e.name.lowercase(Locale.US).contains(w) } }
        return narrowed.singleOrNull()
    }

    /**
     * The redline for [car] and whether it's exact: the chosen engine's; otherwise the engine it's
     * clearly meant to be; otherwise, for a model with engines listed that year, the highest of
     * them (so the dial never runs out before the engine does); otherwise 4,500 rpm for a diesel.
     * Null when nothing is known, and the dial uses its default.
     */
    fun redlineFor(car: Car): Pair<Int, Boolean>? {
        engineFor(car)?.let { return it.redline to true }
        engineLike(car)?.let { return it.redline to true }
        val all = engines(car.make, car.year, car.model)
        if (all.isNotEmpty()) {
            val typedDiesel = car.engine?.contains("diesel", ignoreCase = true) == true
            val pool = if (typedDiesel) all.filter { it.fuel == Fuel.DIESEL }.ifEmpty { all } else all
            return pool.maxOf { it.redline } to false
        }
        if (car.engine?.contains("diesel", ignoreCase = true) == true) return 4500 to false
        return null
    }

    /**
     * Where the rev dial ends: the redline plus at least 500 rpm, rounded up to the next thousand,
     * so the red zone sits near the end like a real tachometer (6,500 → 7k, 7,200 → 8k, 9,000 → 10k).
     * 8,000 when the redline isn't known.
     */
    fun dialMax(redline: Int?): Int = if (redline == null) 8000 else ((redline + 500 + 999) / 1000) * 1000

    fun recommend(car: Car): Recommendation {
        val model = car.model?.lowercase(Locale.US).orEmpty()
        val y = car.year
        var note: String? = null

        val electric = isElectric(car, model)
        val first: List<String> = when {
            electric -> listOf("6", "7")
            // Australian Falcons and Territorys: BA (late 2002) onwards uses 11-bit CAN.
            car.make == "Ford" && model.startsWith("falcon") && y >= 2002 -> {
                note = if (y <= 2005) "BA Falcons (2002–2005) show live data but may not report trouble codes." else null
                listOf("6", "1")
            }
            car.make == "Ford" && model.startsWith("falcon") -> {
                note = "AU Falcons only partly support standard OBD2, so some readings may be missing."
                listOf("1", "6", "3")
            }
            car.make == "Ford" && model.startsWith("territory") -> listOf("6")
            // Ford-built Mazdas share Ford's engine computers.
            car.make == "Mazda" && (model.startsWith("tribute") || model.startsWith("b-series")) && y <= 2006 -> listOf("1", "3")

            // Stellantis models built on other makers' platforms.
            car.make == "Chrysler" && model.startsWith("crossfire") -> {
                note = "The Crossfire is a Mercedes-Benz SLK underneath, with Mercedes electronics."
                if (y >= 2005) listOf("5", "6", "3") else listOf("3", "5", "4")
            }
            (car.make == "Peugeot" && model.startsWith("107")) || (car.make == "Citroën" && model == "c1") -> {
                if (y <= 2014) note = "Built by Toyota alongside the Aygo, with Toyota electronics."
                if (y >= 2008) listOf("6", "7") else listOf("6", "5", "3")
            }
            (car.make == "Peugeot" && model.startsWith("4007")) || (car.make == "Citroën" && model.startsWith("c-crosser")) -> {
                note = "A Mitsubishi Outlander underneath, with Mitsubishi electronics."
                listOf("6", "5", "3")
            }
            car.make == "Fiat" && model.startsWith("fullback") -> {
                note = "The Fullback is a Mitsubishi Triton underneath, with Mitsubishi electronics."
                listOf("6", "7")
            }
            car.make == "Fiat" && model.startsWith("freemont") -> {
                note = "The Freemont is a Dodge Journey underneath, with Chrysler electronics."
                listOf("6", "7")
            }
            (car.make == "Fiat" || car.make == "Abarth") && model.startsWith("124") -> {
                note = "The 124 Spider is built by Mazda on the MX-5, with Mazda electronics."
                listOf("6", "7")
            }
            // Japanese, American and European models built on other makers' platforms.
            car.make == "Infiniti" && (model.startsWith("q30") || model.startsWith("qx30")) -> {
                note = "Built on the Mercedes-Benz A-Class and GLA, with Mercedes electronics."
                listOf("6", "7")
            }
            car.make == "Suzuki" && model.startsWith("equator") -> {
                note = "The Equator is a Nissan Frontier underneath, with Nissan electronics."
                listOf("6", "7")
            }
            car.make == "Suzuki" && model.startsWith("xl-7") && y >= 2007 -> {
                note = "The 2007–2009 XL-7 was built by GM alongside the Chevrolet Equinox, with GM electronics."
                listOf("6", "2")
            }
            car.make == "Isuzu" && (model.startsWith("ascender") || model.startsWith("i-series")) -> {
                note = if (model.startsWith("ascender")) {
                    "The Ascender is a GM TrailBlazer underneath, with GM electronics."
                } else {
                    "The i-Series is a Chevrolet Colorado underneath, with GM electronics."
                }
                if (y >= 2007) listOf("6", "2") else listOf("2", "6")
            }
            car.make == "Mercury" && model.startsWith("villager") -> {
                note = "The Villager is a Nissan Quest underneath, with Nissan electronics."
                listOf("3", "5", "4")
            }
            car.make == "Scion" && model == "ia" -> {
                note = "The iA is a Mazda2 underneath, with Mazda electronics."
                listOf("6", "7")
            }
            car.make == "Smart" && model.startsWith("forfour") && y <= 2006 -> {
                note = "The 2004–2006 ForFour is a Mitsubishi Colt underneath, with Mitsubishi electronics."
                listOf("6", "5", "3")
            }
            car.make == "Smart" && (model.startsWith("forfour") || model.startsWith("fortwo")) && y >= 2015 -> {
                note = "Built alongside the Renault Twingo, with Renault electronics."
                listOf("6", "7")
            }
            car.make in FCA_US -> when {
                y >= 2008 -> listOf("6", "7")
                y >= 2005 -> listOf("6", "2")
                else -> listOf("2", "3", "6")
            }
            car.make in ITALIAN || car.make in PSA || car.make in EUROPEAN -> when {
                y >= 2008 -> listOf("6", "7")
                y >= 2004 -> listOf("6", "5", "3", "4")
                else -> if (car.make in JLR) listOf("3", "5", "4", "1") else listOf("3", "4", "5")
            }
            car.make == "Volvo" -> if (y >= 2005) listOf("6", "7") else listOf("3", "5", "4")
            car.make in JAPANESE -> when {
                car.make == "Isuzu" && (model.startsWith("d-max") || model.startsWith("mu-x")) -> listOf("6", "5", "4")
                y >= 2008 -> listOf("6", "7")
                y >= 2005 -> listOf("6", "5", "3")
                else -> listOf("3", "5", "4")
            }

            // Holden Commodore family: VX/VY only partly OBD2, VZ (2004) fully, VE (2006) onwards CAN.
            car.make == "Holden" && isCommodoreFamily(model) -> {
                note = when {
                    y <= 2004 -> "VX and VY models only partly support OBD2: expect live data, but trouble codes may not appear. VT models (to 2000) use Holden's older ALDL system instead."
                    else -> null
                }
                when {
                    y >= 2006 -> listOf("6")
                    y >= 2004 -> listOf("6", "2", "3")
                    else -> listOf("2", "3", "5")
                }
            }
            car.make == "Pontiac" && model.startsWith("vibe") -> {
                note = "The Vibe is a Toyota Matrix underneath, with Toyota electronics."
                if (y >= 2005) listOf("6", "5", "3") else listOf("3", "5", "4")
            }
            car.make == "Pontiac" && model.startsWith("g8") -> {
                note = "The G8 is a Holden VE Commodore built for the US."
                listOf("6")
            }
            car.make == "Pontiac" && model.startsWith("gto") -> {
                note = "The GTO is a Holden Monaro built for the US, with full OBD2."
                listOf("2", "6")
            }
            car.make == "Saab" && model.startsWith("9-2x") -> {
                note = "The 9-2X is a Subaru Impreza underneath, with Subaru electronics."
                listOf("3", "5", "4")
            }
            car.make == "Saab" && model.startsWith("9-3") && y >= 2003 -> listOf("6", "5", "3")

            car.make in GM_NORTH_AMERICA || (car.make == "Saab" && model.startsWith("9-7x")) -> when {
                y >= 2008 -> listOf("6", "7")
                y >= 2005 -> listOf("6", "2")
                else -> listOf("2", "6")
            }
            car.make == "Holden" -> when {
                y >= 2008 -> listOf("6", "7")
                y >= 2004 -> listOf("5", "6", "3")
                else -> listOf("3", "5", "4", "2")
            }
            car.make == "Opel / Vauxhall" || car.make == "Saab" -> when {
                y >= 2007 -> listOf("6", "7")
                y >= 2004 -> listOf("6", "5", "3")
                else -> listOf("3", "5", "4")
            }
            car.make in VW_GROUP -> when {
                y >= 2008 -> listOf("6", "7")
                y >= 2004 -> listOf("6", "4", "5", "3")
                else -> listOf("3", "4", "5")
            }
            car.make in FORD_GROUP -> when {
                y >= 2008 -> listOf("6", "7")
                y >= 2004 -> listOf("6", "1", "3")
                else -> listOf("1", "6", "3")
            }
            car.make == "Mazda" -> when {
                y >= 2008 -> listOf("6", "7")
                y >= 2004 -> listOf("6", "3", "5")
                else -> listOf("3", "5", "4", "1")
            }
            car.make in TOYOTA_GROUP -> when {
                // The BMW-built GR Supra uses BMW electronics.
                model.startsWith("supra") -> listOf("6", "7")
                y >= 2008 -> listOf("6", "7")
                y >= 2005 -> listOf("6", "5", "3")
                else -> listOf("3", "5", "4")
            }
            car.make in MERCEDES_GROUP -> when {
                y >= 2008 -> listOf("6", "7")
                y >= 2005 -> listOf("5", "6", "3")
                else -> listOf("3", "5", "4")
            }
            car.make in BMW_GROUP -> when {
                y >= 2008 -> listOf("6", "7")
                y >= 2004 -> listOf("5", "3", "6")
                else -> listOf("3", "4", "5")
            }
            car.make in HONDA_GROUP -> when {
                y >= 2008 -> listOf("6", "7")
                y >= 2006 -> listOf("6", "3", "5")
                else -> listOf("3", "5", "4")
            }
            else -> if (y >= 2008) listOf("6", "7", "8", "9") else emptyList()
        }

        if (car.make in GM_NORTH_AMERICA && y < 2008 && note == null) {
            note = "Most GM cars before 2008 use SAE J1850 VPW, which many cheap ELM327 adapters can't speak."
        }
        if (car.make in FCA_US && y < 2008 && note == null) {
            note = "Most Chrysler, Dodge and Jeep vehicles before 2008 use SAE J1850 VPW, which many cheap ELM327 adapters can't speak."
        }
        if (car.make in ITALIAN && note == null) {
            note = if (car.make == "Maserati") {
                "Generic OBD2 covers engine data and emissions codes. Deeper diagnostics need Maserati's dealer tools."
            } else {
                "Generic OBD2 covers engine data and emissions codes. Deeper diagnostics need tools such as AlfaOBD."
            }
        }
        if (car.make in PSA && note == null) {
            note = "Generic OBD2 covers engine data and emissions codes. Deeper diagnostics need tools such as Peugeot-Citroën's DiagBox."
        }
        if (car.make in VW_GROUP) {
            note = if (car.make in VW_DEALER_ONLY) {
                "Generic OBD2 covers engine data and emissions codes. Deeper diagnostics need ${car.make}'s dealer tools."
            } else {
                "Generic OBD2 covers engine data and emissions codes. Deeper diagnostics on VW-group cars need tools such as VCDS."
            }
        }
        if (car.make == "BMW" || car.make == "Mini") {
            note = "Generic OBD2 covers engine data and emissions codes. ${car.make}'s deeper diagnostics need BMW-specific tools."
        }
        if (car.make in DEALER_ONLY && note == null) {
            note = "Generic OBD2 covers engine data and emissions codes. Deeper diagnostics need ${car.make}'s dealer tools."
        }
        if (car.make in JLR && note == null) {
            note = "Generic OBD2 covers engine data and emissions codes. Deeper diagnostics on Jaguar and Land Rover need their dealer tools."
        }
        if (car.make in RENAULT_GROUP && note == null) {
            note = "Generic OBD2 covers engine data and emissions codes. Deeper diagnostics need Renault-specific tools."
        }
        if (car.make == "Volvo" && note == null) {
            note = "Generic OBD2 covers engine data and emissions codes. Volvo's deeper diagnostics need its own dealer tools."
        }
        if (car.make == "Smart" && note == null) {
            note = "Generic OBD2 covers engine data and emissions codes. Deeper diagnostics need Mercedes-Benz dealer tools."
        }
        if (car.make == "Mercedes-Benz") {
            note = if (model.startsWith("x-class")) {
                "The X-Class is built on the Nissan Navara and uses Nissan electronics."
            } else {
                "Generic OBD2 covers engine data and emissions codes. Mercedes-Benz's deeper diagnostics need its own dealer tools."
            }
        }
        if (electric) {
            note = if (car.make == "Tesla") {
                "Teslas have no standard OBD2 port, so an adapter can't plug in directly. Adapter cables exist, but standard OBD2 readings are still very limited."
            } else {
                "Electric cars don't have to support standard OBD2 engine readings, so expect little or no live data."
            }
        }
        if (note == null && y < 2006 && car.make !in NORTH_AMERICA_ONLY) {
            note = "Cars sold new in Australia before 2006 weren't required to have full OBD2."
        }
        val engine = engineFor(car) ?: engineLike(car)
        val isDiesel = engine?.fuel == Fuel.DIESEL || car.engine?.contains("diesel", ignoreCase = true) == true
        if (isDiesel) {
            val dieselNote = "Diesels usually report fewer live readings than petrol engines."
            note = if (note == null) dieselNote else "$note $dieselNote"
        }
        val order = (first + ObdProtocols.ALL.map { it.code }).distinct()
        val redline = redlineFor(car)
        return Recommendation(order, note, redline?.first, redline?.second ?: true)
    }
}
