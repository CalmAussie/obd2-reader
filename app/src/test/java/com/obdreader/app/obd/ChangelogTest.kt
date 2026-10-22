package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChangelogTest {

    private val sample = """
        # Changelog

        Every change, newest first.

        ## 1.13.1 (22 October 2026)
        - **Share preview in the same window.** See [the docs](docs/x.md).
        - Fixed: the `Connect` button.

        ## 1.13.0 (22 October 2026)
        - **Updates from inside the app.**

        ## 1.9.2 (21 October 2026)
        - ThinkDiag adapters.
    """.trimIndent()

    @Test
    fun readsEachVersion() {
        val e = Changelog.parse(sample)
        assertEquals(listOf("1.13.1", "1.13.0", "1.9.2"), e.map { it.version })
        assertEquals("22 October 2026", e[0].date)
        assertEquals("• Share preview in the same window. See the docs.\n• Fixed: the Connect button.", e[0].text)
    }

    @Test
    fun versionsCompareByNumber() {
        assertTrue(Changelog.compare("1.13.0", "1.9.2") > 0)
        assertTrue(Changelog.compare("1.13", "1.13.0") == 0)
        assertTrue(Changelog.compare("1.12.9", "1.13.0") < 0)
    }

    @Test
    fun onlyWhatsNewerThanTheInstalledVersion() {
        val e = Changelog.parse(sample)
        assertEquals(listOf("1.13.1", "1.13.0"), Changelog.since(e, "1.12.0").map { it.version })
        assertEquals(listOf("1.13.1"), Changelog.since(e, "1.13.0").map { it.version })
        assertTrue(Changelog.since(e, "1.13.1").isEmpty())
    }

    @Test
    fun theRealChangelogReads() {
        val f = listOf(File("CHANGELOG.md"), File("../CHANGELOG.md")).firstOrNull { it.isFile } ?: return
        val e = Changelog.parse(f.readText())
        assertTrue(e.size > 10)
        assertTrue(e.all { it.text.isNotBlank() })
        // Newest first, as the file says.
        assertEquals(e.map { it.version }, e.sortedWith { a, b -> Changelog.compare(b.version, a.version) }.map { it.version })
    }
}
