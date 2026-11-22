package com.obdreader.app

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.obdreader.app.MainActivity.Companion.evLog
import com.obdreader.app.MainActivity.Found
import com.obdreader.app.obd.DtcLibrary
import com.obdreader.app.obd.EnhancedReadings
import com.obdreader.app.obd.EvBattery
import com.obdreader.app.obd.ModuleScan
import com.obdreader.app.obd.Readiness
import com.obdreader.app.obd.Units
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.session.Exports
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.displayTypeface
import com.obdreader.app.ui.panelBackground
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.titleTypeface
import com.obdreader.app.ui.uiTypeface
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Diagnostics groups and the deeper checks: recordings, code history, cleared codes, modules, enhanced readings, EV battery.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

// ---------- Car scan ----------

// ---------- Diagnostics groups ----------

/**
 * Diagnostics groups its settings and checks into sections that open and close, so the tab
 * isn't one long list. Each remembers whether it was left open. The rows themselves are the
 * same as ever; they're only moved under their section's header.
 */
internal fun MainActivity.setupGroups() {
    val groups = listOf(
        Triple("car", "Car and connection", listOf(R.id.carRow, R.id.vinPrivacyRow, R.id.marketRow, R.id.protocolRow, R.id.reconnectRow, R.id.repliesRow, R.id.logCard)),
        Triple("checks", "Deeper checks", listOf(R.id.scanRow, R.id.clearedRow, R.id.historyRow, R.id.readinessRow, R.id.testsRow, R.id.modulesRow, R.id.enhancedRow, R.id.evRow, R.id.customRow, R.id.alertsRow)),
        Triple("display", "Display and readings", listOf(R.id.unitsRow, R.id.slotsRow, R.id.widgetRow, R.id.autoRow, R.id.pipRow, R.id.appIconRow)),
        Triple("backup", "Back up", listOf(R.id.backupRow)),
        Triple("help", "Help and feedback", listOf(R.id.helpRow, R.id.reportRow, R.id.ideaRow, R.id.updatesRow)),
    )
    val parent = findViewById<View>(R.id.carRow).parent as ViewGroup
    // The old "Connection" heading sat above the first of these rows: the sections replace it.
    (0 until parent.childCount).map { parent.getChildAt(it) }
        .firstOrNull { it is TextView && it.text == getString(R.string.connection) }?.visibility = View.GONE
    // Everything moves under the sections, in this order, where the first row was. Every row is
    // looked up first: once a row is taken off the screen, findViewById can't find it any more.
    var at = parent.indexOfChild(findViewById<View>(R.id.carRow))
    val found = groups.flatMap { it.third }.associateWith { findViewById<View>(it) }
    found.values.filterNotNull().forEach { parent.removeView(it) }
    for ((key, title, ids) in groups) {
        // Each section's rows are in alphabetical order, by their names, except Your car, which
        // stays first in its section (choosing the car is the first thing to do).
        val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
        val rows = ids.mapNotNull { found[it] }
            .sortedWith(compareBy<View> { it.id != R.id.carRow }.then(compareBy(collator) { rowTitle(it).orEmpty() }))
        val names = rows.mapNotNull { rowTitle(it) }
        // Closed to start with, except Car and connection until a car is chosen (that's the first thing to set).
        val open = prefs.getBoolean("group_$key", key == "car" && AppPrefs.savedCar(prefs) == null)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = if (open) View.VISIBLE else View.GONE
            rows.forEach { r -> addView(r, (r.layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)).apply { topMargin = dp(6f).toInt() }) }
        }
        val chevron = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_right); rotation = if (open) 270f else 90f
            imageTintList = ColorStateList.valueOf(themeColor(R.attr.obdAccent)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true
            background = android.util.TypedValue().let { tv -> theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true); androidx.core.content.ContextCompat.getDrawable(this@setupGroups, tv.resourceId) }
            setPadding(0, dp(14f).toInt(), 0, dp(10f).toInt())
            addView(LinearLayout(this@setupGroups).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@setupGroups).apply {
                    text = title; typeface = titleTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); setTextColor(themeColor(R.attr.obdTextPrimary))
                })
                addView(TextView(this@setupGroups).apply {
                    text = names.joinToString(" · "); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary))
                    maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(2f).toInt(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(chevron, LinearLayout.LayoutParams(dp(28f).toInt(), dp(28f).toInt()))
            fun describe(isOpen: Boolean) { contentDescription = "$title, " + if (isOpen) "expanded" else "collapsed" }
            describe(open)
            setOnClickListener {
                val nowOpen = box.visibility != View.VISIBLE
                box.visibility = if (nowOpen) View.VISIBLE else View.GONE
                chevron.animate().rotation(if (nowOpen) 270f else 90f).setDuration(180).start()
                describe(nowOpen)
                prefs.edit().putBoolean("group_$key", nowOpen).apply()
            }
        }
        val lp = { top: Int -> LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top.toFloat()).toInt() } }
        parent.addView(header, at++, lp(if (key == "car") 20 else 10))
        parent.addView(box, at++, lp(0))
        parent.addView(View(this).apply { setBackgroundColor(themeColor(R.attr.obdTextSecondary)); alpha = 0.2f }, at++, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { topMargin = dp(12f).toInt() })
    }
}
/** The first line of a row (its name), for its section's summary. */
internal fun MainActivity.rowTitle(row: View): String? {
    fun first(v: View): String? = when (v) {
        is TextView -> v.text?.toString()?.takeIf { it.isNotBlank() }
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { first(v.getChildAt(it)) }
        else -> null
    }
    return first(row)
}
// ---------- Deeper checks: readiness, self-tests, other modules, custom readings (all read-only) ----------

