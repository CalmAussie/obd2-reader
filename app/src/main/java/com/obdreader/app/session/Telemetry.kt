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

    data class Recording(val file: File, val startedMs: Long, val rows: Int)

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

    fun start(context: Context) {
        if (job?.isActive == true) return
        // Seconds in the name, so two drives recorded in the same minute don't overwrite each other.
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val file = File(folder(context), "OBD2Reader_$stamp.csv")
        val started = System.currentTimeMillis()
        _active.value = Recording(file, started, 0)
        val latest = mutableMapOf<String, Double>()
        var fresh = false

        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val out: BufferedWriter = file.bufferedWriter()
            val recording = coroutineContext[Job]
            try {
                out.write("Time,Seconds," + columns.joinToString(",") { csv(it.second) })
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
                    out.newLine()
                    rows++
                    if (rows % 25 == 0) out.flush()
                    _active.value = Recording(file, started, rows)
                }
                listener.cancel(); stop.cancel()
            } finally {
                out.flush()
                out.close()
                // Nothing was recorded (never connected): don't leave an empty file behind.
                if ((_active.value?.rows ?: 0) == 0) file.delete()
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

    fun share(context: Context, file: File, mime: String, subject: String) {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            clipData = ClipData.newRawUri(subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Deletes the temporary copies made for sharing, so a report or log isn't kept after it's been sent. */
    fun cleanUp(context: Context) {
        File(context.cacheDir, "exports").listFiles()?.forEach { it.delete() }
    }

    private fun exportFile(context: Context, name: String): File =
        File(File(context.cacheDir, "exports").apply { mkdirs() }, name)

    /** A plain-text report of the car's trouble codes with full explanations, for a mechanic or a forum post. */
    fun codeReport(context: Context, s: ObdSession.State, carLabel: String?): File {
        val codes = s.codes
        val stamp = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())
        val text = buildString {
            appendLine("OBD2 Reader trouble code report")
            appendLine("Date: $stamp")
            val info = s.vin?.let { VinDecoder.decode(it) }
            val vehicle = listOfNotNull(info?.make, info?.modelYear?.let { "$it model year" }).joinToString(", ")
            if (vehicle.isNotEmpty()) appendLine("Vehicle: $vehicle")
            if (carLabel != null) appendLine("Car chosen in the app: $carLabel")
            s.vin?.let { appendLine("VIN: $it") }
            s.summary?.let { appendLine("Adapter and protocol: $it") }
            appendLine()
            if (codes == null || codes.total == 0) {
                appendLine("No trouble codes stored or pending.")
                return@buildString
            }
            appendLine("${codes.total} trouble codes (${codes.stored.size} stored, ${codes.pending.size} pending)")
            fun section(code: String, stored: Boolean) {
                val d = DtcLibrary.lookup(code, ObdSession.carMake(context))
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

    private var started = false
    private val latest = mutableMapOf<String, Double>()
    private var lastPublish = 0L

    /** Sets the redline used by the Racing score (from the car the user chose). */
    fun setRedline(rpm: Float) { score.redline = rpm }

    fun start() {
        if (started) return
        started = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            var last: ObdSession.Phase? = null
            ObdSession.state.collect { s ->
                if (s.phase == ObdSession.Phase.CONNECTING && (last == null || last == ObdSession.Phase.IDLE || last == ObdSession.Phase.ERROR)) {
                    score.reset(); latest.clear(); publish(force = true)
                }
                last = s.phase
            }
        }
        scope.launch {
            ObdSession.samples.collect { s ->
                latest[s.key] = s.value
                // Speed drives the clock: every speed reading is one moment of the drive.
                if (s.key == "0D") {
                    score.update(s.timeMs, s.value, latest["0C"], latest["11"], latest["04"], latest["05"], latest["5C"])
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
