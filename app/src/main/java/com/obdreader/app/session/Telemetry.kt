package com.obdreader.app.session

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.obdreader.app.obd.DtcLibrary
import com.obdreader.app.obd.Pids
import com.obdreader.app.obd.VinDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The last minute of every reading, kept for the Graphs tab so its history survives switching tabs. */
object LiveHistory {
    const val WINDOW_MS = 60_000L

    class Series {
        val times = ArrayDeque<Long>()
        val values = ArrayDeque<Double>()
    }

    private val series = mutableMapOf<String, Series>()
    private var started = false

    @Synchronized
    fun series(key: String): Series = series.getOrPut(key) { Series() }

    /** Starts collecting readings from [ObdSession]; safe to call more than once. */
    fun start() {
        if (started) return
        started = true
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            ObdSession.samples.collect { s ->
                synchronized(this@LiveHistory) {
                    val ser = series(s.key)
                    ser.times.addLast(s.timeMs)
                    ser.values.addLast(s.value)
                    while (ser.times.isNotEmpty() && s.timeMs - ser.times.first() > WINDOW_MS) {
                        ser.times.removeFirst(); ser.values.removeFirst()
                    }
                }
            }
        }
    }

    @Synchronized
    fun clear() = series.clear()
}

/**
 * Records a drive to a CSV file: one row every 200 ms with the latest value of every reading,
 * ready for Excel, Google Sheets or a data-logging tool.
 */
object LogRecorder {

    /** [withLocation]: the route is being saved into this drive's file too. */
    data class Recording(val file: File, val startedMs: Long, val rows: Int, val withLocation: Boolean = false)

    private const val ROW_MS = 200L

    private val _active = MutableStateFlow<Recording?>(null)
    val active: StateFlow<Recording?> = _active.asStateFlow()

    private var job: Job? = null
    private val columns: List<Pair<String, String>> =
        Pids.dashboard.map { it.hex to "${it.label} (${it.unit})" } + (ObdSession.VOLTAGE_KEY to "Battery (V)")

    fun folder(context: Context): File = File(context.filesDir, "logs").apply { mkdirs() }

    /** Saved recordings, newest first. */
    fun saved(context: Context): List<File> =
        folder(context).listFiles { f -> f.extension == "csv" }?.sortedByDescending { it.lastModified() }.orEmpty()

