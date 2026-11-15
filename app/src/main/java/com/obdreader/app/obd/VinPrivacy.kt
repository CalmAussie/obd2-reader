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

    /** Hidden unless the user chooses otherwise (George, 2026-11-12), so nothing on screen or shared identifies the car. */
    val DEFAULT = Mode.HIDDEN

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

    /**
     * A VIN after a "VIN" label ("VIN: ...", "VIN stored in the module=..."), whatever its letters: a
     * VIN made only of digits and A to F looks like a hex reading, so only its label gives it away.
     */
    private val LABELLED_VIN = Regex("(?i:\\bVIN\\b)[^=:\\n|;]{0,40}[=:]\\s*([A-HJ-NPR-Z0-9]{17})\\b")

    /** 17 characters that are all digits or A to F: a hex reading, or a VIN that happens to use only those. */
    private val HEX_WORD = Regex("\\b[0-9A-F]{17}\\b")

    /**
     * True when a hex-looking word is more likely a VIN than a reading: its 9th character is the VIN
     * check digit (required in North America, used by many other makers) and its 10th is a model year.
     * About 1 in 11 random hex words pass too, so this is only used outside the car's raw replies.
     */
    private fun hexVin(word: String): Boolean = word[9] != '0' && VinDecoder.checkDigit(word) == word[8]

    /**
     * Hides the VIN in any text, as [mask] does: the [known] VINs, anything after a "VIN" label, and
     * anything else shaped like one. [hexVins] also hides hex-looking words that pass the VIN check
     * digit; it's off for the car's raw replies, which are hex readings.
     */
    fun scrub(text: String, mode: Mode, known: Collection<String> = emptyList(), hexVins: Boolean = true): String {
        if (mode == Mode.FULL) return text
        var out = text
        known.filter { it.length >= SERIAL }.sortedByDescending { it.length }.forEach { out = out.replace(it, mask(it, mode)) }
        out = LABELLED_VIN.replace(out) { m -> val g = m.groups[1]!!; m.value.substring(0, g.range.first - m.range.first) + mask(g.value, mode) }
        out = VIN_LIKE.replace(out) { mask(it.value, mode) }
        if (hexVins) out = HEX_WORD.replace(out) { if (hexVin(it.value)) mask(it.value, mode) else it.value }
        return out
    }

    /**
     * The adapter log with the VIN taken out: the raw replies to VIN requests (they spell it out in
     * hex) are replaced, and the VIN in any other line is masked.
     */
    fun scrubLog(lines: List<String>, mode: Mode, known: Collection<String> = emptyList()): List<String> {
        if (mode == Mode.FULL) return lines
        return lines.map { line ->
            if (isVinRequest(line)) return@map scrub(line.substringBefore(REPLY), mode, known) + REPLY + "(reply hidden: it holds the VIN)"
            val at = line.indexOf(REPLY)
            if (line.startsWith(">") && at >= 0) scrub(line.substring(0, at), mode, known) + REPLY + scrub(line.substring(at + REPLY.length), mode, known, hexVins = false)
            else scrub(line, mode, known)
        }
    }

    /** Where a logged request ends and the car's reply starts. */
    private const val REPLY = "  < "

    /** A logged exchange that asks for a VIN: the engine computer's (09 02), or a module's (UDS 22 F190, KWP2000 1A 90). */
    private fun isVinRequest(line: String): Boolean {
        if (!line.startsWith(">")) return false
        val cmd = line.removePrefix(">").substringBefore("<").replace(" ", "").uppercase(java.util.Locale.US)
        return cmd == "0902" || cmd == "22F190" || cmd == "1A90"
    }
}
