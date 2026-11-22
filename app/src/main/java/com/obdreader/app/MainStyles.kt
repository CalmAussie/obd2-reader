package com.obdreader.app

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.obdreader.app.MainActivity.Companion.PREF_THEME
import com.obdreader.app.MainActivity.Companion.fadeFrom
import com.obdreader.app.MainActivity.Companion.reopenAppearance
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.ui.AppThemes
import com.obdreader.app.ui.ThemeSwatchView
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.themeShape
import com.obdreader.app.ui.uiTypeface
import kotlinx.coroutines.delay

// Styles, and day and night.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

internal fun MainActivity.showThemePicker() {
    val sheet = BottomSheetDialog(this)
    val mode = com.obdreader.app.ui.StyleClock.mode(prefs)
    val auto = mode != com.obdreader.app.obd.DayNight.Mode.OFF
    // With day and night switching on, the list sets the day or the night style; it starts on the one showing now.
    var slot = when {
        !auto -> PREF_THEME
        com.obdreader.app.ui.StyleClock.isNight(this) -> AppPrefs.STYLE_NIGHT
        else -> AppPrefs.STYLE_DAY
    }
    fun selectedId() = AppThemes.byId(prefs.getString(slot, null) ?: prefs.getString(PREF_THEME, null)).id
    val pad = dp(20f).toInt()
    val list = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, dp(8f).toInt(), pad, dp(28f).toInt())
    }
    list.addView(TextView(this).apply {
        setText(R.string.appearance)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTextColor(themeColor(R.attr.obdTextPrimary))
        typeface = uiTypeface(600)
    })
    list.addView(TextView(this).apply {
        setText(R.string.appearance_subtitle)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(themeColor(R.attr.obdTextSecondary))
        setPadding(0, dp(4f).toInt(), 0, dp(4f).toInt())
    })

    // Day and night: off, or switching between two styles.
    sheetRow(list, getString(R.string.day_night_title), mode.label) { sheet.dismiss(); showDayNight() }
    val cards = ArrayList<Triple<AppThemes.AppTheme, MaterialCardView, ImageView>>()
    fun markSelected() {
        val id = selectedId()
        cards.forEach { (theme, card, check) ->
            val on = theme.id == id
            card.strokeWidth = dp(if (on) 2f else 1f).toInt()
            card.strokeColor = themeColor(if (on) R.attr.obdAccent else R.attr.obdOutline)
            check.visibility = if (on) View.VISIBLE else View.INVISIBLE
        }
    }
    if (auto) {
        list.addView(TextView(this).apply {
            text = dayNightNow(mode); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.15f)
            setTextColor(themeColor(R.attr.obdTextSecondary)); setPadding(dp(2f).toInt(), dp(8f).toInt(), 0, 0)
        })
        val toggle = MaterialButtonToggleGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        val dayBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { id = View.generateViewId(); text = getString(R.string.day_style) }
        val nightBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { id = View.generateViewId(); text = getString(R.string.night_style) }
        toggle.addView(dayBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toggle.addView(nightBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toggle.check(if (slot == AppPrefs.STYLE_NIGHT) nightBtn.id else dayBtn.id)
        toggle.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            slot = if (id == nightBtn.id) AppPrefs.STYLE_NIGHT else AppPrefs.STYLE_DAY
            markSelected()
        }
        list.addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f).toInt() })
    }
    list.addView(View(this), LinearLayout.LayoutParams(1, dp(8f).toInt()))

    for (theme in AppThemes.ALL) {
        if (theme == AppThemes.ALL.first() || theme.ownLayout && theme == AppThemes.ALL.first { it.ownLayout }) {
            list.addView(TextView(this).apply {
                setText(if (theme.ownLayout) R.string.themes_layouts else R.string.themes_liveries)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(themeColor(R.attr.obdTextSecondary))
                typeface = uiTypeface(600)
                setPadding(dp(2f).toInt(), dp(10f).toInt(), 0, dp(8f).toInt())
            })
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f).toInt(), dp(12f).toInt(), dp(14f).toInt(), dp(12f).toInt())
        }
        row.addView(ThemeSwatchView(this, theme), LinearLayout.LayoutParams(dp(72f).toInt(), dp(54f).toInt()))
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f).toInt(), 0, dp(8f).toInt(), 0)
            addView(TextView(context).apply {
                text = theme.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(themeColor(R.attr.obdTextPrimary))
                typeface = uiTypeface(600)
            })
            addView(TextView(context).apply {
                text = theme.description
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(themeColor(R.attr.obdTextSecondary))
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val check = ImageView(this).apply {
            setImageResource(R.drawable.ic_check)
            imageTintList = ColorStateList.valueOf(themeColor(R.attr.obdAccent))
            contentDescription = "Selected"
        }
        row.addView(check, LinearLayout.LayoutParams(dp(24f).toInt(), dp(24f).toInt()))
        val card = MaterialCardView(this).apply {
            shapeAppearanceModel = themeShape()
            cardElevation = 0f
            setCardBackgroundColor(themeColor(R.attr.obdSurfaceHigh))
            isClickable = true
            isFocusable = true
            addView(row)
            setOnClickListener {
                sheet.dismiss()
                if (theme.id != selectedId()) applyTheme(theme, slot)
            }
        }
        cards += Triple(theme, card, check)
        list.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(10f).toInt()
        })
    }
    markSelected()

    sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(list) })
    sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    sheet.behavior.skipCollapsed = true
    sheet.show()
}
/** Saves a style: the chosen one, or with switching on, the day or night one ([slot]). */
internal fun MainActivity.applyTheme(theme: AppThemes.AppTheme, slot: String = PREF_THEME) {
    prefs.edit().putString(slot, theme.id).apply()
    // Redraws every screen in the new style, if it's the one showing now. The car connection lives
    // outside this screen, so it carries on uninterrupted.
    if (com.obdreader.app.ui.StyleClock.styleId(this) != shownStyle) switchStyle()
    else toast(getString(if (slot == AppPrefs.STYLE_NIGHT) R.string.night_style_set else R.string.day_style_set, theme.name))
}
// ---------- Day and night styles ----------

