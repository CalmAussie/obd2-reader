package com.obdreader.app.obd

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Back up and restore: one file with the app's settings, the chosen car, styles, reading choices,
 * custom readings, alert settings, drive history and recorded drives, for moving to a new phone.
 *
 * The file is a zip with three kinds of entry, and nothing else:
 *  - `backup.txt` first: what made it, when, the format number and whether it holds routes or a VIN;
 *  - `settings.txt`: one setting a line, as "type, name, value" with the value in Base64;
 *  - `drives/<name>.csv`: each recorded drive, as the app saved it.
 *
 * Restoring reads a file the user picked, so it's treated as untrusted: only settings the app knows
 * are taken, each checked for its type, size and allowed values (a custom reading must still be a
 * read-only request); drive files must be named and shaped like the app's own; sizes are capped
 * while reading (not trusted from the zip's own header), and entry names are never used as paths.
 * Nothing in a backup can make the app send anything but its usual read-only requests.
 */
object BackupFile {

    const val FORMAT = 1
    const val MAGIC = "OBD2 Reader backup"
    const val INFO = "backup.txt"
    const val SETTINGS = "settings.txt"
    const val DRIVES = "drives/"
    const val MIME = "application/zip"

    // Limits on what a restore reads, counted as it's unpacked.
    const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
    const val MAX_DRIVE_BYTES = 64L * 1024 * 1024
    const val MAX_SETTINGS_BYTES = 4L * 1024 * 1024
    const val MAX_INFO_BYTES = 4L * 1024
    const val MAX_DRIVES = 1000
    const val MAX_SETTINGS = 400

    class BadBackup(message: String) : IOException(message)

    /** One saved setting: [value] is a String, Int, Boolean, Long or Float. */
    data class Setting(val key: String, val value: Any)

    data class Info(
        val format: Int,
        val appVersion: String?,
        val madeAtMs: Long?,
        val hasRoutes: Boolean,
        val hasVin: Boolean,
    )

    /** What a backup holds, ready to restore: its settings (checked) and its drives (unpacked into the staging folder). */
    data class Contents(
        val info: Info,
        val settings: List<Setting>,
        /** Settings this version doesn't know, or that failed their checks: left out. */
        val skipped: Int,
        val drives: List<File>,
    ) {
        fun has(key: String) = settings.any { it.key == key }
        fun value(key: String): Any? = settings.firstOrNull { it.key == key }?.value
    }

    // ---------- What may be in a backup ----------

    private enum class Type(val tag: String) { TEXT("s"), INT("i"), BOOL("b"), LONG("l"), FLOAT("f") }

    /**
     * How one setting is checked. [clean] returns the value to keep (perhaps tidied up) or null to
     * leave it out. [multiline] lets the text have line breaks (history lists and custom readings).
     */
    private class Rule(
        val type: Type,
        val maxChars: Int = 200,
        val multiline: Boolean = false,
        val clean: (Any) -> Any? = { it },
    )

    private val ID = Regex("[a-z0-9_]{1,40}")
    private val NAME = Regex("[A-Za-z0-9_]{1,40}")
    private val TOKEN_LIST = Regex("[A-Za-z0-9_:. ,|+\\-]{0,4000}")
    private val HOST = Regex("[A-Za-z0-9.:\\-]{1,253}")
    private val VIN = Regex("[A-HJ-NPR-Z0-9]{11,17}")

    private fun text(maxChars: Int = 200, check: (String) -> Boolean = { true }) =
        Rule(Type.TEXT, maxChars) { v -> (v as String).takeIf(check) }
    private fun id() = text(40) { ID.matches(it) }
    /** An option's name, as the app saves it ("EVERYDAY", "ZERO_TO_100"). */
    private fun name() = text(40) { NAME.matches(it) }
    private fun bool() = Rule(Type.BOOL)
    private fun int(range: IntRange) = Rule(Type.INT) { v -> (v as Int).takeIf { it in range } }
    private fun history(maxChars: Int, tidy: (String) -> String) =
        Rule(Type.TEXT, maxChars, multiline = true) { v -> tidy(v as String).ifEmpty { null } }

    /** Every setting a backup carries. Anything else in a backup is skipped. */
    private val RULES: Map<String, Rule> = mapOf(
        // Styles and the home-screen icon.
        "theme" to id(), "style_auto" to text(10) { DayNight.mode(it).key == it },
        "style_day" to id(), "style_night" to id(), "app_icon" to id(),
        // The car.
        "car_make" to text(60), "car_year" to int(1975..2100), "car_model" to text(80), "car_engine" to text(80),
        "car_market" to text(40) { Market.byName(it) != null }, "car_asked" to bool(),
        "protocol" to text(3) { it == ObdProtocols.AUTO || ObdProtocols.byCode(it) != null },
        "last_protocol" to text(3) { ObdProtocols.byCode(it) != null },
        "vin_privacy" to text(10) { VinPrivacy.of(it).key == it },
        "user_vin" to text(17) { VIN.matches(it) }, "user_vin_car" to text(160),
        // Connection.
        "wifi_host" to text(253) { HOST.matches(it) }, "wifi_port" to int(1..65535),
        "auto_reconnect" to bool(), "pip" to bool(),
        // Units, readings and alerts.
        "units_mph" to bool(), "units_fahrenheit" to bool(),
        "units_pressure" to text(20) { v -> Units.Pressure.values().any { it.name == v } },
        "alerts" to bool(), "battery_watch" to bool(),
        "car_readings" to text(4000) { TOKEN_LIST.matches(it) }, "widget_readings" to text(4000) { TOKEN_LIST.matches(it) },
        "dash_slots" to text(4000) { TOKEN_LIST.matches(it) }, "graph_channels" to text(4000) { TOKEN_LIST.matches(it) },
        "graph_tab" to name(), "score_style" to name(), "perf_mode" to name(),
        // Custom readings: each one must still pass the same checks as when it's typed in (read-only requests only).
        "custom_readings" to history(64 * 1024) { CustomReading.encodeAll(CustomReading.decodeAll(it).take(100)) },
        // Drives and history.
        "trip_history" to history(256 * 1024) { TripHistory.encode(TripHistory.parse(it)) },
        "last_trip" to Rule(Type.TEXT, 2000) { v -> TripSummary.decode(v as String)?.encode() },
        "perf_history" to Rule(Type.TEXT, 8000) { v -> PerformanceTimer.encode(PerformanceTimer.decode(v as String).takeLast(30)).ifEmpty { null } },
        "code_history" to history(256 * 1024) { CodeHistory.encode(CodeHistory.parse(it).take(CodeHistory.MAX_ENTRIES)) },
        "battery_checks" to history(16 * 1024) { t -> BatteryCheck.parseHistory(t).take(50).joinToString("\n") { it.encode() } },
    )

    /** Settings named by a pattern: which sections of Diagnostics are open, and each car's throttle calibration. */
    private fun ruleFor(key: String): Rule? = RULES[key] ?: when {
        key.startsWith("group_") && ID.matches(key.removePrefix("group_")) -> bool()
        key.startsWith("throttle_closed_") && key.length <= 200 -> Rule(Type.FLOAT) { v -> (v as Float).takeIf { it in 0f..100f } }
        else -> null
    }

    /** Whether the app backs up this setting at all (the rest is per-phone or short-lived state). */
    fun isBackedUp(key: String): Boolean = ruleFor(key) != null

    /** Settings that are lists the phone builds up: a restore adds the backup's entries to the phone's. */
    val MERGED = setOf("trip_history", "code_history", "battery_checks", "perf_history")

    /** Settings that identify the car itself, left out unless the user asks for them. */
    val VIN_KEYS = setOf("user_vin", "user_vin_car")

    /** Checks one setting as a restore would: the value to keep, or null to leave it out. */
    fun check(key: String, value: Any): Any? {
        val rule = ruleFor(key) ?: return null
        val typeOk = when (rule.type) {
            Type.TEXT -> value is String
            Type.INT -> value is Int
            Type.BOOL -> value is Boolean
            Type.LONG -> value is Long
            Type.FLOAT -> value is Float && value.isFinite()
        }
        if (!typeOk) return null
        if (value is String) {
            if (value.length > rule.maxChars) return null
            // No control characters, except line breaks in lists and the separator custom readings use.
            if (value.any { it < ' ' && !(rule.multiline && (it == '\n' || it == '\u001F')) || it == '\u007F' }) return null
        }
        return try { rule.clean(value) } catch (e: Exception) { null }
    }

    /** Joins a list setting from the phone with the backup's copy, so restoring never loses what's already on the phone. */
    fun merge(key: String, phone: String?, backup: String): String = when (key) {
        "trip_history" -> TripHistory.encode((TripHistory.parse(backup) + TripHistory.parse(phone)).distinctBy { it.startedAt })
        "code_history" -> CodeHistory.encode(
            (CodeHistory.parse(phone) + CodeHistory.parse(backup))
                .groupBy { Triple(it.car, it.kind, it.code) }.values
                .map { same -> same.maxBy { it.lastSeen }.copy(firstSeen = same.minOf { it.firstSeen }) }
                .sortedByDescending { it.lastSeen }.take(CodeHistory.MAX_ENTRIES),
        )
        "battery_checks" -> (BatteryCheck.parseHistory(phone) + BatteryCheck.parseHistory(backup))
            .distinctBy { it.atMs }.sortedByDescending { it.atMs }.take(50).joinToString("\n") { it.encode() }
        "perf_history" -> PerformanceTimer.encode(
            (PerformanceTimer.decode(phone) + PerformanceTimer.decode(backup)).distinct().sortedBy { it.atMs }.takeLast(30),
        )
        else -> backup
    }

    // ---------- Drive files ----------

    /** The app's own recording names: OBD2Reader_2026-11-13_142530.csv (older ones without seconds), maybe with a _2 added by a restore. */
    private val DRIVE_NAME = Regex("OBD2Reader_\\d{4}-\\d{2}-\\d{2}_\\d{4}(\\d{2})?(_\\d{1,3})?\\.csv")

    fun isDriveName(name: String) = DRIVE_NAME.matches(name)

    /** The column the route starts at in a drive's header line, or -1 if it has no route. */
    fun routeColumn(header: String): Int = splitHeader(header).indexOf("Latitude")

    /** A CSV header's names, with "quoted, names" kept whole. */
    private fun splitHeader(line: String): List<String> {
        val out = ArrayList<String>(); val cur = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    /** Copies a drive file, leaving out the route columns (Latitude, Longitude, accuracy) when [keepRoute] is false. */
    fun copyDrive(input: InputStream, out: OutputStream, keepRoute: Boolean) {
        if (keepRoute) { input.copyTo(out); return }
        val reader = input.bufferedReader(Charsets.UTF_8)
        val writer = out.bufferedWriter(Charsets.UTF_8)
        var cut = -1
        reader.lineSequence().forEachIndexed { i, line ->
            if (i == 0) {
                cut = routeColumn(line)
                writer.write(if (cut < 0) line else headerPrefix(line, cut))
            } else {
                // Data rows hold only numbers and the time, never quotes, so plain commas split them.
                writer.write(if (cut < 0) line else line.split(",").take(cut).joinToString(","))
            }
            writer.write("\n")
        }
        writer.flush()
    }

    /** The header line up to (not including) column [cut], keeping its original quoting. */
    private fun headerPrefix(line: String, cut: Int): String {
        var quoted = false; var column = 0
        for (i in line.indices) {
            val ch = line[i]
            if (ch == '"') quoted = !quoted
            if (ch == ',' && !quoted) { column++; if (column == cut) return line.substring(0, i) }
        }
        return line
    }

    // ---------- Writing ----------

    /** One recorded drive to put in the backup. */
    class Drive(val name: String, val open: () -> InputStream)

    /**
     * Writes a backup to [out]. [settings] are the phone's settings (anything not backed up is left
     * out here); the VIN only if [includeVin]; routes only if [includeRoutes].
     */
    fun write(
        out: OutputStream,
        settings: Map<String, Any?>,
        drives: List<Drive>,
        appVersion: String,
        madeAtMs: Long,
        includeRoutes: Boolean,
        includeVin: Boolean,
    ) {
        val kept = settings.entries
            .filter { (k, v) -> v != null && isBackedUp(k) && (includeVin || k !in VIN_KEYS) }
            .mapNotNull { (k, v) -> check(k, v!!)?.let { Setting(k, it) } }
            .sortedBy { it.key }
        val usable = drives.filter { isDriveName(it.name) }.take(MAX_DRIVES)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(INFO))
            zip.write(buildString {
                append(MAGIC).append('\n')
                append("format=").append(FORMAT).append('\n')
                append("app=").append(appVersion.filter { it.isLetterOrDigit() || it in ".-+ " }.take(40)).append('\n')
                append("made=").append(madeAtMs).append('\n')
                append("routes=").append(includeRoutes).append('\n')
                append("vin=").append(includeVin && kept.any { it.key in VIN_KEYS }).append('\n')
                append("settings=").append(kept.size).append('\n')
                append("drives=").append(usable.size).append('\n')
            }.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(SETTINGS))
            zip.write(kept.joinToString("") { encodeSetting(it) + "\n" }.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for (d in usable) {
                zip.putNextEntry(ZipEntry(DRIVES + d.name))
                d.open().use { copyDrive(it, NonClosing(zip), includeRoutes) }
                zip.closeEntry()
            }
        }
    }

    private class NonClosing(out: OutputStream) : java.io.FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    fun encodeSetting(s: Setting): String {
        val (tag, text) = when (val v = s.value) {
            is String -> Type.TEXT.tag to v
            is Int -> Type.INT.tag to v.toString()
            is Boolean -> Type.BOOL.tag to v.toString()
            is Long -> Type.LONG.tag to v.toString()
            is Float -> Type.FLOAT.tag to v.toString()
            else -> throw IllegalArgumentException("Can't save ${s.key}")
        }
        return "$tag\t${s.key}\t${b64(text)}"
    }

    /** One line of settings.txt back to a setting, or null if it's malformed. Not checked against the rules yet. */
    fun decodeSetting(line: String): Setting? {
        val p = line.split("\t")
        if (p.size != 3 || p[1].isEmpty() || p[1].length > 200) return null
        val text = try { String(Base64.getDecoder().decode(p[2]), Charsets.UTF_8) } catch (e: IllegalArgumentException) { return null }
        val value: Any = when (p[0]) {
            Type.TEXT.tag -> text
            Type.INT.tag -> text.toIntOrNull() ?: return null
            Type.BOOL.tag -> when (text) { "true" -> true; "false" -> false; else -> return null }
            Type.LONG.tag -> text.toLongOrNull() ?: return null
            Type.FLOAT.tag -> text.toFloatOrNull() ?: return null
            else -> return null
        }
        return Setting(p[1], value)
    }

    // ---------- Reading ----------

    /** Counts what's read and stops at [limit], whatever the zip claims an entry's size is. */
    private class Tally { var bytes = 0L }

    private class Capped(input: InputStream, private val limit: Long, private val what: String, private val total: Tally) : FilterInputStream(input) {
        var count = 0L
            private set

        private fun add(n: Int) {
            if (n <= 0) return
            count += n; total.bytes += n
            if (count > limit) throw BadBackup("$what in this file is bigger than the app allows (${limit / 1024 / 1024} MB).")
            if (total.bytes > MAX_TOTAL_BYTES) throw BadBackup("This file unpacks to more than ${MAX_TOTAL_BYTES / 1024 / 1024} MB, more than a backup can hold.")
        }

        override fun read(): Int = super.read().also { if (it >= 0) add(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { add(it) }
        override fun skip(n: Long): Long = throw IOException("not supported")
        override fun close() {} // the zip stays open for the next entry
    }

    /**
     * Reads a backup from [input]. Drives are unpacked into [staging] (an empty folder of the
     * app's own) under their checked names; nothing else is written anywhere. Throws [BadBackup]
     * with a reason a person can read if the file isn't a backup this version can restore.
     */
    fun read(input: InputStream, staging: File): Contents {
        val total = Tally()
        var info: Info? = null
        val settings = LinkedHashMap<String, Setting>()
        var skipped = 0
        var sawSettings = false
        val drives = ArrayList<File>()
        val names = HashSet<String>()
        try {
            ZipInputStream(input).use { zip ->
                var index = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!names.add(name)) throw BadBackup("This file has \"$name\" twice, so it isn't a backup the app made.")
                    when {
                        index == 0 && name != INFO -> throw BadBackup("This isn't an OBD2 Reader backup.")
                        name == INFO -> info = readInfo(Capped(zip, MAX_INFO_BYTES, "The backup's description", total).readBytes().toString(Charsets.UTF_8))
                        name == SETTINGS -> {
                            sawSettings = true
                            val lines = Capped(zip, MAX_SETTINGS_BYTES, "The settings", total).readBytes().toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }
                            if (lines.size > MAX_SETTINGS) throw BadBackup("This backup has more settings than the app has.")
                            for (line in lines) {
                                val s = decodeSetting(line)
                                val value = s?.let { check(it.key, it.value) }
                                if (s == null || value == null || s.key in settings) skipped++ else settings[s.key] = Setting(s.key, value)
                            }
                        }
                        name.startsWith(DRIVES) && !entry.isDirectory -> {
                            val file = name.removePrefix(DRIVES)
                            if (!isDriveName(file)) throw BadBackup("This backup has a file the app didn't make (\"${name.take(60)}\").")
                            if (drives.size >= MAX_DRIVES) throw BadBackup("This backup has more than $MAX_DRIVES drives.")
                            // The name was checked against the app's own pattern, so it can't lead out of the folder.
                            val target = File(staging, file)
                            if (target.parentFile?.canonicalFile != staging.canonicalFile) throw BadBackup("This backup has a file name the app can't use.")
                            val capped = Capped(zip, MAX_DRIVE_BYTES, "A drive", total)
                            target.outputStream().use { capped.copyTo(it) }
                            if (!looksLikeDrive(target)) { target.delete(); throw BadBackup("\"$file\" in this backup isn't a recorded drive.") }
                            drives += target
                        }
                        else -> throw BadBackup("This backup has something the app doesn't know (\"${name.take(60)}\"), so it wasn't restored.")
                    }
                    zip.closeEntry()
                    index++
                }
            }
        } catch (e: BadBackup) {
            drives.forEach { it.delete() }
            throw e
        } catch (e: IOException) {
            drives.forEach { it.delete() }
            throw BadBackup("This file couldn't be read as a backup (${e.message ?: "it may be damaged"}).")
        }
        val i = info ?: throw BadBackup("This isn't an OBD2 Reader backup.")
        if (!sawSettings) { drives.forEach { it.delete() }; throw BadBackup("This backup is missing its settings, so it may be damaged.") }
        return Contents(i, settings.values.toList(), skipped, drives)
    }

    private fun readInfo(text: String): Info {
        val lines = text.lines()
        if (lines.firstOrNull() != MAGIC) throw BadBackup("This isn't an OBD2 Reader backup.")
        val m = lines.drop(1).mapNotNull { l -> l.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val format = m["format"]?.toIntOrNull() ?: throw BadBackup("This backup doesn't say which format it's in.")
        if (format > FORMAT) throw BadBackup("This backup was made by a newer version of the app. Update the app, then restore it.")
        if (format < 1) throw BadBackup("This backup's format isn't one the app knows.")
        return Info(
            format,
            m["app"]?.take(40)?.filter { it.isLetterOrDigit() || it in ".-+ " },
            m["made"]?.toLongOrNull(),
            m["routes"] == "true",
            m["vin"] == "true",
        )
    }

    /** A drive the app recorded: text, starting with its "Time,Seconds," header. */
    private fun looksLikeDrive(file: File): Boolean {
        val head = file.inputStream().use { s -> ByteArray(4096).let { b -> val n = s.read(b); if (n <= 0) ByteArray(0) else b.copyOf(n) } }
        if (!head.toString(Charsets.UTF_8).startsWith("Time,Seconds,")) return false
        // Text only: a zero byte means it's something else with a drive's first line.
        file.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) return true
                for (i in 0 until n) if (buf[i] == 0.toByte()) return false
            }
        }
    }
}
