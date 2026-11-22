package com.obdreader.app

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.obdreader.app.MainActivity.Companion.PREF_CAR_ENGINE
import com.obdreader.app.MainActivity.Companion.PREF_CAR_MAKE
import com.obdreader.app.MainActivity.Companion.PREF_CAR_MODEL
import com.obdreader.app.MainActivity.Companion.PREF_CAR_YEAR
import com.obdreader.app.MainActivity.Companion.PREF_LAST_PROTOCOL
import com.obdreader.app.MainActivity.Companion.VIN_DATA
import com.obdreader.app.obd.VehicleCatalog
import com.obdreader.app.obd.VinDecoder
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.MilBannerView
import com.obdreader.app.ui.VinDecalView
import com.obdreader.app.ui.panelBackground
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.themeDimen
import com.obdreader.app.ui.uiTypeface
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Trouble codes, battery checks and the VIN.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

// ---------- Diagnostics ----------

internal fun MainActivity.readDtcs() {
    if (ObdSession.client == null) return
    lifecycleScope.launch {
        dtcContainer.removeAllViews()
        setDtcStatus("Reading trouble codes…", "Asking the engine computer for stored and pending codes.")
        try {
            renderCodes(ObdSession.readCodes())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setDtcStatus("Couldn't read codes", e.message ?: "The adapter didn't answer.")
        }
    }
}
internal fun MainActivity.renderCodes(codes: ObdSession.Codes) {
    lastCodes = codes // the code cards read the freeze frame from here
    dtcContainer.removeAllViews()
    setCodeBadge(codes.total)
    roundel.checkEngine = codes.stored.isNotEmpty()
    if (codes.total == 0) {
        setDtcStatus("No trouble codes", "The engine computer has no stored or pending faults.", MilBannerView.State.OK)
    } else {
        setDtcStatus(
            if (codes.total == 1) "1 trouble code" else "${codes.total} trouble codes",
            if (codes.stored.isNotEmpty()) "Stored codes are keeping the check-engine light on." else "Pending codes only, so the light isn't on yet.",
            if (codes.stored.isNotEmpty()) MilBannerView.State.ON else MilBannerView.State.NONE,
        )
        codes.stored.forEach { addDtcRow(it, pending = false) }
        codes.pending.forEach { addDtcRow(it, pending = true) }
    }
    codes.permanent?.takeIf { it.isNotEmpty() }?.let { p ->
        dtcContainer.addView(TextView(this).apply {
            text = getString(R.string.permanent_codes, p.joinToString(", "))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.15f); setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(10f).toInt(), 0, 0)
        })
    }
}
internal fun MainActivity.readVin() {
    if (ObdSession.client == null) return
    lifecycleScope.launch {
        vinText.text = "Reading…"
        try {
            showVin(ObdSession.readVin())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            vinText.text = "Couldn't read it"
        }
    }
}
/** The latest battery check and the ones before it, from the phone. [fresh]: it was just measured. */
internal fun MainActivity.showBatteryCheck(fresh: com.obdreader.app.obd.BatteryCheck.Result? = null) {
    val kept = com.obdreader.app.obd.BatteryCheck.parseHistory(prefs.getString(AppPrefs.BATTERY_CHECKS, null))
    // A demo car's check isn't kept, but it's still shown while that demo runs.
    val history = (listOfNotNull(fresh ?: shownBatteryCheck) + kept).distinctBy { it.atMs }.sortedByDescending { it.atMs }
    val latest = history.firstOrNull()
    val rating = findViewById<TextView>(R.id.batteryRating)
    val text = findViewById<TextView>(R.id.batteryText)
    val past = findViewById<TextView>(R.id.batteryHistory)
    findViewById<View>(R.id.batteryCard).background = panelBackground()
    showBatteryNight()
    if (latest == null) {
        rating.setText(R.string.battery_none); rating.setTextColor(themeColor(R.attr.obdTextPrimary))
        text.setText(R.string.battery_how); past.visibility = View.GONE
        return
    }
    val when_ = java.text.SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(java.util.Date(latest.atMs))
    rating.text = latest.rating.label
    rating.setTextColor(themeColor(when (latest.rating) {
        com.obdreader.app.obd.BatteryCheck.Rating.GOOD -> R.attr.obdTextPrimary
        else -> R.attr.obdWarn
    }))
    text.text = "${latest.summary()}, $when_. ${latest.rating.advice}"
    val older = history.drop(1).take(5)
    past.text = if (older.isEmpty()) "" else "Before: " + older.joinToString(" · ") { String.format(Locale.US, "%.1f V", it.crankingV) }
    past.visibility = if (older.isEmpty()) View.GONE else View.VISIBLE
    if (fresh != null && fresh != shownBatteryCheck) {
        toast("Battery check: ${fresh.rating.label.lowercase(Locale.getDefault())}, ${String.format(Locale.US, "%.1f V", fresh.crankingV)} while starting")
    }
    shownBatteryCheck = fresh ?: shownBatteryCheck
}
/** The switch for the overnight check, and what the last night showed. */
internal fun MainActivity.showBatteryNight() {
    val sw = findViewById<MaterialSwitch>(R.id.batteryWatchSwitch)
    sw.setOnCheckedChangeListener(null)
    sw.isChecked = AppPrefs.batteryWatch(prefs)
    sw.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(AppPrefs.BATTERY_WATCH, on).apply(); showBatteryNight() }
    val night = findViewById<TextView>(R.id.batteryNight)
    val samples = com.obdreader.app.obd.BatteryRest.decode(prefs.getString(AppPrefs.BATTERY_NIGHT, null))
    val off = prefs.getLong(AppPrefs.BATTERY_NIGHT_OFF, 0L)
    val drain = com.obdreader.app.obd.BatteryRest.drain(samples, off)
    night.text = when {
        drain != null -> "Last time the car was off: " + drain.summary()
        samples.isNotEmpty() -> "Watching: ${samples.size} readings so far, latest ${com.obdreader.app.obd.BatteryRest.describe(samples.last().volts)}. A result needs about 5 hours with the car off."
        else -> ""
    }
    night.setTextColor(themeColor(if (drain?.draining == true) R.attr.obdWarn else R.attr.obdTextPrimary))
    night.visibility = if (night.text.isNullOrEmpty()) View.GONE else View.VISIBLE
}
/** Reads the VIN's details from the data inside the app, away from the screen's thread. The VIN isn't sent anywhere. */
internal fun MainActivity.loadVinDetails(vin: String) {
    if (vinDetailsLoading == vin) return
    vinDetailsLoading = vin
    lifecycleScope.launch {
        val started = android.os.SystemClock.elapsedRealtime()
        val d = withContext(Dispatchers.IO) {
            try {
                com.obdreader.app.obd.VinDetails.decode(vin, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) { assets.open(VIN_DATA) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Never the VIN itself in the log: only what went wrong.
                android.util.Log.w("OBD2Vin", "VIN details couldn't be read: ${e.javaClass.simpleName}: ${e.message?.take(80)}")
                null
            }
        }
        if (vinDetailsLoading == vin) vinDetailsLoading = null
        vinDetailsVin = vin; vinDetails = d
        android.util.Log.i("OBD2Vin", "VIN details: ${if (d == null) "maker not in the data" else "${d.values.size} found"} in ${android.os.SystemClock.elapsedRealtime() - started} ms")
        showVin(shownCarVin)
    }
}
internal fun MainActivity.showVin(carVin: String?) {
    shownCarVin = carVin
    val userVin = AppPrefs.userVin(prefs)
    val vin = userVin ?: carVin
    val checked = vin == null || vin.length < 17 || VinDecoder.isValid(vin)
    val setting = vinMode()
    val revealed = vin != null && vin == vinRevealed && setting != com.obdreader.app.obd.VinPrivacy.Mode.FULL
    val mode = if (revealed) com.obdreader.app.obd.VinPrivacy.Mode.FULL else setting
    vinText.text = when {
        vin == null -> "Not reported"
        mode == com.obdreader.app.obd.VinPrivacy.Mode.HIDDEN -> getString(R.string.vin_hidden)
        else -> com.obdreader.app.obd.VinPrivacy.mask(vin, mode)
    }
    showVinTap(if (userVin != null) R.string.vin_entered else R.string.vin, vin != null && setting != com.obdreader.app.obd.VinPrivacy.Mode.FULL, revealed)
    findViewById<MaterialButton>(R.id.vinEnter).apply {
        setText(if (userVin != null) R.string.vin_change else R.string.vin_enter)
        setOnClickListener { askForVin(carVin) }
    }
    val note = findViewById<TextView>(R.id.vinNote)
    note.text = when {
        userVin != null && carVin != null && carVin != userVin ->
            "Your car's engine computer reports ${if (mode == com.obdreader.app.obd.VinPrivacy.Mode.HIDDEN) "a different VIN" else com.obdreader.app.obd.VinPrivacy.mask(carVin, mode)}, which doesn't match your VIN plate. That happens on some cars: the VIN stored in the computer can have a typo from when it was programmed, or come from another car if the computer was replaced. It doesn't affect live data or trouble codes."
        userVin == null && carVin != null && !checked ->
            "This VIN fails its check digit, so it may not be your car's. If it doesn't match your VIN plate, enter the right one below."
        else -> null
    }
    note.visibility = if (note.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    val info = vin?.let { VinDecoder.decode(it) }
    // The demo car's made-up VIN isn't looked up: it would describe some other car.
    val lookUp = checked && vin != null && vin.length == 17 && (userVin != null || !ObdSession.state.value.isDemo)
    val details = vinDetails.takeIf { lookUp && vin == vinDetailsVin }
    if (lookUp && vin != vinDetailsVin) loadVinDetails(vin!!)
    showVinMismatch(info, details)
    val detailsButton = findViewById<MaterialButton>(R.id.vinDetailsButton)
    detailsButton.visibility = if (details != null) View.VISIBLE else View.GONE
    detailsButton.setOnClickListener { details?.let { showVinDetails(it) } }
    vehicleText.text = when {
        !checked -> "Make and year not taken from this VIN"
        details?.model != null && details.make != null -> details.title
        info?.make != null && info.modelYear != null -> "${info.make}, ${info.modelYear} model year"
        info?.make != null -> info.make
        vin != null -> "Make not recognised from VIN"
        savedCar() != null -> "Your car: ${savedCar()?.label}. It doesn't report a VIN."
        else -> "Older cars often don't report a VIN. Live data and codes still work."
    }
}
/**
 * With Hide VIN on, a tap on the VIN card shows the whole VIN on screen, and another tap hides
 * it again; the label says which. With Hide VIN off it stays selectable text, as before.
 */
internal fun MainActivity.showVinTap(label: Int, canReveal: Boolean, revealed: Boolean) {
    val decal = findViewById<VinDecalView>(R.id.vinDecal)
    findViewById<TextView>(R.id.vinLabel).text = when {
        !canReveal -> getString(label)
        revealed -> getString(R.string.vin_tap_hide, getString(label))
        else -> getString(R.string.vin_tap_show, getString(label))
    }
    // Selectable text takes the taps for itself, so it's only selectable when there's nothing to reveal.
    vinText.setTextIsSelectable(!canReveal)
    // A press ripple in the decal's ink colour, the shape of the card, so it reads as a button.
    decal.foreground = if (!canReveal) null else decal.foreground ?: android.graphics.drawable.RippleDrawable(
        ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(decal.inkColor, 48)), null,
        GradientDrawable().apply { setColor(android.graphics.Color.WHITE); cornerRadius = themeDimen(R.attr.obdCardRadius) })
    if (canReveal) {
        decal.setOnClickListener {
            val vin = AppPrefs.userVin(prefs) ?: shownCarVin ?: return@setOnClickListener
            vinRevealed = if (vinRevealed == vin) null else vin
            showVin(shownCarVin)
        }
        androidx.core.view.ViewCompat.replaceAccessibilityAction(decal, androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            getString(if (revealed) R.string.vin_hide_action else R.string.vin_show_action), null)
    } else {
        decal.setOnClickListener(null)
        decal.isClickable = false
        androidx.core.view.ViewCompat.replaceAccessibilityAction(decal, androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK, null, null)
    }
}
/** Lets the user type their VIN from the plate, or go back to the one the car sends. */
internal fun MainActivity.askForVin(carVin: String?) {
    val input = com.google.android.material.textfield.TextInputEditText(this).apply {
        // Filled in only when Hide VIN lets the whole VIN show.
        setText(if (vinMode() == com.obdreader.app.obd.VinPrivacy.Mode.FULL || vinRevealed != null) AppPrefs.userVin(prefs) ?: carVin ?: "" else "")
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        filters = arrayOf(android.text.InputFilter.AllCaps(), android.text.InputFilter.LengthFilter(20))
        typeface = uiTypeface(500)
        setSelection(text?.length ?: 0)
    }
    val box = com.google.android.material.textfield.TextInputLayout(this).apply {
        setPadding(dp(20f).toInt(), dp(8f).toInt(), dp(20f).toInt(), 0)
        addView(input)
    }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(R.string.vin_dialog_title)
        .setMessage(R.string.vin_dialog_text)
        .setView(box)
        .setPositiveButton(R.string.save, null)
        .setNegativeButton(android.R.string.cancel, null)
        .apply { if (AppPrefs.userVin(prefs) != null) setNeutralButton(R.string.vin_use_car) { _, _ -> AppPrefs.setUserVin(prefs, null); showVin(ObdSession.state.value.vin) } }
        .show()
    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val vin = VinDecoder.normalise(input.text?.toString().orEmpty())
        if (vin == null) { box.error = getString(R.string.vin_dialog_bad); return@setOnClickListener }
        AppPrefs.setUserVin(prefs, if (vin == ObdSession.state.value.vin) null else vin)
        dialog.dismiss()
        showVin(ObdSession.state.value.vin)
    }
}
/**
 * The VIN is the car's own word on what it is. If it disagrees with the car the user chose,
 * say so and offer to switch, because the chosen car decides how manufacturer codes are explained.
 * With no car chosen, offer the VIN's car. Either way the car choice opens filled in, and
 * nothing changes until the user taps Save.
 */
