package com.obdreader.app.session

import android.content.Context
import android.net.Uri
import com.obdreader.app.obd.BackupFile
import com.obdreader.app.obd.VinPrivacy
import java.io.File

/**
 * Back up and restore on the phone: gathers the settings and recorded drives into a backup file
 * where the user chose to put it, and restores one the user picked (see [BackupFile] for what's in
 * it and how a restore checks it). Nothing is sent anywhere; the file goes only where the user puts it.
 */
object Backups {

    /** What the phone has to back up, for the Back up screen. */
    data class Summary(val drives: Int, val driveBytes: Long, val anyRoute: Boolean, val hasVin: Boolean)

    fun summary(context: Context): Summary {
        val files = drives(context)
        val anyRoute = files.any { f -> try { f.bufferedReader().use { BackupFile.routeColumn(it.readLine().orEmpty()) >= 0 } } catch (e: Exception) { false } }
        return Summary(files.size, files.sumOf { it.length() }, anyRoute, AppPrefs.of(context).getString(AppPrefs.USER_VIN, null) != null)
    }

    /** Whether the VIN goes in a backup to start with: only when Hide VIN shows it in full. */
    fun vinByDefault(context: Context): Boolean = AppPrefs.vinPrivacy(AppPrefs.of(context)) == VinPrivacy.Mode.FULL

    /** Saved drives, except one still being recorded. */
    private fun drives(context: Context): List<File> {
        val recording = LogRecorder.active.value?.file
        return LogRecorder.saved(context).filter { it != recording && BackupFile.isDriveName(it.name) }
    }

    fun fileName(nowMs: Long): String =
        "OBD2Reader_backup_" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(nowMs)) + ".zip"

    /** Writes a backup to [uri] (a file the user created with Android's file picker). */
    fun write(context: Context, uri: Uri, includeRoutes: Boolean, includeVin: Boolean) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("Android didn't open the file.")
        out.use {
            BackupFile.write(
                it, AppPrefs.of(context).all,
                drives(context).map { f -> BackupFile.Drive(f.name) { f.inputStream() } },
                appVersion(context), System.currentTimeMillis(), includeRoutes, includeVin,
            )
        }
    }

    private fun staging(context: Context) = File(context.cacheDir, "restore")

    /** Reads the backup at [uri] into a staging folder, ready for [restore]. Throws [BackupFile.BadBackup] with the reason. */
    fun read(context: Context, uri: Uri): BackupFile.Contents {
        val dir = staging(context)
        dir.deleteRecursively(); dir.mkdirs()
        val input = context.contentResolver.openInputStream(uri) ?: throw BackupFile.BadBackup("Android didn't open the file.")
        // A refused file leaves nothing behind in the staging folder.
        return try { input.use { BackupFile.read(it, dir) } } catch (e: Exception) { dir.deleteRecursively(); throw e }
    }

    /** Forgets a backup that was read but not restored. */
    fun discard(context: Context) { staging(context).deleteRecursively() }

    data class Restored(val settings: Int, val drivesAdded: Int, val drivesAlreadyThere: Int)

    /**
     * Restores [contents]: the backup's settings replace the phone's (lists the phone builds up, like
     * drive and code history, are joined instead), and its drives are added beside the phone's.
     * Nothing already on the phone is deleted.
     */
    fun restore(context: Context, contents: BackupFile.Contents): Restored {
        val prefs = AppPrefs.of(context)
        val edit = prefs.edit()
        for (s in contents.settings) {
            val v = s.value
            if (s.key in BackupFile.MERGED && v is String) { edit.putString(s.key, BackupFile.merge(s.key, prefs.getString(s.key, null), v)); continue }
            when (v) {
                is String -> edit.putString(s.key, v)
                is Int -> edit.putInt(s.key, v)
                is Boolean -> edit.putBoolean(s.key, v)
                is Long -> edit.putLong(s.key, v)
                is Float -> edit.putFloat(s.key, v)
            }
        }
        // A different car's remembered protocol would only slow the first connection down.
        if (contents.has(AppPrefs.CAR_MAKE) && !contents.has(AppPrefs.LAST_PROTOCOL)) edit.remove(AppPrefs.LAST_PROTOCOL)
        edit.commit()
        AppPrefs.applyUnits(prefs)

        var added = 0; var there = 0
        val folder = LogRecorder.folder(context)
        for (f in contents.drives) {
            val same = File(folder, f.name)
            if (same.exists() && same.length() == f.length()) { there++; f.delete(); continue }
            val target = if (!same.exists()) same else (2..999).asSequence()
                .map { File(folder, f.name.removeSuffix(".csv") + "_$it.csv") }.firstOrNull { !it.exists() } ?: continue
            if (!f.renameTo(target)) { f.copyTo(target); f.delete() }
            // Keep the drive's own date, so the list stays in the order the drives happened.
            target.setLastModified(dateOf(target.name) ?: System.currentTimeMillis())
            added++
        }
        discard(context)
        // The home-screen icon is an app setting too: switch to the backup's.
        (contents.value(AppPrefs.APP_ICON) as? String)?.let { id ->
            com.obdreader.app.ui.AppIcons.ALL.firstOrNull { it.id == id }?.takeIf { it != com.obdreader.app.ui.AppIcons.current(context) }
                ?.let { com.obdreader.app.ui.AppIcons.choose(context, it) }
        }
        return Restored(contents.settings.size, added, there)
    }

    /** When a drive was recorded, from its name (OBD2Reader_2026-11-13_142530.csv). */
    private fun dateOf(name: String): Long? {
        val m = Regex("OBD2Reader_(\\d{4}-\\d{2}-\\d{2})_(\\d{4})(\\d{2})?").find(name) ?: return null
        return try {
            java.text.SimpleDateFormat("yyyy-MM-ddHHmmss", java.util.Locale.US).parse(m.groupValues[1] + m.groupValues[2] + m.groupValues[3].ifEmpty { "00" })?.time
        } catch (e: Exception) { null }
    }

    fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }
}
