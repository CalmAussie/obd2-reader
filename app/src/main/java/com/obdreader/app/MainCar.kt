package com.obdreader.app

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.obdreader.app.MainActivity.AllItemsAdapter
import com.obdreader.app.MainActivity.Companion.PREF_CAR_ASKED
import com.obdreader.app.MainActivity.Companion.PREF_CAR_ENGINE
import com.obdreader.app.MainActivity.Companion.PREF_CAR_MAKE
import com.obdreader.app.MainActivity.Companion.PREF_CAR_MODEL
import com.obdreader.app.MainActivity.Companion.PREF_CAR_YEAR
import com.obdreader.app.MainActivity.Companion.PREF_LAST_PROTOCOL
import com.obdreader.app.MainActivity.Companion.PREF_PROTOCOL
import com.obdreader.app.obd.CarSupport
import com.obdreader.app.obd.DtcLibrary
import com.obdreader.app.obd.ObdProtocols
import com.obdreader.app.obd.VehicleCatalog
import com.obdreader.app.session.DriveScorer
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.DashLayout
import com.obdreader.app.ui.MilBannerView
import com.obdreader.app.ui.PlateView
import com.obdreader.app.ui.panelBackground
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.uiTypeface
import java.util.Locale

// Your car, the protocol setting and the check-engine rows.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

// ---------- Your car ----------

