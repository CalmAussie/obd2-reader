package com.obdreader.app.obd

/**
 * Working out whether a GitHub release is newer than the installed app. Each release is tagged
 * `v<version>-build<n>`, and the app's version code is that same build number, so the build
 * number decides: a bigger number is a newer build.
 */
object UpdateCheck {

    data class Release(
        val tag: String,
        /** "1.12.0". */
        val version: String,
        val build: Int,
        /** What's new, as plain text. */
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
        val pageUrl: String,
    )

    private val TAG = Regex("^v?([0-9][0-9A-Za-z.]*)-build([0-9]+)$")

    /** The version and build number in a release tag, or null if it isn't one of the app's tags. */
    fun parseTag(tag: String): Pair<String, Int>? =
        TAG.matchEntire(tag.trim())?.let { m -> m.groupValues[2].toIntOrNull()?.let { m.groupValues[1] to it } }

    fun isNewer(release: Release, installedBuild: Long): Boolean = release.build > installedBuild

    /** The tag in a release page's address, such as ".../releases/tag/v1.13.1-build51", or null. */
    fun tagFromReleaseUrl(url: String): String? =
        url.substringAfterLast("/releases/tag/", "").substringBefore('?').substringBefore('#')
            .takeIf { it.isNotBlank() }?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    /** How long between automatic checks. */
    const val CHECK_EVERY_MS = 24L * 60 * 60 * 1000

    fun dueForCheck(lastCheckMs: Long, nowMs: Long): Boolean = lastCheckMs <= 0 || nowMs - lastCheckMs >= CHECK_EVERY_MS || nowMs < lastCheckMs

    /**
     * The "What's new" part of a release's notes (the changelog entry), as plain text: the
     * install instructions after it are left out, and Markdown is turned into plain words.
     */
    fun notes(body: String?): String {
        if (body.isNullOrBlank()) return ""
        val lines = body.replace("\r", "").lines()
        val start = lines.indexOfFirst { it.trim().equals("## What's new", ignoreCase = true) }
        val from = if (start >= 0) start + 1 else 0
        val out = mutableListOf<String>()
        for (line in lines.drop(from)) {
            val t = line.trim()
            if (t.startsWith("## ") || t.startsWith("**Download")) break
            out += t
        }
        return out.joinToString("\n")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1") // [text](link) -> text
            .replace("**", "")
            .replace("`", "")
            .replace(Regex("(?m)^- "), "• ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
