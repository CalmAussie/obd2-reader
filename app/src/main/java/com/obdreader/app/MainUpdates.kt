package com.obdreader.app

import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.obdreader.app.session.ObdSession
import com.obdreader.app.session.Updater
import com.obdreader.app.ui.applyTitleFonts
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.uiTypeface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// App updates from the GitHub releases, and setup help.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

internal fun MainActivity.showInstallPrompt(confirm: Intent) {
    try {
        androidx.core.app.NotificationManagerCompat.from(this).cancel(4101)
        startActivity(confirm)
    } catch (e: Exception) {
        android.util.Log.i("OBD2Updater", "couldn't open the install prompt: ${e.message}")
        updateProblem("Android didn't open its Install screen." + (e.message?.let { "\n\nAndroid said: $it" } ?: ""))
    }
}
/** Why an update didn't go through, said plainly, with a way to get the APK by hand. */
internal fun MainActivity.updateProblem(why: String) {
    if (isFinishing || isDestroyed) return
    androidx.core.app.NotificationManagerCompat.from(this).cancel(4101)
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.updates_problem_title)
        .setMessage(why + (if (Updater.isSamsung) "\n\n" + getString(R.string.updates_samsung) else "") + "\n\n" + getString(R.string.updates_problem_manual))
        .setPositiveButton(R.string.updates_open_page) { _, _ ->
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Updater.RELEASES_PAGE))) } catch (e: Exception) {}
        }
        .setNegativeButton("Close", null)
        .show()
}
internal fun MainActivity.showUpdateRow(release: com.obdreader.app.obd.UpdateCheck.Release?) {
    val sub = findViewById<TextView>(R.id.updatesSub)
    sub.text = when {
        Updater.isTestBuild(this) -> getString(R.string.updates_sub_test, appVersion())
        release != null -> getString(R.string.updates_sub_available, release.version)
        else -> getString(R.string.updates_sub, appVersion())
    }
    sub.setTextColor(if (release != null) themeColor(R.attr.obdAccentText) else themeColor(R.attr.obdTextSecondary))
}
/**
 * [manual]: the user tapped App updates, so say what was found. Otherwise it's the daily check:
 * silent unless there's a new version, and never while connected to the car.
 */
