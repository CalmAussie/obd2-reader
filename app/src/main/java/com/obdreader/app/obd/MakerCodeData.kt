package com.obdreader.app.obd

import com.obdreader.app.obd.DtcLibrary.Severity
import com.obdreader.app.obd.MakerCodes.Entry
import com.obdreader.app.obd.MakerCodes.Fit

/**
 * Manufacturer-specific codes taken from each maker's own service bulletins, which the makers file
 * with the US road safety regulator (NHTSA) and NHTSA publishes, plus GM's published engine
 * diagnostic files. Each entry is used only for the make, models, model years and engines its
 * bulletin names; the comment above it is the document it came from.
 *
 * The code, the fault it reports and the cars it applies to are the maker's. The title is the
 * maker's short name for the code in plain words, and the meaning, causes, symptoms and checks are
 * written for this app. Model and engine names are the car list's ([VehicleCatalog]), so a code
 * shows only when the chosen car is one the bulletin covers. Codes for airbag systems are left out.
 */
internal object MakerCodeData {

    private val H = Severity.HIGH
    private val M = Severity.MEDIUM
    private val L = Severity.LOW

    /** A fit for model years [from]..[to]; [models] and [engines] are car list names separated by "|". */
    private fun f(from: Int, to: Int, models: String, engines: String = "") =
        Fit(from..to, models.split("|").toSet(), engines.split("|").filter { it.isNotEmpty() }.toSet())

    private class Sink(val out: MutableList<Pair<String, Entry>>) {
        /** Adds one entry for each of the space-separated [codes]. */
        fun e(codes: String, make: String, fits: List<Fit>, title: String, meaning: String, severity: Severity,
              causes: List<String>, symptoms: List<String>, checks: List<String>, source: String) {
            val entry = Entry(setOf(make), fits, title, meaning, severity, causes, symptoms, checks, source)
            for (code in codes.split(" ")) out += code to entry
        }
    }

    /** Every entry as code to definition; a code can have several, for different makes or cars. */
    val entries: List<Pair<String, Entry>> = buildList {
        val sink = Sink(this)
        kia(sink)
        hyundai(sink)
        toyota(sink)
        gm(sink)
        honda(sink)
        nissan(sink)
        mazda(sink)
        volkswagenGroup(sink)
        subaru(sink)
        mitsubishi(sink)
        jaguarLandRover(sink)
        chrysler(sink)
        dodge(sink)
        ram(sink)
        porsche(sink)
    }

