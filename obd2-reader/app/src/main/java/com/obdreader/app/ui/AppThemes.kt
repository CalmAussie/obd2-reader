package com.obdreader.app.ui

import android.graphics.Color
import androidx.annotation.StyleRes
import com.obdreader.app.R

/** The app's selectable looks. Colours here only draw the picker's previews; the real values live in themes.xml. */
object AppThemes {

    data class AppTheme(
        val id: String,
        val name: String,
        val description: String,
        @StyleRes val style: Int,
        val previewBg: Int,
        val previewSurface: Int,
        val previewAccent: Int,
        val previewAccentAlt: Int,
        val previewText: Int,
        val previewRadiusDp: Float,
        val segmented: Boolean = false,
        /** Cut (angled) corners instead of rounded ones. */
        val cut: Boolean = false,
    )

    val ALL: List<AppTheme> = listOf(
        AppTheme(
            "midnight", "Midnight", "Charcoal and cyan with soft rounded cards. Easy on the eyes at night.",
            R.style.Theme_ObdReader,
            Color.parseColor("#0B0F14"), Color.parseColor("#141A21"), Color.parseColor("#3DDCF0"), Color.parseColor("#7C8CFF"),
            Color.parseColor("#E9EEF3"), 12f,
        ),
        AppTheme(
            "racing", "Racing", "Black with orange and yellow, sharp corners and bold, condensed numbers.",
            R.style.Theme_ObdReader_Racing,
            Color.parseColor("#09090A"), Color.parseColor("#141416"), Color.parseColor("#FF6A00"), Color.parseColor("#FFD60A"),
            Color.parseColor("#F7F7F7"), 3f,
        ),
        AppTheme(
            "apex", "Apex", "Carbon black and volt yellow, cut-off corners and slanted bold numbers, like a race-car display.",
            R.style.Theme_ObdReader_Apex,
            Color.parseColor("#0D0E11"), Color.parseColor("#17191E"), Color.parseColor("#D4FF1E"), Color.parseColor("#00D1FF"),
            Color.parseColor("#F3F5F8"), 9f, cut = true,
        ),
        AppTheme(
            "daylight", "Daylight", "A light theme with white cards and blue accents, for reading in bright sun.",
            R.style.Theme_ObdReader_Daylight,
            Color.parseColor("#F3F5F8"), Color.parseColor("#FFFFFF"), Color.parseColor("#1660D4"), Color.parseColor("#0EA5E9"),
            Color.parseColor("#111827"), 10f,
        ),
        AppTheme(
            "lcd", "Retro LCD", "Phosphor green on black, square edges, monospace digits and segmented dials.",
            R.style.Theme_ObdReader_Lcd,
            Color.parseColor("#040905"), Color.parseColor("#0A140C"), Color.parseColor("#3CFF6E"), Color.parseColor("#3CFF6E"),
            Color.parseColor("#A4FFB8"), 2f, segmented = true,
        ),
    )

    fun byId(id: String?): AppTheme = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
