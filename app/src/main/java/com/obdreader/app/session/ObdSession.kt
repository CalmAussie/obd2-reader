package com.obdreader.app.session

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.obdreader.app.obd.AdapterTimeoutException
import com.obdreader.app.obd.BleTransport
import com.obdreader.app.obd.BluetoothTransport
import com.obdreader.app.obd.DemoTransport
import com.obdreader.app.obd.ElmClient
import com.obdreader.app.obd.ObdProtocols
import com.obdreader.app.obd.ObdTransport
import com.obdreader.app.obd.Pid
import com.obdreader.app.obd.Pids
import com.obdreader.app.obd.VehicleCatalog
import com.obdreader.app.obd.WifiTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The one connection to the car, shared by the phone screens, the Android Auto
 * screen and the background service. It lives for the whole app process, so it
 * survives the phone screen turning off, the app going to the background and
 * theme changes.
 */
object ObdSession {

    enum class Phase { IDLE, CONNECTING, CONNECTED, ERROR }

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
        val protocolCode: String? = null,
        val summary: String? = null,
        val vin: String? = null,
        val vinChecked: Boolean = false,
        val codes: Codes? = null,
    ) {
        val isConnected: Boolean get() = phase == Phase.CONNECTED
    }

    const val VOLTAGE_KEY = "VOLT"
    const val IDLE_HINT = "Plug your adapter into the car and turn the ignition on, or try demo mode."
    private const val MAX_LOG_LINES = 40

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    var client: ElmClient? = null
        private set
    private var transport: ObdTransport? = null
    private var job: Job? = null

    val isActive: Boolean get() = job?.isActive == true

    fun connect(context: Context, t: ObdTransport, onConnected: (() -> Unit)? = null) {
        if (isActive) return
        val app = context.applicationContext
        val prefs = AppPrefs.of(app)
        val isDemo = t is DemoTransport
        transport = t
        _log.value = emptyList()
        _state.value = State(
            phase = Phase.CONNECTING,
            title = "Connecting to ${t.name}…",
            detail = "This can take up to 15 seconds the first time.",
            adapterName = t.name,
            isDemo = isDemo,
        )
        if (!isDemo) ObdConnectionService.start(app)

        job = scope.launch {
            var connected = false
            var end = State()
            val c = ElmClient(t) { line -> appendLog(line) }
            try {
                withContext(Dispatchers.IO) { t.open() }
                _state.update { it.copy(title = "Talking to the car…", detail = "Detecting the vehicle's protocol.") }

                val chosen = prefs.getString(AppPrefs.PROTOCOL, ObdProtocols.AUTO) ?: ObdProtocols.AUTO
                val lastWorking = if (isDemo) null else prefs.getString(AppPrefs.LAST_PROTOCOL, null)
                val carOrder = if (isDemo) null else AppPrefs.savedCar(prefs)?.let { VehicleCatalog.recommend(it).order }
                val info = c.initialize(chosen, lastWorking, carOrder) { step ->
                    _state.update { it.copy(title = "Talking to the car…", detail = step) }
                }

                client = c
                connected = true
                if (!isDemo && info.protocolCode != null) {
                    prefs.edit().putString(AppPrefs.LAST_PROTOCOL, info.protocolCode).apply()
                }
                _state.update {
                    it.copy(
                        phase = Phase.CONNECTED,
                        title = "Connected to ${t.name}",
                        detail = info.summary,
                        summary = info.summary,
                        protocolCode = info.protocolCode,
                    )
                }
                onConnected?.invoke()
                readVinQuietly()
                pollLoop(c)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                end = State(
                    phase = Phase.ERROR,
                    title = if (connected) "Connection lost" else "Couldn't connect",
                    detail = e.message ?: e.javaClass.simpleName,
                )
            } finally {
                t.close()
                client = null
                transport = null
                _state.value = end
                ObdConnectionService.stop(app)
            }
        }
    }

    fun disconnect() {
        transport?.close() // unblocks any read in progress
        job?.cancel()
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

    // ---------- Requests while connected ----------

    suspend fun readCodes(): Codes {
        val c = client ?: throw IOException("Not connected")
        val codes = Codes(c.readDtcs(pending = false), c.readDtcs(pending = true))
        _state.update { it.copy(codes = codes) }
        return codes
    }

    suspend fun clearCodes(): Boolean {
        val c = client ?: throw IOException("Not connected")
        val ok = c.clearDtcs()
        if (ok) _state.update { it.copy(codes = null) }
        return ok
    }

    suspend fun readVin(): String? {
        val c = client ?: throw IOException("Not connected")
        val vin = c.readVin()
        _state.update { it.copy(vin = vin, vinChecked = true) }
        return vin
    }

    /** Reads the VIN once after connecting so the make and year appear without a tap. */
    private suspend fun readVinQuietly() {
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

    private suspend fun pollLoop(c: ElmClient) {
        val supported = try { c.supportedPids() } catch (e: AdapterTimeoutException) { null }
        val pids: List<Pid> = Pids.dashboard.filter { supported == null || it.number in supported }
        _state.update { s -> s.copy(unsupported = Pids.dashboard.filter { it !in pids }.map { it.hex }.toSet()) }

        var cycle = 0
        var timeouts = 0
        while (currentCoroutineContext().isActive) {
            val slowTurn = cycle % 5 == 0
            for (pid in pids) {
                if (!pid.fast && !slowTurn) continue
                try {
                    val value = c.readPid(pid)
                    _state.update { it.copy(readings = it.readings + (pid.hex to value)) }
                    timeouts = 0
                } catch (e: AdapterTimeoutException) {
                    if (++timeouts >= 5) throw IOException("The adapter stopped responding.")
                }
            }
            if (slowTurn) {
                try {
                    c.readVoltage()?.let { v -> _state.update { it.copy(readings = it.readings + (VOLTAGE_KEY to v)) } }
                } catch (ignored: AdapterTimeoutException) {
                }
            }
            cycle++
            delay(50)
        }
    }

    private fun appendLog(line: String) {
        _log.update { (it + line).takeLast(MAX_LOG_LINES) }
    }
}