    private fun kia(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10201615-0001.pdf; https://static.nhtsa.gov/odi/tsbs/2020/MC-10173257-0001.pdf; https://static.nhtsa.gov/odi/tsbs/2019/MC-10159231-0001.pdf; https://static.nhtsa.gov/odi/tsbs/2025/MC-11015268-0001.pdf
        e("P1326", "Kia", listOf(
                f(2012, 2013, "Forte", "2.4 I4"),
                f(2014, 2015, "Forte", "2.0 I4"),
                f(2011, 2013, "Optima", "2.0 hybrid"),
                f(2011, 2018, "Optima", "2.4 I4|2.0 turbo"),
                f(2011, 2018, "Sorento", "2.4 I4"),
                f(2015, 2018, "Sorento", "2.0 turbo"),
                f(2012, 2013, "Soul", "1.6 I4"),
                f(2014, 2016, "Soul", "1.6 I4|2.0 I4"),
                f(2011, 2018, "Sportage", "2.0 turbo"),
                f(2012, 2012, "Sportage", "2.4 I4"),
                f(2014, 2018, "Sportage", "2.4 I4"),
            ),
            "Knock signal range/performance",
            "Kia's definition: the knock sensor is picking up vibration that points to a worn connecting rod bearing inside the engine. The engine computer flashes the check-engine light and limits power to protect the engine.",
            H,
            listOf("A worn connecting rod bearing", "Low or old engine oil speeding up bearing wear", "Less often, a faulty knock sensor or its wiring"),
            listOf("A flashing check-engine light", "Reduced power, with engine speed held low (limp-home mode)", "A knocking noise from the engine that rises with revs"),
            listOf("Check the engine oil level and top up if it's low", "Avoid high revs and long trips until it's checked", "Ask a Kia dealer to inspect the bearings; in some countries Kia covers this under an extended engine warranty"),
            "Kia Knock Sensor Detection System bulletin, PI1803, PI1802W/X, SC291YZ: the maker's own service bulletins, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10236658-0001.pdf
        e("P1AA6", "Kia", listOf(f(2019, 2022, "Niro EV")),
            "Drive battery abnormal performance detected",
            "Kia's definition: the battery management system has detected the drive battery behaving abnormally.",
            H,
            listOf("A weak or damaged battery cell", "A fault in the battery's cooling or heating", "A fault in the battery management system or its sensors", "Water getting into the battery pack"),
            listOf("An EV system warning", "Reduced power or range", "Charging stopping early or not starting"),
            listOf("Stop charging and park outside if you notice heat, smell or smoke", "Don't open or work on the battery yourself", "Ask a Kia dealer to test the battery; battery faults are often covered by the battery warranty"),
            "Kia ELE293: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10236658-0001.pdf
        e("P1AA7", "Kia", listOf(f(2019, 2022, "Niro EV")),
            "Drive battery cell voltage deviation",
            "Kia's definition: one or more cells in the drive battery are at a different voltage from the rest.",
            H,
            listOf("A weak or damaged battery cell", "A fault in the battery's cooling or heating", "A fault in the battery management system or its sensors", "Water getting into the battery pack"),
            listOf("An EV system warning", "Reduced power or range", "Charging stopping early or not starting"),
            listOf("Stop charging and park outside if you notice heat, smell or smoke", "Don't open or work on the battery yourself", "Ask a Kia dealer to test the battery; battery faults are often covered by the battery warranty"),
            "Kia ELE293: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10236658-0001.pdf
        e("P1AA8", "Kia", listOf(f(2019, 2022, "Niro EV")),
            "Drive battery insulation resistance breakdown",
            "Kia's definition: the insulation that keeps high voltage away from the car's body has broken down somewhere.",
            H,
            listOf("A weak or damaged battery cell", "A fault in the battery's cooling or heating", "A fault in the battery management system or its sensors", "Water getting into the battery pack"),
            listOf("An EV system warning", "Reduced power or range", "Charging stopping early or not starting"),
            listOf("Stop charging and park outside if you notice heat, smell or smoke", "Don't open or work on the battery yourself", "Ask a Kia dealer to test the battery; battery faults are often covered by the battery warranty"),
            "Kia ELE293: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10236658-0001.pdf
        e("P1AA9", "Kia", listOf(f(2019, 2022, "Niro EV")),
            "Drive battery cell voltage movement",
            "Kia's definition: a cell's voltage is changing in a way the battery management system doesn't expect.",
            H,
            listOf("A weak or damaged battery cell", "A fault in the battery's cooling or heating", "A fault in the battery management system or its sensors", "Water getting into the battery pack"),
            listOf("An EV system warning", "Reduced power or range", "Charging stopping early or not starting"),
            listOf("Stop charging and park outside if you notice heat, smell or smoke", "Don't open or work on the battery yourself", "Ask a Kia dealer to test the battery; battery faults are often covered by the battery warranty"),
            "Kia ELE293: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10236658-0001.pdf
        e("P1AAA", "Kia", listOf(f(2019, 2022, "Niro EV")),
            "Drive battery cell voltage deviation while charging",
            "Kia's definition: one or more cells drift away from the others in voltage while the car is charging.",
            H,
            listOf("A weak or damaged battery cell", "A fault in the battery's cooling or heating", "A fault in the battery management system or its sensors", "Water getting into the battery pack"),
            listOf("An EV system warning", "Reduced power or range", "Charging stopping early or not starting"),
            listOf("Stop charging and park outside if you notice heat, smell or smoke", "Don't open or work on the battery yourself", "Ask a Kia dealer to test the battery; battery faults are often covered by the battery warranty"),
            "Kia ELE293: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10236658-0001.pdf
        e("P1AAB", "Kia", listOf(f(2019, 2022, "Niro EV")),
            "Drive battery temperature too high",
            "Kia's definition: the drive battery is hotter than its safe limit.",
            H,
            listOf("A weak or damaged battery cell", "A fault in the battery's cooling or heating", "A fault in the battery management system or its sensors", "Water getting into the battery pack"),
            listOf("An EV system warning", "Reduced power or range", "Charging stopping early or not starting"),
            listOf("Stop charging and park outside if you notice heat, smell or smoke", "Don't open or work on the battery yourself", "Ask a Kia dealer to test the battery; battery faults are often covered by the battery warranty"),
            "Kia ELE293: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10236658-0001.pdf
        e("P1AAC", "Kia", listOf(f(2019, 2022, "Niro EV")),
            "Drive battery cell voltage too high",
            "Kia's definition: a cell in the drive battery is above its safe voltage.",
            H,
            listOf("A weak or damaged battery cell", "A fault in the battery's cooling or heating", "A fault in the battery management system or its sensors", "Water getting into the battery pack"),
            listOf("An EV system warning", "Reduced power or range", "Charging stopping early or not starting"),
            listOf("Stop charging and park outside if you notice heat, smell or smoke", "Don't open or work on the battery yourself", "Ask a Kia dealer to test the battery; battery faults are often covered by the battery warranty"),
            "Kia ELE293: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10236658-0001.pdf
        e("P1AAD", "Kia", listOf(f(2019, 2022, "Niro EV")),
            "Drive battery cell voltage too low",
            "Kia's definition: a cell in the drive battery is below its safe voltage.",
            H,
            listOf("A weak or damaged battery cell", "A fault in the battery's cooling or heating", "A fault in the battery management system or its sensors", "Water getting into the battery pack"),
            listOf("An EV system warning", "Reduced power or range", "Charging stopping early or not starting"),
            listOf("Stop charging and park outside if you notice heat, smell or smoke", "Don't open or work on the battery yourself", "Ask a Kia dealer to test the battery; battery faults are often covered by the battery warranty"),
            "Kia ELE293: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10236658-0001.pdf
        e("P1AAE", "Kia", listOf(f(2019, 2022, "Niro EV")),
            "Drive battery temperature deviation",
            "Kia's definition: parts of the drive battery are at noticeably different temperatures from each other.",
            M,
            listOf("A weak or damaged battery cell", "A fault in the battery's cooling or heating", "A fault in the battery management system or its sensors", "Water getting into the battery pack"),
            listOf("An EV system warning", "Reduced power or range", "Charging stopping early or not starting"),
            listOf("Stop charging and park outside if you notice heat, smell or smoke", "Don't open or work on the battery yourself", "Ask a Kia dealer to test the battery; battery faults are often covered by the battery warranty"),
            "Kia ELE293: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10236658-0001.pdf
        e("P1AAF", "Kia", listOf(f(2019, 2022, "Niro EV")),
            "Drive battery abnormal voltage change detection fault",
            "Kia's definition: the check that watches for abnormal voltage changes in the drive battery has itself reported a problem.",
            M,
            listOf("A weak or damaged battery cell", "A fault in the battery's cooling or heating", "A fault in the battery management system or its sensors", "Water getting into the battery pack"),
            listOf("An EV system warning", "Reduced power or range", "Charging stopping early or not starting"),
            listOf("Stop charging and park outside if you notice heat, smell or smoke", "Don't open or work on the battery yourself", "Ask a Kia dealer to test the battery; battery faults are often covered by the battery warranty"),
            "Kia ELE293: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2026/MC-11030345-0001.pdf
        e("P17A0", "Kia", listOf(f(2022, 2024, "Sorento", "1.6 turbo hybrid"), f(2023, 2025, "Sportage", "1.6 turbo hybrid")),
            "Navigation unit requested the check-engine light",
            "Kia's definition: the navigation and infotainment unit asked for the check-engine light to come on, because it supplies route information the hybrid system uses.",
            L,
            listOf("Infotainment software that needs updating", "A fault in the navigation unit"),
            listOf("Check-engine light on with the car driving normally", "Navigation or infotainment glitches"),
            listOf("Check for an infotainment or navigation software update", "Ask a Kia dealer to update the software"),
            "Kia ELE394: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/rcl/2022/RCRIT-22V760-0664.pdf
        e("P1C2D", "Kia", listOf(f(2021, 2022, "K5|Sorento", "2.5 turbo")),
            "High-pressure electric oil pump motor position sensor",
            "Kia's definition: a fault with the position sensor in the electric pump that supplies high-pressure oil to the 8-speed dual-clutch gearbox.",
            H,
            listOf("A fault in the gearbox's electric oil pump", "Gearbox software that needs updating"),
            listOf("A transmission warning", "Loss of drive, sometimes after a short warning", "The gearbox going into a fail-safe mode"),
            listOf("If a transmission warning appears, pull over safely as soon as you can", "Ask a Kia dealer whether the pump recall or software update has been done on your car"),
            "Kia SC250 (recall 22V-760): the maker's own service bulletin, filed with NHTSA")
    }

    private fun hyundai(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10232200-0001.pdf
        e("P1441", "Hyundai", listOf(f(2021, 2022, "Santa Fe", "1.6 turbo hybrid")),
            "Purge valve rear hose missing",
            "Hyundai's definition: the hose on the tank side of the purge control valve, which carries fuel vapour to the engine, is disconnected or missing.",
            L,
            listOf("A purge hose that has come off or wasn't refitted after work", "A split hose", "A faulty purge control valve"),
            listOf("Usually nothing you'd notice", "A fuel smell in some cases"),
            listOf("Check the hoses at the purge control valve are pushed fully home", "Look for split or kinked vapour hoses", "Have the EVAP system tested if the code returns"),
            "Hyundai 23-FL-001H: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10232200-0001.pdf
        e("P1A77", "Hyundai", listOf(f(2021, 2022, "Santa Fe", "1.6 turbo hybrid")),
            "Hybrid starter-generator belt slip",
            "Hyundai's definition: the belt that drives the hybrid starter-generator is slipping. That unit restarts the engine and helps charge the hybrid battery.",
            M,
            listOf("A worn or stretched belt", "A weak belt tensioner", "Oil or coolant on the belt"),
            listOf("A squeal from the engine bay on start-up", "Rough or slow engine restarts", "A hybrid system warning"),
            listOf("Look at the belt for cracks, glazing or fluid on it", "Have the belt and tensioner checked and replaced if worn", "Fix any leak that's wetting the belt"),
            "Hyundai 23-FL-001H: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10238402-0001.pdf
        e("P1233", "Hyundai", listOf(f(2015, 2019, "Sonata")),
            "Low pressure fuel system",
            "Hyundai's definition: pressure on the low-pressure side of the fuel system, from the tank pump to the engine, isn't what the computer expects.",
            M,
            listOf("A weak in-tank fuel pump", "A blocked fuel filter", "A faulty fuel pressure sensor", "Running very low on fuel"),
            listOf("Hard starting", "Hesitation or loss of power", "Stalling in some cases"),
            listOf("Check there's enough fuel", "Have the fuel pressure measured", "Have the pump, filter and pressure sensor checked"),
            "Hyundai 23-FL-004H: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10170893-0001.pdf
        e("P1B77", "Hyundai", listOf(f(2016, 2017, "Sonata", "2.4 hybrid|2.0 hybrid")),
            "High-voltage battery pre-charging fault",
            "Hyundai's definition: the hybrid battery couldn't complete its pre-charge, the gentle power-up it does before connecting fully.",
            H,
            listOf("A failed pre-charge relay or resistor", "A fault in the high-voltage battery's relay assembly", "A fault in the inverter or a high-voltage cable"),
            listOf("A hybrid system warning", "The car refusing to go into READY"),
            listOf("Don't touch the orange high-voltage cables or open any high-voltage part yourself", "Note any hybrid system warning on the dash and whether the car still goes into READY", "Have a dealer or hybrid-trained technician test the high-voltage system"),
            "Hyundai 20-01-003H: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10170893-0001.pdf
        e("P1990", "Hyundai", listOf(f(2016, 2017, "Sonata", "2.4 hybrid|2.0 hybrid")),
            "Oil pump unit high-voltage input wiring open",
            "Hyundai's definition: the high-voltage supply to the electric oil pump unit, which keeps the automatic gearbox supplied with oil on this hybrid, is open (disconnected).",
            H,
            listOf("A disconnected or damaged high-voltage cable to the oil pump unit", "A blown high-voltage fuse", "A fault in the oil pump unit"),
            listOf("A hybrid or transmission warning", "Harsh or slipping gear changes", "Reduced power"),
            listOf("Don't touch the orange high-voltage cables or open any high-voltage part yourself", "Note any hybrid system warning on the dash and whether the car still goes into READY", "Have a dealer or hybrid-trained technician test the high-voltage system"),
            "Hyundai 20-01-003H: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2026/MC-11029938-0001.pdf
        e("P1AA7", "Hyundai", listOf(f(2025, 2025, "Ioniq 5")),
            "Drive battery abnormal performance: cell voltage deviation",
            "Hyundai's definition: one or more cells in the drive battery are at a different voltage from the rest.",
            H,
            listOf("A weak or damaged battery cell", "A fault in the battery management system or its sensors"),
            listOf("An EV system warning", "Reduced power or range", "Charging stopping early or not starting"),
            listOf("Stop charging and park outside if you notice heat, smell or smoke", "Don't open or work on the battery yourself", "Ask a Hyundai dealer to test the battery; battery faults are often covered by the battery warranty"),
            "Hyundai 26-EV-002H: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2026/MC-11029938-0001.pdf
        e("P1AD3", "Hyundai", listOf(f(2025, 2025, "Ioniq 5")),
            "Drive battery abnormal performance: MISD",
            "Hyundai's definition: the battery management system has flagged abnormal drive battery behaviour under a check Hyundai labels MISD. The bulletin doesn't spell the label out.",
            H,
            listOf("A weak or damaged battery cell", "A fault in the battery management system or its sensors"),
            listOf("An EV system warning", "Reduced power or range", "Charging stopping early or not starting"),
            listOf("Stop charging and park outside if you notice heat, smell or smoke", "Don't open or work on the battery yourself", "Ask a Hyundai dealer to test the battery; battery faults are often covered by the battery warranty"),
            "Hyundai 26-EV-002H: the maker's own service bulletin, filed with NHTSA")
    }

    private fun toyota(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10171454-9999.pdf
        e("P1604", "Toyota", listOf(f(2009, 2019, "Tundra", "5.7 V8")),
            "Startability malfunction",
            "Toyota's definition: the engine took too long to start, or didn't keep running once it fired.",
            M,
            listOf("Running very low on fuel", "Poor fuel, or a change in ethanol blend on flex-fuel engines", "Low fuel pressure from a weak pump or blocked filter", "A weak battery or starter", "An air leak in the intake"),
            listOf("Long cranking before the engine starts", "The engine starting and then stalling", "A rough idle straight after starting"),
            listOf("Check there's enough fuel, and think about where you last filled up", "Check the battery's condition and terminals", "Have the fuel pressure tested if it keeps happening"),
            "Toyota T-SB-0166-19: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2013/SB-10061328-2273.pdf
        e("P1605", "Toyota", listOf(f(2012, 2015, "Tundra", "5.7 V8")),
            "Rough idling",
            "Toyota's definition: engine speed dropped well below the normal idle speed while the engine was running.",
            M,
            listOf("Poor fuel, or a change in ethanol blend on flex-fuel engines", "Low fuel pressure", "An air leak in the intake", "A dirty throttle body or air flow sensor"),
            listOf("A rough or lumpy idle", "The engine nearly stalling, or stalling, at a stop", "Hesitation just after starting"),
            listOf("Check the intake hoses for splits or loose clamps", "Try a tank of fresh fuel from a busy station", "Have the fuel pressure and throttle body checked if it returns"),
            "Toyota T-SB-0058-13: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10247820-9999.pdf
        e("P1C84", "Toyota", listOf(
                f(2018, 2023, "Camry", "2.5 hybrid"),
                f(2022, 2022, "Corolla", "1.8 hybrid|2.0 hybrid"),
                f(2020, 2023, "Highlander", "2.5 hybrid"),
                f(2016, 2022, "Prius", "1.8 hybrid"),
                f(2017, 2022, "Prius", "1.8 plug-in hybrid"),
                f(2019, 2023, "RAV4", "2.5 hybrid"),
                f(2021, 2022, "RAV4", "2.5 plug-in hybrid"),
                f(2022, 2023, "Tundra", "3.5 twin-turbo V6 hybrid"),
            ),
            "High-voltage power circuit short during READY",
            "Toyota's definition: the hybrid system found a short in its high-voltage power circuit while the car was ready to drive.",
            H,
            listOf("A fault in the high-voltage battery or its relays", "A fault in the inverter or another high-voltage part", "A damaged high-voltage cable or water in a connector"),
            listOf("A hybrid system warning on the dash", "The car refusing to go into READY", "The car shutting down the hybrid system while driving"),
            listOf("Don't touch the orange high-voltage cables or open any high-voltage part yourself", "Note any hybrid system warning on the dash and whether the car still goes into READY", "Have a dealer or hybrid-trained technician test the high-voltage system"),
            "Toyota T-SB-0085-23: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10247820-9999.pdf
        e("P3004", "Toyota", listOf(
                f(2018, 2023, "Camry", "2.5 hybrid"),
                f(2022, 2022, "Corolla", "1.8 hybrid|2.0 hybrid"),
                f(2020, 2023, "Highlander", "2.5 hybrid"),
                f(2016, 2022, "Prius", "1.8 hybrid"),
                f(2017, 2022, "Prius", "1.8 plug-in hybrid"),
                f(2019, 2023, "RAV4", "2.5 hybrid"),
                f(2021, 2022, "RAV4", "2.5 plug-in hybrid"),
                f(2022, 2023, "Tundra", "3.5 twin-turbo V6 hybrid"),
            ),
            "High-voltage power circuit short during pre-charge",
            "Toyota's definition: the hybrid system found a short in its high-voltage power circuit while the system was powering up (pre-charge).",
            H,
            listOf("A fault in the high-voltage battery or its relays", "A fault in the inverter or another high-voltage part", "A damaged high-voltage cable or water in a connector"),
            listOf("A hybrid system warning on the dash", "The car refusing to go into READY", "The car shutting down the hybrid system while driving"),
            listOf("Don't touch the orange high-voltage cables or open any high-voltage part yourself", "Note any hybrid system warning on the dash and whether the car still goes into READY", "Have a dealer or hybrid-trained technician test the high-voltage system"),
            "Toyota T-SB-0085-23: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10206537-9999.pdf
        e("P1CE9", "Toyota", listOf(f(2021, 2021, "RAV4", "2.5 plug-in hybrid")),
            "Charger cooling fan speed sensor: no signal",
            "Toyota's definition: the computer isn't getting a speed signal from the fan that cools the on-board battery charger.",
            L,
            listOf("A failed charger cooling fan", "Something jamming the fan", "A loose connector or damaged wiring to the fan"),
            listOf("Usually nothing while driving", "Slower charging from the wall, or charging that stops early"),
            listOf("Check nothing is blocking the charger's air vents", "Listen for the fan while the car is charging", "Have a Toyota dealer test the fan and its wiring"),
            "Toyota T-SB-0021-21: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10188912-9999.pdf
        e("P1CCC", "Toyota", listOf(f(2021, 2021, "RAV4", "2.5 plug-in hybrid")),
            "DC/DC converter internal failure",
            "Toyota's definition: an internal fault in the DC/DC converter, the part that charges the 12-volt battery from the high-voltage battery.",
            H,
            listOf("An internal fault in the DC/DC converter", "A software fault in the converter's control unit"),
            listOf("A battery or hybrid system warning", "The 12-volt battery going flat", "The car not starting after it has been parked"),
            listOf("Don't rely on the car for long trips until it's checked", "Have the 12-volt battery tested", "Ask a Toyota dealer to check the converter and whether an update or replacement applies"),
            "Toyota T-SB-0007-21: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10220227-9999.pdf
        e("P1C1B", "Toyota", listOf(f(2021, 2022, "RAV4", "2.5 plug-in hybrid")),
            "Charger boost circuit malfunction",
            "Toyota's definition: a fault in the boost (power factor correction) circuit of the on-board charger, which turns wall power into charge for the battery.",
            M,
            listOf("An internal fault in the on-board charger", "A poor mains supply or faulty charging cable in some cases"),
            listOf("Charging from the wall stopping or not starting", "A charging system warning", "The car still driving normally on petrol and stored charge"),
            listOf("Try a different power point or charging station", "Check the charging cable and plug for damage", "Have a Toyota dealer test the on-board charger"),
            "Toyota T-SB-0067-22: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10220227-9999.pdf
        e("P1BD3", "Toyota", listOf(f(2021, 2022, "RAV4", "2.5 plug-in hybrid")),
            "Charger B boost circuit malfunction",
            "Toyota's definition: a fault in the boost (power factor correction) circuit of the second on-board charger circuit (charger B), which turns wall power into charge for the battery.",
            M,
            listOf("An internal fault in the on-board charger", "A poor mains supply or faulty charging cable in some cases"),
            listOf("Charging from the wall stopping or not starting", "A charging system warning", "The car still driving normally on petrol and stored charge"),
            listOf("Try a different power point or charging station", "Check the charging cable and plug for damage", "Have a Toyota dealer test the on-board charger"),
            "Toyota T-SB-0067-22: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2012/SB-10075945-0699.pdf
        e("P1441", "Toyota", listOf(
                f(2005, 2009, "4Runner", "4.7 V8"),
                f(2006, 2007, "LandCruiser", "4.7 V8 petrol"),
                f(2005, 2006, "Tundra", "4.7 V8"),
            ),
            "Secondary air injection switching valve no. 2 stuck open (bank 1)",
            "Toyota's definition: the second switching valve of the secondary air injection system on bank 1 is stuck open. The system blows fresh air into the exhaust for a short time after a cold start to cut emissions.",
            M,
            listOf("A secondary air switching valve stuck by moisture or corrosion", "A failed air injection pump", "Damaged wiring or a blown fuse for the air injection system"),
            listOf("Check-engine light on", "Reduced power or limp-home mode on some cars", "A loud whirring pump noise after a cold start in some cases"),
            listOf("Listen for the air pump running for a short time after a cold start", "Check the air injection fuses and relay", "Have the switching valves tested; a stuck valve needs replacing"),
            "Toyota T-SB-0230-12: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2012/SB-10075945-0699.pdf
        e("P1442", "Toyota", listOf(
                f(2005, 2009, "4Runner", "4.7 V8"),
                f(2006, 2007, "LandCruiser", "4.7 V8 petrol"),
                f(2005, 2006, "Tundra", "4.7 V8"),
            ),
            "Secondary air injection switching valve no. 2 stuck closed (bank 1)",
            "Toyota's definition: the second switching valve of the secondary air injection system on bank 1 is stuck closed. The system blows fresh air into the exhaust for a short time after a cold start to cut emissions.",
            M,
            listOf("A secondary air switching valve stuck by moisture or corrosion", "A failed air injection pump", "Damaged wiring or a blown fuse for the air injection system"),
            listOf("Check-engine light on", "Reduced power or limp-home mode on some cars", "A loud whirring pump noise after a cold start in some cases"),
            listOf("Listen for the air pump running for a short time after a cold start", "Check the air injection fuses and relay", "Have the switching valves tested; a stuck valve needs replacing"),
            "Toyota T-SB-0230-12: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2012/SB-10075945-0699.pdf
        e("P1444", "Toyota", listOf(
                f(2005, 2009, "4Runner", "4.7 V8"),
                f(2006, 2007, "LandCruiser", "4.7 V8 petrol"),
                f(2005, 2006, "Tundra", "4.7 V8"),
            ),
            "Secondary air injection switching valve no. 2 stuck open (bank 2)",
            "Toyota's definition: the second switching valve of the secondary air injection system on bank 2 is stuck open. The system blows fresh air into the exhaust for a short time after a cold start to cut emissions.",
            M,
            listOf("A secondary air switching valve stuck by moisture or corrosion", "A failed air injection pump", "Damaged wiring or a blown fuse for the air injection system"),
            listOf("Check-engine light on", "Reduced power or limp-home mode on some cars", "A loud whirring pump noise after a cold start in some cases"),
            listOf("Listen for the air pump running for a short time after a cold start", "Check the air injection fuses and relay", "Have the switching valves tested; a stuck valve needs replacing"),
            "Toyota T-SB-0230-12: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2012/SB-10075945-0699.pdf
        e("P1445", "Toyota", listOf(
                f(2005, 2009, "4Runner", "4.7 V8"),
                f(2006, 2007, "LandCruiser", "4.7 V8 petrol"),
                f(2005, 2006, "Tundra", "4.7 V8"),
            ),
            "Secondary air injection switching valve no. 2 stuck closed (bank 2)",
            "Toyota's definition: the second switching valve of the secondary air injection system on bank 2 is stuck closed. The system blows fresh air into the exhaust for a short time after a cold start to cut emissions.",
            M,
            listOf("A secondary air switching valve stuck by moisture or corrosion", "A failed air injection pump", "Damaged wiring or a blown fuse for the air injection system"),
            listOf("Check-engine light on", "Reduced power or limp-home mode on some cars", "A loud whirring pump noise after a cold start in some cases"),
            listOf("Listen for the air pump running for a short time after a cold start", "Check the air injection fuses and relay", "Have the switching valves tested; a stuck valve needs replacing"),
            "Toyota T-SB-0230-12: the maker's own service bulletin, filed with NHTSA")
    }

    private fun gm(sink: Sink) = with(sink) {
        // https://gsi.ext.gm.com/gmspo/mode6/pdf/2006/2006file5.pdf
        e("P1101", "Chevrolet", listOf(
                f(2006, 2006, "Cobalt", "2.0 supercharged (SS)|2.2 I4"),
                f(2006, 2006, "Colorado", "2.8 I4"),
                f(2006, 2006, "Malibu", "2.2 I4"),
                f(2006, 2006, "TrailBlazer", "4.2 I6"),
            ),
            "Intake rationality crosscheck out of range",
            "GM's definition: the engine computer compares its air flow, intake pressure and throttle readings with each other, and they don't agree.",
            M,
            listOf("A dirty or failing air flow (MAF) sensor", "An air leak between the air filter and the engine", "A loose intake duct or split hose", "A blocked air filter", "A dirty throttle body"),
            listOf("Hesitation or uneven power", "A rough idle", "Higher fuel use"),
            listOf("Check the air filter and that the intake duct is clipped together properly", "Look for split or disconnected hoses on the intake", "Have the air flow sensor cleaned or tested if the code returns"),
            "GM 2006 engine diagnostic parameters: the maker's own published document")
        // https://gsi.ext.gm.com/gmspo/mode6/pdf/2006/2006file5.pdf
        e("P1101", "Saturn", listOf(f(2006, 2006, "Ion", "2.0 supercharged|2.2 I4")),
            "Intake rationality crosscheck out of range",
            "GM's definition: the engine computer compares its air flow, intake pressure and throttle readings with each other, and they don't agree.",
            M,
            listOf("A dirty or failing air flow (MAF) sensor", "An air leak between the air filter and the engine", "A loose intake duct or split hose", "A blocked air filter", "A dirty throttle body"),
            listOf("Hesitation or uneven power", "A rough idle", "Higher fuel use"),
            listOf("Check the air filter and that the intake duct is clipped together properly", "Look for split or disconnected hoses on the intake", "Have the air flow sensor cleaned or tested if the code returns"),
            "GM 2006 engine diagnostic parameters: the maker's own published document")
        // https://gsi.ext.gm.com/gmspo/mode6/pdf/2006/2006file5.pdf
        e("P1101", "GMC", listOf(f(2006, 2006, "Canyon", "2.8 I4"), f(2006, 2006, "Envoy", "4.2 I6")),
            "Intake rationality crosscheck out of range",
            "GM's definition: the engine computer compares its air flow, intake pressure and throttle readings with each other, and they don't agree.",
            M,
            listOf("A dirty or failing air flow (MAF) sensor", "An air leak between the air filter and the engine", "A loose intake duct or split hose", "A blocked air filter", "A dirty throttle body"),
            listOf("Hesitation or uneven power", "A rough idle", "Higher fuel use"),
            listOf("Check the air filter and that the intake duct is clipped together properly", "Look for split or disconnected hoses on the intake", "Have the air flow sensor cleaned or tested if the code returns"),
            "GM 2006 engine diagnostic parameters: the maker's own published document")
        // https://gsi.ext.gm.com/gmspo/mode6/pdf/2006/2006file5.pdf
        e("P1101", "Saab", listOf(f(2006, 2006, "9-7X", "4.2 I6")),
            "Intake rationality crosscheck out of range",
            "GM's definition: the engine computer compares its air flow, intake pressure and throttle readings with each other, and they don't agree.",
            M,
            listOf("A dirty or failing air flow (MAF) sensor", "An air leak between the air filter and the engine", "A loose intake duct or split hose", "A blocked air filter", "A dirty throttle body"),
            listOf("Hesitation or uneven power", "A rough idle", "Higher fuel use"),
            listOf("Check the air filter and that the intake duct is clipped together properly", "Look for split or disconnected hoses on the intake", "Have the air flow sensor cleaned or tested if the code returns"),
            "GM 2006 engine diagnostic parameters: the maker's own published document")
        // https://gsi.ext.gm.com/gmspo/mode6/pdf/2006/2006file5.pdf
        e("P1101", "Isuzu", listOf(f(2006, 2006, "Ascender", "4.2 I6")),
            "Intake rationality crosscheck out of range",
            "GM's definition: the engine computer compares its air flow, intake pressure and throttle readings with each other, and they don't agree.",
            M,
            listOf("A dirty or failing air flow (MAF) sensor", "An air leak between the air filter and the engine", "A loose intake duct or split hose", "A blocked air filter", "A dirty throttle body"),
            listOf("Hesitation or uneven power", "A rough idle", "Higher fuel use"),
            listOf("Check the air filter and that the intake duct is clipped together properly", "Look for split or disconnected hoses on the intake", "Have the air flow sensor cleaned or tested if the code returns"),
            "GM 2006 engine diagnostic parameters: the maker's own published document")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10230607-0001.pdf
        e("P1101", "Buick", listOf(f(2016, 2022, "Encore", "1.4 turbo")),
            "Intake air flow system performance",
            "GM's definition: the engine computer compares its air flow, intake pressure, boost and throttle readings with each other, and they don't agree.",
            M,
            listOf("A dirty or failing air flow (MAF) sensor", "An air leak between the air filter and the engine", "A loose intake duct or split hose", "A blocked air filter", "A dirty throttle body", "A leak in the turbo pipework or intercooler"),
            listOf("Hesitation or reduced power", "A rough idle", "Higher fuel use"),
            listOf("Check the air filter and that the intake duct is clipped together properly", "Look for split or disconnected hoses on the intake", "Have the air flow sensor cleaned or tested if the code returns"),
            "GM 22-NA-224: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10230607-0001.pdf
        e("P1101", "Chevrolet", listOf(
                f(2016, 2019, "Cruze", "1.4 turbo"),
                f(2018, 2022, "Equinox", "1.5 turbo"),
                f(2016, 2022, "Malibu", "1.5 turbo"),
                f(2019, 2022, "Trax", "1.4 turbo"),
                f(2016, 2019, "Volt", "1.5 plug-in hybrid"),
            ),
            "Intake air flow system performance",
            "GM's definition: the engine computer compares its air flow, intake pressure, boost and throttle readings with each other, and they don't agree.",
            M,
            listOf("A dirty or failing air flow (MAF) sensor", "An air leak between the air filter and the engine", "A loose intake duct or split hose", "A blocked air filter", "A dirty throttle body", "A leak in the turbo pipework or intercooler"),
            listOf("Hesitation or reduced power", "A rough idle", "Higher fuel use"),
            listOf("Check the air filter and that the intake duct is clipped together properly", "Look for split or disconnected hoses on the intake", "Have the air flow sensor cleaned or tested if the code returns"),
            "GM 22-NA-224: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10230607-0001.pdf
        e("P1101", "GMC", listOf(f(2018, 2022, "Terrain", "1.5 turbo")),
            "Intake air flow system performance",
            "GM's definition: the engine computer compares its air flow, intake pressure, boost and throttle readings with each other, and they don't agree.",
            M,
            listOf("A dirty or failing air flow (MAF) sensor", "An air leak between the air filter and the engine", "A loose intake duct or split hose", "A blocked air filter", "A dirty throttle body", "A leak in the turbo pipework or intercooler"),
            listOf("Hesitation or reduced power", "A rough idle", "Higher fuel use"),
            listOf("Check the air filter and that the intake duct is clipped together properly", "Look for split or disconnected hoses on the intake", "Have the air flow sensor cleaned or tested if the code returns"),
            "GM 22-NA-224: the maker's own service bulletin, filed with NHTSA")
        // https://gsi.ext.gm.com/gmspo/mode6/pdf/2006/2006file5.pdf
        e("P1133", "Chevrolet", listOf(
                f(2006, 2006, "Cobalt", "2.0 supercharged (SS)|2.2 I4"),
                f(2006, 2006, "Colorado", "2.8 I4"),
                f(2006, 2006, "Malibu", "2.2 I4"),
                f(2006, 2006, "TrailBlazer", "4.2 I6"),
            ),
            "Oxygen sensor insufficient switching (bank 1, sensor 1)",
            "GM's definition: the front oxygen sensor (before the catalytic converter) isn't switching between rich and lean readings as often as it should.",
            M,
            listOf("An old, worn-out oxygen sensor", "A sensor contaminated by oil burning or a coolant leak", "An exhaust leak near the sensor", "Damaged sensor wiring"),
            listOf("Higher fuel use", "A rough idle in some cases", "Can lead to a catalytic converter code if ignored"),
            listOf("Check for exhaust leaks near the sensor", "Check the sensor's wiring and connector", "Replace the sensor if it's old and the wiring is good"),
            "GM 2006 engine diagnostic parameters: the maker's own published document")
        // https://gsi.ext.gm.com/gmspo/mode6/pdf/2006/2006file5.pdf
        e("P1133", "Saturn", listOf(f(2006, 2006, "Ion", "2.0 supercharged|2.2 I4")),
            "Oxygen sensor insufficient switching (bank 1, sensor 1)",
            "GM's definition: the front oxygen sensor (before the catalytic converter) isn't switching between rich and lean readings as often as it should.",
            M,
            listOf("An old, worn-out oxygen sensor", "A sensor contaminated by oil burning or a coolant leak", "An exhaust leak near the sensor", "Damaged sensor wiring"),
            listOf("Higher fuel use", "A rough idle in some cases", "Can lead to a catalytic converter code if ignored"),
            listOf("Check for exhaust leaks near the sensor", "Check the sensor's wiring and connector", "Replace the sensor if it's old and the wiring is good"),
            "GM 2006 engine diagnostic parameters: the maker's own published document")
        // https://gsi.ext.gm.com/gmspo/mode6/pdf/2006/2006file5.pdf
        e("P1133", "GMC", listOf(f(2006, 2006, "Canyon", "2.8 I4"), f(2006, 2006, "Envoy", "4.2 I6")),
            "Oxygen sensor insufficient switching (bank 1, sensor 1)",
            "GM's definition: the front oxygen sensor (before the catalytic converter) isn't switching between rich and lean readings as often as it should.",
            M,
            listOf("An old, worn-out oxygen sensor", "A sensor contaminated by oil burning or a coolant leak", "An exhaust leak near the sensor", "Damaged sensor wiring"),
            listOf("Higher fuel use", "A rough idle in some cases", "Can lead to a catalytic converter code if ignored"),
            listOf("Check for exhaust leaks near the sensor", "Check the sensor's wiring and connector", "Replace the sensor if it's old and the wiring is good"),
            "GM 2006 engine diagnostic parameters: the maker's own published document")
        // https://gsi.ext.gm.com/gmspo/mode6/pdf/2006/2006file5.pdf
        e("P1133", "Saab", listOf(f(2006, 2006, "9-7X", "4.2 I6")),
            "Oxygen sensor insufficient switching (bank 1, sensor 1)",
            "GM's definition: the front oxygen sensor (before the catalytic converter) isn't switching between rich and lean readings as often as it should.",
            M,
            listOf("An old, worn-out oxygen sensor", "A sensor contaminated by oil burning or a coolant leak", "An exhaust leak near the sensor", "Damaged sensor wiring"),
            listOf("Higher fuel use", "A rough idle in some cases", "Can lead to a catalytic converter code if ignored"),
            listOf("Check for exhaust leaks near the sensor", "Check the sensor's wiring and connector", "Replace the sensor if it's old and the wiring is good"),
            "GM 2006 engine diagnostic parameters: the maker's own published document")
        // https://gsi.ext.gm.com/gmspo/mode6/pdf/2006/2006file5.pdf
        e("P1133", "Isuzu", listOf(f(2006, 2006, "Ascender", "4.2 I6")),
            "Oxygen sensor insufficient switching (bank 1, sensor 1)",
            "GM's definition: the front oxygen sensor (before the catalytic converter) isn't switching between rich and lean readings as often as it should.",
            M,
            listOf("An old, worn-out oxygen sensor", "A sensor contaminated by oil burning or a coolant leak", "An exhaust leak near the sensor", "Damaged sensor wiring"),
            listOf("Higher fuel use", "A rough idle in some cases", "Can lead to a catalytic converter code if ignored"),
            listOf("Check for exhaust leaks near the sensor", "Check the sensor's wiring and connector", "Replace the sensor if it's old and the wiring is good"),
            "GM 2006 engine diagnostic parameters: the maker's own published document")
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10191214-9999.pdf
        e("P1682", "Chevrolet", listOf(f(2019, 2021, "Silverado", "5.3 V8|6.2 V8")),
            "Ignition 1 switch circuit 2",
            "GM's definition: the engine computer gets the ignition-on signal on two circuits, and the second one doesn't match what it expects.",
            M,
            listOf("A poor connection at a fuse block or body control module", "Damaged wiring in the ignition feed", "A weak battery or loose battery terminal"),
            listOf("Check-engine light on", "Other warning lights or messages at the same time", "Hard starting or stalling in some cases"),
            listOf("Check the battery terminals are clean and tight", "Check the engine bay fuse block for loose or damaged fuses", "Have the ignition feed wiring tested if the code returns"),
            "GM 21-NA-032: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10191214-9999.pdf
        e("P1682", "GMC", listOf(f(2019, 2021, "Sierra", "5.3 V8|6.2 V8")),
            "Ignition 1 switch circuit 2",
            "GM's definition: the engine computer gets the ignition-on signal on two circuits, and the second one doesn't match what it expects.",
            M,
            listOf("A poor connection at a fuse block or body control module", "Damaged wiring in the ignition feed", "A weak battery or loose battery terminal"),
            listOf("Check-engine light on", "Other warning lights or messages at the same time", "Hard starting or stalling in some cases"),
            listOf("Check the battery terminals are clean and tight", "Check the engine bay fuse block for loose or damaged fuses", "Have the ignition feed wiring tested if the code returns"),
            "GM 21-NA-032: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2014/SB-10069964-0335.pdf
        e("P1B0F", "Chevrolet", listOf(f(2011, 2013, "Volt", "1.4 plug-in hybrid")),
            "Drive motor A position sensor learn incorrect",
            "GM's definition: the learned position value for drive motor A's position sensor isn't valid. The computer needs it to control the electric motor accurately.",
            M,
            listOf("The position learn not being completed after a repair", "A fault in the motor position sensor", "A control module fault"),
            listOf("Check-engine light on", "Reduced power in some cases", "Often appears after drive unit or control module work"),
            listOf("Note whether the code appeared after recent repairs", "Have a GM dealer or hybrid-trained technician repeat the position learn", "Have the sensor and wiring tested if the learn won't complete"),
            "GM PIP4871C: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2014/SB-10069964-0335.pdf
        e("P1B10", "Chevrolet", listOf(f(2011, 2013, "Volt", "1.4 plug-in hybrid")),
            "Drive motor B position sensor learn incorrect",
            "GM's definition: the learned position value for drive motor B's position sensor isn't valid. The computer needs it to control the electric motor accurately.",
            M,
            listOf("The position learn not being completed after a repair", "A fault in the motor position sensor", "A control module fault"),
            listOf("Check-engine light on", "Reduced power in some cases", "Often appears after drive unit or control module work"),
            listOf("Note whether the code appeared after recent repairs", "Have a GM dealer or hybrid-trained technician repeat the position learn", "Have the sensor and wiring tested if the learn won't complete"),
            "GM PIP4871C: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10201319-9999.pdf; https://static.nhtsa.gov/odi/tsbs/2023/MC-10230607-0001.pdf
        e("P1101", "Holden", listOf(f(2018, 2020, "Equinox", "1.5 turbo")),
            "Intake air flow system performance",
            "GM's definition: the engine computer compares its air flow, intake pressure, boost and throttle readings with each other, and they don't agree.",
            M,
            listOf("The hose from the charge air cooler (intercooler) has come off the throttle body, the cause GM names for these cars", "A dirty or failing mass air flow sensor", "An air leak in the intake after the air flow sensor", "A blocked air filter"),
            listOf("Reduced engine power", "Hesitation", "A rough idle"),
            listOf("Check the large hose from the intercooler is firmly clamped onto the throttle body", "Look for other loose or split intake and turbo hoses", "Have the air flow sensor readings checked if the hoses are sound"),
            "GM PIP5651F (wording from GM 22-NA-224): the maker's own service bulletin, filed with NHTSA")
    }

    private fun honda(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2013/SB-10053545-3978.pdf
        e("P1456", "Honda", listOf(
                f(1998, 2002, "Accord"),
                f(1998, 2001, "CR-V"),
                f(1996, 2000, "Civic"),
                f(2000, 2005, "Insight"),
                f(1999, 2004, "Odyssey"),
                f(2003, 2004, "Pilot"),
                f(2000, 2006, "S2000"),
            ),
            "EVAP control system leakage (fuel tank system)",
            "Honda's definition: the system that traps fuel vapour has a leak on the side of the system that includes the fuel tank and filler cap.",
            L,
            listOf("A loose, damaged or wrong fuel filler cap", "A worn filler cap seal", "A cracked filler neck or tank hose"),
            listOf("Usually nothing you'd notice", "A fuel smell in some cases"),
            listOf("Refit the fuel cap and turn it until it clicks", "Check the cap seal for cracks and replace the cap if it's worn", "Have the system smoke-tested if the code returns"),
            "Honda service bulletin 99-075: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2015/SB-10099960-9340.pdf
        e("P1456", "Acura", listOf(
                f(1997, 2003, "CL"),
                f(2001, 2004, "MDX"),
                f(1997, 2005, "NSX"),
                f(1996, 2004, "RL"),
                f(1999, 2003, "TL"),
            ),
            "EVAP control system leakage (fuel tank system)",
            "Acura's definition: the system that traps fuel vapour has a leak on the side of the system that includes the fuel tank and filler cap.",
            L,
            listOf("A loose, damaged or wrong fuel filler cap", "A worn filler cap seal", "A cracked filler neck or tank hose"),
            listOf("Usually nothing you'd notice", "A fuel smell in some cases"),
            listOf("Refit the fuel cap and turn it until it clicks", "Check the cap seal for cracks and replace the cap if it's worn", "Have the system smoke-tested if the code returns"),
            "Acura service bulletin 99-040: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2013/SB-10053545-3978.pdf
        e("P1457", "Honda", listOf(
                f(1998, 2002, "Accord"),
                f(1998, 2001, "CR-V"),
                f(1996, 2000, "Civic"),
                f(2000, 2005, "Insight"),
                f(1999, 2004, "Odyssey"),
                f(2003, 2004, "Pilot"),
                f(2000, 2006, "S2000"),
            ),
            "EVAP control system leakage (EVAP canister system)",
            "Honda's definition: the system that traps fuel vapour has a leak on the side of the system that includes the charcoal canister and its valves.",
            L,
            listOf("A canister vent shut valve stuck open", "A cracked charcoal canister or split hose", "A faulty purge or two-way valve"),
            listOf("Usually nothing you'd notice", "A fuel smell in some cases"),
            listOf("Check the hoses around the charcoal canister for splits", "Check the fuel cap as well, since it's the cheapest thing to rule out", "Have the system smoke-tested to find the leak"),
            "Honda service bulletin 99-075: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2015/SB-10099960-9340.pdf
        e("P1457", "Acura", listOf(
                f(1997, 2003, "CL"),
                f(2001, 2004, "MDX"),
                f(1997, 2005, "NSX"),
                f(1996, 2004, "RL"),
                f(1999, 2003, "TL"),
            ),
            "EVAP control system leakage (EVAP canister system)",
            "Acura's definition: the system that traps fuel vapour has a leak on the side of the system that includes the charcoal canister and its valves.",
            L,
            listOf("A canister vent shut valve stuck open", "A cracked charcoal canister or split hose", "A faulty purge or two-way valve"),
            listOf("Usually nothing you'd notice", "A fuel smell in some cases"),
            listOf("Check the hoses around the charcoal canister for splits", "Check the fuel cap as well, since it's the cheapest thing to rule out", "Have the system smoke-tested to find the leak"),
            "Acura service bulletin 99-040: the maker's own service bulletin, filed with NHTSA")
    }

    private fun nissan(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10075090-5448.pdf; https://static.nhtsa.gov/odi/tsbs/2019/MC-10167365-0001.pdf
        e("P17F0", "Nissan", listOf(
                f(2013, 2016, "Altima", "2.5 I4"),
                f(2013, 2017, "Altima|Pathfinder", "3.5 V6"),
                f(2016, 2017, "Maxima", "3.5 V6"),
                f(2015, 2017, "Murano", "3.5 V6"),
                f(2014, 2016, "Rogue", "2.5 I4"),
            ),
            "CVT judder (transmission inspection)",
            "Nissan's definition: the gearbox computer has detected judder (a shudder) in the CVT automatic, and points to the transmission itself.",
            M,
            listOf("Wear inside the CVT, such as the belt and pulleys", "Old or wrong CVT fluid"),
            listOf("A shudder or vibration when pulling away or at light throttle", "Revs rising and falling on their own", "The gearbox feeling like it's slipping"),
            listOf("Note when the shudder happens, such as speed and how warm the car is", "Have the CVT fluid level and condition checked", "Ask a Nissan dealer to test it; Nissan's fix for this code is transmission repair or replacement"),
            "Nissan NTB15-084b, NTB17-039h: the maker's own service bulletins, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10075090-5448.pdf; https://static.nhtsa.gov/odi/tsbs/2019/MC-10167365-0001.pdf
        e("P17F1", "Nissan", listOf(
                f(2013, 2016, "Altima", "2.5 I4"),
                f(2013, 2017, "Altima|Pathfinder", "3.5 V6"),
                f(2016, 2017, "Maxima", "3.5 V6"),
                f(2015, 2017, "Murano", "3.5 V6"),
                f(2014, 2016, "Rogue", "2.5 I4"),
            ),
            "CVT judder (control unit inspection)",
            "Nissan's definition: the gearbox computer has detected judder (a shudder) in the CVT automatic, and points to the gearbox's control valve unit.",
            M,
            listOf("A fault in the CVT's control valve unit", "Old or wrong CVT fluid"),
            listOf("A shudder or vibration when pulling away or at light throttle", "Revs rising and falling on their own"),
            listOf("Note when the shudder happens, such as speed and how warm the car is", "Have the CVT fluid level and condition checked", "Ask a Nissan dealer to test it; Nissan's fix for this code is a new control valve unit"),
            "Nissan NTB15-084b, NTB17-039h: the maker's own service bulletins, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10237836-0001.pdf
        e("P17F4", "Nissan", listOf(f(2018, 2022, "Kicks", "1.6 I4")),
            "Auxiliary gearbox clutch judder",
            "Nissan's definition: the gearbox computer has detected judder in a clutch of the CVT's auxiliary gearbox, the small two-speed gear set built into this transmission.",
            M,
            listOf("Wear in the auxiliary gearbox clutch", "Old or wrong CVT fluid", "Gearbox software that needs updating"),
            listOf("A shudder when pulling away or at low speed", "A jolt as the gearbox changes range"),
            listOf("Note when the shudder happens", "Have the CVT fluid level and condition checked", "Ask a Nissan dealer to test the gearbox and check for updates"),
            "Nissan NTB23-048: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10207877-0001.pdf
        e("P11A7", "Nissan", listOf(f(2019, 2021, "Altima", "2.0 VC-Turbo")),
            "Variable compression stopper position",
            "Nissan's definition: on the variable compression (VC-Turbo) engine, the end-stop position of the mechanism that changes the compression ratio isn't where the engine computer expects.",
            M,
            listOf("A fault in the variable compression actuator", "A fault in the mechanism's linkage", "Engine software that needs updating"),
            listOf("Check-engine light on", "Reduced power", "Higher fuel use"),
            listOf("Check the engine oil level, since the mechanism relies on good oil", "Ask a Nissan dealer to test the variable compression actuator and check for updates"),
            "Nissan NTB21-110A: the maker's own service bulletin, filed with NHTSA")
    }

    private fun mazda(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2025/MC-11015003-0001.pdf
        e("P1260", "Mazda", listOf(
                f(2020, 2025, "CX-30"),
                f(2020, 2025, "CX-5"),
                f(2020, 2023, "CX-9"),
                f(2024, 2025, "CX-90"),
                f(2020, 2025, "MX-5"),
                f(2019, 2025, "Mazda3"),
                f(2020, 2021, "Mazda6"),
            ),
            "Immobiliser system problem",
            "Mazda's definition: a problem in the immobiliser, the system that checks your key before it lets the engine start.",
            M,
            listOf("A flat battery in the key fob", "A key the car no longer recognises", "A fault in the keyless entry or immobiliser units", "Low 12-volt battery voltage"),
            listOf("The engine not starting, or starting and cutting out", "A key or security warning light", "The fault coming and going"),
            listOf("Try the spare key", "Replace the key fob battery", "Have the car's 12-volt battery tested", "Ask a Mazda dealer to check the keys and immobiliser if it returns"),
            "Mazda service alert on advanced keyless entry: the maker's own service bulletin, filed with NHTSA")
    }

    private fun volkswagenGroup(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2017/MC-10128047-9999.pdf
        e("P3081", "Volkswagen", listOf(f(2016, 2017, "Jetta", "1.4 turbo")),
            "Engine temperature too low",
            "Volkswagen's definition: the engine isn't reaching its normal running temperature, or is taking too long to get there.",
            L,
            listOf("A thermostat stuck open", "A fault in the electronic coolant control valve on some engines", "A faulty coolant temperature sensor", "Low coolant"),
            listOf("A temperature gauge that stays low", "A heater that's slow to blow warm", "Higher fuel use"),
            listOf("Check the coolant level when the engine is cold", "Watch whether the temperature gauge reaches the middle on a normal drive", "Have the thermostat and temperature sensor tested"),
            "VW technical bulletin 2045384: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10208895-0001.pdf
        e("P3081", "Audi", listOf(
                f(2018, 2020, "A6|S4|A5", "3.0 turbo V6"),
                f(2019, 2020, "A8", "3.0 turbo V6"),
                f(2018, 2020, "Q5", "3.0 TFSI turbo (SQ5)"),
                f(2020, 2021, "Q7", "3.0 TFSI"),
            ),
            "Engine temperature too low",
            "Audi's definition: the engine isn't reaching its normal running temperature, or is taking too long to get there.",
            L,
            listOf("A thermostat stuck open", "A fault in the electronic coolant control valve on some engines", "A faulty coolant temperature sensor", "Low coolant"),
            listOf("A temperature gauge that stays low", "A heater that's slow to blow warm", "Higher fuel use"),
            listOf("Check the coolant level when the engine is cold", "Watch whether the temperature gauge reaches the middle on a normal drive", "Have the thermostat and temperature sensor tested"),
            "Audi TSB 2058767/8: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2014/SB-10065411-2280.pdf
        e("P119A", "Audi", listOf(
                f(2008, 2012, "A3", "2.0 TFSI turbo"),
                f(2009, 2012, "A4|TT", "2.0 TFSI turbo"),
                f(2009, 2012, "A5", "2.0 turbo"),
                f(2011, 2012, "Q5", "2.0 TFSI turbo"),
            ),
            "Fuel pressure sensor malfunction",
            "Audi's definition: a fault with the fuel pressure sensor (Audi calls it G247) that reads the pressure in the high-pressure fuel rail.",
            M,
            listOf("A failing fuel pressure sensor", "Damaged wiring or a loose connector at the sensor", "A real fuel pressure problem, such as a weak pump"),
            listOf("Hard starting", "Hesitation or reduced power", "Stalling in some cases"),
            listOf("Check the sensor's connector and wiring on the fuel rail", "Have the fuel pressure measured to see whether the sensor or the pressure is wrong", "Replace the sensor if the pressure is good"),
            "Audi TSB 2027306: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2014/MC-10239837-9999.pdf
        e("P13CE P13D1 P13D4 P13D7", "Volkswagen", listOf(
                f(2012, 2014, "Beetle", "2.0 diesel"),
                f(2010, 2014, "Golf", "2.0 TDI diesel"),
                f(2009, 2014, "Jetta", "2.0 diesel"),
                f(2012, 2014, "Passat", "2.0 TDI diesel"),
            ),
            "Cylinder pressure sensor: electrical fault",
            "Volkswagen's definition: the pressure sensor inside the glow plug, which measures pressure in the cylinder, has an electrical fault. The four codes are for cylinders 1 to 4 in order.",
            M,
            listOf("A loose connector on that cylinder's glow plug, the most likely cause in Volkswagen's bulletin", "A failed glow plug (the pressure sensor is built into it)", "Damaged wiring to the glow plug"),
            listOf("Check-engine light on", "Harder cold starts or a rough idle when cold in some cases", "Often no change in how the car drives"),
            listOf("Check that the glow plug connector is fully pushed home and doesn't pull off easily", "Have a workshop swap that glow plug with the one next to it and see whether the code moves with it", "Replace the glow plug only if the code follows it to the other cylinder"),
            "VW technical bulletin 2037386: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2014/MC-10239837-9999.pdf
        e("P13CF P13D2 P13D5 P13D8", "Volkswagen", listOf(
                f(2012, 2014, "Beetle", "2.0 diesel"),
                f(2010, 2014, "Golf", "2.0 TDI diesel"),
                f(2009, 2014, "Jetta", "2.0 diesel"),
                f(2012, 2014, "Passat", "2.0 TDI diesel"),
            ),
            "Cylinder pressure sensor: short circuit to ground",
            "Volkswagen's definition: the pressure sensor inside the glow plug, which measures pressure in the cylinder, is shorted to ground. The four codes are for cylinders 1 to 4 in order.",
            M,
            listOf("A loose connector on that cylinder's glow plug, the most likely cause in Volkswagen's bulletin", "A failed glow plug (the pressure sensor is built into it)", "Damaged wiring to the glow plug"),
            listOf("Check-engine light on", "Harder cold starts or a rough idle when cold in some cases", "Often no change in how the car drives"),
            listOf("Check that the glow plug connector is fully pushed home and doesn't pull off easily", "Have a workshop swap that glow plug with the one next to it and see whether the code moves with it", "Replace the glow plug only if the code follows it to the other cylinder"),
            "VW technical bulletin 2037386: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2014/MC-10239837-9999.pdf
        e("P13D0 P13D3 P13D6 P13D9", "Volkswagen", listOf(
                f(2012, 2014, "Beetle", "2.0 diesel"),
                f(2010, 2014, "Golf", "2.0 TDI diesel"),
                f(2009, 2014, "Jetta", "2.0 diesel"),
                f(2012, 2014, "Passat", "2.0 TDI diesel"),
            ),
            "Cylinder pressure sensor: implausible signal",
            "Volkswagen's definition: the pressure sensor inside the glow plug, which measures pressure in the cylinder, is sending a reading that doesn't make sense. The four codes are for cylinders 1 to 4 in order.",
            M,
            listOf("A loose connector on that cylinder's glow plug, the most likely cause in Volkswagen's bulletin", "A failed glow plug (the pressure sensor is built into it)", "Damaged wiring to the glow plug"),
            listOf("Check-engine light on", "Harder cold starts or a rough idle when cold in some cases", "Often no change in how the car drives"),
            listOf("Check that the glow plug connector is fully pushed home and doesn't pull off easily", "Have a workshop swap that glow plug with the one next to it and see whether the code moves with it", "Replace the glow plug only if the code follows it to the other cylinder"),
            "VW technical bulletin 2037386: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2014/MC-10239837-9999.pdf
        e("P13E0 P13E1 P13E2 P13E3", "Volkswagen", listOf(
                f(2012, 2014, "Beetle", "2.0 diesel"),
                f(2010, 2014, "Golf", "2.0 TDI diesel"),
                f(2009, 2014, "Jetta", "2.0 diesel"),
                f(2012, 2014, "Passat", "2.0 TDI diesel"),
            ),
            "Cylinder pressure sensor: malfunction",
            "Volkswagen's definition: the pressure sensor inside the glow plug, which measures pressure in the cylinder, isn't working properly. The four codes are for cylinders 1 to 4 in order.",
            M,
            listOf("A loose connector on that cylinder's glow plug, the most likely cause in Volkswagen's bulletin", "A failed glow plug (the pressure sensor is built into it)", "Damaged wiring to the glow plug"),
            listOf("Check-engine light on", "Harder cold starts or a rough idle when cold in some cases", "Often no change in how the car drives"),
            listOf("Check that the glow plug connector is fully pushed home and doesn't pull off easily", "Have a workshop swap that glow plug with the one next to it and see whether the code moves with it", "Replace the glow plug only if the code follows it to the other cylinder"),
            "VW technical bulletin 2037386: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/MC-10107504-9430.pdf
        e("P130A", "Volkswagen", listOf(
                f(2008, 2017, "Beetle", "1.6 I4|2.0 I4|2.5 I5|1.2 turbo|1.4 turbo|1.8 TSI|2.0 turbo"),
                f(2008, 2017, "CC", "1.8 turbo|2.0 turbo|3.6 V6"),
                f(2008, 2015, "Eos", "1.4 turbo|2.0 turbo|3.2 VR6|3.6 V6"),
                f(2008, 2017, "Golf", "1.6|2.0|3.2 V6 (R32)|2.0 TSI turbo (GTI/R)|1.4 TSI|1.2 TSI|1.4|2.5 I5|1.0 TSI|1.4 TSI plug-in hybrid (GTE)"),
                f(2008, 2017, "Jetta", "2.0 I4|2.5 I5|2.0 turbo|1.4 turbo|1.8 TSI|1.4 turbo hybrid"),
                f(2008, 2017, "Passat", "2.0 TSI turbo|3.6 V6|2.0|3.2 VR6|1.8 TSI|1.4 TSI|2.5 I5|1.4 TSI plug-in hybrid (GTE)"),
                f(2008, 2017, "Tiguan", "1.4 TSI|2.0 TSI turbo"),
                f(2008, 2017, "Touareg", "4.2 V8|3.6 V6|6.0 W12|3.0 V6 TSI|3.0 V6 hybrid"),
            ),
            "Cylinder switched off after misfires",
            "Volkswagen's definition: the engine computer has switched a cylinder off (\"hidden\" it) because it was misfiring. Volkswagen treats misfire codes with this one as a real fault, not just poor fuel.",
            H,
            listOf("A failed ignition coil or spark plug", "A faulty fuel injector", "Loose or damaged wiring or a poor engine ground", "Poor quality fuel"),
            listOf("A flashing or steady check-engine light", "The engine running roughly or on fewer cylinders", "Reduced power"),
            listOf("Ease off and avoid hard driving while the light is flashing", "Note which cylinder's misfire code (P0301 to P0308) came with it", "Have the coil, plug and injector on that cylinder checked"),
            "VW technical bulletin 01 16 13: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/MC-10107504-9430.pdf
        e("P1250", "Volkswagen", listOf(
                f(2008, 2017, "Beetle", "1.6 I4|2.0 I4|2.5 I5|1.2 turbo|1.4 turbo|1.8 TSI|2.0 turbo"),
                f(2008, 2017, "CC", "1.8 turbo|2.0 turbo|3.6 V6"),
                f(2008, 2015, "Eos", "1.4 turbo|2.0 turbo|3.2 VR6|3.6 V6"),
                f(2008, 2017, "Golf", "1.6|2.0|3.2 V6 (R32)|2.0 TSI turbo (GTI/R)|1.4 TSI|1.2 TSI|1.4|2.5 I5|1.0 TSI|1.4 TSI plug-in hybrid (GTE)"),
                f(2008, 2017, "Jetta", "2.0 I4|2.5 I5|2.0 turbo|1.4 turbo|1.8 TSI|1.4 turbo hybrid"),
                f(2008, 2017, "Passat", "2.0 TSI turbo|3.6 V6|2.0|3.2 VR6|1.8 TSI|1.4 TSI|2.5 I5|1.4 TSI plug-in hybrid (GTE)"),
                f(2008, 2017, "Tiguan", "1.4 TSI|2.0 TSI turbo"),
                f(2008, 2017, "Touareg", "4.2 V8|3.6 V6|6.0 W12|3.0 V6 TSI|3.0 V6 hybrid"),
            ),
            "Fuel level too low",
            "Volkswagen's definition: the engine computer recorded that the fuel level was too low. Volkswagen says misfire codes stored with this one most likely came from running low on fuel, not from a failed part.",
            L,
            listOf("Running the tank very low", "Less often, a fuel gauge sender reading wrongly"),
            listOf("Often nothing once the car has been refuelled", "Stumbling or misfires when the tank was nearly empty"),
            listOf("Fill up and see whether the misfire codes come back", "If they return with plenty of fuel in the tank, have the misfire diagnosed as usual"),
            "VW technical bulletin 01 16 13: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10226218-0001.pdf
        e("P30C0", "Volkswagen", listOf(f(2022, 2022, "Jetta|Taos")),
            "Poor fuel quality",
            "Volkswagen's definition: the engine computer judged the fuel to be of poor quality. Volkswagen covers this code in an engine software update for these cars.",
            L,
            listOf("Poor quality or stale fuel", "Engine software from before Volkswagen's update 24iR"),
            listOf("Uneven pulling power at low revs, about 1,200 to 2,000 rpm", "Check-engine light on", "Often no other symptom"),
            listOf("Fill up with fresh fuel from a busy station", "Ask a Volkswagen dealer whether software update 24iR has been done on your car"),
            "VW software update 24iR: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10209417-0001.pdf
        e("P148D", "Volkswagen", listOf(f(2020, 2021, "Jetta", "1.4 turbo")),
            "EVAP leak detection pump: implausible frequency",
            "Volkswagen's definition: the signal from the pump that tests the fuel vapour system for leaks doesn't make sense. Volkswagen says not to replace parts for this code on these cars until the engine software has been updated.",
            L,
            listOf("Engine software from before Volkswagen's update, the cause named in the bulletin", "Less often, a faulty leak detection pump or its wiring"),
            listOf("Check-engine light on", "Usually no change in how the car drives"),
            listOf("Ask a Volkswagen dealer whether the engine software update in bulletin 01-22-03 has been done", "Check that the fuel cap clicks shut and its seal is intact", "If the code returns after the update, have the leak detection pump and wiring tested"),
            "VW technical bulletin 01-22-03: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10209417-0001.pdf
        e("P30DC", "Volkswagen", listOf(f(2020, 2021, "Jetta", "1.4 turbo")),
            "Fuel tank pressure can't be released for refuelling",
            "Volkswagen's definition: the car couldn't release the pressure in the fuel tank ready for refuelling. Volkswagen says not to replace parts for this code on these cars until the engine software has been updated.",
            M,
            listOf("Engine software from before Volkswagen's update, the cause named in the bulletin", "Less often, a stuck vent valve or a blocked vapour line"),
            listOf("Check-engine light on", "A fuel pump nozzle that keeps clicking off, in some cases"),
            listOf("Ask a Volkswagen dealer whether the engine software update in bulletin 01-22-03 has been done", "If the code returns after the update, have the tank vent valve and vapour lines checked"),
            "VW technical bulletin 01-22-03: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10233278-0001.pdf
        e("P1CC3", "Volkswagen", listOf(f(2023, 2023, "ID.4")),
            "External charger malfunction",
            "Volkswagen's definition: the car detected a fault in the charging station or cable it was plugged into, not in the car itself. Volkswagen's bulletin covers 2023 ID.4s built in Chattanooga, USA, and says that on those cars this code can be stored with no real fault, when it comes with a particular Volkswagen symptom code that a basic reader can't show.",
            L,
            listOf("A faulty or incompatible public charger or wallbox", "A damaged charging cable", "A dropout in the charger's power supply"),
            listOf("Often nothing at all", "A charging session that didn't start or stopped early, in some cases"),
            listOf("Try a different charger or cable and see whether it charges normally", "If charging keeps failing on more than one charger, ask a Volkswagen dealer to read the code's symptom code"),
            "VW technical bulletin 01-23-05: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10233278-0001.pdf
        e("P1DCB", "Volkswagen", listOf(f(2023, 2023, "ID.4")),
            "External charger not ready (charging socket A)",
            "Volkswagen's definition: the charging station plugged into the car's main charging socket didn't signal that it was ready to charge. Volkswagen's bulletin covers 2023 ID.4s built in Chattanooga, USA, and says that on those cars this code can be stored with no real fault, when it comes with a particular Volkswagen symptom code that a basic reader can't show.",
            L,
            listOf("A charger that wasn't started, authorised or switched on", "A faulty charger or cable", "A plug that wasn't pushed fully home"),
            listOf("Often nothing at all", "A charging session that didn't start or stopped early, in some cases"),
            listOf("Try a different charger or cable and see whether it charges normally", "If charging keeps failing on more than one charger, ask a Volkswagen dealer to read the code's symptom code"),
            "VW technical bulletin 01-23-05: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10233278-0001.pdf
        e("P1DCF", "Volkswagen", listOf(f(2023, 2023, "ID.4")),
            "Charging socket B: emergency charging function active",
            "Volkswagen's definition: the car's second charging socket has dropped into its fallback charging mode. Volkswagen's bulletin covers 2023 ID.4s built in Chattanooga, USA, and says that on those cars this code can be stored with no real fault, when it comes with a particular Volkswagen symptom code that a basic reader can't show.",
            L,
            listOf("A temperature reading at the charging socket that the car couldn't trust", "A software quirk on these cars, as described in the bulletin"),
            listOf("Often nothing at all", "Slower charging than usual, in some cases"),
            listOf("Try a different charger or cable and see whether it charges normally", "If charging keeps failing on more than one charger, ask a Volkswagen dealer to read the code's symptom code"),
            "VW technical bulletin 01-23-05: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10233278-0001.pdf
        e("P1DD1 P1DD2", "Volkswagen", listOf(f(2023, 2023, "ID.4")),
            "Charging socket temperature: emergency charging function active",
            "Volkswagen's definition: the car is using its fallback charging mode because of the temperature reading at a charging socket. The first code is for socket A and the second for socket B. Volkswagen's bulletin covers 2023 ID.4s built in Chattanooga, USA, and says that on those cars this code can be stored with no real fault, when it comes with a particular Volkswagen symptom code that a basic reader can't show.",
            L,
            listOf("A hot or dirty charging plug or socket", "A temperature sensor reading at the socket that the car couldn't trust", "A software quirk on these cars, as described in the bulletin"),
            listOf("Often nothing at all", "Slower charging than usual, in some cases"),
            listOf("Check the charging socket and plug for dirt, damage or signs of heat", "Try a different charger or cable", "If charging stays slow on more than one charger, ask a Volkswagen dealer to read the code's symptom code"),
            "VW technical bulletin 01-23-05: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10233278-0001.pdf
        e("P1CF4", "Volkswagen", listOf(f(2023, 2023, "ID.4")),
            "Charging connector lock blocked (socket A)",
            "Volkswagen's definition: the lock that holds the charging plug in the socket couldn't move. Volkswagen's bulletin covers 2023 ID.4s built in Chattanooga, USA, and says that on those cars this code can be stored with no real fault, when it comes with a particular Volkswagen symptom code that a basic reader can't show.",
            L,
            listOf("A plug that wasn't pushed fully home", "Dirt or ice in the charging socket", "A sticking lock actuator"),
            listOf("Often nothing at all", "A charging plug that won't lock in or won't release, in some cases"),
            listOf("Push the plug in firmly and try again", "Check the socket for dirt, ice or damage", "If the plug won't release, use the emergency release described in the owner's manual and have the lock checked"),
            "VW technical bulletin 01-23-05: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10233278-0001.pdf
        e("P31DD", "Volkswagen", listOf(f(2023, 2023, "ID.4")),
            "Charging connector lock: electrical fault (socket A)",
            "Volkswagen's definition: the circuit that drives the lock for the charging plug has an electrical fault. Volkswagen's bulletin covers 2023 ID.4s built in Chattanooga, USA, and says that on those cars this code can be stored with no real fault, when it comes with a particular Volkswagen symptom code that a basic reader can't show.",
            L,
            listOf("A software quirk on these cars, as described in the bulletin", "A faulty lock actuator or its wiring"),
            listOf("Often nothing at all", "A charging plug that won't lock in or won't release, in some cases"),
            listOf("See whether the plug still locks and releases normally", "If it doesn't, ask a Volkswagen dealer to test the lock actuator and read the code's symptom code"),
            "VW technical bulletin 01-23-05: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10233278-0001.pdf
        e("P1CB5", "Volkswagen", listOf(f(2023, 2023, "ID.4")),
            "Plug and Charge: vehicle certificate could not be installed",
            "Volkswagen's definition: the car had a communication error while installing the digital certificate it uses to identify itself to chargers for Plug and Charge. Volkswagen's bulletin covers 2023 ID.4s built in Chattanooga, USA, and says that on those cars this code can be stored with no real fault, when it comes with a particular Volkswagen symptom code that a basic reader can't show.",
            L,
            listOf("A dropped data connection while the certificate was being installed", "A software quirk on these cars, as described in the bulletin"),
            listOf("Often nothing at all", "Plug and Charge not working, so the charger asks for an app or card instead"),
            listOf("Start the charge with the app or a card instead", "If Plug and Charge never works, ask a Volkswagen dealer to check the car's software and certificate"),
            "VW technical bulletin 01-23-05: the maker's own service bulletin, filed with NHTSA")
    }

    private fun subaru(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2019/MC-10163564-0001.pdf
        e("P173B", "Subaru", listOf(f(2019, 2019, "Forester", "2.5")),
            "CVT electric fluid pump performance",
            "Subaru's definition: the electric pump that keeps fluid pressure up in the CVT automatic while the engine is stopped by auto stop-start isn't performing as expected.",
            M,
            listOf("A failing electric fluid pump", "Damaged wiring or a loose connector at the pump", "Low or old CVT fluid"),
            listOf("Auto stop-start not working", "A delay or jolt when moving off after an automatic stop", "A transmission warning light"),
            listOf("Note whether auto stop-start has stopped working", "Have the CVT fluid level checked", "Ask a Subaru dealer to test the pump"),
            "Subaru 16-123-19: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2023/MC-10231308-0001.pdf
        e("P1604", "Subaru", listOf(f(2022, 2022, "WRX", "2.4 turbo")),
            "Startability malfunction",
            "Subaru's definition: the engine took too long to start, or didn't keep running once it fired.",
            M,
            listOf("Running very low on fuel", "Low fuel pressure", "A weak battery", "Engine software that needs updating"),
            listOf("Long cranking before the engine starts", "The engine starting and then stalling"),
            listOf("Check there's enough fuel", "Check the battery's condition and terminals", "Ask a Subaru dealer whether a software update applies"),
            "Subaru 09-95-22R: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2019/MC-10154420-9999.pdf
        e("P1C06", "Subaru", listOf(f(2014, 2016, "Crosstrek", "2.0 hybrid")),
            "12-volt battery relay close circuit performance",
            "Subaru's definition: the relay that connects the 12-volt battery on this hybrid didn't close the way the computer commanded.",
            M,
            listOf("A sticking or failed battery relay", "A weak 12-volt battery", "Damaged wiring or a poor connection"),
            listOf("A hybrid system or battery warning", "Auto stop-start not working", "Trouble starting in some cases"),
            listOf("Have the 12-volt battery tested", "Check the battery terminals are clean and tight", "Ask a Subaru dealer to test the relay"),
            "Subaru 07-147-19: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2019/MC-10154420-9999.pdf
        e("P1C07", "Subaru", listOf(f(2014, 2016, "Crosstrek", "2.0 hybrid")),
            "12-volt battery relay open circuit performance",
            "Subaru's definition: the relay that connects the 12-volt battery on this hybrid didn't open the way the computer commanded.",
            M,
            listOf("A sticking or failed battery relay", "A weak 12-volt battery", "Damaged wiring or a poor connection"),
            listOf("A hybrid system or battery warning", "Auto stop-start not working", "Trouble starting in some cases"),
            listOf("Have the 12-volt battery tested", "Check the battery terminals are clean and tight", "Ask a Subaru dealer to test the relay"),
            "Subaru 07-147-19: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10182223-0001.pdf
        e("B1572", "Subaru", listOf(f(2017, 2019, "Legacy|Outback")),
            "Immobiliser circuit fault (other than the antenna circuit)",
            "Subaru's definition: a fault in the immobiliser's circuit, in a part other than the antenna around the ignition.",
            M,
            listOf("A fault in the immobiliser control unit or its wiring", "A key the car no longer recognises", "Low 12-volt battery voltage"),
            listOf("The engine not starting", "A security warning light"),
            listOf("Try the spare key", "Have the 12-volt battery tested", "Ask a Subaru dealer to test the immobiliser system"),
            "Subaru 07-181-20: the maker's own service bulletin, filed with NHTSA")
    }

    private fun mitsubishi(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2016/MC-10183867-9999.pdf
        e("P1590", "Mitsubishi", listOf(f(2014, 2015, "Mirage", "1.2 I3")),
            "Gearbox-to-engine communication error in torque reduction request",
            "Mitsubishi's definition: the gearbox computer asked the engine computer to cut torque, and that request wasn't received or wasn't valid.",
            M,
            listOf("A tracker or other device left plugged into the diagnostic port, which Mitsubishi says can set this code falsely", "A communication fault between the gearbox and engine computers", "Damaged wiring or a poor connector", "Low battery voltage"),
            listOf("Harsh take-up or jolts from the CVT", "A transmission warning light"),
            listOf("Unplug any tracker, insurance dongle or adapter left in the diagnostic port, then see if the code comes back", "Have the battery tested and check its terminals", "Have the wiring between the engine and gearbox computers checked"),
            "Mitsubishi Tech Talk vol. 219: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/MC-10183867-9999.pdf
        e("P1797", "Mitsubishi", listOf(f(2014, 2015, "Mirage", "1.2 I3")),
            "G sensor and brake pressure sensor malfunction",
            "Mitsubishi's definition: the readings from the G sensor (which senses acceleration and slope) and the brake pressure sensor don't agree, so the gearbox can't trust them.",
            M,
            listOf("A tracker or other device left plugged into the diagnostic port, which Mitsubishi says can set this code falsely", "A faulty G sensor", "A faulty brake pressure sensor", "Damaged wiring to either sensor"),
            listOf("A transmission or stability control warning", "Hill-start assist not working", "Unusual CVT behaviour on hills"),
            listOf("Unplug any tracker, insurance dongle or adapter left in the diagnostic port, then see if the code comes back", "Note any other warning lights that came on at the same time", "Have a Mitsubishi dealer test both sensors"),
            "Mitsubishi Tech Talk vol. 219: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2015/MC-10170074-0001.pdf
        e("P1A15", "Mitsubishi", listOf(f(2012, 2013, "i-MiEV")),
            "High-voltage system error",
            "Mitsubishi's definition: the electric drive system has detected a fault somewhere in its high-voltage circuits.",
            H,
            listOf("A fault in the drive battery or its relays", "A fault in the charger, inverter or a high-voltage cable", "A weak 12-volt battery in some cases"),
            listOf("An EV system warning", "The car not going into READY", "Charging not starting"),
            listOf("Don't touch the orange high-voltage cables or open any high-voltage part yourself", "Note any hybrid system warning on the dash and whether the car still goes into READY", "Have a dealer or hybrid-trained technician test the high-voltage system"),
            "Mitsubishi TSB-15-54-003: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/MC-10183867-9999.pdf
        e("C1219", "Mitsubishi", listOf(f(2013, 2015, "Outlander Sport")),
            "Abnormality in steering wheel sensor signal",
            "Mitsubishi's definition: the signal from the steering wheel angle sensor isn't what the stability control system expects.",
            M,
            listOf("A steering angle sensor that needs calibrating, often after a wheel alignment", "A faulty steering angle sensor", "Damaged wiring"),
            listOf("A stability control or ABS warning light", "Stability control switching itself off"),
            listOf("Note whether it appeared after an alignment or steering work", "Have a Mitsubishi dealer calibrate or test the steering angle sensor"),
            "Mitsubishi Tech Talk vol. 219: the maker's own service bulletin, filed with NHTSA")
    }

    private fun jaguarLandRover(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2015/MC-10105424-9340.pdf
        e("P1315", "Land Rover", listOf(f(2013, 2014, "Freelander", "2.0 turbo"), f(2012, 2014, "Range Rover Evoque", "2.0 turbo")),
            "Persistent misfire",
            "Land Rover's definition: the engine has been misfiring for long enough that the computer has logged it as a persistent fault.",
            H,
            listOf("Worn spark plugs or a failing ignition coil", "A faulty fuel injector", "An air leak in the intake", "Low compression in one cylinder"),
            listOf("Rough running or shaking", "Loss of power", "A flashing check-engine light", "A fuel smell from the exhaust"),
            listOf("Avoid hard driving; a misfire can overheat the catalytic converter", "Look for cylinder misfire codes (P0301 and so on) to see which cylinder it is", "Have the plugs, coils and injectors checked"),
            "JLR LTB00730NAS1: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10219483-0001.pdf
        e("P1338", "Land Rover", listOf(f(2018, 2022, "Defender|Discovery Sport|Range Rover Sport|Range Rover Evoque|Range Rover Velar", "2.0 turbo")),
            "Fuel pump driver module communication: signal frequency incorrect",
            "Jaguar Land Rover's definition: the signal between the engine computer and the module that drives the fuel pump isn't at the frequency expected.",
            M,
            listOf("A failing fuel pump driver module", "Damaged wiring or a corroded connector", "Software that needs updating"),
            listOf("Hard starting", "Hesitation or loss of power", "Stalling in some cases"),
            listOf("Have the wiring and connector at the fuel pump driver module checked", "Ask a dealer whether a software update applies", "Have the module tested if the code returns"),
            "Jaguar Land Rover technical call notes, August 2022: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10219483-0001.pdf
        e("P1338", "Jaguar", listOf(f(2018, 2022, "XF|XE|E-Pace|F-Pace", "2.0 turbo")),
            "Fuel pump driver module communication: signal frequency incorrect",
            "Jaguar Land Rover's definition: the signal between the engine computer and the module that drives the fuel pump isn't at the frequency expected.",
            M,
            listOf("A failing fuel pump driver module", "Damaged wiring or a corroded connector", "Software that needs updating"),
            listOf("Hard starting", "Hesitation or loss of power", "Stalling in some cases"),
            listOf("Have the wiring and connector at the fuel pump driver module checked", "Ask a dealer whether a software update applies", "Have the module tested if the code returns"),
            "Jaguar Land Rover technical call notes, August 2022: the maker's own service bulletin, filed with NHTSA")
    }

    private fun chrysler(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2015/MC-10159927-9999.pdf
        e("P1239", "Chrysler", listOf(f(2011, 2012, "300", "3.6 V6")),
            "Engine oil temperature too low",
            "Chrysler's definition: the engine oil isn't warming up the way the computer expects for how the engine has been running.",
            L,
            listOf("A thermostat stuck open", "A faulty oil temperature sensor", "Engine software that needs updating"),
            listOf("Usually nothing you'd notice", "A heater that's slow to blow warm in some cases"),
            listOf("Watch whether the temperature gauge reaches normal on a drive", "Have the thermostat and oil temperature sensor tested", "Ask a dealer whether a software update applies"),
            "FCA 18-085-15: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2019/MC-10162535-9999.pdf
        e("P1D73", "Chrysler", listOf(f(2017, 2017, "Pacifica", "3.6 V6")),
            "Active grille shutter performance",
            "Chrysler's definition: the shutters behind the grille, which open and close to manage cooling and drag, aren't moving the way the computer commands.",
            L,
            listOf("Shutters jammed by debris, ice or accident damage", "A failed shutter motor", "Damaged wiring or a loose connector"),
            listOf("Usually nothing you'd notice", "Slightly higher fuel use", "The engine running warmer than usual in rare cases"),
            listOf("Look through the grille for debris, ice or bent shutters", "Check the shutter connector behind the bumper", "Have the shutter motor tested"),
            "FCA 18-002-19: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10229791-9999.pdf
        e("P1D73", "Jeep", listOf(f(2019, 2019, "Cherokee", "2.0 turbo")),
            "Active grille shutter performance",
            "Jeep's definition: the shutters behind the grille, which open and close to manage cooling and drag, aren't moving the way the computer commands.",
            L,
            listOf("Shutters jammed by debris, ice or accident damage", "A failed shutter motor", "Damaged wiring or a loose connector"),
            listOf("Usually nothing you'd notice", "Slightly higher fuel use", "The engine running warmer than usual in rare cases"),
            listOf("Look through the grille for debris, ice or bent shutters", "Check the shutter connector behind the bumper", "Have the shutter motor tested"),
            "FCA 18-161-22: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10229791-9999.pdf
        e("P152F", "Jeep", listOf(f(2019, 2019, "Cherokee", "2.0 turbo")),
            "Engine hood switch 1 and 2 correlation",
            "Jeep's definition: the two bonnet (hood) switches disagree about whether the bonnet is open or shut.",
            L,
            listOf("A bonnet that isn't fully latched", "A faulty or misadjusted bonnet switch", "Damaged wiring at the latch"),
            listOf("A bonnet-open warning that won't go away", "Auto stop-start or remote start not working"),
            listOf("Open the bonnet and close it firmly", "Check the bonnet latch and switch for dirt or damage", "Have the switch tested if the warning stays"),
            "FCA 18-161-22: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2017/MC-10138918-9999.pdf
        e("P1B13", "Chrysler", listOf(f(2017, 2017, "Pacifica", "3.6 V6")),
            "Park-by-wire: unintended out-of-park position",
            "Chrysler's definition: the gearbox computer found the transmission out of Park when it hadn't been asked to leave Park.",
            H,
            listOf("A fault in the park-by-wire actuator or its sensor", "A gearbox control fault", "Software that needs updating"),
            listOf("A transmission warning or 'service shifter' message", "The gear display not matching the selector"),
            listOf("Always apply the parking brake before getting out", "Ask a Chrysler dealer to test the park system and check for updates before relying on Park"),
            "FCA 21-027-17: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2017/MC-10138918-9999.pdf
        e("P1DCD", "Chrysler", listOf(f(2017, 2017, "Pacifica", "3.6 V6")),
            "Gearbox monitoring processor: multiple clutches locked up",
            "Chrysler's definition: the gearbox computer's safety monitor detected more clutches applied at once than should be, which could lock the gearbox.",
            H,
            listOf("A sticking valve or solenoid in the gearbox", "An internal gearbox fault", "Gearbox software that needs updating"),
            listOf("A harsh jolt or the car slowing suddenly", "The gearbox dropping into limp-home mode", "A transmission warning"),
            listOf("Drive gently and avoid motorways until it's checked", "Ask a Chrysler dealer to test the gearbox and check for software updates"),
            "FCA 21-027-17: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2017/MC-10138918-9999.pdf
        e("P1CC9", "Chrysler", listOf(f(2017, 2017, "Pacifica", "3.6 V6")),
            "Unable to engage gear",
            "Chrysler's definition: the 9-speed automatic couldn't engage the gear it was trying to select.",
            H,
            listOf("A fault with a dog clutch or its actuator inside the gearbox", "Low gearbox fluid", "Gearbox software that needs updating"),
            listOf("The car dropping into neutral or not pulling away", "Harsh or delayed gear changes", "A transmission warning"),
            listOf("Stop somewhere safe if drive is lost, then switch off and restart", "Ask a Chrysler dealer to test the gearbox and check for software updates"),
            "FCA 21-027-17: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2017/MC-10138918-9999.pdf
        e("P1DDE", "Chrysler", listOf(f(2017, 2017, "Pacifica", "3.6 V6")),
            "Ignition run/start input 1 and 2 correlation",
            "Chrysler's definition: the two ignition-on signals the gearbox computer receives don't agree with each other.",
            M,
            listOf("Damaged wiring or a poor connector", "A fault in a fuse or relay box", "Low battery voltage"),
            listOf("A transmission warning", "Odd gear change behaviour", "Trouble starting in some cases"),
            listOf("Have the battery tested and check its terminals", "Have the ignition feed wiring to the gearbox computer checked"),
            "FCA 21-027-17: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2019/MC-10169224-9999.pdf
        e("P1DD2", "Jeep", listOf(f(2018, 2018, "Compass", "2.4 I4")),
            "Torque request signal from gearbox denied",
            "Jeep's definition: the gearbox computer asked the engine to adjust its torque for a gear change, and the engine computer refused the request.",
            M,
            listOf("An engine fault that stops it meeting the request, often with its own code", "A communication fault between the two computers", "Software that needs updating"),
            listOf("Harsh or delayed gear changes", "A transmission warning"),
            listOf("Look for other engine codes stored at the same time and deal with those first", "Ask a Jeep dealer whether a software update applies"),
            "FCA 18-091-19: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10229791-9999.pdf
        e("C1252", "Jeep", listOf(f(2019, 2019, "Cherokee", "2.0 turbo")),
            "Vacuum pump control circuit performance or incorrect operation",
            "Jeep's definition: the electric vacuum pump that gives the brakes their power assistance isn't working as commanded.",
            H,
            listOf("A failing electric vacuum pump", "A blown fuse or faulty relay", "Damaged wiring or a vacuum hose leak"),
            listOf("A brake warning light", "A brake pedal that feels harder than usual"),
            listOf("Test the brake pedal feel before driving; a hard pedal means more effort to stop", "Check the vacuum pump fuse and relay", "Have the pump and its vacuum hoses tested"),
            "FCA 18-161-22: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2019/MC-10162535-9999.pdf
        e("C2222", "Chrysler", listOf(f(2017, 2017, "Pacifica", "3.6 V6")),
            "Improper power-down: event information",
            "Chrysler's definition: a control module recorded that it lost power or shut down in a way it didn't expect.",
            L,
            listOf("A battery that went flat or was disconnected", "A loose battery terminal or poor earth", "A module wiring fault if it keeps coming back"),
            listOf("Usually nothing you'd notice", "Often stored after a flat or disconnected battery"),
            listOf("Have the 12-volt battery tested and check its terminals", "Note whether it appeared after battery work or a jump start", "Have the module's power and earth wiring checked if it returns"),
            "FCA 18-002-19: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2019/MC-10162535-9999.pdf
        e("C121C", "Chrysler", listOf(f(2017, 2017, "Pacifica", "3.6 V6")),
            "Torque request signal denied",
            "Chrysler's definition: the brake or stability control module asked the engine to change its torque and the request was refused.",
            M,
            listOf("An engine or gearbox fault that stops it meeting the request, often with its own code", "A communication fault between modules", "Software that needs updating"),
            listOf("A traction or stability control warning", "Cruise control switching off"),
            listOf("Have the 12-volt battery tested and check its terminals; low voltage causes many communication codes", "Look for other codes stored at the same time, which usually point to the real fault", "Have a dealer check the wiring between the modules and whether a software update applies"),
            "FCA 18-002-19: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2019/MC-10162535-9999.pdf
        e("U145D", "Chrysler", listOf(f(2017, 2017, "Pacifica", "3.6 V6")),
            "Implausible data received from the gearbox computer",
            "Chrysler's definition: another module received data from the gearbox computer that doesn't make sense. The module that sent the signal usually stores its own code too.",
            M,
            listOf("A fault in the module or sensor that sends the signal", "A communication fault between modules", "Low battery voltage", "Software that needs updating"),
            listOf("A warning light for the gearbox, stability control or engine", "Odd gear change behaviour in some cases"),
            listOf("Have the 12-volt battery tested and check its terminals; low voltage causes many communication codes", "Look for other codes stored at the same time, which usually point to the real fault", "Have a dealer check the wiring between the modules and whether a software update applies"),
            "FCA 18-002-19: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2019/MC-10162535-9999.pdf
        e("U1465", "Chrysler", listOf(f(2017, 2017, "Pacifica", "3.6 V6")),
            "Implausible driver shift request signal received",
            "Chrysler's definition: a module received a gear selector request that doesn't make sense. The module that sent the signal usually stores its own code too.",
            M,
            listOf("A fault in the module or sensor that sends the signal", "A communication fault between modules", "Low battery voltage", "Software that needs updating"),
            listOf("A warning light for the gearbox, stability control or engine", "Odd gear change behaviour in some cases"),
            listOf("Have the 12-volt battery tested and check its terminals; low voltage causes many communication codes", "Look for other codes stored at the same time, which usually point to the real fault", "Have a dealer check the wiring between the modules and whether a software update applies"),
            "FCA 18-002-19: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2019/MC-10162535-9999.pdf
        e("U1400", "Chrysler", listOf(f(2017, 2017, "Pacifica", "3.6 V6")),
            "Implausible throttle position signal received",
            "Chrysler's definition: a module received a throttle position reading that doesn't make sense. The module that sent the signal usually stores its own code too.",
            M,
            listOf("A fault in the module or sensor that sends the signal", "A communication fault between modules", "Low battery voltage", "Software that needs updating"),
            listOf("A warning light for the gearbox, stability control or engine", "Odd gear change behaviour in some cases"),
            listOf("Have the 12-volt battery tested and check its terminals; low voltage causes many communication codes", "Look for other codes stored at the same time, which usually point to the real fault", "Have a dealer check the wiring between the modules and whether a software update applies"),
            "FCA 18-002-19: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2019/MC-10169224-9999.pdf
        e("U1428", "Jeep", listOf(f(2018, 2018, "Compass", "2.4 I4")),
            "Inaccurate engine torque received",
            "Jeep's definition: a module received an engine torque figure that doesn't match what it expects. The module that sent the signal usually stores its own code too.",
            M,
            listOf("A fault in the module or sensor that sends the signal", "A communication fault between modules", "Low battery voltage", "Software that needs updating"),
            listOf("A warning light for the gearbox, stability control or engine", "Odd gear change behaviour in some cases"),
            listOf("Have the 12-volt battery tested and check its terminals; low voltage causes many communication codes", "Look for other codes stored at the same time, which usually point to the real fault", "Have a dealer check the wiring between the modules and whether a software update applies"),
            "FCA 18-091-19: the maker's own service bulletin, filed with NHTSA")
    }

    private fun dodge(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2015/MC-10159927-9999.pdf
        e("P1239", "Dodge", listOf(
                f(2011, 2011, "Avenger", "3.6 V6"),
                f(2011, 2012, "Challenger", "3.6 V6"),
                f(2011, 2012, "Charger", "3.6 V6"),
                f(2011, 2011, "Grand Caravan", "3.6 V6"),
                f(2011, 2011, "Journey", "3.6 V6"),
            ),
            "Engine oil temperature too low",
            "Chrysler's definition: the engine oil isn't warming up the way the computer expects for how the engine has been running.",
            L,
            listOf("A thermostat stuck open", "A faulty oil temperature sensor", "Engine software that needs updating"),
            listOf("Usually nothing you'd notice", "A heater that's slow to blow warm in some cases"),
            listOf("Watch whether the temperature gauge reaches normal on a drive", "Have the thermostat and oil temperature sensor tested", "Ask a dealer whether a software update applies"),
            "FCA 18-085-15: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10076529-2280.pdf
        e("P1610", "Dodge", listOf(f(2015, 2015, "Challenger|Charger", "6.2 V8 supercharged")),
            "Supercharger bypass valve position sensor 1 circuit low",
            "Dodge's definition: the first position sensor on the supercharger bypass valve is reading lower than it can in normal use. The bypass valve controls how much boost the supercharger makes.",
            M,
            listOf("A faulty supercharger bypass valve actuator", "Damaged wiring or a loose connector at the actuator", "A sticking bypass valve"),
            listOf("Reduced power", "Check-engine light on", "Less supercharger boost than usual"),
            listOf("Check the connector and wiring at the bypass valve actuator on the supercharger", "Have the actuator tested with a scan tool", "Replace the actuator if the wiring is good"),
            "FCA 18-047-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10076529-2280.pdf
        e("P1611", "Dodge", listOf(f(2015, 2015, "Challenger|Charger", "6.2 V8 supercharged")),
            "Supercharger bypass valve position sensor 1 circuit high",
            "Dodge's definition: the first position sensor on the supercharger bypass valve is reading higher than it can in normal use. The bypass valve controls how much boost the supercharger makes.",
            M,
            listOf("A faulty supercharger bypass valve actuator", "Damaged wiring or a loose connector at the actuator", "A sticking bypass valve"),
            listOf("Reduced power", "Check-engine light on", "Less supercharger boost than usual"),
            listOf("Check the connector and wiring at the bypass valve actuator on the supercharger", "Have the actuator tested with a scan tool", "Replace the actuator if the wiring is good"),
            "FCA 18-047-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10076529-2280.pdf
        e("P1616", "Dodge", listOf(f(2015, 2015, "Challenger|Charger", "6.2 V8 supercharged")),
            "Supercharger bypass valve position sensor 2 circuit low",
            "Dodge's definition: the second position sensor on the supercharger bypass valve is reading lower than it can in normal use. The bypass valve controls how much boost the supercharger makes.",
            M,
            listOf("A faulty supercharger bypass valve actuator", "Damaged wiring or a loose connector at the actuator", "A sticking bypass valve"),
            listOf("Reduced power", "Check-engine light on", "Less supercharger boost than usual"),
            listOf("Check the connector and wiring at the bypass valve actuator on the supercharger", "Have the actuator tested with a scan tool", "Replace the actuator if the wiring is good"),
            "FCA 18-047-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10076529-2280.pdf
        e("P1617", "Dodge", listOf(f(2015, 2015, "Challenger|Charger", "6.2 V8 supercharged")),
            "Supercharger bypass valve position sensor 2 circuit high",
            "Dodge's definition: the second position sensor on the supercharger bypass valve is reading higher than it can in normal use. The bypass valve controls how much boost the supercharger makes.",
            M,
            listOf("A faulty supercharger bypass valve actuator", "Damaged wiring or a loose connector at the actuator", "A sticking bypass valve"),
            listOf("Reduced power", "Check-engine light on", "Less supercharger boost than usual"),
            listOf("Check the connector and wiring at the bypass valve actuator on the supercharger", "Have the actuator tested with a scan tool", "Replace the actuator if the wiring is good"),
            "FCA 18-047-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10076529-2280.pdf
        e("P1627", "Dodge", listOf(f(2015, 2015, "Challenger|Charger", "6.2 V8 supercharged")),
            "Supercharger bypass valve position sensor 1 and 2 correlation",
            "Dodge's definition: the two position sensors on the supercharger bypass valve disagree about where the valve is. The bypass valve controls how much boost the supercharger makes.",
            M,
            listOf("A faulty supercharger bypass valve actuator", "Damaged wiring or a loose connector at the actuator", "A sticking bypass valve"),
            listOf("Reduced power", "Check-engine light on", "Less supercharger boost than usual"),
            listOf("Check the connector and wiring at the bypass valve actuator on the supercharger", "Have the actuator tested with a scan tool", "Replace the actuator if the wiring is good"),
            "FCA 18-047-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10076529-2280.pdf
        e("P1620", "Dodge", listOf(f(2015, 2015, "Challenger|Charger", "6.2 V8 supercharged")),
            "Supercharger bypass valve control open circuit",
            "Dodge's definition: the circuit that drives the supercharger bypass valve is open (disconnected). The bypass valve controls how much boost the supercharger makes.",
            M,
            listOf("A faulty supercharger bypass valve actuator", "Damaged wiring or a loose connector at the actuator", "A sticking bypass valve"),
            listOf("Reduced power", "Check-engine light on", "Less supercharger boost than usual"),
            listOf("Check the connector and wiring at the bypass valve actuator on the supercharger", "Have the actuator tested with a scan tool", "Replace the actuator if the wiring is good"),
            "FCA 18-047-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10076529-2280.pdf
        e("P162C", "Dodge", listOf(f(2015, 2015, "Challenger|Charger", "6.2 V8 supercharged")),
            "Supercharger bypass valve control circuit",
            "Dodge's definition: there's an electrical fault in the circuit that drives the supercharger bypass valve. The bypass valve controls how much boost the supercharger makes.",
            M,
            listOf("A faulty supercharger bypass valve actuator", "Damaged wiring or a loose connector at the actuator", "A sticking bypass valve"),
            listOf("Reduced power", "Check-engine light on", "Less supercharger boost than usual"),
            listOf("Check the connector and wiring at the bypass valve actuator on the supercharger", "Have the actuator tested with a scan tool", "Replace the actuator if the wiring is good"),
            "FCA 18-047-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10076529-2280.pdf
        e("P162A", "Dodge", listOf(f(2015, 2015, "Challenger|Charger", "6.2 V8 supercharged")),
            "Supercharger bypass valve actuator: maximum drive exceeded",
            "Dodge's definition: the computer had to drive the supercharger bypass valve actuator at full effort for too long, which points to a valve that's hard to move. The bypass valve controls how much boost the supercharger makes.",
            M,
            listOf("A faulty supercharger bypass valve actuator", "Damaged wiring or a loose connector at the actuator", "A sticking bypass valve"),
            listOf("Reduced power", "Check-engine light on", "Less supercharger boost than usual"),
            listOf("Check the connector and wiring at the bypass valve actuator on the supercharger", "Have the actuator tested with a scan tool", "Replace the actuator if the wiring is good"),
            "FCA 18-047-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10076529-2280.pdf
        e("P1613", "Dodge", listOf(f(2015, 2015, "Challenger|Charger", "6.2 V8 supercharged")),
            "Boost pressure sensor and air flow sensor correlation",
            "Dodge's definition: the boost pressure reading and the air flow reading don't agree with each other.",
            M,
            listOf("An air leak in the intake or supercharger pipework", "A dirty or failing air flow sensor", "A faulty boost pressure sensor"),
            listOf("Reduced power", "Hesitation", "Higher fuel use"),
            listOf("Check the intake ducts and clamps for leaks", "Check the air filter", "Have both sensors tested"),
            "FCA 18-047-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10076529-2280.pdf
        e("P1217", "Dodge", listOf(f(2015, 2015, "Challenger|Charger", "6.2 V8 supercharged")),
            "Active exhaust valve 1 performance",
            "Dodge's definition: active exhaust valve 1, a flap in the exhaust that changes the sound and flow, isn't moving the way the computer commands.",
            L,
            listOf("A flap seized by heat or corrosion", "A failed valve actuator", "Damaged wiring or a loose connector"),
            listOf("A change in exhaust sound", "A rattle from the exhaust in some cases"),
            listOf("Look at the valve's connector and wiring under the car", "Have the actuator tested and the flap checked for free movement"),
            "FCA 18-047-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/SB-10076529-2280.pdf
        e("P121B", "Dodge", listOf(f(2015, 2015, "Challenger|Charger", "6.2 V8 supercharged")),
            "Active exhaust valve 2 performance",
            "Dodge's definition: active exhaust valve 2, a flap in the exhaust that changes the sound and flow, isn't moving the way the computer commands.",
            L,
            listOf("A flap seized by heat or corrosion", "A failed valve actuator", "Damaged wiring or a loose connector"),
            listOf("A change in exhaust sound", "A rattle from the exhaust in some cases"),
            listOf("Look at the valve's connector and wiring under the car", "Have the actuator tested and the flap checked for free movement"),
            "FCA 18-047-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2017/MC-10138935-9999.pdf
        e("P3032", "Dodge", listOf(f(2016, 2016, "Challenger|Charger", "6.2 V8 supercharged")),
            "Barometric pressure sensor performance plausibility",
            "Dodge's definition: the reading from the barometric (outside air) pressure sensor doesn't make sense against the engine's other pressure readings.",
            M,
            listOf("A faulty barometric pressure sensor", "A fault in another intake pressure sensor", "Damaged wiring"),
            listOf("Check-engine light on", "Slightly uneven power or fuel use"),
            listOf("Have the pressure sensors compared with the engine off; they should all read the same", "Replace the sensor that disagrees"),
            "FCA 18-062-17: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2017/MC-10138935-9999.pdf
        e("U1424", "Dodge", listOf(f(2016, 2016, "Challenger|Charger", "6.2 V8 supercharged")),
            "Implausible engine torque signal received",
            "Dodge's definition: a module received an engine torque signal that doesn't make sense. The module that sent the signal usually stores its own code too.",
            M,
            listOf("A fault in the module or sensor that sends the signal", "A communication fault between modules", "Low battery voltage", "Software that needs updating"),
            listOf("A warning light for the gearbox, stability control or engine", "Odd gear change behaviour in some cases"),
            listOf("Have the 12-volt battery tested and check its terminals; low voltage causes many communication codes", "Look for other codes stored at the same time, which usually point to the real fault", "Have a dealer check the wiring between the modules and whether a software update applies"),
            "FCA 18-062-17: the maker's own service bulletin, filed with NHTSA")
    }

    private fun ram(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10190734-9999.pdf
        e("P1B14", "Ram", listOf(f(2020, 2021, "1500", "6.2 supercharged V8 (TRX)")),
            "Park-by-wire: unintended park position",
            "Ram's definition: the gearbox computer found the transmission in Park when Park hadn't been requested.",
            H,
            listOf("A fault in the park-by-wire actuator or its sensor", "A gearbox control fault", "Software that needs updating"),
            listOf("A transmission warning or 'service shifter' message", "The gear display not matching the selector"),
            listOf("Apply the parking brake whenever you stop", "Ask a Ram dealer to test the park system and check for updates"),
            "FCA 09-003-21: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2013/SB-10063262-0335.pdf
        e("P1775", "Ram", listOf(f(2013, 2013, "2500", "6.7 diesel")),
            "Solenoid switch valve latched in TCC position",
            "Ram's definition: inside the automatic gearbox, the solenoid switch valve is stuck in its torque converter clutch position.",
            M,
            listOf("A sticking valve in the gearbox valve body", "Dirty or low gearbox fluid", "A faulty solenoid pack"),
            listOf("Harsh or missing gear changes", "The gearbox staying in one gear (limp-home mode)", "A transmission warning"),
            listOf("Have the gearbox fluid level and condition checked", "Ask a transmission specialist to test the valve body and solenoids"),
            "FCA 18-018-13: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2013/SB-10063262-0335.pdf
        e("P1776", "Ram", listOf(f(2013, 2013, "2500", "6.7 diesel")),
            "Solenoid switch valve latched in LR position",
            "Ram's definition: inside the automatic gearbox, the solenoid switch valve is stuck in its low/reverse position.",
            M,
            listOf("A sticking valve in the gearbox valve body", "Dirty or low gearbox fluid", "A faulty solenoid pack"),
            listOf("Harsh or missing gear changes", "The gearbox staying in one gear (limp-home mode)", "A transmission warning"),
            listOf("Have the gearbox fluid level and condition checked", "Ask a transmission specialist to test the valve body and solenoids"),
            "FCA 18-018-13: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2013/SB-10063262-0335.pdf
        e("P1C70", "Ram", listOf(f(2013, 2013, "2500", "6.7 diesel")),
            "Exhaust fluid (SCR) error detected: engine disabled",
            "Ram's definition: a fault in the diesel exhaust fluid (AdBlue) system went unfixed through its warning countdown, so the engine won't restart or is held to very low power.",
            H,
            listOf("An empty or contaminated exhaust fluid tank", "A failed fluid pump, injector or heater", "A faulty NOx sensor", "An earlier emissions fault left unrepaired"),
            listOf("A countdown warning before this point", "The engine refusing to restart, or crawling speed only"),
            listOf("Check the exhaust fluid level and top up with the correct fluid", "Look for other emissions codes, which point to the real fault", "Have a diesel specialist or dealer repair the fault so the limit can be lifted"),
            "FCA 18-018-13: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10228032-9999.pdf
        e("P1C55", "Ram", listOf(f(2015, 2015, "2500", "6.7 diesel")),
            "NOx sensor intermittent (bank 1, sensor 1)",
            "Ram's definition: the signal from the NOx sensor ahead of the exhaust treatment catalyst keeps dropping out.",
            M,
            listOf("A failing NOx sensor", "Damaged wiring or a loose connector on the exhaust", "Water in the sensor or its module"),
            listOf("Check-engine light on", "An exhaust fluid system warning in some cases"),
            listOf("Check the sensor wiring along the exhaust for chafing or heat damage", "Have the sensor tested and replaced if it's faulty"),
            "FCA 18-076-22: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10228032-9999.pdf
        e("P1451", "Ram", listOf(f(2013, 2013, "2500", "6.7 diesel"), f(2015, 2015, "2500", "6.7 diesel")),
            "Diesel particulate filter system performance",
            "Ram's definition: the diesel particulate filter isn't cleaning the exhaust as well as the computer expects.",
            M,
            listOf("A filter loaded with soot or ash", "A cracked or damaged filter", "A faulty exhaust pressure sensor or blocked sensor pipe", "Lots of short trips that never let the filter clean itself"),
            listOf("Check-engine light on", "More frequent filter regenerations", "Reduced power if the filter is blocked"),
            listOf("Take a longer drive at steady speed to let the filter regenerate", "Have the exhaust pressure sensor and its pipes checked", "Have the filter inspected if the code returns"),
            "FCA 18-076-22 and 18-091-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10190734-9999.pdf
        e("P1D90", "Ram", listOf(f(2020, 2021, "1500", "6.2 supercharged V8 (TRX)")),
            "Incorrect gear ratio: clutch 2 defective",
            "Ram's definition: the 8-speed automatic isn't giving the gear ratio it should, and the gearbox computer has traced the slip to clutch 2.",
            H,
            listOf("A worn or damaged clutch pack inside the gearbox", "Low or burnt gearbox fluid", "A valve body or solenoid fault"),
            listOf("Harsh, slipping or missing gear changes", "The gearbox dropping into limp-home mode", "A transmission warning"),
            listOf("Drive gently and avoid towing until it's checked", "Have the gearbox fluid level and condition checked", "Ask a Ram dealer or transmission specialist to test the gearbox"),
            "FCA 09-003-21: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10190734-9999.pdf
        e("P1D91", "Ram", listOf(f(2020, 2021, "1500", "6.2 supercharged V8 (TRX)")),
            "Incorrect gear ratio: clutch 3 defective",
            "Ram's definition: the 8-speed automatic isn't giving the gear ratio it should, and the gearbox computer has traced the slip to clutch 3.",
            H,
            listOf("A worn or damaged clutch pack inside the gearbox", "Low or burnt gearbox fluid", "A valve body or solenoid fault"),
            listOf("Harsh, slipping or missing gear changes", "The gearbox dropping into limp-home mode", "A transmission warning"),
            listOf("Drive gently and avoid towing until it's checked", "Have the gearbox fluid level and condition checked", "Ask a Ram dealer or transmission specialist to test the gearbox"),
            "FCA 09-003-21: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10190734-9999.pdf
        e("P1D92", "Ram", listOf(f(2020, 2021, "1500", "6.2 supercharged V8 (TRX)")),
            "Incorrect gear ratio: clutch 4 defective",
            "Ram's definition: the 8-speed automatic isn't giving the gear ratio it should, and the gearbox computer has traced the slip to clutch 4.",
            H,
            listOf("A worn or damaged clutch pack inside the gearbox", "Low or burnt gearbox fluid", "A valve body or solenoid fault"),
            listOf("Harsh, slipping or missing gear changes", "The gearbox dropping into limp-home mode", "A transmission warning"),
            listOf("Drive gently and avoid towing until it's checked", "Have the gearbox fluid level and condition checked", "Ask a Ram dealer or transmission specialist to test the gearbox"),
            "FCA 09-003-21: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10190734-9999.pdf
        e("P1D93", "Ram", listOf(f(2020, 2021, "1500", "6.2 supercharged V8 (TRX)")),
            "Incorrect gear ratio: clutch 5 defective",
            "Ram's definition: the 8-speed automatic isn't giving the gear ratio it should, and the gearbox computer has traced the slip to clutch 5.",
            H,
            listOf("A worn or damaged clutch pack inside the gearbox", "Low or burnt gearbox fluid", "A valve body or solenoid fault"),
            listOf("Harsh, slipping or missing gear changes", "The gearbox dropping into limp-home mode", "A transmission warning"),
            listOf("Drive gently and avoid towing until it's checked", "Have the gearbox fluid level and condition checked", "Ask a Ram dealer or transmission specialist to test the gearbox"),
            "FCA 09-003-21: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10190734-9999.pdf
        e("P1D99", "Ram", listOf(f(2020, 2021, "1500", "6.2 supercharged V8 (TRX)")),
            "Incorrect gear ratio: clutch B or E defective",
            "Ram's definition: the 8-speed automatic isn't giving the gear ratio it should, and the gearbox computer has traced the slip to clutch B or clutch E.",
            H,
            listOf("A worn or damaged clutch pack inside the gearbox", "Low or burnt gearbox fluid", "A valve body or solenoid fault"),
            listOf("Harsh, slipping or missing gear changes", "The gearbox dropping into limp-home mode", "A transmission warning"),
            listOf("Drive gently and avoid towing until it's checked", "Have the gearbox fluid level and condition checked", "Ask a Ram dealer or transmission specialist to test the gearbox"),
            "FCA 09-003-21: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10190734-9999.pdf
        e("P1D9A", "Ram", listOf(f(2020, 2021, "1500", "6.2 supercharged V8 (TRX)")),
            "Incorrect gear ratio: clutch C or D defective",
            "Ram's definition: the 8-speed automatic isn't giving the gear ratio it should, and the gearbox computer has traced the slip to clutch C or clutch D.",
            H,
            listOf("A worn or damaged clutch pack inside the gearbox", "Low or burnt gearbox fluid", "A valve body or solenoid fault"),
            listOf("Harsh, slipping or missing gear changes", "The gearbox dropping into limp-home mode", "A transmission warning"),
            listOf("Drive gently and avoid towing until it's checked", "Have the gearbox fluid level and condition checked", "Ask a Ram dealer or transmission specialist to test the gearbox"),
            "FCA 09-003-21: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2021/MC-10190734-9999.pdf
        e("P1D95", "Ram", listOf(f(2020, 2021, "1500", "6.2 supercharged V8 (TRX)")),
            "Gearbox clutch failure, undetermined",
            "Ram's definition: the 8-speed automatic has a clutch that isn't holding, but the gearbox computer couldn't tell which one.",
            H,
            listOf("A worn or damaged clutch pack inside the gearbox", "Low or burnt gearbox fluid", "A valve body or solenoid fault"),
            listOf("Harsh, slipping or missing gear changes", "The gearbox dropping into limp-home mode", "A transmission warning"),
            listOf("Drive gently and avoid towing until it's checked", "Have the gearbox fluid level and condition checked", "Ask a Ram dealer or transmission specialist to test the gearbox"),
            "FCA 09-003-21: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10228032-9999.pdf
        e("U1421", "Ram", listOf(f(2013, 2013, "2500", "6.7 diesel"), f(2015, 2015, "2500", "6.7 diesel")),
            "Implausible ignition key-off time received",
            "Ram's definition: a module received a figure for how long the ignition was off that doesn't make sense. The module that sent the signal usually stores its own code too.",
            L,
            listOf("A fault in the module or sensor that sends the signal", "A communication fault between modules", "Low battery voltage", "Software that needs updating"),
            listOf("Usually nothing you'd notice"),
            listOf("Have the 12-volt battery tested and check its terminals; low voltage causes many communication codes", "Look for other codes stored at the same time, which usually point to the real fault", "Have a dealer check the wiring between the modules and whether a software update applies"),
            "FCA 18-076-22 and 18-091-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2022/MC-10228032-9999.pdf
        e("U110E", "Ram", listOf(f(2013, 2013, "2500", "6.7 diesel"), f(2015, 2015, "2500", "6.7 diesel")),
            "Lost ambient temperature message",
            "Ram's definition: a module stopped receiving the outside air temperature reading over the car's network.",
            L,
            listOf("A faulty ambient temperature sensor or its wiring", "A communication fault between modules"),
            listOf("A blank or wrong outside temperature display", "Climate control behaving oddly"),
            listOf("Have the 12-volt battery tested and check its terminals; low voltage causes many communication codes", "Look for other codes stored at the same time, which usually point to the real fault", "Have a dealer check the wiring between the modules and whether a software update applies"),
            "FCA 18-076-22 and 18-091-16: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2016/MC-10136532-9999.pdf
        e("U1A24", "Ram", listOf(f(2013, 2013, "2500", "6.7 diesel")),
            "Lost communication with ammonia sensor",
            "Ram's definition: the engine computer has lost contact with the ammonia sensor in the diesel exhaust treatment system.",
            M,
            listOf("A failed ammonia sensor or its module", "Damaged wiring or a corroded connector on the exhaust", "A blown fuse"),
            listOf("Check-engine light on", "An exhaust fluid system warning in some cases"),
            listOf("Check the sensor wiring along the exhaust for chafing or heat damage", "Check the fuses for the exhaust treatment system", "Have the sensor tested and replaced if it's faulty"),
            "FCA 18-091-16: the maker's own service bulletin, filed with NHTSA")
    }

    private fun porsche(sink: Sink) = with(sink) {
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P1731 P1732 P1733 P1734", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Shift rod distance sensor reading outside valid range",
            "Porsche's definition: a sensor that measures how far a gear-shift rod has moved is giving a reading outside its valid range.",
            M,
            listOf("A faulty sensor inside the PDK gearbox", "Damaged wiring or a poor connector", "A PDK control unit fault"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P1743", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Input shaft speed sensor plausibility (clutch 1)",
            "Porsche's definition: the speed reading for the gearbox input shaft on clutch 1 doesn't make sense against the other speed signals.",
            M,
            listOf("A faulty sensor inside the PDK gearbox", "Damaged wiring or a poor connector", "A PDK control unit fault"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P1744", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Input shaft speed sensor plausibility (clutch 2)",
            "Porsche's definition: the speed reading for the gearbox input shaft on clutch 2 doesn't make sense against the other speed signals.",
            M,
            listOf("A faulty sensor inside the PDK gearbox", "Damaged wiring or a poor connector", "A PDK control unit fault"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P1745", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Input shaft overspeed",
            "Porsche's definition: a gearbox input shaft has spun faster than its limit.",
            H,
            listOf("A gear change into too low a gear", "A clutch or gear selection fault", "A faulty speed sensor"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P1749", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "PDK control unit variant coding invalid",
            "Porsche's definition: the PDK control unit isn't coded correctly for this car.",
            M,
            listOf("A PDK control unit that was replaced or reprogrammed without being set up", "Corrupt or mismatched gearbox software", "An internal fault in the PDK control unit"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note whether the code appeared after gearbox or software work", "Ask a Porsche dealer to check the control unit's coding and software"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P174B", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "PDK control unit and transmission mismatch",
            "Porsche's definition: the PDK control unit and the gearbox it's fitted to don't belong together.",
            M,
            listOf("A PDK control unit that was replaced or reprogrammed without being set up", "Corrupt or mismatched gearbox software", "An internal fault in the PDK control unit"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note whether the code appeared after gearbox or software work", "Ask a Porsche dealer to check the control unit's coding and software"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P174C", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Gearbox data file doesn't match the software version",
            "Porsche's definition: the data file loaded into the PDK control unit doesn't match its software version.",
            M,
            listOf("A PDK control unit that was replaced or reprogrammed without being set up", "Corrupt or mismatched gearbox software", "An internal fault in the PDK control unit"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note whether the code appeared after gearbox or software work", "Ask a Porsche dealer to check the control unit's coding and software"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P174E", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Stored control unit data incorrect at start-up",
            "Porsche's definition: the settings stored in the PDK control unit's memory were wrong or implausible when it started up.",
            M,
            listOf("A PDK control unit that was replaced or reprogrammed without being set up", "Corrupt or mismatched gearbox software", "An internal fault in the PDK control unit"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note whether the code appeared after gearbox or software work", "Ask a Porsche dealer to check the control unit's coding and software"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P172D", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Transmission temperature sensor gradient fault",
            "Porsche's definition: the gearbox temperature reading changed faster than is physically possible.",
            M,
            listOf("A faulty sensor inside the PDK gearbox", "Damaged wiring or a poor connector", "A PDK control unit fault"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P17B1 P17B2", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Clutch fault: activation pressure implausible",
            "Porsche's definition: the pressure used to apply a clutch doesn't match what the control unit commanded.",
            H,
            listOf("A fault inside the PDK gearbox", "A fault in the gearbox's hydraulic control unit", "A PDK control unit or software fault"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P17B3 P17B4", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Clutch valve pressure stays too high after quick filling",
            "Porsche's definition: during a gear change, the pressure at a clutch valve stayed too high after the clutch was filled.",
            H,
            listOf("A fault inside the PDK gearbox", "A fault in the gearbox's hydraulic control unit", "A PDK control unit or software fault"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P17BB P17BC P17BD P17BE", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Clutch fault: clutch cannot be opened",
            "Porsche's definition: one of the gearbox's two clutches won't release.",
            H,
            listOf("A fault inside the PDK gearbox", "A fault in the gearbox's hydraulic control unit", "A PDK control unit or software fault"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P17D0 P17D1 P17D2 P17D3 P17D4 P17D5 P17D6 P17D7 P17D8 P17D9", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Hydraulic gear selection fault",
            "Porsche's definition: the hydraulics that move the gear selectors aren't working as commanded.",
            H,
            listOf("A fault inside the PDK gearbox", "A fault in the gearbox's hydraulic control unit", "A PDK control unit or software fault"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P17F0 P17F1 P17F2", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Transmission overtemperature",
            "Porsche's definition: the PDK gearbox is too hot.",
            H,
            listOf("Hard driving, repeated launches or creeping in traffic on a hill", "Low or old PDK fluid", "A fault in the gearbox cooling"),
            listOf("A transmission temperature warning", "Reduced power", "Rougher pull-away while the gearbox protects itself"),
            listOf("Stop somewhere safe and let the gearbox cool with the engine idling", "Check for blocked radiators or coolers behind the front bumper", "Have the PDK fluid and cooling checked if it happens in normal driving"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P1779 P177A P177B P177C P177D P177E P177F", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "Synchronisation fault: gear cannot be disengaged",
            "Porsche's definition: the gearbox couldn't pull a gear out of mesh.",
            H,
            listOf("A fault inside the PDK gearbox", "A fault in the gearbox's hydraulic control unit", "A PDK control unit or software fault"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2012/SB-10043332-8620.pdf
        e("P1764 P1768 P1769 P176A P176B P176C P176D P176E P176F", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman")),
            "Synchronisation fault: gear cannot be engaged",
            "Porsche's definition: the gearbox couldn't engage a gear, or the gear blocked or jumped out as it engaged.",
            H,
            listOf("A fault inside the PDK gearbox", "A fault in the gearbox's hydraulic control unit", "A PDK control unit or software fault"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 35/09: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2012/SB-10043332-8620.pdf
        e("P1760", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman")),
            "Double fault",
            "Porsche's definition: the PDK control unit has detected two gearbox faults at the same time.",
            H,
            listOf("A fault inside the PDK gearbox", "A fault in the gearbox's hydraulic control unit", "A PDK control unit or software fault"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 35/09: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P1870 P1871 P1872 P186F", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "PDK control unit data record invalid",
            "Porsche's definition: a set of learned values in the PDK control unit, such as for the shift rods, clutches or hydraulics, isn't valid.",
            M,
            listOf("A PDK control unit that was replaced or reprogrammed without being set up", "Corrupt or mismatched gearbox software", "An internal fault in the PDK control unit"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note whether the code appeared after gearbox or software work", "Ask a Porsche dealer to check the control unit and repeat the gearbox calibration"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P1897 P189A P189C P189D P18A0 P18A1 P18A2 P18A3 P18A4 P18A5 P18A6 P18A7 P18A8 P18A9 P18AA P18AB P18AC P18AD P18AE P18AF P18B0 P18B1 P18B2 P18B3 P18B4 P18B5", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman"), f(2010, 2016, "Panamera")),
            "PDK control unit internal fault (software monitoring)",
            "Porsche's definition: the PDK control unit's own self-check has found an internal fault.",
            H,
            listOf("A PDK control unit that was replaced or reprogrammed without being set up", "Corrupt or mismatched gearbox software", "An internal fault in the PDK control unit"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note whether the code appeared after gearbox or software work", "Ask a Porsche dealer to check the control unit's coding and software"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P189B", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman")),
            "PDK control unit internal fault (software monitoring)",
            "Porsche's definition: the PDK control unit's own self-check has found an internal fault.",
            H,
            listOf("A PDK control unit that was replaced or reprogrammed without being set up", "Corrupt or mismatched gearbox software", "An internal fault in the PDK control unit"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note whether the code appeared after gearbox or software work", "Ask a Porsche dealer to check the control unit's coding and software"),
            "Porsche TI 35/09 and 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2012/SB-10043332-8620.pdf
        e("P1990 P1991", "Porsche", listOf(f(2009, 2012, "Boxster|Cayman")),
            "Engine torque intervention not possible",
            "Porsche's definition: the gearbox asked the engine to adjust its torque for a gear change and the engine couldn't do it.",
            M,
            listOf("An engine fault, often with its own code", "A communication fault between the engine and PDK control units"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Look for engine codes stored at the same time and deal with those first", "Ask a Porsche dealer to check communication between the engine and gearbox control units"),
            "Porsche TI 35/09: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P1715", "Porsche", listOf(f(2010, 2016, "Panamera")),
            "PDK control unit internal fault",
            "Porsche's definition: the PDK control unit has an internal fault.",
            H,
            listOf("A PDK control unit that was replaced or reprogrammed without being set up", "Corrupt or mismatched gearbox software", "An internal fault in the PDK control unit"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note whether the code appeared after gearbox or software work", "Ask a Porsche dealer to check the control unit's coding and software"),
            "Porsche TI 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P18D0", "Porsche", listOf(f(2010, 2016, "Panamera")),
            "Clutch data file implausible",
            "Porsche's definition: the clutch data loaded into the PDK control unit doesn't make sense.",
            M,
            listOf("A PDK control unit that was replaced or reprogrammed without being set up", "Corrupt or mismatched gearbox software", "An internal fault in the PDK control unit"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note whether the code appeared after gearbox or software work", "Ask a Porsche dealer to check the control unit's coding and software"),
            "Porsche TI 43/10: the maker's own service bulletin, filed with NHTSA")
        // https://static.nhtsa.gov/odi/tsbs/2020/MC-10177958-0001.pdf
        e("P17E0", "Porsche", listOf(f(2010, 2016, "Panamera")),
            "Hydraulic emergency hold cannot be cancelled (mechanical blockage)",
            "Porsche's definition: the gearbox's emergency hold function can't be released because something is mechanically blocked.",
            H,
            listOf("A fault inside the PDK gearbox", "A fault in the gearbox's hydraulic control unit", "A PDK control unit or software fault"),
            listOf("A transmission warning on the dash", "Harsh, delayed or missing gear changes", "The gearbox using only some gears (limp-home mode)"),
            listOf("Note which gears are affected and whether the warning is yellow or red", "Have the PDK fluid level and condition checked", "Ask a Porsche dealer or PDK specialist to read the gearbox's own fault memory"),
            "Porsche TI 43/10: the maker's own service bulletin, filed with NHTSA")
    }
}
