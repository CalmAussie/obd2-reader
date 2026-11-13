package com.obdreader.app.obd

/**
 * The live readings sorted by what part of the car they come from, so a long list of readings is
 * easy to scan: the Graphs tab's "Add a graph" sheet lists them this way.
 *
 * Keys are a reading's two-digit PID in hex, or [Boost.KEY] / "VOLT" for the two the app works out
 * itself. Within a group they're in the order shown, most used first.
 */
object ReadingGroups {

    /** [label] heads a list; [short] names the group's tab on the Graphs tab. */
    enum class Group(val label: String, val short: String) {
        ENGINE("Engine", "Engine"),
        FUEL("Fuel and mixture", "Fuel"),
        AIR("Air and boost", "Air"),
        EXHAUST("Exhaust", "Exhaust"),
        ELECTRICAL("Electrical", "Electrical"),
        /** How the car moves, from the phone's own motion sensor. */
        DRIVING("Driving", "Driving"),
        /** The car's own checks: monitor status, counters since the codes were cleared, the OBD standard it meets. Never graphed. */
        DIAGNOSTICS("Diagnostics", "Diagnostics"),
        OTHER("Other", "Other"),
    }

    private fun range(from: Int, to: Int) = (from..to).map { hex(it) }

    // Every standard reading the car scan can name (CarCoverage), so "What your car reports" groups them all.
    private val order: Map<Group, List<String>> = mapOf(
        Group.ENGINE to listOf("0C", "0D", "11", "04", "43", "0E", "05", "67", "5C", "61", "62", "63", "64", "8E",
            "45", "47", "48", "49", "4A", "4B", "4C", "5A", "6C", "8D", "A4", "A6", "1F", "7F"),
        Group.FUEL to listOf("2F", "06", "07", "08", "09") + range(0x24, 0x2B) + range(0x14, 0x1B) + range(0x34, 0x3B) +
            listOf("13", "1D", "44", "55", "56", "57", "58", "0A", "22", "23", "59", "6D", "6E", "5D", "5E", "9D", "52", "03", "51"),
        Group.AIR to listOf("0F", "46", "68", "0B", "87", "10", "50", "66", Boost.KEY, "33", "6F", "70", "71", "72", "74", "75", "76", "77"),
        Group.EXHAUST to listOf("3C", "3E", "3D", "3F", "78", "79", "73", "84", "2C", "2D", "6B", "12", "32", "53", "54", "2E",
            "7A", "7B", "7C", "83", "85", "86", "9E"),
        Group.ELECTRICAL to listOf("VOLT", "42", "5B", "9A"),
        Group.DRIVING to listOf(GForce.FORWARD_KEY, GForce.SIDE_KEY),
        Group.DIAGNOSTICS to listOf("01", "41", "21", "4D", "31", "30", "4E", "02", "1C", "5F", "7D", "7E", "1E", "65", "4F"),
    )

    private val groupOf: Map<String, Group> = order.flatMap { (g, keys) -> keys.map { it to g } }.toMap()

    fun of(key: String): Group = groupOf[key.uppercase()] ?: Group.OTHER

    /**
     * [keys] in groups, in the groups' order and each group's own order; empty groups are left out.
     * Keys the table doesn't know go in Other, at the end, in the order given.
     */
    fun sort(keys: Collection<String>): List<Pair<Group, List<String>>> {
        val rank = order.values.flatten().withIndex().associate { (i, k) -> k to i }
        return keys.distinct().groupBy { of(it) }.toSortedMap()
            .map { (g, ks) -> g to (if (g == Group.OTHER) ks else ks.sortedBy { rank[it.uppercase()] ?: Int.MAX_VALUE }) }
    }

    private fun hex(n: Int) = String.format(java.util.Locale.US, "%02X", n)
}
