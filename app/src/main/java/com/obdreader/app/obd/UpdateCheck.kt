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
     * Whether the background check should post a "new version" notification for [release]: it's
     * newer than the installed build, and it isn't one already notified about ([notifiedBuild]) or
     * one the person tapped Later on in the app ([snoozedBuild]). So each new version gets one
     * notification at most, and a version that's been put off isn't brought up again.
     */
    fun shouldNotify(release: Release, installedBuild: Long, notifiedBuild: Int, snoozedBuild: Int): Boolean =
        isNewer(release, installedBuild) && release.build != notifiedBuild && release.build != snoozedBuild

    /** The first few lines of a release's notes, short enough for a notification. */
    fun noticeNotes(notes: String, maxChars: Int = 300): String {
        val text = notes.trim()
        if (text.length <= maxChars) return text
        val cut = text.take(maxChars)
        val end = cut.lastIndexOf('\n').takeIf { it > maxChars / 2 } ?: cut.lastIndexOf(' ').takeIf { it > 0 } ?: maxChars
        return cut.take(end).trimEnd() + "…"
    }

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
