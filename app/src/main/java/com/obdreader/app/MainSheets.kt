package com.obdreader.app

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.obdreader.app.obd.Units
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.ui.panelBackground
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.titleTypeface
import com.obdreader.app.ui.uiTypeface

// Units, and the building blocks of the swipe-down settings sheets.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

// ---------- Units ----------

/** Speed in km/h or mph, and temperature in °C or °F, chosen separately. */
internal fun MainActivity.showUnitsPicker() {
    var mph = Units.mph
    var fahrenheit = Units.fahrenheit
    var pressure = Units.pressure
    choiceSheet(getString(R.string.units)) { body, sheet ->
        fun group(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
            sheetHeading(body, title)
            val radios = android.widget.RadioGroup(this)
            options.forEachIndexed { i, name ->
                radios.addView(com.google.android.material.radiobutton.MaterialRadioButton(this).apply {
                    id = View.generateViewId(); text = name; isChecked = i == selected
                    setOnCheckedChangeListener { _, on -> if (on) onPick(i) }
                })
            }
            body.addView(radios)
        }
        group("Speed and distance", listOf("Kilometres (km/h, km)", "Miles (mph, mi)"), if (mph) 1 else 0) { mph = it == 1 }
        group("Temperature", listOf("Celsius (°C)", "Fahrenheit (°F)"), if (fahrenheit) 1 else 0) { fahrenheit = it == 1 }
        group("Pressure (boost, manifold)", listOf("Kilopascals (kPa)", "Pounds per square inch (psi)", "Bar"), pressure.ordinal) { pressure = Units.Pressure.values()[it] }
        sheetNote(body, "Recorded drives (CSV) and the adapter log stay in the car's own metric units.")
        sheetButtons(body, "Save" to {
            val changed = mph != Units.mph || fahrenheit != Units.fahrenheit || pressure != Units.pressure
            // Saved even when unchanged, so the choice stays put if the phone's country changes.
            prefs.edit().putBoolean(AppPrefs.UNITS_MPH, mph).putBoolean(AppPrefs.UNITS_FAHRENHEIT, fahrenheit)
                .putString(AppPrefs.UNITS_PRESSURE, pressure.name).apply()
            sheet.dismiss()
            if (changed) {
                AppPrefs.applyUnits(prefs)
                recreate() // rebuilds every readout in the new units
            }
        })
    }
}
// ---- Swipe-down sheets for settings ----
// Every settings picker is a bottom sheet. A picker opened from another opens on top of it, so
// one swipe down (or Back) closes only the top one and lands on the sheet underneath.

/** A settings sheet: [title], then [build] fills it. Swipe down or Back closes it without saving. */
internal fun MainActivity.choiceSheet(title: String, build: (body: LinearLayout, sheet: BottomSheetDialog) -> Unit): BottomSheetDialog {
    val sheet = BottomSheetDialog(this)
    val pad = dp(20f).toInt()
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
    body.addView(TextView(this).apply {
        text = title; setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary))
        setPadding(0, 0, 0, dp(4f).toInt())
    })
    build(body, sheet)
    sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
    sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    sheet.behavior.skipCollapsed = true
    sheet.show()
    return sheet
}
/** A panel inside a sheet: one step up from the sheet's own surface, so it stands out (the sheet itself is obdSurface). */
internal fun MainActivity.sheetPanel() = panelBackground().apply { setColor(themeColor(R.attr.obdSurfaceHigh)) }
internal fun MainActivity.sheetHeading(body: LinearLayout, text: String) = body.addView(TextView(this).apply {
    this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdTextPrimary))
    setPadding(0, dp(14f).toInt(), 0, dp(2f).toInt())
})
internal fun MainActivity.sheetNote(body: LinearLayout, text: String) = body.addView(TextView(this).apply {
    this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.15f); setTextColor(themeColor(R.attr.obdTextSecondary))
    setPadding(0, dp(10f).toInt(), 0, 0)
})
/** An on/off setting in a sheet: 15sp in the primary text colour, at least 48dp tall to tap. */
internal fun MainActivity.sheetSwitch(body: LinearLayout, label: String, checked: Boolean, onChange: (Boolean) -> Unit) = body.addView(MaterialSwitch(this).apply {
    text = label; isChecked = checked
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextPrimary))
    minHeight = dp(48f).toInt()
    setOnCheckedChangeListener { _, on -> onChange(on) }
}, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f).toInt() })
/**
 * Full-width buttons stacked down the sheet, as on the trip summary: the first filled, then
 * outlined, and a [danger] one (Delete) last, borderless in the danger colour.
 */
