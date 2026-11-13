package com.obdreader.app.obd

import java.io.IOException
import java.util.Locale

/**
 * Record and replay a car's replies.
 *
 * With "Record car replies" on, every request the app sends to a real car (all of them reads,
 * since [ReadOnlyGuard] has already let them through) is saved with the car's reply. The file can
 * then be replayed later, like the demo car, without the car or an adapter: useful for trying the
 * app on an emulator and for bug reports.
 *
 * A recording is plain text, so anyone can read what's in it before sharing it:
 *
 *     # OBD2 Reader recording 1
 *     # app 2.5.3
 *     # made 2026-11-13 10:42
 *     # car 2012 Ford Focus
 *     > ATSH7E0
 *     < OK
 *     > 010C
 *     < 41 0C 1A F8
 *     > 0902
 *     < (VIN hidden)
 *     > 03
 *     ! no reply
 *
 * Each request starts with "> ", each line of its reply with "< ", and "! no reply" marks a request
 * the adapter didn't answer in time. Replies that hold the VIN are replaced with "(VIN hidden)"
 * unless Hide VIN is set to show it in full.
 */
object ReplyRecording {

    const val HEADER = "# OBD2 Reader recording 1"
    const val VIN_HIDDEN = "(VIN hidden)"
    const val NO_REPLY = "! no reply"

    /** Largest recording the app saves or opens. */
    const val MAX_BYTES = 2 * 1024 * 1024
    /** About half an hour of live readings. */
    const val MAX_EXCHANGES = 20_000
    const val MAX_REPLY_LINES = 64
    const val MAX_LINE = 256
    const val MAX_COMMAND = 24
    private const val MAX_INFO = 80

    /** One request and the car's reply ([lines] is empty for an empty reply; null when there was no reply in time). */
    data class Exchange(val command: String, val lines: List<String>?)

    /** A recording: what it says about itself ([app], [made], [car]) and the exchanges in order. */
    data class Recording(
        val app: String?,
        val made: String?,
        val car: String?,
        val exchanges: List<Exchange>,
    )

    /** Why a recording file couldn't be opened, in words for the user. */
    class BadRecording(message: String) : IOException(message)

    /**
     * Requests whose reply spells out the VIN: OBD2 09 02, UDS 22 F190 and KWP2000 1A 90. Their
     * replies are hidden, and so is any other reply whose bytes read as the car's VIN.
     */
    private val VIN_REQUESTS = setOf("0902", "22F190", "1A90")

    fun normalize(command: String): String = command.replace(" ", "").uppercase(Locale.US)

    /** True when this reply has to be hidden: it answers a VIN request, or its bytes spell a VIN. */
    fun holdsVin(command: String, lines: List<String>, known: Collection<String>): Boolean {
        if (normalize(command) in VIN_REQUESTS) return true
        val text = ascii(lines)
        if (known.any { it.length >= 11 && text.contains(it) }) return true
        return VIN_IN_TEXT.containsMatchIn(text)
    }

    /** 17 VIN characters (no I, O or Q) with at least one letter, read from a reply's bytes. */
    private val VIN_IN_TEXT = Regex("(?=[A-HJ-NPR-Z0-9]{17})(?=[A-HJ-NPR-Z0-9]{0,16}[A-HJ-NPR-Z])[A-HJ-NPR-Z0-9]{17}")

    /**
     * The reply's bytes as text, line by line (CAN frame numbers like "0:" dropped), joined so a
     * VIN split over frames reads whole. Anything unprintable becomes a space.
     */
    private fun ascii(lines: List<String>): String = lines.joinToString("") { line ->
        val hex = line.replace(" ", "").uppercase(Locale.US).replace(Regex("^[0-9A-F]:"), "")
        if (!hex.all { it in '0'..'9' || it in 'A'..'F' }) " "
        else hex.chunked(2).filter { it.length == 2 }.map { b ->
            val c = b.toInt(16).toChar()
            if (c in ' '..'~') c else ' '
        }.joinToString("")
    }

    /**
     * Writes [recording] as text. Unless [showVin], replies that hold the VIN are replaced with
     * "(VIN hidden)" ([known] are VINs the app already knows for this car, so they're caught too).
     */
    fun encode(recording: Recording, showVin: Boolean, known: Collection<String> = emptyList()): String = buildString {
        appendLine(HEADER)
        recording.app?.let { appendLine("# app ${clean(it)}") }
        recording.made?.let { appendLine("# made ${clean(it)}") }
        recording.car?.let { appendLine("# car ${clean(it)}") }
        for (x in recording.exchanges) {
            appendLine("> ${normalize(x.command)}")
            val lines = x.lines
            when {
                lines == null -> appendLine(NO_REPLY)
                !showVin && holdsVin(x.command, lines, known) -> appendLine("< $VIN_HIDDEN")
                else -> lines.take(MAX_REPLY_LINES).forEach { appendLine("< ${clean(it, MAX_LINE)}") }
            }
        }
    }