internal fun MainActivity.savedCar(): VehicleCatalog.Car? {
    val make = prefs.getString(PREF_CAR_MAKE, null) ?: return null
    val year = prefs.getInt(PREF_CAR_YEAR, 0).takeIf { it > 0 } ?: return null
    return VehicleCatalog.Car(make, year, prefs.getString(PREF_CAR_MODEL, null), prefs.getString(PREF_CAR_ENGINE, null))
}
/** Scales the RPM dial to the chosen engine's redline, or the default 8,000 / 6,500 rpm. */
internal fun MainActivity.applyRedline() {
    val redline = savedCar()?.let { VehicleCatalog.recommend(it).redline }
    val max = VehicleCatalog.dialMax(redline).toFloat()
    this.redline = (redline ?: 6500).toFloat()
    DriveScorer.setRedline(this.redline)
    roundel.max = max
    roundel.warnAt = this.redline
    cockpitRing.rpmMax = max
    cockpitRing.warnAt = this.redline
    cyberHud.rpmMax = max
    cyberHud.warnAt = this.redline
    retroDash.rpmMax = max; retroDash.warnAt = this.redline
    rallyRev.max = max; rallyRev.warnAt = this.redline
    hairlineDial.rpmMax = max; hairlineDial.warnAt = this.redline
    trace.rpmMax = max
}
internal fun MainActivity.updateCarSetting() {
    val car = savedCar()
    carSetting.text = car?.label ?: getString(R.string.car_not_set)
    findViewById<View>(R.id.carExpect).visibility = if (car != null && CarSupport.expectations(car).isNotEmpty()) View.VISIBLE else View.GONE
}
/** No car chosen yet, and the user hasn't said theirs isn't listed. */
internal fun MainActivity.needsCar(): Boolean = savedCar() == null && !prefs.getBoolean(PREF_CAR_ASKED, false)
internal fun MainActivity.showCarPicker(firstRun: Boolean = false, prefill: VehicleCatalog.Car? = null, onDone: (() -> Unit)? = null) {
    val sheet = BottomSheetDialog(this)
    carPickerShowing = true
    sheet.setOnDismissListener { carPickerShowing = false }
    val view = layoutInflater.inflate(R.layout.sheet_car, null)
    view.applyTitleFonts()
    if (firstRun) {
        view.findViewById<TextView>(R.id.carTitle).setText(R.string.car_first_title)
        view.findViewById<TextView>(R.id.carSubtitle).setText(R.string.car_first_subtitle)
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.carClear).setText(R.string.car_skip)
        sheet.setCanceledOnTouchOutside(false)
    }
    val makeInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.makeInput)
    val yearInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.yearInput)
    val modelInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.modelInput)
    val protocolView = view.findViewById<TextView>(R.id.carProtocol)
    val noteView = view.findViewById<TextView>(R.id.carNote)
    val engineInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.engineInput)
    val engineLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.engineLayout)
    val redlineView = view.findViewById<TextView>(R.id.carRedline)

    // Filled in from the VIN when the user asked for that: nothing is saved until they tap Save.
    val current = prefill ?: savedCar()
    if (prefill != null) view.findViewById<TextView>(R.id.carSubtitle).setText(R.string.car_from_vin)
    var make = current?.make ?: VehicleCatalog.MAKES.first().name
    var year = current?.year ?: 2010

    makeInput.setSimpleItems(VehicleCatalog.makeNames.toTypedArray())
    yearInput.setSimpleItems((VehicleCatalog.LAST_YEAR downTo VehicleCatalog.FIRST_YEAR).map { it.toString() }.toTypedArray())
    makeInput.setText(make, false)
    yearInput.setText(year.toString(), false)
    modelInput.setText(current?.model.orEmpty())
    engineInput.setText(current?.engine.orEmpty())

    fun carNow() = VehicleCatalog.Car(
        make, year,
        modelInput.text?.toString()?.trim()?.ifEmpty { null },
        engineInput.text?.toString()?.trim()?.ifEmpty { null },
    )

    fun refreshEngines() {
        val engines = VehicleCatalog.engines(make, year, modelInput.text?.toString()).map { it.name }
        // Always lists every engine for this model and year, whatever is typed (a plain adapter
        // would only show the ones matching the text already in the box).
        engineInput.setAdapter(AllItemsAdapter(this, engines))
        engineLayout.endIconMode = if (engines.isEmpty()) com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
            else com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU
        val model = modelInput.text?.toString()?.trim().orEmpty()
        val electric = model.isNotEmpty() && VehicleCatalog.isElectric(VehicleCatalog.Car(make, year, model), model.lowercase(Locale.US))
        engineLayout.helperText = getString(when {
            engines.isNotEmpty() -> R.string.car_engine_help
            electric -> R.string.car_engine_electric
            else -> R.string.car_engine_none
        })
    }

    fun refreshModels() {
        val names = VehicleCatalog.models(make, year).map { it.name }
        modelInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, names))
    }

    fun refreshResult() {
        val rec = VehicleCatalog.recommend(carNow())
        protocolView.text = rec.likely?.name ?: "Automatic search"
        noteView.text = rec.note.orEmpty()
        noteView.visibility = if (rec.note == null) View.GONE else View.VISIBLE
        redlineView.text = rec.redline?.let {
            val r = String.format(Locale.US, "%,d", it)
            val dial = "${rec.dialMax / 1000}k"
            if (rec.redlineExact) "RPM dial redline set to $r rpm for this engine (dial to $dial)."
            else "RPM dial redline set to $r rpm, the highest for this model (dial to $dial). Choose your engine for its own redline."
        }.orEmpty()
        redlineView.visibility = if (rec.redline == null) View.GONE else View.VISIBLE
    }

    makeInput.setOnItemClickListener { parent, _, position, _ ->
        make = parent.getItemAtPosition(position) as String
        modelInput.setText("")
        engineInput.setText("")
        refreshModels()
        refreshEngines()
        refreshResult()
    }
    yearInput.setOnItemClickListener { parent, _, position, _ ->
        year = (parent.getItemAtPosition(position) as String).toInt()
        refreshModels()
        refreshEngines()
        refreshResult()
    }
    modelInput.doAfterTextChanged {
        refreshEngines()
        refreshResult()
    }
    engineInput.doAfterTextChanged { refreshResult() }
    // Open the list on the first tap, like the Manufacturer and Year boxes.
    engineInput.setOnClickListener { if ((engineInput.adapter?.count ?: 0) > 0) engineInput.showDropDown() }
    engineInput.setOnFocusChangeListener { _, focused -> if (focused && (engineInput.adapter?.count ?: 0) > 0) engineInput.post { engineInput.showDropDown() } }
    modelInput.setOnClickListener { if (modelInput.adapter?.count ?: 0 > 0) modelInput.showDropDown() }
    refreshModels()
    refreshEngines()
    refreshResult()

    view.findViewById<View>(R.id.carSave).setOnClickListener {
        val car = carNow()
        prefs.edit()
            .putString(PREF_CAR_MAKE, car.make)
            .putInt(PREF_CAR_YEAR, car.year)
            .putString(PREF_CAR_MODEL, car.model)
            .putString(PREF_CAR_ENGINE, car.engine)
            .remove(PREF_LAST_PROTOCOL) // the remembered protocol may belong to a different car
            .apply()
        prefs.edit().putBoolean(PREF_CAR_ASKED, true).apply()
        updateCarSetting()
        applyRedline()
        sheet.dismiss()
        if (lastCodes != null) lastCodes?.let { renderCodes(it) } // re-explain codes for the new make
        showVin(ObdSession.state.value.vin) // an entered VIN belongs to the car it was entered for
        val after = {
            when {
                onDone != null -> onDone()
                firstRun -> toast("Saved. Tap Connect when you're in the car, or try the demo car.")
                else -> toast(if (ObdSession.client != null) "Saved. Disconnect and connect again to use it." else "Saved. Tap Connect to try again.")
            }
        }
        // What may not work with this car, before anything else happens.
        if (!showExpectations(car, after)) after()
    }
    view.findViewById<View>(R.id.carClear).setOnClickListener {
        prefs.edit().remove(PREF_CAR_MAKE).remove(PREF_CAR_YEAR).remove(PREF_CAR_MODEL).remove(PREF_CAR_ENGINE)
            .putBoolean(PREF_CAR_ASKED, true).apply()
        updateCarSetting()
        applyRedline()
        sheet.dismiss()
        onDone?.invoke()
    }

    sheet.setContentView(view)
    sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    sheet.behavior.skipCollapsed = true
    sheet.show()
}
/**
 * A popup listing the readings and features that may not work with [car], each with why.
 * Returns false (and shows nothing) when there's nothing to warn about. [onClose] runs when
 * it's closed.
 */
