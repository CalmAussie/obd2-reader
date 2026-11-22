package com.obdreader.app

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.obdreader.app.obd.DriveTrack
import com.obdreader.app.obd.TripSummary
import com.obdreader.app.session.Exports
import com.obdreader.app.session.LogRecorder
import com.obdreader.app.session.ObdSession
import com.obdreader.app.session.OverlayExporter
import com.obdreader.app.ui.SkewButton
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.uiTypeface
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Recording drives, saved drives and the video overlay.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

internal fun MainActivity.renderRecording(r: LogRecorder.Recording?) {
    recordBtn.isEnabled = r != null || ObdSession.state.value.isConnected
    showRecordGroup(ObdSession.state.value.isConnected)
    recordBtn.text = getString(if (r == null) R.string.record_drive else R.string.stop_recording)
    recordBtn.kind = if (r == null) SkewButton.Kind.PRIMARY else SkewButton.Kind.DANGER
    recordStatus.text = if (r == null) getString(R.string.record_hint) else {
        val secs = (System.currentTimeMillis() - r.startedMs) / 1000
        String.format(Locale.US, "Recording %d:%02d, %,d rows%s. Saved as %s", secs / 60, secs % 60, r.rows, if (r.withLocation) ", with route" else "", r.file.name)
    }
}
/** Record drive lives on the Dashboard, shown only while connected (or recording, so it can be stopped). */
internal fun MainActivity.showRecordGroup(connected: Boolean) {
    val show = connected || LogRecorder.active.value != null
    val v = findViewById<View>(R.id.recordGroup)
    val want = if (show) View.VISIBLE else View.GONE
    if (v.visibility != want) v.visibility = want
}
/** Lists recorded drives: tap one to share it, or delete it. */
internal fun MainActivity.showSavedDrives() {
    val files = LogRecorder.saved(this)
    if (files.isEmpty()) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.saved_drives)
            .setMessage("No drives recorded yet. Connect, then tap Record drive on the Dashboard.")
            .setPositiveButton("OK", null)
            .show()
        return
    }
    val names = files.map { f ->
        val kb = (f.length() + 1023) / 1024
        "${driveLabel(f)}, $kb KB"
    }.toTypedArray()
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.saved_drives)
        .setItems(names) { _, i -> showDriveActions(files[i]) }
        .setNegativeButton("Close", null)
        .show()
}
/** "19 Oct 2026, 10:37 pm" from a recording's file name (OBD2Reader_2026-10-19_223705.csv). */
internal fun MainActivity.driveLabel(f: java.io.File): String {
    val stamp = f.nameWithoutExtension.removePrefix("OBD2Reader_")
    val parsed = listOf("yyyy-MM-dd_HHmmss", "yyyy-MM-dd_HHmm").firstNotNullOfOrNull { pattern ->
        try { java.text.SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(stamp) } catch (e: java.text.ParseException) { null }
    }
    return parsed?.let { java.text.SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(it) } ?: stamp.replace('_', ' ')
}
internal fun MainActivity.showDriveActions(file: java.io.File) {
    val actions = listOf(
        "Make a video overlay" to { showOverlayOptions(file) },
        "Share the data (CSV)" to { Exports.share(this, file, "text/csv", "OBD2 Reader drive ${file.nameWithoutExtension}") },
        "Delete" to {
            if (LogRecorder.active.value?.file == file) {
                toast("This drive is still recording. Stop recording first.")
            } else {
                // Deleting always asks first (style guide): a recorded drive can't be got back.
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.saved_drive_delete_title)
                    .setMessage(R.string.saved_drive_delete_text)
                    .setPositiveButton(R.string.history_delete) { _, _ ->
                        file.delete()
                        toast(getString(R.string.saved_drive_deleted))
                    }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
        },
    )
    MaterialAlertDialogBuilder(this)
        .setTitle(driveLabel(file))
        .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
        .setNegativeButton("Cancel", null)
        .show()
}
/** Background and size for the overlay video, then makes it. */
/** Reads the drive in the background (a long one can be several megabytes), then offers the video choices. */
internal fun MainActivity.showOverlayOptions(file: java.io.File) {
    if (LogRecorder.active.value?.file == file) { toast("This drive is still recording. Stop recording first."); return }
    if (readingDrive) return
    readingDrive = true
    lifecycleScope.launch {
        val done = readingNotice()
        val read: Result<DriveTrack?> = try {
            withContext(Dispatchers.IO) { runCatching { DriveTrack.parse(file.readLines()) } }
        } finally { readingDrive = false; done() }
        when (val e = read.exceptionOrNull()) {
            null -> {}
            is OutOfMemoryError -> { toast("This drive is too long to make a video on this phone."); return@launch }
            is java.io.IOException -> { toast("Couldn't read this drive. It may have been deleted."); return@launch }
            else -> throw e
        }
        val drive = read.getOrNull()
        if (drive == null || drive.duration < 2.0) { toast("This drive is too short to make a video."); return@launch }
        showOverlayChoices(file, drive)
    }
}
internal fun MainActivity.showOverlayChoices(file: java.io.File, drive: DriveTrack) {
    val backgrounds = listOf(
        Triple("Green screen", "Remove it with your editor's chroma key (CapCut, Premiere, DaVinci Resolve…)", android.graphics.Color.rgb(0, 255, 0)),
        Triple("Black", "Use the Screen blend mode to drop the black", android.graphics.Color.BLACK),
    )
    var bg = 0
    var size = 0
    val view = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24f).toInt(), dp(8f).toInt(), dp(24f).toInt(), 0) }
    view.addView(TextView(this).apply {
        text = "Your readings as a video to lay over dashcam or GoPro footage: rpm, speed, shift lights, throttle and a 30-second trace, in your style's colours. ${TripSummary.duration(drive.duration.toInt())} long, 30 frames a second."
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdTextSecondary)); setLineSpacing(0f, 1.15f)
    })
    fun group(title: String, options: List<Pair<String, String?>>, onPick: (Int) -> Unit) {
        view.addView(TextView(this).apply {
            text = title; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdTextPrimary))
            setPadding(0, dp(16f).toInt(), 0, dp(2f).toInt())
        })
        val radios = android.widget.RadioGroup(this)
        options.forEachIndexed { i, (name, hint) ->
            radios.addView(com.google.android.material.radiobutton.MaterialRadioButton(this).apply {
                id = View.generateViewId()
                text = if (hint == null) name else "$name\n$hint"
                isChecked = i == 0
                setOnCheckedChangeListener { _, on -> if (on) onPick(i) }
            })
        }
        view.addView(radios)
    }
    group("Background", backgrounds.map { it.first to it.second }) { bg = it }
    group("Size", OverlayExporter.Size.values().map { it.label to null }) { size = it }
    MaterialAlertDialogBuilder(this)
        .setTitle("Video overlay")
        .setView(androidx.core.widget.NestedScrollView(this).apply { addView(view) })
        .setPositiveButton("Make video") { _, _ -> makeOverlay(file, drive, backgrounds[bg].third, OverlayExporter.Size.values()[size]) }
        .setNegativeButton("Cancel", null)
        .show()
}
internal fun MainActivity.makeOverlay(file: java.io.File, drive: DriveTrack, background: Int, size: OverlayExporter.Size) {
    val out = Exports.exportFile(this, file.nameWithoutExtension + "_overlay.mp4")
    val progress = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply { max = 1000; isIndeterminate = false }
    val label = TextView(this).apply { text = "Starting…"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary)) }
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(24f).toInt(), dp(12f).toInt(), dp(24f).toInt(), 0)
        addView(progress); addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f).toInt() })
    }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle("Making your video")
        .setView(box)
        .setCancelable(false)
        .setNegativeButton("Cancel") { _, _ -> overlayJob?.cancel() }
        .show()
    val started = android.os.SystemClock.elapsedRealtime()
    overlayJob = lifecycleScope.launch {
        try {
            OverlayExporter.export(this@makeOverlay, drive, out, background, size, redline, roundel.max) { f ->
                progress.progress = (f * 1000).toInt()
                val spent = (android.os.SystemClock.elapsedRealtime() - started) / 1000.0
                val left = if (f > 0.02f) (spent / f * (1 - f)).toInt() else null
                label.text = "${(f * 100).toInt()}%" + (left?.let { " · about ${TripSummary.duration(it.coerceAtLeast(1))} left" } ?: "")
            }
            dialog.dismiss()
            val saved = OverlayExporter.saveToMovies(this@makeOverlay, out)
            MaterialAlertDialogBuilder(this@makeOverlay)
                .setTitle("Video ready")
                .setMessage(if (saved != null) "Saved to Movies › OBD2 Reader, where your video editor can find it." else "Share it to your video editor, Drive or Files to keep it.")
                .setPositiveButton(R.string.share) { _, _ -> Exports.share(this@makeOverlay, out, "video/mp4", "OBD2 Reader video overlay") }
                .setNegativeButton(R.string.done, null)
                .show()
        } catch (e: kotlinx.coroutines.CancellationException) {
            dialog.dismiss(); out.delete()
            throw e
        } catch (e: Exception) {
            dialog.dismiss(); out.delete()
            MaterialAlertDialogBuilder(this@makeOverlay)
                .setTitle("Couldn't make the video")
                .setMessage("This phone's video encoder refused the job (${e.message ?: e.javaClass.simpleName}). Try the 720p size.")
                .setPositiveButton(R.string.done, null)
                .show()
        } finally {
        }
    }
}