    /** Printable ASCII only, cut to [max] characters. */
    private fun clean(s: String, max: Int = MAX_INFO): String =
        s.map { if (it in ' '..'~') it else ' ' }.joinToString("").trim().take(max)

    /**
     * Reads a recording from a file the user picked, which may not be one this app made. Anything
     * unexpected stops it with [BadRecording]: a missing header, a line that's too long or not
     * plain text, a request that isn't a read, too many lines in one reply, or too many requests.
     */
    fun parse(text: String): Recording {
        if (text.length > MAX_BYTES) throw BadRecording("This recording is larger than 2 MB.")
        val lines = text.split('\n').map { it.removeSuffix("\r") }
        if (lines.firstOrNull()?.trim() != HEADER) throw BadRecording("This isn't an OBD2 Reader recording.")
        var app: String? = null
        var made: String? = null
        var car: String? = null
        val exchanges = ArrayList<Exchange>()
        var command: String? = null
        var reply: MutableList<String>? = null
        var noReply = false
        var seenExchange = false

        fun finish() {
            val c = command ?: return
            exchanges.add(Exchange(c, if (noReply) null else reply.orEmpty().toList()))
            if (exchanges.size > MAX_EXCHANGES) throw BadRecording("This recording has more than ${String.format(Locale.US, "%,d", MAX_EXCHANGES)} requests.")
            command = null; reply = null; noReply = false
        }

        for ((i, raw) in lines.withIndex().drop(1)) {
            val n = i + 1
            if (raw.length > MAX_LINE + 2) throw BadRecording("Line $n is too long.")
            if (!raw.all { it in ' '..'~' || it == '\t' }) throw BadRecording("Line $n isn't plain text.")
            val line = raw.trimEnd()
            when {
                line.isBlank() -> {}
                line.startsWith("#") -> {
                    if (seenExchange) throw BadRecording("Line $n: notes belong at the top of the file.")
                    val body = line.removePrefix("#").trim()
                    val key = body.substringBefore(' ')
                    val value = clean(body.substringAfter(' ', ""))
                    when (key) {
                        "app" -> app = value
                        "made" -> made = value
                        "car" -> car = value
                    }
                }
                line.startsWith(">") -> {
                    finish()
                    val c = normalize(line.removePrefix(">"))
                    if (c.isEmpty() || c.length > MAX_COMMAND || !c.all { it in '0'..'9' || it in 'A'..'Z' }) {
                        throw BadRecording("Line $n isn't a request the app sends.")
                    }
                    if (!ReadOnlyGuard.isAllowed(c)) throw BadRecording("Line $n asks the car to change something, and the app only reads.")
                    command = c
                    reply = ArrayList()
                    seenExchange = true
                }
                line.startsWith("<") -> {
                    val r = reply ?: throw BadRecording("Line $n is a reply with no request before it.")
                    if (noReply) throw BadRecording("Line $n is a reply to a request marked as not answered.")
                    if (r.size >= MAX_REPLY_LINES) throw BadRecording("Line $n: one reply has more than $MAX_REPLY_LINES lines.")
                    val text = line.removePrefix("<").trim()
                    if (text.isNotEmpty()) r.add(text)
                }
                line == NO_REPLY -> {
                    if (command == null || reply?.isNotEmpty() == true) throw BadRecording("Line $n: \"$NO_REPLY\" has to follow a request.")
                    noReply = true
                }
                else -> throw BadRecording("Line $n isn't part of a recording.")
            }
        }
        finish()
        if (exchanges.isEmpty()) throw BadRecording("This recording is empty.")
        return Recording(app, made, car, exchanges)
    }
}

/**
 * Collects a car's replies while connected. Stops adding once the recording would be larger than
 * [ReplyRecording.MAX_BYTES] or longer than [ReplyRecording.MAX_EXCHANGES], keeping the start.
 */
class ReplyRecorder {
    private val exchanges = ArrayList<ReplyRecording.Exchange>()
    private var bytes = 0

    @Volatile var full = false
        private set

    val size: Int @Synchronized get() = exchanges.size

    @Synchronized
    fun add(command: String, lines: List<String>?) {
        if (full) return
        // Only reads go to the car anyway; this keeps anything else out of the file too.
        if (!ReadOnlyGuard.isAllowed(command)) return
        val cost = command.length + 4 + (lines?.sumOf { it.length + 3 } ?: 12)
        if (exchanges.size >= ReplyRecording.MAX_EXCHANGES || bytes + cost > ReplyRecording.MAX_BYTES - 4096) {
            full = true
            return
        }
        exchanges.add(ReplyRecording.Exchange(ReplyRecording.normalize(command), lines?.take(ReplyRecording.MAX_REPLY_LINES)))
        bytes += cost
    }

