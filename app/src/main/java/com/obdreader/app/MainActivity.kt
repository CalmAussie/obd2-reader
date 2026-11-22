package com.obdreader.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
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
import com.obdreader.app.obd.GForce
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.session.Exports
import com.obdreader.app.session.Updater
import com.obdreader.app.session.LogRecorder
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.GraphStripView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.obdreader.app.obd.DemoTransport
import com.obdreader.app.obd.ElmClient
import com.obdreader.app.obd.ObdTransport
import com.obdreader.app.obd.Boost
import com.obdreader.app.obd.Pids
import com.obdreader.app.obd.ObdProtocols
import com.obdreader.app.obd.Units
import com.obdreader.app.obd.EvBattery
import com.obdreader.app.obd.ModuleScan
import com.obdreader.app.obd.ChargingStatus
import com.obdreader.app.obd.VinDecoder
import com.obdreader.app.ui.AppIcons
import com.obdreader.app.ui.AppThemes
import com.obdreader.app.ui.CockpitRingView
import com.obdreader.app.ui.CyberChipView
import com.obdreader.app.ui.CyberHudView
import com.obdreader.app.ui.DashLayout
import com.obdreader.app.ui.DecalView
import com.obdreader.app.ui.MilBannerView
import com.obdreader.app.ui.RoundelView
import com.obdreader.app.ui.ShiftLightsView
import com.obdreader.app.ui.SkewButton
import com.obdreader.app.ui.StartLightsView
import com.obdreader.app.ui.TowerRowView
import com.obdreader.app.ui.TraceView
import com.obdreader.app.ui.panelBackground
import com.obdreader.app.ui.themeBool
import com.obdreader.app.ui.SmoothText
import com.obdreader.app.session.Alerts
import com.obdreader.app.session.DriveScorer
import com.obdreader.app.obd.DrivingScore
import com.obdreader.app.ui.themeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

open class MainActivity : AppCompatActivity() {

    internal enum class Status { IDLE, CONNECTING, CONNECTED, ERROR }

    internal lateinit var statusText: TextView
    internal lateinit var statusText2: TextView
    internal lateinit var bottomNav: BottomNavigationView
    internal lateinit var pageDashboard: View
    internal lateinit var pageDiagnostics: View
    internal lateinit var pageGraphs: View
    internal lateinit var graphList: LinearLayout
    internal lateinit var graphRate: TextView
    internal lateinit var recordBtn: SkewButton
    internal lateinit var recordStatus: TextView
    internal lateinit var shareReportBtn: MaterialButton
    internal val graphStrips = mutableListOf<GraphStripView>()
    internal var graphWindowMs = 30_000L
    internal var graphsPaused = false
    internal lateinit var statusDetail: TextView
    internal lateinit var connectBtn: SkewButton
    internal lateinit var demoBtn: SkewButton
    internal lateinit var readDtcBtn: SkewButton
    internal lateinit var vinBtn: MaterialButton
    internal lateinit var towerList: LinearLayout
    internal lateinit var vinText: TextView
    internal lateinit var vehicleText: TextView
    internal lateinit var protocolText: TextView
    internal lateinit var protocolSetting: TextView
    internal lateinit var carSetting: TextView
    internal lateinit var chooseCarButton: MaterialButton
    internal lateinit var dtcStatus: TextView
    internal lateinit var dtcSub: TextView
    internal lateinit var milBanner: MilBannerView
    internal lateinit var dtcContainer: LinearLayout
    internal lateinit var logText: TextView
    internal lateinit var logToggle: TextView

    // The three dashboards; only the one the chosen style uses is shown.
    internal lateinit var dash: DashLayout
    internal lateinit var roundel: RoundelView
    internal lateinit var startLights: StartLightsView
    internal lateinit var shiftLights: ShiftLightsView
    internal lateinit var trace: TraceView
    internal lateinit var gMeter: com.obdreader.app.ui.GMeterView
    internal lateinit var teleRpm: TextView
    internal lateinit var teleSpeed: TextView
    internal lateinit var teleCoolant: TextView
    internal lateinit var teleFuel: TextView
    internal lateinit var cockpitRing: CockpitRingView
    internal lateinit var cyberHud: CyberHudView
    internal lateinit var retroDash: com.obdreader.app.ui.RetroDashView
    internal lateinit var rallyRev: com.obdreader.app.ui.RallyRevView
    internal lateinit var hairlineDial: com.obdreader.app.ui.HairlineDialView
    internal val retroChips = mutableMapOf<String, com.obdreader.app.ui.RetroChipView>()
    internal val rallyBlocks = mutableMapOf<String, com.obdreader.app.ui.RallyBlockView>()
    internal val hairlineRows = mutableMapOf<String, TowerRowView>()
    internal val cyberChips = mutableMapOf<String, CyberChipView>()
    internal val decals = mutableMapOf<String, DecalView>()
    internal val glass = mutableMapOf<String, SmoothText>()
    // Big text numbers that roll to each reading instead of jumping (the drawn dials do it themselves).
    internal lateinit var teleRpmText: SmoothText
    internal lateinit var teleSpeedText: SmoothText
    /** The two telemetry readouts under the trace, by reading. */
    internal val teleTexts = mutableMapOf<String, SmoothText>()
    internal val teleLabels = mutableMapOf<String, String>()
    internal val glassUnits = mutableMapOf<String, TextView>()
    internal var lastStandIns: Map<String, String> = emptyMap()
    internal var lastSupported: Set<Int>? = null
    internal var lastRelabels: Map<String, String> = emptyMap()
    internal var lastGraphUnsupported: Set<String> = emptySet()
    internal var lastFuelStatus: Double? = null
    internal var lastGraphConnected = false
    internal var lastCleared: com.obdreader.app.obd.ClearedCheck? = null

    /** The line under "Codes recently cleared?" on Diagnostics. */
    internal fun clearedHelp(): TextView? = (findViewById<ViewGroup>(R.id.clearedRow)?.getChildAt(1) as? TextView)
    internal var lastCharging: ChargingStatus.State? = null
    internal val glassLabels = mutableMapOf<String, TextView>()

    /** A reading the dashboard can show in one of its main readouts. */
    internal data class Readout(val key: String, val label: String, val unit: String, val decimals: Int, val min: Double, val max: Double, val warn: Double?)

    internal fun readout(key: String): Readout =
        (Pids.byHex(key)?.let { Readout(it.hex, it.label, it.unit, it.decimals, it.min, it.max, it.warnAbove) }
            ?: if (key == Boost.KEY) Readout(Boost.KEY, Boost.LABEL, "kPa", 0, Boost.MIN, Boost.MAX, null)
            else if (key == GForce.FORWARD_KEY) Readout(key, getString(R.string.g_forward), "g", 2, -1.0, 1.0, null)
            else if (key == GForce.SIDE_KEY) Readout(key, getString(R.string.g_side), "g", 2, -1.0, 1.0, null)
            else Readout(VOLTAGE_KEY, "Battery", "V", 1, 11.0, 15.0, null)).inUnits()

    /** The readout in the chosen units (°F, mph, psi) where that applies. */
    internal fun Readout.inUnits(): Readout = if (!Units.isTemperature(key) && !Units.isSpeed(key) && !Units.isPressure(key)) this else copy(
        unit = Units.unit(key, unit), decimals = Units.decimals(key, decimals),
        min = Units.value(key, min), max = Units.value(key, max), warn = warn?.let { Units.value(key, it) },
    )

