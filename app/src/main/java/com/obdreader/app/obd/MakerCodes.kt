package com.obdreader.app.obd

import com.obdreader.app.obd.DtcLibrary.DtcInfo
import com.obdreader.app.obd.DtcLibrary.Severity

/**
 * Manufacturer-specific codes, only where the maker's own published documents give the definition
 * and say which cars and years it applies to. A code is used only for those makes and model years;
 * anything else keeps the "look it up for your make" explanation, because the same code means
 * different things on other cars.
 *
 * Ford: "OBD System Operation Summary for Gasoline Engines", published by Ford Motor Company for
 * each model year (fordservicecontent.com, Motorcraft service content). A code's years are the
 * model-year summaries it appears in with the same definition.
 */
internal object MakerCodes {

    private class Entry(
        val makes: Set<String>,
        val years: IntRange,
        val title: String,
        val meaning: String,
        val severity: Severity,
        val causes: List<String>,
        val symptoms: List<String>,
        val checks: List<String>,
        val source: String,
    )

    private val FORD = setOf("Ford", "Lincoln")
    private const val FORD_SOURCE = "Ford OBD System Operation Summary for petrol (gasoline) engines"

    private val EVAP_CAUSES = listOf(
        "A purge valve stuck open or leaking", "A blocked or kinked vapour line", "A blocked canister vent or charcoal canister", "Damaged wiring to the purge or vent valve",
    )
    private val EVAP_CHECKS = listOf(
        "Check the EVAP hoses between the tank, canister and engine for kinks, blockages or damage",
        "Check the purge and vent valve connectors and wiring",
        "Have the purge and vent valves tested, then replace the one that's stuck",
    )
    private val UEGO_CAUSES = listOf(
        "A failing wide-band oxygen sensor", "Damaged or melted sensor wiring near the exhaust", "A loose or corroded sensor connector", "Less often, the engine computer",
    )
    private val UEGO_CHECKS = listOf(
        "Check the front oxygen sensor's wiring and connector, away from hot exhaust parts",
        "Fix any wiring faults, clear the code and drive again",
        "Replace the sensor if the code returns with good wiring; if that doesn't fix it, have the engine computer checked",
    )

    private val entries: Map<String, Entry> = buildMap {
        fun e(code: String, years: IntRange, title: String, meaning: String, severity: Severity,
              causes: List<String>, symptoms: List<String>, checks: List<String>) {
            put(code, Entry(FORD, years, title, meaning, severity, causes, symptoms, checks, FORD_SOURCE))
        }
        e("P1336", 2018..2023, "Noisy crankshaft position signal, or no camshaft/crankshaft sync",
            "Ford's definition: the crankshaft position sensor signal is noisy, or the engine computer can't line up the camshaft and crankshaft signals. While this is set, misfire checks can't run.",
            Severity.HIGH,
            listOf("A failing crankshaft or camshaft position sensor", "Damaged wiring or a loose connector", "A damaged reluctor (tone) ring", "Timing chain or belt problems"),
            listOf("Hard starting or stalling", "Misfires or rough running", "Other misfire codes not being set while this one is"),
            listOf("Check the crank and cam sensor connectors and wiring", "Check the sensors are fitted firmly with nothing stuck to their tips", "Replace the sensor; have the timing checked if the code returns"))
        e("P1450", 2018..2025, "Unable to bleed up fuel tank vacuum (excessive vacuum)",
            "Ford's definition: during its evaporative emissions (EVAP) test, the fuel tank pulled more vacuum than it should, and it didn't bleed back up. Later Ford calibrations report this as P0496 instead.",
            Severity.LOW, EVAP_CAUSES,
            listOf("Usually nothing you'd notice", "Sometimes trouble filling the tank, or a whoosh when the fuel cap comes off"),
            EVAP_CHECKS)
        e("P144A", 2020..2025, "EVAP purge vapour line restricted or blocked",
            "Ford's definition: the line that carries fuel vapour from the charcoal canister to the engine is restricted or blocked. Later Ford calibrations report this as P00FE instead.",
            Severity.LOW, EVAP_CAUSES,
            listOf("Usually nothing you'd notice", "A fuel smell in some cases"),
            EVAP_CHECKS)
        e("P144C", 2020..2025, "EVAP purge check valve performance",
            "Ford's definition: on turbocharged engines, a check valve stops boost pressure pushing back into the EVAP purge line. Ford tests it during boost, and this code means it isn't working as expected. Later Ford calibrations report this as P04F0 instead.",
            Severity.LOW,
            listOf("A stuck or leaking purge check valve", "A cracked or disconnected purge hose", "A purge valve fault"),
            listOf("Usually nothing you'd notice", "A fuel smell in some cases"),
            listOf("Check the purge hoses and check valve near the intake for cracks or loose ends", "Check the purge valve connector and wiring", "Replace the check valve or purge hose assembly if it's faulty"))
        for ((code, bank) in listOf("P164A" to 1, "P164B" to 2)) {
            e(code, 2020..2025, "Oxygen sensor positive current trim circuit performance (bank $bank sensor 1)",
                "Ford's definition: a fault in the trim circuit of the front wide-band oxygen sensor on bank $bank. Ford notes it's set only on engines with NTK wide-band sensors. Bank 1 is the side of the engine with cylinder 1.",
                Severity.MEDIUM, UEGO_CAUSES,
                listOf("Higher fuel use", "Rough running in some cases", "Failed emissions test"),
                UEGO_CHECKS)
        }
        for ((code, bank) in listOf("P1646" to 1, "P1647" to 2)) {
            e(code, 2020..2025, "Linear oxygen sensor control chip (bank $bank)",
                "Ford's definition: a fault reported by the chip in the engine computer that runs the front wide-band (linear) oxygen sensor on bank $bank. Bank 1 is the side of the engine with cylinder 1.",
                Severity.MEDIUM, UEGO_CAUSES,
                listOf("Higher fuel use", "Rough running in some cases", "Failed emissions test"),
                UEGO_CHECKS)
        }
    }

    /** Every code with a maker's definition (for tests). */
    val codes: Set<String> get() = entries.keys

    /** The maker's definition of [code] for this [make] and model [year], or null if there isn't one. */
    fun lookup(code: String, make: String?, year: Int?): DtcInfo? {
        val e = entries[code] ?: return null
        val brand = make?.substringBefore(" (")?.substringBefore(" / ")?.trim() ?: return null
        if (brand !in e.makes || year == null || year !in e.years) return null
        return DtcInfo(
            code, e.title, e.meaning, e.severity, e.causes, e.symptoms, e.checks,
            system = "Engine and transmission (powertrain), manufacturer-specific code: $brand's definition for ${e.years.first}–${e.years.last} petrol engines (${e.source})",
        )
    }
}
