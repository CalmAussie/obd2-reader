package com.obdreader.app.obd

/**
 * Code history: every trouble code the app has seen on each car, kept on the phone only, with
 * when it was first and last seen and, once a later read no longer has it, since when it's gone.
 *
 * Only the user's own list: clearing it never touches the car's codes (the app can't clear those).
 */
object CodeHistory {

    enum class Kind(val key: String, val label: String) {
        STORED("s", "Stored"),
        PENDING("p", "Pending"),
        PERMANENT("m", "Permanent"),
    }

    data class Entry(
        val code: String,
        val kind: Kind,
        /** The car it was seen on, as chosen in the app ("2006 Mazda Tribute"). */
        val car: String,
        val firstSeen: Long,
        val lastSeen: Long,
        /** When a read first came back without it; null while it's still there. */
        val goneSince: Long? = null,
    ) {
        val active: Boolean get() = goneSince == null
    }

    /** Oldest entries go first past this, so the list can't grow without end. */
    const val MAX_ENTRIES = 300

    /**
     * Adds one code read to [history]. [seen] has the codes read for each kind; a kind that's left
     * out or null wasn't read (or the car doesn't report it), so its entries are left as they were.
     */
    fun record(history: List<Entry>, car: String, seen: Map<Kind, List<String>?>, at: Long): List<Entry> {
        var out = history
        for ((kind, codes) in seen) {
            if (codes == null) continue
            val now = codes.toSet()
            out = out.map { e ->
                when {
                    e.car != car || e.kind != kind -> e
                    e.code in now -> e.copy(lastSeen = at, goneSince = null)
                    e.active -> e.copy(goneSince = at)
                    else -> e
                }
            }
            out = out + now.filter { code -> out.none { it.car == car && it.kind == kind && it.code == code } }.map { Entry(it, kind, car, at, at) }
        }
        return out.sortedByDescending { it.lastSeen }.take(MAX_ENTRIES)
    }

    /** One car's entries, as shown: the ones still there first (latest first), then the ones gone (most recently gone first). */
    fun forCar(history: List<Entry>, car: String): List<Entry> =
        history.filter { it.car == car }.sortedWith(compareBy<Entry> { !it.active }.thenByDescending { it.goneSince ?: it.lastSeen }.thenBy { it.code })

    /** The cars in [history], [current] first, then the most recently seen. */
    fun cars(history: List<Entry>, current: String?): List<String> =
        history.sortedByDescending { it.lastSeen }.map { it.car }.distinct().sortedBy { it != current }

    /** An entry's dates in a line: "Stored. First seen 3 Nov 2026, still there on 12 Nov 2026". */
    fun describe(e: Entry, date: (Long) -> String): String {
        val first = date(e.firstSeen)
        return when {
            e.goneSince != null -> "${e.kind.label}. Seen ${if (date(e.lastSeen) == first) first else "$first to ${date(e.lastSeen)}"}, gone since ${date(e.goneSince)}"
            date(e.lastSeen) == first -> "${e.kind.label}. First seen $first"
            else -> "${e.kind.label}. First seen $first, still there on ${date(e.lastSeen)}"
        }
    }

    // Saved as one line per entry: code|kind|first|last|gone|car (the car last, as it's free text).
    fun encode(history: List<Entry>): String = history.joinToString("\n") { e ->
        listOf(e.code, e.kind.key, e.firstSeen, e.lastSeen, e.goneSince ?: "", e.car.replace("\n", " ")).joinToString("|")
    }

    fun parse(text: String?): List<Entry> = text.orEmpty().lines().mapNotNull { line ->
        val f = line.split("|", limit = 6)
        if (f.size < 6) return@mapNotNull null
        val kind = Kind.entries.firstOrNull { it.key == f[1] } ?: return@mapNotNull null
        val first = f[2].toLongOrNull() ?: return@mapNotNull null
        val last = f[3].toLongOrNull() ?: return@mapNotNull null
        if (!f[0].matches(Regex("[PCBU][0-9A-F]{4}"))) return@mapNotNull null
        Entry(f[0], kind, f[5], first, last, f[4].toLongOrNull())
    }
}