internal fun MainActivity.checkForUpdate(manual: Boolean) {
    val now = System.currentTimeMillis()
    if (!manual && (updateBusy || !com.obdreader.app.obd.UpdateCheck.dueForCheck(prefs.getLong(Updater.LAST_CHECK, 0L), now))) {
        return
    }
    if (manual) toast(getString(R.string.updates_checking))
    updateBusy = true
    lifecycleScope.launch {
        val release = try { Updater.latest() } catch (e: Exception) { android.util.Log.i("OBD2Updater", "check failed: ${e.message}"); null }
        updateBusy = false
        if (release == null) { if (manual) toast(getString(R.string.updates_failed)); return@launch }
        android.util.Log.i("OBD2Updater", "latest build ${release.build}, installed ${Updater.installedBuild(this@checkForUpdate)}")
        prefs.edit().putLong(Updater.LAST_CHECK, now).apply()
        val newer = com.obdreader.app.obd.UpdateCheck.isNewer(release, Updater.installedBuild(this@checkForUpdate))
        availableUpdate = release.takeIf { newer }
        showUpdateRow(availableUpdate)
        if (isFinishing || isDestroyed) return@launch
        when {
            Updater.isTestBuild(this@checkForUpdate) -> if (manual) offerReleasePage(release)
            !newer -> { if (manual) toast(getString(R.string.updates_latest, appVersion())) }
            manual -> offerUpdate(release)
            // The daily check: once per new version, and not while driving.
            !ObdSession.state.value.isConnected && prefs.getInt(Updater.SNOOZED_BUILD, 0) != release.build -> offerUpdate(release)
        }
    }
}
/** The changelog, but only the versions after the one installed: what an update brings. */
internal fun MainActivity.showChangesSince() {
    val installed = appVersion()
    val sheet = BottomSheetDialog(this)
    val pad = dp(20f).toInt()
    val list = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, dp(8f).toInt(), pad, dp(28f).toInt())
    }
    list.addView(TextView(this).apply {
        text = getString(R.string.updates_changes_title, installed)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); typeface = uiTypeface(600)
        setTextColor(themeColor(R.attr.obdTextPrimary))
    })
    val status = TextView(this).apply {
        setText(R.string.updates_changes_loading)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdTextSecondary))
        setPadding(0, dp(10f).toInt(), 0, dp(4f).toInt())
    }
    list.addView(status)
    fun entry(e: com.obdreader.app.obd.Changelog.Entry) {
        list.addView(TextView(this).apply {
            text = listOf(e.version, e.date).filter { it.isNotBlank() }.joinToString("  ·  ")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); typeface = uiTypeface(600)
            setTextColor(themeColor(R.attr.obdTextPrimary)); setPadding(0, dp(16f).toInt(), 0, dp(4f).toInt())
        })
        list.addView(TextView(this).apply {
            text = e.text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setLineSpacing(0f, 1.15f)
            setTextColor(themeColor(R.attr.obdTextPrimary))
        })
    }
    sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(list) })
    sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    sheet.behavior.skipCollapsed = true
    sheet.show()
    lifecycleScope.launch {
        val entries = try { com.obdreader.app.obd.Changelog.parse(Updater.changelog()) } catch (e: Exception) { null }
        if (!sheet.isShowing) return@launch
        if (entries == null) { status.setText(R.string.updates_changes_failed); return@launch }
        val newer = com.obdreader.app.obd.Changelog.since(entries, installed)
        if (newer.isEmpty()) {
            status.text = getString(R.string.updates_changes_none, installed)
            entries.firstOrNull { com.obdreader.app.obd.Changelog.compare(it.version, installed) == 0 }?.let { entry(it) }
        } else {
            status.visibility = View.GONE
            newer.forEach { entry(it) }
            if (!Updater.isTestBuild(this@showChangesSince)) list.addView(MaterialButton(this@showChangesSince).apply {
                text = getString(R.string.updates_changes_get, newer.first().version)
                setOnClickListener {
                    sheet.dismiss()
                    availableUpdate?.let { offerUpdate(it) } ?: checkForUpdate(manual = true)
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f).toInt() })
        }
        list.applyTitleFonts()
    }
}
internal fun MainActivity.offerReleasePage(release: com.obdreader.app.obd.UpdateCheck.Release) {
    MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.updates_available_title, release.version))
        .setMessage(R.string.updates_test_body)
        .setPositiveButton(R.string.updates_open_page) { _, _ ->
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(release.pageUrl))) } catch (e: Exception) { toast(Updater.RELEASES_PAGE) }
        }
        .setNegativeButton(R.string.updates_later, null)
        .show()
}
internal fun MainActivity.offerUpdate(release: com.obdreader.app.obd.UpdateCheck.Release) {
    val pad = dp(20f).toInt()
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, dp(8f).toInt(), pad, 0)
        addView(TextView(context).apply {
            text = getString(R.string.updates_available_body, appVersion()) + (if (Updater.isSamsung) "\n\n" + getString(R.string.updates_samsung) else "")
            setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        })
        if (release.notes.isNotBlank()) {
            addView(TextView(context).apply {
                setText(R.string.updates_whats_new); typeface = uiTypeface(600)
                setTextColor(themeColor(R.attr.obdTextPrimary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, dp(14f).toInt(), 0, dp(4f).toInt())
            })
            addView(TextView(context).apply {
                text = release.notes
                setTextColor(themeColor(R.attr.obdTextPrimary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.15f)
            })
        }
    }
    // Seen here, so the notification about it isn't needed any more.
    com.obdreader.app.session.UpdateCheckJob.dismiss(this)
    updateBusy = true
    MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.updates_available_title, release.version))
        .setView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
        .setPositiveButton(R.string.updates_update) { _, _ -> downloadAndInstall(release) }
        .setNegativeButton(R.string.updates_later) { _, _ -> prefs.edit().putInt(Updater.SNOOZED_BUILD, release.build).apply() }
        .setOnDismissListener { updateBusy = false }
        .show()
}
internal fun MainActivity.downloadAndInstall(release: com.obdreader.app.obd.UpdateCheck.Release) {
    if (!Updater.canInstall(this)) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.updates_permission_title)
            .setMessage(R.string.updates_permission_body)
            .setPositiveButton(R.string.updates_open_settings) { _, _ ->
                try { startActivity(Updater.permissionIntent(this)) } catch (e: Exception) { toast(getString(R.string.updates_permission_body)) }
            }
            .setNegativeButton("Cancel", null)
            .show()
        return
    }
    val bar = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply { isIndeterminate = true; max = 100 }
    val pad = dp(24f).toInt()
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.updates_downloading, release.version))
        .setView(android.widget.FrameLayout(this).apply { setPadding(pad, dp(12f).toInt(), pad, dp(8f).toInt()); addView(bar) })
        .setCancelable(false)
        .show()
    var downloadError: String? = null
    lifecycleScope.launch {
        val file = try {
            Updater.download(this@downloadAndInstall, release) { f ->
                if (bar.isIndeterminate) bar.isIndeterminate = false
                bar.setProgressCompat((f * 100).toInt(), false)
            }
        } catch (e: Exception) { android.util.Log.i("OBD2Updater", "download failed: ${e.message}"); downloadError = e.message; null }
        dialog.dismiss()
        if (file == null) {
            updateProblem(getString(R.string.updates_download_failed) + (downloadError?.let { "\n\nWhat happened: $it" } ?: ""))
            return@launch
        }
        android.util.Log.i("OBD2Updater", "downloaded ${file.length()} bytes")
        val handedOver = android.os.SystemClock.elapsedRealtime()
        try {
            Updater.install(this@downloadAndInstall, file, release.build)
            // Some phones (Samsung's Auto Blocker) refuse an app's own update without a word.
            kotlinx.coroutines.delay(20_000)
            if (Updater.lastAnswerMs < handedOver && !isFinishing && !isDestroyed) {
                android.util.Log.i("OBD2Updater", "no answer from the installer")
                updateProblem(getString(R.string.updates_no_answer))
            }
        } catch (e: Exception) {
            android.util.Log.i("OBD2Updater", "install failed: ${e.message}")
            updateProblem("Android didn't accept the update." + (e.message?.let { "\n\nAndroid said: $it" } ?: ""))
        }
    }
}
/** Setup help inside the app, so someone given just the APK can get going without the README. */
internal fun MainActivity.showHelp() {
    val sheet = BottomSheetDialog(this)
    val pad = dp(20f).toInt()
    val list = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, dp(8f).toInt(), pad, dp(28f).toInt())
    }
    list.addView(TextView(this).apply {
        setText(R.string.help_title)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTextColor(themeColor(R.attr.obdTextPrimary))
        typeface = uiTypeface(600)
    })
    val sections = listOf(
        R.string.help_start_title to R.string.help_start_body,
        R.string.help_adapters_title to R.string.help_adapters_body,
        R.string.help_car_title to R.string.help_car_body,
        R.string.help_auto_title to R.string.help_auto_body,
        R.string.help_codes_title to R.string.help_codes_body,
        R.string.help_battery_title to R.string.help_battery_body,
        R.string.help_location_title to R.string.help_location_body,
        R.string.help_safety_title to R.string.help_safety_body,
    )
    for ((title, body) in sections) {
        list.addView(TextView(this).apply {
            setText(title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(themeColor(R.attr.obdAccentText))
            typeface = uiTypeface(600)
            setPadding(0, dp(18f).toInt(), 0, dp(6f).toInt())
        })
        list.addView(TextView(this).apply {
            setText(body)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setLineSpacing(0f, 1.15f)
        })
    }
    list.addView(TextView(this).apply {
        text = getString(R.string.help_version, appVersion())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(themeColor(R.attr.obdTextSecondary))
        setPadding(0, dp(22f).toInt(), 0, 0)
    })
    sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(list) })
    sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    sheet.show()
}