/** Adds the deeper-check rows to Diagnostics, under Scan what your car reports. */
internal fun MainActivity.setupDeeperChecks() {
    val scanRow = findViewById<View>(R.id.scanRow)
    val parent = scanRow.parent as ViewGroup
    var at = parent.indexOfChild(scanRow) + 1
    fun row(id: Int, title: String, help: String, onClick: () -> Unit) {
        val r = LinearLayout(this).apply {
            this.id = id
            orientation = LinearLayout.VERTICAL
            background = panelBackground()
            isClickable = true; isFocusable = true
            val p = dp(16f).toInt(); setPadding(p, p, p, p)
            addView(LinearLayout(this@setupDeeperChecks).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@setupDeeperChecks).apply {
                    text = title; typeface = uiTypeface(500); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(themeColor(R.attr.obdTextPrimary))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(android.widget.ImageView(this@setupDeeperChecks).apply {
                    setImageResource(R.drawable.ic_chevron_right); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    imageTintList = ColorStateList.valueOf(themeColor(R.attr.obdTextSecondary))
                }, LinearLayout.LayoutParams(dp(20f).toInt(), dp(20f).toInt()))
            })
            addView(TextView(this@setupDeeperChecks).apply {
                text = help; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary))
                setPadding(0, dp(6f).toInt(), 0, 0)
            })
            setOnClickListener { onClick() }
        }
        parent.addView(r, at++, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f).toInt() })
    }
    row(R.id.clearedRow, getString(R.string.cleared_title), getString(R.string.cleared_help)) { runCheck(getString(R.string.cleared_title)) { showClearedCheck() } }
    row(R.id.readinessRow, getString(R.string.readiness_title), getString(R.string.readiness_help)) { runCheck(getString(R.string.readiness_title)) { showReadiness() } }
    row(R.id.testsRow, getString(R.string.tests_title), getString(R.string.tests_help)) { runCheck(getString(R.string.tests_title)) { showTestResults() } }
    row(R.id.modulesRow, getString(R.string.modules_title), getString(R.string.modules_help)) { runCheck(getString(R.string.modules_title), "Asking each module in turn. This can take half a minute.") { showModules() } }
    row(R.id.enhancedRow, getString(R.string.enhanced_title), getString(R.string.enhanced_help)) { runCheck(getString(R.string.enhanced_title)) { showEnhanced() } }
    row(R.id.evRow, getString(R.string.ev_title), getString(R.string.ev_help)) { runCheck(getString(R.string.ev_title)) { showEvBattery() } }
    row(R.id.customRow, getString(R.string.custom_title), getString(R.string.custom_help)) { showCustomReadings() }
    row(R.id.historyRow, getString(R.string.history_codes_title), getString(R.string.history_codes_help)) { showCodeHistory() }
    row(R.id.vinPrivacyRow, getString(R.string.vin_privacy_title), vinMode().label) { showVinPrivacy() }
    row(R.id.backupRow, getString(R.string.backup_title), getString(R.string.backup_help)) { showBackup() }
    row(R.id.repliesRow, getString(R.string.replies_title), repliesHelp()) { showReplies() }
}
internal fun MainActivity.repliesHelp() = getString(if (AppPrefs.recordReplies(prefs)) R.string.replies_help_on else R.string.replies_help_off)
/**
 * Record and replay: a switch to save each connection's requests and replies (reads only, the
 * VIN left out unless Hide VIN shows it), the saved recordings, and a button to open a file.
 */
