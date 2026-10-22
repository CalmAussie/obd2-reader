package com.obdreader.app.obd

/**
 * The app's CHANGELOG.md, read into versions, so the app can show just what changed after the
 * version that's installed. Each version starts with a "## 1.13.1 (22 October 2026)" heading.
 */
object Changelog {

    data class Entry(val version: String, val date: String, val text: String)

    private val HEADING = Regex("^##\\s+([0-9]+(?:\\.[0-9]+)*)\\s*(?:\\(([^)]*)\\))?\\s*$")

    fun parse(markdown: String): List<Entry> {
        val out = mutableListOf<Entry>()
        var version: String? = null; var date = ""; val body = StringBuilder()
        fun flush() { version?.let { out += Entry(it, date, plain(body.toString())) }; body.setLength(0) }
        for (line in markdown.replace("\r", "").lines()) {
            val m = HEADING.matchEntire(line.trim())
            if (m != null) { flush(); version = m.groupValues[1]; date = m.groupValues[2].trim() }
            else if (line.startsWith("## ")) { flush(); version = null } // another kind of section
            else if (version != null) body.appendLine(line)
        }
        flush()
        return out
    }

    /** Compares "1.13.1" with "1.9.2" number by number, so 1.13 is newer than 1.9. */
    fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** The versions newer than [installed], newest first. */
    fun since(entries: List<Entry>, installed: String): List<Entry> =
        entries.filter { compare(it.version, installed) > 0 }.sortedWith { a, b -> compare(b.version, a.version) }

    /** Markdown turned into plain words: links keep their text, bold and code marks go, bullets become dots. */
    fun plain(markdown: String): String = markdown
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace("**", "")
        .replace("`", "")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("(?m)^[-*] "), "• ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
