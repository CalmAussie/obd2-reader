package com.obdreader.app

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.obdreader.app.MainActivity.Companion.PREF_CUSTOM
import com.obdreader.app.obd.CustomReading
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.AppIcons
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.titleTypeface
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Custom readings, market, car and widget readings, and the app icon.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

// ---- Custom readings ----

internal fun MainActivity.customReadings(): MutableList<CustomReading> = CustomReading.decodeAll(prefs.getString(PREF_CUSTOM, null)).toMutableList()
internal fun MainActivity.saveCustom(list: List<CustomReading>) = prefs.edit().putString(PREF_CUSTOM, CustomReading.encodeAll(list)).apply()
internal fun MainActivity.showCustomReadings() {
    val list = customReadings()
    val sheet = BottomSheetDialog(this)
    val pad = dp(20f).toInt()
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
    body.addView(TextView(this).apply { setText(R.string.custom_title); setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary)) })
    val values = mutableMapOf<CustomReading, TextView>()
    if (list.isEmpty()) body.addView(TextView(this).apply {
        setText(R.string.custom_empty); setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setPadding(0, dp(8f).toInt(), 0, 0)
    })
    list.forEach { r ->
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = sheetPanel(); val p = dp(14f).toInt(); setPadding(p, p, p, p)
            addView(LinearLayout(this@showCustomReadings).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@showCustomReadings).apply { text = r.name; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextPrimary)) })
                addView(TextView(this@showCustomReadings).apply { text = (r.header?.let { "$it · " } ?: "") + r.request + " · " + r.formula; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@showCustomReadings).apply {
                text = "–"; typeface = titleTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); setTextColor(themeColor(R.attr.obdAccentText))
                values[r] = this
            })
            setOnLongClickListener {
                MaterialAlertDialogBuilder(this@showCustomReadings).setTitle("Delete ${r.name}?")
                    .setPositiveButton(R.string.history_delete) { _, _ -> saveCustom(customReadings().filter { it != r }); sheet.dismiss(); showCustomReadings() }
                    .setNegativeButton(android.R.string.cancel, null).show(); true
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f).toInt() })
    }
    body.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(MaterialButton(this@showCustomReadings).apply { setText(R.string.custom_add); setOnClickListener { sheet.dismiss(); addCustomReading() } })
        if (list.isNotEmpty()) addView(MaterialButton(this@showCustomReadings, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            setText(R.string.custom_refresh); setTextColor(themeColor(R.attr.obdAccentText)); setOnClickListener { readCustomInto(values) }
        })
        setPadding(0, dp(12f).toInt(), 0, 0)
    })
    body.addView(TextView(this).apply {
        setText(R.string.custom_note); setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setPadding(0, dp(12f).toInt(), 0, 0)
    })
    sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
    sheet.show()
    readCustomInto(values)
}
internal fun MainActivity.readCustomInto(values: Map<CustomReading, TextView>) {
    if (!ObdSession.state.value.isConnected) { values.values.forEach { it.text = "–" }; return }
    lifecycleScope.launch {
        for ((r, tv) in values) {
            val v = try { ObdSession.readCustom(r) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            tv.text = v?.let { String.format(Locale.US, if (kotlin.math.abs(it) >= 100) "%.0f" else "%.2f", it) + if (r.unit.isEmpty()) "" else " ${r.unit}" } ?: "N/A"
            android.util.Log.i("OBD2Deep", "custom ${r.name}: ${tv.text}")
        }
    }
}
internal fun MainActivity.addCustomReading() {
    val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20f).toInt(), dp(8f).toInt(), dp(20f).toInt(), 0) }
    fun field(hint: String, caps: Boolean): com.google.android.material.textfield.TextInputEditText {
        val box = com.google.android.material.textfield.TextInputLayout(this).apply { this.hint = hint }
        val e = com.google.android.material.textfield.TextInputEditText(box.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or (if (caps) android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else 0) or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        box.addView(e); form.addView(box); return e
    }
    val name = field("Name (e.g. Transmission temp)", false)
    val req = field("Request, hex (e.g. 221E1C)", true)
    val hdr = field("Module address, optional (e.g. 7E0)", true)
    val f = field("Formula (e.g. (A*256+B)/16)", true)
    val unit = field("Unit (e.g. °C)", false)
    val d = MaterialAlertDialogBuilder(this).setTitle(R.string.custom_add).setView(form)
        .setPositiveButton(R.string.save, null).setNegativeButton(android.R.string.cancel, null).show()
    d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val t = { e: com.google.android.material.textfield.TextInputEditText -> e.text?.toString().orEmpty() }
        val problem = CustomReading.problem(t(name), t(req), t(hdr), t(f))
        if (problem != null) { toast(problem); return@setOnClickListener }
        val r = CustomReading.validate(t(name), t(req), t(hdr), t(f), t(unit)) ?: return@setOnClickListener
        saveCustom(customReadings() + r)
        d.dismiss(); showCustomReadings()
    }
}
internal fun MainActivity.marketLabel(): String =
    if (prefs.contains(AppPrefs.MARKET)) AppPrefs.market(prefs).label else "Automatic (${AppPrefs.market(prefs).label})"
