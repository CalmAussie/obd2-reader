package com.obdreader.app

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.obdreader.app.MainActivity.Companion.PREF_SLOTS
import com.obdreader.app.MainActivity.Readout
import com.obdreader.app.obd.CarCoverage
import com.obdreader.app.obd.ObdProtocols
import com.obdreader.app.obd.Pids
import com.obdreader.app.obd.Units
import com.obdreader.app.session.Exports
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.titleTypeface
import com.obdreader.app.ui.uiTypeface
import java.util.Locale
import kotlinx.coroutines.launch

// The car scan and the dashboard readout picker.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

internal fun MainActivity.runCarScan() {
    if (!ObdSession.state.value.isConnected) { toast("Connect to your car first."); return }
    val wait = MaterialAlertDialogBuilder(this)
        .setTitle(R.string.scan)
        .setView(com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
            isIndeterminate = true; setPadding(dp(24f).toInt(), dp(16f).toInt(), dp(24f).toInt(), dp(8f).toInt())
        })
        .setMessage("Asking your car. This takes a few seconds.")
        .setCancelable(false)
        .show()
    lifecycleScope.launch {
        val scan = try { ObdSession.scanCar(this@runCarScan) } catch (e: Exception) { null }
        wait.dismiss()
        if (scan == null) { toast("The car stopped answering. Try again."); return@launch }
        showScan(scan)
    }
}
internal fun MainActivity.showScan(scan: ObdSession.Scan) {
    val sheet = BottomSheetDialog(this)
    val pad = dp(20f).toInt()
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
    fun heading(t: String) = body.addView(TextView(this).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdTextPrimary))
        setPadding(0, dp(16f).toInt(), 0, dp(4f).toInt())
    })
    fun line(t: String, secondary: Boolean = true) = body.addView(TextView(this).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.1f)
        setTextColor(themeColor(if (secondary) R.attr.obdTextSecondary else R.attr.obdTextPrimary)); setPadding(0, dp(3f).toInt(), 0, 0)
    })
    body.addView(TextView(this).apply {
        text = "What your car reports"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary))
    })
    val readings = scan.supported.filter { it % 0x20 != 0 }.sorted()
    line("${readings.size} standard readings, by part of the car, with their value now. Ones without a value aren't read by the app yet.")
    // Grouped like the Graphs tabs; each value updates every second while the sheet is open.
    val values = mutableListOf<Pair<Int, TextView>>()
    val hex = { n: Int -> String.format(Locale.US, "%02X", n) }
    for ((group, keys) in com.obdreader.app.obd.ReadingGroups.sort(readings.map(hex))) {
        heading(group.label)
        for (k in keys) {
            val n = k.toInt(16)
            val value = TextView(this).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextPrimary)); gravity = Gravity.END
                // A value in words (fuel system status) wraps rather than squeezing the name.
                maxWidth = (resources.displayMetrics.widthPixels * 0.42f).toInt()
                text = scanValueText(n).orEmpty()
            }
            values += n to value
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3f).toInt(), 0, 0)
                addView(TextView(this@showScan).apply {
                    text = String.format(Locale.US, "%02X  %s", n, ObdSession.state.value.relabels[k]?.let { "$it" } ?: CarCoverage.name(n))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.1f)
                    setTextColor(themeColor(if (CarCoverage.shownByApp(n)) R.attr.obdTextPrimary else R.attr.obdTextSecondary))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10f).toInt() })
            })
        }
    }
    // Every other standard reading, folded away: the ones this car doesn't report.
    val notReported = CarCoverage.named.filter { it % 0x20 != 0 && it !in scan.supported }
    if (notReported.isNotEmpty()) {
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        com.obdreader.app.obd.ReadingGroups.sort(notReported.map(hex)).forEach { (_, keys) ->
            keys.forEach { k -> list.addView(TextView(this).apply {
                text = String.format(Locale.US, "%s  %s", k, CarCoverage.name(k.toInt(16)))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary)); setPadding(0, dp(3f).toInt(), 0, 0)
            }) }
        }
        val title = "Not reported by your car (${notReported.size})"
        body.addView(TextView(this).apply {
            text = "$title  ▸"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdAccentText))
            setPadding(0, dp(16f).toInt(), 0, dp(4f).toInt()); minHeight = dp(48f).toInt(); gravity = Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true; contentDescription = "$title, collapsed"
            setOnClickListener {
                val open = list.visibility != View.VISIBLE
                list.visibility = if (open) View.VISIBLE else View.GONE
                text = "$title  ${if (open) "▾" else "▸"}"; contentDescription = "$title, ${if (open) "expanded" else "collapsed"}"
            }
        })
        body.addView(list)
    }
    val missing = Pids.dashboard.filter { it.number !in scan.supported }
    if (missing.isNotEmpty()) {
        heading("Not reported by your car")
        missing.forEach { p -> line("${p.label}" + (CarCoverage.STAND_INS.firstOrNull { it.forHex == p.hex && it.pid.number in scan.supported }?.let { " (using ${it.note.substringBefore(",")})" } ?: "")) }
    }
    heading("Battery")
    batteryLines(scan).forEach { (t, found) -> line(t, secondary = !found) }
    if (scan.enhanced.isNotEmpty()) {
        heading("Manufacturer readings (experimental)")
        line("Known readings for your make, tried read-only. An answer means your car has it; send this scan to have it added.")
        scan.enhanced.forEach { (e, data) -> line("${if (data != null) "✓" else "·"}  ${e.name} (${e.request})" + (data?.let { ": $it" } ?: ": no answer"), secondary = data == null) }
    }
    body.addView(MaterialButton(this).apply {
        text = "Share scan"
        setOnClickListener {
            val text = buildString {
                appendLine("OBD2 Reader car scan, ${java.text.SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(java.util.Date())}")
                savedCar()?.label?.let { appendLine("Car: $it") }
                Exports.vinLines(this@showScan, ObdSession.state.value.vin).forEach { appendLine(it) }
                appendLine("Protocol: ${ObdProtocols.byCode(ObdSession.state.value.protocolCode)?.name ?: "unknown"}")
                appendLine(); appendLine("Standard readings:")
                readings.forEach { appendLine(String.format(Locale.US, "  %02X  %s", it, CarCoverage.name(it))) }
                appendLine(); appendLine("Battery:")
                batteryLines(scan).forEach { (t, _) -> appendLine("  $t") }
                if (scan.enhanced.isNotEmpty()) {
                    appendLine(); appendLine("Manufacturer readings:")
                    scan.enhanced.forEach { (e, d) -> appendLine("  ${e.request} ${e.name} [${e.header ?: "default"}]: ${d ?: "no answer"}") }
                }
            }
            val f = Exports.exportFile(this@showScan, "OBD2Reader_car_scan.txt").apply { writeText(text) }
            Exports.share(this@showScan, f, "text/plain", "OBD2 Reader car scan")
        }
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f).toInt() })
    body.applyTitleFonts()
    sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
    sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    sheet.behavior.skipCollapsed = true
    val ticker = object : Runnable {
        override fun run() {
            // Only values that changed, so the sheet stays quiet for screen readers.
            values.forEach { (n, v) -> scanValueText(n).orEmpty().let { t -> if (v.text.toString() != t) v.text = t } }
            body.postDelayed(this, 1000)
        }
    }
    body.postDelayed(ticker, 1000)
    sheet.setOnDismissListener { body.removeCallbacks(ticker) }
    sheet.show()
    android.util.Log.i("OBD2Scan", "What your car reports: shown, ${values.size} live values")
}
/**
 * A standard reading's value now, for What your car reports: from the live readings, the
 * counters since the codes were cleared, or the readiness monitors. "–" while the app hasn't
 * got one yet; null for readings the app doesn't read.
 */