internal fun MainActivity.sheetStackedButtons(body: LinearLayout, buttons: List<Pair<String, () -> Unit>>, danger: Pair<String, () -> Unit>? = null) {
    val all = buttons + listOfNotNull(danger)
    all.forEachIndexed { i, (label, action) ->
        val isDanger = danger != null && i == all.lastIndex
        val style = when {
            isDanger -> androidx.appcompat.R.attr.borderlessButtonStyle
            i == 0 -> com.google.android.material.R.attr.materialButtonStyle
            else -> com.google.android.material.R.attr.materialButtonOutlinedStyle
        }
        body.addView(MaterialButton(this, null, style).apply {
            text = label
            if (isDanger) setTextColor(themeColor(R.attr.obdDangerText))
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(if (i == 0) 18f else 4f).toInt() })
    }
}
/** A tappable row: [name] with its current [value] and a chevron, opening the next sheet. */
internal fun MainActivity.sheetRow(body: LinearLayout, name: String, value: String, onClick: () -> Unit): TextView {
    val valueView = TextView(this).apply {
        text = value; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdAccentText))
        gravity = Gravity.END
    }
    body.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = sheetPanel(); val p = dp(14f).toInt(); setPadding(p, p, dp(8f).toInt(), p)
        isClickable = true; isFocusable = true; contentDescription = "$name: $value"
        addView(TextView(this@sheetRow).apply {
            text = name; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextPrimary))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(valueView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(android.widget.ImageView(this@sheetRow).apply {
            setImageResource(R.drawable.ic_chevron_right); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        setOnClickListener { onClick() }
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f).toInt() })
    return valueView
}
/** A single-choice list: tapping an option calls [onPick] with its index. */
internal fun MainActivity.sheetOptions(body: LinearLayout, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val radios = android.widget.RadioGroup(this)
    options.forEachIndexed { i, name ->
        radios.addView(com.google.android.material.radiobutton.MaterialRadioButton(this).apply {
            id = View.generateViewId(); text = name; isChecked = i == selected
            minHeight = dp(48f).toInt()
            setOnClickListener { onPick(i) }
        })
    }
    body.addView(radios)
}
/** Buttons along the bottom, right-aligned, the last one filled. */
internal fun MainActivity.sheetButtons(body: LinearLayout, vararg buttons: Pair<String, () -> Unit>) {
    body.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
        setPadding(0, dp(18f).toInt(), 0, 0)
        buttons.forEachIndexed { i, (label, action) ->
            val last = i == buttons.lastIndex
            addView(MaterialButton(this@sheetButtons, null, if (last) com.google.android.material.R.attr.materialButtonStyle else androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                text = label
                if (!last) setTextColor(themeColor(R.attr.obdAccentText))
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8f).toInt() })
        }
    })
}
/**
 * A picker for a few numbered places (Dashboard readouts, widget readings, Android Auto tiles):
 * the first sheet lists each place and what's in it; tapping one opens a second sheet on top
 * with the choices. Picking closes only that second sheet; Save or Reset closes the first.
 * Choosing something already in another place swaps the two.
 */
internal fun <K> MainActivity.placesSheet(
    title: String, note: String?, places: List<String>, chosen: MutableList<K>,
    choicesFor: (Int) -> List<Pair<K, String>>, nameOf: (K) -> String,
    save: (List<K>) -> Unit, reset: () -> Unit,
) {
    choiceSheet(title) { body, sheet ->
        if (note != null) sheetNote(body, note)
        val values = mutableListOf<TextView>()
        places.forEachIndexed { slot, place ->
            values += sheetRow(body, place, nameOf(chosen[slot])) {
                val choices = choicesFor(slot)
                choiceSheet(place) { inner, innerSheet ->
                    sheetOptions(inner, choices.map { it.second }, choices.indexOfFirst { it.first == chosen[slot] }) { i ->
                        val pick = choices[i].first
                        val other = chosen.indexOf(pick)
                        if (pick != null && other >= 0 && other != slot) chosen[other] = chosen[slot] // swap rather than show one twice
                        chosen[slot] = pick
                        places.indices.forEach { values[it].text = nameOf(chosen[it]) }
                        innerSheet.dismiss()
                    }
                }
            }
        }
        sheetButtons(body, "Reset" to { reset(); sheet.dismiss() }, "Save" to { save(chosen.toList()); sheet.dismiss() })
    }
}