/**
 * Diagnostics → Where your car was sold: decides the notes on when OBD2 became required.
 * Automatic follows the phone's country; an imported car follows the market it was built for.
 */
internal fun MainActivity.showMarketPicker() {
    val all = com.obdreader.app.obd.Market.values()
    val auto = com.obdreader.app.obd.Market.fromCountry(AppPrefs.phoneCountry())
    val names = listOf("Automatic, from your phone (${auto.label})") + all.map { it.label }
    val chosen = com.obdreader.app.obd.Market.byName(prefs.getString(AppPrefs.MARKET, null))
    choiceSheet(getString(R.string.market)) { body, sheet ->
        sheetNote(body, "Decides the notes on when OBD2 became required. An imported car follows the market it was built for.")
        sheetOptions(body, names, if (chosen == null) 0 else all.indexOf(chosen) + 1) { i ->
            if (i == 0) prefs.edit().remove(AppPrefs.MARKET).apply() else prefs.edit().putString(AppPrefs.MARKET, all[i - 1].name).apply()
            AppPrefs.applyUnits(prefs)
            android.util.Log.i("OBD2Car", "market: " + com.obdreader.app.obd.Market.current.name)
            findViewById<TextView>(R.id.marketSetting).text = marketLabel()
            updateCarSetting()
            sheet.dismiss()
        }
    }
}
internal fun MainActivity.carReadingsLabel(): String =
    com.obdreader.app.obd.CarReadings.decode(prefs.getString(AppPrefs.CAR_READINGS, null)).joinToString(", ") { com.obdreader.app.obd.CarReadings.label(it) }
/**
 * Diagnostics → Android Auto readings: the four tiles next to speed and engine speed on the
 * car's screen, in order. Picking one that's already shown swaps them.
 */
internal fun MainActivity.showCarReadings(chosen: MutableList<String>) {
    val all = com.obdreader.app.obd.CarReadings.CHOICES
    placesSheet(
        getString(R.string.car_readings), "Speed and engine speed always come first. Choose the four tiles after them.",
        (1..com.obdreader.app.obd.CarReadings.COUNT).map { "Tile ${it + 2}" }, chosen,
        { all.map { it.key to it.label } }, { com.obdreader.app.obd.CarReadings.label(it) },
        save = { keys ->
            prefs.edit().putString(AppPrefs.CAR_READINGS, com.obdreader.app.obd.CarReadings.encode(keys)).apply()
            android.util.Log.i("OBD2Car", "car readings: " + keys.joinToString(","))
            findViewById<TextView>(R.id.autoSetting).text = carReadingsLabel()
        },
        reset = {
            prefs.edit().remove(AppPrefs.CAR_READINGS).apply()
            findViewById<TextView>(R.id.autoSetting).text = carReadingsLabel()
        },
    )
}
internal fun MainActivity.widgetReadingsLabel(): String =
    com.obdreader.app.obd.WidgetContent.decodeReadings(prefs.getString(com.obdreader.app.widget.ObdWidget.PREF_READINGS, null))
        .mapNotNull { k -> com.obdreader.app.obd.WidgetContent.READINGS.firstOrNull { it.key == k }?.label }.joinToString(", ")
