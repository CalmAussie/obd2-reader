package com.obdreader.app.ui

import androidx.annotation.StyleRes
import com.obdreader.app.R

/** The styles a user can pick: seven livery colourways, plus three layouts of their own. */
object AppThemes {

    data class AppTheme(
        val id: String,
        val name: String,
        val description: String,
        @StyleRes val style: Int,
        /** True for the telemetry, cockpit and cyberpunk layouts, which change the dashboard's layout too. */
        val ownLayout: Boolean = false,
    )

    val ALL: List<AppTheme> = listOf(
        AppTheme("asphalt", "Asphalt", "Signal orange and white stripes on dark tarmac grey.", R.style.Theme_ObdReader),
        AppTheme("endurance", "Endurance", "Night-race navy with yellow, like a Le Mans prototype after dark.", R.style.Theme_ObdReader_Endurance),
        AppTheme("kerb", "Kerb", "Red and white kerb stripes, straight off the apex of a corner.", R.style.Theme_ObdReader_Kerb),
        AppTheme("pitlane", "Pit lane", "A light livery in black and race red. Easiest to read in sunlight.", R.style.Theme_ObdReader_Pitlane),
        AppTheme("drift", "Drift", "Hot pink and teal, from the drift and street scene.", R.style.Theme_ObdReader_Drift),
        AppTheme("apex", "Apex", "Volt yellow on carbon black with sharp, raked edges.", R.style.Theme_ObdReader_Apex),
        AppTheme("british", "British racing", "Cream and gold on British racing green, like a classic sports racer.", R.style.Theme_ObdReader_British),
        AppTheme(
            "telemetry", "Race telemetry",
            "A data-logger screen: shift lights, big numbers and a live 30-second trace for every reading.",
            R.style.Theme_ObdReader_Telemetry, ownLayout = true,
        ),
        AppTheme(
            "cockpit", "Digital cockpit",
            "Calm and premium, like a modern EV: one big speed in a glowing ring, on layered glass.",
            R.style.Theme_ObdReader_Cockpit, ownLayout = true,
        ),
        AppTheme(
            "cyberpunk", "Cyberpunk",
            "A neon heads-up display: a climbing rev bar, glitch-split speed digits and glowing readouts on night-city black.",
            R.style.Theme_ObdReader_Cyberpunk, ownLayout = true,
        ),
    )

    /** Themes from earlier versions, mapped to their closest new style. */
    private val OLD = mapOf(
        "midnight" to "asphalt", "racing" to "asphalt", "daylight" to "pitlane", "lcd" to "drift",
    )

    fun byId(id: String?): AppTheme {
        val key = OLD[id] ?: id
        return ALL.firstOrNull { it.id == key } ?: ALL.first()
    }
}