    @Synchronized
    fun snapshot(): List<ReplyRecording.Exchange> = exchanges.toList()
}

/** Passes everything to [inner] and hands each request and its reply to [recorder]. */
class RecordingTransport(private val inner: ObdTransport, private val recorder: ReplyRecorder) : ObdTransport {
    override val name: String get() = inner.name

    override fun open() = inner.open()

    override fun exchange(command: String, timeoutMs: Long): String {
        val raw = try {
            inner.exchange(command, timeoutMs)
        } catch (e: AdapterTimeoutException) {
            recorder.add(command, null)
            throw e
        }
        recorder.add(command, ElmParser.cleanLines(raw, command))
        return raw
    }

    override fun close() = inner.close()
}

/**
 * Plays a recording back as if it were the car, like the demo car. Each request gets the replies
 * recorded for it in turn (going round again at the end), so live readings move as they did.
 * The adapter settings in force (header, network and so on) are part of the match, since the same
 * request asks a different module after them. Nothing is sent anywhere.
 */
class ReplayTransport(val recording: ReplyRecording.Recording, val label: String) : ObdTransport {
    override val name: String get() = "Recording: $label"

    private val replies: Map<String, List<List<String>?>>
    private val next = HashMap<String, Int>()
    private val context = Context()
    @Volatile private var open = false

    init {
        val map = LinkedHashMap<String, MutableList<List<String>?>>()
        val ctx = Context()
        for (x in recording.exchanges) {
            map.getOrPut(ctx.key(x.command)) { ArrayList() }.add(x.lines)
            ctx.track(x.command)
        }
        replies = map
    }

    /**
     * The adapter settings that change which module answers, or how its reply looks: the header
     * (ATSH, ATCP), the init address (ATIIA), the receive filter (ATCRA), headers on or off (ATH)
     * and the network (STP). A request is matched together with these.
     */
    private class Context {
        private val settings = sortedMapOf<String, String>()

        fun key(c: String): String =
            if (c.startsWith("AT") || c.startsWith("ST")) c else settings.entries.joinToString(";") { "${it.key}=${it.value}" } + "|" + c

        fun track(c: String) {
            when {
                c == "ATZ" || c == "ATD" -> settings.clear()
                c == "ATH0" || c == "ATH1" -> settings["H"] = c.substring(3)
                Regex("STP[0-9]{1,2}").matches(c) -> settings["STP"] = c.substring(3)
                else -> for (p in listOf("ATSH", "ATCP", "ATIIA", "ATCRA")) if (c.startsWith(p)) { settings[p] = c.substring(p.length); break }
            }
        }
    }

    override fun open() {
        Thread.sleep(300)
        open = true
    }

    override fun close() {
        open = false
    }

    @Synchronized
    override fun exchange(command: String, timeoutMs: Long): String {
        if (!open) throw IOException("Replay stopped")
        Thread.sleep(PACE_MS)
        val c = ReplyRecording.normalize(command)
        val k = context.key(c)
        context.track(c)
        val list = replies[k]
        val lines: List<String>? = if (!list.isNullOrEmpty()) take(k, list) else combined(c) ?: fallback(c)
        if (lines == null) throw AdapterTimeoutException("No reply to '$c' in the recording")
        val shown = lines.map { if (it == ReplyRecording.VIN_HIDDEN) "NO DATA" else it }
        return shown.joinToString("\r") + "\r\r"
    }

    /** The next of [list], the replies recorded for [k], going round again at the end (null: no reply in time). */
    private fun take(k: String, list: List<List<String>?>): List<String>? {
        val i = next[k] ?: 0
        next[k] = (i + 1) % list.size
        return list[i]
    }

    /**
     * Several live readings in one request (01 0C 0D 05…) that the recording only has one at a time,
     * as when it was made by another version of the app or on a car that answers one at a time:
     * the reply is put together from each reading's own recorded reply.
     */
    private fun combined(c: String): List<String>? {
        if (!c.startsWith("01") || c.length < 6 || c.length % 2 != 0) return null
        val parts = c.substring(2).chunked(2).mapNotNull { pid ->
            val k = context.key("01$pid")
            val one = replies[k]?.takeIf { it.isNotEmpty() }?.let { take(k, it) }
            one?.singleOrNull()?.replace(" ", "")?.takeIf { it.startsWith("41$pid") }?.substring(2)
        }
        return if (parts.isEmpty()) null else listOf("41" + parts.joinToString(""))
    }

    /** A request the recording doesn't have: the adapter's own commands just say OK, anything for the car gets no data. */
    private fun fallback(c: String): List<String> = when {
        c == "ATZ" || c == "ATI" -> listOf("ELM327 v1.5")
        c.startsWith("AT") -> listOf("OK")
        else -> listOf("NO DATA")
    }

    companion object {
        /** A short pause per request, like a real adapter, so polling doesn't spin. */
        const val PACE_MS = 25L
    }
}