    /** Every reading that can go in a main readout (not engine speed or speed, which have their own). */
    internal val readoutChoices: List<Readout> by lazy {
        Pids.dashboard.filter { it.hex != RPM_KEY && it.hex != SPEED_KEY }.map { readout(it.hex) } + readout(Boost.KEY) + readout(VOLTAGE_KEY) +
            // G-force from the phone's motion sensor.
            readout(GForce.FORWARD_KEY) + readout(GForce.SIDE_KEY) +
            // More standard readings, for cars that report them (N/A on the rest).
            Pids.extraNumbers.map { readout(it.hex) }
    }

    /** What the main readouts show, chosen under Diagnostics → Dashboard readouts. Coolant, fuel and battery to start with. */
    internal val slots: List<String> by lazy {
        val saved = prefs.getString(PREF_SLOTS, null)?.split(",").orEmpty()
        val valid = readoutChoices.map { it.key }.toSet()
        listOf(COOLANT_KEY, FUEL_KEY, VOLTAGE_KEY).mapIndexed { i, def -> saved.getOrNull(i)?.takeIf { it in valid } ?: def }
    }
    internal var lastRpm: Float? = null
    internal var lastSpeed: Float? = null
    internal var redline = 6500f

    internal val tiles = mutableMapOf<String, TowerRowView>()
    internal var lastPhase: ObdSession.Phase? = null
    internal var lastReadings: Map<String, Double?> = emptyMap()
    internal var lastUnsupported: Set<String> = emptySet()
    internal var lastVinChecked = false
    internal var lastCodes: ObdSession.Codes? = null

