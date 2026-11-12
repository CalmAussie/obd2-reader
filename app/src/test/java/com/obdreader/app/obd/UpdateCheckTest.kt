package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    private fun release(build: Int) = UpdateCheck.Release("v1.12.0-build$build", "1.12.0", build, "", "https://x/OBD2Reader.apk", 1L, "https://x")

    @Test
    fun readsTheAppsReleaseTags() {
        assertEquals("1.12.0" to 49, UpdateCheck.parseTag("v1.12.0-build49"))
        assertEquals("1.9.2" to 7, UpdateCheck.parseTag("1.9.2-build7"))
        assertNull(UpdateCheck.parseTag("nightly"))
        assertNull(UpdateCheck.parseTag("v1.12.0"))
    }

    @Test
    fun readsTheTagFromTheReleasePageAddress() {
        assertEquals("v1.13.1-build51", UpdateCheck.tagFromReleaseUrl("https://github.com/CalmAussie/obd2-reader/releases/tag/v1.13.1-build51"))
        assertEquals("v1.13.1-build51", UpdateCheck.tagFromReleaseUrl("https://github.com/x/y/releases/tag/v1.13.1-build51?from=latest"))
        assertNull(UpdateCheck.tagFromReleaseUrl("https://github.com/x/y/releases"))
    }

    @Test
    fun onlyABiggerBuildIsAnUpdate() {
        assertTrue(UpdateCheck.isNewer(release(50), 49))
        assertFalse(UpdateCheck.isNewer(release(49), 49))
        assertFalse(UpdateCheck.isNewer(release(48), 49))
    }

    @Test
    fun checksAtMostOnceADay() {
        val day = UpdateCheck.CHECK_EVERY_MS
        assertTrue(UpdateCheck.dueForCheck(0, 1000))
        assertFalse(UpdateCheck.dueForCheck(1000, 1000 + day - 1))
        assertTrue(UpdateCheck.dueForCheck(1000, 1000 + day))
        // The clock went backwards (changed by hand): check again rather than wait.
        assertTrue(UpdateCheck.dueForCheck(5000, 1000))
    }

    @Test
    fun notesAreTheChangelogEntryInPlainText() {
        val body = """
            ## What's new
            - **Preview before you share a drive.** See [the docs](docs/x.md) for `more`.
            - Fixed a thing.

            **Download `OBD2Reader.apk` below** and open it on your phone.

            **Installing**
            1. Open the file.
        """.trimIndent()
        assertEquals("• Preview before you share a drive. See the docs for more.\n• Fixed a thing.", UpdateCheck.notes(body))
        assertEquals("", UpdateCheck.notes(null))
    }

    @Test
    fun eachNewVersionIsAnnouncedOnce() {
        // Newer than installed, not announced yet, not put off: announce it.
        assertTrue(UpdateCheck.shouldNotify(release(95), 94, notifiedBuild = 0, snoozedBuild = 0))
        // Already announced, or the person tapped Later on it in the app: stay quiet.
        assertFalse(UpdateCheck.shouldNotify(release(95), 94, notifiedBuild = 95, snoozedBuild = 0))
        assertFalse(UpdateCheck.shouldNotify(release(95), 94, notifiedBuild = 0, snoozedBuild = 95))
        // A later version is announced even after an earlier one was announced or put off.
        assertTrue(UpdateCheck.shouldNotify(release(96), 94, notifiedBuild = 95, snoozedBuild = 95))
        // Up to date (or a newer build installed by hand): nothing to announce.
        assertFalse(UpdateCheck.shouldNotify(release(95), 95, notifiedBuild = 0, snoozedBuild = 0))
        assertFalse(UpdateCheck.shouldNotify(release(95), 97, notifiedBuild = 0, snoozedBuild = 0))
    }

    @Test
    fun notificationNotesAreShort() {
        assertEquals("• Short note", UpdateCheck.noticeNotes("  • Short note \n"))
        val long = (1..40).joinToString("\n") { "• Line number $it of the notes" }
        val short = UpdateCheck.noticeNotes(long)
        assertTrue(short.length <= 301)
        assertTrue(short.endsWith("…"))
        assertTrue(short.startsWith("• Line number 1 of the notes"))
        // Cut at the end of a line, not mid-word.
        assertTrue(short.removeSuffix("…").endsWith("of the notes"))
        assertEquals("", UpdateCheck.noticeNotes(""))
    }
}
