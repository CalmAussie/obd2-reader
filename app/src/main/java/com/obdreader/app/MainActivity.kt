package com.obdreader.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.session.ObdSession
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.obdreader.app.obd.AdapterTimeoutException
import com.obdreader.app.obd.BleTransport
import com.obdreader.app.obd.BluetoothTransport
import com.obdreader.app.obd.DemoTransport
import com.obdreader.app.obd.DtcDescriptions
import com.obdreader.app.obd.ElmClient
import com.obdreader.app.obd.ObdTransport
import com.obdreader.app.obd.Pid
import com.obdreader.app.obd.Pids
import com.obdreader.app.obd.ObdProtocols
import com.obdreader.app.obd.VehicleCatalog
import com.obdreader.app.obd.VinDecoder
import com.obdreader.app.obd.WifiTransport
import com.obdreader.app.ui.GaugeView
import com.google.android.material.card.MaterialCardView
import com.obdreader.app.ui.AppThemes
import com.obdreader.app.ui.ThemeSwatchView
import com.obdreader.app.ui.themeBool
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.themeShape
import com.obdreader.app.ui.TileView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private enum class Status { IDLE, CONNECTING, CONNECTED, ERROR }

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var statusDot2: View
    private lateinit var statusText2: TextView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var pageDashboard: View
    private lateinit var pageDiagnostics: View
    private lateinit var statusDetail: TextView
    private lateinit var connectBtn: MaterialButton
    private lateinit var demoBtn: MaterialButton
    private lateinit var readDtcBtn: MaterialButton
    private lateinit var clearDtcBtn: MaterialButton
    private lateinit var vinBtn: MaterialButton
    private lateinit var rpmGauge: GaugeView
    private lateinit var speedGauge: GaugeView
    private lateinit var grid: GridLayout
    private lateinit var vinText: TextView
    private lateinit var vehicleText: TextView
    private lateinit var protocolText: TextView
    private lateinit var protocolSetting: TextView
    private lateinit var carSetting: TextView
    private lateinit var chooseCarButton: MaterialButton
    private lateinit var dtcStatus: TextView
    private lateinit var dtcContainer: LinearLayout
    private lateinit var logText: TextView
    private lateinit var logToggle: TextView

    private val tiles = mutableMapOf<String, TileView>()
    private var lastPhase: ObdSession.Phase? = null
    private var lastReadings: Map<String, Double?> = emptyMap()
    private var lastUnsupported: Set<String> = emptySet()
    private var lastVinChecked = false
    private var lastCodes: ObdSession.Codes? = null


    private val prefs by lazy { getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE) }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        getSystemService(BluetoothManager::class.java)?.adapter
    }

    private var afterPermissions: (() -> Unit)? = null
    private var afterBluetoothOn: ((BluetoothAdapter) -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val action = afterPermissions
            afterPermissions = null
            if (result.values.all { it }) action?.invoke()
            else toast("Permission is needed to find and talk to the adapter")
        }

    /** Asked once, so the connection notification (with its Disconnect button) can show. */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val action = afterBluetoothOn
            afterBluetoothOn = null
            val adapter = bluetoothAdapter
            if (adapter?.isEnabled == true) action?.invoke(adapter)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The chosen theme must be set before any view is created.
        setTheme(AppThemes.byId(prefs.getString(PREF_THEME, null)).style)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Dark status-bar icons on the light theme, light icons on the dark ones.
        val lightTheme = themeBool(R.attr.obdIsLight)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightTheme
            isAppearanceLightNavigationBars = lightTheme
        }
        setContentView(R.layout.activity_main)

        // Edge-to-edge: pages pad below the status bar; the bottom bar pads itself above the nav bar.
        val pages = listOf(findViewById<View>(R.id.content), findViewById<View>(R.id.contentDiag))
        val basePadTop = pages.first().paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            pages.forEach { it.setPadding(it.paddingLeft, basePadTop + bars.top, it.paddingRight, it.paddingBottom) }
            insets
        }

        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        statusDetail = findViewById(R.id.statusDetail)
        statusDot2 = findViewById(R.id.statusDot2)
        statusText2 = findViewById(R.id.statusText2)
        bottomNav = findViewById(R.id.bottomNav)
        pageDashboard = findViewById(R.id.pageDashboard)
        pageDiagnostics = findViewById(R.id.pageDiagnostics)
        connectBtn = findViewById(R.id.connectButton)
        demoBtn = findViewById(R.id.demoButton)
        readDtcBtn = findViewById(R.id.readDtcButton)
        clearDtcBtn = findViewById(R.id.clearDtcButton)
        vinBtn = findViewById(R.id.vinButton)
        rpmGauge = findViewById(R.id.rpmGauge)
        speedGauge = findViewById(R.id.speedGauge)
        grid = findViewById(R.id.gaugeGrid)
        vinText = findViewById(R.id.vinText)
        vehicleText = findViewById(R.id.vehicleText)
        protocolText = findViewById(R.id.protocolText)
        protocolSetting = findViewById(R.id.protocolSetting)
        findViewById<View>(R.id.protocolRow).setOnClickListener { showProtocolPicker() }
        updateProtocolSetting()
        carSetting = findViewById(R.id.carSetting)
        chooseCarButton = findViewById(R.id.chooseCarButton)
        findViewById<View>(R.id.carRow).setOnClickListener { showCarPicker() }
        chooseCarButton.setOnClickListener { showCarPicker() }
        updateCarSetting()
        dtcStatus = findViewById(R.id.dtcStatus)
        dtcContainer = findViewById(R.id.dtcContainer)
        logText = findViewById(R.id.logText)
        logToggle = findViewById(R.id.logToggle)

        setupGauges()
        applyRedline() // after setupGauges, which sets the default range
        Pids.dashboard.filter { it.number != Pids.RPM && it.number != Pids.SPEED }.forEach { pid ->
            addTile(pid.hex, TileView(this, pid.label, pid.unit, pid.min, pid.max, pid.warnAbove, pid.decimals))
        }
        addTile(VOLTAGE_KEY, TileView(this, "Battery", "V", 11.0, 15.0, 15.0, 1))

        connectBtn.setOnClickListener { if (ObdSession.isActive) disconnect() else showConnectSheet() }
        demoBtn.setOnClickListener { if (!ObdSession.isActive) startSession(DemoTransport()) }
        readDtcBtn.setOnClickListener { readDtcs() }
        clearDtcBtn.setOnClickListener { confirmClearDtcs() }
        vinBtn.setOnClickListener { readVin() }
        findViewById<View>(R.id.logCard).setOnClickListener { toggleLog() }
        findViewById<View>(R.id.helpRow).setOnClickListener { showHelp() }
        findViewById<View>(R.id.themeButton).setOnClickListener { showThemePicker() }
        if (themeBool(R.attr.obdTitleItalic)) {
            listOf(R.id.titleDashboard, R.id.titleDiagnostics).forEach { id ->
                findViewById<TextView>(id).let { it.setTypeface(it.typeface, android.graphics.Typeface.BOLD_ITALIC) }
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            showPage(diagnostics = item.itemId == R.id.nav_diagnostics)
            true
        }
        // Back from Diagnostics returns to the Dashboard instead of closing the app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pageDiagnostics.visibility == View.VISIBLE) {
                    bottomNav.selectedItemId = R.id.nav_dashboard
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // The connection lives in ObdSession (shared with Android Auto and the background
        // service), so this screen just draws whatever state it's in.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { ObdSession.state.collect { render(it) } }
                launch { ObdSession.log.collect { logText.text = it.joinToString("\n") } }
            }
        }
    }

    private fun setupGauges() {
        Pids.dashboard.first { it.number == Pids.RPM }.let { p ->
            rpmGauge.label = "RPM"
            rpmGauge.unit = "× 1000"
            rpmGauge.min = 0f
            rpmGauge.max = (p.max / 1000).toFloat()
            rpmGauge.warnAt = ((p.warnAbove ?: Double.MAX_VALUE) / 1000).toFloat()
            rpmGauge.decimals = 1
        }
        Pids.dashboard.first { it.number == Pids.SPEED }.let { p ->
            speedGauge.label = "Speed"
            speedGauge.unit = p.unit
            speedGauge.min = p.min.toFloat()
            speedGauge.max = p.max.toFloat()
            speedGauge.decimals = 0
        }
    }

    // ---------- Choosing an adapter ----------

    private fun showConnectSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_connect, null)

        fun bind(id: Int, icon: Int, title: Int, subtitle: Int, onPick: () -> Unit) {
            val card = view.findViewById<View>(id)
            card.findViewById<ImageView>(R.id.optionIcon).setImageResource(icon)
            card.findViewById<TextView>(R.id.optionTitle).setText(title)
            card.findViewById<TextView>(R.id.optionSubtitle).setText(subtitle)
            card.setOnClickListener {
                sheet.dismiss()
                onPick()
            }
        }
        val lastType = prefs.getString(PREF_LAST_TYPE, null)
        val lastName = prefs.getString(PREF_LAST_NAME, null)
        if (lastType != null && lastName != null) {
            val (icon, typeLabel) = when (lastType) {
                TYPE_BLE -> R.drawable.ic_bluetooth_le to getString(R.string.opt_ble_title)
                TYPE_WIFI -> R.drawable.ic_wifi to getString(R.string.opt_wifi_title)
                else -> R.drawable.ic_bluetooth to getString(R.string.opt_classic_title)
            }
            val card = view.findViewById<View>(R.id.optionLast)
            view.findViewById<View>(R.id.lastLabel).visibility = View.VISIBLE
            view.findViewById<View>(R.id.otherLabel).visibility = View.VISIBLE
            card.visibility = View.VISIBLE
            card.findViewById<ImageView>(R.id.optionIcon).setImageResource(icon)
            card.findViewById<TextView>(R.id.optionTitle).text = getString(R.string.reconnect_to, lastName)
            card.findViewById<TextView>(R.id.optionSubtitle).text = typeLabel
            card.setOnClickListener {
                sheet.dismiss()
                reconnectLast()
            }
        }
        bind(R.id.optionClassic, R.drawable.ic_bluetooth, R.string.opt_classic_title, R.string.opt_classic_sub) { connectClassic() }
        bind(R.id.optionBle, R.drawable.ic_bluetooth_le, R.string.opt_ble_title, R.string.opt_ble_sub) { connectBle() }
        bind(R.id.optionWifi, R.drawable.ic_wifi, R.string.opt_wifi_title, R.string.opt_wifi_sub) { connectWifi() }

        sheet.setContentView(view)
        sheet.show()
    }

    private fun withPermissions(permissions: Array<String>, action: () -> Unit) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            afterPermissions = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun withBluetoothOn(action: (BluetoothAdapter) -> Unit) {
        val adapter = bluetoothAdapter ?: return toast("This phone has no Bluetooth")
        if (adapter.isEnabled) {
            action(adapter)
        } else {
            afterBluetoothOn = action
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun rememberLast(type: String, address: String?, name: String?) {
        prefs.edit()
            .putString(PREF_LAST_TYPE, type)
            .putString(PREF_LAST_ADDRESS, address)
            .putString(PREF_LAST_NAME, name ?: address ?: "adapter")
            .apply()
    }

    @SuppressLint("MissingPermission")
    private fun reconnectLast() {
        val type = prefs.getString(PREF_LAST_TYPE, null) ?: return
        if (type == TYPE_WIFI) {
            val host = prefs.getString(PREF_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
            startSession(WifiTransport(applicationContext, host, prefs.getInt(PREF_PORT, DEFAULT_PORT)))
            return
        }
        val address = prefs.getString(PREF_LAST_ADDRESS, null) ?: return
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }
        withPermissions(perms) {
            withBluetoothOn { adapter ->
                val device = try {
                    adapter.getRemoteDevice(address)
                } catch (e: IllegalArgumentException) {
                    toast("The saved adapter address is invalid. Choose it again.")
                    return@withBluetoothOn
                }
                startSession(if (type == TYPE_BLE) BleTransport(applicationContext, device) else BluetoothTransport(device, adapter))
            }
        }
    }

    private fun connectClassic() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }
        withPermissions(perms) { withBluetoothOn { showPairedPicker(it) } }
    }

    private fun connectBle() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        withPermissions(perms) { withBluetoothOn { showBleScanner(it) } }
    }

    private fun connectWifi() {
        val form = layoutInflater.inflate(R.layout.dialog_wifi, null)
        val hostInput = form.findViewById<TextInputEditText>(R.id.hostInput)
        val portInput = form.findViewById<TextInputEditText>(R.id.portInput)
        val portLayout = form.findViewById<TextInputLayout>(R.id.portLayout)
        hostInput.setText(prefs.getString(PREF_HOST, DEFAULT_HOST))
        portInput.setText(prefs.getInt(PREF_PORT, DEFAULT_PORT).toString())

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Wi-Fi adapter")
            .setMessage("Join the adapter's Wi-Fi network first (often WiFi_OBDII or V-LINK). If Android says it has no internet, choose to stay connected.")
            .setView(form)
            .setPositiveButton("Connect", null)
            .setNeutralButton("Wi-Fi settings") { _, _ -> startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            .setNegativeButton("Cancel", null)
            .show()

        // Validate before closing the dialog.
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val host = hostInput.text?.toString()?.trim().orEmpty()
            val port = portInput.text?.toString()?.trim()?.toIntOrNull()
            if (host.isEmpty()) {
                form.findViewById<TextInputLayout>(R.id.hostLayout).error = "Enter the adapter's IP address"
                return@setOnClickListener
            }
            if (port == null || port !in 1..65535) {
                portLayout.error = "Enter a port between 1 and 65535"
                return@setOnClickListener
            }
            prefs.edit().putString(PREF_HOST, host).putInt(PREF_PORT, port).apply()
            dialog.dismiss()
            startSession(WifiTransport(applicationContext, host, port)) { rememberLast(TYPE_WIFI, null, "$host:$port") }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showBleScanner(adapter: BluetoothAdapter) {
        val scanner = adapter.bluetoothLeScanner ?: return toast("Bluetooth LE isn't available on this phone")
        val devices = mutableListOf<BluetoothDevice>()
        val labels = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf())
        var scanning = true

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = result.scanRecord?.deviceName ?: safeName(device) ?: return
                runOnUiThread {
                    if (devices.any { it.address == device.address }) return@runOnUiThread
                    val label = "$name\n${device.address}"
                    if (looksLikeObd(name)) {
                        devices.add(0, device)
                        labels.insert(label, 0)
                    } else {
                        devices.add(device)
                        labels.add(label)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                runOnUiThread { toast("Bluetooth LE scan failed (code $errorCode)") }
            }
        }

        fun stopScan() {
            if (!scanning) return
            scanning = false
            try {
                scanner.stopScan(callback)
            } catch (ignored: Exception) {
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Searching for adapters…")
            .setAdapter(labels) { _, which ->
                stopScan()
                val device = devices[which]
                startSession(BleTransport(applicationContext, device)) { rememberLast(TYPE_BLE, device.address, labels.getItem(which)?.substringBefore('\n')) }
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { stopScan() }
            .show()

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, callback)
        } catch (e: SecurityException) {
            dialog.dismiss()
            toast("Bluetooth scan permission was refused")
            return
        }

        lifecycleScope.launch {
            delay(BLE_SCAN_MS)
            stopScan()
            if (dialog.isShowing) {
                dialog.setTitle(if (devices.isEmpty()) "No adapters found. Is the ignition on?" else "Choose your adapter")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showPairedPicker(adapter: BluetoothAdapter) {
        val devices: List<BluetoothDevice> = adapter.bondedDevices.orEmpty()
            .sortedByDescending { looksLikeObd(it.name) }

        if (devices.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("No paired adapters")
                .setMessage("Plug the adapter into the car, turn the ignition on, then pair it in Android's Bluetooth settings (PIN is usually 1234 or 0000).\n\nIf it never shows up there, it's probably a Bluetooth LE adapter. Use that option instead.")
                .setPositiveButton("Bluetooth settings") { _, _ -> startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val labels = devices.map { "${it.name ?: "Unknown device"}\n${it.address}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Choose your adapter")
            .setItems(labels) { _, which ->
                val device = devices[which]
                startSession(BluetoothTransport(device, adapter)) { rememberLast(TYPE_CLASSIC, device.address, safeName(device)) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String? =
        try { device.name } catch (e: SecurityException) { null }

    private fun looksLikeObd(name: String?): Boolean {
        val n = name?.uppercase(Locale.US) ?: return false
        return listOf("OBD", "ELM", "VLINK", "V-LINK", "VGATE", "ICAR", "KONNWEI", "VEEPEAK", "CAN").any { n.contains(it) }
    }

    // ---------- Session ----------

    private fun startSession(t: ObdTransport, onConnected: (() -> Unit)? = null) {
        if (ObdSession.isActive) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && t !is DemoTransport &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ObdSession.connect(this, t, onConnected)
    }

    private fun disconnect() = ObdSession.disconnect()

    /** Draws the shared connection state: status, buttons, dials, tiles, VIN and codes. */
    private fun render(s: ObdSession.State) {
        val status = when (s.phase) {
            ObdSession.Phase.IDLE -> Status.IDLE
            ObdSession.Phase.CONNECTING -> Status.CONNECTING
            ObdSession.Phase.CONNECTED -> Status.CONNECTED
            ObdSession.Phase.ERROR -> Status.ERROR
        }
        setStatus(status, s.title, s.detail)
        updateButtons(connected = s.isConnected, busy = s.phase == ObdSession.Phase.CONNECTING)

        if (s.phase != lastPhase) {
            if (s.phase == ObdSession.Phase.CONNECTING) clearReadings()
            if (s.isConnected) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!s.isConnected) {
                lastReadings = emptyMap()
                lastUnsupported = emptySet()
                lastVinChecked = false
                lastCodes = null
            }
            lastPhase = s.phase
        }

        if (s.readings !== lastReadings) {
            for ((key, value) in s.readings) {
                if (lastReadings.containsKey(key) && lastReadings[key] == value) continue
                when (key) {
                    RPM_KEY -> rpmGauge.setValue(value?.let { (it / 1000).toFloat() })
                    SPEED_KEY -> speedGauge.setValue(value?.toFloat())
                    else -> tiles[key]?.setValue(value)
                }
            }
            lastReadings = s.readings
        }
        if (s.unsupported != lastUnsupported) {
            s.unsupported.forEach { tiles[it]?.setUnsupported() }
            lastUnsupported = s.unsupported
        }
        if (s.isConnected) {
            protocolText.text = ObdProtocols.byCode(s.protocolCode)?.name ?: s.summary.orEmpty()
        }
        if (s.vinChecked && !lastVinChecked) {
            showVin(s.vin)
            lastVinChecked = true
        }
        val codes = s.codes
        if (codes != null && codes != lastCodes) renderCodes(codes)
        lastCodes = codes
    }

    // ---------- Diagnostics ----------

    private fun readDtcs() {
        if (ObdSession.client == null) return
        lifecycleScope.launch {
            dtcContainer.removeAllViews()
            setDtcStatus("Reading trouble codes…", null)
            try {
                renderCodes(ObdSession.readCodes())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setDtcStatus("Couldn't read codes: ${e.message}", R.attr.obdDanger)
            }
        }
    }

    private fun renderCodes(codes: ObdSession.Codes) {
        dtcContainer.removeAllViews()
        setCodeBadge(codes.total)
        if (codes.total == 0) {
            setDtcStatus("No trouble codes", R.attr.obdOk)
        } else {
            setDtcStatus(if (codes.total == 1) "1 trouble code found" else "${codes.total} trouble codes found", R.attr.obdWarn)
            codes.stored.forEach { addDtcRow(it, pending = false) }
            codes.pending.forEach { addDtcRow(it, pending = true) }
        }
    }

    private fun confirmClearDtcs() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear trouble codes?")
            .setMessage("This turns off the check-engine light and erases stored codes and freeze-frame data. It also resets emissions readiness monitors. Do this with the engine off and ignition on. It doesn't fix the underlying fault.")
            .setPositiveButton("Clear codes") { _, _ -> clearDtcs() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearDtcs() {
        if (ObdSession.client == null) return
        lifecycleScope.launch {
            setDtcStatus("Clearing…", null)
            try {
                if (ObdSession.clearCodes()) {
                    dtcContainer.removeAllViews()
                    setCodeBadge(0)
                    setDtcStatus("Codes cleared. Read again to confirm.", R.attr.obdOk)
                } else {
                    setDtcStatus("The car refused the request. Some cars need the engine off.", R.attr.obdWarn)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setDtcStatus("Couldn't clear codes: ${e.message}", R.attr.obdDanger)
            }
        }
    }

    private fun readVin() {
        if (ObdSession.client == null) return
        lifecycleScope.launch {
            vinText.text = "VIN  reading…"
            try {
                showVin(ObdSession.readVin())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                vinText.text = "VIN  error (${e.message})"
            }
        }
    }

    private fun showVin(vin: String?) {
        vinText.text = "VIN  " + (vin ?: "not reported by this car")
        val info = vin?.let { VinDecoder.decode(it) }
        vehicleText.text = when {
            info?.make != null && info.modelYear != null -> "${info.make} · ${info.modelYear} model year"
            info?.make != null -> info.make
            vin != null -> "Make not recognised from VIN"
            savedCar() != null -> "Your car: ${savedCar()?.label}. It doesn't report a VIN."
            else -> "Older cars often don't report a VIN. Live data and codes still work."
        }
    }

    // ---------- Appearance ----------

    private fun appVersion(): String =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

    /** Setup help inside the app, so someone given just the APK can get going without the README. */
    private fun showHelp() {
        val sheet = BottomSheetDialog(this)
        val pad = dp(20f).toInt()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8f).toInt(), pad, dp(28f).toInt())
        }
        list.addView(TextView(this).apply {
            setText(R.string.help_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(themeColor(R.attr.obdTextPrimary))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        })
        val sections = listOf(
            R.string.help_start_title to R.string.help_start_body,
            R.string.help_adapters_title to R.string.help_adapters_body,
            R.string.help_car_title to R.string.help_car_body,
            R.string.help_auto_title to R.string.help_auto_body,
            R.string.help_safety_title to R.string.help_safety_body,
        )
        for ((title, body) in sections) {
            list.addView(TextView(this).apply {
                setText(title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(themeColor(R.attr.obdAccent))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setPadding(0, dp(18f).toInt(), 0, dp(6f).toInt())
            })
            list.addView(TextView(this).apply {
                setText(body)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(themeColor(R.attr.obdTextSecondary))
                setLineSpacing(0f, 1.15f)
            })
        }
        list.addView(TextView(this).apply {
            text = getString(R.string.help_version, appVersion())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(22f).toInt(), 0, 0)
        })
        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(list) })
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        sheet.show()
    }

    private fun showThemePicker() {
        val sheet = BottomSheetDialog(this)
        val current = AppThemes.byId(prefs.getString(PREF_THEME, null))
        val pad = dp(20f).toInt()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8f).toInt(), pad, dp(28f).toInt())
        }
        list.addView(TextView(this).apply {
            setText(R.string.appearance)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(themeColor(R.attr.obdTextPrimary))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        })
        list.addView(TextView(this).apply {
            setText(R.string.appearance_subtitle)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(4f).toInt(), 0, dp(16f).toInt())
        })

        for (theme in AppThemes.ALL) {
            val selected = theme.id == current.id
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f).toInt(), dp(12f).toInt(), dp(14f).toInt(), dp(12f).toInt())
            }
            row.addView(ThemeSwatchView(this, theme), LinearLayout.LayoutParams(dp(84f).toInt(), dp(60f).toInt()))
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14f).toInt(), 0, dp(8f).toInt(), 0)
                addView(TextView(context).apply {
                    text = theme.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(themeColor(R.attr.obdTextPrimary))
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                })
                addView(TextView(context).apply {
                    text = theme.description
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(themeColor(R.attr.obdTextSecondary))
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (selected) {
                row.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(themeColor(R.attr.obdAccent))
                    contentDescription = "Selected"
                }, LinearLayout.LayoutParams(dp(24f).toInt(), dp(24f).toInt()))
            }
            val card = MaterialCardView(this).apply {
                shapeAppearanceModel = themeShape()
                cardElevation = 0f
                setCardBackgroundColor(themeColor(R.attr.obdSurfaceHigh))
                strokeWidth = dp(if (selected) 2f else 1f).toInt()
                strokeColor = themeColor(if (selected) R.attr.obdAccent else R.attr.obdOutline)
                isClickable = true
                isFocusable = true
                addView(row)
                setOnClickListener {
                    sheet.dismiss()
                    if (!selected) applyTheme(theme)
                }
            }
            list.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10f).toInt()
            })
        }

        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(list) })
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.show()
    }

    private fun applyTheme(theme: AppThemes.AppTheme) {
        prefs.edit().putString(PREF_THEME, theme.id).apply()
        // Redraws every screen in the new theme. The car connection lives outside this
        // screen, so it carries on uninterrupted.
        recreate()
    }

    // ---------- Your car ----------

    private fun savedCar(): VehicleCatalog.Car? {
        val make = prefs.getString(PREF_CAR_MAKE, null) ?: return null
        val year = prefs.getInt(PREF_CAR_YEAR, 0).takeIf { it > 0 } ?: return null
        return VehicleCatalog.Car(make, year, prefs.getString(PREF_CAR_MODEL, null), prefs.getString(PREF_CAR_ENGINE, null))
    }

    /** Scales the RPM dial to the chosen engine's redline, or the default 8,000 / 6,500 rpm. */
    private fun applyRedline() {
        val redline = savedCar()?.let { VehicleCatalog.recommend(it).redline }
        rpmGauge.max = if (redline == null) 8f else (redline / 1000 + 2).toFloat()
        rpmGauge.warnAt = (redline ?: 6500) / 1000f
        rpmGauge.invalidate()
    }

    private fun updateCarSetting() {
        carSetting.text = savedCar()?.label ?: getString(R.string.car_not_set)
    }

    private fun showCarPicker() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_car, null)
        val makeInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.makeInput)
        val yearInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.yearInput)
        val modelInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.modelInput)
        val protocolView = view.findViewById<TextView>(R.id.carProtocol)
        val noteView = view.findViewById<TextView>(R.id.carNote)
        val engineInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.engineInput)
        val engineLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.engineLayout)
        val redlineView = view.findViewById<TextView>(R.id.carRedline)

        val current = savedCar()
        var make = current?.make ?: VehicleCatalog.MAKES.first().name
        var year = current?.year ?: 2010

        makeInput.setSimpleItems(VehicleCatalog.makeNames.toTypedArray())
        yearInput.setSimpleItems((VehicleCatalog.LAST_YEAR downTo VehicleCatalog.FIRST_YEAR).map { it.toString() }.toTypedArray())
        makeInput.setText(make, false)
        yearInput.setText(year.toString(), false)
        modelInput.setText(current?.model.orEmpty())
        engineInput.setText(current?.engine.orEmpty())

        fun carNow() = VehicleCatalog.Car(
            make, year,
            modelInput.text?.toString()?.trim()?.ifEmpty { null },
            engineInput.text?.toString()?.trim()?.ifEmpty { null },
        )

        fun refreshEngines() {
            val engines = VehicleCatalog.engines(make, year, modelInput.text?.toString()).map { it.name }
            engineInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, engines))
            engineLayout.helperText = getString(if (engines.isEmpty()) R.string.car_engine_none else R.string.car_engine_help)
        }

        fun refreshModels() {
            val names = VehicleCatalog.models(make, year).map { it.name }
            modelInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, names))
        }

        fun refreshResult() {
            val rec = VehicleCatalog.recommend(carNow())
            protocolView.text = rec.likely?.name ?: "Automatic search"
            noteView.text = rec.note.orEmpty()
            noteView.visibility = if (rec.note == null) View.GONE else View.VISIBLE
            redlineView.text = rec.redline?.let { "RPM dial redline set to ${String.format(Locale.US, "%,d", it)} rpm for this engine." }.orEmpty()
            redlineView.visibility = if (rec.redline == null) View.GONE else View.VISIBLE
        }

        makeInput.setOnItemClickListener { parent, _, position, _ ->
            make = parent.getItemAtPosition(position) as String
            modelInput.setText("")
            engineInput.setText("")
            refreshModels()
            refreshEngines()
            refreshResult()
        }
        yearInput.setOnItemClickListener { parent, _, position, _ ->
            year = (parent.getItemAtPosition(position) as String).toInt()
            refreshModels()
            refreshEngines()
            refreshResult()
        }
        modelInput.doAfterTextChanged {
            refreshEngines()
            refreshResult()
        }
        engineInput.doAfterTextChanged { refreshResult() }
        engineInput.setOnClickListener { if ((engineInput.adapter?.count ?: 0) > 0) engineInput.showDropDown() }
        modelInput.setOnClickListener { if (modelInput.adapter?.count ?: 0 > 0) modelInput.showDropDown() }
        refreshModels()
        refreshEngines()
        refreshResult()

        view.findViewById<View>(R.id.carSave).setOnClickListener {
            val car = carNow()
            prefs.edit()
                .putString(PREF_CAR_MAKE, car.make)
                .putInt(PREF_CAR_YEAR, car.year)
                .putString(PREF_CAR_MODEL, car.model)
                .putString(PREF_CAR_ENGINE, car.engine)
                .remove(PREF_LAST_PROTOCOL) // the remembered protocol may belong to a different car
                .apply()
            updateCarSetting()
            applyRedline()
            sheet.dismiss()
            toast(if (ObdSession.client != null) "Saved. Disconnect and connect again to use it." else "Saved. Tap Connect to try again.")
        }
        view.findViewById<View>(R.id.carClear).setOnClickListener {
            prefs.edit().remove(PREF_CAR_MAKE).remove(PREF_CAR_YEAR).remove(PREF_CAR_MODEL).remove(PREF_CAR_ENGINE).apply()
            updateCarSetting()
            applyRedline()
            sheet.dismiss()
        }

        sheet.setContentView(view)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.show()
    }

    // ---------- Protocol setting ----------

    private fun updateProtocolSetting() {
        val chosen = prefs.getString(PREF_PROTOCOL, ObdProtocols.AUTO)
        protocolSetting.text = ObdProtocols.byCode(chosen)?.name ?: getString(R.string.protocol_auto)
    }

    private fun showProtocolPicker() {
        val options = listOf<Pair<String, String>>(ObdProtocols.AUTO to getString(R.string.protocol_auto_detail)) +
            ObdProtocols.ALL.map { it.code to "${it.name}\n${it.hint}" }
        val current = prefs.getString(PREF_PROTOCOL, ObdProtocols.AUTO)
        val checked = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.protocol)
            .setSingleChoiceItems(options.map { it.second }.toTypedArray(), checked) { dialog, which ->
                prefs.edit().putString(PREF_PROTOCOL, options[which].first).apply()
                updateProtocolSetting()
                dialog.dismiss()
                if (ObdSession.client != null) toast("Disconnect and connect again to use the new protocol")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setDtcStatus(text: String, colorRes: Int?) {
        dtcStatus.text = text
        dtcStatus.setTextColor(this.themeColor(colorRes ?: R.attr.obdTextPrimary))
        if (colorRes == R.attr.obdOk) {
            val icon = ContextCompat.getDrawable(this, R.drawable.ic_check)?.mutate()
            icon?.setTint(this.themeColor(R.attr.obdOk))
            dtcStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        } else {
            dtcStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
        }
    }

    private fun addDtcRow(code: String, pending: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12f).toInt(), 0, 0)
        }
        val chip = TextView(this).apply {
            text = code
            typeface = android.graphics.Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(context.themeColor(if (pending) R.attr.obdTextSecondary else R.attr.obdWarn))
            background = GradientDrawable().apply {
                cornerRadius = dp(8f)
                setColor(context.themeColor(if (pending) R.attr.obdSurfaceHigh else R.attr.obdWarnContainer))
            }
            setPadding(dp(10f).toInt(), dp(5f).toInt(), dp(10f).toInt(), dp(5f).toInt())
        }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f).toInt(), 0, 0, 0)
            addView(TextView(context).apply {
                this.text = DtcDescriptions.describe(code)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(context.themeColor(R.attr.obdTextPrimary))
            })
            addView(TextView(context).apply {
                this.text = if (pending) "Pending, not yet confirmed" else "Stored, check-engine light"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(context.themeColor(R.attr.obdTextSecondary))
            })
        }
        row.addView(chip)
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dtcContainer.addView(row)
    }

    // ---------- UI helpers ----------

    private fun addTile(key: String, tile: TileView) {
        val margin = dp(5f).toInt()
        val params = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
        ).apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            setMargins(margin, margin, margin, margin)
        }
        grid.addView(tile, params)
        tiles[key] = tile
    }

    private fun clearReadings() {
        tiles.values.forEach { it.setValue(null) }
        rpmGauge.setValue(null)
        speedGauge.setValue(null)
        vinText.setText(R.string.vin_unknown)
        vehicleText.setText(R.string.vehicle_unknown)
        protocolText.setText(R.string.protocol_unknown)
        dtcContainer.removeAllViews()
        setCodeBadge(0)
        setDtcStatus(getString(R.string.dtc_hint), null)
    }

    private fun setStatus(status: Status, title: String, detail: String) {
        val color = when (status) {
            Status.IDLE -> R.attr.obdIdle
            Status.CONNECTING -> R.attr.obdWarn
            Status.CONNECTED -> R.attr.obdOk
            Status.ERROR -> R.attr.obdDanger
        }
        val tint = ColorStateList.valueOf(this.themeColor(color))
        statusDot.backgroundTintList = tint
        statusDot2.backgroundTintList = tint
        statusText.text = title
        statusText2.text = title
        chooseCarButton.visibility = if (status == Status.ERROR) View.VISIBLE else View.GONE
        statusDetail.text = detail
    }

    private fun updateButtons(connected: Boolean, busy: Boolean = false) {
        val active = connected || busy
        connectBtn.setText(if (active) R.string.disconnect else R.string.connect)
        connectBtn.setIconResource(if (active) R.drawable.ic_close else R.drawable.ic_bluetooth)
        demoBtn.isEnabled = !active
        readDtcBtn.isEnabled = connected
        clearDtcBtn.isEnabled = connected
        vinBtn.isEnabled = connected
        clearDtcBtn.alpha = if (connected) 1f else 0.5f
    }

    private fun showPage(diagnostics: Boolean) {
        pageDashboard.visibility = if (diagnostics) View.GONE else View.VISIBLE
        pageDiagnostics.visibility = if (diagnostics) View.VISIBLE else View.GONE
    }

    /** Shows the number of trouble codes on the Diagnostics tab, so they're visible from the Dashboard. */
    private fun setCodeBadge(count: Int) {
        if (count > 0) {
            bottomNav.getOrCreateBadge(R.id.nav_diagnostics).apply {
                number = count
                backgroundColor = this@MainActivity.themeColor(R.attr.obdWarn)
                badgeTextColor = this@MainActivity.themeColor(R.attr.obdBg)
                setVisible(true)
            }
        } else {
            bottomNav.removeBadge(R.id.nav_diagnostics)
        }
    }

    private fun toggleLog() {
        val show = logText.visibility != View.VISIBLE
        logText.visibility = if (show) View.VISIBLE else View.GONE
        logToggle.setText(if (show) R.string.hide else R.string.show)
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private const val VOLTAGE_KEY = ObdSession.VOLTAGE_KEY
        private val RPM_KEY = String.format(Locale.US, "%02X", Pids.RPM)
        private val SPEED_KEY = String.format(Locale.US, "%02X", Pids.SPEED)
        private const val BLE_SCAN_MS = 12_000L
        private const val PREF_HOST = AppPrefs.HOST
        private const val PREF_PORT = AppPrefs.PORT
        private const val DEFAULT_HOST = AppPrefs.DEFAULT_HOST
        private const val DEFAULT_PORT = AppPrefs.DEFAULT_PORT
        private const val PREF_THEME = AppPrefs.THEME
        private const val PREF_CAR_MAKE = AppPrefs.CAR_MAKE
        private const val PREF_CAR_YEAR = AppPrefs.CAR_YEAR
        private const val PREF_CAR_MODEL = AppPrefs.CAR_MODEL
        private const val PREF_CAR_ENGINE = AppPrefs.CAR_ENGINE
        private const val PREF_PROTOCOL = AppPrefs.PROTOCOL
        private const val PREF_LAST_PROTOCOL = AppPrefs.LAST_PROTOCOL
        private const val PREF_LAST_TYPE = AppPrefs.LAST_TYPE
        private const val PREF_LAST_ADDRESS = AppPrefs.LAST_ADDRESS
        private const val PREF_LAST_NAME = AppPrefs.LAST_NAME
        private const val TYPE_CLASSIC = AppPrefs.TYPE_CLASSIC
        private const val TYPE_BLE = AppPrefs.TYPE_BLE
        private const val TYPE_WIFI = AppPrefs.TYPE_WIFI
    }
}