/** Day and night: off, at sunset and sunrise, with the phone's dark mode, or by the light sensor. */
internal fun MainActivity.showDayNight() {
    val modes = com.obdreader.app.obd.DayNight.Mode.entries
    val current = com.obdreader.app.ui.StyleClock.mode(prefs)
    choiceSheet(getString(R.string.day_night_title)) { body, sheet ->
        sheetNote(body, getString(R.string.day_night_intro))
        sheetOptions(body, modes.map { it.label }, modes.indexOf(current)) { i ->
            sheet.dismiss()
            setDayNight(modes[i])
        }
        modes.drop(1).forEach { sheetNote(body, "${it.label}: ${it.help}") }
        if (getSystemService(android.hardware.SensorManager::class.java)?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT) == null) {
            sheetNote(body, getString(R.string.day_night_no_sensor))
        }
        sheetNote(body, getString(R.string.day_night_privacy))
        sheetNote(body, getString(R.string.day_night_car))
    }
}
internal fun MainActivity.setDayNight(mode: com.obdreader.app.obd.DayNight.Mode) {
    val edit = prefs.edit().putString(AppPrefs.STYLE_AUTO, mode.key)
    val showing = AppThemes.byId(shownStyle)
    if (mode == com.obdreader.app.obd.DayNight.Mode.OFF) {
        // Off: keep the style showing now.
        edit.putString(PREF_THEME, showing.id)
    } else if (prefs.getString(AppPrefs.STYLE_DAY, null) == null || prefs.getString(AppPrefs.STYLE_NIGHT, null) == null) {
        // First time on: the chosen style for whichever it suits, and a light or dark one for the other.
        val light = android.view.ContextThemeWrapper(this, showing.style).themeBool(R.attr.obdIsLight)
        edit.putString(AppPrefs.STYLE_DAY, if (light) showing.id else if (showing.ownLayout) "rally" else "pitlane")
        edit.putString(AppPrefs.STYLE_NIGHT, if (light) "asphalt" else showing.id)
    }
    edit.apply()
    restartStyleWatch()
    // Back to the Style sheet, to pick the day and night styles (after the redraw, if the style changes now).
    if (com.obdreader.app.ui.StyleClock.styleId(this) != shownStyle) { reopenAppearance = true; switchStyle() } else showThemePicker()
}
/** "Now: day style. Night style from 19:42." */
internal fun MainActivity.dayNightNow(mode: com.obdreader.app.obd.DayNight.Mode): String {
    val night = com.obdreader.app.ui.StyleClock.isNight(this)
    val now = getString(if (night) R.string.day_night_now_night else R.string.day_night_now_day)
    val time = android.text.format.DateFormat.getTimeFormat(this)
    return when (mode) {
        com.obdreader.app.obd.DayNight.Mode.SUN -> {
            val place = com.obdreader.app.ui.StyleClock.place(this)
            val next = com.obdreader.app.obd.DayNight.nextChange(System.currentTimeMillis(), place.latitude, place.longitude)
            now + (next?.let { " " + getString(if (night) R.string.day_night_next_day else R.string.day_night_next_night, time.format(java.util.Date(it))) } ?: "") +
                if (place.exact) "" else " " + getString(R.string.day_night_rough)
        }
        com.obdreader.app.obd.DayNight.Mode.PHONE -> now + " " + getString(if (night) R.string.day_night_phone_dark else R.string.day_night_phone_light)
        com.obdreader.app.obd.DayNight.Mode.LIGHT -> now + " " + getString(if (night) R.string.day_night_light_dark else R.string.day_night_light_bright)
        else -> ""
    }
}
/** While the screen is open: the light sensor (if that's the mode) and a check every few seconds for sunset, sunrise or the light. */
internal fun MainActivity.startStyleWatch() {
    stopStyleWatch()
    val mode = com.obdreader.app.ui.StyleClock.mode(prefs)
    if (mode == com.obdreader.app.obd.DayNight.Mode.LIGHT) {
        val sm = getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)
        if (sm != null && sensor != null) {
            val l = object : android.hardware.SensorEventListener {
                override fun onSensorChanged(e: android.hardware.SensorEvent) {
                    val lux = e.values.firstOrNull() ?: return
                    lastLux = lux
                    val before = com.obdreader.app.ui.StyleClock.light.isNight
                    if (com.obdreader.app.ui.StyleClock.light.feed(lux, android.os.SystemClock.elapsedRealtime()) != before) checkStyle()
                }
                override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
            }
            sm.registerListener(l, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            lightListener = l
        }
    }
    styleWatch = lifecycleScope.launch {
        var tick = 0
        while (true) {
            lastLux?.let { lux ->
                val before = com.obdreader.app.ui.StyleClock.light.isNight
                if (com.obdreader.app.ui.StyleClock.light.feed(lux, android.os.SystemClock.elapsedRealtime()) != before) checkStyle()
            }
            // Every 30 s: has the sun set (or risen), or a switch waited for the screen to be left alone?
            if (tick++ % 6 == 0) checkStyle()
            delay(5_000)
        }
    }
}
internal fun MainActivity.stopStyleWatch() {
    styleWatch?.cancel(); styleWatch = null
    lightListener?.let { getSystemService(android.hardware.SensorManager::class.java)?.unregisterListener(it) }
    lightListener = null
    lastLux = null
}
internal fun MainActivity.restartStyleWatch() { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startStyleWatch() }
/**
 * Switches to the day or night style if it's time. It waits while the screen is in use (touched
 * in the last few seconds, a sheet or dialog open, or the floating window), so nothing changes
 * under a finger; the next check tries again.
 */
internal fun MainActivity.checkStyle() {
    val want = com.obdreader.app.ui.StyleClock.styleId(this)
    if (want == shownStyle) return
    if (isInPictureInPictureMode || !hasWindowFocus() || System.currentTimeMillis() - lastTouchMs < 8_000) return
    android.util.Log.i("OBD2Style", "day/night: $shownStyle -> $want")
    switchStyle()
}
/** Redraws the screen in the style that should show now, fading over from the old one (no flash). */
internal fun MainActivity.switchStyle() {
    fadeFrom = try { window.decorView.drawToBitmap() } catch (e: Exception) { null }
    com.obdreader.app.widget.ObdWidget.refresh(this)
    recreate()
}
/** After [switchStyle]: lays the old screen's picture over the new one and fades it out. */
internal fun MainActivity.fadeFromLastStyle() {
    val shot = fadeFrom ?: return
    fadeFrom = null
    val decor = window.decorView as? ViewGroup ?: return
    val cover = ImageView(this).apply {
        setImageBitmap(shot); scaleType = ImageView.ScaleType.FIT_XY
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    decor.addView(cover, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    cover.animate().alpha(0f).setStartDelay(120).setDuration(900).withEndAction { decor.removeView(cover) }.start()
}