internal fun MainActivity.showVinMismatch(info: VinDecoder.Info?, details: com.obdreader.app.obd.VinDetails.Details? = null) {
    val note = findViewById<TextView>(R.id.vinMismatch)
    val fix = findViewById<MaterialButton>(R.id.vinMismatchFix)
    val car = savedCar()
    val vinMake = details?.make ?: info?.make
    val vinYear = details?.modelYear ?: info?.modelYear
    val suggestion = details?.let { com.obdreader.app.obd.VinDetails.suggestedCar(it) }
    val makeDiffers = car != null && vinMake != null && !VinDecoder.sameMake(vinMake, car.make)
    val yearDiffers = car != null && vinMake != null && !makeDiffers && vinYear != null && kotlin.math.abs(vinYear - car.year) > 1
    note.visibility = View.GONE; fix.visibility = View.GONE
    if (car == null) {
        if (suggestion != null) {
            fix.text = "Use the ${suggestion.label} as my car"
            fix.visibility = View.VISIBLE
            fix.setOnClickListener { showCarPicker(prefill = suggestion) }
        }
        return
    }
    if (!makeDiffers && !yearDiffers) return
    val fromVin = suggestion?.label ?: listOfNotNull(vinYear?.toString(), vinMake?.substringBefore(" (")).joinToString(" ")
    note.text = "This car's VIN says it's a $fromVin, but your car is set to ${car.label}. Fault codes are explained for the car you choose."
    note.visibility = View.VISIBLE
    fix.text = "Set my car to $fromVin"
    fix.visibility = View.VISIBLE
    fix.setOnClickListener {
        if (suggestion != null) { showCarPicker(prefill = suggestion); return@setOnClickListener }
        val make = VehicleCatalog.makeNames.firstOrNull { VinDecoder.sameMake(vinMake!!, it) } ?: vinMake!!.substringBefore(" (")
        prefs.edit().putString(PREF_CAR_MAKE, make).putInt(PREF_CAR_YEAR, vinYear ?: car.year)
            .remove(PREF_CAR_MODEL).remove(PREF_CAR_ENGINE).remove(PREF_LAST_PROTOCOL).apply()
        updateCarSetting(); applyRedline()
        showVinMismatch(info, details)
        lastCodes?.let { renderCodes(it) }
        toast("Your car is now set to $make. Add the model under Your car if you like.")
    }
}
/**
 * Everything the VIN says about the car, from NHTSA's vPIC data inside the app. Whatever the
 * data doesn't cover shows N/A. Offers the car choice filled in from it.
 */
