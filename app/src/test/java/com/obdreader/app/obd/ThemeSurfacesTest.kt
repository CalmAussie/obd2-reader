package com.obdreader.app.obd

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bottom sheets and menus take their background from Material's colorSurfaceContainerLow and
 * colorSurfaceContainer. Every style must make those solid: on Digital cockpit they were its
 * see-through glass panel colour, so every sheet showed the page behind it through the text.
 */
class ThemeSurfacesTest {

    private val themes: File? = listOf(File("src/main/res/values/themes.xml"), File("app/src/main/res/values/themes.xml")).firstOrNull { it.isFile }

    private fun items(body: String): Map<String, String> =
        Regex("""<item name="([^"]+)">([^<]*)</item>""").findAll(body).associate { it.groupValues[1] to it.groupValues[2].trim() }

    @Test fun sheetsAndMenusAreSolidInEveryStyle() {
        val xml = themes?.readText() ?: throw AssertionError("themes.xml not found from ${File(".").absolutePath}")
        val styles = Regex("""<style name="([^"]+)" parent="([^"]*)">(.*?)</style>""", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .associate { it.groupValues[1] to (it.groupValues[2] to items(it.groupValues[3])) }
        fun resolve(style: String, name: String): String? {
            var s: String? = style
            while (s != null) {
                val (parent, own) = styles[s] ?: return null
                own[name]?.let { v -> return if (v.startsWith("?attr/")) resolve(style, v.removePrefix("?attr/")) else v }
                s = parent.takeIf { it in styles }
            }
            return null
        }
        val apps = styles.keys.filter { it.startsWith("Theme.ObdReader") }
        assertTrue(apps.size >= 13)
        for (style in apps) for (attr in listOf("colorSurfaceContainerLow", "colorSurfaceContainer", "colorSurfaceContainerHigh")) {
            val v = resolve(style, attr)
            assertTrue("$style $attr = $v", v != null && (Regex("#[0-9A-Fa-f]{6}").matches(v) || Regex("#[Ff]{2}[0-9A-Fa-f]{6}").matches(v)))
        }
    }
}