internal fun MainActivity.showReplies() {
    val sheet = choiceSheet(getString(R.string.replies_title)) { body, sheet ->
        sheetNote(body, getString(R.string.replies_note))
        sheetSwitch(body, getString(R.string.replies_switch), AppPrefs.recordReplies(prefs)) { on ->
            prefs.edit().putBoolean(AppPrefs.RECORD_REPLIES, on).apply()
            findViewById<View>(R.id.repliesRow)?.let { r -> rowHelp(r)?.text = repliesHelp() }
            if (on && ObdSession.state.value.isConnected && !ObdSession.state.value.isDemo) toast(getString(R.string.replies_next_time))
        }
        sheetHeading(body, getString(R.string.replies_saved))
        val files = com.obdreader.app.session.ReplyRecordings.saved(this)
        if (files.isEmpty()) sheetNote(body, getString(R.string.replies_none))
        for (file in files) {
            sheetRow(body, com.obdreader.app.session.ReplyRecordings.describe(file), com.obdreader.app.session.ReplyRecordings.size(file)) {
                recordingActions(file, sheet)
            }
        }
        sheetButtons(body, getString(R.string.replies_open) to {
            try { openRecordingLauncher.launch(arrayOf("text/plain", "application/octet-stream")) }
            catch (e: android.content.ActivityNotFoundException) { toast(getString(R.string.replies_open_failed)) }
        })
    }
    currentSheet = sheet
}
/** One saved recording: replay it, share or save it, or delete it (after asking). */
internal fun MainActivity.recordingActions(file: java.io.File, parent: BottomSheetDialog) {
    val rec = try { com.obdreader.app.session.ReplyRecordings.load(file) } catch (e: Exception) { null }
    choiceSheet(getString(R.string.replies_recording)) { body, sheet ->
        body.addView(TextView(this).apply {
            text = com.obdreader.app.session.ReplyRecordings.describe(file) + " · " + com.obdreader.app.session.ReplyRecordings.size(file)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextPrimary))
        })
        if (rec == null) sheetNote(body, getString(R.string.replies_unreadable))
        rec?.let { r ->
            val made = listOfNotNull(r.app?.let { "Made by OBD2 Reader $it" }, com.obdreader.app.session.ReplyRecordings.madeText(r.made)?.let { "on $it" }).joinToString(" ")
            sheetNote(body, listOf(made, String.format(Locale.getDefault(), "%,d requests", r.exchanges.size)).filter { it.isNotEmpty() }.joinToString(". ") + ".")
        }
        val buttons = mutableListOf<Pair<String, () -> Unit>>()
        if (rec != null) buttons += getString(R.string.replies_replay) to { sheet.dismiss(); parent.dismiss(); replay(file, rec) }
        buttons += getString(R.string.replies_share) to {
            sheet.dismiss()
            // Shared from the exports folder (the one the file provider serves), never from the recordings folder itself.
            val copy = try { Exports.exportFile(this, "OBD2Reader_" + file.name).also { file.copyTo(it, overwrite = true) } } catch (e: Exception) { null }
            if (copy == null) toast(getString(R.string.file_save_failed)) else offerFile(copy, "OBD2 Reader recording")
        }
        sheetStackedButtons(body, buttons, getString(R.string.history_delete) to {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.replies_delete_title)
                .setMessage(R.string.replies_delete_text)
                .setPositiveButton(R.string.history_delete) { _, _ ->
                    file.delete()
                    sheet.dismiss(); parent.dismiss()
                    toast(getString(R.string.replies_deleted))
                    showReplies()
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        })
    }
}
/** Replays [rec] like the demo car: the app talks to the recording, not to a car or adapter. */
internal fun MainActivity.replay(file: java.io.File, rec: com.obdreader.app.obd.ReplyRecording.Recording) {
    if (ObdSession.isActive) { toast(getString(R.string.replies_disconnect_first)); return }
    val label = com.obdreader.app.session.ReplyRecordings.label(file)
    android.util.Log.i("OBD2Replay", "Replaying a recording: ${rec.exchanges.size} requests")
    startSession(com.obdreader.app.obd.ReplayTransport(rec, label))
    bottomNav.selectedItemId = R.id.nav_dashboard
}
internal fun MainActivity.vinMode() = AppPrefs.vinPrivacy(prefs)
/** Hide VIN: show it in full, hide the serial number (last 6) or hide it completely, on screen and in shares. */
internal fun MainActivity.showVinPrivacy() {
    val modes = com.obdreader.app.obd.VinPrivacy.Mode.entries
    choiceSheet(getString(R.string.vin_privacy_title)) { body, sheet ->
        sheetNote(body, getString(R.string.vin_privacy_note))
        sheetOptions(body, modes.map { it.label }, modes.indexOf(vinMode())) { i ->
            prefs.edit().putString(AppPrefs.VIN_PRIVACY, modes[i].key).apply()
            findViewById<View>(R.id.vinPrivacyRow)?.let { r -> rowHelp(r)?.text = modes[i].label }
            showVin(ObdSession.state.value.vin)
            sheet.dismiss()
        }
        modes.forEach { sheetNote(body, "${it.label}: ${it.help}") }
    }
}
/** A Diagnostics row's second line (its help text). */
internal fun MainActivity.rowHelp(row: View): TextView? = ((row as? ViewGroup)?.getChildAt(1) as? TextView)
/** Code history: every code seen on each car, with dates; a button clears the phone's list (never the car's codes). */
internal fun MainActivity.showCodeHistory() {
    val all = com.obdreader.app.obd.CodeHistory.parse(prefs.getString(AppPrefs.CODE_HISTORY, null))
    val day = java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val make = ObdSession.carMake(this); val year = ObdSession.carYear(this); val model = ObdSession.carModel(this); val engine = ObdSession.carEngine(this)
    val current = ObdSession.historyCar(this)
    infoSheet(getString(R.string.history_codes_title)) { heading, line, body ->
        if (all.isEmpty()) {
            line(getString(R.string.history_codes_empty), true)
            return@infoSheet
        }
        line(getString(R.string.history_codes_intro), true)
        for (car in com.obdreader.app.obd.CodeHistory.cars(all, current)) {
            heading(car)
            for (e in com.obdreader.app.obd.CodeHistory.forCar(all, car)) {
                // Maker codes are explained for the chosen car only, so other cars' codes get the generic title.
                val title = if (car == current) DtcLibrary.lookup(e.code, make, year, model, engine).title else DtcLibrary.lookup(e.code, null, null, null, null).title
                line("${e.code}  $title", !e.active)
                line(com.obdreader.app.obd.CodeHistory.describe(e) { day.format(java.util.Date(it)) }, true)
            }
        }
        body.addView(MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.history_codes_clear); setTextColor(themeColor(R.attr.obdAccentText))
            setOnClickListener {
                MaterialAlertDialogBuilder(this@showCodeHistory)
                    .setTitle(R.string.history_codes_clear_title)
                    .setMessage(R.string.history_codes_clear_text)
                    .setPositiveButton(R.string.history_codes_clear) { _, _ ->
                        prefs.edit().remove(AppPrefs.CODE_HISTORY).apply()
                        currentSheet?.dismiss()
                        toast(getString(R.string.history_codes_cleared))
                    }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f).toInt() })
    }
}
/** Shows a "please wait" while [work] asks the car, then its sheet; says so if the car stops answering. */
internal fun MainActivity.runCheck(title: String, message: String = "Asking your car. This takes a few seconds.", work: suspend () -> Unit) {
    if (!ObdSession.state.value.isConnected) { toast("Connect to your car first."); return }
    val wait = MaterialAlertDialogBuilder(this).setTitle(title)
        .setView(com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
            isIndeterminate = true; setPadding(dp(24f).toInt(), dp(16f).toInt(), dp(24f).toInt(), dp(8f).toInt())
        })
        .setMessage(message).setCancelable(false).show()
    lifecycleScope.launch {
        try { work() } catch (e: CancellationException) { throw e } catch (e: Exception) { toast("The car stopped answering. Try again.") }
        finally { wait.dismiss() }
    }
}
/** A bottom sheet with a title; [fill] adds the lines. */
internal fun MainActivity.infoSheet(title: String, fill: (heading: (String) -> Unit, line: (String, Boolean) -> Unit, body: LinearLayout) -> Unit) {
    val sheet = BottomSheetDialog(this)
    val pad = dp(20f).toInt()
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
    body.addView(TextView(this).apply {
        text = title; setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary))
    })
    val heading = { t: String -> body.addView(TextView(this).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdTextPrimary))
        setPadding(0, dp(16f).toInt(), 0, dp(4f).toInt())
    }) }
    val line = { t: String, secondary: Boolean -> body.addView(TextView(this).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setLineSpacing(0f, 1.15f); setTextIsSelectable(true)
        setTextColor(themeColor(if (secondary) R.attr.obdTextSecondary else R.attr.obdTextPrimary)); setPadding(0, dp(4f).toInt(), 0, 0)
    }) }
    fill(heading, line, body)
    sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
    currentSheet = sheet
    sheet.show()
}
/** "Codes recently cleared?": the verdict, each counter (N/A where the car doesn't say) and what it means. */
internal suspend fun MainActivity.showClearedCheck() {
    val check = ObdSession.readClearedCheck()
    android.util.Log.i("OBD2Deep", "cleared: ${check.verdict}")
    infoSheet(getString(R.string.cleared_title)) { heading, line, _ ->
        line(check.headline(), false)
        check.notes().forEach { line(it, false) }
        heading("What the car counts")
        check.lines().forEach { (k, v) -> line("$k: $v", v == com.obdreader.app.obd.ClearedCheck.NA) }
        heading("What this means")
        line("When trouble codes are cleared, the engine computer starts these counts again from zero. Very small numbers on a car for sale can mean a fault was cleared to hide the warning light until it comes back.", true)
        line(com.obdreader.app.obd.ClearedCheck.CAVEAT, true)
        line("It's also in the code report when you share it.", true)
    }
}
internal suspend fun MainActivity.showReadiness() {
    val r = ObdSession.readReadiness()
    val cal = ObdSession.readCalibrationIds()
    if (r == null) { toast("The car didn't report its readiness monitors."); return }
    android.util.Log.i("OBD2Deep", "readiness: ${r.summary()}")
    infoSheet(getString(R.string.readiness_title)) { heading, line, _ ->
        line(r.summary() + ". Check-engine light " + (if (r.milOn) "on" else "off") + ", ${r.codeCount} stored ${if (r.codeCount == 1) "code" else "codes"}.", false)
        heading("Monitors")
        r.monitors.forEach { m ->
            line((if (m.state == Readiness.State.COMPLETE) "✓  " else "…  ") + "${m.name}: ${m.state.label}", m.state == Readiness.State.COMPLETE)
            line("     ${m.about}", true)
        }
        heading("What this means")
        line("The engine computer runs these checks by itself as you drive. They reset whenever the codes are cleared or the battery is disconnected, and finish again after a few days of normal driving, town and highway. An emissions inspection usually wants them complete.", true)
        if (cal.isNotEmpty()) { heading("Engine computer software (calibration ID)"); cal.forEach { line(it, false) } }
    }
}
internal suspend fun MainActivity.showTestResults() {
    val results = ObdSession.readTestResults()
    android.util.Log.i("OBD2Deep", "self-tests: ${results?.size ?: "not CAN"}")
    infoSheet(getString(R.string.tests_title)) { heading, line, _ ->
        when {
            results == null -> line("Self-test results are read on CAN cars (most from 2008, many earlier). This car uses an older protocol, where each maker sets out these results its own way.", true)
            results.isEmpty() -> line("The car didn't report any self-test results.", true)
            else -> {
                val fails = results.count { !it.passed }
                line(if (fails == 0) "All ${results.size} tests within their limits." else "$fails of ${results.size} tests outside their limits.", false)
                results.groupBy { it.mid }.forEach { (_, list) ->
                    heading(list.first().monitor)
                    list.forEach { t -> line("${if (t.passed) "✓" else "✗"}  Test ${String.format(Locale.US, "%02X", t.tid)}: ${t.text()}", t.passed) }
                }
                heading("What this means")
                line("These are the engine computer's own measurements from the last time it ran each check, with the limits they must stay between. A result close to a limit can show a part wearing out before it sets a trouble code. The test numbers are the maker's own, so what each one measures isn't always published.", true)
            }
        }
    }
}
internal suspend fun MainActivity.showModules() {
    val results = ObdSession.scanModules(this)
    logModules(results)
    moduleSheet(results, null)
}
internal fun MainActivity.logModules(results: List<Pair<ModuleScan.Module, ModuleScan.Answer>>?, tag: String = "modules") {
    android.util.Log.i("OBD2Deep", "$tag: " + (results?.joinToString("; ") { (m, a) -> "${m.name}=" + when (a) {
        is ModuleScan.Answer.Codes -> a.codes.joinToString(",") { it.label }.ifEmpty { "none" }
        ModuleScan.Answer.Refused -> "refused"; ModuleScan.Answer.Silent -> "silent" } } ?: "not CAN"))
}
/** The modules sheet: the make's known modules, and after a search, any others that answered. */
internal fun MainActivity.moduleSheet(results: List<Pair<ModuleScan.Module, ModuleScan.Answer>>?, found: List<Pair<ModuleScan.Module, ModuleScan.Answer>>?) {
    val make = ObdSession.carMake(this); val year = ObdSession.carYear(this)
    val model = ObdSession.carModel(this); val engine = ObdSession.carEngine(this)
    infoSheet(getString(R.string.modules_title)) { heading, line, body ->
        if (results == null) {
            line("Other modules are read on CAN cars (most from 2008), and on Fords and Mazdas that use J1850. This car uses an older protocol, where only the engine computer can be reached with a standard adapter.", true)
            return@infoSheet
        }
        val all = results + found.orEmpty()
        val total = all.sumOf { (it.second as? ModuleScan.Answer.Codes)?.codes?.size ?: 0 }
        line(if (total == 0) "No codes in the modules that answered." else "$total ${if (total == 1) "code" else "codes"} found.", false)
        fun show(list: List<Pair<ModuleScan.Module, ModuleScan.Answer>>) = list.forEach { (m, a) ->
            heading(m.name)
            when (a) {
                is ModuleScan.Answer.Codes -> if (a.codes.isEmpty()) line("No codes", true) else a.codes.forEach { c ->
                    line("${c.label}  ${c.text ?: DtcLibrary.lookup(c.code, make, year, model, engine).title}" + if (c.pending && !c.confirmed) " (pending)" else " (stored)", false)
                }
                ModuleScan.Answer.Refused -> line("Answered, but doesn't share its codes this way.", true)
                ModuleScan.Answer.Silent -> line("Didn't answer: not fitted, or on a part of the car's network the adapter can't reach.", true)
            }
        }
        val volvo = ObdSession.isVolvoD3B0(this)
        if (!volvo) show(results)
        if (volvo) {
            // Only the modules this car has: the rest are listed together, not one "didn't answer" each.
            val answered = results.filter { it.second !is ModuleScan.Answer.Silent }
            if (ObdSession.volvoAdapterRefused) {
                line("Your adapter can't wake Volvo's own modules: it doesn't support the command they need, which many cheap clones leave out. Adapters that can: OBDLink (MX+, LX, EX), BAFX and Veepeak OBDCheck BLE+. Everything else in the app still works with your adapter.", false)
            } else {
                show(answered)
                if (answered.none { it.first.request in com.obdreader.app.obd.VolvoD3B0.ENGINES }) {
                    heading("Engine")
                    line("Your engine's codes are on the Diagnostics tab. This engine (such as the turbo 850's Motronic 4.3) reports them with standard OBD2 rather than Volvo's own protocol, and the app reads those on every car.", true)
                }
                if (answered.none { it.first.request == com.obdreader.app.obd.VolvoD3B0.COMBI.address }) {
                    heading(com.obdreader.app.obd.VolvoD3B0.COMBI.name)
                    line("Didn't answer. Some 1996–97 850s have a Yazaki-made cluster that doesn't talk through the OBD2 port; its warning lights still work as normal.", true)
                }
                val skip = com.obdreader.app.obd.VolvoD3B0.ENGINES + com.obdreader.app.obd.VolvoD3B0.COMBI.address
                val missing = results.filter { it.second is ModuleScan.Answer.Silent && it.first.request !in skip }
                if (missing.isNotEmpty()) line("Not on this car (or not answering): " + missing.joinToString(", ") { it.first.name } + ".", true)
            }
            heading("About Volvo module codes")
            line("Read with Volvo's own keyword D3 B0 protocol, which these 1996–2000 cars use. The airbag module is never asked. Code meanings come from Volvo owners' work (jonesrh.info) and aren't Volvo's official list.", true)
        } else if (found != null) {
            body.addView(TextView(this).apply {
                text = "Found by searching"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary))
                setPadding(0, dp(24f).toInt(), 0, 0)
            })
            if (found.isEmpty()) line("No other modules answered.", true) else show(found)
        } else {
            body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.modules_search); setTextColor(themeColor(R.attr.obdAccentText))
                setOnClickListener { searchAllModules(results) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f).toInt() })
            line(getString(R.string.modules_search_help), true)
        }
        if (ModuleScan.SecondNetwork.offeredFor(make, ObdSession.state.value.protocolCode)) {
            body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.modules_second); setTextColor(themeColor(R.attr.obdAccentText))
                setOnClickListener { searchSecondNetwork() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f).toInt() })
            line(getString(R.string.modules_second_help), true)
        }
        heading("About this")
        line("Read-only: the app asks each module for its codes and never clears them. Airbag modules are never asked. Codes starting C (chassis), B (body) or U (network) with a 1 after the letter are the maker's own; look them up in your maker's service information.", true)
    }
}
/** "Search every address", with a progress bar, then the sheet again with what it found. */
internal fun MainActivity.searchAllModules(results: List<Pair<ModuleScan.Module, ModuleScan.Answer>>) {
    if (!ObdSession.state.value.isConnected) { toast("Connect to your car first."); return }
    currentSheet?.dismiss()
    val bar = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
        isIndeterminate = false; max = 100; setPadding(dp(24f).toInt(), dp(16f).toInt(), dp(24f).toInt(), dp(8f).toInt())
    }
    val wait = MaterialAlertDialogBuilder(this).setTitle(R.string.modules_search).setView(bar)
        .setMessage("Asking every module address in turn. This takes about a minute.").setCancelable(false).show()
    lifecycleScope.launch {
        try {
            val known = results.filter { it.second !is ModuleScan.Answer.Silent }.map { it.first.request }.toSet()
            val found = ObdSession.searchModules(this@searchAllModules, known) { done, total ->
                runOnUiThread { bar.setProgressCompat(if (total == 0) 100 else done * 100 / total, true) }
            }
            logModules(found, "search")
            foundModules = found.orEmpty().filter { it.second !is ModuleScan.Answer.Silent }.map { it.first }
            wait.dismiss()
            // A known module that was silent before but answered the search moves to the found list.
            val foundReq = found.orEmpty().map { it.first.request }.toSet()
            moduleSheet(results.filter { it.first.request !in foundReq }, found.orEmpty())
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            wait.dismiss(); toast("The car stopped answering. Try again.")
        }
    }
}
/** "Search the second network" (Fords and Mazdas, STN adapters), with a progress bar, then its own sheet. */
internal fun MainActivity.searchSecondNetwork() {
    if (!ObdSession.state.value.isConnected) { toast("Connect to your car first."); return }
    currentSheet?.dismiss()
    val bar = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
        isIndeterminate = false; max = 100; setPadding(dp(24f).toInt(), dp(16f).toInt(), dp(24f).toInt(), dp(8f).toInt())
    }
    val wait = MaterialAlertDialogBuilder(this).setTitle(R.string.modules_second).setView(bar)
        .setMessage("Asking every module address on the second network in turn. This takes about a minute.").setCancelable(false).show()
    lifecycleScope.launch {
        try {
            val result = ObdSession.searchSecondNetwork { done, total ->
                runOnUiThread { bar.setProgressCompat(if (total == 0) 100 else done * 100 / total, true) }
            }
            wait.dismiss()
            val make = ObdSession.carMake(this@searchSecondNetwork); val year = ObdSession.carYear(this@searchSecondNetwork)
            val model = ObdSession.carModel(this@searchSecondNetwork); val engine = ObdSession.carEngine(this@searchSecondNetwork)
            when (result) {
                ObdSession.SecondNetworkResult.AdapterCantReach -> {
                    android.util.Log.i("OBD2Deep", "second network: adapter can't reach it")
                    infoSheet(getString(R.string.modules_second)) { _, line, _ ->
                        line("Your adapter can't switch to the second network. Adapters built on STN chips can, such as OBDLink MX+ and OBDLink EX; most cheap ELM327 adapters can't.", false)
                        line("Everything else in Deeper checks still works with your adapter.", true)
                    }
                }
                is ObdSession.SecondNetworkResult.Found -> {
                    logModules(result.modules, "second network")
                    infoSheet(getString(R.string.modules_second)) { heading, line, _ ->
                        val total = result.modules.sumOf { (it.second as? ModuleScan.Answer.Codes)?.codes?.size ?: 0 }
                        line(when {
                            result.modules.isEmpty() -> "No modules answered on the second network. Your car may not have one, or its modules don't share their codes this way."
                            total == 0 -> "${result.modules.size} ${if (result.modules.size == 1) "module" else "modules"} answered, with no codes."
                            else -> "$total ${if (total == 1) "code" else "codes"} found."
                        }, false)
                        result.modules.forEach { (m, a) ->
                            heading(m.name)
                            when (a) {
                                is ModuleScan.Answer.Codes -> if (a.codes.isEmpty()) line("No codes", true) else a.codes.forEach { c ->
                                    line("${c.label}  ${DtcLibrary.lookup(c.code, make, year, model, engine).title}" + if (c.pending && !c.confirmed) " (pending)" else " (stored)", false)
                                }
                                ModuleScan.Answer.Refused -> line("Answered, but doesn't share its codes this way.", true)
                                ModuleScan.Answer.Silent -> line("Didn't answer when asked for its codes.", true)
                            }
                        }
                        heading("About this")
                        line("Read-only: the app asks each module for its codes and never clears them. The airbag module is never asked.", true)
                    }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            wait.dismiss(); toast("The car stopped answering. Try again.")
        }
    }
}
internal suspend fun MainActivity.showEnhanced() {
    val car = AppPrefs.savedCar(AppPrefs.of(this))
    val make = ObdSession.carMake(this)
    val tried = EnhancedReadings.forCar(make, car?.model, ObdSession.carYear(this))
    val first = ObdSession.readEnhanced(tried)
    // A 1996–2000 Volvo: the instrument cluster's stored mileage (the true distance).
    val volvo = ObdSession.isVolvoD3B0(this)
    val volvoKm = if (volvo) ObdSession.readVolvoMileage() else null
    if (volvo) android.util.Log.i("OBD2Deep", "volvo mileage: " + (volvoKm?.let { String.format(Locale.US, "%.0f km", it) } ?: "none"))
    val answered = tried.filter { first[it] != null }
    android.util.Log.i("OBD2Deep", "enhanced: tried ${tried.size}, answered " + answered.joinToString(", ") { "${it.name}=${first[it]?.let(it::format)}" }.ifEmpty { "none" })
    val values = mutableMapOf<EnhancedReadings.Reading, TextView>()
    infoSheet(getString(R.string.enhanced_title)) { heading, line, body ->
        heading("Manufacturer readings")
        if (volvo) {
            val odo = volvoKm?.let { km -> if (Units.mph) String.format(Locale.US, "%,.0f miles", km / 1.609344) else String.format(Locale.US, "%,.0f km", km) } ?: "N/A"
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6f).toInt(), 0, 0)
                addView(LinearLayout(this@showEnhanced).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@showEnhanced).apply { text = "Odometer (true mileage)"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdTextPrimary)) })
                    addView(TextView(this@showEnhanced).apply { text = "Instrument cluster (COMBI)"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@showEnhanced).apply { text = odo; typeface = titleTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(themeColor(R.attr.obdAccentText)) })
            })
            line(if (volvoKm == null && ObdSession.volvoAdapterRefused) "Your adapter can't wake Volvo's instrument cluster (many cheap clones can't). An OBDLink, BAFX or Veepeak OBDCheck BLE+ can."
                else if (volvoKm == null) "Your instrument cluster didn't share its mileage. Some 1996–97 850s have a Yazaki-made cluster that doesn't give it out through the OBD2 port at all, so no app can read it there; the mileage is still on the dashboard."
                else "The mileage the instrument cluster has stored, counted in 10-mile steps, so it's right to within 10 miles (16 km) even if the dashboard odometer has stopped. A replaced cluster shows its own mileage.", true)
        }
        when {
            volvo && tried.isEmpty() -> {}
            tried.isEmpty() -> line("No manufacturer readings are known for ${make ?: "this make"} yet. Module information, below, works on every make.", true)
            answered.isEmpty() -> line("Your car didn't answer any of the ${tried.size} readings known for ${make ?: "its make"}. Not every model has them.", true)
            else -> answered.forEach { r ->
                body.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6f).toInt(), 0, 0)
                    addView(LinearLayout(this@showEnhanced).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(this@showEnhanced).apply { text = r.name; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdTextPrimary)) })
                        addView(TextView(this@showEnhanced).apply { text = r.module; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(this@showEnhanced).apply {
                        text = first[r]?.let(r::format) ?: "N/A"; typeface = titleTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(themeColor(R.attr.obdAccentText))
                        values[r] = this
                    })
                })
            }
        }
        if (answered.isNotEmpty() && answered.size < tried.size) line("${tried.size - answered.size} more known for your make weren't answered by your car.", true)
        if (tried.isNotEmpty()) line(getString(R.string.enhanced_source), true)
        heading("Module information")
        line("Part numbers, software versions and the VIN each module holds, read with the standard identifiers every make's modules use.", true)
        body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.enhanced_module_info); setTextColor(themeColor(R.attr.obdAccentText))
            setOnClickListener { readModuleInfo() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f).toInt() })
        heading("About this")
        line("Read-only: these only read from the car, with the makers' own read services. Nothing is changed or cleared.", true)
    }
    // Live while the sheet is open: the readings that answered, every 2 seconds.
    val sheet = currentSheet ?: return
    if (answered.isEmpty()) return
    val job = lifecycleScope.launch {
        while (true) {
            kotlinx.coroutines.delay(2000)
            if (!ObdSession.state.value.isConnected) { values.values.forEach { it.text = "N/A" }; continue }
            val now = try { ObdSession.readEnhanced(answered) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyMap() }
            values.forEach { (r, tv) -> tv.text = now[r]?.let(r::format) ?: "N/A" }
        }
    }
    sheet.setOnDismissListener { job.cancel() }
}
// ---- EV battery ----