internal fun MainActivity.showVinDetails(d: com.obdreader.app.obd.VinDetails.Details) {
    val na = getString(R.string.na)
    infoSheet(getString(R.string.vin_details_title)) { heading, line, body ->
        line(d.title.ifEmpty { na }, false)
        fun row(name: String, value: String?) = line("$name: ${value ?: na}", value == null)
        heading("The car")
        row("Make", d.make); row("Model", d.model); row("Model year", d.modelYear?.toString())
        row("Trim", d.trim); row("Body", d.body); row("Vehicle type", d.vehicleTypeName)
        heading("Engine and drive")
        row("Engine", d.engine); row("Engine code", d.engineCode); row("Fuel", d.fuel)
        row("Drive", d.drive); row("Transmission", d.transmission)
        heading("Where it was built")
        row("Built in", d.plant); row("Made by", d.manufacturer?.let { makerName(it) })
        val suggestion = com.obdreader.app.obd.VinDetails.suggestedCar(d)
        val car = savedCar()
        if (suggestion != null && (car == null || car.make != suggestion.make || car.year != suggestion.year ||
                !car.model.equals(suggestion.model, true) || (suggestion.engine != null && !car.engine.equals(suggestion.engine, true)))) {
            sheetStackedButtons(body, listOf(getString(R.string.vin_details_use) to {
                currentSheet?.dismiss()
                showCarPicker(prefill = suggestion)
            }))
        }
        // A footnote, set apart from the button above it.
        body.addView(TextView(this).apply {
            text = getString(R.string.vin_details_source); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.15f)
            setTextColor(themeColor(R.attr.obdTextSecondary)); setPadding(0, dp(16f).toInt(), 0, 0)
        })
    }
}
/** vPIC writes makers in capitals ("FORD MOTOR COMPANY"); shown as "Ford Motor Company", keeping LLC, USA and the like. */
internal fun MainActivity.makerName(s: String): String =
    if (s.any { it.isLowerCase() }) s else s.split(' ').joinToString(" ") { w ->
        if (w.trimEnd(',', '.') in setOf("LLC", "USA", "US", "BMW", "AG", "GMBH", "KG", "SA", "SPA", "NV", "BV", "AB", "UK", "GM", "FCA"))
            w.replace("GMBH", "GmbH") else w.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
    }