internal fun MainActivity.showExpectations(car: VehicleCatalog.Car, onClose: (() -> Unit)? = null): Boolean {
    val items = CarSupport.expectations(car)
    if (items.isEmpty()) return false
    val pad = dp(24f).toInt()
    val list = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, dp(4f).toInt(), pad, dp(8f).toInt())
    }
    list.addView(TextView(this).apply {
        text = "Based on what cars like yours usually report. When you connect, the app asks your car and shows everything it does report."
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(themeColor(R.attr.obdTextSecondary))
    })
    items.forEach { item ->
        val colour = when (item.level) {
            CarSupport.Level.UNLIKELY -> themeColor(R.attr.obdDangerText)
            CarSupport.Level.MAYBE -> themeColor(R.attr.obdWarnText)
            CarSupport.Level.LIMITED -> themeColor(R.attr.obdTextSecondary)
        }
        list.addView(TextView(this).apply {
            text = item.name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = uiTypeface(600)
            setTextColor(themeColor(R.attr.obdTextPrimary))
            setPadding(0, dp(18f).toInt(), 0, 0)
        })
        list.addView(TextView(this).apply {
            text = item.level.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = uiTypeface(600)
            setTextColor(colour)
            setPadding(0, dp(2f).toInt(), 0, dp(2f).toInt())
        })
        list.addView(TextView(this).apply {
            text = item.reason
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setLineSpacing(0f, 1.15f)
        })
    }
    MaterialAlertDialogBuilder(this)
        .setTitle("What may not work with your ${car.label}")
        .setView(android.widget.ScrollView(this).apply { addView(list) })
        .setPositiveButton("Got it", null)
        .setOnDismissListener { onClose?.invoke() }
        .show()
    return true
}
/** The protocol setting, and while connected on Automatic, the protocol it found: one name everywhere. */
internal fun MainActivity.updateProtocolSetting() {
    val chosen = prefs.getString(PREF_PROTOCOL, ObdProtocols.AUTO)
    val forced = ObdProtocols.byCode(chosen)
    val inUse = ObdSession.state.value.takeIf { it.isConnected }?.protocolCode?.let { ObdProtocols.byCode(it) }
    protocolSetting.text = when {
        forced != null -> forced.name
        inUse != null -> getString(R.string.protocol_auto_using, inUse.name)
        else -> getString(R.string.protocol_auto)
    }
}
internal fun MainActivity.showProtocolPicker() {
    val options = listOf<Pair<String, String>>(ObdProtocols.AUTO to getString(R.string.protocol_auto_detail)) +
        ObdProtocols.ALL.map { it.code to "${it.name}\n${it.hint}" }
    val current = prefs.getString(PREF_PROTOCOL, ObdProtocols.AUTO)
    val checked = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
    choiceSheet(getString(R.string.protocol)) { body, sheet ->
        sheetOptions(body, options.map { it.second }, checked) { which ->
            prefs.edit().putString(PREF_PROTOCOL, options[which].first).apply()
            updateProtocolSetting()
            sheet.dismiss()
            if (ObdSession.client != null) toast("Disconnect and connect again to use the new protocol")
        }
    }
}
/** The check-engine banner: [state] ON turns it amber, OK marks it green. */
internal fun MainActivity.setDtcStatus(title: String, sub: String = "", state: MilBannerView.State = MilBannerView.State.NONE) {
    milBanner.state = state
    val ink = milBanner.inkColor(themeColor(R.attr.obdTextPrimary))
    val inkSub = milBanner.inkColor(themeColor(R.attr.obdTextSecondary))
    dtcStatus.text = title
    dtcStatus.setTextColor(ink)
    dtcSub.text = sub
    dtcSub.setTextColor(inkSub)
    dtcSub.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
    val lamp = findViewById<ImageView>(R.id.milLamp)
    lamp.imageTintList = ColorStateList.valueOf(
        when (state) {
            MilBannerView.State.ON -> if (dash == DashLayout.COCKPIT) themeColor(R.attr.obdWarn) else ink
            MilBannerView.State.OK -> themeColor(R.attr.obdOk)
            MilBannerView.State.NONE -> themeColor(R.attr.obdTextSecondary)
        },
    )
}
/**
 * One trouble code: its plate and name, then (tap to open) how urgent it is, what it means,
 * what you might notice, common causes and what to check first.
 */
