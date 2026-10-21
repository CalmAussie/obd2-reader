package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Keeps docs/CODE-SUPPORT.md in step with the car list and the code library: the table is built
 * from the same data the app uses. Run with REGENERATE_DOCS=1 to rewrite the file after a change.
 */
class CodeSupportDocTest {

    private fun explainedCount(): Int {
        val hex = "0123456789ABCDEF"
        var n = 0
        for (l in "PBCU") for (a in "0123") for (b in hex) for (c in hex) for (d in hex) {
            if (DtcLibrary.lookup("$l$a$b$c$d").known) n++
        }
        return n
    }

    private fun years(r: IntRange) = if (r.first == r.last) "${r.first}" else "${r.first}–${r.last}"

    /** "2018–2019: 2 codes · 2020–2025: 8 codes" for the years of [model] that have maker codes. */
    private fun makerCell(make: String, model: VehicleCatalog.Model): String {
        if (VehicleCatalog.isElectric(VehicleCatalog.Car(make, model.to, model.name), model.name.lowercase(Locale.US))) return "None"
        val byYear = (model.from..model.to).associateWith { y ->
            MakerCodes.coverage.count { make in it.makes && y in it.years }
        }.filterValues { it > 0 }
        if (byYear.isEmpty()) return "None yet"
        // Diesel-only years: the published definitions are for petrol engines.
        val petrolYears = byYear.filterKeys { y ->
            val engines = model.engines.filter { y in it.from..it.to }
            engines.isEmpty() || engines.any { it.fuel != VehicleCatalog.Fuel.DIESEL }
        }
        if (petrolYears.isEmpty()) return "None yet (the maker's definitions are for petrol engines)"
        val groups = mutableListOf<Pair<IntRange, Int>>()
        for ((y, n) in petrolYears.toSortedMap()) {
            val last = groups.lastOrNull()
            if (last != null && last.second == n && last.first.last == y - 1) groups[groups.size - 1] = (last.first.first..y) to n
            else groups += (y..y) to n
        }
        return groups.joinToString(" · ") { (r, n) -> "${years(r)}: $n ${if (n == 1) "code" else "codes"}" } + " (petrol)"
    }

    private fun render(): String = buildString {
        val explained = String.format(Locale.US, "%,d", explainedCount())
        val makes = VehicleCatalog.MAKES.sortedBy { it.name.lowercase(Locale.US) }
        val models = makes.sumOf { it.models.size }
        val coverage = MakerCodes.coverage.sortedBy { it.code }
        val makerMakes = coverage.flatMap { it.makes }.toSortedSet()

        appendLine("# Trouble code support by car")
        appendLine()
        appendLine("Which cars in OBD2 Reader's car list get trouble codes explained, and how. This page is generated from the app's own car list and code library, so it always matches the current version.")
        appendLine()
        appendLine("## The two kinds of code")
        appendLine()
        appendLine("- **Generic codes** (P0, P2, P34, U0 and U3) mean the same thing on every car. The app explains $explained of them, with how urgent each is, common causes and what to check. They work on every petrol, diesel and hybrid car in the list, as long as its engine computer supports standard OBD2: most petrol cars from about 2001 and diesels from about 2004, depending on the country.")
        appendLine("- **Manufacturer-specific codes** (such as P1 codes) mean different things on different cars. The app only explains one where the maker's own published documents give the definition and say which model years it covers. Every other manufacturer-specific code is shown with the maker's name and where to look it up.")
        appendLine()
        appendLine("Fully electric cars have no engine computer, so they report few or no standard trouble codes.")
        appendLine()
        appendLine("## Manufacturer-specific codes explained")
        appendLine()
        appendLine("| Code | Makes | Model years | Name | Source |")
        appendLine("|---|---|---|---|---|")
        for (c in coverage) appendLine("| ${c.code} | ${c.makes.sorted().joinToString(", ")} | ${years(c.years)} | ${c.title} | ${c.source} |")
        appendLine()
        appendLine("Makes with manufacturer-specific codes so far: ${makerMakes.joinToString(", ")}. Every other make: none yet.")
        appendLine()
        appendLine("## Every car in the list")
        appendLine()
        appendLine("${makes.size} makes, $models models. **Generic codes** says whether the $explained generic codes apply. **Manufacturer-specific codes** says how many maker definitions apply, by model year.")
        appendLine()
        appendLine("| Make | Model | Years | Generic codes | Manufacturer-specific codes |")
        appendLine("|---|---|---|---|---|")
        for (make in makes) for (model in make.models.sortedBy { it.name.lowercase(Locale.US) }) {
            val electric = VehicleCatalog.isElectric(VehicleCatalog.Car(make.name, model.to, model.name), model.name.lowercase(Locale.US))
            val generic = if (electric) "Few or none (electric)" else "Yes"
            appendLine("| ${make.name} | ${model.name.replace("|", "/")} | ${years(model.from..model.to)} | $generic | ${makerCell(make.name, model)} |")
        }
    }

    private fun docFile(): File = listOf(File("docs/CODE-SUPPORT.md"), File("../docs/CODE-SUPPORT.md"))
        .firstOrNull { it.parentFile?.isDirectory == true } ?: File("../docs/CODE-SUPPORT.md")

    @Test
    fun supportTableMatchesTheApp() {
        val text = render()
        val file = docFile()
        if (System.getenv("REGENERATE_DOCS") == "1") file.writeText(text)
        assertEquals("docs/CODE-SUPPORT.md is out of date: run the tests with REGENERATE_DOCS=1", text, file.readText())
    }
}
