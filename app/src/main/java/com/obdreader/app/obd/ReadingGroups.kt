package com.obdreader.app.obd

/**
 * The live readings sorted by what part of the car they come from, so a long list of readings is
 * easy to scan: the Graphs tab's "Add a graph" sheet lists them this way.
 *
 * Keys are a reading's two-digit PID in hex, or [Boost.KEY] / "VOLT" for the two the app works out
 * itself. Within a group they're in the order shown, most used first.
 */
object ReadingGroups {

    enum class Group(val label: String) {
        ENGINE("Engine"),
        FUEL("Fuel and mixture"),
        AIR("Air and boost"),
        EXHAUST("Exhaust"),
        ELECTRICAL("Electrical"),
        OTHER("Other"),
    }

    private val o2Voltage = (0x14..0x1B).map { hex(it) }
    private val o2Lambda = (0x24..0x2B).map { hex(it) }

    private val order: Map<Group, List<String>> = mapOf(
        Group.ENGINE to listOf("0C", "0D", "11", "04", "0E", "05", "5C", "61", "62", "63", "1F", "7F"),
        Group.FUEL to listOf("2F", "06", "07", "08", "09") + o2Lambda + o2Voltage +
            listOf("0A", "22", "23", "59", "5E", "52", "03", "51"),
        Group.AIR to listOf("0F", "46", "0B", "10", Boost.KEY, "33"),
        Group.EXHAUST to listOf("3C", "3E", "3D", "3F", "78", "79", "2C", "2D", "32"),
        Group.ELECTRICAL to listOf("VOLT", "42"),
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
