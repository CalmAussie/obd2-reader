package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Keeps docs/CODE-SUPPORT.md in step with the car list and the code library: the page is built
 * from the same data the app uses, with a section per manufacturer. Run with REGENERATE_DOCS=1 to rewrite the file after a change.
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

    /**
     * "2018–2019: 2 codes · 2020–2025: 8 codes" for the years of [model] that have maker codes.
     * Definitions with no model or engine limit come from the makers' petrol-engine summaries, so
     * they aren't counted for diesel-only years. Where a definition is limited to some engines the
     * count is the most any engine of that year gets.
     */
    private fun makerCell(make: String, model: VehicleCatalog.Model): String {
        val electric = VehicleCatalog.isElectric(VehicleCatalog.Car(make, model.to, model.name), model.name.lowercase(Locale.US))
        val (limited, petrolOnly) = MakerCodes.coverage.filter { make in it.makes }.partition { it.limited }
        var byEngine = false
        var petrolSkipped = false
        val byYear = (model.from..model.to).associateWith { y ->
            val engines = model.engines.filter { y in it.from..it.to }
            val petrol = !electric && (engines.isEmpty() || engines.any { it.fuel != VehicleCatalog.Fuel.DIESEL })
            val general = petrolOnly.count { it.covers(make, model.name, y) }
            if (general > 0 && !petrol) petrolSkipped = true
            val mine = limited.filter { it.covers(make, model.name, y) }
            if (mine.any { c -> c.fits.any { it.engines.isNotEmpty() && it.covers(y, model.name) } }) byEngine = true
            mine.size + if (petrol) general else 0
        }.filterValues { it > 0 }
        if (byYear.isEmpty()) return when {
            electric -> "None"
            petrolSkipped -> "None yet (the maker's definitions are for petrol engines)"
            else -> "None yet"
        }
        val groups = mutableListOf<Pair<IntRange, Int>>()
        for ((y, n) in byYear.toSortedMap()) {
            val last = groups.lastOrNull()
            if (last != null && last.second == n && last.first.last == y - 1) groups[groups.size - 1] = (last.first.first..y) to n
            else groups += (y..y) to n
        }
        val note = when {
            byEngine -> " (depending on the engine)"
            limited.none { c -> byYear.keys.any { c.covers(make, model.name, it) } } -> " (petrol)"
            else -> ""
        }
        return groups.joinToString(" · ") { (r, n) -> "${years(r)}: $n ${if (n == 1) "code" else "codes"}" } + note
    }

    /** The anchor GitHub gives a heading: lower case, punctuation dropped, spaces to hyphens. */
    private fun anchor(heading: String): String = heading.lowercase(Locale.US)
        .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
        .replace(' ', '-')

    private fun link(make: String) = "[$make](#${anchor(make)})"

    /**
     * The cars of [make] that [c] covers, one per line of a table cell: "Optima 2011–2014 (2.4 I4)".
     * A definition with no model limit comes from the maker's petrol-engine summary.
     */
    private fun carsCell(make: VehicleCatalog.Make, c: MakerCodes.Coverage): String {
        val lines = mutableListOf<String>()
        for (fit in c.fits) {
            val engines = fit.engines.sorted()
            if (fit.models.isEmpty()) {
                lines += "Petrol models, ${years(fit.years)}" + if (engines.isEmpty()) "" else " (${engines.joinToString(", ")})"
                continue
            }
            for (model in make.models.sortedBy { it.name.lowercase(Locale.US) }) {
                val from = maxOf(fit.years.first, model.from)
                val to = minOf(fit.years.last, model.to)
                if (from > to || !fit.covers(from, model.name)) continue
                val offered = engines.filter { e -> model.engines.any { it.name.equals(e, ignoreCase = true) && it.from <= to && it.to >= from } }
                if (engines.isNotEmpty() && offered.isEmpty()) continue
                lines += "${model.name.replace("|", "/")} ${years(from..to)}" + if (offered.isEmpty()) "" else " (${offered.joinToString(", ")})"
            }
        }
        return lines.distinct().joinToString("<br>")
    }

    /** The code rows of [make]'s own table: code, name, cars, source. */
    private fun codeRows(make: VehicleCatalog.Make): List<List<String>> = MakerCodes.coverage
        .filter { make.name in it.makes }
        .sortedWith(compareBy({ it.code }, { it.years.first }))
        .mapNotNull { c -> carsCell(make, c).takeIf { it.isNotEmpty() }?.let { listOf(c.code, c.title, it, c.source) } }

    private fun render(): String = buildString {
        val explained = String.format(Locale.US, "%,d", explainedCount())
        val makes = VehicleCatalog.MAKES.sortedBy { it.name.lowercase(Locale.US) }
        val models = makes.sumOf { it.models.size }
        val rows = makes.associateWith { codeRows(it) }
        val withCodes = makes.filter { rows.getValue(it).isNotEmpty() }
        val definitions = MakerCodes.coverage.size
        val codes = MakerCodes.coverage.map { it.code }.toSet().size

        appendLine("# Trouble code support by car")
        appendLine()
        appendLine("Which cars in OBD2 Reader's car list get trouble codes explained, and how. This page is generated from the app's own car list and code library, so it always matches the current version.")
        appendLine()
        appendLine("## The two kinds of code")
        appendLine()
        appendLine("- **Generic codes** (P0, P2, P34, U0 and U3) mean the same thing on every car. The app explains $explained of them, with how urgent each is, common causes and what to check. They work on every petrol, diesel and hybrid car in the list, as long as its engine computer supports standard OBD2: cars sold in the US from 1996 (and a few from 1994), and elsewhere most petrol cars from about 2001 and diesels from about 2004, depending on the country.")
        appendLine("- **Manufacturer-specific codes** (such as P1 codes) mean different things on different cars. The app only explains one where the maker's own published documents give the definition and say which cars and model years it covers, and only when your chosen car is one of them. Every other manufacturer-specific code is shown with the maker's name and where to look it up.")
        appendLine()
        appendLine("Fully electric cars have no engine computer, so they report few or no standard trouble codes.")
        appendLine()
        appendLine("## Find your manufacturer")
        appendLine()
        appendLine("Every manufacturer has its own section below, with its manufacturer-specific codes and then its models. Three ways to get there:")
        appendLine()
        appendLine("- Pick the manufacturer from one of the two lists here.")
        appendLine("- On GitHub, open the outline (the list button at the top right of this page) and type the manufacturer's name into its filter box.")
        appendLine("- Use your browser's find (Ctrl+F, or Find in page on a phone) for a manufacturer, a model or a code such as P1326.")
        appendLine()
        appendLine("**With manufacturer-specific codes** (${withCodes.size} makes, $codes codes, $definitions definitions, because makers reuse code numbers for different faults):")
        appendLine()
        appendLine(withCodes.joinToString(" · ") { "${link(it.name)} (${rows.getValue(it).map { r -> r[0] }.toSet().size})" })
        appendLine()
        appendLine("**All ${makes.size} manufacturers, A to Z:**")
        appendLine()
        for ((letter, group) in makes.groupBy { it.name.first().uppercaseChar() }.toSortedMap()) {
            appendLine("- **$letter** " + group.joinToString(" · ") { link(it.name) })
        }
        appendLine()
        appendLine("## Codes and cars by manufacturer")
        appendLine()
        appendLine("${makes.size} makes, $models models. In each model table, **Generic codes** says whether the $explained generic codes apply, and **Manufacturer-specific codes** says how many maker definitions apply, by model year.")
        for (make in makes) {
            val mine = rows.getValue(make)
            appendLine()
            appendLine("### ${make.name}")
            appendLine()
            if (mine.isEmpty()) {
                appendLine("Manufacturer-specific codes: none yet. A manufacturer-specific code on these cars is shown with the maker's name and where to look it up.")
            } else {
                val n = mine.map { it[0] }.toSet().size
                appendLine("Manufacturer-specific codes: $n. Each is explained only on the cars listed beside it.")
                appendLine()
                appendLine("| Code | Name | Cars | Source |")
                appendLine("|---|---|---|---|")
                for (r in mine) appendLine("| ${r.joinToString(" | ")} |")
            }
            appendLine()
            appendLine("| Model | Years | Generic codes | Manufacturer-specific codes |")
            appendLine("|---|---|---|---|")
            for (model in make.models.sortedBy { it.name.lowercase(Locale.US) }) {
                val electric = VehicleCatalog.isElectric(VehicleCatalog.Car(make.name, model.to, model.name), model.name.lowercase(Locale.US))
                val generic = if (electric) "Few or none (electric)" else "Yes"
                appendLine("| ${model.name.replace("|", "/")} | ${years(model.from..model.to)} | $generic | ${makerCell(make.name, model)} |")
            }
            appendLine()
            appendLine("[Back to the manufacturer list](#find-your-manufacturer)")
        }
    }

    private fun docFile(): File = listOf(File("docs/CODE-SUPPORT.md"), File("../docs/CODE-SUPPORT.md"))
        .firstOrNull { it.parentFile?.isDirectory == true } ?: File("../docs/CODE-SUPPORT.md")

    /** Every link in the page lands on a heading, and no two headings share an anchor, so the manufacturer list works. */
    @Test
    fun everyManufacturerLinkLandsOnItsOwnSection() {
        val text = render()
        val anchors = text.lines().filter { it.startsWith("#") }.map { anchor(it.trimStart('#').trim()) }
        assertEquals("two headings share an anchor", anchors.size, anchors.toSet().size)
        val targets = Regex("""\]\(#([^)]+)\)""").findAll(text).map { it.groupValues[1] }.toSet()
        assertEquals("links with no heading", emptySet<String>(), targets - anchors.toSet())
        for (make in VehicleCatalog.MAKES) assertEquals("${make.name} needs one section", 1, anchors.count { it == anchor(make.name) })
    }

    /** Every definition in the app shows up under each of its makes, with the cars it covers. */
    @Test
    fun everyDefinitionIsListedUnderEachOfItsMakes() {
        val byName = VehicleCatalog.MAKES.associateBy { it.name }
        for (c in MakerCodes.coverage) for (make in c.makes) {
            val rows = codeRows(byName.getValue(make))
            assertEquals("${c.code} missing under $make", true, rows.any { it[0] == c.code && it[1] == c.title })
        }
    }

    @Test
    fun supportTableMatchesTheApp() {
        val text = render()
        val file = docFile()
        if (System.getenv("REGENERATE_DOCS") == "1") file.writeText(text)
        assertEquals("docs/CODE-SUPPORT.md is out of date: run the tests with REGENERATE_DOCS=1", text, file.readText())
    }
}