internal fun MainActivity.addDtcRow(code: String, pending: Boolean) {
    val info = DtcLibrary.lookup(code, ObdSession.carMake(this), ObdSession.carYear(this), ObdSession.carModel(this), ObdSession.carEngine(this))
    val pad = dp(14f).toInt()
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = panelBackground()
        setPadding(pad, pad, pad, pad)
        isClickable = true
        isFocusable = true
    }
    val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val names = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14f).toInt(), 0, dp(6f).toInt(), 0)
        addView(TextView(context).apply {
            text = info.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = uiTypeface(500)
            setTextColor(context.themeColor(R.attr.obdTextPrimary))
        })
        addView(TextView(context).apply {
            text = (if (pending) "Pending, not yet confirmed" else "Stored, check-engine light on") + ". " + info.severity.label + "."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(context.themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(2f).toInt(), 0, 0)
        })
    }
    val chevron = ImageView(this).apply {
        setImageResource(R.drawable.ic_chevron_right)
        imageTintList = ColorStateList.valueOf(themeColor(R.attr.obdTextSecondary))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    head.addView(PlateView(this, code, stored = !pending))
    head.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    head.addView(chevron, LinearLayout.LayoutParams(dp(22f).toInt(), dp(22f).toInt()))
    card.addView(head)

    val details = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(12f).toInt(), 0, 0)
        visibility = View.GONE
    }
    val severityColour = when (info.severity) {
        DtcLibrary.Severity.HIGH -> themeColor(R.attr.obdDanger)
        DtcLibrary.Severity.MEDIUM -> themeColor(R.attr.obdWarn)
        DtcLibrary.Severity.LOW -> themeColor(R.attr.obdOk)
        DtcLibrary.Severity.UNKNOWN -> themeColor(R.attr.obdTextSecondary)
    }
    details.addView(TextView(this).apply {
        text = info.severity.label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = uiTypeface(600)
        // Amber text is darker on the light styles; the tag's tint stays the plain status colour.
        setTextColor(when (info.severity) { DtcLibrary.Severity.MEDIUM -> themeColor(R.attr.obdWarnText); DtcLibrary.Severity.HIGH -> themeColor(R.attr.obdDangerText); DtcLibrary.Severity.LOW -> themeColor(R.attr.obdOkText); else -> severityColour })
        background = GradientDrawable().apply {
            cornerRadius = dp(4f)
            setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(severityColour, 40))
        }
        setPadding(dp(10f).toInt(), dp(4f).toInt(), dp(10f).toInt(), dp(4f).toInt())
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    fun para(text: String, secondary: Boolean = true) = details.addView(TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setLineSpacing(0f, 1.15f)
        setTextColor(themeColor(if (secondary) R.attr.obdTextSecondary else R.attr.obdTextPrimary))
        setPadding(0, dp(6f).toInt(), 0, 0)
    })
    fun heading(text: String) = details.addView(TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = uiTypeface(600)
        setTextColor(themeColor(R.attr.obdTextPrimary))
        setPadding(0, dp(14f).toInt(), 0, 0)
    })
    para(info.severity.advice)
    heading("What it means")
    para(info.meaning, secondary = false)
    if (info.symptoms.isNotEmpty()) { heading("You might notice"); para(info.symptoms.joinToString("\n") { "•  $it" }) }
    if (info.causes.isNotEmpty()) { heading("Common causes"); para(info.causes.joinToString("\n") { "•  $it" }) }
    if (info.checks.isNotEmpty()) { heading("What to check"); para(info.checks.mapIndexed { i, c -> "${i + 1}.  $c" }.joinToString("\n")) }
    // The engine's snapshot from the moment this code was set, if the car kept one for it.
    lastCodes?.freezeFrame?.takeIf { it.code == code && !pending }?.let { frame ->
        heading("When it was set (freeze frame)")
        para(frame.lines().joinToString("\n"), secondary = false)
        para("The car's own snapshot of the engine when this code was stored. Show it to your mechanic: it says what the engine was doing when the fault happened.")
    }
    if (info.system.isNotEmpty()) para("$code: ${info.system}.")
    card.addView(details)

    fun setOpen(open: Boolean) {
        details.visibility = if (open) View.VISIBLE else View.GONE
        chevron.rotation = if (open) 90f else 0f
        card.contentDescription = "$code, ${info.title}. ${if (open) "Tap to hide details" else "Tap for details"}"
    }
    setOpen(dtcContainer.childCount == 0) // the first code opens straight away
    card.setOnClickListener { setOpen(details.visibility != View.VISIBLE) }
    dtcContainer.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(8f).toInt()
    })
}
