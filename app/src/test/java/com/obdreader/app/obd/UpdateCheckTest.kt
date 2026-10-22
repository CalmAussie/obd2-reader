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
}
