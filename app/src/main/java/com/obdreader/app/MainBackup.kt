package com.obdreader.app

import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.obdreader.app.MainActivity.Companion.PREF_CUSTOM
import com.obdreader.app.obd.VehicleCatalog
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.session.DriveScorer
import com.obdreader.app.session.LogRecorder
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.AppThemes
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Back up and restore.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

// ---------- Back up and restore ----------

/** Back up and restore: what goes in a backup, the routes and VIN choices, then the file picker. */
internal fun MainActivity.showBackup() {
    lifecycleScope.launch {
        val summary = withContext(Dispatchers.IO) { com.obdreader.app.session.Backups.summary(applicationContext) }
        backupVin = com.obdreader.app.session.Backups.vinByDefault(this@showBackup)
        choiceSheet(getString(R.string.backup_title)) { body, sheet ->
            sheetNote(body, getString(R.string.backup_intro))
            sheetNote(body, resources.getQuantityString(R.plurals.backup_drives, summary.drives, summary.drives,
                android.text.format.Formatter.formatShortFileSize(this@showBackup, summary.driveBytes)))
            if (summary.anyRoute || summary.hasVin) sheetHeading(body, getString(R.string.backup_include))
            if (summary.anyRoute) sheetSwitch(body, getString(R.string.backup_routes), backupRoutes) { on -> backupRoutes = on }
            if (summary.hasVin) sheetSwitch(body, getString(R.string.backup_vin), backupVin) { on -> backupVin = on }
            sheetNote(body, getString(R.string.backup_privacy))
            sheetButtons(body,
                getString(R.string.restore) to {
                    sheet.dismiss()
                    if (ObdSession.isActive || LogRecorder.active.value != null) toast(getString(R.string.restore_disconnect_first))
                    else restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                },
                getString(R.string.back_up) to { sheet.dismiss(); backupLauncher.launch(com.obdreader.app.session.Backups.fileName(System.currentTimeMillis())) },
            )
        }
    }
}
internal fun MainActivity.writeBackup(uri: android.net.Uri) {
    val routes = backupRoutes; val vin = backupVin
    lifecycleScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { com.obdreader.app.session.Backups.write(applicationContext, uri, routes, vin) } }
        android.util.Log.i("OBD2Backup", "backup written: ${result.isSuccess}")
        toast(result.fold({ getString(R.string.backup_saved) }, { getString(R.string.backup_failed, it.message ?: "") }))
    }
}
internal fun MainActivity.readBackup(uri: android.net.Uri) {
    if (ObdSession.isActive || LogRecorder.active.value != null) { toast(getString(R.string.restore_disconnect_first)); return }
    lifecycleScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { com.obdreader.app.session.Backups.read(applicationContext, uri) } }
        result.onSuccess { showRestorePreview(it) }.onFailure { e ->
            android.util.Log.i("OBD2Backup", "restore refused: ${e.message}")
            withContext(Dispatchers.IO) { com.obdreader.app.session.Backups.discard(applicationContext) }
            MaterialAlertDialogBuilder(this@readBackup)
                .setTitle(R.string.restore_refused_title)
                .setMessage((e as? com.obdreader.app.obd.BackupFile.BadBackup)?.message ?: getString(R.string.restore_unreadable))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
}
/** What a backup holds, before anything on the phone changes; Restore asks to go ahead. */
internal fun MainActivity.showRestorePreview(c: com.obdreader.app.obd.BackupFile.Contents) {
    var restoring = false
    val sheet = choiceSheet(getString(R.string.restore_title)) { body, sheet ->
        val day = java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        sheetNote(body, getString(R.string.restore_made, c.info.madeAtMs?.let { day.format(java.util.Date(it)) } ?: "?", c.info.appVersion ?: "?"))
        sheetHeading(body, getString(R.string.restore_contents))
        val car = (c.value(AppPrefs.CAR_MAKE) as? String)?.let { make ->
            VehicleCatalog.Car(make, c.value(AppPrefs.CAR_YEAR) as? Int ?: 0, c.value(AppPrefs.CAR_MODEL) as? String, c.value(AppPrefs.CAR_ENGINE) as? String).label
        }
        val theme = (c.value(AppPrefs.THEME) as? String)?.let { AppThemes.byId(it).name }
        val auto = com.obdreader.app.obd.DayNight.mode(c.value(AppPrefs.STYLE_AUTO) as? String)
        val custom = com.obdreader.app.obd.CustomReading.decodeAll(c.value(PREF_CUSTOM) as? String).size
        val trips = com.obdreader.app.obd.TripHistory.parse(c.value(DriveScorer.PREF_TRIP_HISTORY) as? String).size
        val codes = com.obdreader.app.obd.CodeHistory.parse(c.value(AppPrefs.CODE_HISTORY) as? String).size
        val lines = listOfNotNull(
            getString(R.string.restore_car, car ?: getString(R.string.restore_none)),
            theme?.let { getString(R.string.restore_style, it) + if (auto != com.obdreader.app.obd.DayNight.Mode.OFF) " · " + getString(R.string.day_night_title) + ": " + auto.label.lowercase() else "" },
            getString(R.string.restore_custom, custom),
            getString(R.string.restore_trips, trips),
            getString(R.string.restore_codes, codes),
            getString(if (c.info.hasRoutes) R.string.restore_drives_routes else R.string.restore_drives, c.drives.size),
            getString(if (c.has(AppPrefs.USER_VIN)) R.string.restore_vin_in else R.string.restore_vin_out),
            // Where Connect looks for a Wi-Fi adapter: shown, since it changes where the app connects.
            (c.value(AppPrefs.HOST) as? String)?.let { h -> getString(R.string.restore_wifi, h + ((c.value(AppPrefs.PORT) as? Int)?.let { ":$it" } ?: "")) },
            getString(R.string.restore_settings, c.settings.size),
            c.skipped.takeIf { it > 0 }?.let { getString(R.string.restore_skipped, it) },
        )
        lines.forEach { sheetNote(body, "• $it") }
        sheetNote(body, getString(R.string.restore_what_happens))
        sheetButtons(body,
            getString(android.R.string.cancel) to { sheet.dismiss() },
            getString(R.string.restore) to {
                restoring = true
                sheet.dismiss()
                lifecycleScope.launch {
                    val done = withContext(Dispatchers.IO) { runCatching { com.obdreader.app.session.Backups.restore(applicationContext, c) } }
                    done.onSuccess { r ->
                        android.util.Log.i("OBD2Backup", "restored ${r.settings} settings, ${r.drivesAdded} drives added, ${r.drivesAlreadyThere} already there")
                        toast(getString(R.string.restore_done, r.settings, r.drivesAdded))
                        com.obdreader.app.widget.ObdWidget.refresh(this@showRestorePreview)
                        recreate()
                    }.onFailure { toast(getString(R.string.backup_failed, it.message ?: "")) }
                }
                Unit
            },
        )
    }
    // Closed without restoring: the unpacked copy is thrown away.
    sheet.setOnDismissListener { if (!restoring) com.obdreader.app.session.Backups.discard(applicationContext) }
}