/**
 * EV battery: the standard charge reading (5B), the maker's battery readings where they're
 * known for this car (marked Unofficial), and the charge's pace and graph while the sheet is
 * open. Read-only: the high-voltage system is never woken or commanded.
 */
internal suspend fun MainActivity.showEvBattery() {
    val car = AppPrefs.savedCar(AppPrefs.of(this))
    val make = ObdSession.carMake(this)
    val maker = EvBattery.makerReadings(make, car?.model, ObdSession.carYear(this))
    val first: Map<EnhancedReadings.Reading, Double?> = if (maker.isEmpty()) emptyMap() else ObdSession.readEnhanced(maker)
    val answered = maker.filter { first[it] != null }
    val listed = ObdSession.state.value.supported?.contains(0x5B) == true
    val hasCharge = listed || answered.any { EvBattery.isCharge(it) }
    android.util.Log.i("OBD2Deep", "ev battery: 5B " + (if (listed) "listed" else "not listed") + ", maker tried ${maker.size}, answered " +
        answered.joinToString(", ") { "${it.id}=${first[it]?.let(it::format)}" }.ifEmpty { "none" })
    fun pct(v: Double?) = v?.let { String.format(Locale.US, "%.0f %%", it) } ?: "N/A"
    // Percentages with a space, as on the Dashboard ("82 %").
    fun fmt(r: EnhancedReadings.Reading, v: Double?) = v?.let { r.format(it).replace(Regex("(\\d)%$"), "$1 %") } ?: "N/A"
    val values = mutableMapOf<EnhancedReadings.Reading, TextView>()
    var standardView: TextView? = null
    var paceView: TextView? = null
    var graph: com.obdreader.app.ui.ChargeGraphView? = null
    infoSheet(getString(R.string.ev_title)) { heading, line, body ->
        fun valueRow(name: String, sub: String, value: String, unofficial: Boolean): TextView {
            // A live number, like the trip tiles: display font, even-width digits so it doesn't jump every 5 s.
            val v = TextView(this).apply { text = value; typeface = displayTypeface(); fontFeatureSettings = "tnum"; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f); setTextColor(themeColor(R.attr.obdTextPrimary)) }
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6f).toInt(), 0, 0)
                addView(LinearLayout(this@showEvBattery).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@showEvBattery).apply { text = name; typeface = uiTypeface(500); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextPrimary)) })
                    addView(TextView(this@showEvBattery).apply {
                        text = if (unofficial) getString(R.string.ev_unofficial) + " · " + sub else sub
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary))
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(12f).toInt() })
                addView(v)
            })
            return v
        }
        heading("Battery")
        // The standard Charge row, unless the car doesn't list it and a maker charge reading answered instead:
        // a "Charge N/A" right above that reading's percentage would look broken.
        if (listed || answered.none { EvBattery.isCharge(it) })
            standardView = valueRow(getString(R.string.ev_charge), getString(R.string.ev_charge_sub), pct(ObdSession.state.value.readings[EvBattery.CHARGE_KEY]), false)
        answered.forEach { r -> values[r] = valueRow(r.name, r.module, fmt(r, first[r]), true) }
        if (!listed && answered.isEmpty()) line(getString(R.string.ev_none), true)
        heading("Charging")
        if (hasCharge) {
            val pace = TextView(this).apply {
                text = getString(R.string.ev_watching); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextPrimary))
                setPadding(0, dp(4f).toInt(), 0, dp(8f).toInt())
            }
            body.addView(pace)
            paceView = pace
            val chart = com.obdreader.app.ui.ChargeGraphView(this, themeColor(R.attr.obdAccent), themeColor(R.attr.obdTextSecondary) and 0x40FFFFFF, themeColor(R.attr.obdTextSecondary))
            body.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140f).toInt()))
            graph = chart
            line(getString(R.string.ev_charging_help), true)
        } else line(getString(R.string.ev_no_charge), true)
        if (answered.isNotEmpty()) line(getString(R.string.ev_unofficial_note), true)
        heading("About this")
        line(getString(R.string.ev_about), true)
    }
    val sheet = currentSheet ?: return
    if (!hasCharge && answered.isEmpty()) return
    // Live while the sheet is open, every 5 seconds.
    val job = lifecycleScope.launch {
        while (true) {
            val connected = ObdSession.state.value.isConnected
            val std = if (connected) ObdSession.state.value.readings[EvBattery.CHARGE_KEY] else null
            val now: Map<EnhancedReadings.Reading, Double?> = if (!connected || answered.isEmpty()) emptyMap()
                else try { ObdSession.readEnhanced(answered) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyMap() }
            standardView?.text = pct(std)
            values.forEach { (r, tv) -> tv.text = fmt(r, now[r]) }
            evLog.add(android.os.SystemClock.elapsedRealtime(), EvBattery.charge(std, now))
            graph?.show(evLog.all.map { it.timeMs to it.percent })
            val pack = EvBattery.packKwh(now.entries.firstOrNull { it.key.id == "leaf.capacity" }?.value, now.entries.firstOrNull { it.key.id == "leaf.hv_volts" }?.value)
            paceView?.text = evPace(pack) ?: getString(if (connected) R.string.ev_watching else R.string.ev_disconnected)
            kotlinx.coroutines.delay(5000)
        }
    }
    sheet.setOnDismissListener { job.cancel() }
}
/** "Charging: +24 % an hour · about 5.8 kW · 80 % in about 1 h 10 min", or null until the pace is known. */
internal fun MainActivity.evPace(packKwh: Double?): String? {
    val text = EvBattery.describe(evLog) ?: return null
    if (evLog.state() != EvBattery.State.CHARGING) return text
    val parts = mutableListOf(text)
    EvBattery.powerKw(evLog.ratePerHour(), packKwh)?.let { parts += String.format(Locale.US, "about %.1f kW", it) }
    val to80 = evLog.minutesTo(80.0)
    val toFull = evLog.minutesTo(100.0)
    when {
        to80 != null -> parts += "80 % in about ${EvBattery.duration(to80)}"
        toFull != null -> parts += "full in about ${EvBattery.duration(toFull)}"
    }
    return parts.joinToString(" · ")
}
internal fun MainActivity.readModuleInfo() {
    if (!ObdSession.state.value.isConnected) { toast("Connect to your car first."); return }
    currentSheet?.dismiss()
    val car = AppPrefs.savedCar(AppPrefs.of(this))
    val modules = (ModuleScan.modulesFor(ObdSession.carMake(this), car?.model, ObdSession.state.value.protocolCode?.removePrefix("A") in setOf("7", "9")) + foundModules).distinctBy { it.request }
    val bar = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
        isIndeterminate = false; max = 100; setPadding(dp(24f).toInt(), dp(16f).toInt(), dp(24f).toInt(), dp(8f).toInt())
    }
    val wait = MaterialAlertDialogBuilder(this).setTitle(R.string.enhanced_module_info).setView(bar)
        .setMessage("Asking each module for its part numbers and versions.").setCancelable(false).show()
    lifecycleScope.launch {
        val info = try {
            ObdSession.readModuleInfo(ObdSession.carMake(this@readModuleInfo), modules) { done, total -> runOnUiThread { bar.setProgressCompat(done * 100 / total.coerceAtLeast(1), true) } }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        wait.dismiss()
        if (info == null) { toast("The car stopped answering. Try again."); return@launch }
        android.util.Log.i("OBD2Deep", com.obdreader.app.obd.VinPrivacy.scrub("module info: " + info.joinToString("; ") { (m, f) -> "${m.name}: " + f.joinToString(", ") { "${it.first}=${it.second}" } }.ifEmpty { "none" }, vinMode(), Exports.knownVins(this@readModuleInfo, ObdSession.state.value.vin)))
        infoSheet(getString(R.string.enhanced_module_info)) { heading, line, _ ->
            if (info.isEmpty()) { line("No module answered with its identifiers. Older modules don't have them.", true); return@infoSheet }
            val carVin = ObdSession.state.value.vin
            info.forEach { (m, fields) ->
                heading(m.name)
                if (fields.isEmpty()) line("Answered, but gave none of the standard identifiers.", true)
                fields.forEach { (k, v) ->
                    val vinNote = if (k.startsWith("VIN") && carVin != null && v != carVin) "  (not the VIN the engine computer reports)" else ""
                    line("$k: ${if (k.startsWith("VIN")) com.obdreader.app.obd.VinPrivacy.mask(v, vinMode()) else v}$vinNote", false)
                }
            }
            heading("About this")
            line("A module's VIN that doesn't match the car's can mean it came from another car, or wasn't programmed with this car's VIN when it was fitted.", true)
        }
    }
}