internal fun MainActivity.scanValueText(n: Int): String? {
    val s = ObdSession.state.value
    val k = String.format(Locale.US, "%02X", n)
    if (k in Pids.TEXT_ONLY) return s.readings[k]?.toInt()?.let { v -> if (k == "03") com.obdreader.app.obd.FuelInfo.status(v) else com.obdreader.app.obd.FuelInfo.type(v) } ?: "–"
    Pids.byHex(k)?.let { p ->
        val v = s.readings[k] ?: return if (k in s.unsupported) "N/A" else "–"
        return String.format(Locale.US, "%.${Units.decimals(k, p.decimals)}f", Units.value(k, v)) + Units.unit(k, p.unit).let { if (it.isEmpty()) "" else " $it" }
    }
    // A stand-in arrives under the reading it stands in for.
    CarCoverage.STAND_INS.firstOrNull { it.pid.number == n }?.let { si ->
        if (s.standIns[si.forHex] == null) return null
        val v = s.readings[si.forHex] ?: return "–"
        return String.format(Locale.US, "%.${Units.decimals(si.forHex, si.pid.decimals)}f", Units.value(si.forHex, v)) + Units.unit(si.forHex, si.pid.unit).let { if (it.isEmpty()) "" else " $it" }
    }
    val c = s.cleared ?: return null
    val na = com.obdreader.app.obd.ClearedCheck.NA
    return when (n) {
        0x31 -> c.distanceKm?.let { com.obdreader.app.obd.ClearedCheck.distanceText(it) } ?: na
        0x30 -> c.warmUps?.let { com.obdreader.app.obd.ClearedCheck.warmUpText(it) } ?: na
        0x4E -> c.minutes?.let { com.obdreader.app.obd.ClearedCheck.durationText(it) } ?: na
        0x21 -> c.milDistanceKm?.let { com.obdreader.app.obd.ClearedCheck.distanceText(it) } ?: na
        0x4D -> c.milMinutes?.let { com.obdreader.app.obd.ClearedCheck.durationText(it) } ?: na
        0x01 -> c.readiness?.summary() ?: na
        else -> null
    }
}
/** What the scan found about the battery, as (text, found) lines. */
internal fun MainActivity.batteryLines(scan: ObdSession.Scan): List<Pair<String, Boolean>> = buildList {
    add("✓  Battery voltage: measured by the adapter, on every car" to true)
    add("·  12-volt battery current: not part of standard OBD2. Some cars measure it in a maker-specific module, which the app can't read yet, so the Dashboard estimates charging from voltage instead." to false)
    val pack = scan.battery[0x9A]
    add(when {
        0x9A !in scan.supported -> "·  Hybrid/EV battery data (PID 9A): not offered by this car" to false
        pack != null -> "✓  Hybrid/EV battery data (PID 9A): offered. Raw: $pack. Send this scan so it can be decoded for your car." to true
        else -> "·  Hybrid/EV battery data (PID 9A): listed, but no answer" to false
    })
    if (0x5B in scan.supported) {
        val life = scan.battery[0x5B]?.split(" ")?.firstOrNull()?.toIntOrNull(16)?.let { (it * 100.0 / 255).roundToInt() }
        add((if (life != null) "✓  Hybrid battery remaining life: $life%" else "·  Hybrid battery remaining life: listed, but no answer") to (life != null))
    }
}
// ---------- Dashboard readouts ----------

/**
 * Choose the readings in the Dashboard's main readouts: two on the liveries and Race
 * telemetry, three on the Digital cockpit and Cyberpunk. Picking one that's already used swaps them.
 */
internal fun MainActivity.showSlotPicker(chosen: MutableList<String>) {
    placesSheet(
        getString(R.string.slots), null, listOf("Readout 1", "Readout 2", "Readout 3 (Digital cockpit and Cyberpunk)"), chosen,
        { readoutChoices.map { it.key to it.label } }, { readout(it).label },
        save = { keys ->
            if (keys != slots) {
                prefs.edit().putString(PREF_SLOTS, keys.joinToString(",")).apply()
                recreate() // rebuilds the Dashboard with the new readouts
            }
        },
        reset = { prefs.edit().remove(PREF_SLOTS).apply(); recreate() },
    )
}