/**
 * Diagnostics → Widget readings: up to three live readings for the home-screen widget, in
 * order. The first is the one the 2 × 1 widget shows. RPM is shown in thousands (3.2k).
 */
internal fun MainActivity.showWidgetReadings(chosen: MutableList<String>) {
    val all = com.obdreader.app.obd.WidgetContent.READINGS
    fun name(k: String?) = k?.let { key -> all.firstOrNull { it.key == key }?.label } ?: "None"
    val places = listOf("Reading 1 (also the 2 × 1 widget)", "Reading 2", "Reading 3")
    val slotsNow: MutableList<String?> = (0 until 3).map { chosen.getOrNull(it) }.toMutableList()
    placesSheet(
        getString(R.string.widget_readings), null, places, slotsNow,
        // Reading 1 is always set; the others can be left empty.
        { slot -> (if (slot == 0) emptyList() else listOf(null to "None")) + all.map { it.key to it.label } }, { name(it) },
        save = { keys ->
            val list = keys.filterNotNull()
            prefs.edit().putString(com.obdreader.app.widget.ObdWidget.PREF_READINGS, com.obdreader.app.obd.WidgetContent.encodeReadings(list)).apply()
            android.util.Log.i("OBD2Widget", "widget readings: " + list.joinToString(","))
            findViewById<TextView>(R.id.widgetSetting).text = widgetReadingsLabel()
            com.obdreader.app.widget.ObdWidget.refresh(this)
        },
        reset = {
            prefs.edit().remove(com.obdreader.app.widget.ObdWidget.PREF_READINGS).apply()
            findViewById<TextView>(R.id.widgetSetting).text = widgetReadingsLabel()
            com.obdreader.app.widget.ObdWidget.refresh(this)
        },
    )
}
/** Diagnostics → App icon: the Spark colourways, each shown as it looks on the home screen. */
internal fun MainActivity.showIconPicker() {
    val sheet = BottomSheetDialog(this)
    val pad = dp(20f).toInt()
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
    body.addView(TextView(this).apply { setText(R.string.app_icon); setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary)) })
    body.addView(TextView(this).apply { setText(R.string.app_icon_help); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdTextSecondary)); setPadding(0, dp(4f).toInt(), 0, dp(8f).toInt()) })
    val current = AppIcons.current(this)
    AppIcons.ALL.forEach { icon ->
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = sheetPanel(); val p = dp(12f).toInt(); setPadding(p, p, p, p)
            isClickable = true; isFocusable = true
            contentDescription = icon.name + if (icon == current) ", chosen" else ""
            addView(android.widget.ImageView(this@showIconPicker).apply {
                setImageDrawable(androidx.core.content.ContextCompat.getDrawable(this@showIconPicker, icon.icon))
                // The launcher's own shape: a rounded square here, as most phones show it.
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, v.width * 0.28f) }
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(48f).toInt(), dp(48f).toInt()))
            addView(LinearLayout(this@showIconPicker).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14f).toInt(), 0, 0, 0)
                addView(TextView(this@showIconPicker).apply { text = icon.name; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextPrimary)) })
                addView(TextView(this@showIconPicker).apply { text = icon.note; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (icon == current) addView(android.widget.ImageView(this@showIconPicker).apply {
                setImageResource(R.drawable.ic_check); imageTintList = ColorStateList.valueOf(themeColor(R.attr.obdAccent))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24f).toInt(), dp(24f).toInt()))
            setOnClickListener {
                sheet.dismiss()
                if (icon == current) return@setOnClickListener
                android.util.Log.i("OBD2Icon", "app icon: ${icon.id}")
                toast("App icon changed to ${icon.name}. Your home screen can take a few seconds to show it.")
                AppIcons.choose(this@showIconPicker, icon)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f).toInt() })
    }
    sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
    sheet.show()
}
