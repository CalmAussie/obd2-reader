package com.obdreader.app.obd

/**
 * The Hide VIN setting: how much of the VIN the app shows on screen and puts in anything shared
 * (code reports, car scans, adapter logs).
 *
 * The last 6 characters are the car's serial number, the part that identifies one car. The first
 * 11 only say the maker, the kind of car, the model year and the factory, so hiding the last 6
 * keeps the VIN useful to a mechanic without giving away which car it is.
 */
object VinPrivacy {

    enum class Mode(val key: String, val label: String, val help: String) {
        FULL("full", "Show it in full", "The whole VIN, on screen and in what you share."),
        PARTIAL("partial", "Hide the last 6 characters", "The last 6, your car's serial number, are hidden. The rest still says the maker, model year and factory."),
        HIDDEN("hidden", "Hide it completely", "No VIN on screen or in anything you share."),
    }

    /** Hidden serial number unless the user chooses otherwise, so a shared report or log doesn't identify the car. */
    val DEFAULT = Mode.PARTIAL

    fun of(key: String?): Mode = Mode.entries.firstOrNull { it.key == key } ?: DEFAULT

    private const val SERIAL = 6
    private const val HIDDEN_TEXT = "hidden"

    /** The VIN as the setting allows it to be shown: "JM8YC5XXX5Y******", or "hidden". */
    fun mask(vin: String, mode: Mode): String = when (mode) {
        Mode.FULL -> vin
        Mode.PARTIAL -> if (vin.length > SERIAL) vin.dropLast(SERIAL) + "*".repeat(SERIAL) else "*".repeat(vin.length)
        Mode.HIDDEN -> HIDDEN_TEXT
    }

    /** VIN-shaped words: 17 VIN characters (no I, O or Q) with at least one letter that isn't hex, so readings in hex aren't caught. */
    private val VIN_LIKE = Regex("\\b(?=[A-HJ-NPR-Z0-9]{17}\\b)(?=[A-HJ-NPR-Z0-9]*[G-HJ-NPR-Z])[A-HJ-NPR-Z0-9]{17}\\b")

    /** Hides the VIN in any text, as [mask] does: the [known] VINs, and anything else shaped like one. */
    fun scrub(text: String, mode: Mode, known: Collection<String> = emptyList()): String {
        if (mode == Mode.FULL) return text
        var out = text
        known.filter { it.length >= SERIAL }.sortedByDescending { it.length }.forEach { out = out.replace(it, mask(it, mode)) }
        return VIN_LIKE.replace(out) { mask(it.value, mode) }
    }

    /**
     * The adapter log with the VIN taken out: the raw replies to VIN requests (they spell it out in
     * hex) are replaced, and the VIN in any other line is masked.
     */
    fun scrubLog(lines: List<String>, mode: Mode, known: Collection<String> = emptyList()): List<String> {
        if (mode == Mode.FULL) return lines
        return lines.map { line ->
            val l = if (isVinRequest(line)) line.substringBefore("  < ") + "  < (reply hidden: it holds the VIN)" else line
            scrub(l, mode, known)
        }
    }

    /** A logged exchange that asks for a VIN: the engine computer's (09 02), or a module's (22 F190). */
    private fun isVinRequest(line: String): Boolean {
        if (!line.startsWith(">")) return false
        val cmd = line.removePrefix(">").substringBefore("<").replace(" ", "").uppercase(java.util.Locale.US)
        return cmd == "0902" || cmd == "22F190"
    }
}
