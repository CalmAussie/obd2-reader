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
import com.obdreader.app.obd.ModuleScan
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

    data class Codes(val stored: List<String>, val pending: List<String>, val freezeFrame: com.obdreader.app.obd.FreezeFrame? = null) {
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
        /** The battery check from the engine starting during this connection, if it was caught. */
        val batteryCheck: com.obdreader.app.obd.BatteryCheck.Result? = null,
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
            // Connected, but not a word back to the very first command: most likely not an ELM327 at all.
            if (!c.identified && e is AdapterTimeoutException && e.message?.contains("'ATZ'") == true) {
                throw IOException(LockedAdapters.noReplyMessage(t.name))
            }
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
        batteryWatch.reset()
        _state.update { it.copy(standIns = standIns.associate { s -> s.forHex to s.pid.label }) }
        val pids = base + standIns.map { it.pid }
        val covered = base.map { it.hex }.toSet() + standIns.map { it.forHex }
        _state.update { s -> s.copy(unsupported = Pids.dashboard.map { it.hex }.filter { it !in covered }.toSet()) }

        while (currentCoroutineContext().isActive) {
            try {
                pollLoop(c, pids)
            } catch (e: IgnitionOffException) {
                // The adapter is still there; the car has gone to sleep. Read the battery from the
                // adapter every second (that doesn't involve the car), ask the car every few seconds
                // whether it's awake (at once if the voltage moves, as it does when the key turns),
                // and carry straight on when it is.
                if (isDemo || !AppPrefs.autoReconnect(prefs)) throw IOException("The car stopped answering. Is the ignition off?")
                waitIgnitionOff(c, prefs)
                _state.update(connected)
            }
        }
    }

    /**
     * The ignition is off but the adapter is still connected: show the battery's voltage and charge
     * from the adapter's own reading, keep an overnight record of it if the user switched that on,
     * and return as soon as the car answers again.
     */
    private suspend fun waitIgnitionOff(c: ElmClient, prefs: android.content.SharedPreferences) {
        val since = SystemClock.elapsedRealtime()
        val offAt = System.currentTimeMillis()
        val watching = AppPrefs.batteryWatch(prefs)
        val limit = if (watching) ReconnectPolicy.WATCH_GIVE_UP_AFTER_MS else ReconnectPolicy.GIVE_UP_AFTER_MS
        if (watching) prefs.edit().putLong(AppPrefs.BATTERY_NIGHT_OFF, offAt).putString(AppPrefs.BATTERY_NIGHT, "").apply()
        val night = mutableListOf<com.obdreader.app.obd.BatteryRest.Sample>()
        var lastSample = 0L
        var lastProbe = SystemClock.elapsedRealtime()
        var resting: Double? = null
        _state.update {
            it.copy(phase = Phase.WAITING, title = "Ignition off", detail = "Waiting for the car. Live data comes back as soon as the ignition is on.", readings = emptyMap(), charging = null, rate = 0.0)
        }
        while (true) {
            delay(ReconnectPolicy.VOLT_READ_MS)
            val v = try { c.readVoltage() } catch (e: AdapterTimeoutException) { null }
            val now = SystemClock.elapsedRealtime()
            if (v != null) {
                batteryWatch.update(now, v, 0.0) // the engine is off: these are resting readings
                val settling = System.currentTimeMillis() - offAt < com.obdreader.app.obd.BatteryRest.SETTLE_MS
                val text = "Battery " + com.obdreader.app.obd.BatteryRest.describe(v) +
                    (if (settling) " (it reads a little high for a couple of hours after a drive)" else "") +
                    ". Live data comes back as soon as the ignition is on."
                _state.update { it.copy(readings = mapOf(VOLTAGE_KEY to v), detail = text) }
                _samples.tryEmit(Sample(VOLTAGE_KEY, v, now))
                if (resting == null) resting = v
                if (watching && System.currentTimeMillis() - lastSample >= com.obdreader.app.obd.BatteryRest.SAMPLE_EVERY_MS) {
                    lastSample = System.currentTimeMillis()
                    night += com.obdreader.app.obd.BatteryRest.Sample(lastSample, v)
                    prefs.edit().putString(AppPrefs.BATTERY_NIGHT, com.obdreader.app.obd.BatteryRest.encode(night.takeLast(400))).apply()
                }
            }
            if (ReconnectPolicy.shouldProbe(now - lastProbe, v, resting)) {
                lastProbe = now
                val awake = try { c.probe() } catch (e: AdapterTimeoutException) { false }
                if (awake) return
                resting = v ?: resting
            }
            if (ReconnectPolicy.shouldGiveUp(since, now, limit)) {
                throw IOException(if (watching) "The car didn't answer for 24 hours." else "The car didn't answer for 30 minutes.")
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
            ?: vinFor(context)?.let { com.obdreader.app.obd.VinDecoder.decode(it).make }

    /** The car's model year, the same way: the car the user chose, else the VIN's year. */
    fun carYear(context: Context?): Int? =
        context?.let { AppPrefs.savedCar(AppPrefs.of(it))?.year }
            ?: vinFor(context)?.let { com.obdreader.app.obd.VinDecoder.decode(it).modelYear }

    /** The VIN to go by: the one the user entered from their VIN plate, else the one the car sent. */
    fun vinFor(context: Context?): String? =
        context?.let { AppPrefs.userVin(AppPrefs.of(it)) } ?: _state.value.vin

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
        // The freeze frame only exists once a code has been stored.
        val frame = if (stored.isEmpty()) null else try { c.readFreezeFrame() } catch (e: AdapterTimeoutException) { null }
        val time = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
        _codeLog.value = listOf("Trouble codes read at $time") + storedLines + pendingLines +
            listOfNotNull(frame?.let { "Freeze frame stored with ${it.code}: ${it.lines().joinToString("; ")}" })
        val codes = Codes(stored, pending, frame)
        _state.update { it.copy(codes = codes) }
        return codes
    }

    /** Readings arriving under one key that stand in for another (accelerator pedal for throttle, say). */
    private var standInFor: Map<String, String> = emptyMap()
    private val misses = mutableMapOf<String, Int>()
    private val chargingWatch = ChargingStatus.Watch()
    private val batteryWatch = com.obdreader.app.obd.BatteryCheck.Watch()

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

    // ---------- Deeper checks (all read-only) ----------

    /** The readiness monitors (service 01 PID 01), or null if the car didn't answer. */
    suspend fun readReadiness(): com.obdreader.app.obd.Readiness? {
        val c = client ?: throw IOException("Not connected")
        return com.obdreader.app.obd.Readiness.decode(ElmParser.extractData(c.command("0101", 3000), "4101"))
    }

    /** The engine computer's calibration IDs (its software versions), or an empty list. */
    suspend fun readCalibrationIds(): List<String> {
        val c = client ?: throw IOException("Not connected")
        return try { ElmParser.parseCalibrationIds(c.command("0904", 5000)) } catch (e: AdapterTimeoutException) { emptyList() }
    }

    /**
     * Self-test results (service 06). Null when the car isn't on CAN (older cars use a
     * maker-specific layout for these), empty when it reports none.
     */
    suspend fun readTestResults(): List<com.obdreader.app.obd.TestResults.Result>? {
        val c = client ?: throw IOException("Not connected")
        if (!c.isCan) return null
        val mids = mutableListOf<Int>()
        var base = 0x00
        while (base <= 0xE0) {
            val req = String.format(Locale.US, "06%02X", base)
            val bitmap = try { ElmParser.extractData(c.command(req, 3000), "46" + req.substring(2)) } catch (e: AdapterTimeoutException) { null }
            val found = com.obdreader.app.obd.TestResults.supported(base, bitmap)
            mids += found.filter { it % 0x20 != 0 }
            if ((base + 0x20) !in found) break
            base += 0x20
        }
        val results = mutableListOf<com.obdreader.app.obd.TestResults.Result>()
        for (mid in mids) {
            val req = String.format(Locale.US, "06%02X", mid)
            val bytes = try { ElmParser.extractData(c.command(req, 3000), "46") } catch (e: AdapterTimeoutException) { null } ?: continue
            results += com.obdreader.app.obd.TestResults.parse(bytes).filter { it.mid == mid }
        }
        return results
    }

    /** True when the car talks OBD2 on 29-bit CAN addresses (protocols 7 and 9). */
    private fun carIs29Bit(): Boolean = _state.value.protocolCode?.trim()?.uppercase(java.util.Locale.US)?.removePrefix("A") in setOf("7", "9")

    /**
     * Trouble codes from the car's other modules: the ones the car's make is known to have. Each
     * module is asked on its own address with a read-only request (UDS 19, or KWP2000 18 if it says
     * no to that), and the adapter is pointed back at the engine computer after each one. Null on
     * non-CAN cars.
     */
    suspend fun scanModules(context: Context): List<Pair<ModuleScan.Module, ModuleScan.Answer>>? {
        val c = client ?: throw IOException("Not connected")
        if (!c.isCan) return null
        val modules = ModuleScan.modulesFor(carMake(context), AppPrefs.savedCar(AppPrefs.of(context))?.model, carIs29Bit())
        return modules.map { m -> m to readModule(c, m) }
    }

    /** One module's codes. A 29-bit module on an 11-bit car is asked with the adapter briefly on the 29-bit form of the car's protocol. */
    private suspend fun readModule(c: ElmClient, m: ModuleScan.Module): ModuleScan.Answer {
        val switch = if (m.is29Bit && !carIs29Bit()) ModuleScan.protocolSwitch(_state.value.protocolCode) else null
        if (m.is29Bit && !carIs29Bit() && switch == null) return ModuleScan.Answer.Silent
        val point = (listOfNotNull(switch?.first) + ModuleScan.pointAt(m)).map { it to 1500L }
        val back = ModuleScan.pointBack(CarCoverage.defaultHeader(_state.value.protocolCode)) + listOfNotNull(switch?.second)
        var answer = ModuleScan.parseUds(c.sequence(point + (ModuleScan.UDS_REQUEST to 3000L), back).last())
        if (answer is ModuleScan.Answer.Refused) {
            answer = ModuleScan.parseKwp(c.sequence(point + (ModuleScan.KWP_REQUEST to 3000L), back).last())
        }
        appendLog("= ${m.name}: " + when (val a = answer) {
            is ModuleScan.Answer.Codes -> if (a.codes.isEmpty()) "no codes" else a.codes.joinToString(", ") { it.label }
            ModuleScan.Answer.Refused -> "doesn't offer its codes this way"
            ModuleScan.Answer.Silent -> "didn't answer"
        })
        return answer
    }

    /**
     * "Search every address": asks every module address the car's make could use, a block at a
     * time, with a read-only request, and reads the codes of each module that answers and isn't in
     * [already]. [progress] gets (asked so far, total). Null on non-CAN cars.
     */
    suspend fun searchModules(
        context: Context,
        already: Set<String>,
        progress: (Int, Int) -> Unit,
    ): List<Pair<ModuleScan.Module, ModuleScan.Answer>>? {
        val c = client ?: throw IOException("Not connected")
        if (!c.isCan) return null
        val make = carMake(context)
        val bit29 = carIs29Bit()
        val sweeps = ModuleScan.sweepsFor(make, bit29).filter { !it.is29Bit || bit29 || ModuleScan.protocolSwitch(_state.value.protocolCode) != null }
        val total = sweeps.sumOf { it.requests.size }
        var done = 0
        appendLog("= Searching every module address ($total) with a read-only request")
        val found = linkedMapOf<String, String>()   // request address -> answer address
        for (sweep in sweeps) {
            val switch = if (sweep.is29Bit && !bit29) ModuleScan.protocolSwitch(_state.value.protocolCode) else null
            val setup = listOfNotNull(switch?.first) + ModuleScan.sweepSetup(sweep)
            val teardown = ModuleScan.sweepTeardown(CarCoverage.defaultHeader(_state.value.protocolCode)) + listOfNotNull(switch?.second)
            // A block of 16 at a time, so other readings get a turn in between.
            for (block in sweep.requests.chunked(16)) {
                val cmds = setup.map { it to 1500L } + block.flatMap { listOf(ModuleScan.sweepProbe(it) to 1000L, ModuleScan.UDS_REQUEST to 1000L) }
                val replies = c.sequence(cmds, teardown)
                block.forEachIndexed { i, req ->
                    val answers = ModuleScan.answeringAddresses(replies[setup.size + i * 2 + 1], sweep.is29Bit)
                    answers.firstOrNull()?.let { if (req !in found) found[req] = it }
                }
                done += block.size
                progress(done, total)
            }
        }
        appendLog("= Found ${found.size} answering: " + found.entries.joinToString(", ") { "${it.key}→${it.value}" }.ifEmpty { "none" })
        return found.filterKeys { it !in already }.map { (req, reply) ->
            val m = ModuleScan.Module(ModuleScan.nameFor(make, req, bit29), req, reply)
            m to readModule(c, m)
        }
    }

    /** One custom reading's value now, or null when the car didn't give one. */
    suspend fun readCustom(r: com.obdreader.app.obd.CustomReading): Double? {
        val c = client ?: throw IOException("Not connected")
        val back = if (r.header == null) emptyList() else listOfNotNull(CarCoverage.defaultHeader(_state.value.protocolCode)?.let { "ATSH $it" })
        val cmds = listOfNotNull(r.header?.let { "ATSH $it" to 1500L }, r.request to 3000L)
        val lines = c.sequence(cmds, back).last()
        if (ElmParser.errorIn(lines) != null) return null
        return ElmParser.extractData(lines, r.replyHeader)?.let { r.value(it) }
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

            // Battery check: with the ignition on and the engine off, read the battery every turn,
            // so the dip while the engine starts is caught.
            val engineOff = (_state.value.readings["0C"] ?: 9999.0) < 300.0
            var voltNow: Double? = null
            if (engineOff && !slowTurn) {
                voltNow = try { c.readVoltage() } catch (ignored: AdapterTimeoutException) { null }
                voltNow?.let { v -> _state.update { it.copy(readings = it.readings + (VOLTAGE_KEY to v)) } }
            }
            batteryWatch.update(now(), voltNow, _state.value.readings["0C"])?.copy(atMs = System.currentTimeMillis())?.let { result ->
                appendLog("= Battery check: ${result.summary()}, ${result.rating.label.lowercase(Locale.US)}")
                // The demo car's starts are shown but not kept, so they don't fill the real history.
                if (!_state.value.isDemo) appContext?.let { ctx ->
                    val prefs = AppPrefs.of(ctx)
                    prefs.edit().putString(AppPrefs.BATTERY_CHECKS, com.obdreader.app.obd.BatteryCheck.addToHistory(prefs.getString(AppPrefs.BATTERY_CHECKS, null), result)).apply()
                }
                _state.update { it.copy(batteryCheck = result) }
            }

            if (SystemClock.elapsedRealtime() >= nextCodeCheck) {
                nextCodeCheck = SystemClock.elapsedRealtime() + CODE_CHECK_MS
                try {
                    val stored = c.readDtcs(pending = false)
                    // Keep the freeze frame read earlier while its code is still stored.
                    val codes = Codes(stored, c.readDtcs(pending = true), _state.value.codes?.freezeFrame?.takeIf { it.code in stored })
                    _state.update { it.copy(codes = codes) }
                } catch (ignored: AdapterTimeoutException) {
                }
            }
            if (slowTurn) {
                try {
                    c.readVoltage()?.let { v ->
                        _state.update { it.copy(readings = it.readings + (VOLTAGE_KEY to v)) }
                        _samples.tryEmit(Sample(VOLTAGE_KEY, v, now()))
                        batteryWatch.update(now(), v, null)
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
        else listOf("= ${codes.size} $kind ${if (codes.size == 1) "code" else "codes"}:") + codes.map { "=   " + DtcLibrary.logLine(it, carMake(appContext), carYear(appContext)) }
        lines.forEach { appendLog(it) }
        return lines
    }

    private fun appendLog(line: String) {
        _log.update { (it + line).takeLast(MAX_LOG_LINES) }
    }
}