    internal val prefs by lazy { getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE) }

    /** The style this screen is drawn in: the chosen one, or the day or night one with switching on. */
    internal var shownStyle: String? = null
    /** When the screen was last touched: a style change waits while someone's using the screen. */
    internal var lastTouchMs = 0L
    internal var styleWatch: kotlinx.coroutines.Job? = null
    internal var lightListener: android.hardware.SensorEventListener? = null

    /** Back up: where the user chose to save the file. */
    internal var backupRoutes = false
    internal var backupVin = false
    internal val backupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(com.obdreader.app.obd.BackupFile.MIME)) { uri ->
            if (uri != null) writeBackup(uri)
        }
    /** Restore: the backup file the user picked. */
    internal val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readBackup(uri)
        }

    internal val bluetoothAdapter: BluetoothAdapter? by lazy {
        getSystemService(BluetoothManager::class.java)?.adapter
    }

    internal var afterPermissions: (() -> Unit)? = null
    internal var afterBluetoothOn: ((BluetoothAdapter) -> Unit)? = null

    internal val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val action = afterPermissions
            afterPermissions = null
            if (result.values.all { it }) action?.invoke()
            else toast("Permission is needed to find and talk to the adapter")
        }

    /** Location for a recorded drive's route: asked when recording starts, never otherwise. */
    internal val routePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (!fine) toast("Recording without the route")
            beginRecording(withLocation = fine)
        }

    /** Asked once, so the connection notification (with its Disconnect button) can show. */
    internal val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Android 9 and older: the user picks where a saved file goes. */
    internal var pendingSave: java.io.File? = null
    internal val saveDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            val file = pendingSave; pendingSave = null
            if (uri == null || file == null) return@registerForActivityResult
            val ok = try { contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null } catch (e: Exception) { false }
            toast(if (ok) getString(R.string.file_saved_picked) else getString(R.string.file_save_failed))
        }

    /** Record and replay: a recording file the user picked. It's checked line by line before it's replayed. */
    internal val openRecordingLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val opened = try {
                    withContext(Dispatchers.IO) { com.obdreader.app.session.ReplyRecordings.importFile(this@MainActivity, uri) }
                } catch (e: CancellationException) { throw e } catch (e: com.obdreader.app.obd.ReplyRecording.BadRecording) {
                    toast(getString(R.string.replies_bad, e.message)); return@launch
                } catch (e: Exception) {
                    toast(getString(R.string.replies_open_failed)); return@launch
                } catch (e: OutOfMemoryError) {
                    toast(getString(R.string.replies_open_failed)); return@launch
                }
                currentSheet?.dismiss()
                replay(opened.first, opened.second)
            }
        }

    /** A text file the app made (code report, adapter log): save it to the phone, or share it. */
    internal fun offerFile(file: java.io.File, subject: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(subject.removePrefix("OBD2 Reader ").replaceFirstChar { it.uppercase() })
            .setItems(arrayOf(getString(R.string.file_save), getString(R.string.share))) { _, i ->
                if (i == 1) { Exports.share(this, file, "text/plain", subject); return@setItems }
                if (Exports.saveToDownloads(this, file, "text/plain")) {
                    android.util.Log.i("OBD2Save", "saved ${file.name} to Downloads")
                    toast(getString(R.string.file_saved, file.name))
                } else {
                    pendingSave = file
                    saveDocumentLauncher.launch(file.name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    internal val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val action = afterBluetoothOn
            afterBluetoothOn = null
            val adapter = bluetoothAdapter
            if (adapter?.isEnabled == true) action?.invoke(adapter)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The chosen theme (or the day or night one) must be set before any view is created.
        shownStyle = com.obdreader.app.ui.StyleClock.styleId(this)
        setTheme(AppThemes.byId(shownStyle).style)
        AppPrefs.applyUnits(prefs)
        super.onCreate(savedInstanceState)
        // While the app is open on screen, the phone doesn't lock, connected or not. Android drops
        // this by itself when the app is minimised, so it never keeps the screen on in the background.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Dark status-bar icons on the light theme, light icons on the dark ones.
        val lightTheme = themeBool(R.attr.obdIsLight)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightTheme
            isAppearanceLightNavigationBars = lightTheme
        }
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).applyTitleFonts()
        fadeFromLastStyle()

        // Edge-to-edge: pages pad below the status bar; the bottom bar pads itself above the nav bar.
        // The livery stripes start below the status bar too (its icons vanished on the black and
        // white bands), and a strip of the page's backdrop covers the status bar, so text scrolled
        // up goes behind it instead of under the clock.
        val pages = listOf(findViewById<View>(R.id.content), findViewById<View>(R.id.contentDiag), findViewById<View>(R.id.contentGraphs))
        val stripes = listOf(R.id.stripesDash, R.id.stripesDiag, R.id.stripesGraphs).map { findViewById<View>(it) }
        val scrim = findViewById<com.obdreader.app.ui.StatusBarScrim>(R.id.statusScrim)
        val basePadTop = pages.first().paddingTop
        val basePadLeft = pages.first().paddingLeft
        val basePadRight = pages.first().paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // Sideways with the three-button bar (or a camera cutout) at the side, the pages keep clear of it too.
            pages.forEach { it.setPadding(basePadLeft + bars.left, basePadTop + bars.top, basePadRight + bars.right, it.paddingBottom) }
            stripes.forEach { s -> (s.layoutParams as ViewGroup.MarginLayoutParams).let { if (it.topMargin != bars.top) { it.topMargin = bars.top; s.layoutParams = it } } }
            scrim.layoutParams = scrim.layoutParams.apply { height = bars.top }
            insets
        }

        statusText = findViewById(R.id.statusText)
        statusDetail = findViewById(R.id.statusDetail)
        statusText2 = findViewById(R.id.statusText2)
        bottomNav = findViewById(R.id.bottomNav)
        pageDashboard = findViewById(R.id.pageDashboard)
        pageDiagnostics = findViewById(R.id.pageDiagnostics)
        pageGraphs = findViewById(R.id.pageGraphs)
        connectBtn = findViewById(R.id.connectButton)
        demoBtn = findViewById(R.id.demoButton)
        readDtcBtn = findViewById(R.id.readDtcButton)
        vinBtn = findViewById(R.id.vinButton)
        towerList = findViewById(R.id.gaugeGrid)
        vinText = findViewById(R.id.vinText)
        vehicleText = findViewById(R.id.vehicleText)
        protocolText = findViewById(R.id.protocolText)
        protocolSetting = findViewById(R.id.protocolSetting)
        findViewById<View>(R.id.protocolRow).setOnClickListener { showProtocolPicker() }
        updateProtocolSetting()
        carSetting = findViewById(R.id.carSetting)
        chooseCarButton = findViewById(R.id.chooseCarButton)
        findViewById<View>(R.id.carRow).setOnClickListener { showCarPicker() }
        findViewById<View>(R.id.carExpect).setOnClickListener { savedCar()?.let { showExpectations(it) } }
        chooseCarButton.setOnClickListener { showCarPicker() }
        updateCarSetting()
        dtcStatus = findViewById(R.id.dtcStatus)
        dtcSub = findViewById(R.id.dtcSub)
        milBanner = findViewById(R.id.milBanner)
        dtcContainer = findViewById(R.id.dtcContainer)
        logText = findViewById(R.id.logText)
        logToggle = findViewById(R.id.logToggle)

        setupDashboard()
        // After setupDashboard: Digital cockpit and Cyberpunk give the root their glow there.
        findViewById<com.obdreader.app.ui.StatusBarScrim>(R.id.statusScrim).backdropOf = findViewById(R.id.root)
        applyRedline()
        applyOrientation()

        connectBtn.display = true
        connectBtn.setTextSizeSp(19f)
        demoBtn.text = getString(R.string.demo_car)
        demoBtn.kind = SkewButton.Kind.OUTLINE
        readDtcBtn.text = getString(R.string.read_codes)

        connectBtn.setOnClickListener {
            when {
                ObdSession.isActive -> disconnect()
                needsCar() -> showCarPicker(firstRun = true) { showConnectSheet() }
                else -> showConnectSheet()
            }
        }
        demoBtn.setOnClickListener { if (!ObdSession.isActive) startSession(DemoTransport(VinDecoder.sampleVin(savedCar()?.make, savedCar()?.year))) }
        readDtcBtn.setOnClickListener { readDtcs() }
        vinBtn.setOnClickListener { readVin() }
        findViewById<View>(R.id.logCard).setOnClickListener { toggleLog() }
        findViewById<View>(R.id.helpRow).setOnClickListener { showHelp() }
        findViewById<View>(R.id.reportRow).setOnClickListener { openFeedback(bug = true) }
        findViewById<View>(R.id.ideaRow).setOnClickListener { openFeedback(bug = false) }
        // The button checks GitHub; the rest of the row shows what's changed since this version.
        findViewById<View>(R.id.updatesRow).setOnClickListener { showChangesSince() }
        findViewById<View>(R.id.updatesCheck).setOnClickListener { checkForUpdate(manual = true) }
        showUpdateRow(null)
        // Once a day, a quiet look at GitHub for a newer release.
        lifecycleScope.launch { kotlinx.coroutines.delay(4000); checkForUpdate(manual = false) }
        // And about once a day in the background, so a new version is announced even when the app isn't open.
        com.obdreader.app.session.UpdateCheckJob.schedule(applicationContext)
        findViewById<View>(R.id.themeButton).setOnClickListener { showThemePicker() }
        findViewById<View>(R.id.logShare).setOnClickListener {
            offerFile(Exports.adapterLog(this, ObdSession.fullLog(), ObdSession.state.value), "OBD2 Reader adapter log")
        }
        shareReportBtn = findViewById(R.id.shareReportButton)
        shareReportBtn.setOnClickListener {
            lifecycleScope.launch {
                // The report says whether the codes look recently cleared, so read that first if it hasn't been.
                if (ObdSession.state.value.isConnected && ObdSession.state.value.cleared == null) {
                    try { ObdSession.readClearedCheck() } catch (e: CancellationException) { throw e } catch (ignored: Exception) { }
                }
                offerFile(Exports.codeReport(this@MainActivity, ObdSession.state.value, savedCar()?.label), "OBD2 Reader trouble code report")
            }
        }
        findViewById<MaterialSwitch>(R.id.reconnectSwitch).apply {
            isChecked = AppPrefs.autoReconnect(prefs)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(AppPrefs.AUTO_RECONNECT, on).apply() }
        }
        findViewById<View>(R.id.reconnectRow).apply {
            background = panelBackground()
            setOnClickListener { findViewById<MaterialSwitch>(R.id.reconnectSwitch).toggle() }
            readAsSwitch(findViewById(R.id.reconnectSwitch))
        }
        findViewById<MaterialSwitch>(R.id.pipSwitch).apply {
            isChecked = prefs.getBoolean(PREF_PIP, false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(PREF_PIP, on).apply(); updatePipParams() }
        }
        findViewById<View>(R.id.pipRow).apply {
            background = panelBackground()
            visibility = if (pipSupported()) View.VISIBLE else View.GONE
            setOnClickListener { findViewById<MaterialSwitch>(R.id.pipSwitch).toggle() }
            readAsSwitch(findViewById(R.id.pipSwitch))
        }
        findViewById<MaterialSwitch>(R.id.alertsSwitch).apply {
            isChecked = AppPrefs.alerts(prefs)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(AppPrefs.ALERTS, on).apply() }
        }
        findViewById<View>(R.id.alertsRow).apply {
            background = panelBackground()
            setOnClickListener { findViewById<MaterialSwitch>(R.id.alertsSwitch).toggle() }
            readAsSwitch(findViewById(R.id.alertsSwitch))
        }
        Alerts.start(this)
        findViewById<TextView>(R.id.slotsSetting).text = slots.joinToString(", ") { readout(it).label }
        findViewById<TextView>(R.id.unitsSetting).text = "${Units.speedUnit}, ${Units.temperatureUnit}, ${Units.pressure.unit}"
        findViewById<View>(R.id.unitsRow).apply {
            background = panelBackground()
            setOnClickListener { showUnitsPicker() }
        }
        findViewById<TextView>(R.id.widgetSetting).text = widgetReadingsLabel()
        findViewById<View>(R.id.widgetRow).apply {
            background = panelBackground()
            setOnClickListener { showWidgetReadings(com.obdreader.app.obd.WidgetContent.decodeReadings(prefs.getString(com.obdreader.app.widget.ObdWidget.PREF_READINGS, null)).toMutableList()) }
        }
        findViewById<TextView>(R.id.marketSetting).text = marketLabel()
        findViewById<View>(R.id.marketRow).apply {
            background = panelBackground()
            setOnClickListener { showMarketPicker() }
        }
        findViewById<TextView>(R.id.autoSetting).text = carReadingsLabel()
        findViewById<View>(R.id.autoRow).apply {
            background = panelBackground()
            setOnClickListener { showCarReadings(com.obdreader.app.obd.CarReadings.decode(prefs.getString(AppPrefs.CAR_READINGS, null)).toMutableList()) }
        }
        findViewById<TextView>(R.id.appIconSetting).text = AppIcons.current(this).name
        findViewById<View>(R.id.appIconRow).apply {
            background = panelBackground()
            setOnClickListener { showIconPicker() }
        }
        findViewById<View>(R.id.scanRow).apply {
            background = panelBackground()
            setOnClickListener { runCarScan() }
        }
        setupDeeperChecks()
        findViewById<View>(R.id.slotsRow).apply {
            background = panelBackground()
            setOnClickListener { showSlotPicker(slots.toMutableList()) }
        }
        setupGroups()
        setupGraphs()
        setupScore()
        // Drawn again in another style while connected: the traces carry on from the last 30 seconds.
        seedDashTraces()
        if (savedInstanceState == null) Exports.cleanUp(this) // shared reports and logs are temporary copies

        bottomNav.setOnItemSelectedListener { item ->
            showPage(item.itemId)
            true
        }
        // Back from another tab returns to the Dashboard instead of closing the app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pageDiagnostics.visibility == View.VISIBLE || pageGraphs.visibility == View.VISIBLE) {
                    bottomNav.selectedItemId = R.id.nav_dashboard
                } else {
                    // Back from the Dashboard minimises the app (as Android 12 and newer do), so the
                    // connection and any recording carry on. Only swiping it away closes it.
                    moveTaskToBack(true)
                }
            }
        })

        // The connection lives in ObdSession (shared with Android Auto and the background
        // service), so this screen just draws whatever state it's in.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { ObdSession.state.collect { render(it); onStateForPip(it) } }
                launch {
                    // Live polling adds dozens of lines a second; redraw the log at most four
                    // times a second, and only while it's open.
                    combine(ObdSession.codeLog, ObdSession.log, logShown) { codes, lines, shown ->
                        if (shown) com.obdreader.app.obd.VinPrivacy.scrubLog(ObdSession.fullLog(codes, lines), vinMode(), Exports.knownVins(this@MainActivity, ObdSession.state.value.vin)) else null
                    }
                        .conflate()
                        .collect { lines ->
                            if (lines != null) logText.text = styledLog(lines)
                            delay(LOG_REFRESH_MS)
                        }
                }
                launch { LogRecorder.active.collect { renderRecording(it) } }
                launch { DriveScorer.results.collect { renderScore() } }
                launch { DriveScorer.topSpeed.collect { showTopSpeed(it) } }
                launch { com.obdreader.app.session.PhoneMotion.status.collect { showMotionStatus(it) } }
                // When a drive ends, sum it up (once), and keep it as the last drive.
                launch {
                    DriveScorer.lastTrip.collect { latest ->
                        // A drive that ended while the app was away (even closed) is kept on the phone,
                        // so it's summed up the next time the app is on screen.
                        val trip = latest ?: savedTrip()
                        if (trip == null || trip.endedAt <= prefs.getLong(PREF_LAST_TRIP_SHOWN, 0L)) return@collect
                        prefs.edit().putString(PREF_LAST_TRIP, trip.encode()).putLong(PREF_LAST_TRIP_SHOWN, trip.endedAt).apply()
                        lastTripButton?.visibility = View.VISIBLE
                        historyButton?.visibility = View.VISIBLE
                        showTripSummary(trip, justEnded = true)
                    }
                }
            }
        }

        // Reconnect to the last adapter by itself when the app opens (if that's switched on).
        if (savedInstanceState == null) ObdSession.autoConnect(this)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // The bottom bar restores its selected tab by itself after the screen is recreated; show its page.
        showPage(bottomNav.selectedItemId)
        if (savedInstanceState == null) handleWidgetConnect(intent)
        drawCarTilesIfAsked(intent)
        if (savedInstanceState == null) showCodesIfAsked(intent)
        if (savedInstanceState == null) showUpdateIfAsked(intent)
        com.obdreader.app.widget.ObdWidget.Feed.start(this)
        com.obdreader.app.widget.ObdWidget.refresh(this)
        // The car comes first: it decides how fault codes are explained, the redline and the protocol.
        // Checked here rather than only on a fresh start, so it comes back if the screen is recreated.
        if (needsCar() && !carPickerShowing) showCarPicker(firstRun = true)
        else if (reopenAppearance) { reopenAppearance = false; showThemePicker() }
    }

    // ---------- Driving score ----------

    internal lateinit var scoreCard: LinearLayout
    internal var scoreStyle = DrivingScore.Style.EVERYDAY

    internal var lastTripButton: SkewButton? = null

    internal var historyButton: SkewButton? = null

    /** The trip summary: what the drive added up to, as a sheet of tiles. */
    /** The drive's g-force on its summary: the hardest braking, speeding up and cornering, and any harsh moments. */
    /** Shrinks one line of text from [maxSp] towards [minSp] until it fits its width, e.g. "1.21 g" on a 360dp phone. */
    internal fun TextView.shrinkToFit(maxSp: Float, minSp: Float) {
        addOnLayoutChangeListener { v, l, _, r, _, oldL, _, oldR, _ ->
            if (r - l == oldR - oldL) return@addOnLayoutChangeListener
            val t = v as TextView
            val room = (r - l) - t.paddingLeft - t.paddingRight
            var size = maxSp
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            while (size > minSp && t.paint.measureText(t.text.toString()) > room) {
                size -= 1f
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            }
        }
    }

    /**
     * A settings row with a switch at its end: TalkBack reads the whole row (its title and help
     * line) as one switch with its on or off state, instead of a row and then an unnamed "Switch, off".
     */
    internal fun View.readAsSwitch(switch: MaterialSwitch) {
        switch.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setAccessibilityDelegate(object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Switch::class.java.name
                info.isCheckable = true; info.isChecked = switch.isChecked
            }
        })
    }

    /** The deeper-check sheet on screen, so a search can close it before showing its results. */
    internal var currentSheet: BottomSheetDialog? = null

    // ---- Enhanced diagnostics ----

    /** Modules the last "Search every address" found, so module information can include them. */
    internal var foundModules: List<ModuleScan.Module> = emptyList()

    /** True while a drive file is being read for the drive card or video overlay, so a second tap waits. */
    internal var readingDrive = false

    // ---------- Graphs ----------

    /** The channels that can be graphed, with a colour each so stacked charts are easy to tell apart. */
    internal data class Channel(val key: String, val label: String, val unit: String, val decimals: Int, val min: Double, val max: Double)

    internal val channels: List<Channel> by lazy {
        Pids.dashboard.map { Channel(it.hex, if (it.number == Pids.RPM) "RPM" else it.label, Units.unit(it.hex, it.unit), Units.decimals(it.hex, it.decimals), Units.value(it.hex, it.min), Units.value(it.hex, it.max)) } +
            Channel(Boost.KEY, Boost.LABEL, Units.unit(Boost.KEY, "kPa"), Units.decimals(Boost.KEY, 0), Units.value(Boost.KEY, Boost.MIN), Units.value(Boost.KEY, Boost.MAX)) +
            Channel(VOLTAGE_KEY, "Battery", "V", 1, 11.0, 15.0) +
            // G-force from the phone's motion sensor (no car reading needed beyond speed).
            Channel(GForce.FORWARD_KEY, getString(R.string.g_forward), "g", 2, -1.0, 1.0) +
            Channel(GForce.SIDE_KEY, getString(R.string.g_side), "g", 2, -1.0, 1.0) +
            // More standard readings, read only when the car lists them.
            Pids.extraNumbers.map { Channel(it.hex, it.label, Units.unit(it.hex, it.unit), Units.decimals(it.hex, it.decimals), Units.value(it.hex, it.min), Units.value(it.hex, it.max)) }
    }

    /** The graphs shown, in order: a newly added reading goes to the bottom, and Change order moves them. */
    internal val graphChosen = mutableListOf<String>()
    internal lateinit var graphChipGroup: ChipGroup
    internal lateinit var graphCount: TextView

    internal lateinit var graphOrderBtn: MaterialButton

    /** The Graphs tab's reading tabs: null is All. */
    internal var graphTab: com.obdreader.app.obd.ReadingGroups.Group? = null
    internal lateinit var graphTabs: com.google.android.material.tabs.TabLayout

    internal var draggedGraph: View? = null

    // ---------- Video overlay ----------

    internal var overlayJob: kotlinx.coroutines.Job? = null

    /** A dropdown list that always shows all its items, ignoring what's typed in the box. */
    internal class AllItemsAdapter(context: android.content.Context, private val items: List<String>) :
        ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, items) {
        override fun getFilter(): android.widget.Filter = object : android.widget.Filter() {
            override fun performFiltering(constraint: CharSequence?) = FilterResults().apply { values = items; count = items.size }
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) = notifyDataSetChanged()
        }
    }

    /** A device in the Bluetooth list, and how to talk to it. */
    internal class Found(val device: BluetoothDevice, val name: String, var ble: Boolean, val paired: Boolean)

    // ---------- Session ----------

    internal fun startSession(t: ObdTransport, onConnected: (() -> Unit)? = null) {
        if (ObdSession.isActive) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && t !is DemoTransport && t !is com.obdreader.app.obd.ReplayTransport &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ObdSession.connect(this, t, onConnected)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        drawCarTilesIfAsked(intent)
        showCodesIfAsked(intent)
        showUpdateIfAsked(intent)
        handleWidgetConnect(intent)
    }

    /** The "new version available" notification opens the Update window, with what's new. */
    internal fun showUpdateIfAsked(intent: Intent?) {
        // Debug builds: the install test asks for the notification with a made-up version, to check it shows.
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 && intent?.getBooleanExtra("update_notice_demo", false) == true) {
            intent.removeExtra("update_notice_demo")
            val build = Updater.installedBuild(this).toInt() + 1
            com.obdreader.app.session.UpdateCheckJob.post(applicationContext, com.obdreader.app.obd.UpdateCheck.Release(
                "v9.9.9-build$build", "9.9.9", build, "• A sample of what's new.", Updater.RELEASES_PAGE, 0L, Updater.RELEASES_PAGE))
            return
        }
        if (intent?.getBooleanExtra(com.obdreader.app.session.UpdateCheckJob.EXTRA_SHOW_UPDATE, false) != true) return
        intent.removeExtra(com.obdreader.app.session.UpdateCheckJob.EXTRA_SHOW_UPDATE)
        android.util.Log.i("OBD2Updater", "opened from the new version notification")
        com.obdreader.app.session.UpdateCheckJob.dismiss(this)
        if (updateBusy) return // already checking, or the Update window is already open
        availableUpdate?.let { offerUpdate(it) } ?: checkForUpdate(manual = true)
    }

    /** The widget's Connect button: connects to the last adapter, or offers the adapters if there isn't one. */
    internal fun handleWidgetConnect(intent: Intent?) {
        if (intent?.getBooleanExtra(com.obdreader.app.widget.ObdWidget.EXTRA_CONNECT, false) != true) return
        intent.removeExtra(com.obdreader.app.widget.ObdWidget.EXTRA_CONNECT)
        android.util.Log.i("OBD2Widget", "connect from the widget")
        bottomNav.selectedItemId = R.id.nav_dashboard
        if (ObdSession.isActive) return
        val last = ObdSession.lastAdapter(this)
        when {
            needsCar() -> showCarPicker(firstRun = true) { showConnectSheet() }
            last != null -> startSession(last)
            else -> showConnectSheet()
        }
    }

    /**
     * Report a problem or suggest an improvement: opens the project's GitHub form in the browser,
     * with the app version and phone (and, for a problem, the chosen car and last adapter's name)
     * already filled in. Nothing is sent until the person submits the form themselves; no VIN,
     * location or log is included.
     */
    internal fun openFeedback(bug: Boolean) {
        val q = linkedMapOf<String, String>()
        q["template"] = if (bug) "bug_report.yml" else "feature_request.yml"
        q["version"] = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        if (bug) {
            q["phone"] = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
            savedCar()?.label?.let { q["car"] = it }
            ObdSession.lastAdapterName(this)?.let { q["adapter_name"] = it }
        }
        val url = "https://github.com/CalmAussie/obd2-reader/issues/new?" + q.entries.joinToString("&") { (k, v) -> "$k=" + java.net.URLEncoder.encode(v, "UTF-8") }
        android.util.Log.i("OBD2Feedback", "opening ${q["template"]} (version ${q["version"]})")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (e: android.content.ActivityNotFoundException) {
            toast("No web browser found to open the form")
        }
    }

    /** The new-code notification opens Diagnostics, where the code is listed with what it means. */
    internal fun showCodesIfAsked(intent: Intent?) {
        // Debug builds: the install test makes the demo car report a new pending code mid-drive.
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            intent?.getStringExtra("demo_new_code")?.let {
                com.obdreader.app.obd.DemoTestHooks.newPendingCode = it; intent.removeExtra("demo_new_code")
            }
            // And a speed for the demo car to hold (above 0), or back to normal driving (0 or less).
            if (intent?.hasExtra("demo_speed") == true) {
                com.obdreader.app.obd.DemoTestHooks.fixedSpeed = intent.getIntExtra("demo_speed", 0).takeIf { it > 0 }
                intent.removeExtra("demo_speed")
            }
        }
        if (intent?.getBooleanExtra(com.obdreader.app.session.Alerts.EXTRA_SHOW_CODES, false) != true) return
        intent.removeExtra(com.obdreader.app.session.Alerts.EXTRA_SHOW_CODES)
        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_diagnostics
    }

    /** Debug builds: the install test asks for pictures of the Android Auto tiles in every style. */
    internal fun drawCarTilesIfAsked(intent: Intent?) {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0 || intent?.getBooleanExtra("car_tiles", false) != true) return
        intent.removeExtra("car_tiles")
        Thread {
            val files = try { com.obdreader.app.car.CarTiles.renderSamples(applicationContext) } catch (e: Throwable) { android.util.Log.w("OBD2CarTiles", "failed", e); emptyList() }
            android.util.Log.i("OBD2CarTiles", "drew ${files.size}: ${files.joinToString(" ") { it.name }}")
        }.start()
    }

    internal var pipConnected: Boolean? = null
    internal var pipView: FrameLayout? = null
    internal var pipLeftAt = 0L

    /** Before Android 12, leaving the app (Home or another app) goes into the window here. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && pipWanted() && !isInPictureInPictureMode) {
            try { enterPictureInPictureMode(pipParams()) } catch (e: Exception) { android.util.Log.w("OBD2Pip", "enter: ${e.message}") }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val root = findViewById<ViewGroup>(android.R.id.content)
        val main = root.getChildAt(0)
        android.util.Log.i("OBD2Pip", "picture-in-picture: ${if (isInPictureInPictureMode) "on" else "off"}")
        if (isInPictureInPictureMode) {
            currentSheet?.dismiss()
            val v = pipView ?: buildPipView().also { pipView = it; root.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
            main.visibility = View.INVISIBLE
            v.visibility = View.VISIBLE
            drawPip()
        } else {
            pipLeftAt = System.currentTimeMillis()
            pipView?.visibility = View.GONE
            main.visibility = View.VISIBLE
        }
    }

    /** Closing the floating window is minimising, not closing the app: the connection carries on (the notification has Disconnect). */
    override fun onStop() {
        if (isInPictureInPictureMode) com.obdreader.app.session.AppClose.keepConnectionUntil = System.currentTimeMillis() + 5_000
        super.onStop()
    }

    internal lateinit var pipStatus: TextView
    internal lateinit var pipMain: TextView
    internal lateinit var pipMainLabel: TextView
    internal lateinit var pipMainBox: LinearLayout
    internal val pipSmall = mutableListOf<Pair<TextView, TextView>>()

    internal fun disconnect() = ObdSession.disconnect()

    /** Draws the shared connection state: status, buttons, dials, tiles, VIN and codes. */
    internal fun render(s: ObdSession.State) {
        val status = when (s.phase) {
            ObdSession.Phase.IDLE -> Status.IDLE
            ObdSession.Phase.CONNECTING -> Status.CONNECTING
            ObdSession.Phase.CONNECTED -> Status.CONNECTED
            ObdSession.Phase.WAITING -> Status.CONNECTING
            ObdSession.Phase.ERROR -> Status.ERROR
        }
        setStatus(status, s.title, s.detail)
        updateButtons(connected = s.isConnected, busy = s.phase == ObdSession.Phase.CONNECTING || s.phase == ObdSession.Phase.WAITING)
        if (pageGraphs.visibility == View.VISIBLE) graphRate.setIfChanged(when {
            s.isConnected && s.rate > 0 -> String.format(Locale.US, "%.0f readings a second from %s", s.rate, s.adapterName ?: "the car")
            s.isConnected -> "Connected. Readings appear here as they arrive."
            s.phase == ObdSession.Phase.WAITING -> s.title + ". " + s.detail
            else -> getString(R.string.graphs_hint)
        })
        recordBtn.isEnabled = s.isConnected || LogRecorder.active.value != null
        showRecordGroup(s.isConnected)

        if (s.phase != lastPhase) {
            renderScore()
            if (s.phase == ObdSession.Phase.CONNECTING) {
                if (lastPhase != ObdSession.Phase.WAITING) clearReadings()
                startLights.start()
            } else {
                startLights.out()
            }
            if (s.phase == ObdSession.Phase.WAITING) {
                lastUnsupported = emptySet() // so "N/A" comes back when the car wakes up
                setRpm(null); setSpeed(null)
                tiles.values.forEach { it.setValue(null) }
                slots.forEach { setBigReading(it, null) }
            }
            // Lights out, then the rev ring sweeps: like a car's self-test when you turn the key.
            if (s.isConnected && (lastPhase == ObdSession.Phase.CONNECTING || lastPhase == ObdSession.Phase.WAITING)) roundel.sweep()
            if (!s.isConnected && s.phase != ObdSession.Phase.WAITING && s.phase != ObdSession.Phase.CONNECTING) {
                // Disconnected: blank the gauges so old readings aren't mistaken for live ones.
                // Codes and the VIN stay on screen until the next connection.
                setRpm(null); setSpeed(null)
                tiles.values.forEach { it.setValue(null) }
                slots.forEach { setBigReading(it, null) }
                trace.clear()
                lastReadings = emptyMap()
                lastUnsupported = emptySet()
                lastVinChecked = false
                lastCodes = null
            }
            lastPhase = s.phase
        }

        // Live readings only redraw the Dashboard while it's on screen. Updating it behind another
        // tab relaid out the whole screen 20 times a second for nothing; it catches up on return.
        if (s.readings !== lastReadings && pageDashboard.visibility == View.VISIBLE) {
            for ((key, value) in s.readings) {
                if (lastReadings.containsKey(key) && lastReadings[key] == value) continue
                when (key) {
                    RPM_KEY -> {
                        setRpm(value?.toFloat())
                        if (value == null && s.isConnected) teleRpmText.showText("N/A")
                    }
                    SPEED_KEY -> {
                        setSpeed(Units.value(key, value)?.toFloat())
                        if (value == null && s.isConnected) { decals[SPEED_KEY]?.setUnsupported(); teleSpeedText.showText("N/A") }
                    }
                    else -> {
                        val shown = Units.value(key, value) // °F where chosen
                        if (shown == null && s.isConnected) {
                            // Connected, but the car has stopped answering this one: N/A, never an old number.
                            showNoReading(key)
                        } else {
                            if (key in slots) setBigReading(key, shown)
                            tiles[key]?.setValue(shown)
                        }
                    }
                }
            }
            if (s.readings[GForce.FORWARD_KEY] != lastReadings[GForce.FORWARD_KEY] || s.readings[GForce.SIDE_KEY] != lastReadings[GForce.SIDE_KEY]) {
                gMeter.set(s.readings[GForce.FORWARD_KEY], s.readings[GForce.SIDE_KEY])
            }
            lastReadings = s.readings
        }
        // Graphs: names, notes (fuel system status under fuel trims) and N/A for this car.
        if (s.supported != lastSupported || s.relabels != lastRelabels || s.unsupported != lastGraphUnsupported || s.readings["03"] != lastFuelStatus || s.isConnected != lastGraphConnected) {
            if ((s.supported != lastSupported || s.relabels != lastRelabels) && ::graphCount.isInitialized) showGraphChips()
            refreshGraphNotes(s)
            lastSupported = s.supported; lastRelabels = s.relabels; lastGraphUnsupported = s.unsupported
            lastFuelStatus = s.readings["03"]; lastGraphConnected = s.isConnected
        }
        // "Codes recently cleared?": its row shows the verdict once it's been read.
        if (s.cleared != lastCleared) {
            clearedHelp()?.text = s.cleared?.headline() ?: getString(R.string.cleared_help)
            lastCleared = s.cleared
        }
        // A stand-in reading is labelled for what it really is.
        if (s.standIns != lastStandIns) {
            tiles.forEach { (k, t) -> if (k != VOLTAGE_KEY && k !in GForce.KEYS) t.labelOverride = s.standIns[k] }
            decals.forEach { (k, d) -> if (k != VOLTAGE_KEY) d.labelOverride = s.standIns[k] }
            lastStandIns = s.standIns
        }
        // Battery: whether it looks like it's charging, estimated from voltage and engine speed.
        if (s.charging != lastCharging) {
            showCharging(s.charging)
            lastCharging = s.charging
        }
        if (s.unsupported != lastUnsupported) {
            s.unsupported.forEach { tiles[it]?.setUnsupported() }
            // The main readouts say so too, instead of a dash that never fills in.
            slots.filter { it in s.unsupported }.forEach { showNoReading(it) }
            lastUnsupported = s.unsupported
        }
        if (s.isConnected) {
            protocolText.setIfChanged("Protocol: " + (ObdProtocols.byCode(s.protocolCode)?.name ?: s.summary.orEmpty()))
        }
        if (s.isConnected != lastProtocolConnected || s.protocolCode != lastProtocolCode) {
            lastProtocolConnected = s.isConnected; lastProtocolCode = s.protocolCode
            updateProtocolSetting()
        }
        if (s.batteryCheck != null && s.batteryCheck != shownBatteryCheck) showBatteryCheck(s.batteryCheck)
        if (s.vinChecked && !lastVinChecked) {
            showVin(s.vin)
            lastVinChecked = true
        }
        val codes = s.codes
        if (codes != null && codes != lastCodes) renderCodes(codes)
        lastCodes = codes
        shareReportBtn.visibility = if (codes != null) View.VISIBLE else View.GONE
    }

    /**
     * Shows the VIN: the one the user entered from their VIN plate if there is one, else the one
     * the car sent ([carVin]). The car's VIN comes from its engine computer, which can hold a VIN
     * that isn't the car's (a typo when it was programmed, or a computer from another car), so when
     * the two differ both are shown.
     */
    internal var shownBatteryCheck: com.obdreader.app.obd.BatteryCheck.Result? = null

    /** What the offline vPIC data says about [vinDetailsVin] (null: nothing), once it's been read. */
    internal var vinDetails: com.obdreader.app.obd.VinDetails.Details? = null
    internal var vinDetailsVin: String? = null
    internal var vinDetailsLoading: String? = null
    internal var shownCarVin: String? = null

    /**
     * The VIN the user tapped to see in full, on screen only: Hide VIN still applies to everything
     * shared. It's hidden again when they tap it again or the app leaves the screen (onPause).
     */
    internal var vinRevealed: String? = null

    // ---------- Appearance ----------

    internal fun appVersion(): String =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

    override fun onDestroy() {
        // Swiped away or closed for good (not just minimised or rotated): stop the app's work.
        android.util.Log.i("OBD2Close", "screen destroyed: finishing=$isFinishing, picture-in-picture=$isInPictureInPictureMode, left it ${System.currentTimeMillis() - pipLeftAt} ms ago")
        // The floating window being put away or swapped back to full screen isn't closing the app.
        if (isInPictureInPictureMode || System.currentTimeMillis() - pipLeftAt < 5_000) com.obdreader.app.session.AppClose.keepConnectionUntil = System.currentTimeMillis() + 5_000
        if (isFinishing && !isChangingConfigurations) com.obdreader.app.session.AppClose.closed(applicationContext)
        super.onDestroy()
    }

    // ---------- Swiping between tabs ----------

    internal val tabOrder = listOf(R.id.nav_dashboard, R.id.nav_graphs, R.id.nav_diagnostics)
    internal var swipeDownX = 0f
    internal var swipeDownY = 0f
    internal var swipeDownTime = 0L
    internal var swipeTracking = false
    internal var swiping = false

    /**
     * A sideways swipe across the screen moves to the next tab (right to left) or the previous one
     * (left to right). A tap, a vertical scroll or a short sideways nudge does nothing, so buttons
     * and scrolling work as before. Once a swipe is clearly sideways, the touch is taken away from
     * whatever is under the finger, so a button under it isn't pressed.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastTouchMs = System.currentTimeMillis()
        if (draggedGraph != null) { swipeTracking = false; swiping = false; return super.dispatchTouchEvent(ev) }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeDownX = ev.rawX; swipeDownY = ev.rawY; swipeDownTime = ev.eventTime
                swipeTracking = ::bottomNav.isInitialized; swiping = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> { swipeTracking = false; swiping = false }
            MotionEvent.ACTION_MOVE -> if (swipeTracking && !swiping) {
                val dx = ev.rawX - swipeDownX; val dy = ev.rawY - swipeDownY
                when {
                    kotlin.math.abs(dy) > dp(28f) && kotlin.math.abs(dy) > kotlin.math.abs(dx) -> swipeTracking = false
                    kotlin.math.abs(dx) > dp(40f) && kotlin.math.abs(dx) > 2.5f * kotlin.math.abs(dy) -> {
                        swiping = true
                        val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancel); cancel.recycle()
                    }
                }
            }
            MotionEvent.ACTION_UP -> if (swiping) {
                swiping = false; swipeTracking = false
                val dx = ev.rawX - swipeDownX; val dy = ev.rawY - swipeDownY
                val width = resources.displayMetrics.widthPixels
                val quick = ev.eventTime - swipeDownTime < 350 && kotlin.math.abs(dx) > width * 0.25f
                if ((kotlin.math.abs(dx) > width * 0.4f || quick) && kotlin.math.abs(dx) > 2f * kotlin.math.abs(dy)) {
                    switchTabBySwipe(if (dx < 0) 1 else -1)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { swiping = false; swipeTracking = false }
        }
        if (swiping) return true
        return super.dispatchTouchEvent(ev)
    }

    /** Moves [step] tabs along (1 = next, -1 = previous), sliding the new tab in from that side. */
    internal fun switchTabBySwipe(step: Int) {
        val i = tabOrder.indexOf(bottomNav.selectedItemId)
        val next = tabOrder.getOrNull(i + step) ?: return
        android.util.Log.i("OBD2Swipe", "swiped to tab ${i + step}")
        bottomNav.selectedItemId = next
        val page = when (next) { R.id.nav_graphs -> pageGraphs; R.id.nav_diagnostics -> pageDiagnostics; else -> pageDashboard }
        page.translationX = step * dp(56f); page.alpha = 0.4f
        page.animate().translationX(0f).alpha(1f).setDuration(200)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    override fun onResume() {
        super.onResume()
        startStyleWatch()
        showBatteryNight() // the overnight readings may have moved on while the app was away
        Updater.showPrompt = { confirm -> showInstallPrompt(confirm) }
        Updater.waitingPrompt?.let { Updater.waitingPrompt = null; showInstallPrompt(it) }
        Updater.showProblem = { why -> updateProblem(why) }
        Updater.waitingProblem?.let { Updater.waitingProblem = null; updateProblem(it) }
    }

    override fun onPause() {
        stopStyleWatch()
        Updater.showPrompt = null
        Updater.showProblem = null
        // A VIN tapped to show is hidden again as the app leaves the screen: here rather than in
        // onStop, so it's already hidden when Android takes the Recents (app switcher) picture.
        if (vinRevealed != null) { vinRevealed = null; showVin(shownCarVin) }
        super.onPause()
    }

    // ---------- App updates (from the GitHub releases) ----------

    internal var availableUpdate: com.obdreader.app.obd.UpdateCheck.Release? = null
    /** A check is under way or the Update window is up, so the daily check doesn't open a second one. */
    internal var updateBusy = false

    /** Last light reading, fed again every few seconds: the sensor only reports changes, and a switch waits for the light to stay changed. */
    internal var lastLux: Float? = null

    internal var carPickerShowing = false

    // ---------- Protocol setting ----------

    internal var lastProtocolConnected = false
    internal var lastProtocolCode: String? = null

    // ---------- Landscape ----------

    internal var landscape = false
    internal var landRow: LinearLayout? = null
    /** Rows built for landscape, removed again in portrait. */
    internal val landRows = ArrayList<LinearLayout>()
    /** Views moved for landscape, with where they came from, so portrait can put them back exactly. */
    internal val moved = ArrayList<Triple<View, ViewGroup, Pair<Int, ViewGroup.LayoutParams>>>()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientation()
        // The phone's dark mode may have changed.
        checkStyle()
    }

    internal var heroFitter: View.OnLayoutChangeListener? = null

    // ---------- UI helpers ----------

    internal fun clearReadings() {
        tiles.values.forEach { it.setValue(null) }
        setRpm(null)
        setSpeed(null)
        slots.forEach { setBigReading(it, null) }
        trace.clear()
        roundel.checkEngine = false
        vinText.setText(R.string.vin_unknown)
        vehicleText.setText(R.string.vehicle_unknown)
        vinRevealed = null
        if (AppPrefs.userVin(prefs) != null) showVin(null) else { shownCarVin = null; showVinTap(R.string.vin, canReveal = false, revealed = false) }
        protocolText.setText(R.string.protocol_unknown)
        dtcContainer.removeAllViews()
        setCodeBadge(0)
        setDtcStatus(getString(R.string.engine_not_checked), getString(R.string.dtc_hint))
    }

    internal fun setStatus(status: Status, title: String, detail: String) {
        statusText.setIfChanged(title)
        statusText2.setIfChanged(title)
        chooseCarButton.visibility = if (status == Status.ERROR) View.VISIBLE else View.GONE
        statusDetail.setIfChanged(detail)
        statusDetail.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE
    }

    internal fun updateButtons(connected: Boolean, busy: Boolean = false) {
        val active = connected || busy
        connectBtn.text = getString(if (active) R.string.disconnect else R.string.connect)
        connectBtn.kind = if (active) SkewButton.Kind.OUTLINE else SkewButton.Kind.PRIMARY
        demoBtn.isEnabled = !active
        // While connected (or connecting), Demo car is hidden and Disconnect takes the whole row.
        demoBtn.visibility = if (active) View.GONE else View.VISIBLE
        readDtcBtn.isEnabled = connected
        vinBtn.isEnabled = connected
    }

    internal fun showPage(itemId: Int) {
        pageDashboard.visibility = if (itemId == R.id.nav_dashboard) View.VISIBLE else View.GONE
        pageGraphs.visibility = if (itemId == R.id.nav_graphs) View.VISIBLE else View.GONE
        pageDiagnostics.visibility = if (itemId == R.id.nav_diagnostics) View.VISIBLE else View.GONE
        if (itemId != R.id.nav_diagnostics && ::graphRate.isInitialized) {
            // Back on the Dashboard: its traces weren't drawn behind the other tab, so they catch up first.
            if (itemId == R.id.nav_dashboard) seedDashTraces()
            lastReadings = emptyMap() // redraw every reading now that the Dashboard is back
            render(ObdSession.state.value)
        }
        updateFocus()
        graphStrips.forEach { it.invalidate() }
    }

    /** Shows the number of trouble codes on the Diagnostics tab, so they're visible from the Dashboard. */
    internal fun setCodeBadge(count: Int) {
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

    /** Raw adapter traffic stays muted; the plain-English lines and the code summary stand out. */
    internal fun styledLog(lines: List<String>): CharSequence {
        val strong = themeColor(R.attr.obdTextPrimary)
        val out = SpannableStringBuilder()
        lines.forEachIndexed { i, line ->
            if (i > 0) out.append('\n')
            val start = out.length
            out.append(line)
            if (!line.startsWith(">")) out.setSpan(ForegroundColorSpan(strong), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    internal val logShown = MutableStateFlow(false)

    internal fun toggleLog() {
        val show = logText.visibility != View.VISIBLE
        logText.visibility = if (show) View.VISIBLE else View.GONE
        logShown.value = show
        logToggle.setText(if (show) R.string.hide else R.string.show)
    }

    /** Setting the same text again still makes Android lay the screen out again; skip it. */
    internal fun TextView.setIfChanged(value: CharSequence) {
        if (text.toString() != value.toString()) text = value
    }

    internal fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    internal fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        /** The EV battery's charge while its sheet is open, kept across reopening (a gap of 10 minutes starts it again). */
        internal val evLog = EvBattery.ChargeLog()
        /** Recordings bigger than this (several hours of driving) are left out of the same-route comparison. */
        const val COMPARE_MAX_BYTES = 16L * 1024 * 1024
        /** The old screen's picture, faded out over the new one after a style switch. */
        internal var fadeFrom: android.graphics.Bitmap? = null
        /** Open the Style sheet again once the screen is redrawn (after turning day and night on). */
        internal var reopenAppearance = false
        internal const val VOLTAGE_KEY = ObdSession.VOLTAGE_KEY
        internal val RPM_KEY = String.format(Locale.US, "%02X", Pids.RPM)
        internal val SPEED_KEY = String.format(Locale.US, "%02X", Pids.SPEED)
        internal const val COOLANT_KEY = "05"
        internal const val THROTTLE_KEY = "11"
        internal const val PREF_GRAPHS = "graph_channels"
        internal const val PREF_GRAPH_TAB = "graph_tab"
        internal const val MAX_GRAPHS = 6  // six is what a CAN car answers in one request (ElmClient.MAX_MULTI_PIDS)
        internal const val FUEL_KEY = "2F"
        internal const val BLE_SCAN_MS = 12_000L
        internal const val LOG_REFRESH_MS = 250L
        /** Engine speed, road speed, throttle, engine load, manifold pressure and airflow. */
        internal val DASHBOARD_FOCUS = setOf("0C", "0D", "11", "04", "0B", "10")
        internal const val PREF_HOST = AppPrefs.HOST
        internal const val PREF_PORT = AppPrefs.PORT
        internal const val DEFAULT_HOST = AppPrefs.DEFAULT_HOST
        internal const val DEFAULT_PORT = AppPrefs.DEFAULT_PORT
        internal const val PREF_THEME = AppPrefs.THEME
        internal const val PREF_CAR_MAKE = AppPrefs.CAR_MAKE
        internal const val PREF_CAR_YEAR = AppPrefs.CAR_YEAR
        /** NHTSA's vPIC data, slimmed (tools/vpic/build_subset.py): gzip, named .dat so the build stores it as it is. */
        internal const val VIN_DATA = "vpic/vin-data.dat"
        internal const val PREF_CAR_MODEL = AppPrefs.CAR_MODEL
        internal const val PREF_CAR_ENGINE = AppPrefs.CAR_ENGINE
        internal const val PREF_CAR_ASKED = "car_asked"
        internal const val PREF_SCORE_STYLE = "score_style"
        internal const val PREF_LAST_TRIP = "last_trip"
        /** Picture-in-picture when leaving the app while connected (off until you turn it on). */
        internal const val PREF_PIP = "pip"
        internal const val PREF_CUSTOM = "custom_readings"
        internal const val PREF_SLOTS = "dash_slots"
        internal const val PREF_PERF_HISTORY = "perf_history"
        internal const val PREF_PERF_MODE = "perf_mode"
        internal const val PREF_LAST_TRIP_SHOWN = "last_trip_shown"
        internal const val PREF_PROTOCOL = AppPrefs.PROTOCOL
        internal const val PREF_LAST_PROTOCOL = AppPrefs.LAST_PROTOCOL
        internal const val PREF_LAST_TYPE = AppPrefs.LAST_TYPE
        internal const val PREF_LAST_ADDRESS = AppPrefs.LAST_ADDRESS
        internal const val PREF_LAST_NAME = AppPrefs.LAST_NAME
        internal const val TYPE_CLASSIC = AppPrefs.TYPE_CLASSIC
        internal const val TYPE_BLE = AppPrefs.TYPE_BLE
        internal const val TYPE_WIFI = AppPrefs.TYPE_WIFI
    }
}
