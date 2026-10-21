package com.obdreader.app.session

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.obdreader.app.obd.AdapterTimeoutException
import com.obdreader.app.obd.ThrottleCalibration
import com.obdreader.app.obd.CarCoverage
import com.obdreader.app.obd.ChargingStatus
import com.obdreader.app.obd.ElmParser
import com.obdreader.app.obd.LockedAdapters
import com.obdreader.app.obd.BleTransport
import com.obdreader.app.obd.BluetoothTransport
import com.obdreader.app.obd.DemoTransport
import com.obdreader.app.obd.ElmClient
import com.obdreader.app.obd.IgnitionOffException
import com.obdreader.app.obd.IgnitionWatch
import com.obdreader.app.obd.ObdException
import com.obdreader.app.obd.ObdProtocols
import com.obdreader.app.obd.ObdTransport
import com.obdreader.app.obd.Pid
import com.obdreader.app.obd.Pids
import com.obdreader.app.obd.ReconnectPolicy
import com.obdreader.app.obd.DtcLibrary
import com.obdreader.app.obd.VehicleCatalog
import com.obdreader.app.obd.WifiTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The one connection to the car, shared by the phone screens, the Android Auto
 * screen and the background service. It lives for the whole app process, so it
 * survives the phone screen turning off, the app going to the background and
 * style changes.
 *
 * With "Reconnect automatically" on, a lost connection isn't the end: if the car goes
 * quiet (ignition off) the app keeps the adapter link and resumes the moment the car
 * answers again; if the link itself drops, it keeps reconnecting to the same adapter,
 * using the protocol that worked last time, until it gets through or 30 minutes pass.
 */
object ObdSession {

    enum class Phase { IDLE, CONNECTING, CONNECTED, WAITING, ERROR }

    data class Codes(val stored: List<String>, val pending: List<String>) {
        val total: Int get() = stored.size + pending.size
    }

    data class State(
        val phase: Phase = Phase.IDLE,
        val title: String = "Not connected",
        val detail: String = IDLE_HINT,
        val adapterName: String? = null,
        val isDemo: Boolean = false,
        /** Latest values keyed by PID hex (e.g. "0C") or [VOLTAGE_KEY]; null means the car didn't answer. */
        val readings: Map<String, Double?> = emptyMap(),
        val unsupported: Set<String> = emptySet(),
        /** Readings shown through a stand-in, with the stand-in's name ("11" to "Accelerator pedal"). */
        val standIns: Map<String, String> = emptyMap(),
        /** Whether the battery looks like it's charging, estimated from voltage and engine speed (null until known). */
        val charging: ChargingStatus.State? = null,
        val protocolCode: String? = null,
        val summary: String? = null,
        val vin: String? = null,
        val vinChecked: Boolean = false,
        val codes: Codes? = null,
        /** Readings per second over the last few seconds, for the Graphs tab. */
        val rate: Double = 0.0,
    ) {
        val isConnected: Boolean get() = phase == Phase.CONNECTED
    }

    /** One timestamped reading, for live graphs and the recorder. */
    data class Sample(val key: String, val value: Double, val timeMs: Long)

    const val VOLTAGE_KEY = "VOLT"
    const val IDLE_HINT = "Plug your adapter into the car and turn the ignition on, or try demo mode."
    private const val MAX_LOG_LINES = 200
    private const val FIRST_CODE_CHECK_MS = 8_000L
    private const val MISSES_BEFORE_NA = 3
    private const val CODE_CHECK_MS = 120_000L

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    /**
     * The last trouble-code read, decoded. Live polling fills the log within seconds, so this is
     * kept separately and shown above the log, and included when the log is shared.
     */
    private val _codeLog = MutableStateFlow<List<String>>(emptyList())
    val codeLog: StateFlow<List<String>> = _codeLog.asStateFlow()

    /** The decoded codes followed by the raw adapter conversation, as shown and shared. */
    fun fullLog(codes: List<String> = _codeLog.value, lines: List<String> = _log.value): List<String> =
        if (codes.isEmpty()) lines else codes + "" + lines