    /** Starts recording; [withLocation] also saves the route (Latitude, Longitude) in this drive's file only. */
    fun start(context: Context, withLocation: Boolean = false) {
        if (job?.isActive == true) return
        val located = withLocation && LocationFeed.start(context)
        // The background service needs to say it uses location while this drive records the route.
        if (located) ObdConnectionService.start(context)
        // Seconds in the name, so two drives recorded in the same minute don't overwrite each other.
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val file = File(folder(context), "OBD2Reader_$stamp.csv")
        val started = System.currentTimeMillis()
        _active.value = Recording(file, started, 0, located)
        val latest = mutableMapOf<String, Double>()
        var fresh = false

        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val out: BufferedWriter = file.bufferedWriter()
            val recording = coroutineContext[Job]
            try {
                out.write("Time,Seconds," + columns.joinToString(",") { csv(it.second) } + (if (located) ",Latitude,Longitude,Location accuracy (m)" else ""))
                out.newLine()
                val listener = launch {
                    ObdSession.samples.collect { s -> synchronized(latest) { latest[s.key] = s.value; fresh = true } }
                }
                val stop = launch {
                    // Stop when the user disconnects; carry on through automatic reconnects.
                    ObdSession.state.collect { s ->
                        if (s.phase == ObdSession.Phase.IDLE || s.phase == ObdSession.Phase.ERROR) recording?.cancel()
                    }
                }
                val clock = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                var rows = 0
                while (isActive) {
                    delay(ROW_MS)
                    val row = synchronized(latest) {
                        if (!fresh) null else { fresh = false; columns.map { latest[it.first] } }
                    } ?: continue
                    val now = System.currentTimeMillis()
                    out.write(clock.format(Date(now)) + "," + String.format(Locale.US, "%.1f", (now - started) / 1000.0) + "," +
                        row.joinToString(",") { v -> v?.let { String.format(Locale.US, "%.2f", it) } ?: "" })
                    if (located) {
                        // Blank when there's no recent fix, never an old position.
                        val fix = LocationFeed.current()
                        out.write(if (fix == null) ",,," else String.format(Locale.US, ",%.6f,%.6f,%s", fix.latitude, fix.longitude, fix.accuracyM?.let { String.format(Locale.US, "%.0f", it) } ?: ""))
                    }
                    out.newLine()
                    rows++
                    if (rows % 25 == 0) out.flush()
                    _active.value = Recording(file, started, rows, located)
                }
                listener.cancel(); stop.cancel()
            } finally {
                out.flush()
                out.close()
                // Nothing was recorded (never connected): don't leave an empty file behind.
                if ((_active.value?.rows ?: 0) == 0) file.delete()
                if (located) LocationFeed.stop()
                // The service was only kept for the route: let it go if the car is disconnected too.
                if (located && !ObdSession.isActive) ObdConnectionService.stop(context)
                _active.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun csv(s: String) = if (s.contains(',') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

/** Sharing files and reports through Android's share sheet (email, Drive, messages and so on). */
object Exports {

    private fun authority(context: Context) = "${context.packageName}.files"

    /** [text]: the post's caption, for apps that take one (Instagram, X, WhatsApp and the like). */
    fun share(context: Context, file: File, mime: String, subject: String, text: String? = null) {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            clipData = ClipData.newRawUri(subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Deletes the temporary copies made for sharing, so a report or log isn't kept after it's been sent. */
    fun cleanUp(context: Context) {
        File(context.cacheDir, "exports").listFiles()?.forEach { it.delete() }
    }

    /** Keeps a copy of a picture in Pictures › OBD2 Reader, so it's in the gallery too (Android 10 and later). */
    fun saveToPictures(context: Context, file: File) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/OBD2 Reader")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
        }
    }

    /**
     * Saves a copy of [file] to Downloads › OBD2 Reader (Android 10 and later). Returns false if
     * it couldn't, so the caller can let the user pick a folder instead.
     */
    fun saveToDownloads(context: Context, file: File, mime: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return false
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/OBD2 Reader")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
        } catch (e: Exception) {
            resolver.delete(uri, null, null); false
        }
    }

    fun exportFile(context: Context, name: String): File =
        File(File(context.cacheDir, "exports").apply { mkdirs() }, name)

    /**
     * The VIN lines for a report: the VIN from the user's plate if they entered one, and what the
     * car's engine computer sent when that's different, so a mechanic sees both.
     */
    fun vinLines(context: Context, carVin: String?): List<String> {
        val user = AppPrefs.userVin(AppPrefs.of(context))
        return when {
            user != null -> listOfNotNull("VIN: $user (from the VIN plate)",
                carVin?.takeIf { it != user }?.let { "VIN reported by the car's engine computer: $it" })
            carVin != null -> listOf("VIN: $carVin" + if (carVin.length == 17 && !VinDecoder.isValid(carVin)) " (fails its check digit, so it may not be this car's)" else "")
            else -> emptyList()
        }
    }

    /** A plain-text report of the car's trouble codes with full explanations, for a mechanic or a forum post. */
    fun codeReport(context: Context, s: ObdSession.State, carLabel: String?): File {
        val codes = s.codes
        val stamp = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())
        val text = buildString {
            appendLine("OBD2 Reader trouble code report")
            appendLine("Date: $stamp")
            val info = ObdSession.vinFor(context)?.let { VinDecoder.decode(it) }
            val vehicle = listOfNotNull(info?.make, info?.modelYear?.let { "$it model year" }).joinToString(", ")
            if (vehicle.isNotEmpty()) appendLine("Vehicle: $vehicle")
            if (carLabel != null) appendLine("Car chosen in the app: $carLabel")
            vinLines(context, s.vin).forEach { appendLine(it) }
            s.summary?.let { appendLine("Adapter and protocol: $it") }
            appendLine()
            if (codes == null || codes.total == 0) {
                appendLine("No trouble codes stored or pending.")
                return@buildString
            }
            appendLine("${codes.total} trouble codes (${codes.stored.size} stored, ${codes.pending.size} pending)")
            fun section(code: String, stored: Boolean) {
                val d = DtcLibrary.lookup(code, ObdSession.carMake(context), ObdSession.carYear(context))
                appendLine()
                appendLine("$code  ${d.title}")
                appendLine("Status: " + if (stored) "Stored, check-engine light on" else "Pending, not yet confirmed")
                appendLine("Urgency: ${d.severity.label}. ${d.severity.advice}")
                if (d.system.isNotEmpty()) appendLine("Type: ${d.system}")
                appendLine("What it means: ${d.meaning}")
                if (d.symptoms.isNotEmpty()) { appendLine("You might notice:"); d.symptoms.forEach { appendLine("  - $it") } }
                if (d.causes.isNotEmpty()) { appendLine("Common causes:"); d.causes.forEach { appendLine("  - $it") } }
                if (d.checks.isNotEmpty()) { appendLine("What to check:"); d.checks.forEachIndexed { i, c -> appendLine("  ${i + 1}. $c") } }
            }
            codes.stored.forEach { section(it, true) }
            codes.pending.forEach { section(it, false) }
            appendLine()
            appendLine("Explanations are general guidance for generic OBD2 codes. Confirm with a mechanic before replacing parts.")
        }
        return exportFile(context, "OBD2Reader_codes_${SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())}.txt").apply { writeText(text) }
    }

    /** The raw adapter conversation, for troubleshooting a stubborn adapter or car. */
    fun adapterLog(context: Context, lines: List<String>, s: ObdSession.State): File {
        val text = buildString {
            appendLine("OBD2 Reader adapter log, ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}")
            appendLine("State: ${s.title}. ${s.detail}")
            s.summary?.let { appendLine("Adapter and protocol: $it") }
            appendLine()
            lines.forEach { appendLine(it) }
        }
        return exportFile(context, "OBD2Reader_adapter_log.txt").apply { writeText(text) }
    }
}

/**
 * Scores the current drive (see [DrivingScore]) from the live readings, in memory only: nothing
 * about the drive is saved. A new connection starts a new drive.
 */
object DriveScorer {

    private val score = com.obdreader.app.obd.DrivingScore()
    private val _results = MutableStateFlow<Map<com.obdreader.app.obd.DrivingScore.Style, com.obdreader.app.obd.DrivingScore.Result>>(emptyMap())
    val results: StateFlow<Map<com.obdreader.app.obd.DrivingScore.Style, com.obdreader.app.obd.DrivingScore.Result>> = _results.asStateFlow()

    private val _topSpeed = MutableStateFlow<Double?>(null)
    /** The highest speed since the app connected (ignition-off pauses don't reset it), in km/h. */
    val topSpeed: StateFlow<Double?> = _topSpeed.asStateFlow()

    private val _lastTrip = MutableStateFlow<com.obdreader.app.obd.TripSummary?>(null)
    /** The drive that just ended (when the connection closes), for the trip summary. */
    val lastTrip: StateFlow<com.obdreader.app.obd.TripSummary?> = _lastTrip.asStateFlow()
    private var tripStart = 0L
    private var tripEnded = false
    /** When the ignition went off during this drive with the adapter still connected, while it's off. */
    private var offSince: Long? = null
    private var appContext: Context? = null

    private fun newTrip() {
        score.reset(); latest.clear(); _topSpeed.value = null; publish(force = true)
        tripStart = System.currentTimeMillis(); tripEnded = false; offSince = null
    }

    /**
     * Sums up the drive that ended at [endedAt] and keeps it as the last drive on the phone straight
     * away, so its summary (and its drive card, with the route) is there even if the app wasn't on
     * screen when the drive ended.
     */
    private fun endTrip(endedAt: Long) {
        if (tripEnded) return
        tripEnded = true
        val all = com.obdreader.app.obd.DrivingScore.Style.values().associateWith { score.result(it) }
        val trip = com.obdreader.app.obd.TripSummary.from(all.values.first(), all, tripStart, maxOf(endedAt, tripStart))
        if (!trip.isDrive) return
        appContext?.let { AppPrefs.of(it).edit().putString(PREF_LAST_TRIP, trip.encode()).apply() }
        _lastTrip.value = trip
    }

    private var started = false
    private val latest = mutableMapOf<String, Double>()
    private var lastPublish = 0L

    /** Sets the redline used by the Racing score (from the car the user chose). */
    fun setRedline(rpm: Float) { score.redline = rpm }

    /** Where the last drive is kept, so its summary can still be shown after the app was closed. */
    const val PREF_LAST_TRIP = "last_trip"

    fun start(context: Context? = null) {
        context?.let { appContext = it.applicationContext }
        if (started) return
        started = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            var last: ObdSession.Phase? = null
            ObdSession.state.collect { s ->
                if (s.phase == ObdSession.Phase.CONNECTING && (last == null || last == ObdSession.Phase.IDLE || last == ObdSession.Phase.ERROR)) {
                    newTrip()
                }
                // Back from ignition off: the same drive after a short stop, a new one after a long one.
                if (s.phase == ObdSession.Phase.CONNECTED) {
                    if (tripEnded) newTrip()
                    offSince = null
                }
                if (s.phase == ObdSession.Phase.WAITING && offSince == null && last == ObdSession.Phase.CONNECTED) {
                    offSince = System.currentTimeMillis()
                }
                // The drive ends when the connection closes (Disconnect, or the car stayed off).
                val wasOn = last == ObdSession.Phase.CONNECTED || last == ObdSession.Phase.WAITING
                if (wasOn && (s.phase == ObdSession.Phase.IDLE || s.phase == ObdSession.Phase.ERROR)) {
                    endTrip(offSince ?: System.currentTimeMillis())
                    offSince = null
                }
                last = s.phase
            }
        }
        // It also ends once the ignition has been off for a while with the adapter still connected
        // (many cars keep the OBD2 port powered). Before, only Disconnect ended a drive, so one that
        // ended with the car simply switched off never got its summary.
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(15_000)
                val off = offSince ?: continue
                if (!tripEnded && com.obdreader.app.obd.TripRules.endedByIgnitionOff(off, System.currentTimeMillis())) endTrip(off)
            }
        }
        scope.launch {
            ObdSession.samples.collect { s ->
                latest[s.key] = s.value
                // Speed drives the clock: every speed reading is one moment of the drive.
                if (s.key == "0D") {
                    score.update(s.timeMs, s.value, latest["0C"], latest["11"], latest["04"], latest["05"], latest["5C"])
                    _topSpeed.value = score.topSpeed
                    publish()
                }
            }
        }
    }

    private fun publish(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastPublish < 500) return
        lastPublish = now
        _results.value = com.obdreader.app.obd.DrivingScore.Style.values().associateWith { score.result(it) }
    }
}
