package com.obdreader.app.session

import android.content.Context
import android.net.Uri
import com.obdreader.app.obd.ReplyRecorder
import com.obdreader.app.obd.ReplyRecording
import com.obdreader.app.obd.VinPrivacy
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Where "Record car replies" keeps its recordings: the app's own private folder, which no other
 * app can read. A recording leaves the phone only when the user shares or saves it.
 */
object ReplyRecordings {

    /** The newest 10 are kept, so recordings never pile up. */
    const val KEEP = 10

    fun folder(context: Context): File = File(context.filesDir, "replies").apply { mkdirs() }

    /** Saved recordings, newest first. */
    fun saved(context: Context): List<File> =
        folder(context).listFiles { f -> f.isFile && f.extension == "txt" }?.sortedByDescending { it.lastModified() }.orEmpty()

    /**
     * Writes what [recorder] collected to a new file. The VIN is hidden unless Hide VIN is set to
     * show it in full. Returns null when there was nothing to save.
     */
    fun save(context: Context, recorder: ReplyRecorder, carVin: String?): File? {
        val exchanges = recorder.snapshot()
        if (exchanges.isEmpty()) return null
        val prefs = AppPrefs.of(context)
        val now = Date()
        val recording = ReplyRecording.Recording(
            app = appVersion(context),
            made = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(now),
            car = AppPrefs.savedCar(prefs)?.label,
            exchanges = exchanges,
        )
        val showVin = AppPrefs.vinPrivacy(prefs) == VinPrivacy.Mode.FULL
        val text = ReplyRecording.encode(recording, showVin, Exports.knownVins(context, carVin))
        val name = "recording-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now) + ".txt"
        val file = File(folder(context), name)
        file.writeText(text)
        saved(context).drop(KEEP).forEach { it.delete() }
        return file
    }

    /** Reads one of the app's own recordings. */
    fun load(file: File): ReplyRecording.Recording {
        if (file.length() > ReplyRecording.MAX_BYTES) throw ReplyRecording.BadRecording("This recording is larger than 2 MB.")
        return ReplyRecording.parse(file.readText(Charsets.US_ASCII))
    }

    /**
     * Opens a recording file the user picked (it may have come from anywhere): reads no more than
     * 2 MB, checks it line by line, and keeps a copy in the app's folder so it can be replayed again.
     */
    fun importFile(context: Context, uri: Uri): Pair<File, ReplyRecording.Recording> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(ReplyRecording.MAX_BYTES + 1)
            var n = 0
            while (n < buf.size) {
                val r = input.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            if (n > ReplyRecording.MAX_BYTES) throw ReplyRecording.BadRecording("This recording is larger than 2 MB.")
            buf.copyOf(n)
        } ?: throw ReplyRecording.BadRecording("Couldn't open that file.")
        // Plain text only: the check below refuses anything that isn't printable ASCII.
        val recording = ReplyRecording.parse(String(bytes, Charsets.ISO_8859_1))
        // The app picks the name, never the file's own, so nothing outside its folder is touched.
        val name = "recording-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + "-opened.txt"
        val file = File(folder(context), name)
        file.writeBytes(bytes)
        saved(context).drop(KEEP).forEach { it.delete() }
        return file to recording
    }

    /** "Fri 13 Nov, 10:42 am · 2012 Ford Focus" for the list, from the file's first lines only. */
    fun describe(file: File): String {
        val made = dateText(Date(file.lastModified()))
        val car = try {
            file.bufferedReader(Charsets.US_ASCII).useLines { lines ->
                lines.take(5).firstOrNull { it.startsWith("# car ") }?.removePrefix("# car ")
                    ?.filter { it in ' '..'~' }?.trim()?.take(40)?.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) { null }
        return listOfNotNull(made, car).joinToString(" · ")
    }

    /** "24 KB": the file's size, rounded up. */
    fun size(file: File): String = String.format(Locale.getDefault(), "%,d KB", (file.length() + 1023) / 1024)

    /** "Fri 13 Nov, 10:42 am", as drive history shows dates, with the year added when it isn't this year. */
    fun dateText(date: Date): String {
        val cal = Calendar.getInstance(); val thisYear = cal.get(Calendar.YEAR); cal.time = date
        val day = SimpleDateFormat(if (cal.get(Calendar.YEAR) == thisYear) "EEE d MMM" else "EEE d MMM yyyy", Locale.getDefault()).format(date)
        return day + ", " + DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    }

    /** When the recording was made ("2026-11-13 10:42" in the file) in [dateText]'s words, or null if it can't be read. */
    fun madeText(made: String?): String? = made?.let {
        try { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }.parse(it)?.let(::dateText) } catch (e: Exception) { null }
    }

    /** "13 Nov, 10:42": the name a replay goes by. */
    fun label(file: File): String = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(file.lastModified()))

    private fun appVersion(context: Context): String? = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) { null }
}