    private val _samples = MutableSharedFlow<Sample>(extraBufferCapacity = 512)
    /** Every reading as it arrives, with its time. */
    val samples: SharedFlow<Sample> = _samples.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    var client: ElmClient? = null
        private set
    private var transport: ObdTransport? = null
    private var job: Job? = null

    /** Counts connections, so a cancelled one finishing late can't overwrite the state of the next. */
    private var generation = 0

    /**
     * PIDs (by hex) the Graphs tab is showing. They're read every cycle, and everything else
     * less often, so the graphs update as fast as the adapter allows.
     */
    @Volatile
    var focus: Set<String> = emptySet()

    val isActive: Boolean get() = job?.isActive == true

    /**
     * Connects to [t]. [auto] marks a connection the app started by itself (on launch or when
     * Android Auto starts), which keeps retrying instead of showing an error straight away.
     */
    fun connect(context: Context, t: ObdTransport, onConnected: (() -> Unit)? = null, auto: Boolean = false) {
        if (isActive) return
        val app = context.applicationContext
        appContext = app
        val prefs = AppPrefs.of(app)
        val isDemo = t is DemoTransport
        throttleKey = if (isDemo) null else "throttle_closed_" + (AppPrefs.savedCar(prefs)?.label ?: "unknown")
        throttle = ThrottleCalibration(throttleKey?.let { k -> prefs.getFloat(k, -1f).takeIf { it >= 0f }?.toDouble() })
        _log.value = emptyList()
        _codeLog.value = emptyList()
        _state.value = State(
            phase = Phase.CONNECTING,
            title = "Connecting to ${t.name}…",
            detail = "This can take up to 15 seconds the first time.",
            adapterName = t.name,
            isDemo = isDemo,
        )
        if (!isDemo) ObdConnectionService.start(app)
        val gen = ++generation
        fun isCurrent() = gen == generation

        job = scope.launch {
            var current = t
            var everConnected = false
            var attempt = 0
            var waitingSince = SystemClock.elapsedRealtime()
            var end = State()
            var first = true
            try {
                while (currentCoroutineContext().isActive) {
                    var lastError: Exception? = null
                    try {
                        runConnection(app, current, isDemo) {
                            everConnected = true
                            attempt = 0
                            if (first) onConnected?.invoke()
                            first = false
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                    } finally {
                        current.close()
                        if (isCurrent()) {
                            client = null
                            transport = null
                        }
                    }
                    // Disconnected by the user while the link was closing: stop here, quietly.
                    currentCoroutineContext().ensureActive()

                    val retry = !isDemo && AppPrefs.autoReconnect(prefs) && (everConnected || auto) && lastError !is NotAnAdapter
                    if (!retry) {
                        end = State(
                            phase = Phase.ERROR,
                            title = if (everConnected) "Connection lost" else "Couldn't connect",
                            detail = lastError?.message ?: "The connection closed.",
                        )
                        break
                    }
                    if (attempt == 0) waitingSince = SystemClock.elapsedRealtime()
                    val limit = if (everConnected) ReconnectPolicy.GIVE_UP_AFTER_MS else ReconnectPolicy.AUTO_START_GIVE_UP_MS
                    if (ReconnectPolicy.shouldGiveUp(waitingSince, SystemClock.elapsedRealtime(), limit)) {
                        end = if (everConnected) {
                            State(title = "Stopped reconnecting", detail = "The car didn't answer for 30 minutes. Tap Connect when you're back in the car.")
                        } else {
                            State(detail = "Couldn't find ${t.name}. Tap Connect when you're in the car with the ignition on.")
                        }
                        break
                    }
                    _state.update {
                        it.copy(
                            phase = Phase.WAITING,
                            title = "Waiting for the car",
                            detail = "Turn the ignition on. The app reconnects to ${t.name} automatically.",
                            readings = emptyMap(), charging = null,
                            rate = 0.0,
                        )
                    }
                    delay(ReconnectPolicy.delayFor(attempt++))
                    // A Bluetooth or Wi-Fi link can't be reopened once closed, so build a fresh one.
                    var next = lastAdapter(app)
                    while (next == null) {
                        if (ReconnectPolicy.shouldGiveUp(waitingSince, SystemClock.elapsedRealtime(), limit)) break
                        _state.update { it.copy(detail = "Turn Bluetooth on. The app reconnects to ${t.name} automatically.") }
                        delay(ReconnectPolicy.delayFor(Int.MAX_VALUE))
                        next = lastAdapter(app)
                    }
                    current = next ?: current
                }
            } finally {
                if (isCurrent()) {
                    client = null
                    transport = null
                    _state.value = end
                    ObdConnectionService.stop(app)
                }
            }
        }
    }

    /** Thrown for links that will never work, such as a Bluetooth device that isn't an OBD2 adapter. */
    private class NotAnAdapter(message: String) : Exception(message)

    /** Opens [t], finds the car's protocol and polls until the link drops. Waits out ignition-off periods. */
    private suspend fun runConnection(app: Context, t: ObdTransport, isDemo: Boolean, onConnected: () -> Unit) {
        val prefs = AppPrefs.of(app)
        transport = t
        val c = ElmClient(t) { line -> appendLog(line) }
        try {
            withContext(Dispatchers.IO) { t.open() }
        } catch (e: ObdException) {
            throw NotAnAdapter(e.message ?: "This device isn't an OBD2 adapter.")
        }
        _state.update { it.copy(phase = Phase.CONNECTING, title = "Talking to the car…", detail = "Detecting the vehicle's protocol.") }

        val chosen = prefs.getString(AppPrefs.PROTOCOL, ObdProtocols.AUTO) ?: ObdProtocols.AUTO
        val lastWorking = if (isDemo) null else prefs.getString(AppPrefs.LAST_PROTOCOL, null)
        val carOrder = if (isDemo) null else AppPrefs.savedCar(prefs)?.let { VehicleCatalog.recommend(it).order }
        val info = try {
            c.initialize(chosen, lastWorking, carOrder) { step ->
                _state.update { it.copy(title = "Talking to the car…", detail = step) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // A maker-locked adapter (ThinkDiag, BlueDriver) that doesn't speak ELM327: say why, and don't keep retrying.
            if (!c.identified) LockedAdapters.match(t.name)?.let { throw NotAnAdapter(LockedAdapters.message(it)) }
            throw e
        }

        client = c
        if (!isDemo && info.protocolCode != null) {
            prefs.edit().putString(AppPrefs.LAST_PROTOCOL, info.protocolCode).apply()
        }
        val connected = { s: State ->
            s.copy(phase = Phase.CONNECTED, title = "Connected to ${t.name}", detail = info.summary, summary = info.summary, protocolCode = info.protocolCode)
        }
        _state.update(connected)
        onConnected()
        readVinQuietly()

        val supported = try { c.supportedPids() } catch (e: AdapterTimeoutException) { null }
        // Some cars leave readings out of their supported list but answer them anyway (or the list
        // stops early), so ask once for each dashboard reading the list left out before giving up on it.
        val extra = if (supported == null) emptySet() else Pids.dashboard.filter { it.number !in supported }.filter { p ->
            try { c.readPid(p) != null } catch (e: AdapterTimeoutException) { false }
        }.map { it.number }.toSet()
        val base: List<Pid> = Pids.dashboard.filter { supported == null || it.number in supported || it.number in extra }
        // Stand-ins: a close standard reading for a dashboard one the car leaves out.
        val standIns = if (supported == null) emptyList() else CarCoverage.standInsFor(supported + extra)
        standIns.forEach { appendLog("= Using ${it.note}") }
        standInFor = standIns.associate { it.pid.hex to it.forHex }
        misses.clear()
        chargingWatch.reset()
        _state.update { it.copy(standIns = standIns.associate { s -> s.forHex to s.pid.label }) }
        val pids = base + standIns.map { it.pid }
        val covered = base.map { it.hex }.toSet() + standIns.map { it.forHex }
        _state.update { s -> s.copy(unsupported = Pids.dashboard.map { it.hex }.filter { it !in covered }.toSet()) }

        while (currentCoroutineContext().isActive) {
            try {
                pollLoop(c, pids)
            } catch (e: IgnitionOffException) {
                // The adapter is still there; the car has gone to sleep. Ask it every second and a
                // half whether it's awake, and carry straight on when it is.
                if (isDemo || !AppPrefs.autoReconnect(prefs)) throw IOException("The car stopped answering. Is the ignition off?")
                val since = SystemClock.elapsedRealtime()
                _state.update {
                    it.copy(phase = Phase.WAITING, title = "Ignition off", detail = "Waiting for the car. Live data comes back as soon as the ignition is on.", readings = emptyMap(), charging = null, rate = 0.0)
                }
                while (true) {
                    delay(ReconnectPolicy.WAKE_PROBE_MS)
                    val awake = try { c.probe() } catch (e: AdapterTimeoutException) { false }
                    if (awake) break
                    if (ReconnectPolicy.shouldGiveUp(since, SystemClock.elapsedRealtime())) throw IOException("The car didn't answer for 30 minutes.")
                }
                _state.update(connected)
            }
        }
    }

    fun disconnect() {
        transport?.close() // unblocks any read in progress
        job?.cancel()
    }

    /**
     * Connects to the last adapter by itself, if "Reconnect automatically" is on and there is one.
     * Used when the app opens and when Android Auto starts.
     */
    fun autoConnect(context: Context) {
        if (isActive || !AppPrefs.autoReconnect(AppPrefs.of(context))) return
        val t = lastAdapter(context) ?: return
        connect(context, t, auto = true)
    }

    /**
     * The adapter used last time, ready to connect, or null if there isn't one or
     * it can't be used right now (Bluetooth off or permission not yet granted).
     */
    @SuppressLint("MissingPermission")
    fun lastAdapter(context: Context): ObdTransport? {
        val app = context.applicationContext
        val prefs = AppPrefs.of(app)
        val type = prefs.getString(AppPrefs.LAST_TYPE, null) ?: return null
        if (type == AppPrefs.TYPE_WIFI) {
            return WifiTransport(
                app,
                prefs.getString(AppPrefs.HOST, AppPrefs.DEFAULT_HOST) ?: AppPrefs.DEFAULT_HOST,
                prefs.getInt(AppPrefs.PORT, AppPrefs.DEFAULT_PORT),
            )
        }
        val address = prefs.getString(AppPrefs.LAST_ADDRESS, null) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return null
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        if (!adapter.isEnabled) return null
        val device = try { adapter.getRemoteDevice(address) } catch (e: IllegalArgumentException) { return null }
        return if (type == AppPrefs.TYPE_BLE) BleTransport(app, device) else BluetoothTransport(device, adapter)
    }

    fun lastAdapterName(context: Context): String? = AppPrefs.of(context).getString(AppPrefs.LAST_NAME, null)

    /** The car's make, for explaining manufacturer-specific codes: the car the user chose, else the VIN's maker. */
    fun carMake(context: Context?): String? =
        context?.let { AppPrefs.savedCar(AppPrefs.of(it))?.make }
            ?: _state.value.vin?.let { com.obdreader.app.obd.VinDecoder.decode(it).make }

    private var appContext: Context? = null

    /** Throttle measured from its closed position, so it reads 0% with your foot off. Learnt per car. */
    private var throttle = ThrottleCalibration()
    private var throttleKey: String? = null

    // ---------- Requests while connected ----------

    suspend fun readCodes(): Codes {
        val c = client ?: throw IOException("Not connected")
        val stored = c.readDtcs(pending = false)
        val storedLines = logCodes(stored, pending = false)
        val pending = c.readDtcs(pending = true)
        val pendingLines = logCodes(pending, pending = true)
        val time = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
        _codeLog.value = listOf("Trouble codes read at $time") + storedLines + pendingLines
        val codes = Codes(stored, pending)
        _state.update { it.copy(codes = codes) }
        return codes
    }

    /** Readings arriving under one key that stand in for another (accelerator pedal for throttle, say). */
    private var standInFor: Map<String, String> = emptyMap()
    private val misses = mutableMapOf<String, Int>()
    private val chargingWatch = ChargingStatus.Watch()

    /** What the car scan found: the standard readings the car lists, and which manufacturer readings answered. */
    data class Scan(
        val supported: Set<Int>,
        val enhanced: List<Pair<CarCoverage.Enhanced, String?>>,
        /** Raw answers (hex) to the standard hybrid/EV battery readings the car lists: PID 0x9A and 0x5B. */
        val battery: Map<Int, String?> = emptyMap(),
    )

    /**
     * Asks the car which standard readings it supports, and tries a few known manufacturer
     * readings (read-only service 22). Live data carries on in between.
     */
    suspend fun scanCar(context: Context): Scan {
        val c = client ?: throw IOException("Not connected")
        val supported = try { c.supportedPids().orEmpty() } catch (e: AdapterTimeoutException) { emptySet() }
        // Hybrid/EV battery readings, when the car lists them. PID 0x9A's layout isn't published, so it's kept raw.
        val battery = listOf(0x9A, 0x5B).filter { it in supported }.associateWith { n ->
            val req = String.format(Locale.US, "01%02X", n)
            val lines = try { c.command(req, 2000) } catch (e: AdapterTimeoutException) { emptyList() }
            ElmParser.extractData(lines, "41" + req.substring(2))?.joinToString(" ") { String.format(Locale.US, "%02X", it) }
        }
        val protocol = _state.value.protocolCode
        val probes = CarCoverage.enhancedFor(carMake(context), AppPrefs.savedCar(AppPrefs.of(context))?.model, protocol)
        val answers = mutableListOf<Pair<CarCoverage.Enhanced, String?>>()
        var header: String? = null
        try {
            for (p in probes) {
                if (p.header != null && p.header != header) { c.command("ATSH ${p.header}"); header = p.header }
                val lines = try { c.command(p.request, 2000) } catch (e: AdapterTimeoutException) { emptyList() }
                val data = if (ElmParser.errorIn(lines) == null) ElmParser.extractData(lines, "62" + p.request.substring(2)) else null
                answers += p to data?.joinToString(" ") { String.format(Locale.US, "%02X", it) }
            }
        } finally {
            // Back to the usual header for live data.
            if (header != null) CarCoverage.defaultHeader(protocol)?.let { try { c.command("ATSH $it") } catch (ignored: Exception) {} }
        }
        return Scan(supported, answers, battery)
    }

    suspend fun readVin(): String? {
        val c = client ?: throw IOException("Not connected")
        val vin = c.readVin()
        _state.update { it.copy(vin = vin, vinChecked = true) }
        return vin
    }

    /** Reads the VIN once after connecting so the make and year appear without a tap. */
    private suspend fun readVinQuietly() {
        if (_state.value.vinChecked) return
        try {
            readVin()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Many pre-2005 cars don't report a VIN; it can still be read on request.
            _state.update { it.copy(vinChecked = true) }
        }
    }

    // ---------- Live data ----------

    /**
     * Reads live data until the link drops (IOException) or the car goes quiet
     * ([IgnitionOffException]). On CAN cars it asks for up to six PIDs per request, which is
     * several times faster; if the car doesn't support that, it falls back to one at a time.
     */
    private suspend fun pollLoop(c: ElmClient, pids: List<Pid>) {
        val watch = IgnitionWatch()
        var multi = c.isCan
        var cycle = 0
        var timeouts = 0
        val rateWindow = ArrayDeque<Long>()
        // Trouble codes are checked shortly after connecting and then every two minutes, so a code
        // that appears mid-drive shows up (and can raise an alert) without tapping Read codes.
        var nextCodeCheck = SystemClock.elapsedRealtime() + FIRST_CODE_CHECK_MS

        while (currentCoroutineContext().isActive) {
            val focused = focus
            val slowTurn = cycle % 5 == 0
            val wanted = pids.filter { p ->
                when {
                    focused.isNotEmpty() -> p.hex in focused || cycle % 10 == 0
                    else -> p.fast || slowTurn
                }
            }
            var answered = 0
            val now = { SystemClock.elapsedRealtime() }
            val values = mutableMapOf<String, Double?>()
            try {
                if (multi) {
                    for (chunk in wanted.chunked(ElmClient.MAX_MULTI_PIDS)) {
                        val got = c.readPids(chunk)
                        if (got.isEmpty() && chunk.size > 1) {
                            // Either the car is quiet (ignition off) or it doesn't support multi-PID
                            // requests. If a single request works, it's the latter: switch to one at a time.
                            val single = c.readPid(chunk.first())
                            if (single != null) {
                                multi = false
                                values[chunk.first().hex] = single
                                break
                            }
                        }
                        for (p in chunk) values[p.hex] = got[p]
                    }
                }
                if (!multi) {
                    for (p in wanted) if (p.hex !in values || values[p.hex] == null) values[p.hex] = c.readPid(p)
                }
                timeouts = 0
            } catch (e: AdapterTimeoutException) {
                if (++timeouts >= 5) throw IOException("The adapter stopped responding.")
            }

            // Counted before missed replies are held back, so the ignition-off check sees every request.
            val asked = values.size
            // Stand-in readings take the place of the dashboard reading they stand in for.
            standInFor.forEach { (from, to) -> if (from in values) values[to] = values.remove(from) }
            // A reading the car didn't answer keeps its last value for a couple of tries (one missed
            // reply is common); after that it's cleared, so the app says N/A instead of an old number.
            for (k in values.keys.toList()) {
                if (values[k] == null) {
                    val n = (misses[k] ?: 0) + 1
                    misses[k] = n
                    if (n < MISSES_BEFORE_NA) values.remove(k)
                } else {
                    misses.remove(k)
                }
            }
            values[ThrottleCalibration.KEY]?.let { raw ->
                values[ThrottleCalibration.KEY] = throttle.adjust(raw)
                if (throttle.changed) throttleKey?.let { k -> appContext?.let { AppPrefs.of(it).edit().putFloat(k, throttle.closed!!.toFloat()).apply() } }
            }
            val t = now()
            for ((k, v) in values) {
                if (v != null) {
                    answered++
                    _samples.tryEmit(Sample(k, v, t))
                    rateWindow.addLast(t)
                }
            }
            while (rateWindow.isNotEmpty() && t - rateWindow.first() > 3000) rateWindow.removeFirst()
            val rate = rateWindow.size / 3.0
            if (values.isNotEmpty()) _state.update { it.copy(readings = it.readings + values, rate = rate) }

            if (watch.cycle(answered, asked)) throw IgnitionOffException()

            // Charging status from the latest voltage and engine speed (held steady by the watch).
            _state.value.let { st ->
                val charging = chargingWatch.update(now(), st.readings[VOLTAGE_KEY], st.readings["0C"])
                if (charging != st.charging) _state.update { it.copy(charging = charging) }
            }

            if (SystemClock.elapsedRealtime() >= nextCodeCheck) {
                nextCodeCheck = SystemClock.elapsedRealtime() + CODE_CHECK_MS
                try {
                    val codes = Codes(c.readDtcs(pending = false), c.readDtcs(pending = true))
                    _state.update { it.copy(codes = codes) }
                } catch (ignored: AdapterTimeoutException) {
                }
            }
            if (slowTurn) {
                try {
                    c.readVoltage()?.let { v ->
                        _state.update { it.copy(readings = it.readings + (VOLTAGE_KEY to v)) }
                        _samples.tryEmit(Sample(VOLTAGE_KEY, v, now()))
                    }
                } catch (ignored: AdapterTimeoutException) {
                }
            }
            cycle++
            delay(if (focused.isNotEmpty()) 10 else 40)
        }
    }

    /** Writes each decoded trouble code and its meaning under the raw reply, so the log reads on its own. */
    private fun logCodes(codes: List<String>, pending: Boolean): List<String> {
        val kind = if (pending) "pending" else "stored"
        val lines = if (codes.isEmpty()) listOf("= No $kind codes")
        else listOf("= ${codes.size} $kind ${if (codes.size == 1) "code" else "codes"}:") + codes.map { "=   " + DtcLibrary.logLine(it, carMake(appContext)) }
        lines.forEach { appendLog(it) }
        return lines
    }

    private fun appendLog(line: String) {
        _log.update { (it + line).takeLast(MAX_LOG_LINES) }
    }
}
