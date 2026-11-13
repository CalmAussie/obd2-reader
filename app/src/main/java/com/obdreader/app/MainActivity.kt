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
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
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
import androidx.core.view.drawToBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.obdreader.app.obd.DtcLibrary
import com.obdreader.app.obd.GForce
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.session.Exports
import com.obdreader.app.session.Updater
import com.obdreader.app.session.LiveHistory
import com.obdreader.app.session.LogRecorder
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.GraphStripView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
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
import com.obdreader.app.obd.CarSupport
import com.obdreader.app.obd.DemoTransport
import com.obdreader.app.obd.DtcDescriptions
import com.obdreader.app.obd.ElmClient
import com.obdreader.app.obd.ObdTransport
import com.obdreader.app.obd.Pid
import com.obdreader.app.obd.Boost
import com.obdreader.app.obd.Pids
import com.obdreader.app.obd.ObdProtocols
import com.obdreader.app.obd.VehicleCatalog
import com.obdreader.app.obd.TripSummary
import com.obdreader.app.obd.Units
import com.obdreader.app.obd.CustomReading
import com.obdreader.app.obd.EnhancedReadings
import com.obdreader.app.obd.EvBattery
import com.obdreader.app.obd.ModuleScan
import com.obdreader.app.obd.Readiness
import com.obdreader.app.obd.CarCoverage
import com.obdreader.app.obd.ChargingStatus
import com.obdreader.app.obd.PerformanceTimer
import com.obdreader.app.obd.DriveTrack
import com.obdreader.app.session.OverlayExporter
import com.obdreader.app.obd.VinDecoder
import com.obdreader.app.obd.WifiTransport
import com.google.android.material.card.MaterialCardView
import com.obdreader.app.ui.AppIcons
import com.obdreader.app.ui.AppThemes
import com.obdreader.app.ui.Channels
import com.obdreader.app.ui.CockpitRingView
import com.obdreader.app.ui.CyberChipView
import com.obdreader.app.ui.CyberHudView
import com.obdreader.app.ui.DashLayout
import com.obdreader.app.ui.DriveCardRenderer
import com.obdreader.app.ui.FitTextView
import com.obdreader.app.ui.DecalView
import com.obdreader.app.ui.LiveryStripesView
import com.obdreader.app.ui.MilBannerView
import com.obdreader.app.ui.PlateView
import com.obdreader.app.ui.RoundelView
import com.obdreader.app.ui.ShiftLightsView
import com.obdreader.app.ui.SkewButton
import com.obdreader.app.ui.StartLightsView
import com.obdreader.app.ui.ThemeSwatchView
import com.obdreader.app.ui.TowerRowView
import com.obdreader.app.ui.TraceView
import com.obdreader.app.ui.VinDecalView
import com.obdreader.app.ui.dashLayout
import com.obdreader.app.ui.panelBackground
import com.obdreader.app.ui.themeBool
import com.obdreader.app.ui.SmoothText
import com.obdreader.app.ui.applyTitleFonts
import com.obdreader.app.ui.displayTypeface
import com.obdreader.app.ui.titleTypeface
import com.obdreader.app.session.Alerts
import com.obdreader.app.session.DriveScorer
import com.obdreader.app.obd.DrivingScore
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.themeDimen
import com.obdreader.app.ui.themeShape
import com.obdreader.app.ui.uiTypeface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

open class MainActivity : AppCompatActivity() {

    private enum class Status { IDLE, CONNECTING, CONNECTED, ERROR }

    private lateinit var statusText: TextView
    private lateinit var statusText2: TextView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var pageDashboard: View
    private lateinit var pageDiagnostics: View
    private lateinit var pageGraphs: View
    private lateinit var graphList: LinearLayout
    private lateinit var graphRate: TextView
    private lateinit var recordBtn: SkewButton
    private lateinit var recordStatus: TextView
    private lateinit var shareReportBtn: MaterialButton
    private val graphStrips = mutableListOf<GraphStripView>()
    private var graphWindowMs = 30_000L
    private var graphsPaused = false
    private lateinit var statusDetail: TextView
    private lateinit var connectBtn: SkewButton
    private lateinit var demoBtn: SkewButton
    private lateinit var readDtcBtn: SkewButton
    private lateinit var vinBtn: MaterialButton
    private lateinit var towerList: LinearLayout
    private lateinit var vinText: TextView
    private lateinit var vehicleText: TextView
    private lateinit var protocolText: TextView
    private lateinit var protocolSetting: TextView
    private lateinit var carSetting: TextView
    private lateinit var chooseCarButton: MaterialButton
    private lateinit var dtcStatus: TextView
    private lateinit var dtcSub: TextView
    private lateinit var milBanner: MilBannerView
    private lateinit var dtcContainer: LinearLayout
    private lateinit var logText: TextView
    private lateinit var logToggle: TextView

    // The three dashboards; only the one the chosen style uses is shown.
    private lateinit var dash: DashLayout
    private lateinit var roundel: RoundelView
    private lateinit var startLights: StartLightsView
    private lateinit var shiftLights: ShiftLightsView
    private lateinit var trace: TraceView
    private lateinit var gMeter: com.obdreader.app.ui.GMeterView
    private lateinit var teleRpm: TextView
    private lateinit var teleSpeed: TextView
    private lateinit var teleCoolant: TextView
    private lateinit var teleFuel: TextView
    private lateinit var cockpitRing: CockpitRingView
    private lateinit var cyberHud: CyberHudView
    private lateinit var retroDash: com.obdreader.app.ui.RetroDashView
    private lateinit var rallyRev: com.obdreader.app.ui.RallyRevView
    private lateinit var hairlineDial: com.obdreader.app.ui.HairlineDialView
    private val retroChips = mutableMapOf<String, com.obdreader.app.ui.RetroChipView>()
    private val rallyBlocks = mutableMapOf<String, com.obdreader.app.ui.RallyBlockView>()
    private val hairlineRows = mutableMapOf<String, TowerRowView>()
    private val cyberChips = mutableMapOf<String, CyberChipView>()
    private val decals = mutableMapOf<String, DecalView>()
    private val glass = mutableMapOf<String, SmoothText>()
    // Big text numbers that roll to each reading instead of jumping (the drawn dials do it themselves).
    private lateinit var teleRpmText: SmoothText
    private lateinit var teleSpeedText: SmoothText
    /** The two telemetry readouts under the trace, by reading. */
    private val teleTexts = mutableMapOf<String, SmoothText>()
    private val teleLabels = mutableMapOf<String, String>()
    private val glassUnits = mutableMapOf<String, TextView>()
    private var lastStandIns: Map<String, String> = emptyMap()
    private var lastSupported: Set<Int>? = null
    private var lastRelabels: Map<String, String> = emptyMap()
    private var lastGraphUnsupported: Set<String> = emptySet()
    private var lastFuelStatus: Double? = null
    private var lastGraphConnected = false
    private var lastCleared: com.obdreader.app.obd.ClearedCheck? = null

    /** The line under "Codes recently cleared?" on Diagnostics. */
    private fun clearedHelp(): TextView? = (findViewById<ViewGroup>(R.id.clearedRow)?.getChildAt(1) as? TextView)
    private var lastCharging: ChargingStatus.State? = null
    private val glassLabels = mutableMapOf<String, TextView>()

    /** A reading the dashboard can show in one of its main readouts. */
    private data class Readout(val key: String, val label: String, val unit: String, val decimals: Int, val min: Double, val max: Double, val warn: Double?)

    private fun readout(key: String): Readout =
        (Pids.byHex(key)?.let { Readout(it.hex, it.label, it.unit, it.decimals, it.min, it.max, it.warnAbove) }
            ?: if (key == Boost.KEY) Readout(Boost.KEY, Boost.LABEL, "kPa", 0, Boost.MIN, Boost.MAX, null)
            else if (key == GForce.FORWARD_KEY) Readout(key, getString(R.string.g_forward), "g", 2, -1.0, 1.0, null)
            else if (key == GForce.SIDE_KEY) Readout(key, getString(R.string.g_side), "g", 2, -1.0, 1.0, null)
            else Readout(VOLTAGE_KEY, "Battery", "V", 1, 11.0, 15.0, null)).inUnits()

    /** The readout in the chosen units (°F, mph, psi) where that applies. */
    private fun Readout.inUnits(): Readout = if (!Units.isTemperature(key) && !Units.isSpeed(key) && !Units.isPressure(key)) this else copy(
        unit = Units.unit(key, unit), decimals = Units.decimals(key, decimals),
        min = Units.value(key, min), max = Units.value(key, max), warn = warn?.let { Units.value(key, it) },
    )

    /** Every reading that can go in a main readout (not engine speed or speed, which have their own). */
    private val readoutChoices: List<Readout> by lazy {
        Pids.dashboard.filter { it.hex != RPM_KEY && it.hex != SPEED_KEY }.map { readout(it.hex) } + readout(Boost.KEY) + readout(VOLTAGE_KEY) +
            // G-force from the phone's motion sensor.
            readout(GForce.FORWARD_KEY) + readout(GForce.SIDE_KEY) +
            // More standard readings, for cars that report them (N/A on the rest).
            Pids.extraNumbers.map { readout(it.hex) }
    }

    /** What the main readouts show, chosen under Diagnostics → Dashboard readouts. Coolant, fuel and battery to start with. */
    private val slots: List<String> by lazy {
        val saved = prefs.getString(PREF_SLOTS, null)?.split(",").orEmpty()
        val valid = readoutChoices.map { it.key }.toSet()
        listOf(COOLANT_KEY, FUEL_KEY, VOLTAGE_KEY).mapIndexed { i, def -> saved.getOrNull(i)?.takeIf { it in valid } ?: def }
    }
    private var lastRpm: Float? = null
    private var lastSpeed: Float? = null
    private var redline = 6500f

    private val tiles = mutableMapOf<String, TowerRowView>()
    private var lastPhase: ObdSession.Phase? = null
    private var lastReadings: Map<String, Double?> = emptyMap()
    private var lastUnsupported: Set<String> = emptySet()
    private var lastVinChecked = false
    private var lastCodes: ObdSession.Codes? = null


    private val prefs by lazy { getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE) }

    /** The style this screen is drawn in: the chosen one, or the day or night one with switching on. */
    private var shownStyle: String? = null
    /** When the screen was last touched: a style change waits while someone's using the screen. */
    private var lastTouchMs = 0L
    private var styleWatch: kotlinx.coroutines.Job? = null
    private var lightListener: android.hardware.SensorEventListener? = null

    /** Back up: where the user chose to save the file. */
    private var backupRoutes = false
    private var backupVin = false
    private val backupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(com.obdreader.app.obd.BackupFile.MIME)) { uri ->
            if (uri != null) writeBackup(uri)
        }
    /** Restore: the backup file the user picked. */
    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readBackup(uri)
        }

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

    /** Location for a recorded drive's route: asked when recording starts, never otherwise. */
    private val routePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (!fine) toast("Recording without the route")
            beginRecording(withLocation = fine)
        }

    /** Asked once, so the connection notification (with its Disconnect button) can show. */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Android 9 and older: the user picks where a saved file goes. */
    private var pendingSave: java.io.File? = null
    private val saveDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            val file = pendingSave; pendingSave = null
            if (uri == null || file == null) return@registerForActivityResult
            val ok = try { contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null } catch (e: Exception) { false }
            toast(if (ok) getString(R.string.file_saved_picked) else getString(R.string.file_save_failed))
        }

    /** Record and replay: a recording file the user picked. It's checked line by line before it's replayed. */
    private val openRecordingLauncher =
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
    private fun offerFile(file: java.io.File, subject: String) {
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

    private val enableBluetoothLauncher =
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
        val pages = listOf(findViewById<View>(R.id.content), findViewById<View>(R.id.contentDiag), findViewById<View>(R.id.contentGraphs))
        val basePadTop = pages.first().paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            pages.forEach { it.setPadding(it.paddingLeft, basePadTop + bars.top, it.paddingRight, it.paddingBottom) }
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
        }
        findViewById<MaterialSwitch>(R.id.pipSwitch).apply {
            isChecked = prefs.getBoolean(PREF_PIP, false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(PREF_PIP, on).apply(); updatePipParams() }
        }
        findViewById<View>(R.id.pipRow).apply {
            background = panelBackground()
            visibility = if (pipSupported()) View.VISIBLE else View.GONE
            setOnClickListener { findViewById<MaterialSwitch>(R.id.pipSwitch).toggle() }
        }
        findViewById<MaterialSwitch>(R.id.alertsSwitch).apply {
            isChecked = AppPrefs.alerts(prefs)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(AppPrefs.ALERTS, on).apply() }
        }
        findViewById<View>(R.id.alertsRow).apply {
            background = panelBackground()
            setOnClickListener { findViewById<MaterialSwitch>(R.id.alertsSwitch).toggle() }
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

    private lateinit var scoreCard: LinearLayout
    private var scoreStyle = DrivingScore.Style.EVERYDAY

    private fun setupScore() {
        scoreCard = findViewById(R.id.scoreCard)
        scoreCard.background = panelBackground()
        DriveScorer.start(this)
        showBatteryCheck()
        scoreStyle = DrivingScore.Style.values().firstOrNull { it.name == prefs.getString(PREF_SCORE_STYLE, null) } ?: DrivingScore.Style.EVERYDAY
        val ids = mapOf(DrivingScore.Style.EFFICIENCY to R.id.styleEfficiency, DrivingScore.Style.EVERYDAY to R.id.styleEveryday, DrivingScore.Style.RACING to R.id.styleRacing)
        // Narrow padding and one line each, so "Efficiency" isn't cut short on small phones or with large text.
        ids.values.forEach { id ->
            findViewById<MaterialButton>(id).apply {
                maxLines = 1
                setPaddingRelative(dp(4f).toInt(), paddingTop, dp(4f).toInt(), paddingBottom)
            }
        }
        findViewById<MaterialButtonToggleGroup>(R.id.scoreStyle).apply {
            ids.values.forEach { id ->
                findViewById<MaterialButton>(id).apply {
                    setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(themeColor(R.attr.obdOnAccent), themeColor(R.attr.obdTextPrimary))))
                    backgroundTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(themeColor(R.attr.obdAccent), android.graphics.Color.TRANSPARENT))
                    strokeColor = ColorStateList.valueOf(themeColor(R.attr.obdOutline))
                }
            }
            check(ids.getValue(scoreStyle))
            // Under the score card, three buttons across: Performance timers, Last drive summary
            // and Drive history. The last two show once there's been a drive; the row shares its
            // width between the ones showing.
            val content = scoreCard.parent as ViewGroup
            // The same buttons as Connect, so they take each style's own shape: angled on the
            // liveries, square tiles on telemetry, pills on the cockpit, cut corners on cyberpunk.
            fun quickButton(label: Int, onClick: () -> Unit) = SkewButton(this@MainActivity).apply {
                text = getString(label)
                kind = SkewButton.Kind.OUTLINE
                setTextSizeSp(13f)
                setOnClickListener { onClick() }
            }
            val perfButton = quickButton(R.string.perf_title) { showPerformance() }.apply { id = R.id.perfButton }
            val lastButton = quickButton(R.string.last_trip) { savedTrip()?.let { showTripSummary(it, justEnded = false) } }.apply {
                visibility = if (savedTrip() != null) View.VISIBLE else View.GONE
            }
            val histButton = quickButton(R.string.history_title) { showHistory() }.apply {
                id = R.id.historyButton
                visibility = if (tripHistory().isNotEmpty()) View.VISIBLE else View.GONE
            }
            lastTripButton = lastButton
            historyButton = histButton
            val quickRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                listOf(perfButton, lastButton, histButton).forEachIndexed { i, btn ->
                    addView(btn, LinearLayout.LayoutParams(0, dp(52f).toInt(), 1f).apply { if (i > 0) marginStart = dp(8f).toInt() })
                }
            }
            content.addView(quickRow, content.indexOfChild(scoreCard) + 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10f).toInt() })
            addOnButtonCheckedListener { _, id, checked ->
                if (!checked) return@addOnButtonCheckedListener
                scoreStyle = ids.entries.first { it.value == id }.key
                prefs.edit().putString(PREF_SCORE_STYLE, scoreStyle.name).apply()
                renderScore()
            }
        }
        renderScore()
    }

    /** The chosen style's score: the number, what it means, each part with a bar, and the tip that would help most. */
    private fun renderScore() {
        if (!::scoreCard.isInitialized) return
        val r = DriveScorer.results.value[scoreStyle]
        val connected = ObdSession.state.value.isConnected || ObdSession.state.value.phase == ObdSession.Phase.WAITING
        scoreCard.removeAllViews()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        head.addView(TextView(this).apply {
            text = r?.score?.toString() ?: getString(R.string.dash)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            typeface = displayTypeface()
            includeFontPadding = false
            setTextColor(r?.score?.let { scoreColour(it) } ?: themeColor(R.attr.obdTextPrimary))
        })
        head.addView(TextView(this).apply {
            text = "/100"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(dp(6f).toInt(), 0, 0, dp(6f).toInt())
        })
        scoreCard.addView(head)
        scoreCard.addView(TextView(this).apply {
            text = when {
                r == null || (r.drivingSeconds == 0 && !connected) -> getString(R.string.score_not_connected)
                r.score == null -> getString(R.string.score_waiting)
                else -> scoreStyle.blurb
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(4f).toInt(), 0, dp(6f).toInt())
        })
        val parts = r?.parts ?: DrivingScore().result(scoreStyle).parts
        for (p in parts) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8f).toInt(), 0, dp(3f).toInt()) }
            row.addView(TextView(this).apply {
                text = p.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(themeColor(R.attr.obdTextPrimary))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = if (r?.score == null || p.score == null) getString(R.string.dash) else p.score.toString()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = uiTypeface(600)
                fontFeatureSettings = "tnum"
                setTextColor(themeColor(R.attr.obdTextPrimary))
            })
            scoreCard.addView(row)
            scoreCard.addView(com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
                max = 100
                progress = if (r?.score == null) 0 else p.score ?: 0
                trackCornerRadius = dp(2f).toInt()
                trackThickness = dp(4f).toInt()
                setIndicatorColor(p.score?.let { scoreColour(it) } ?: themeColor(R.attr.obdAccent))
                trackColor = themeColor(R.attr.obdTrack)
            })
        }
        val weakest = if (r?.score == null) null else r.parts.filter { it.score != null }.minByOrNull { it.score!! }?.takeIf { it.score!! < 90 }
        val extra = listOfNotNull(
            weakest?.let { "Tip: ${it.tip}" },
            r?.topSpeed?.let { "Top speed this drive: ${Units.speedText(it)}" },
            r?.best0to100?.takeIf { scoreStyle == DrivingScore.Style.RACING }?.let { String.format(Locale.US, "Best 0 to 100 km/h this drive: %.1f s", it) },
            if (scoreStyle == DrivingScore.Style.RACING) getString(R.string.score_racing_note) else null,
        )
        for (line in extra) {
            scoreCard.addView(TextView(this).apply {
                text = line
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(themeColor(R.attr.obdTextSecondary))
                setLineSpacing(0f, 1.15f)
                setPadding(0, dp(12f).toInt(), 0, 0)
            })
        }
    }

    private var lastTripButton: SkewButton? = null

    private fun savedTrip(): TripSummary? = TripSummary.decode(prefs.getString(PREF_LAST_TRIP, null))

    private var historyButton: SkewButton? = null

    /** Every drive kept on the phone, newest first. Before drive history, only the last drive was kept. */
    private fun tripHistory(): List<TripSummary> {
        val stored = prefs.getString(DriveScorer.PREF_TRIP_HISTORY, null)
        if (stored == null) savedTrip()?.let { last ->
            prefs.edit().putString(DriveScorer.PREF_TRIP_HISTORY, com.obdreader.app.obd.TripHistory.add(null, last)).apply()
        }
        return com.obdreader.app.obd.TripHistory.parse(prefs.getString(DriveScorer.PREF_TRIP_HISTORY, null))
    }

    /** Drive history: the drive score's trend, then every drive; tap one for its summary and card. */
    private fun showHistory() {
        val trips = tripHistory()
        val sheet = BottomSheetDialog(this)
        val pad = dp(20f).toInt()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
        body.addView(TextView(this).apply {
            setText(R.string.history_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary))
        })
        val (km, secs) = com.obdreader.app.obd.TripHistory.totals(trips)
        body.addView(TextView(this).apply {
            text = "${trips.size} ${if (trips.size == 1) "drive" else "drives"} · ${TripSummary.distance(km)} · ${TripSummary.duration(secs)} driving"
            setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(4f).toInt(), 0, dp(12f).toInt())
        })
        val trend = com.obdreader.app.obd.TripHistory.scoreTrend(trips)
        if (trend.size >= 2) {
            body.addView(TextView(this).apply {
                text = getString(R.string.history_trend, trend.size)
                setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
            body.addView(com.obdreader.app.ui.TrendView(this, trend, themeColor(R.attr.obdAccent), themeColor(R.attr.obdOutline), themeColor(R.attr.obdTextSecondary)).apply {
                contentDescription = "Drive score over your last ${trend.size} drives: from ${trend.first()} to ${trend.last()}"
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96f).toInt()).apply { bottomMargin = dp(12f).toInt() })
        }
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        val day = java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault())
        trips.forEach { trip ->
            val score = com.obdreader.app.obd.ShareScore.of(trip.scores, raceTelemetry = false).score
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = panelBackground()
                setPadding(dp(16f).toInt(), dp(12f).toInt(), dp(16f).toInt(), dp(12f).toInt())
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@MainActivity).apply {
                        text = "${day.format(java.util.Date(trip.startedAt))}, ${time.format(java.util.Date(trip.startedAt))}"
                        setTextColor(themeColor(R.attr.obdTextPrimary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = "${TripSummary.distance(trip.distanceKm)} · ${TripSummary.duration(trip.drivingSeconds)}" + (trip.topSpeed?.let { " · top ${Units.speedText(it)}" } ?: "")
                        setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@MainActivity).apply {
                    text = score?.toString() ?: "–"
                    typeface = titleTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    setTextColor(themeColor(R.attr.obdAccent))
                    contentDescription = score?.let { "Drive score $it" } ?: "No score"
                })
                setOnClickListener { sheet.dismiss(); showTripSummary(trip, justEnded = false) }
                setOnLongClickListener {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.history_delete_title)
                        .setMessage(R.string.history_delete_text)
                        .setPositiveButton(R.string.history_delete) { _, _ ->
                            prefs.edit().putString(DriveScorer.PREF_TRIP_HISTORY, com.obdreader.app.obd.TripHistory.remove(prefs.getString(DriveScorer.PREF_TRIP_HISTORY, null), trip.startedAt)).apply()
                            sheet.dismiss(); if (tripHistory().isNotEmpty()) showHistory() else historyButton?.visibility = View.GONE
                        }
                        .setNegativeButton(android.R.string.cancel, null).show()
                    true
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8f).toInt() })
        }
        body.addView(TextView(this).apply {
            setText(R.string.history_hint)
            setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(8f).toInt(), 0, 0)
        })
        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
        sheet.show()
    }

    /** The trip summary: what the drive added up to, as a sheet of tiles. */
    /** The drive's g-force on its summary: the hardest braking, speeding up and cornering, and any harsh moments. */
    /** Shrinks one line of text from [maxSp] towards [minSp] until it fits its width, e.g. "1.21 g" on a 360dp phone. */
    private fun TextView.shrinkToFit(maxSp: Float, minSp: Float) {
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

    private fun addTripMotion(body: LinearLayout, m: TripSummary.Motion) {
        body.addView(TextView(this).apply {
            setText(R.string.g_trip_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = uiTypeface(600)
            setTextColor(themeColor(R.attr.obdTextPrimary))
            setPadding(0, dp(14f).toInt(), 0, dp(6f).toInt())
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = tripTile(); setPadding(0, dp(12f).toInt(), 0, dp(12f).toInt()) }
        listOf(R.string.g_trip_brake to m.peakBrake, R.string.g_trip_accel to m.peakAccel, R.string.g_trip_side to m.peakSide).forEach { (label, g) ->
            // Each line fills its cell's width, so the text itself is centred, not just the line.
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(TextView(context).apply {
                    text = TripSummary.Motion.g(g); gravity = Gravity.CENTER_HORIZONTAL; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f); typeface = displayTypeface(); fontFeatureSettings = "tnum"
                    shrinkToFit(24f, 18f)
                    setTextColor(themeColor(R.attr.obdTextPrimary))
                })
                addView(TextView(context).apply { setText(label); gravity = Gravity.CENTER_HORIZONTAL; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        body.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(TextView(this).apply {
            text = m.harshText() + ". " + getString(R.string.g_trip_note)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(6f).toInt(), 0, 0)
        })
    }

    private fun showTripSummary(trip: TripSummary, justEnded: Boolean) {
        val sheet = BottomSheetDialog(this)
        val pad = dp(20f).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt())
        }
        body.addView(TextView(this).apply {
            text = getString(if (justEnded) R.string.trip_title_ended else R.string.trip_title_last)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = titleTypeface()
            setTextColor(themeColor(R.attr.obdTextPrimary))
        })
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        val day = java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault())
        body.addView(TextView(this).apply {
            text = listOfNotNull(
                "${day.format(java.util.Date(trip.startedAt))}, ${time.format(java.util.Date(trip.startedAt))} to ${time.format(java.util.Date(trip.endedAt))}",
                savedCar()?.label,
            ).joinToString(" · ")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(2f).toInt(), 0, dp(14f).toInt())
        })
        val tiles = listOfNotNull(
            "Distance" to TripSummary.distance(Units.distance(trip.distanceKm)).replace(" km", " ${Units.distanceUnit}"),
            "Driving time" to TripSummary.duration(trip.drivingSeconds),
            trip.topSpeed?.let { "Top speed" to Units.speedText(it) },
            trip.averageSpeed?.let { "Average speed" to Units.speedText(it) },
            "Idling" to TripSummary.duration(trip.idleSeconds),
            trip.best0to100?.let { "Best 0–100 km/h" to String.format(Locale.US, "%.1f s", it) },
        )
        tiles.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, (label, value) ->
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = tripTile()
                    setPadding(dp(14f).toInt(), dp(12f).toInt(), dp(14f).toInt(), dp(12f).toInt())
                    addView(TextView(context).apply { text = label; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
                    addView(TextView(context).apply {
                        text = value; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                        typeface = displayTypeface(); fontFeatureSettings = "tnum"; setTextColor(themeColor(R.attr.obdTextPrimary))
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(8f).toInt() })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(8f).toInt() })
            body.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8f).toInt() })
        }
        // The driving score in each style.
        body.addView(TextView(this).apply {
            setText(R.string.driving_score)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = uiTypeface(600)
            setTextColor(themeColor(R.attr.obdTextPrimary))
            setPadding(0, dp(10f).toInt(), 0, dp(6f).toInt())
        })
        val scores = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = tripTile(); setPadding(0, dp(12f).toInt(), 0, dp(12f).toInt()) }
        DrivingScore.Style.values().forEach { style ->
            val s = trip.scores[style]
            scores.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(TextView(context).apply {
                    text = s?.toString() ?: getString(R.string.dash); gravity = Gravity.CENTER_HORIZONTAL; setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f); typeface = displayTypeface(); fontFeatureSettings = "tnum"
                    setTextColor(s?.let { scoreColour(it) } ?: themeColor(R.attr.obdTextSecondary))
                })
                addView(TextView(context).apply { text = style.label; gravity = Gravity.CENTER_HORIZONTAL; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        body.addView(scores, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (trip.scores.values.all { it == null }) body.addView(TextView(this).apply {
            setText(R.string.trip_too_short)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(6f).toInt(), 0, 0)
        })
        trip.motion?.let { addTripMotion(body, it) }
        addRouteComparison(body, trip)
        body.addView(MaterialButton(this).apply {
            setText(R.string.done)
            setOnClickListener { sheet.dismiss() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f).toInt() })
        body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(R.string.share_picture)
            setOnClickListener { shareDriveCard(trip) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f).toInt() })
        body.applyTitleFonts()
        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.show()
    }

    // ---------- Units ----------

    /** Speed in km/h or mph, and temperature in °C or °F, chosen separately. */
    private fun showUnitsPicker() {
        var mph = Units.mph
        var fahrenheit = Units.fahrenheit
        var pressure = Units.pressure
        choiceSheet(getString(R.string.units)) { body, sheet ->
            fun group(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
                sheetHeading(body, title)
                val radios = android.widget.RadioGroup(this)
                options.forEachIndexed { i, name ->
                    radios.addView(com.google.android.material.radiobutton.MaterialRadioButton(this).apply {
                        id = View.generateViewId(); text = name; isChecked = i == selected
                        setOnCheckedChangeListener { _, on -> if (on) onPick(i) }
                    })
                }
                body.addView(radios)
            }
            group("Speed and distance", listOf("Kilometres (km/h, km)", "Miles (mph, mi)"), if (mph) 1 else 0) { mph = it == 1 }
            group("Temperature", listOf("Celsius (°C)", "Fahrenheit (°F)"), if (fahrenheit) 1 else 0) { fahrenheit = it == 1 }
            group("Pressure (boost, manifold)", listOf("Kilopascals (kPa)", "Pounds per square inch (psi)", "Bar"), pressure.ordinal) { pressure = Units.Pressure.values()[it] }
            sheetNote(body, "Recorded drives (CSV) and the adapter log stay in the car's own metric units.")
            sheetButtons(body, "Save" to {
                val changed = mph != Units.mph || fahrenheit != Units.fahrenheit || pressure != Units.pressure
                // Saved even when unchanged, so the choice stays put if the phone's country changes.
                prefs.edit().putBoolean(AppPrefs.UNITS_MPH, mph).putBoolean(AppPrefs.UNITS_FAHRENHEIT, fahrenheit)
                    .putString(AppPrefs.UNITS_PRESSURE, pressure.name).apply()
                sheet.dismiss()
                if (changed) {
                    AppPrefs.applyUnits(prefs)
                    recreate() // rebuilds every readout in the new units
                }
            })
        }
    }

    // ---- Swipe-down sheets for settings ----
    // Every settings picker is a bottom sheet. A picker opened from another opens on top of it, so
    // one swipe down (or Back) closes only the top one and lands on the sheet underneath.

    /** A settings sheet: [title], then [build] fills it. Swipe down or Back closes it without saving. */
    private fun choiceSheet(title: String, build: (body: LinearLayout, sheet: BottomSheetDialog) -> Unit): BottomSheetDialog {
        val sheet = BottomSheetDialog(this)
        val pad = dp(20f).toInt()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
        body.addView(TextView(this).apply {
            text = title; setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary))
            setPadding(0, 0, 0, dp(4f).toInt())
        })
        build(body, sheet)
        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.show()
        return sheet
    }

    private fun sheetHeading(body: LinearLayout, text: String) = body.addView(TextView(this).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdTextPrimary))
        setPadding(0, dp(14f).toInt(), 0, dp(2f).toInt())
    })

    private fun sheetNote(body: LinearLayout, text: String) = body.addView(TextView(this).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.15f); setTextColor(themeColor(R.attr.obdTextSecondary))
        setPadding(0, dp(10f).toInt(), 0, 0)
    })

    /** An on/off setting in a sheet: 15sp in the primary text colour, at least 48dp tall to tap. */
    private fun sheetSwitch(body: LinearLayout, label: String, checked: Boolean, onChange: (Boolean) -> Unit) = body.addView(MaterialSwitch(this).apply {
        text = label; isChecked = checked
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextPrimary))
        minHeight = dp(48f).toInt()
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f).toInt() })

    /**
     * Full-width buttons stacked down the sheet, as on the trip summary: the first filled, then
     * outlined, and a [danger] one (Delete) last, borderless in the danger colour.
     */
    private fun sheetStackedButtons(body: LinearLayout, buttons: List<Pair<String, () -> Unit>>, danger: Pair<String, () -> Unit>? = null) {
        val all = buttons + listOfNotNull(danger)
        all.forEachIndexed { i, (label, action) ->
            val isDanger = danger != null && i == all.lastIndex
            val style = when {
                isDanger -> com.google.android.material.R.attr.borderlessButtonStyle
                i == 0 -> com.google.android.material.R.attr.materialButtonStyle
                else -> com.google.android.material.R.attr.materialButtonOutlinedStyle
            }
            body.addView(MaterialButton(this, null, style).apply {
                text = label
                if (isDanger) setTextColor(themeColor(R.attr.obdDanger))
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(if (i == 0) 18f else 4f).toInt() })
        }
    }

    /** A tappable row: [name] with its current [value] and a chevron, opening the next sheet. */
    private fun sheetRow(body: LinearLayout, name: String, value: String, onClick: () -> Unit): TextView {
        val valueView = TextView(this).apply {
            text = value; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdAccent))
            gravity = Gravity.END
        }
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = panelBackground(); val p = dp(14f).toInt(); setPadding(p, p, dp(8f).toInt(), p)
            isClickable = true; isFocusable = true; contentDescription = "$name: $value"
            addView(TextView(this@MainActivity).apply {
                text = name; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextPrimary))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(valueView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(android.widget.ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_chevron_right); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f).toInt() })
        return valueView
    }

    /** A single-choice list: tapping an option calls [onPick] with its index. */
    private fun sheetOptions(body: LinearLayout, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val radios = android.widget.RadioGroup(this)
        options.forEachIndexed { i, name ->
            radios.addView(com.google.android.material.radiobutton.MaterialRadioButton(this).apply {
                id = View.generateViewId(); text = name; isChecked = i == selected
                minHeight = dp(48f).toInt()
                setOnClickListener { onPick(i) }
            })
        }
        body.addView(radios)
    }

    /** Buttons along the bottom, right-aligned, the last one filled. */
    private fun sheetButtons(body: LinearLayout, vararg buttons: Pair<String, () -> Unit>) {
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
            setPadding(0, dp(18f).toInt(), 0, 0)
            buttons.forEachIndexed { i, (label, action) ->
                val last = i == buttons.lastIndex
                addView(MaterialButton(this@MainActivity, null, if (last) com.google.android.material.R.attr.materialButtonStyle else com.google.android.material.R.attr.borderlessButtonStyle).apply {
                    text = label
                    if (!last) setTextColor(themeColor(R.attr.obdAccent))
                    setOnClickListener { action() }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8f).toInt() })
            }
        })
    }

    /**
     * A picker for a few numbered places (Dashboard readouts, widget readings, Android Auto tiles):
     * the first sheet lists each place and what's in it; tapping one opens a second sheet on top
     * with the choices. Picking closes only that second sheet; Save or Reset closes the first.
     * Choosing something already in another place swaps the two.
     */
    private fun <K> placesSheet(
        title: String, note: String?, places: List<String>, chosen: MutableList<K>,
        choicesFor: (Int) -> List<Pair<K, String>>, nameOf: (K) -> String,
        save: (List<K>) -> Unit, reset: () -> Unit,
    ) {
        choiceSheet(title) { body, sheet ->
            if (note != null) sheetNote(body, note)
            val values = mutableListOf<TextView>()
            places.forEachIndexed { slot, place ->
                values += sheetRow(body, place, nameOf(chosen[slot])) {
                    val choices = choicesFor(slot)
                    choiceSheet(place) { inner, innerSheet ->
                        sheetOptions(inner, choices.map { it.second }, choices.indexOfFirst { it.first == chosen[slot] }) { i ->
                            val pick = choices[i].first
                            val other = chosen.indexOf(pick)
                            if (pick != null && other >= 0 && other != slot) chosen[other] = chosen[slot] // swap rather than show one twice
                            chosen[slot] = pick
                            places.indices.forEach { values[it].text = nameOf(chosen[it]) }
                            innerSheet.dismiss()
                        }
                    }
                }
            }
            sheetButtons(body, "Reset" to { reset(); sheet.dismiss() }, "Save" to { save(chosen.toList()); sheet.dismiss() })
        }
    }

    // ---------- Car scan ----------

    // ---------- Diagnostics groups ----------

    /**
     * Diagnostics groups its settings and checks into sections that open and close, so the tab
     * isn't one long list. Each remembers whether it was left open. The rows themselves are the
     * same as ever; they're only moved under their section's header.
     */
    private fun setupGroups() {
        val groups = listOf(
            Triple("car", "Car and connection", listOf(R.id.carRow, R.id.vinPrivacyRow, R.id.marketRow, R.id.protocolRow, R.id.reconnectRow, R.id.repliesRow, R.id.logCard)),
            Triple("checks", "Deeper checks", listOf(R.id.scanRow, R.id.clearedRow, R.id.historyRow, R.id.readinessRow, R.id.testsRow, R.id.modulesRow, R.id.enhancedRow, R.id.evRow, R.id.customRow, R.id.alertsRow)),
            Triple("display", "Display and readings", listOf(R.id.unitsRow, R.id.slotsRow, R.id.widgetRow, R.id.autoRow, R.id.pipRow, R.id.appIconRow)),
            Triple("backup", "Back up", listOf(R.id.backupRow)),
            Triple("help", "Help and feedback", listOf(R.id.helpRow, R.id.reportRow, R.id.ideaRow, R.id.updatesRow)),
        )
        val parent = findViewById<View>(R.id.carRow).parent as ViewGroup
        // The old "Connection" heading sat above the first of these rows: the sections replace it.
        (0 until parent.childCount).map { parent.getChildAt(it) }
            .firstOrNull { it is TextView && it.text == getString(R.string.connection) }?.visibility = View.GONE
        // Everything moves under the sections, in this order, where the first row was. Every row is
        // looked up first: once a row is taken off the screen, findViewById can't find it any more.
        var at = parent.indexOfChild(findViewById<View>(R.id.carRow))
        val found = groups.flatMap { it.third }.associateWith { findViewById<View>(it) }
        found.values.filterNotNull().forEach { parent.removeView(it) }
        for ((key, title, ids) in groups) {
            // Each section's rows are in alphabetical order, by their names, except Your car, which
            // stays first in its section (choosing the car is the first thing to do).
            val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
            val rows = ids.mapNotNull { found[it] }
                .sortedWith(compareBy<View> { it.id != R.id.carRow }.then(compareBy(collator) { rowTitle(it).orEmpty() }))
            val names = rows.mapNotNull { rowTitle(it) }
            // Closed to start with, except Car and connection until a car is chosen (that's the first thing to set).
            val open = prefs.getBoolean("group_$key", key == "car" && AppPrefs.savedCar(prefs) == null)
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; visibility = if (open) View.VISIBLE else View.GONE
                rows.forEach { r -> addView(r, (r.layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)).apply { topMargin = dp(6f).toInt() }) }
            }
            val chevron = android.widget.ImageView(this).apply {
                setImageResource(R.drawable.ic_chevron_right); rotation = if (open) 270f else 90f
                imageTintList = ColorStateList.valueOf(themeColor(R.attr.obdAccent)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                isClickable = true; isFocusable = true
                background = android.util.TypedValue().let { tv -> theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true); androidx.core.content.ContextCompat.getDrawable(this@MainActivity, tv.resourceId) }
                setPadding(0, dp(14f).toInt(), 0, dp(10f).toInt())
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@MainActivity).apply {
                        text = title; typeface = titleTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); setTextColor(themeColor(R.attr.obdTextPrimary))
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = names.joinToString(" · "); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary))
                        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(2f).toInt(), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(chevron, LinearLayout.LayoutParams(dp(28f).toInt(), dp(28f).toInt()))
                fun describe(isOpen: Boolean) { contentDescription = "$title, " + if (isOpen) "expanded" else "collapsed" }
                describe(open)
                setOnClickListener {
                    val nowOpen = box.visibility != View.VISIBLE
                    box.visibility = if (nowOpen) View.VISIBLE else View.GONE
                    chevron.animate().rotation(if (nowOpen) 270f else 90f).setDuration(180).start()
                    describe(nowOpen)
                    prefs.edit().putBoolean("group_$key", nowOpen).apply()
                }
            }
            val lp = { top: Int -> LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top.toFloat()).toInt() } }
            parent.addView(header, at++, lp(if (key == "car") 20 else 10))
            parent.addView(box, at++, lp(0))
            parent.addView(View(this).apply { setBackgroundColor(themeColor(R.attr.obdTextSecondary)); alpha = 0.2f }, at++, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { topMargin = dp(12f).toInt() })
        }
    }

    /** The first line of a row (its name), for its section's summary. */
    private fun rowTitle(row: View): String? {
        fun first(v: View): String? = when (v) {
            is TextView -> v.text?.toString()?.takeIf { it.isNotBlank() }
            is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { first(v.getChildAt(it)) }
            else -> null
        }
        return first(row)
    }

    // ---------- Deeper checks: readiness, self-tests, other modules, custom readings (all read-only) ----------

    /** Adds the deeper-check rows to Diagnostics, under Scan what your car reports. */
    private fun setupDeeperChecks() {
        val scanRow = findViewById<View>(R.id.scanRow)
        val parent = scanRow.parent as ViewGroup
        var at = parent.indexOfChild(scanRow) + 1
        fun row(id: Int, title: String, help: String, onClick: () -> Unit) {
            val r = LinearLayout(this).apply {
                this.id = id
                orientation = LinearLayout.VERTICAL
                background = panelBackground()
                isClickable = true; isFocusable = true
                val p = dp(16f).toInt(); setPadding(p, p, p, p)
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(this@MainActivity).apply {
                        text = title; typeface = uiTypeface(500); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        setTextColor(themeColor(R.attr.obdTextPrimary))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(android.widget.ImageView(this@MainActivity).apply {
                        setImageResource(R.drawable.ic_chevron_right); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        imageTintList = ColorStateList.valueOf(themeColor(R.attr.obdTextSecondary))
                    }, LinearLayout.LayoutParams(dp(20f).toInt(), dp(20f).toInt()))
                })
                addView(TextView(this@MainActivity).apply {
                    text = help; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary))
                    setPadding(0, dp(6f).toInt(), 0, 0)
                })
                setOnClickListener { onClick() }
            }
            parent.addView(r, at++, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f).toInt() })
        }
        row(R.id.clearedRow, getString(R.string.cleared_title), getString(R.string.cleared_help)) { runCheck(getString(R.string.cleared_title)) { showClearedCheck() } }
        row(R.id.readinessRow, getString(R.string.readiness_title), getString(R.string.readiness_help)) { runCheck(getString(R.string.readiness_title)) { showReadiness() } }
        row(R.id.testsRow, getString(R.string.tests_title), getString(R.string.tests_help)) { runCheck(getString(R.string.tests_title)) { showTestResults() } }
        row(R.id.modulesRow, getString(R.string.modules_title), getString(R.string.modules_help)) { runCheck(getString(R.string.modules_title), "Asking each module in turn. This can take half a minute.") { showModules() } }
        row(R.id.enhancedRow, getString(R.string.enhanced_title), getString(R.string.enhanced_help)) { runCheck(getString(R.string.enhanced_title)) { showEnhanced() } }
        row(R.id.evRow, getString(R.string.ev_title), getString(R.string.ev_help)) { runCheck(getString(R.string.ev_title)) { showEvBattery() } }
        row(R.id.customRow, getString(R.string.custom_title), getString(R.string.custom_help)) { showCustomReadings() }
        row(R.id.historyRow, getString(R.string.history_codes_title), getString(R.string.history_codes_help)) { showCodeHistory() }
        row(R.id.vinPrivacyRow, getString(R.string.vin_privacy_title), vinMode().label) { showVinPrivacy() }
        row(R.id.backupRow, getString(R.string.backup_title), getString(R.string.backup_help)) { showBackup() }
        row(R.id.repliesRow, getString(R.string.replies_title), repliesHelp()) { showReplies() }
    }

    private fun repliesHelp() = getString(if (AppPrefs.recordReplies(prefs)) R.string.replies_help_on else R.string.replies_help_off)

    /**
     * Record and replay: a switch to save each connection's requests and replies (reads only, the
     * VIN left out unless Hide VIN shows it), the saved recordings, and a button to open a file.
     */
    private fun showReplies() {
        val sheet = choiceSheet(getString(R.string.replies_title)) { body, sheet ->
            sheetNote(body, getString(R.string.replies_note))
            sheetSwitch(body, getString(R.string.replies_switch), AppPrefs.recordReplies(prefs)) { on ->
                prefs.edit().putBoolean(AppPrefs.RECORD_REPLIES, on).apply()
                findViewById<View>(R.id.repliesRow)?.let { r -> rowHelp(r)?.text = repliesHelp() }
                if (on && ObdSession.state.value.isConnected && !ObdSession.state.value.isDemo) toast(getString(R.string.replies_next_time))
            }
            sheetHeading(body, getString(R.string.replies_saved))
            val files = com.obdreader.app.session.ReplyRecordings.saved(this)
            if (files.isEmpty()) sheetNote(body, getString(R.string.replies_none))
            for (file in files) {
                sheetRow(body, com.obdreader.app.session.ReplyRecordings.describe(file), com.obdreader.app.session.ReplyRecordings.size(file)) {
                    recordingActions(file, sheet)
                }
            }
            sheetButtons(body, getString(R.string.replies_open) to {
                try { openRecordingLauncher.launch(arrayOf("text/plain", "application/octet-stream")) }
                catch (e: android.content.ActivityNotFoundException) { toast(getString(R.string.replies_open_failed)) }
            })
        }
        currentSheet = sheet
    }

    /** One saved recording: replay it, share or save it, or delete it (after asking). */
    private fun recordingActions(file: java.io.File, parent: BottomSheetDialog) {
        val rec = try { com.obdreader.app.session.ReplyRecordings.load(file) } catch (e: Exception) { null }
        choiceSheet(getString(R.string.replies_recording)) { body, sheet ->
            body.addView(TextView(this).apply {
                text = com.obdreader.app.session.ReplyRecordings.describe(file) + " · " + com.obdreader.app.session.ReplyRecordings.size(file)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextPrimary))
            })
            if (rec == null) sheetNote(body, getString(R.string.replies_unreadable))
            rec?.let { r ->
                val made = listOfNotNull(r.app?.let { "Made by OBD2 Reader $it" }, com.obdreader.app.session.ReplyRecordings.madeText(r.made)?.let { "on $it" }).joinToString(" ")
                sheetNote(body, listOf(made, String.format(Locale.getDefault(), "%,d requests", r.exchanges.size)).filter { it.isNotEmpty() }.joinToString(". ") + ".")
            }
            val buttons = mutableListOf<Pair<String, () -> Unit>>()
            if (rec != null) buttons += getString(R.string.replies_replay) to { sheet.dismiss(); parent.dismiss(); replay(file, rec) }
            buttons += getString(R.string.replies_share) to {
                sheet.dismiss()
                // Shared from the exports folder (the one the file provider serves), never from the recordings folder itself.
                val copy = try { Exports.exportFile(this, "OBD2Reader_" + file.name).also { file.copyTo(it, overwrite = true) } } catch (e: Exception) { null }
                if (copy == null) toast(getString(R.string.file_save_failed)) else offerFile(copy, "OBD2 Reader recording")
            }
            sheetStackedButtons(body, buttons, getString(R.string.history_delete) to {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.replies_delete_title)
                    .setMessage(R.string.replies_delete_text)
                    .setPositiveButton(R.string.history_delete) { _, _ ->
                        file.delete()
                        sheet.dismiss(); parent.dismiss()
                        toast(getString(R.string.replies_deleted))
                        showReplies()
                    }
                    .setNegativeButton(android.R.string.cancel, null).show()
            })
        }
    }

    /** Replays [rec] like the demo car: the app talks to the recording, not to a car or adapter. */
    private fun replay(file: java.io.File, rec: com.obdreader.app.obd.ReplyRecording.Recording) {
        if (ObdSession.isActive) { toast(getString(R.string.replies_disconnect_first)); return }
        val label = com.obdreader.app.session.ReplyRecordings.label(file)
        android.util.Log.i("OBD2Replay", "Replaying a recording: ${rec.exchanges.size} requests")
        startSession(com.obdreader.app.obd.ReplayTransport(rec, label))
        bottomNav.selectedItemId = R.id.nav_dashboard
    }

    private fun vinMode() = AppPrefs.vinPrivacy(prefs)

    /** Hide VIN: show it in full, hide the serial number (last 6) or hide it completely, on screen and in shares. */
    private fun showVinPrivacy() {
        val modes = com.obdreader.app.obd.VinPrivacy.Mode.entries
        choiceSheet(getString(R.string.vin_privacy_title)) { body, sheet ->
            sheetNote(body, getString(R.string.vin_privacy_note))
            sheetOptions(body, modes.map { it.label }, modes.indexOf(vinMode())) { i ->
                prefs.edit().putString(AppPrefs.VIN_PRIVACY, modes[i].key).apply()
                findViewById<View>(R.id.vinPrivacyRow)?.let { r -> rowHelp(r)?.text = modes[i].label }
                showVin(ObdSession.state.value.vin)
                sheet.dismiss()
            }
            modes.forEach { sheetNote(body, "${it.label}: ${it.help}") }
        }
    }

    /** A Diagnostics row's second line (its help text). */
    private fun rowHelp(row: View): TextView? = ((row as? ViewGroup)?.getChildAt(1) as? TextView)

    /** Code history: every code seen on each car, with dates; a button clears the phone's list (never the car's codes). */
    private fun showCodeHistory() {
        val all = com.obdreader.app.obd.CodeHistory.parse(prefs.getString(AppPrefs.CODE_HISTORY, null))
        val day = java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val make = ObdSession.carMake(this); val year = ObdSession.carYear(this); val model = ObdSession.carModel(this); val engine = ObdSession.carEngine(this)
        val current = ObdSession.historyCar(this)
        infoSheet(getString(R.string.history_codes_title)) { heading, line, body ->
            if (all.isEmpty()) {
                line(getString(R.string.history_codes_empty), true)
                return@infoSheet
            }
            line(getString(R.string.history_codes_intro), true)
            for (car in com.obdreader.app.obd.CodeHistory.cars(all, current)) {
                heading(car)
                for (e in com.obdreader.app.obd.CodeHistory.forCar(all, car)) {
                    // Maker codes are explained for the chosen car only, so other cars' codes get the generic title.
                    val title = if (car == current) DtcLibrary.lookup(e.code, make, year, model, engine).title else DtcLibrary.lookup(e.code, null, null, null, null).title
                    line("${e.code}  $title", !e.active)
                    line(com.obdreader.app.obd.CodeHistory.describe(e) { day.format(java.util.Date(it)) }, true)
                }
            }
            body.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = getString(R.string.history_codes_clear); setTextColor(themeColor(R.attr.obdAccent))
                setOnClickListener {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.history_codes_clear_title)
                        .setMessage(R.string.history_codes_clear_text)
                        .setPositiveButton(R.string.history_codes_clear) { _, _ ->
                            prefs.edit().remove(AppPrefs.CODE_HISTORY).apply()
                            currentSheet?.dismiss()
                            toast(getString(R.string.history_codes_cleared))
                        }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f).toInt() })
        }
    }

    /** Shows a "please wait" while [work] asks the car, then its sheet; says so if the car stops answering. */
    private fun runCheck(title: String, message: String = "Asking your car. This takes a few seconds.", work: suspend () -> Unit) {
        if (!ObdSession.state.value.isConnected) { toast("Connect to your car first."); return }
        val wait = MaterialAlertDialogBuilder(this).setTitle(title)
            .setView(com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
                isIndeterminate = true; setPadding(dp(24f).toInt(), dp(16f).toInt(), dp(24f).toInt(), dp(8f).toInt())
            })
            .setMessage(message).setCancelable(false).show()
        lifecycleScope.launch {
            try { work() } catch (e: CancellationException) { throw e } catch (e: Exception) { toast("The car stopped answering. Try again.") }
            finally { wait.dismiss() }
        }
    }

    /** A bottom sheet with a title; [fill] adds the lines. */
    private fun infoSheet(title: String, fill: (heading: (String) -> Unit, line: (String, Boolean) -> Unit, body: LinearLayout) -> Unit) {
        val sheet = BottomSheetDialog(this)
        val pad = dp(20f).toInt()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
        body.addView(TextView(this).apply {
            text = title; setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary))
        })
        val heading = { t: String -> body.addView(TextView(this).apply {
            text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdTextPrimary))
            setPadding(0, dp(16f).toInt(), 0, dp(4f).toInt())
        }) }
        val line = { t: String, secondary: Boolean -> body.addView(TextView(this).apply {
            text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setLineSpacing(0f, 1.15f); setTextIsSelectable(true)
            setTextColor(themeColor(if (secondary) R.attr.obdTextSecondary else R.attr.obdTextPrimary)); setPadding(0, dp(4f).toInt(), 0, 0)
        }) }
        fill(heading, line, body)
        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
        currentSheet = sheet
        sheet.show()
    }

    /** The deeper-check sheet on screen, so a search can close it before showing its results. */
    private var currentSheet: BottomSheetDialog? = null

    /** "Codes recently cleared?": the verdict, each counter (N/A where the car doesn't say) and what it means. */
    private suspend fun showClearedCheck() {
        val check = ObdSession.readClearedCheck()
        android.util.Log.i("OBD2Deep", "cleared: ${check.verdict}")
        infoSheet(getString(R.string.cleared_title)) { heading, line, _ ->
            line(check.headline(), false)
            check.notes().forEach { line(it, false) }
            heading("What the car counts")
            check.lines().forEach { (k, v) -> line("$k: $v", v == com.obdreader.app.obd.ClearedCheck.NA) }
            heading("What this means")
            line("When trouble codes are cleared, the engine computer starts these counts again from zero. Very small numbers on a car for sale can mean a fault was cleared to hide the warning light until it comes back.", true)
            line(com.obdreader.app.obd.ClearedCheck.CAVEAT, true)
            line("It's also in the code report when you share it.", true)
        }
    }

    private suspend fun showReadiness() {
        val r = ObdSession.readReadiness()
        val cal = ObdSession.readCalibrationIds()
        if (r == null) { toast("The car didn't report its readiness monitors."); return }
        android.util.Log.i("OBD2Deep", "readiness: ${r.summary()}")
        infoSheet(getString(R.string.readiness_title)) { heading, line, _ ->
            line(r.summary() + ". Check-engine light " + (if (r.milOn) "on" else "off") + ", ${r.codeCount} stored ${if (r.codeCount == 1) "code" else "codes"}.", false)
            heading("Monitors")
            r.monitors.forEach { m ->
                line((if (m.state == Readiness.State.COMPLETE) "✓  " else "…  ") + "${m.name}: ${m.state.label}", m.state == Readiness.State.COMPLETE)
                line("     ${m.about}", true)
            }
            heading("What this means")
            line("The engine computer runs these checks by itself as you drive. They reset whenever the codes are cleared or the battery is disconnected, and finish again after a few days of normal driving, town and highway. An emissions inspection usually wants them complete.", true)
            if (cal.isNotEmpty()) { heading("Engine computer software (calibration ID)"); cal.forEach { line(it, false) } }
        }
    }

    private suspend fun showTestResults() {
        val results = ObdSession.readTestResults()
        android.util.Log.i("OBD2Deep", "self-tests: ${results?.size ?: "not CAN"}")
        infoSheet(getString(R.string.tests_title)) { heading, line, _ ->
            when {
                results == null -> line("Self-test results are read on CAN cars (most from 2008, many earlier). This car uses an older protocol, where each maker sets out these results its own way.", true)
                results.isEmpty() -> line("The car didn't report any self-test results.", true)
                else -> {
                    val fails = results.count { !it.passed }
                    line(if (fails == 0) "All ${results.size} tests within their limits." else "$fails of ${results.size} tests outside their limits.", false)
                    results.groupBy { it.mid }.forEach { (_, list) ->
                        heading(list.first().monitor)
                        list.forEach { t -> line("${if (t.passed) "✓" else "✗"}  Test ${String.format(Locale.US, "%02X", t.tid)}: ${t.text()}", t.passed) }
                    }
                    heading("What this means")
                    line("These are the engine computer's own measurements from the last time it ran each check, with the limits they must stay between. A result close to a limit can show a part wearing out before it sets a trouble code. The test numbers are the maker's own, so what each one measures isn't always published.", true)
                }
            }
        }
    }

    private suspend fun showModules() {
        val results = ObdSession.scanModules(this)
        logModules(results)
        moduleSheet(results, null)
    }

    private fun logModules(results: List<Pair<ModuleScan.Module, ModuleScan.Answer>>?, tag: String = "modules") {
        android.util.Log.i("OBD2Deep", "$tag: " + (results?.joinToString("; ") { (m, a) -> "${m.name}=" + when (a) {
            is ModuleScan.Answer.Codes -> a.codes.joinToString(",") { it.label }.ifEmpty { "none" }
            ModuleScan.Answer.Refused -> "refused"; ModuleScan.Answer.Silent -> "silent" } } ?: "not CAN"))
    }

    /** The modules sheet: the make's known modules, and after a search, any others that answered. */
    private fun moduleSheet(results: List<Pair<ModuleScan.Module, ModuleScan.Answer>>?, found: List<Pair<ModuleScan.Module, ModuleScan.Answer>>?) {
        val make = ObdSession.carMake(this); val year = ObdSession.carYear(this)
        val model = ObdSession.carModel(this); val engine = ObdSession.carEngine(this)
        infoSheet(getString(R.string.modules_title)) { heading, line, body ->
            if (results == null) {
                line("Other modules are read on CAN cars (most from 2008), and on Fords and Mazdas that use J1850. This car uses an older protocol, where only the engine computer can be reached with a standard adapter.", true)
                return@infoSheet
            }
            val all = results + found.orEmpty()
            val total = all.sumOf { (it.second as? ModuleScan.Answer.Codes)?.codes?.size ?: 0 }
            line(if (total == 0) "No codes in the modules that answered." else "$total ${if (total == 1) "code" else "codes"} found.", false)
            fun show(list: List<Pair<ModuleScan.Module, ModuleScan.Answer>>) = list.forEach { (m, a) ->
                heading(m.name)
                when (a) {
                    is ModuleScan.Answer.Codes -> if (a.codes.isEmpty()) line("No codes", true) else a.codes.forEach { c ->
                        line("${c.label}  ${c.text ?: DtcLibrary.lookup(c.code, make, year, model, engine).title}" + if (c.pending && !c.confirmed) " (pending)" else " (stored)", false)
                    }
                    ModuleScan.Answer.Refused -> line("Answered, but doesn't share its codes this way.", true)
                    ModuleScan.Answer.Silent -> line("Didn't answer: not fitted, or on a part of the car's network the adapter can't reach.", true)
                }
            }
            val volvo = ObdSession.isVolvoD3B0(this)
            if (!volvo) show(results)
            if (volvo) {
                // Only the modules this car has: the rest are listed together, not one "didn't answer" each.
                val answered = results.filter { it.second !is ModuleScan.Answer.Silent }
                if (ObdSession.volvoAdapterRefused) {
                    line("Your adapter can't wake Volvo's own modules: it doesn't support the command they need, which many cheap clones leave out. Adapters that can: OBDLink (MX+, LX, EX), BAFX and Veepeak OBDCheck BLE+. Everything else in the app still works with your adapter.", false)
                } else {
                    show(answered)
                    if (answered.none { it.first.request in com.obdreader.app.obd.VolvoD3B0.ENGINES }) {
                        heading("Engine")
                        line("Your engine's codes are on the Diagnostics tab. This engine (such as the turbo 850's Motronic 4.3) reports them with standard OBD2 rather than Volvo's own protocol, and the app reads those on every car.", true)
                    }
                    if (answered.none { it.first.request == com.obdreader.app.obd.VolvoD3B0.COMBI.address }) {
                        heading(com.obdreader.app.obd.VolvoD3B0.COMBI.name)
                        line("Didn't answer. Some 1996–97 850s have a Yazaki-made cluster that doesn't talk through the OBD2 port; its warning lights still work as normal.", true)
                    }
                    val skip = com.obdreader.app.obd.VolvoD3B0.ENGINES + com.obdreader.app.obd.VolvoD3B0.COMBI.address
                    val missing = results.filter { it.second is ModuleScan.Answer.Silent && it.first.request !in skip }
                    if (missing.isNotEmpty()) line("Not on this car (or not answering): " + missing.joinToString(", ") { it.first.name } + ".", true)
                }
                heading("About Volvo module codes")
                line("Read with Volvo's own keyword D3 B0 protocol, which these 1996–2000 cars use. The airbag module is never asked. Code meanings come from Volvo owners' work (jonesrh.info) and aren't Volvo's official list.", true)
            } else if (found != null) {
                body.addView(TextView(this).apply {
                    text = "Found by searching"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary))
                    setPadding(0, dp(24f).toInt(), 0, 0)
                })
                if (found.isEmpty()) line("No other modules answered.", true) else show(found)
            } else {
                body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = getString(R.string.modules_search); setTextColor(themeColor(R.attr.obdAccent))
                    setOnClickListener { searchAllModules(results) }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f).toInt() })
                line(getString(R.string.modules_search_help), true)
            }
            if (ModuleScan.SecondNetwork.offeredFor(make, ObdSession.state.value.protocolCode)) {
                body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = getString(R.string.modules_second); setTextColor(themeColor(R.attr.obdAccent))
                    setOnClickListener { searchSecondNetwork() }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f).toInt() })
                line(getString(R.string.modules_second_help), true)
            }
            heading("About this")
            line("Read-only: the app asks each module for its codes and never clears them. Airbag modules are never asked. Codes starting C (chassis), B (body) or U (network) with a 1 after the letter are the maker's own; look them up in your maker's service information.", true)
        }
    }

    /** "Search every address", with a progress bar, then the sheet again with what it found. */
    private fun searchAllModules(results: List<Pair<ModuleScan.Module, ModuleScan.Answer>>) {
        if (!ObdSession.state.value.isConnected) { toast("Connect to your car first."); return }
        currentSheet?.dismiss()
        val bar = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
            isIndeterminate = false; max = 100; setPadding(dp(24f).toInt(), dp(16f).toInt(), dp(24f).toInt(), dp(8f).toInt())
        }
        val wait = MaterialAlertDialogBuilder(this).setTitle(R.string.modules_search).setView(bar)
            .setMessage("Asking every module address in turn. This takes about a minute.").setCancelable(false).show()
        lifecycleScope.launch {
            try {
                val known = results.filter { it.second !is ModuleScan.Answer.Silent }.map { it.first.request }.toSet()
                val found = ObdSession.searchModules(this@MainActivity, known) { done, total ->
                    runOnUiThread { bar.setProgressCompat(if (total == 0) 100 else done * 100 / total, true) }
                }
                logModules(found, "search")
                foundModules = found.orEmpty().filter { it.second !is ModuleScan.Answer.Silent }.map { it.first }
                wait.dismiss()
                // A known module that was silent before but answered the search moves to the found list.
                val foundReq = found.orEmpty().map { it.first.request }.toSet()
                moduleSheet(results.filter { it.first.request !in foundReq }, found.orEmpty())
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                wait.dismiss(); toast("The car stopped answering. Try again.")
            }
        }
    }

    /** "Search the second network" (Fords and Mazdas, STN adapters), with a progress bar, then its own sheet. */
    private fun searchSecondNetwork() {
        if (!ObdSession.state.value.isConnected) { toast("Connect to your car first."); return }
        currentSheet?.dismiss()
        val bar = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
            isIndeterminate = false; max = 100; setPadding(dp(24f).toInt(), dp(16f).toInt(), dp(24f).toInt(), dp(8f).toInt())
        }
        val wait = MaterialAlertDialogBuilder(this).setTitle(R.string.modules_second).setView(bar)
            .setMessage("Asking every module address on the second network in turn. This takes about a minute.").setCancelable(false).show()
        lifecycleScope.launch {
            try {
                val result = ObdSession.searchSecondNetwork { done, total ->
                    runOnUiThread { bar.setProgressCompat(if (total == 0) 100 else done * 100 / total, true) }
                }
                wait.dismiss()
                val make = ObdSession.carMake(this@MainActivity); val year = ObdSession.carYear(this@MainActivity)
                val model = ObdSession.carModel(this@MainActivity); val engine = ObdSession.carEngine(this@MainActivity)
                when (result) {
                    ObdSession.SecondNetworkResult.AdapterCantReach -> {
                        android.util.Log.i("OBD2Deep", "second network: adapter can't reach it")
                        infoSheet(getString(R.string.modules_second)) { _, line, _ ->
                            line("Your adapter can't switch to the second network. Adapters built on STN chips can, such as OBDLink MX+ and OBDLink EX; most cheap ELM327 adapters can't.", false)
                            line("Everything else in Deeper checks still works with your adapter.", true)
                        }
                    }
                    is ObdSession.SecondNetworkResult.Found -> {
                        logModules(result.modules, "second network")
                        infoSheet(getString(R.string.modules_second)) { heading, line, _ ->
                            val total = result.modules.sumOf { (it.second as? ModuleScan.Answer.Codes)?.codes?.size ?: 0 }
                            line(when {
                                result.modules.isEmpty() -> "No modules answered on the second network. Your car may not have one, or its modules don't share their codes this way."
                                total == 0 -> "${result.modules.size} ${if (result.modules.size == 1) "module" else "modules"} answered, with no codes."
                                else -> "$total ${if (total == 1) "code" else "codes"} found."
                            }, false)
                            result.modules.forEach { (m, a) ->
                                heading(m.name)
                                when (a) {
                                    is ModuleScan.Answer.Codes -> if (a.codes.isEmpty()) line("No codes", true) else a.codes.forEach { c ->
                                        line("${c.label}  ${DtcLibrary.lookup(c.code, make, year, model, engine).title}" + if (c.pending && !c.confirmed) " (pending)" else " (stored)", false)
                                    }
                                    ModuleScan.Answer.Refused -> line("Answered, but doesn't share its codes this way.", true)
                                    ModuleScan.Answer.Silent -> line("Didn't answer when asked for its codes.", true)
                                }
                            }
                            heading("About this")
                            line("Read-only: the app asks each module for its codes and never clears them. The airbag module is never asked.", true)
                        }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                wait.dismiss(); toast("The car stopped answering. Try again.")
            }
        }
    }

    // ---- Enhanced diagnostics ----

    /** Modules the last "Search every address" found, so module information can include them. */
    private var foundModules: List<ModuleScan.Module> = emptyList()

    private suspend fun showEnhanced() {
        val car = AppPrefs.savedCar(AppPrefs.of(this))
        val make = ObdSession.carMake(this)
        val tried = EnhancedReadings.forCar(make, car?.model, ObdSession.carYear(this))
        val first = ObdSession.readEnhanced(tried)
        // A 1996–2000 Volvo: the instrument cluster's stored mileage (the true distance).
        val volvo = ObdSession.isVolvoD3B0(this)
        val volvoKm = if (volvo) ObdSession.readVolvoMileage() else null
        if (volvo) android.util.Log.i("OBD2Deep", "volvo mileage: " + (volvoKm?.let { String.format(Locale.US, "%.0f km", it) } ?: "none"))
        val answered = tried.filter { first[it] != null }
        android.util.Log.i("OBD2Deep", "enhanced: tried ${tried.size}, answered " + answered.joinToString(", ") { "${it.name}=${first[it]?.let(it::format)}" }.ifEmpty { "none" })
        val values = mutableMapOf<EnhancedReadings.Reading, TextView>()
        infoSheet(getString(R.string.enhanced_title)) { heading, line, body ->
            heading("Manufacturer readings")
            if (volvo) {
                val odo = volvoKm?.let { km -> if (Units.mph) String.format(Locale.US, "%,.0f miles", km / 1.609344) else String.format(Locale.US, "%,.0f km", km) } ?: "N/A"
                body.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6f).toInt(), 0, 0)
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(this@MainActivity).apply { text = "Odometer (true mileage)"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdTextPrimary)) })
                        addView(TextView(this@MainActivity).apply { text = "Instrument cluster (COMBI)"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(this@MainActivity).apply { text = odo; typeface = titleTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(themeColor(R.attr.obdAccent)) })
                })
                line(if (volvoKm == null && ObdSession.volvoAdapterRefused) "Your adapter can't wake Volvo's instrument cluster (many cheap clones can't). An OBDLink, BAFX or Veepeak OBDCheck BLE+ can."
                    else if (volvoKm == null) "Your instrument cluster didn't share its mileage. Some 1996–97 850s have a Yazaki-made cluster that doesn't give it out through the OBD2 port at all, so no app can read it there; the mileage is still on the dashboard."
                    else "The mileage the instrument cluster has stored, counted in 10-mile steps, so it's right to within 10 miles (16 km) even if the dashboard odometer has stopped. A replaced cluster shows its own mileage.", true)
            }
            when {
                volvo && tried.isEmpty() -> {}
                tried.isEmpty() -> line("No manufacturer readings are known for ${make ?: "this make"} yet. Module information, below, works on every make.", true)
                answered.isEmpty() -> line("Your car didn't answer any of the ${tried.size} readings known for ${make ?: "its make"}. Not every model has them.", true)
                else -> answered.forEach { r ->
                    body.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6f).toInt(), 0, 0)
                        addView(LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(TextView(this@MainActivity).apply { text = r.name; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdTextPrimary)) })
                            addView(TextView(this@MainActivity).apply { text = r.module; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(TextView(this@MainActivity).apply {
                            text = first[r]?.let(r::format) ?: "N/A"; typeface = titleTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(themeColor(R.attr.obdAccent))
                            values[r] = this
                        })
                    })
                }
            }
            if (answered.isNotEmpty() && answered.size < tried.size) line("${tried.size - answered.size} more known for your make weren't answered by your car.", true)
            if (tried.isNotEmpty()) line(getString(R.string.enhanced_source), true)
            heading("Module information")
            line("Part numbers, software versions and the VIN each module holds, read with the standard identifiers every make's modules use.", true)
            body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.enhanced_module_info); setTextColor(themeColor(R.attr.obdAccent))
                setOnClickListener { readModuleInfo() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f).toInt() })
            heading("About this")
            line("Read-only: these only read from the car, with the makers' own read services. Nothing is changed or cleared.", true)
        }
        // Live while the sheet is open: the readings that answered, every 2 seconds.
        val sheet = currentSheet ?: return
        if (answered.isEmpty()) return
        val job = lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(2000)
                if (!ObdSession.state.value.isConnected) { values.values.forEach { it.text = "N/A" }; continue }
                val now = try { ObdSession.readEnhanced(answered) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyMap() }
                values.forEach { (r, tv) -> tv.text = now[r]?.let(r::format) ?: "N/A" }
            }
        }
        sheet.setOnDismissListener { job.cancel() }
    }

    // ---- EV battery ----

    /**
     * EV battery: the standard charge reading (5B), the maker's battery readings where they're
     * known for this car (marked Unofficial), and the charge's pace and graph while the sheet is
     * open. Read-only: the high-voltage system is never woken or commanded.
     */
    private suspend fun showEvBattery() {
        val car = AppPrefs.savedCar(AppPrefs.of(this))
        val make = ObdSession.carMake(this)
        val maker = EvBattery.makerReadings(make, car?.model, ObdSession.carYear(this))
        val first: Map<EnhancedReadings.Reading, Double?> = if (maker.isEmpty()) emptyMap() else ObdSession.readEnhanced(maker)
        val answered = maker.filter { first[it] != null }
        val listed = ObdSession.state.value.supported?.contains(0x5B) == true
        val hasCharge = listed || answered.any { EvBattery.isCharge(it) }
        android.util.Log.i("OBD2Deep", "ev battery: 5B " + (if (listed) "listed" else "not listed") + ", maker tried ${maker.size}, answered " +
            answered.joinToString(", ") { "${it.id}=${first[it]?.let(it::format)}" }.ifEmpty { "none" })
        fun pct(v: Double?) = v?.let { String.format(Locale.US, "%.0f%%", it) } ?: "N/A"
        val values = mutableMapOf<EnhancedReadings.Reading, TextView>()
        var standardView: TextView? = null
        var paceView: TextView? = null
        var graph: com.obdreader.app.ui.ChargeGraphView? = null
        infoSheet(getString(R.string.ev_title)) { heading, line, body ->
            fun valueRow(name: String, sub: String, value: String, unofficial: Boolean): TextView {
                val v = TextView(this).apply { text = value; typeface = titleTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(themeColor(R.attr.obdAccent)) }
                body.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6f).toInt(), 0, 0)
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(this@MainActivity).apply { text = name; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdTextPrimary)) })
                        addView(TextView(this@MainActivity).apply {
                            text = if (unofficial) getString(R.string.ev_unofficial) + " · " + sub else sub
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary))
                        })
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(v)
                })
                return v
            }
            heading("Battery")
            standardView = valueRow(getString(R.string.ev_charge), getString(R.string.ev_charge_sub), pct(ObdSession.state.value.readings[EvBattery.CHARGE_KEY]), false)
            answered.forEach { r -> values[r] = valueRow(r.name, r.module, first[r]?.let(r::format) ?: "N/A", true) }
            if (!listed && answered.isEmpty()) line(getString(R.string.ev_none), true)
            heading("Charging")
            if (hasCharge) {
                val pace = TextView(this).apply {
                    text = getString(R.string.ev_watching); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextPrimary))
                    setPadding(0, dp(4f).toInt(), 0, dp(8f).toInt())
                }
                body.addView(pace)
                paceView = pace
                val chart = com.obdreader.app.ui.ChargeGraphView(this, themeColor(R.attr.obdAccent), themeColor(R.attr.obdTextSecondary) and 0x40FFFFFF, themeColor(R.attr.obdTextSecondary))
                body.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140f).toInt()))
                graph = chart
                line(getString(R.string.ev_charging_help), true)
            } else line(getString(R.string.ev_no_charge), true)
            if (answered.isNotEmpty()) line(getString(R.string.ev_unofficial_note), true)
            heading("About this")
            line(getString(R.string.ev_about), true)
        }
        val sheet = currentSheet ?: return
        if (!hasCharge && answered.isEmpty()) return
        // Live while the sheet is open, every 5 seconds.
        val job = lifecycleScope.launch {
            while (true) {
                val connected = ObdSession.state.value.isConnected
                val std = if (connected) ObdSession.state.value.readings[EvBattery.CHARGE_KEY] else null
                val now: Map<EnhancedReadings.Reading, Double?> = if (!connected || answered.isEmpty()) emptyMap()
                    else try { ObdSession.readEnhanced(answered) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyMap() }
                standardView?.text = pct(std)
                values.forEach { (r, tv) -> tv.text = now[r]?.let(r::format) ?: "N/A" }
                evLog.add(android.os.SystemClock.elapsedRealtime(), EvBattery.charge(std, now))
                graph?.show(evLog.all.map { it.timeMs to it.percent })
                val pack = EvBattery.packKwh(now.entries.firstOrNull { it.key.id == "leaf.capacity" }?.value, now.entries.firstOrNull { it.key.id == "leaf.hv_volts" }?.value)
                paceView?.text = evPace(pack) ?: getString(if (connected) R.string.ev_watching else R.string.ev_disconnected)
                kotlinx.coroutines.delay(5000)
            }
        }
        sheet.setOnDismissListener { job.cancel() }
    }

    /** "Charging: +24 % an hour · about 5.8 kW · 80% in about 1 h 10 min", or null until the pace is known. */
    private fun evPace(packKwh: Double?): String? {
        val text = EvBattery.describe(evLog) ?: return null
        if (evLog.state() != EvBattery.State.CHARGING) return text
        val parts = mutableListOf(text)
        EvBattery.powerKw(evLog.ratePerHour(), packKwh)?.let { parts += String.format(Locale.US, "about %.1f kW", it) }
        val to80 = evLog.minutesTo(80.0)
        val toFull = evLog.minutesTo(100.0)
        when {
            to80 != null -> parts += "80% in about ${EvBattery.duration(to80)}"
            toFull != null -> parts += "full in about ${EvBattery.duration(toFull)}"
        }
        return parts.joinToString(" · ")
    }

    private fun readModuleInfo() {
        if (!ObdSession.state.value.isConnected) { toast("Connect to your car first."); return }
        currentSheet?.dismiss()
        val car = AppPrefs.savedCar(AppPrefs.of(this))
        val modules = (ModuleScan.modulesFor(ObdSession.carMake(this), car?.model, ObdSession.state.value.protocolCode?.removePrefix("A") in setOf("7", "9")) + foundModules).distinctBy { it.request }
        val bar = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
            isIndeterminate = false; max = 100; setPadding(dp(24f).toInt(), dp(16f).toInt(), dp(24f).toInt(), dp(8f).toInt())
        }
        val wait = MaterialAlertDialogBuilder(this).setTitle(R.string.enhanced_module_info).setView(bar)
            .setMessage("Asking each module for its part numbers and versions.").setCancelable(false).show()
        lifecycleScope.launch {
            val info = try {
                ObdSession.readModuleInfo(ObdSession.carMake(this@MainActivity), modules) { done, total -> runOnUiThread { bar.setProgressCompat(done * 100 / total.coerceAtLeast(1), true) } }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            wait.dismiss()
            if (info == null) { toast("The car stopped answering. Try again."); return@launch }
            android.util.Log.i("OBD2Deep", com.obdreader.app.obd.VinPrivacy.scrub("module info: " + info.joinToString("; ") { (m, f) -> "${m.name}: " + f.joinToString(", ") { "${it.first}=${it.second}" } }.ifEmpty { "none" }, vinMode(), Exports.knownVins(this@MainActivity, ObdSession.state.value.vin)))
            infoSheet(getString(R.string.enhanced_module_info)) { heading, line, _ ->
                if (info.isEmpty()) { line("No module answered with its identifiers. Older modules don't have them.", true); return@infoSheet }
                val carVin = ObdSession.state.value.vin
                info.forEach { (m, fields) ->
                    heading(m.name)
                    if (fields.isEmpty()) line("Answered, but gave none of the standard identifiers.", true)
                    fields.forEach { (k, v) ->
                        val vinNote = if (k.startsWith("VIN") && carVin != null && v != carVin) "  (not the VIN the engine computer reports)" else ""
                        line("$k: ${if (k.startsWith("VIN")) com.obdreader.app.obd.VinPrivacy.mask(v, vinMode()) else v}$vinNote", false)
                    }
                }
                heading("About this")
                line("A module's VIN that doesn't match the car's can mean it came from another car, or wasn't programmed with this car's VIN when it was fitted.", true)
            }
        }
    }

    // ---- Custom readings ----

    private fun customReadings(): MutableList<CustomReading> = CustomReading.decodeAll(prefs.getString(PREF_CUSTOM, null)).toMutableList()
    private fun saveCustom(list: List<CustomReading>) = prefs.edit().putString(PREF_CUSTOM, CustomReading.encodeAll(list)).apply()

    private fun showCustomReadings() {
        val list = customReadings()
        val sheet = BottomSheetDialog(this)
        val pad = dp(20f).toInt()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
        body.addView(TextView(this).apply { setText(R.string.custom_title); setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary)) })
        val values = mutableMapOf<CustomReading, TextView>()
        if (list.isEmpty()) body.addView(TextView(this).apply {
            setText(R.string.custom_empty); setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setPadding(0, dp(8f).toInt(), 0, 0)
        })
        list.forEach { r ->
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = panelBackground(); val p = dp(14f).toInt(); setPadding(p, p, p, p)
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@MainActivity).apply { text = r.name; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextPrimary)) })
                    addView(TextView(this@MainActivity).apply { text = (r.header?.let { "$it · " } ?: "") + r.request + " · " + r.formula; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@MainActivity).apply {
                    text = "–"; typeface = titleTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); setTextColor(themeColor(R.attr.obdAccent))
                    values[r] = this
                })
                setOnLongClickListener {
                    MaterialAlertDialogBuilder(this@MainActivity).setTitle("Delete ${r.name}?")
                        .setPositiveButton(R.string.history_delete) { _, _ -> saveCustom(customReadings().filter { it != r }); sheet.dismiss(); showCustomReadings() }
                        .setNegativeButton(android.R.string.cancel, null).show(); true
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f).toInt() })
        }
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(MaterialButton(this@MainActivity).apply { setText(R.string.custom_add); setOnClickListener { sheet.dismiss(); addCustomReading() } })
            if (list.isNotEmpty()) addView(MaterialButton(this@MainActivity, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                setText(R.string.custom_refresh); setTextColor(themeColor(R.attr.obdAccent)); setOnClickListener { readCustomInto(values) }
            })
            setPadding(0, dp(12f).toInt(), 0, 0)
        })
        body.addView(TextView(this).apply {
            setText(R.string.custom_note); setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setPadding(0, dp(12f).toInt(), 0, 0)
        })
        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
        sheet.show()
        readCustomInto(values)
    }

    private fun readCustomInto(values: Map<CustomReading, TextView>) {
        if (!ObdSession.state.value.isConnected) { values.values.forEach { it.text = "–" }; return }
        lifecycleScope.launch {
            for ((r, tv) in values) {
                val v = try { ObdSession.readCustom(r) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                tv.text = v?.let { String.format(Locale.US, if (kotlin.math.abs(it) >= 100) "%.0f" else "%.2f", it) + if (r.unit.isEmpty()) "" else " ${r.unit}" } ?: "N/A"
                android.util.Log.i("OBD2Deep", "custom ${r.name}: ${tv.text}")
            }
        }
    }

    private fun addCustomReading() {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20f).toInt(), dp(8f).toInt(), dp(20f).toInt(), 0) }
        fun field(hint: String, caps: Boolean): com.google.android.material.textfield.TextInputEditText {
            val box = com.google.android.material.textfield.TextInputLayout(this).apply { this.hint = hint }
            val e = com.google.android.material.textfield.TextInputEditText(box.context).apply {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or (if (caps) android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else 0) or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            box.addView(e); form.addView(box); return e
        }
        val name = field("Name (e.g. Transmission temp)", false)
        val req = field("Request, hex (e.g. 221E1C)", true)
        val hdr = field("Module address, optional (e.g. 7E0)", true)
        val f = field("Formula (e.g. (A*256+B)/16)", true)
        val unit = field("Unit (e.g. °C)", false)
        val d = MaterialAlertDialogBuilder(this).setTitle(R.string.custom_add).setView(form)
            .setPositiveButton(R.string.save, null).setNegativeButton(android.R.string.cancel, null).show()
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val t = { e: com.google.android.material.textfield.TextInputEditText -> e.text?.toString().orEmpty() }
            val problem = CustomReading.problem(t(name), t(req), t(hdr), t(f))
            if (problem != null) { toast(problem); return@setOnClickListener }
            val r = CustomReading.validate(t(name), t(req), t(hdr), t(f), t(unit)) ?: return@setOnClickListener
            saveCustom(customReadings() + r)
            d.dismiss(); showCustomReadings()
        }
    }

    private fun marketLabel(): String =
        if (prefs.contains(AppPrefs.MARKET)) AppPrefs.market(prefs).label else "Automatic (${AppPrefs.market(prefs).label})"

    /**
     * Diagnostics → Where your car was sold: decides the notes on when OBD2 became required.
     * Automatic follows the phone's country; an imported car follows the market it was built for.
     */
    private fun showMarketPicker() {
        val all = com.obdreader.app.obd.Market.values()
        val auto = com.obdreader.app.obd.Market.fromCountry(AppPrefs.phoneCountry())
        val names = listOf("Automatic, from your phone (${auto.label})") + all.map { it.label }
        val chosen = com.obdreader.app.obd.Market.byName(prefs.getString(AppPrefs.MARKET, null))
        choiceSheet(getString(R.string.market)) { body, sheet ->
            sheetNote(body, "Decides the notes on when OBD2 became required. An imported car follows the market it was built for.")
            sheetOptions(body, names, if (chosen == null) 0 else all.indexOf(chosen) + 1) { i ->
                if (i == 0) prefs.edit().remove(AppPrefs.MARKET).apply() else prefs.edit().putString(AppPrefs.MARKET, all[i - 1].name).apply()
                AppPrefs.applyUnits(prefs)
                android.util.Log.i("OBD2Car", "market: " + com.obdreader.app.obd.Market.current.name)
                findViewById<TextView>(R.id.marketSetting).text = marketLabel()
                updateCarSetting()
                sheet.dismiss()
            }
        }
    }

    private fun carReadingsLabel(): String =
        com.obdreader.app.obd.CarReadings.decode(prefs.getString(AppPrefs.CAR_READINGS, null)).joinToString(", ") { com.obdreader.app.obd.CarReadings.label(it) }

    /**
     * Diagnostics → Android Auto readings: the four tiles next to speed and engine speed on the
     * car's screen, in order. Picking one that's already shown swaps them.
     */
    private fun showCarReadings(chosen: MutableList<String>) {
        val all = com.obdreader.app.obd.CarReadings.CHOICES
        placesSheet(
            getString(R.string.car_readings), "Speed and engine speed always come first. Choose the four tiles after them.",
            (1..com.obdreader.app.obd.CarReadings.COUNT).map { "Tile ${it + 2}" }, chosen,
            { all.map { it.key to it.label } }, { com.obdreader.app.obd.CarReadings.label(it) },
            save = { keys ->
                prefs.edit().putString(AppPrefs.CAR_READINGS, com.obdreader.app.obd.CarReadings.encode(keys)).apply()
                android.util.Log.i("OBD2Car", "car readings: " + keys.joinToString(","))
                findViewById<TextView>(R.id.autoSetting).text = carReadingsLabel()
            },
            reset = {
                prefs.edit().remove(AppPrefs.CAR_READINGS).apply()
                findViewById<TextView>(R.id.autoSetting).text = carReadingsLabel()
            },
        )
    }

    private fun widgetReadingsLabel(): String =
        com.obdreader.app.obd.WidgetContent.decodeReadings(prefs.getString(com.obdreader.app.widget.ObdWidget.PREF_READINGS, null))
            .mapNotNull { k -> com.obdreader.app.obd.WidgetContent.READINGS.firstOrNull { it.key == k }?.label }.joinToString(", ")

    /**
     * Diagnostics → Widget readings: up to three live readings for the home-screen widget, in
     * order. The first is the one the 2 × 1 widget shows. RPM is shown in thousands (3.2k).
     */
    private fun showWidgetReadings(chosen: MutableList<String>) {
        val all = com.obdreader.app.obd.WidgetContent.READINGS
        fun name(k: String?) = k?.let { key -> all.firstOrNull { it.key == key }?.label } ?: "None"
        val places = listOf("Reading 1 (also the 2 × 1 widget)", "Reading 2", "Reading 3")
        val slotsNow: MutableList<String?> = (0 until 3).map { chosen.getOrNull(it) }.toMutableList()
        placesSheet(
            getString(R.string.widget_readings), null, places, slotsNow,
            // Reading 1 is always set; the others can be left empty.
            { slot -> (if (slot == 0) emptyList() else listOf(null to "None")) + all.map { it.key to it.label } }, { name(it) },
            save = { keys ->
                val list = keys.filterNotNull()
                prefs.edit().putString(com.obdreader.app.widget.ObdWidget.PREF_READINGS, com.obdreader.app.obd.WidgetContent.encodeReadings(list)).apply()
                android.util.Log.i("OBD2Widget", "widget readings: " + list.joinToString(","))
                findViewById<TextView>(R.id.widgetSetting).text = widgetReadingsLabel()
                com.obdreader.app.widget.ObdWidget.refresh(this)
            },
            reset = {
                prefs.edit().remove(com.obdreader.app.widget.ObdWidget.PREF_READINGS).apply()
                findViewById<TextView>(R.id.widgetSetting).text = widgetReadingsLabel()
                com.obdreader.app.widget.ObdWidget.refresh(this)
            },
        )
    }

    /** Diagnostics → App icon: the Spark colourways, each shown as it looks on the home screen. */
    private fun showIconPicker() {
        val sheet = BottomSheetDialog(this)
        val pad = dp(20f).toInt()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
        body.addView(TextView(this).apply { setText(R.string.app_icon); setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary)) })
        body.addView(TextView(this).apply { setText(R.string.app_icon_help); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdTextSecondary)); setPadding(0, dp(4f).toInt(), 0, dp(8f).toInt()) })
        val current = AppIcons.current(this)
        AppIcons.ALL.forEach { icon ->
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = panelBackground(); val p = dp(12f).toInt(); setPadding(p, p, p, p)
                isClickable = true; isFocusable = true
                contentDescription = icon.name + if (icon == current) ", chosen" else ""
                addView(android.widget.ImageView(this@MainActivity).apply {
                    setImageDrawable(androidx.core.content.ContextCompat.getDrawable(this@MainActivity, icon.icon))
                    // The launcher's own shape: a rounded square here, as most phones show it.
                    clipToOutline = true
                    outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, v.width * 0.28f) }
                    }
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(48f).toInt(), dp(48f).toInt()))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(14f).toInt(), 0, 0, 0)
                    addView(TextView(this@MainActivity).apply { text = icon.name; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextPrimary)) })
                    addView(TextView(this@MainActivity).apply { text = icon.note; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (icon == current) addView(android.widget.ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_check); imageTintList = ColorStateList.valueOf(themeColor(R.attr.obdAccent))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(24f).toInt(), dp(24f).toInt()))
                setOnClickListener {
                    sheet.dismiss()
                    if (icon == current) return@setOnClickListener
                    android.util.Log.i("OBD2Icon", "app icon: ${icon.id}")
                    toast("App icon changed to ${icon.name}. Your home screen can take a few seconds to show it.")
                    AppIcons.choose(this@MainActivity, icon)
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f).toInt() })
        }
        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
        sheet.show()
    }

    private fun runCarScan() {
        if (!ObdSession.state.value.isConnected) { toast("Connect to your car first."); return }
        val wait = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.scan)
            .setView(com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
                isIndeterminate = true; setPadding(dp(24f).toInt(), dp(16f).toInt(), dp(24f).toInt(), dp(8f).toInt())
            })
            .setMessage("Asking your car. This takes a few seconds.")
            .setCancelable(false)
            .show()
        lifecycleScope.launch {
            val scan = try { ObdSession.scanCar(this@MainActivity) } catch (e: Exception) { null }
            wait.dismiss()
            if (scan == null) { toast("The car stopped answering. Try again."); return@launch }
            showScan(scan)
        }
    }

    private fun showScan(scan: ObdSession.Scan) {
        val sheet = BottomSheetDialog(this)
        val pad = dp(20f).toInt()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
        fun heading(t: String) = body.addView(TextView(this).apply {
            text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdTextPrimary))
            setPadding(0, dp(16f).toInt(), 0, dp(4f).toInt())
        })
        fun line(t: String, secondary: Boolean = true) = body.addView(TextView(this).apply {
            text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.1f)
            setTextColor(themeColor(if (secondary) R.attr.obdTextSecondary else R.attr.obdTextPrimary)); setPadding(0, dp(3f).toInt(), 0, 0)
        })
        body.addView(TextView(this).apply {
            text = "What your car reports"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary))
        })
        val readings = scan.supported.filter { it % 0x20 != 0 }.sorted()
        line("${readings.size} standard readings, by part of the car, with their value now. Ones without a value aren't read by the app yet.")
        // Grouped like the Graphs tabs; each value updates every second while the sheet is open.
        val values = mutableListOf<Pair<Int, TextView>>()
        val hex = { n: Int -> String.format(Locale.US, "%02X", n) }
        for ((group, keys) in com.obdreader.app.obd.ReadingGroups.sort(readings.map(hex))) {
            heading(group.label)
            for (k in keys) {
                val n = k.toInt(16)
                val value = TextView(this).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextPrimary)); gravity = Gravity.END
                    // A value in words (fuel system status) wraps rather than squeezing the name.
                    maxWidth = (resources.displayMetrics.widthPixels * 0.42f).toInt()
                    text = scanValueText(n).orEmpty()
                }
                values += n to value
                body.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(3f).toInt(), 0, 0)
                    addView(TextView(this@MainActivity).apply {
                        text = String.format(Locale.US, "%02X  %s", n, ObdSession.state.value.relabels[k]?.let { "$it" } ?: CarCoverage.name(n))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.1f)
                        setTextColor(themeColor(if (CarCoverage.shownByApp(n)) R.attr.obdTextPrimary else R.attr.obdTextSecondary))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10f).toInt() })
                })
            }
        }
        // Every other standard reading, folded away: the ones this car doesn't report.
        val notReported = CarCoverage.named.filter { it % 0x20 != 0 && it !in scan.supported }
        if (notReported.isNotEmpty()) {
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            com.obdreader.app.obd.ReadingGroups.sort(notReported.map(hex)).forEach { (_, keys) ->
                keys.forEach { k -> list.addView(TextView(this).apply {
                    text = String.format(Locale.US, "%s  %s", k, CarCoverage.name(k.toInt(16)))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary)); setPadding(0, dp(3f).toInt(), 0, 0)
                }) }
            }
            val title = "Not reported by your car (${notReported.size})"
            body.addView(TextView(this).apply {
                text = "$title  ▸"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdAccent))
                setPadding(0, dp(16f).toInt(), 0, dp(4f).toInt()); minHeight = dp(48f).toInt(); gravity = Gravity.CENTER_VERTICAL
                isClickable = true; isFocusable = true; contentDescription = "$title, collapsed"
                setOnClickListener {
                    val open = list.visibility != View.VISIBLE
                    list.visibility = if (open) View.VISIBLE else View.GONE
                    text = "$title  ${if (open) "▾" else "▸"}"; contentDescription = "$title, ${if (open) "expanded" else "collapsed"}"
                }
            })
            body.addView(list)
        }
        val missing = Pids.dashboard.filter { it.number !in scan.supported }
        if (missing.isNotEmpty()) {
            heading("Not reported by your car")
            missing.forEach { p -> line("${p.label}" + (CarCoverage.STAND_INS.firstOrNull { it.forHex == p.hex && it.pid.number in scan.supported }?.let { " (using ${it.note.substringBefore(",")})" } ?: "")) }
        }
        heading("Battery")
        batteryLines(scan).forEach { (t, found) -> line(t, secondary = !found) }
        if (scan.enhanced.isNotEmpty()) {
            heading("Manufacturer readings (experimental)")
            line("Known readings for your make, tried read-only. An answer means your car has it; send this scan to have it added.")
            scan.enhanced.forEach { (e, data) -> line("${if (data != null) "✓" else "·"}  ${e.name} (${e.request})" + (data?.let { ": $it" } ?: ": no answer"), secondary = data == null) }
        }
        body.addView(MaterialButton(this).apply {
            text = "Share scan"
            setOnClickListener {
                val text = buildString {
                    appendLine("OBD2 Reader car scan, ${java.text.SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(java.util.Date())}")
                    savedCar()?.label?.let { appendLine("Car: $it") }
                    Exports.vinLines(this@MainActivity, ObdSession.state.value.vin).forEach { appendLine(it) }
                    appendLine("Protocol: ${ObdProtocols.byCode(ObdSession.state.value.protocolCode)?.name ?: "unknown"}")
                    appendLine(); appendLine("Standard readings:")
                    readings.forEach { appendLine(String.format(Locale.US, "  %02X  %s", it, CarCoverage.name(it))) }
                    appendLine(); appendLine("Battery:")
                    batteryLines(scan).forEach { (t, _) -> appendLine("  $t") }
                    if (scan.enhanced.isNotEmpty()) {
                        appendLine(); appendLine("Manufacturer readings:")
                        scan.enhanced.forEach { (e, d) -> appendLine("  ${e.request} ${e.name} [${e.header ?: "default"}]: ${d ?: "no answer"}") }
                    }
                }
                val f = Exports.exportFile(this@MainActivity, "OBD2Reader_car_scan.txt").apply { writeText(text) }
                Exports.share(this@MainActivity, f, "text/plain", "OBD2 Reader car scan")
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f).toInt() })
        body.applyTitleFonts()
        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        val ticker = object : Runnable {
            override fun run() {
                // Only values that changed, so the sheet stays quiet for screen readers.
                values.forEach { (n, v) -> scanValueText(n).orEmpty().let { t -> if (v.text.toString() != t) v.text = t } }
                body.postDelayed(this, 1000)
            }
        }
        body.postDelayed(ticker, 1000)
        sheet.setOnDismissListener { body.removeCallbacks(ticker) }
        sheet.show()
        android.util.Log.i("OBD2Scan", "What your car reports: shown, ${values.size} live values")
    }

    /**
     * A standard reading's value now, for What your car reports: from the live readings, the
     * counters since the codes were cleared, or the readiness monitors. "–" while the app hasn't
     * got one yet; null for readings the app doesn't read.
     */
    private fun scanValueText(n: Int): String? {
        val s = ObdSession.state.value
        val k = String.format(Locale.US, "%02X", n)
        if (k in Pids.TEXT_ONLY) return s.readings[k]?.toInt()?.let { v -> if (k == "03") com.obdreader.app.obd.FuelInfo.status(v) else com.obdreader.app.obd.FuelInfo.type(v) } ?: "–"
        Pids.byHex(k)?.let { p ->
            val v = s.readings[k] ?: return if (k in s.unsupported) "N/A" else "–"
            return String.format(Locale.US, "%.${Units.decimals(k, p.decimals)}f", Units.value(k, v)) + Units.unit(k, p.unit).let { if (it.isEmpty()) "" else " $it" }
        }
        // A stand-in arrives under the reading it stands in for.
        CarCoverage.STAND_INS.firstOrNull { it.pid.number == n }?.let { si ->
            if (s.standIns[si.forHex] == null) return null
            val v = s.readings[si.forHex] ?: return "–"
            return String.format(Locale.US, "%.${Units.decimals(si.forHex, si.pid.decimals)}f", Units.value(si.forHex, v)) + Units.unit(si.forHex, si.pid.unit).let { if (it.isEmpty()) "" else " $it" }
        }
        val c = s.cleared ?: return null
        val na = com.obdreader.app.obd.ClearedCheck.NA
        return when (n) {
            0x31 -> c.distanceKm?.let { com.obdreader.app.obd.ClearedCheck.distanceText(it) } ?: na
            0x30 -> c.warmUps?.let { com.obdreader.app.obd.ClearedCheck.warmUpText(it) } ?: na
            0x4E -> c.minutes?.let { com.obdreader.app.obd.ClearedCheck.durationText(it) } ?: na
            0x21 -> c.milDistanceKm?.let { com.obdreader.app.obd.ClearedCheck.distanceText(it) } ?: na
            0x4D -> c.milMinutes?.let { com.obdreader.app.obd.ClearedCheck.durationText(it) } ?: na
            0x01 -> c.readiness?.summary() ?: na
            else -> null
        }
    }

    /** What the scan found about the battery, as (text, found) lines. */
    private fun batteryLines(scan: ObdSession.Scan): List<Pair<String, Boolean>> = buildList {
        add("✓  Battery voltage: measured by the adapter, on every car" to true)
        add("·  12-volt battery current: not part of standard OBD2. Some cars measure it in a maker-specific module, which the app can't read yet, so the Dashboard estimates charging from voltage instead." to false)
        val pack = scan.battery[0x9A]
        add(when {
            0x9A !in scan.supported -> "·  Hybrid/EV battery data (PID 9A): not offered by this car" to false
            pack != null -> "✓  Hybrid/EV battery data (PID 9A): offered. Raw: $pack. Send this scan so it can be decoded for your car." to true
            else -> "·  Hybrid/EV battery data (PID 9A): listed, but no answer" to false
        })
        if (0x5B in scan.supported) {
            val life = scan.battery[0x5B]?.split(" ")?.firstOrNull()?.toIntOrNull(16)?.let { (it * 100.0 / 255).roundToInt() }
            add((if (life != null) "✓  Hybrid battery remaining life: $life%" else "·  Hybrid battery remaining life: listed, but no answer") to (life != null))
        }
    }

    // ---------- Dashboard readouts ----------

    /**
     * Choose the readings in the Dashboard's main readouts: two on the liveries and Race
     * telemetry, three on the Digital cockpit and Cyberpunk. Picking one that's already used swaps them.
     */
    private fun showSlotPicker(chosen: MutableList<String>) {
        placesSheet(
            getString(R.string.slots), null, listOf("Readout 1", "Readout 2", "Readout 3 (Digital cockpit and Cyberpunk)"), chosen,
            { readoutChoices.map { it.key to it.label } }, { readout(it).label },
            save = { keys ->
                if (keys != slots) {
                    prefs.edit().putString(PREF_SLOTS, keys.joinToString(",")).apply()
                    recreate() // rebuilds the Dashboard with the new readouts
                }
            },
            reset = { prefs.edit().remove(PREF_SLOTS).apply(); recreate() },
        )
    }

    // ---------- Performance timers ----------

    /** 0–100 km/h, 80–120 km/h and the quarter mile, timed from the car's speed, with a history. */
    private fun showPerformance() {
        val sheet = BottomSheetDialog(this)
        val timer = PerformanceTimer()
        // 0–100 and 80–120 km/h, or 0–60 and 50–70 mph, plus the quarter mile.
        val offered = PerformanceTimer.Mode.offered(Units.mph)
        var mode = offered.firstOrNull { it.name == prefs.getString(PREF_PERF_MODE, null) } ?: offered.first()
        val pad = dp(20f).toInt()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
        body.addView(TextView(this).apply {
            setText(R.string.perf_title); setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary))
        })
        body.addView(TextView(this).apply {
            setText(R.string.perf_note); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(4f).toInt(), 0, dp(14f).toInt())
        })
        val modes = MaterialButtonToggleGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        val ids = offered.associateWith { View.generateViewId() }
        offered.forEach { m ->
            modes.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = ids.getValue(m); text = m.label; maxLines = 1
                setPaddingRelative(dp(4f).toInt(), paddingTop, dp(4f).toInt(), paddingBottom)
                setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(themeColor(R.attr.obdOnAccent), themeColor(R.attr.obdTextPrimary))))
                backgroundTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(themeColor(R.attr.obdAccent), android.graphics.Color.TRANSPARENT))
                strokeColor = ColorStateList.valueOf(themeColor(R.attr.obdOutline))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        body.addView(modes, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val big = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f); typeface = displayTypeface(); fontFeatureSettings = "tnum"
            setTextColor(themeColor(R.attr.obdTextPrimary)); gravity = Gravity.CENTER; includeFontPadding = false
            setPadding(0, dp(24f).toInt(), 0, dp(4f).toInt())
        }
        val status = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextSecondary)); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14f).toInt())
        }
        // The quarter mile is worked out from speed, so say it's an estimate.
        val estimate = TextView(this).apply {
            setText(R.string.perf_quarter_estimate); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdWarn))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12f).toInt())
        }
        body.addView(big); body.addView(status); body.addView(estimate)
        val again = MaterialButton(this).apply { setText(R.string.perf_again); visibility = View.GONE }
        body.addView(again, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(16f).toInt(), 0, 0) }
        body.addView(history)

        fun results() = PerformanceTimer.decode(prefs.getString(PREF_PERF_HISTORY, null))
        fun fmt(r: PerformanceTimer.Result) = String.format(Locale.US, "%.2f s", r.seconds) + (r.trapSpeed?.let { " at ${Units.speedText(it)}" } ?: "")
        fun renderHistory() {
            history.removeAllViews()
            val mine = results().filter { it.mode == mode }
            if (mine.isEmpty()) return
            val best = mine.minByOrNull { it.seconds }!!
            history.addView(TextView(this).apply {
                text = "Best ${mode.label}: ${fmt(best)}"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdTextPrimary))
            })
            val day = java.text.SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
            mine.sortedByDescending { it.atMs }.take(5).forEach { r ->
                history.addView(TextView(this).apply {
                    text = "${fmt(r)}  ·  ${day.format(java.util.Date(r.atMs))}"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary))
                    setPadding(0, dp(6f).toInt(), 0, 0)
                })
            }
        }
        var readyAt = 0L
        var shownDone = false
        fun show(s: PerformanceTimer.State) {
            when (s) {
                is PerformanceTimer.State.Staging -> { big.text = "—"; status.text = if (ObdSession.state.value.isConnected) s.message else getString(R.string.perf_connect); again.visibility = View.GONE }
                PerformanceTimer.State.Ready -> {
                    // A start-light countdown, then go (the clock starts when the car moves).
                    if (readyAt == 0L) readyAt = android.os.SystemClock.elapsedRealtime()
                    val n = 3 - ((android.os.SystemClock.elapsedRealtime() - readyAt) / 1000).toInt()
                    big.text = if (n > 0) n.toString() else "GO"
                    status.text = if (mode.from != null) "Ready. The clock starts as you pass ${mode.label.substringBefore("–")} ${mode.label.substringAfterLast(" ")}." else "Ready. The clock starts when the car moves."
                }
                is PerformanceTimer.State.Running -> {
                    big.text = String.format(Locale.US, "%.1f", s.seconds)
                    status.text = if (mode == PerformanceTimer.Mode.QUARTER) "${s.metres.roundToInt()} m of 402" else "Timing…"
                }
                is PerformanceTimer.State.Done -> {
                    // Readings keep arriving after the finish; save and show the result only once.
                    if (shownDone) return
                    shownDone = true
                    val r = s.result
                    val previous = results().filter { it.mode == mode }.minOfOrNull { it.seconds }
                    big.text = String.format(Locale.US, "%.2f s", r.seconds)
                    status.text = listOfNotNull(r.trapSpeed?.let { "Trap speed ${Units.speedText(it)}" }, if (previous == null || r.seconds < previous) "New best!" else null).joinToString(" · ").ifEmpty { mode.label }
                    prefs.edit().putString(PREF_PERF_HISTORY, PerformanceTimer.encode((results() + r).takeLast(30))).apply()
                    renderHistory(); again.visibility = View.VISIBLE
                }
                is PerformanceTimer.State.Aborted -> { big.text = "—"; status.text = "${s.reason}. Run again when you're ready."; again.visibility = View.VISIBLE }
            }
        }
        fun arm() {
            timer.arm(mode); readyAt = 0L; shownDone = false
            estimate.visibility = if (mode == PerformanceTimer.Mode.QUARTER) View.VISIBLE else View.GONE
            prefs.edit().putString(PREF_PERF_MODE, mode.name).apply()
            show(timer.state); renderHistory()
        }
        modes.check(ids.getValue(mode))
        modes.addOnButtonCheckedListener { _, id, checked -> if (checked) { mode = ids.entries.first { it.value == id }.key; arm() } }
        again.setOnClickListener { arm() }
        arm()
        val job = lifecycleScope.launch {
            launch { ObdSession.samples.collect { if (it.key == SPEED_KEY) show(timer.update(it.timeMs, it.value)) } }
            launch { while (true) { kotlinx.coroutines.delay(100); if (timer.state is PerformanceTimer.State.Ready || timer.state is PerformanceTimer.State.Staging) show(timer.state) } }
        }
        sheet.setOnDismissListener { job.cancel() }
        body.applyTitleFonts()
        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.show()
    }

    /** A picture of the drive in this style, for Shorts, TikTok, Reels or an Instagram post. */
    private fun shareDriveCard(trip: TripSummary) {
        val formats = DriveCardRenderer.Format.values()
        val card = shareScore(trip)
        val roast = com.obdreader.app.obd.Roast.line(card, trip.startedAt)
        val route = routeFor(trip)
        android.util.Log.i("OBD2Share", "drive card: route of ${route?.points?.size ?: 0} points")
        var format = DriveCardRenderer.Format.STORY
        var preview: android.graphics.Bitmap? = null
        val pad = dp(20f).toInt()
        val image = android.widget.ImageView(this).apply {
            adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            maxHeight = (resources.displayMetrics.heightPixels * 0.5f).toInt()
            contentDescription = getString(R.string.share_preview_title)
        }
        // Tap and hold to see the card big.
        com.obdreader.app.ui.PeekZoom.attach(image) { preview }
        val use = TextView(this).apply {
            setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER; setPadding(0, dp(6f).toInt(), 0, dp(10f).toInt())
        }
        // The card as it'll look, drawn again whenever the shape changes. The roast's space stays blank.
        fun show(f: DriveCardRenderer.Format) {
            format = f
            image.setImageDrawable(null); preview?.recycle()
            preview = DriveCardRenderer(this).render(trip, card, savedCar()?.label, f, route, roast, hideRoast = true)
            image.setImageBitmap(preview)
            use.text = f.label.substringAfter(") ").replaceFirstChar { it.uppercase() }
        }
        val group = com.google.android.material.button.MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true; isSelectionRequired = true
        }
        val ids = formats.associateWith { View.generateViewId() }
        formats.forEach { f ->
            group.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = ids.getValue(f); text = f.label.substringBefore(" for"); isAllCaps = false
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        group.check(ids.getValue(format))
        group.addOnButtonCheckedListener { _, id, checked -> if (checked) formats.firstOrNull { ids[it] == id }?.let { show(it) } }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8f).toInt(), pad, 0)
            addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(use, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        show(format)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.share_picture)
            .setView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
            .setPositiveButton(R.string.share_preview_share) { _, _ -> sendDriveCard(trip, card, format, route, roast) }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { image.setImageDrawable(null); preview?.recycle(); preview = null }
            .show()
    }

    /** Race telemetry shares the Racing score; every other layout the average of Everyday and Efficiency. */
    private fun shareScore(trip: TripSummary) = com.obdreader.app.obd.ShareScore.of(trip.scores, dashLayout() == com.obdreader.app.ui.DashLayout.TELEMETRY)

    private fun sendDriveCard(trip: TripSummary, card: com.obdreader.app.obd.ShareScore, format: DriveCardRenderer.Format, route: com.obdreader.app.obd.Route?, roast: String) {
        val bmp = DriveCardRenderer(this).render(trip, card, savedCar()?.label, format, route, roast)
        val name = "OBD2Reader_drive_" + java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(java.util.Date(trip.startedAt)) + "_${format.name.lowercase(Locale.US)}.png"
        val file = Exports.exportFile(this, name)
        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        Exports.saveToPictures(this, file)
        // The post's text: just the score and the tag. The line on the card stays on the card.
        val score = card.score
        val text = listOfNotNull(score?.let { "My drive: $it/100 ${card.kind.label.lowercase(Locale.getDefault())} score." }, "#OBD2Reader").joinToString(" ")
        Exports.share(this, file, "image/png", "My drive", text = text)
    }

    /**
     * Same route as before? When [trip] was recorded with its route and an earlier recorded drive
     * followed the same roads the same way round, the summary shows how this one compared: time,
     * average speed, idling, top speed and score. Both routes are read from their own recordings
     * (the only place location is kept) and nothing new is saved.
     */
    private fun addRouteComparison(body: LinearLayout, trip: TripSummary) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        body.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val mineFiles = tripFiles(trip)
                    if (mineFiles.isEmpty()) return@withContext null
                    val mineLines = mineFiles.map { it.readLines() }
                    val mine = com.obdreader.app.obd.Route.join(mineLines.map { com.obdreader.app.obd.Route.parse(it) })
                    if (mine.isEmpty) return@withContext null
                    val mineStats = com.obdreader.app.obd.RouteCompare.Stats.join(mineLines.mapNotNull { com.obdreader.app.obd.RouteCompare.Stats.parse(it) })
                    val history = tripHistory()
                    LogRecorder.saved(this@MainActivity).asSequence()
                        // A very long recording (hours) isn't read just to compare routes.
                        .filter { it !in mineFiles && (fileStart(it) ?: Long.MAX_VALUE) < trip.startedAt && it.length() <= COMPARE_MAX_BYTES }
                        .take(60)
                        .mapNotNull { f ->
                            val lines = f.readLines()
                            val route = com.obdreader.app.obd.Route.parse(lines)
                            if (!com.obdreader.app.obd.RouteCompare.sameRoute(mine, route)) return@mapNotNull null
                            val stats = com.obdreader.app.obd.RouteCompare.Stats.parse(lines) ?: return@mapNotNull null
                            val start = fileStart(f) ?: f.lastModified()
                            // The scores belong to whole drives: only compared when each recording covered its drive.
                            fun score(t: TripSummary?, st: com.obdreader.app.obd.RouteCompare.Stats) =
                                t?.takeIf { st.seconds >= it.connectedSeconds * 0.8 }?.let { com.obdreader.app.obd.ShareScore.of(it.scores, raceTelemetry = false).score }
                            val before = history.firstOrNull { start in (it.startedAt - 60_000)..it.endedAt }
                            start to com.obdreader.app.obd.RouteCompare.compare(mineStats, stats, score(trip, mineStats), score(before, stats))
                        }
                        .firstOrNull()
                } catch (e: Exception) { android.util.Log.w("OBD2Route", "compare: ${e.message}"); null
                } catch (e: OutOfMemoryError) { android.util.Log.w("OBD2Route", "compare: too big to read"); null }
            } ?: return@launch
            val (foundAt, lines) = result
            android.util.Log.i("OBD2Route", "same route as the drive of $foundAt: ${lines.joinToString { it.label + " " + it.change }}")
            val day = java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault())
            val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            box.addView(TextView(this@MainActivity).apply {
                setText(R.string.same_route_title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdTextPrimary))
                setPadding(0, dp(16f).toInt(), 0, dp(2f).toInt())
            })
            box.addView(TextView(this@MainActivity).apply {
                text = getString(R.string.same_route_since, "${day.format(java.util.Date(foundAt))}, ${time.format(java.util.Date(foundAt))}")
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary))
                setPadding(0, 0, 0, dp(8f).toInt())
            })
            val table = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; background = tripTile()
                setPadding(dp(14f).toInt(), dp(6f).toInt(), dp(14f).toInt(), dp(6f).toInt())
            }
            lines.forEach { line ->
                table.addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(7f).toInt(), 0, dp(7f).toInt())
                    addView(TextView(context).apply { text = line.label; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary)) },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(context).apply {
                        text = line.now; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = uiTypeface(600); fontFeatureSettings = "tnum"
                        setTextColor(themeColor(R.attr.obdTextPrimary)); setPadding(dp(8f).toInt(), 0, dp(12f).toInt(), 0)
                    })
                    addView(TextView(context).apply {
                        text = line.change; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); fontFeatureSettings = "tnum"; gravity = Gravity.END
                        setTextColor(themeColor(when (line.better) { true -> R.attr.obdOk; false -> R.attr.obdDanger; null -> R.attr.obdTextSecondary }))
                    }, LinearLayout.LayoutParams(dp(118f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
                })
            }
            box.addView(table, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            box.visibility = View.VISIBLE
        }
    }

    /**
     * The route of [trip], from the recording(s) made during it with the route saved. Location
     * lives only in those files, so with no recording there's simply no outline.
     */
    private fun routeFor(trip: TripSummary): com.obdreader.app.obd.Route? = try {
        com.obdreader.app.obd.Route.join(tripFiles(trip).map { com.obdreader.app.obd.Route.parse(it.readLines()) }).takeIf { !it.isEmpty }
    } catch (e: OutOfMemoryError) { null }

    /** When a recording started, from its name. */
    private fun fileStart(f: java.io.File): Long? = try {
        java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).parse(f.nameWithoutExtension.removePrefix("OBD2Reader_"))?.time
    } catch (e: Exception) { null }

    /** The recordings made during [trip], in order. */
    private fun tripFiles(trip: TripSummary): List<java.io.File> = LogRecorder.saved(this)
        .filter { f -> val start = fileStart(f) ?: return@filter false; start <= trip.endedAt && f.lastModified() >= trip.startedAt }
        .sortedBy { it.name }

    /** A tile one shade lighter than the sheet behind it. */
    private fun tripTile() = GradientDrawable().apply {
        setColor(themeColor(R.attr.obdSurfaceHigh))
        cornerRadius = themeDimen(R.attr.obdCardRadius)
    }

    private fun scoreColour(score: Int): Int = when {
        score >= 80 -> themeColor(R.attr.obdOk)
        score >= 55 -> themeColor(R.attr.obdWarn)
        else -> themeColor(R.attr.obdDanger)
    }

    // ---------- Graphs ----------

    /** The channels that can be graphed, with a colour each so stacked charts are easy to tell apart. */
    private data class Channel(val key: String, val label: String, val unit: String, val decimals: Int, val min: Double, val max: Double)

    private val channels: List<Channel> by lazy {
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
    private val graphChosen = mutableListOf<String>()
    private lateinit var graphChipGroup: ChipGroup
    private lateinit var graphCount: TextView

    private fun setupGraphs() {
        graphList = findViewById(R.id.graphList)
        graphRate = findViewById(R.id.graphRate)
        recordBtn = findViewById(R.id.recordButton)
        recordStatus = findViewById(R.id.recordStatus)
        LiveHistory.start()

        // Only the chosen graphs show as chips; the rest wait in the "Add a graph" sheet, grouped by
        // what part of the car they come from, so the tab stays tidy however many readings a car reports.
        graphChipGroup = findViewById(R.id.graphChips)
        // Tabs over the chips: All shows the chosen graphs and "+ Add a graph"; each part of the
        // car shows its own readings as chips to tap on or off.
        graphTab = prefs.getString(PREF_GRAPH_TAB, null)?.let { n -> com.obdreader.app.obd.ReadingGroups.Group.entries.firstOrNull { it.name == n } }
        graphTabs = com.google.android.material.tabs.TabLayout(this).apply {
            tabMode = com.google.android.material.tabs.TabLayout.MODE_SCROLLABLE
            tabGravity = com.google.android.material.tabs.TabLayout.GRAVITY_START
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTabTextColors(themeColor(R.attr.obdTextSecondary), themeColor(R.attr.obdTextPrimary))
            setSelectedTabIndicatorColor(themeColor(R.attr.obdAccent))
            tabRippleColor = null
        }
        (graphChipGroup.parent as ViewGroup).let { parent ->
            parent.addView(graphTabs, parent.indexOfChild(graphChipGroup),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10f).toInt() })
        }
        (graphChipGroup.layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(10f).toInt()
        graphCount = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary))
        }
        (graphChipGroup.parent as ViewGroup).let { parent ->
            parent.addView(graphCount, parent.indexOfChild(graphChipGroup) + 1,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f).toInt() })
        }
        graphChosen += (prefs.getString(PREF_GRAPHS, null) ?: "0C,0D,11").split(",").filter { k -> k.isNotEmpty() && channels.any { it.key == k } }.distinct()
        if (graphChosen.isEmpty()) graphChosen += RPM_KEY
        graphOrderBtn = findViewById(R.id.graphOrder)
        graphOrderBtn.setOnClickListener { showGraphOrder(graphChosen) }
        findViewById<MaterialButtonToggleGroup>(R.id.graphWindow).apply {
            check(R.id.window30)
            addOnButtonCheckedListener { _, id, checked ->
                if (!checked) return@addOnButtonCheckedListener
                graphWindowMs = when (id) { R.id.window10 -> 10_000L; R.id.window60 -> 60_000L; else -> 30_000L }
                graphStrips.forEach { it.windowMs = graphWindowMs }
            }
        }
        val pause = findViewById<MaterialButton>(R.id.graphPause)
        pause.setOnClickListener {
            graphsPaused = !graphsPaused
            pause.setText(if (graphsPaused) R.string.resume else R.string.pause)
            graphStrips.forEach { it.paused = graphsPaused }
        }
        recordBtn.text = getString(R.string.record_drive)
        recordBtn.setOnClickListener {
            if (LogRecorder.active.value != null) LogRecorder.stop() else startRecording()
        }
        findViewById<View>(R.id.savedLogsButton).setOnClickListener { showSavedDrives() }
        buildGraphs(graphChosen)
    }

    private lateinit var graphOrderBtn: MaterialButton

    private fun graphLabel(key: String): String =
        ObdSession.state.value.relabels[key] ?: channels.firstOrNull { it.key == key }?.label ?: key

    private fun graphColour(key: String): Int = when (key) {
        RPM_KEY -> Channels.RPM
        SPEED_KEY -> Channels.SPEED
        else -> Channels.forKey(key, themeColor(R.attr.obdAccent))
    }

    /**
     * What can be graphed: once the car has said which readings it supports, only the extra readings
     * it reports (and any already chosen, which say N/A). Until then, or if the car won't say, all.
     */
    private fun graphChoices(supported: Set<Int>?): List<Channel> {
        val extraKeys = Pids.extraNumbers.map { it.hex }.toSet()
        return channels.filter { ch -> ch.key !in extraKeys || supported == null || ch.key.toInt(16) in supported || ch.key in graphChosen }
    }

    /** The Graphs tab's reading tabs: null is All. */
    private var graphTab: com.obdreader.app.obd.ReadingGroups.Group? = null
    private lateinit var graphTabs: com.google.android.material.tabs.TabLayout

    /** All, then each part of the car this car has readings for. Kept selected across visits. */
    private fun showGraphTabs(groups: List<com.obdreader.app.obd.ReadingGroups.Group>) {
        val names = listOf<com.obdreader.app.obd.ReadingGroups.Group?>(null) + groups
        val same = graphTabs.tabCount == names.size && names.indices.all { graphTabs.getTabAt(it)?.tag == names[it] }
        if (graphTab !in groups) graphTab = null
        if (!same) {
            graphTabs.clearOnTabSelectedListeners()
            graphTabs.removeAllTabs()
            for (g in names) graphTabs.addTab(graphTabs.newTab().apply {
                tag = g; text = g?.short ?: getString(R.string.graphs_all)
            }, g == graphTab)
            graphTabs.getTabAt(names.indexOf(graphTab))?.select()
            graphTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                    graphTab = tab.tag as com.obdreader.app.obd.ReadingGroups.Group?
                    prefs.edit().putString(PREF_GRAPH_TAB, graphTab?.name).apply()
                    showGraphChips()
                }
                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            })
        }
    }

    /**
     * Under the tabs. All: the chosen graphs as chips with a remove cross, then "+ Add a graph".
     * A part of the car: its readings as chips, the graphed ones ticked. Then how many of how many.
     */
    private fun showGraphChips() {
        val choices = graphChoices(ObdSession.state.value.supported)
        val groups = com.obdreader.app.obd.ReadingGroups.sort(choices.map { it.key })
        showGraphTabs(groups.map { it.first })
        graphChipGroup.removeAllViews()
        graphCount.text = getString(R.string.graphs_count, graphChosen.size, MAX_GRAPHS, choices.size)
        val tab = graphTab
        if (tab != null) {
            for (k in groups.firstOrNull { it.first == tab }?.second.orEmpty()) {
                graphChipGroup.addView(Chip(this).apply {
                    text = graphLabel(k)
                    isCheckable = true
                    isChecked = k in graphChosen
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(if (isChecked) graphColour(k) else themeColor(R.attr.obdOutline))
                    setOnClickListener { if (!toggleGraph(k)) isChecked = k in graphChosen }
                })
            }
            return
        }
        for (k in graphChosen) {
            val label = graphLabel(k)
            graphChipGroup.addView(Chip(this).apply {
                text = label
                isCloseIconVisible = true
                closeIconContentDescription = getString(R.string.graphs_remove, label)
                setOnCloseIconClickListener { toggleGraph(k) }
                setOnClickListener { showAddGraph() }
            })
        }
        graphChipGroup.addView(Chip(this).apply {
            text = getString(R.string.graphs_add)
            setTextColor(themeColor(R.attr.obdAccent))
            chipStrokeColor = android.content.res.ColorStateList.valueOf(themeColor(R.attr.obdAccent))
            setOnClickListener { showAddGraph() }
        })
    }

    /** Adds or removes one graph, up to [MAX_GRAPHS] and at least one. Returns whether it changed. */
    private fun toggleGraph(key: String): Boolean {
        if (key in graphChosen) {
            if (graphChosen.size == 1) { toast(getString(R.string.graphs_keep_one)); return false }
            graphChosen -= key
        } else {
            if (graphChosen.size >= MAX_GRAPHS) { toast(getString(R.string.graphs_max, MAX_GRAPHS)); return false }
            graphChosen += key
        }
        prefs.edit().putString(PREF_GRAPHS, graphChosen.joinToString(",")).apply()
        buildGraphs(graphChosen)
        return true
    }

    /** A reading's value now, in the user's units, for the Add a graph sheet. */
    private fun graphValueText(ch: Channel): String {
        val s = ObdSession.state.value
        if (!s.isConnected) return ""
        if (ch.key in s.unsupported) return com.obdreader.app.obd.ClearedCheck.NA
        val v = s.readings[ch.key]?.let { Units.value(ch.key, it) } ?: return "–"
        return String.format(Locale.US, "%.${ch.decimals}f", v) + (if (ch.unit.isEmpty()) "" else " ${ch.unit}")
    }

    /**
     * "Add a graph": every reading the car reports, grouped by what part of the car it comes from,
     * with its value now. Tapping a row adds or removes its graph straight away.
     */
    private fun showAddGraph() {
        val values = mutableListOf<Pair<Channel, TextView>>()
        val ticker = object : Runnable {
            override fun run() {
                values.forEach { (ch, v) -> v.text = graphValueText(ch) }
                graphChipGroup.postDelayed(this, 1000)
            }
        }
        val sheet = choiceSheet(getString(R.string.graphs_add_title)) { body, open ->
            sheetNote(body, getString(R.string.graphs_add_sub, MAX_GRAPHS))
            val byKey = graphChoices(ObdSession.state.value.supported).associateBy { it.key }
            for ((group, keys) in com.obdreader.app.obd.ReadingGroups.sort(byKey.keys)) {
                sheetHeading(body, group.label)
                for (k in keys) {
                    val ch = byKey.getValue(k)
                    val label = graphLabel(k)
                    val check = com.google.android.material.checkbox.MaterialCheckBox(this).apply {
                        isChecked = k in graphChosen; isClickable = false; isFocusable = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    val value = TextView(this).apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary))
                        text = graphValueText(ch)
                    }
                    values += ch to value
                    body.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = dp(48f).toInt()
                        setPadding(dp(4f).toInt(), 0, 0, 0)
                        isClickable = true; isFocusable = true
                        background = android.util.TypedValue().let { tv -> theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true); androidx.core.content.ContextCompat.getDrawable(context, tv.resourceId) }
                        setAccessibilityDelegate(object : View.AccessibilityDelegate() {
                            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                                super.onInitializeAccessibilityNodeInfo(host, info)
                                info.isCheckable = true; info.isChecked = check.isChecked
                                info.className = android.widget.CheckBox::class.java.name
                            }
                        })
                        contentDescription = label
                        addView(View(this@MainActivity).apply {
                            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(graphColour(k)) }
                        }, LinearLayout.LayoutParams(dp(10f).toInt(), dp(10f).toInt()).apply { marginEnd = dp(12f).toInt() })
                        addView(TextView(this@MainActivity).apply {
                            text = label; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(themeColor(R.attr.obdTextPrimary))
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8f).toInt() })
                        addView(check)
                        setOnClickListener { if (toggleGraph(k)) check.isChecked = k in graphChosen }
                    })
                }
            }
            sheetButtons(body, getString(R.string.done) to { open.dismiss() })
        }
        ticker.run()
        sheet.setOnDismissListener { graphChipGroup.removeCallbacks(ticker) }
    }

    /** The graphs' names, hints and N/A for this car: oxygen sensor names, fuel system status under the fuel trims. */
    private fun refreshGraphNotes(s: ObdSession.State) {
        val status = s.readings["03"]?.let { com.obdreader.app.obd.FuelInfo.status(it.toInt()) }
        graphStrips.forEach { g ->
            channels.firstOrNull { it.key == g.key }?.let { ch -> g.label = s.relabels[g.key] ?: ch.label }
            val about = Pids.about(g.key)
            val isTrim = g.key in setOf("06", "07", "08", "09")
            g.hint = when {
                about == null -> null
                isTrim && status != null -> "$about Now: ${status.replaceFirstChar { it.lowercase() }}."
                else -> about
            }
            g.notReported = s.isConnected && g.key in s.unsupported
        }
    }

    /** Lets the user move the chosen graphs up and down; saved straight away. */
    private fun showGraphOrder(chosen: MutableList<String>) {
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12f).toInt(), dp(4f).toInt(), dp(12f).toInt(), 0) }
        fun label(k: String) = channels.first { it.key == k }.label
        fun refresh() {
            list.removeAllViews()
            chosen.forEachIndexed { i, k ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(TextView(this).apply {
                    text = "${i + 1}.  ${label(k)}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(themeColor(R.attr.obdTextPrimary))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                fun move(by: Int, glyph: String, word: String) = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                    text = glyph
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    contentDescription = "Move ${label(k)} $word"
                    minWidth = dp(48f).toInt(); minimumWidth = dp(48f).toInt()
                    val j = i + by
                    isEnabled = j in chosen.indices
                    setOnClickListener {
                        java.util.Collections.swap(chosen, i, j)
                        prefs.edit().putString(PREF_GRAPHS, chosen.joinToString(",")).apply()
                        buildGraphs(chosen)
                        refresh()
                    }
                }
                row.addView(move(-1, "▲", "up"))
                row.addView(move(1, "▼", "down"))
                list.addView(row)
            }
        }
        refresh()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.graph_order_title)
            .setMessage(R.string.graph_order_hint)
            .setView(list)
            .setPositiveButton(R.string.done, null)
            .show()
    }

    private var draggedGraph: View? = null

    /**
     * Tap and hold a graph, then drag it up or down to move it. The others slide out of its way,
     * and the new order is saved when you let go. Also offered to screen readers as Move up / down.
     */
    private fun enableGraphDrag(strip: View, keys: MutableList<String>, label: String) {
        var lastY = 0f
        var waitForLayout = false
        fun save() {
            prefs.edit().putString(PREF_GRAPHS, keys.joinToString(",")).apply()
            android.util.Log.i("OBD2Graphs", "order: ${keys.joinToString(",")}")
            graphStrips.sortBy { graphList.indexOfChild(it) }
        }
        fun swapWith(by: Int) {
            val i = graphList.indexOfChild(strip); val j = i + by
            val other = graphList.getChildAt(j) ?: return
            val shift = (other.height + (other.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin).toFloat()
            // Move the neighbour, not the graph under the finger, so the finger keeps hold of it.
            graphList.removeView(other)
            graphList.addView(other, i)
            java.util.Collections.swap(keys, i, j)
            strip.translationY -= by * shift
            other.translationY = by * shift
            other.animate().translationY(0f).setDuration(160).start()
            waitForLayout = true
            graphList.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, l0: Int, t0: Int, r0: Int, b0: Int) {
                    waitForLayout = false; graphList.removeOnLayoutChangeListener(this)
                }
            })
            graphList.requestLayout()
        }
        strip.isLongClickable = true
        strip.setOnLongClickListener {
            if (keys.size < 2) return@setOnLongClickListener false
            draggedGraph = strip
            strip.parent?.requestDisallowInterceptTouchEvent(true)
            strip.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            strip.animate().scaleX(1.03f).scaleY(1.03f).translationZ(dp(8f)).setDuration(120).start()
            true
        }
        strip.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastY = e.rawY; false }
                MotionEvent.ACTION_MOVE -> {
                    if (draggedGraph !== v) { lastY = e.rawY; return@setOnTouchListener false }
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    v.translationY += e.rawY - lastY; lastY = e.rawY
                    if (!waitForLayout) {
                        val i = graphList.indexOfChild(v)
                        val mid = v.top + v.translationY + v.height / 2f
                        val below = graphList.getChildAt(i + 1)
                        val above = if (i > 0) graphList.getChildAt(i - 1) else null
                        when {
                            below != null && mid > below.top + below.height / 2f -> swapWith(1)
                            above != null && mid < above.top + above.height / 2f -> swapWith(-1)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (draggedGraph !== v) return@setOnTouchListener false
                    draggedGraph = null
                    v.animate().translationY(0f).scaleX(1f).scaleY(1f).translationZ(0f).setDuration(160).start()
                    save()
                    true
                }
                else -> draggedGraph === v
            }
        }
        // Screen readers: the same moves as actions on the graph.
        ViewCompat.addAccessibilityAction(strip, "Move $label up") { _, _ ->
            val i = keys.indexOf(strip.tag as? String ?: return@addAccessibilityAction false)
            if (i <= 0) return@addAccessibilityAction false
            java.util.Collections.swap(keys, i, i - 1); prefs.edit().putString(PREF_GRAPHS, keys.joinToString(",")).apply(); buildGraphs(keys); true
        }
        ViewCompat.addAccessibilityAction(strip, "Move $label down") { _, _ ->
            val i = keys.indexOf(strip.tag as? String ?: return@addAccessibilityAction false)
            if (i < 0 || i >= keys.size - 1) return@addAccessibilityAction false
            java.util.Collections.swap(keys, i, i + 1); prefs.edit().putString(PREF_GRAPHS, keys.joinToString(",")).apply(); buildGraphs(keys); true
        }
    }

    private fun buildGraphs(keys: List<String>) {
        graphList.removeAllViews()
        graphStrips.clear()
        if (::graphOrderBtn.isInitialized) graphOrderBtn.visibility = if (keys.size >= 2) View.VISIBLE else View.GONE
        if (::graphCount.isInitialized) showGraphChips()
        android.util.Log.i("OBD2Graphs", "order: ${keys.joinToString(",")}")
        val fallback = themeColor(R.attr.obdAccent)
        for (ch in keys.mapNotNull { k -> channels.firstOrNull { it.key == k } }) {
            val colour = when (ch.key) {
                RPM_KEY -> Channels.RPM
                SPEED_KEY -> Channels.SPEED
                else -> Channels.forKey(ch.key, fallback)
            }
            val strip = GraphStripView(this, ch.key, ch.label, ch.unit, ch.decimals, ch.min, ch.max, colour).apply {
                tag = ch.key
                windowMs = graphWindowMs
                paused = graphsPaused
            }
            graphList.addView(strip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8f).toInt()
            })
            graphStrips += strip
            if (keys is MutableList<String>) enableGraphDrag(strip, keys, ch.label)
        }
        refreshGraphNotes(ObdSession.state.value)
        updateFocus()
    }

    /** While the Graphs tab is open, the graphed readings are polled first, so they update as fast as possible. */
    private fun updateFocus() {
        ObdSession.focus = when {
            // G-force isn't read from the car; it needs the car's speed instead.
            pageGraphs.visibility == View.VISIBLE -> graphStrips.map { it.key }.toSet().let { keys ->
                if (keys.none { it in GForce.KEYS }) keys else keys - GForce.KEYS + SPEED_KEY
            }
            // The Dashboard's moving readings, in one request per cycle (six is a CAN car's limit),
            // so the dials update as often as the graphs do.
            // An extra reading picked for a readout joins them, since it isn't otherwise read every turn.
            pageDashboard.visibility == View.VISIBLE -> DASHBOARD_FOCUS + slots.filter { k -> Pids.extra.any { it.hex == k } }
            else -> emptySet()
        }
    }

    private fun renderRecording(r: LogRecorder.Recording?) {
        recordBtn.isEnabled = r != null || ObdSession.state.value.isConnected
        showRecordGroup(ObdSession.state.value.isConnected)
        recordBtn.text = getString(if (r == null) R.string.record_drive else R.string.stop_recording)
        recordBtn.kind = if (r == null) SkewButton.Kind.PRIMARY else SkewButton.Kind.DANGER
        recordStatus.text = if (r == null) getString(R.string.record_hint) else {
            val secs = (System.currentTimeMillis() - r.startedMs) / 1000
            String.format(Locale.US, "Recording %d:%02d, %,d rows%s. Saved as %s", secs / 60, secs % 60, r.rows, if (r.withLocation) ", with route" else "", r.file.name)
        }
    }

    /** Record drive lives on the Dashboard, shown only while connected (or recording, so it can be stopped). */
    private fun showRecordGroup(connected: Boolean) {
        val show = connected || LogRecorder.active.value != null
        val v = findViewById<View>(R.id.recordGroup)
        val want = if (show) View.VISIBLE else View.GONE
        if (v.visibility != want) v.visibility = want
    }

    /** Lists recorded drives: tap one to share it, or delete it. */
    private fun showSavedDrives() {
        val files = LogRecorder.saved(this)
        if (files.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.saved_drives)
                .setMessage("No drives recorded yet. Connect, then tap Record drive on the Dashboard.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val names = files.map { f ->
            val kb = (f.length() + 1023) / 1024
            "${driveLabel(f)}, $kb KB"
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.saved_drives)
            .setItems(names) { _, i -> showDriveActions(files[i]) }
            .setNegativeButton("Close", null)
            .show()
    }

    /** "19 Oct 2026, 10:37 pm" from a recording's file name (OBD2Reader_2026-10-19_223705.csv). */
    private fun driveLabel(f: java.io.File): String {
        val stamp = f.nameWithoutExtension.removePrefix("OBD2Reader_")
        val parsed = listOf("yyyy-MM-dd_HHmmss", "yyyy-MM-dd_HHmm").firstNotNullOfOrNull { pattern ->
            try { java.text.SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(stamp) } catch (e: java.text.ParseException) { null }
        }
        return parsed?.let { java.text.SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(it) } ?: stamp.replace('_', ' ')
    }

    private fun showDriveActions(file: java.io.File) {
        val actions = listOf(
            "Make a video overlay" to { showOverlayOptions(file) },
            "Share the data (CSV)" to { Exports.share(this, file, "text/csv", "OBD2 Reader drive ${file.nameWithoutExtension}") },
            "Delete" to {
                if (LogRecorder.active.value?.file == file) {
                    toast("This drive is still recording. Stop recording first.")
                } else {
                    file.delete()
                    toast("Deleted")
                }
            },
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(driveLabel(file))
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Video overlay ----------

    private var overlayJob: kotlinx.coroutines.Job? = null

    /** Background and size for the overlay video, then makes it. */
    private fun showOverlayOptions(file: java.io.File) {
        if (LogRecorder.active.value?.file == file) { toast("This drive is still recording. Stop recording first."); return }
        val drive = try { DriveTrack.parse(file.readLines()) } catch (e: OutOfMemoryError) { toast("This drive is too long to make a video on this phone."); return }
        if (drive == null || drive.duration < 2.0) { toast("This drive is too short to make a video."); return }
        val backgrounds = listOf(
            Triple("Green screen", "Remove it with your editor's chroma key (CapCut, Premiere, DaVinci Resolve…)", android.graphics.Color.rgb(0, 255, 0)),
            Triple("Black", "Use the Screen blend mode to drop the black", android.graphics.Color.BLACK),
        )
        var bg = 0
        var size = 0
        val view = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24f).toInt(), dp(8f).toInt(), dp(24f).toInt(), 0) }
        view.addView(TextView(this).apply {
            text = "Your readings as a video to lay over dashcam or GoPro footage: rpm, speed, shift lights, throttle and a 30-second trace, in your style's colours. ${TripSummary.duration(drive.duration.toInt())} long, 30 frames a second."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdTextSecondary)); setLineSpacing(0f, 1.15f)
        })
        fun group(title: String, options: List<Pair<String, String?>>, onPick: (Int) -> Unit) {
            view.addView(TextView(this).apply {
                text = title; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdTextPrimary))
                setPadding(0, dp(16f).toInt(), 0, dp(2f).toInt())
            })
            val radios = android.widget.RadioGroup(this)
            options.forEachIndexed { i, (name, hint) ->
                radios.addView(com.google.android.material.radiobutton.MaterialRadioButton(this).apply {
                    id = View.generateViewId()
                    text = if (hint == null) name else "$name\n$hint"
                    isChecked = i == 0
                    setOnCheckedChangeListener { _, on -> if (on) onPick(i) }
                })
            }
            view.addView(radios)
        }
        group("Background", backgrounds.map { it.first to it.second }) { bg = it }
        group("Size", OverlayExporter.Size.values().map { it.label to null }) { size = it }
        MaterialAlertDialogBuilder(this)
            .setTitle("Video overlay")
            .setView(androidx.core.widget.NestedScrollView(this).apply { addView(view) })
            .setPositiveButton("Make video") { _, _ -> makeOverlay(file, drive, backgrounds[bg].third, OverlayExporter.Size.values()[size]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun makeOverlay(file: java.io.File, drive: DriveTrack, background: Int, size: OverlayExporter.Size) {
        val out = Exports.exportFile(this, file.nameWithoutExtension + "_overlay.mp4")
        val progress = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply { max = 1000; isIndeterminate = false }
        val label = TextView(this).apply { text = "Starting…"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary)) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24f).toInt(), dp(12f).toInt(), dp(24f).toInt(), 0)
            addView(progress); addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f).toInt() })
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Making your video")
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> overlayJob?.cancel() }
            .show()
        val started = android.os.SystemClock.elapsedRealtime()
        overlayJob = lifecycleScope.launch {
            try {
                OverlayExporter.export(this@MainActivity, drive, out, background, size, redline, roundel.max) { f ->
                    progress.progress = (f * 1000).toInt()
                    val spent = (android.os.SystemClock.elapsedRealtime() - started) / 1000.0
                    val left = if (f > 0.02f) (spent / f * (1 - f)).toInt() else null
                    label.text = "${(f * 100).toInt()}%" + (left?.let { " · about ${TripSummary.duration(it.coerceAtLeast(1))} left" } ?: "")
                }
                dialog.dismiss()
                val saved = OverlayExporter.saveToMovies(this@MainActivity, out)
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Video ready")
                    .setMessage(if (saved != null) "Saved to Movies › OBD2 Reader, where your video editor can find it." else "Share it to your video editor, Drive or Files to keep it.")
                    .setPositiveButton(R.string.share) { _, _ -> Exports.share(this@MainActivity, out, "video/mp4", "OBD2 Reader video overlay") }
                    .setNegativeButton(R.string.done, null)
                    .show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                dialog.dismiss(); out.delete()
                throw e
            } catch (e: Exception) {
                dialog.dismiss(); out.delete()
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Couldn't make the video")
                    .setMessage("This phone's video encoder refused the job (${e.message ?: e.javaClass.simpleName}). Try the 720p size.")
                    .setPositiveButton(R.string.done, null)
                    .show()
            } finally {
            }
        }
    }

    /** Shows the chosen style's dashboard and builds its readings. */
    private fun setupDashboard() {
        dash = dashLayout()
        val livery = dash == DashLayout.LIVERY
        findViewById<View>(R.id.layoutLivery).visibility = if (livery) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutTelemetry).visibility = if (dash == DashLayout.TELEMETRY) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutCockpit).visibility = if (dash == DashLayout.COCKPIT) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutCyberpunk).visibility = if (dash == DashLayout.CYBERPUNK) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutRetro).visibility = if (dash == DashLayout.RETRO) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutRally).visibility = if (dash == DashLayout.RALLY) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutHairline).visibility = if (dash == DashLayout.HAIRLINE) View.VISIBLE else View.GONE
        // Livery stripes run across the top of each page; the title sits below them.
        listOf(R.id.stripesDash, R.id.stripesDiag, R.id.stripesGraphs).forEach { findViewById<LiveryStripesView>(it).visibility = if (livery) View.VISIBLE else View.GONE }
        if (livery) {
            findViewById<View>(R.id.topBar).setPadding(0, dp(92f).toInt(), 0, 0)
            findViewById<View>(R.id.titleDiagnostics).setPadding(0, dp(92f).toInt(), 0, 0)
            findViewById<View>(R.id.titleGraphs).setPadding(0, dp(92f).toInt(), 0, 0)
        }
        if (dash == DashLayout.COCKPIT) {
            findViewById<View>(R.id.root).background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(android.graphics.Color.parseColor("#13203F"), android.graphics.Color.parseColor("#070A12"), themeColor(R.attr.obdBg))
                gradientRadius = resources.displayMetrics.heightPixels * 0.7f
                setGradientCenter(0.5f, 0.15f)
            }
        }
        if (dash == DashLayout.CYBERPUNK) {
            // A magenta haze over the top of the night-city black.
            findViewById<View>(R.id.root).background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(android.graphics.Color.parseColor("#2A0A3A"), android.graphics.Color.parseColor("#0B0716"), themeColor(R.attr.obdBg))
                gradientRadius = resources.displayMetrics.heightPixels * 0.6f
                setGradientCenter(0.5f, 0f)
            }
        }
        listOf(R.id.carRow, R.id.protocolRow, R.id.logCard, R.id.helpRow, R.id.reportRow, R.id.ideaRow, R.id.updatesRow).forEach { findViewById<View>(it).background = panelBackground() }
        val vinDecal = findViewById<VinDecalView>(R.id.vinDecal)
        vinText.setTextColor(vinDecal.inkColor)
        findViewById<TextView>(R.id.vinLabel).setTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(vinDecal.inkColor, 180))

        roundel = findViewById(R.id.rpmRoundel)
        startLights = findViewById(R.id.startLights)
        shiftLights = findViewById(R.id.shiftLights)
        trace = findViewById(R.id.teleTrace)
        gMeter = findViewById(R.id.teleGMeter)
        gMeter.follow = trace
        teleRpm = findViewById(R.id.teleRpm)
        teleSpeed = findViewById(R.id.teleSpeed)
        (teleRpm as FitTextView).sample = "8,888"
        (teleSpeed as FitTextView).sample = "888"
        (findViewById<View>(R.id.teleCoolant) as FitTextView).sample = "Coolant     888 °C"
        teleCoolant = findViewById(R.id.teleCoolant)
        teleFuel = findViewById(R.id.teleFuel)
        cockpitRing = findViewById(R.id.cockpitRing)
        cyberHud = findViewById(R.id.cyberHud)
        retroDash = findViewById(R.id.retroDash)
        rallyRev = findViewById(R.id.rallyRev)
        hairlineDial = findViewById(R.id.hairlineDial)
        retroDash.speedUnit = Units.speedUnit
        hairlineDial.speedUnit = Units.speedUnit
        val blank = getString(R.string.dash)
        teleRpmText = SmoothText(teleRpm, blank, settle = 2f) { String.format(Locale.US, "%,d", ((it / 10f).roundToInt() * 10)) }
        teleSpeedText = SmoothText(teleSpeed, blank) { it.roundToInt().toString() }
        // The two readouts under the trace: the first two chosen readings.
        listOf(teleCoolant to slots[0], teleFuel to slots[1]).forEach { (view, key) ->
            val r = readout(key)
            val gap = if (r.label.length > 8) "   " else "     "
            (view as FitTextView).sample = "${r.label}${gap}888.8 ${r.unit}"
            teleLabels[key] = "${r.label}$gap"
            if (key == VOLTAGE_KEY) (view as FitTextView).sample = "Battery · charging weakly 88.8 ${r.unit}"
            teleTexts[key] = SmoothText(view, "${r.label}$gap$blank ${r.unit}", settle = if (r.decimals > 0) 0.01f else 0.05f) {
                "${teleLabels[key].orEmpty()}${String.format(Locale.US, "%.${r.decimals}f", it)} ${r.unit}"
            }
        }
        findViewById<View>(R.id.teleRpmBar).setBackgroundColor(Channels.RPM)
        findViewById<View>(R.id.teleSpeedBar).setBackgroundColor(Channels.SPEED)
        findViewById<View>(R.id.telePanel).background = panelBackground()
        teleCoolant.background = panelBackground()
        teleFuel.background = panelBackground()
        findViewById<TextView>(R.id.teleLegend).text = android.text.SpannableStringBuilder().apply {
            fun key(label: String, colour: Int) {
                val start = length
                append("■ ")
                setSpan(android.text.style.ForegroundColorSpan(colour), start, length, 0)
                append(label).append("    ")
            }
            key("rpm", Channels.RPM); key(Units.speedUnit, Channels.SPEED); key("throttle", Channels.THROTTLE); append("Last 30 s")
        }

        fun pid(n: Int) = Pids.dashboard.first { it.number == n }
        val speed = pid(Pids.SPEED)
        fun decal(holder: Int, key: String, r: Readout, wide: Boolean) {
            val d = DecalView(this, r.label, r.unit, r.min, r.max, r.warn, r.decimals, wide)
            findViewById<android.widget.FrameLayout>(holder).addView(d)
            decals[key] = d
        }
        decal(R.id.decalSpeed, SPEED_KEY, Readout(SPEED_KEY, "Speed", Units.speedUnit, speed.decimals, 0.0, Units.speed(240.0), null), true)
        cockpitRing.speedMax = Units.speed(240.0).toFloat(); cockpitRing.speedUnit = Units.speedUnit
        cyberHud.speedUnit = Units.speedUnit
        findViewById<TextView>(R.id.teleSpeedUnit).text = Units.speedUnit
        decal(R.id.decalCoolant, slots[0], readout(slots[0]), false)
        decal(R.id.decalFuel, slots[1], readout(slots[1]), false)

        // Cockpit glass panels: the three chosen readings (coolant, fuel and battery to start with).
        val row = findViewById<LinearLayout>(R.id.glassRow)
        slots.map { readout(it) }.forEachIndexed { i, r ->
            val key = r.key; val label = r.label; val unit = r.unit
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = panelBackground(stroke = true)
                setPadding(dp(12f).toInt(), dp(12f).toInt(), dp(12f).toInt(), dp(10f).toInt())
            }
            box.addView(TextView(this).apply {
                text = label; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary))
                glassLabels[key] = this
            })
            // The number and its unit share one line, like the other styles' readouts.
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = true }
            val value = TextView(this).apply {
                text = getString(R.string.dash); maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f); setTextColor(themeColor(R.attr.obdTextPrimary))
                typeface = uiTypeface(500); fontFeatureSettings = "tnum"
            }
            line.addView(value)
            line.addView(TextView(this).apply {
                glassUnits[key] = this
                text = unit; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary))
                setPadding(dp(4f).toInt(), 0, 0, 0)
            })
            box.addView(line)
            // Match the row's height, so a two-line label (the battery's status) keeps the panels even.
            row.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                if (i > 0) marginStart = dp(8f).toInt()
            })
            glass[key] = SmoothText(value, getString(R.string.dash), settle = if (r.decimals > 0) 0.01f else 0.05f) {
                String.format(Locale.US, "%.${r.decimals}f", it)
            }
        }

        // Cyberpunk neon readouts: coolant, fuel and battery.
        val cyberRow = findViewById<LinearLayout>(R.id.cyberRow)
        slots.map { readout(it) }.map { r ->
            CyberChipView(this, r.label, r.unit, r.decimals, r.min.toFloat(), r.max.toFloat(), r.warn?.toFloat()) to r.key
        }.forEachIndexed { i, (chip, key) ->
            cyberRow.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(8f).toInt() })
            cyberChips[key] = chip
        }

        // Retro digital: three glowing readouts with segment bars.
        val retroRow = findViewById<LinearLayout>(R.id.retroRow)
        slots.map { readout(it) }.forEachIndexed { i, r ->
            val chip = com.obdreader.app.ui.RetroChipView(this, r.label, r.unit, r.decimals, r.min.toFloat(), r.max.toFloat(), r.warn?.toFloat())
            retroRow.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(8f).toInt() })
            retroChips[r.key] = chip
        }
        // Rally stage: speed in a black frame and the first reading on an orange block, the other two in a strip.
        val rallyRow = findViewById<LinearLayout>(R.id.rallyBlocks)
        val speedBlock = com.obdreader.app.ui.RallyBlockView(this, "Speed", Units.speedUnit, 0, null, com.obdreader.app.ui.RallyBlockView.Style.OUTLINE)
        rallyRow.addView(speedBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
        rallyBlocks[SPEED_KEY] = speedBlock
        readout(slots[0]).let { r ->
            val b = com.obdreader.app.ui.RallyBlockView(this, r.label, r.unit, r.decimals, r.warn, com.obdreader.app.ui.RallyBlockView.Style.ORANGE)
            rallyRow.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10f).toInt() })
            rallyBlocks[r.key] = b
        }
        val strip = findViewById<LinearLayout>(R.id.rallyStrip)
        slots.drop(1).map { readout(it) }.forEach { r ->
            val b = com.obdreader.app.ui.RallyBlockView(this, r.label, r.unit, r.decimals, r.warn, com.obdreader.app.ui.RallyBlockView.Style.CELL)
            strip.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            rallyBlocks[r.key] = b
        }
        // Hairline: the three chosen readings as a quiet list under the dial.
        val hairList = findViewById<LinearLayout>(R.id.hairlineList)
        slots.map { readout(it) }.forEach { r ->
            val row = TowerRowView(this, r.label, r.unit, r.min, r.max, r.warn, r.decimals, themeColor(R.attr.obdStripe1))
            hairList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            hairlineRows[r.key] = row
        }

        // The live-data list: everything except the readings the dashboard already shows big.
        val shownBig = setOf(RPM_KEY, SPEED_KEY, slots[0], slots[1])
        Pids.dashboard.filter { it.hex !in shownBig }.forEach { p ->
            val r = readout(p.hex)
            addTile(p.hex, TowerRowView(this, r.label, r.unit, r.min, r.max, r.warn, r.decimals, Channels.forKey(p.hex, themeColor(R.attr.obdStripe1))))
        }
        if (Boost.KEY !in shownBig) readout(Boost.KEY).let { r ->
            addTile(Boost.KEY, TowerRowView(this, r.label, r.unit, r.min, r.max, r.warn, r.decimals, Channels.forKey(Boost.KEY, themeColor(R.attr.obdStripe1))))
        }
        if (VOLTAGE_KEY !in shownBig) addTile(VOLTAGE_KEY, TowerRowView(this, "Battery", "V", 11.0, 15.0, 15.0, 1, Channels.forKey(VOLTAGE_KEY, themeColor(R.attr.obdStripe1))))
        // G-force from the phone, at the end of the list.
        GForce.KEYS.filter { it !in shownBig }.forEach { k ->
            val r = readout(k)
            addTile(k, TowerRowView(this, r.label, r.unit, r.min, r.max, r.warn, r.decimals, Channels.forKey(k, themeColor(R.attr.obdStripe1))))
        }
    }

    private fun setRpm(value: Float?) {
        lastRpm = value
        roundel.setValue(value)
        shiftLights.setRpm(value, redline)
        teleRpmText.set(value)
        cockpitRing.set(lastSpeed, value)
        cyberHud.set(lastSpeed, value)
        retroDash.set(lastSpeed, value)
        rallyRev.setValue(value)
        hairlineDial.set(lastSpeed, value)
        if (ObdSession.state.value.isConnected) {
            trace.push(value, lastSpeed, ObdSession.state.value.readings[THROTTLE_KEY]?.toFloat())
            cyberHud.push(value)
        } else if (value == null) {
            cyberHud.push(null)
        }
    }

    private fun setSpeed(value: Float?) {
        lastSpeed = value
        decals[SPEED_KEY]?.setValue(value?.toDouble())
        teleSpeedText.set(value)
        cockpitRing.set(value, lastRpm)
        cyberHud.set(value, lastRpm)
        retroDash.set(value, lastRpm)
        rallyBlocks[SPEED_KEY]?.setValue(value?.toDouble())
        hairlineDial.set(value, lastRpm)
    }

    /** G-force: while the phone learns how it sits in its mount, the readings say so instead of a blank. */
    private fun showMotionStatus(status: com.obdreader.app.session.PhoneMotion.Status) {
        val learning = status == com.obdreader.app.session.PhoneMotion.Status.LEARNING
        GForce.KEYS.forEach { k -> tiles[k]?.labelOverride = if (learning) readout(k).label + " · " + getString(R.string.g_learning).lowercase(Locale.getDefault()) else null }
        gMeter.mode = when (status) {
            com.obdreader.app.session.PhoneMotion.Status.OFF -> com.obdreader.app.ui.GMeterView.Mode.OFF
            com.obdreader.app.session.PhoneMotion.Status.LEARNING -> com.obdreader.app.ui.GMeterView.Mode.LEARNING
            com.obdreader.app.session.PhoneMotion.Status.NO_SENSOR -> com.obdreader.app.ui.GMeterView.Mode.NO_SENSOR
            com.obdreader.app.session.PhoneMotion.Status.READY -> com.obdreader.app.ui.GMeterView.Mode.LIVE
        }
    }

    /** The top speed this drive, beside the speed in every style. */
    private fun showTopSpeed(top: Double?) {
        val t = top?.let { Units.speed(it) }
        decals[SPEED_KEY]?.top = t
        cockpitRing.top = t?.toFloat()
        cyberHud.top = t?.toFloat()
        retroDash.top = t?.toFloat()
        hairlineDial.top = t?.toFloat()
        rallyBlocks[SPEED_KEY]?.note = t?.let { "Top ${it.roundToInt()}" }
        findViewById<TextView>(R.id.teleSpeedUnit).setIfChanged(t?.let { "${Units.speedUnit} · top ${it.roundToInt()}" } ?: Units.speedUnit)
    }

    /** A dropdown list that always shows all its items, ignoring what's typed in the box. */
    private class AllItemsAdapter(context: android.content.Context, private val items: List<String>) :
        ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, items) {
        override fun getFilter(): android.widget.Filter = object : android.widget.Filter() {
            override fun performFiltering(constraint: CharSequence?) = FilterResults().apply { values = items; count = items.size }
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) = notifyDataSetChanged()
        }
    }

    /** Shows the estimated charging status beside the battery voltage, wherever the battery is shown. */
    private fun showCharging(state: ChargingStatus.State?) {
        val k = VOLTAGE_KEY
        tiles[k]?.labelOverride = state?.let { ChargingStatus.label(it) }
        decals[k]?.labelOverride = state?.let { ChargingStatus.label(it, roomy = false) }
        glassLabels[k]?.text = ChargingStatus.label(state, roomy = false)
        cyberChips[k]?.labelOverride = state?.short
        retroChips[k]?.labelOverride = state?.short
        rallyBlocks[k]?.labelOverride = state?.short
        hairlineRows[k]?.labelOverride = state?.let { ChargingStatus.label(it) }
        if (k in teleLabels) {
            teleLabels[k] = ChargingStatus.label(state, roomy = false) + "   "
            ObdSession.state.value.readings[k]?.let { teleTexts[k]?.set(it.toFloat()) }
        }
    }

    /** No reading from the car for [key]: every place it's shown says N/A, never a stale or made-up number. */
    private fun showNoReading(key: String) {
        tiles[key]?.setUnsupported()
        if (key !in slots) return
        decals[key]?.setUnsupported()
        cyberChips[key]?.setUnsupported()
        retroChips[key]?.setUnsupported()
        rallyBlocks[key]?.setUnsupported()
        hairlineRows[key]?.setUnsupported()
        glass[key]?.showText("N/A")
        glassUnits[key]?.visibility = View.GONE
        teleTexts[key]?.showText("${teleLabels[key].orEmpty()}N/A")
    }

    private fun setBigReading(key: String, value: Double?) {
        if (value != null) glassUnits[key]?.visibility = View.VISIBLE
        decals[key]?.setValue(value)
        glass[key]?.set(value?.toFloat())
        cyberChips[key]?.setValue(value)
        retroChips[key]?.setValue(value)
        rallyBlocks[key]?.setValue(value)
        hairlineRows[key]?.setValue(value)
        teleTexts[key]?.set(value?.toFloat())
    }

    // ---------- Choosing an adapter ----------

    private fun showConnectSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_connect, null)
        view.applyTitleFonts()

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
                TYPE_WIFI -> R.drawable.ic_wifi to getString(R.string.opt_wifi_title)
                else -> R.drawable.ic_bluetooth to getString(R.string.opt_bluetooth_title)
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
        bind(R.id.optionBluetooth, R.drawable.ic_bluetooth, R.string.opt_bluetooth_title, R.string.opt_bluetooth_sub) { connectBluetooth() }
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

    /**
     * One Bluetooth option for every adapter: the permissions both kinds need, then a single
     * list of paired and nearby adapters, whichever kind of Bluetooth they use.
     */
    private fun connectBluetooth() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        withPermissions(perms) { withBluetoothOn { showBluetoothPicker(it) } }
    }

    /**
     * Records a drive. The first time, asks whether to save the route too: location goes only into
     * this drive's file (for a map in other software and the outline on the drive card).
     */
    private fun startRecording() {
        if (com.obdreader.app.session.LocationFeed.permitted(this)) { beginRecording(withLocation = true); return }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.route_title)
            .setMessage(R.string.route_message)
            .setPositiveButton(R.string.route_yes) { _, _ ->
                routePermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            .setNegativeButton(R.string.route_no) { _, _ -> beginRecording(withLocation = false) }
            .show()
    }

    private fun beginRecording(withLocation: Boolean) {
        LogRecorder.start(this, withLocation)
        val r = LogRecorder.active.value
        if (withLocation && r?.withLocation == false) toast("Location is off on this phone, so the route isn't being saved")
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

    /** A device in the Bluetooth list, and how to talk to it. */
    private class Found(val device: BluetoothDevice, val name: String, var ble: Boolean, val paired: Boolean)

    /**
     * Lists paired adapters straight away, then adds nearby ones as they're found: Bluetooth LE
     * adapters (the "iPhone-compatible" kind) from an LE scan, and unpaired Bluetooth Classic
     * adapters from a normal search. Tapping one connects the right way for its kind, pairing a
     * Classic adapter first if it needs it. People only ever see names.
     */
    @SuppressLint("MissingPermission")
    private fun showBluetoothPicker(adapter: BluetoothAdapter) {
        val found = mutableListOf<Found>()
        val labels = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf())
        var searching = true

        fun relabel() {
            val seen = mutableListOf<String>()
            labels.setNotifyOnChange(false)
            labels.clear()
            found.forEach { labels.add(uniqueLabel(it.name, seen)) }
            labels.notifyDataSetChanged()
        }
        fun add(device: BluetoothDevice, rawName: String?, ble: Boolean, paired: Boolean) {
            val name = rawName?.trim()?.takeIf { it.isNotEmpty() } ?: return // nameless devices are never adapters
            val existing = found.firstOrNull { it.device.address == device.address }
            if (existing != null) return
            val item = Found(device, name, ble, paired)
            // Likely adapters first, then paired devices, then everything else in the order found.
            val index = when {
                looksLikeObd(name) -> found.indexOfFirst { !looksLikeObd(it.name) }.let { if (it < 0) found.size else it }
                paired -> found.indexOfFirst { !looksLikeObd(it.name) && !it.paired }.let { if (it < 0) found.size else it }
                else -> found.size
            }
            found.add(index, item)
            relabel()
        }

        // Paired devices: an LE-only device talks over LE, everything else over Classic.
        adapter.bondedDevices.orEmpty().forEach { d ->
            add(d, safeName(d), ble = d.type == BluetoothDevice.DEVICE_TYPE_LE, paired = true)
        }

        // Nearby Bluetooth LE adapters.
        val scanner = adapter.bluetoothLeScanner
        val leCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device
                val name = result.scanRecord?.deviceName ?: safeName(d)
                runOnUiThread { add(d, name, ble = true, paired = false) }
            }
        }
        // Nearby unpaired Bluetooth Classic adapters.
        val classicReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_FOUND) return
                val d = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                } ?: return
                if (d.type == BluetoothDevice.DEVICE_TYPE_LE) return // the LE scan covers these
                val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: safeName(d)
                add(d, name, ble = false, paired = d.bondState == BluetoothDevice.BOND_BONDED)
            }
        }

        fun stopSearching() {
            if (!searching) return
            searching = false
            try { scanner?.stopScan(leCallback) } catch (ignored: Exception) {}
            try { adapter.cancelDiscovery() } catch (ignored: Exception) {}
            try { unregisterReceiver(classicReceiver) } catch (ignored: Exception) {}
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.searching_adapters))
            .setAdapter(labels) { _, which ->
                stopSearching()
                connectFound(adapter, found[which])
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { stopSearching() }
            .show()

        try {
            ContextCompat.registerReceiver(this, classicReceiver, android.content.IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_EXPORTED)
            adapter.startDiscovery()
            scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), leCallback)
        } catch (e: SecurityException) {
            stopSearching()
            if (found.isEmpty()) { dialog.dismiss(); toast("Bluetooth permission was refused"); return }
        }

        lifecycleScope.launch {
            delay(BLE_SCAN_MS)
            stopSearching()
            if (dialog.isShowing) {
                dialog.setTitle(if (found.isEmpty()) getString(R.string.no_adapters_found) else getString(R.string.choose_adapter))
            }
        }
    }

    /** Connects to a device from the Bluetooth list, pairing a Classic adapter first if it isn't paired yet. */
    @SuppressLint("MissingPermission")
    private fun connectFound(adapter: BluetoothAdapter, f: Found) {
        val d = f.device
        if (f.ble) {
            startSession(BleTransport(applicationContext, d)) { rememberLast(TYPE_BLE, d.address, f.name) }
            return
        }
        if (d.bondState == BluetoothDevice.BOND_BONDED) {
            startSession(BluetoothTransport(d, adapter)) { rememberLast(TYPE_CLASSIC, d.address, f.name) }
            return
        }
        // Not paired: Android asks for the PIN (usually 1234 or 0000), then the app connects.
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: Intent) {
                val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (dev?.address != d.address) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                    BluetoothDevice.BOND_BONDED -> {
                        try { unregisterReceiver(this) } catch (ignored: Exception) {}
                        startSession(BluetoothTransport(d, adapter)) { rememberLast(TYPE_CLASSIC, d.address, f.name) }
                    }
                    BluetoothDevice.BOND_NONE -> {
                        try { unregisterReceiver(this) } catch (ignored: Exception) {}
                        toast("Pairing with ${f.name} didn't finish. The PIN is usually 1234 or 0000.")
                    }
                }
            }
        }
        ContextCompat.registerReceiver(this, receiver, android.content.IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        if (!d.createBond()) {
            try { unregisterReceiver(receiver) } catch (ignored: Exception) {}
            // Some adapters refuse to pair from an app; a direct connection may still work.
            startSession(BluetoothTransport(d, adapter)) { rememberLast(TYPE_CLASSIC, d.address, f.name) }
        } else {
            toast("Pairing with ${f.name}. If asked for a PIN, try 1234 or 0000.")
        }
    }

    /** The device's name, numbered if another one in the list has the same name ("OBDII", "OBDII (2)"). */
    private fun uniqueLabel(name: String, seen: MutableList<String>): String {
        val n = seen.count { it == name }
        seen.add(name)
        return if (n == 0) name else "$name (${n + 1})"
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
    private fun showUpdateIfAsked(intent: Intent?) {
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
    private fun handleWidgetConnect(intent: Intent?) {
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
    private fun openFeedback(bug: Boolean) {
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
    private fun showCodesIfAsked(intent: Intent?) {
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
    private fun drawCarTilesIfAsked(intent: Intent?) {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0 || intent?.getBooleanExtra("car_tiles", false) != true) return
        intent.removeExtra("car_tiles")
        Thread {
            val files = try { com.obdreader.app.car.CarTiles.renderSamples(applicationContext) } catch (e: Throwable) { android.util.Log.w("OBD2CarTiles", "failed", e); emptyList() }
            android.util.Log.i("OBD2CarTiles", "drew ${files.size}: ${files.joinToString(" ") { it.name }}")
        }.start()
    }

    // ---------- Picture-in-picture ----------

    /** True when this phone can show a floating picture-in-picture window. */
    private fun pipSupported(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Picture-in-picture when leaving: switched on, supported, and connected to a car (or the demo). */
    private fun pipWanted(): Boolean = pipSupported() && prefs.getBoolean(PREF_PIP, false) && ObdSession.state.value.isConnected

    private var pipConnected: Boolean? = null
    private var pipView: FrameLayout? = null
    private var pipLeftAt = 0L

    private fun pipParams(): android.app.PictureInPictureParams {
        val disconnect = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_close), getString(R.string.disconnect), getString(R.string.disconnect),
            android.app.PendingIntent.getBroadcast(this, 3, Intent(this, com.obdreader.app.widget.ObdWidget::class.java).setAction(com.obdreader.app.widget.ObdWidget.ACTION_DISCONNECT),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE),
        )
        return android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(3, 2))
            .setActions(listOf(disconnect))
            .apply { if (Build.VERSION.SDK_INT >= 31) { setAutoEnterEnabled(pipWanted()); setSeamlessResizeEnabled(true) } }
            .build()
    }

    /** Keeps Android's picture-in-picture settings in step: on Android 12 and newer it then goes into the window by itself when you leave. */
    private fun updatePipParams() {
        if (!pipSupported()) return
        try { setPictureInPictureParams(pipParams()) } catch (e: Exception) { android.util.Log.w("OBD2Pip", "params: ${e.message}") }
    }

    private fun onStateForPip(s: ObdSession.State) {
        if (s.isConnected != pipConnected) { pipConnected = s.isConnected; updatePipParams() }
        if (isInPictureInPictureMode) drawPip()
    }

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

    private lateinit var pipStatus: TextView
    private lateinit var pipMain: TextView
    private lateinit var pipMainLabel: TextView
    private lateinit var pipMainBox: LinearLayout
    private val pipSmall = mutableListOf<Pair<TextView, TextView>>()

    /** The floating window: your widget readings, the first one big, in your style's colours. */
    private fun buildPipView(): FrameLayout = FrameLayout(this).apply {
        setBackgroundColor(themeColor(R.attr.obdBg))
        val col = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val p = dp(8f).toInt(); setPadding(p, p, p, p)
        }
        pipStatus = TextView(this@MainActivity).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); gravity = Gravity.CENTER; maxLines = 1; setTextColor(themeColor(R.attr.obdTextSecondary)) }
        pipMainBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; val p = dp(4f).toInt(); setPadding(p * 2, p, p * 2, p) }
        pipMainLabel = TextView(this@MainActivity).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); gravity = Gravity.CENTER; maxLines = 1 }
        pipMain = TextView(this@MainActivity).apply { typeface = displayTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f); gravity = Gravity.CENTER; maxLines = 1; fontFeatureSettings = "tnum" }
        pipMainBox.addView(pipMainLabel); pipMainBox.addView(pipMain)
        col.addView(pipStatus); col.addView(pipMainBox)
        val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(4f).toInt(), 0, 0) }
        pipSmall.clear()
        repeat(2) {
            val cell = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            // Centred in their half of the window, like the big reading above.
            val l = TextView(this@MainActivity).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); maxLines = 1; gravity = Gravity.CENTER; setTextColor(themeColor(R.attr.obdTextSecondary)) }
            val v = TextView(this@MainActivity).apply { typeface = uiTypeface(600); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); maxLines = 1; gravity = Gravity.CENTER; setTextColor(themeColor(R.attr.obdTextPrimary)); fontFeatureSettings = "tnum" }
            val full = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            cell.addView(l, full); cell.addView(v, LinearLayout.LayoutParams(full))
            row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            pipSmall += l to v
        }
        col.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // The window can be tiny or pinched bigger: size the text to the window's height so everything fits.
        addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom -> if (bottom - top != oldBottom - oldTop) post { sizePip(bottom - top) } }
    }

    private fun sizePip(h: Int) {
        if (h <= 0) return
        val px = TypedValue.COMPLEX_UNIT_PX
        pipStatus.setTextSize(px, h * 0.075f)
        pipMainLabel.setTextSize(px, h * 0.075f)
        pipMain.setTextSize(px, h * 0.26f)
        pipSmall.forEach { (l, v) -> l.setTextSize(px, h * 0.07f); v.setTextSize(px, h * 0.12f) }
        listOf(pipStatus, pipMainLabel, pipMain).plus(pipSmall.flatMap { listOf(it.first, it.second) }).forEach { it.includeFontPadding = false }
        val p = (h * 0.04f).toInt(); (pipStatus.parent as View).setPadding(p, p, p, p)
        pipMainBox.setPadding(p * 2, p / 2, p * 2, p / 2)
        ((pipSmall.firstOrNull()?.first?.parent?.parent) as? View)?.setPadding(0, p / 2, 0, 0)
    }

    private fun drawPip() {
        if (pipView == null) return
        val c = com.obdreader.app.widget.ObdWidget.content(this)
        pipStatus.text = c.status
        pipStatus.setTextColor(themeColor(if (c.connected) R.attr.obdAccent else R.attr.obdTextSecondary))
        val first = c.cells.getOrNull(0)
        pipMainLabel.text = first?.label.orEmpty()
        pipMain.text = first?.withUnit ?: "—"
        // Near the redline or top speed: the big reading lights up in the style's second colour, like the widget.
        val hot = first?.highlight == true
        pipMainBox.background = if (hot) android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(10f); setColor(themeColor(R.attr.obdStripe2)) } else null
        val ink = if (hot) (if (androidx.core.graphics.ColorUtils.calculateLuminance(themeColor(R.attr.obdStripe2)) > 0.3) 0xFF111214.toInt() else 0xFFFFFFFF.toInt()) else themeColor(R.attr.obdTextPrimary)
        pipMain.setTextColor(ink)
        pipMainLabel.setTextColor(if (hot) ink else themeColor(R.attr.obdTextSecondary))
        pipSmall.forEachIndexed { i, (l, v) ->
            val cell = c.cells.getOrNull(i + 1)
            (l.parent as View).visibility = if (cell == null) View.GONE else View.VISIBLE
            l.text = cell?.label.orEmpty(); v.text = cell?.withUnit.orEmpty()
        }
    }

    private fun disconnect() = ObdSession.disconnect()

    /** Draws the shared connection state: status, buttons, dials, tiles, VIN and codes. */
    private fun render(s: ObdSession.State) {
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

    // ---------- Diagnostics ----------

    private fun readDtcs() {
        if (ObdSession.client == null) return
        lifecycleScope.launch {
            dtcContainer.removeAllViews()
            setDtcStatus("Reading trouble codes…", "Asking the engine computer for stored and pending codes.")
            try {
                renderCodes(ObdSession.readCodes())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setDtcStatus("Couldn't read codes", e.message ?: "The adapter didn't answer.")
            }
        }
    }

    private fun renderCodes(codes: ObdSession.Codes) {
        lastCodes = codes // the code cards read the freeze frame from here
        dtcContainer.removeAllViews()
        setCodeBadge(codes.total)
        roundel.checkEngine = codes.stored.isNotEmpty()
        if (codes.total == 0) {
            setDtcStatus("No trouble codes", "The engine computer has no stored or pending faults.", MilBannerView.State.OK)
        } else {
            setDtcStatus(
                if (codes.total == 1) "1 trouble code" else "${codes.total} trouble codes",
                if (codes.stored.isNotEmpty()) "Stored codes are keeping the check-engine light on." else "Pending codes only, so the light isn't on yet.",
                if (codes.stored.isNotEmpty()) MilBannerView.State.ON else MilBannerView.State.NONE,
            )
            codes.stored.forEach { addDtcRow(it, pending = false) }
            codes.pending.forEach { addDtcRow(it, pending = true) }
        }
        codes.permanent?.takeIf { it.isNotEmpty() }?.let { p ->
            dtcContainer.addView(TextView(this).apply {
                text = getString(R.string.permanent_codes, p.joinToString(", "))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.15f); setTextColor(themeColor(R.attr.obdTextSecondary))
                setPadding(0, dp(10f).toInt(), 0, 0)
            })
        }
    }

    private fun readVin() {
        if (ObdSession.client == null) return
        lifecycleScope.launch {
            vinText.text = "Reading…"
            try {
                showVin(ObdSession.readVin())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                vinText.text = "Couldn't read it"
            }
        }
    }

    /**
     * Shows the VIN: the one the user entered from their VIN plate if there is one, else the one
     * the car sent ([carVin]). The car's VIN comes from its engine computer, which can hold a VIN
     * that isn't the car's (a typo when it was programmed, or a computer from another car), so when
     * the two differ both are shown.
     */
    private var shownBatteryCheck: com.obdreader.app.obd.BatteryCheck.Result? = null

    /** The latest battery check and the ones before it, from the phone. [fresh]: it was just measured. */
    private fun showBatteryCheck(fresh: com.obdreader.app.obd.BatteryCheck.Result? = null) {
        val kept = com.obdreader.app.obd.BatteryCheck.parseHistory(prefs.getString(AppPrefs.BATTERY_CHECKS, null))
        // A demo car's check isn't kept, but it's still shown while that demo runs.
        val history = (listOfNotNull(fresh ?: shownBatteryCheck) + kept).distinctBy { it.atMs }.sortedByDescending { it.atMs }
        val latest = history.firstOrNull()
        val rating = findViewById<TextView>(R.id.batteryRating)
        val text = findViewById<TextView>(R.id.batteryText)
        val past = findViewById<TextView>(R.id.batteryHistory)
        findViewById<View>(R.id.batteryCard).background = panelBackground()
        showBatteryNight()
        if (latest == null) {
            rating.setText(R.string.battery_none); rating.setTextColor(themeColor(R.attr.obdTextPrimary))
            text.setText(R.string.battery_how); past.visibility = View.GONE
            return
        }
        val when_ = java.text.SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(java.util.Date(latest.atMs))
        rating.text = latest.rating.label
        rating.setTextColor(themeColor(when (latest.rating) {
            com.obdreader.app.obd.BatteryCheck.Rating.GOOD -> R.attr.obdTextPrimary
            else -> R.attr.obdWarn
        }))
        text.text = "${latest.summary()}, $when_. ${latest.rating.advice}"
        val older = history.drop(1).take(5)
        past.text = if (older.isEmpty()) "" else "Before: " + older.joinToString(" · ") { String.format(Locale.US, "%.1f V", it.crankingV) }
        past.visibility = if (older.isEmpty()) View.GONE else View.VISIBLE
        if (fresh != null && fresh != shownBatteryCheck) {
            toast("Battery check: ${fresh.rating.label.lowercase(Locale.getDefault())}, ${String.format(Locale.US, "%.1f V", fresh.crankingV)} while starting")
        }
        shownBatteryCheck = fresh ?: shownBatteryCheck
    }

    /** The switch for the overnight check, and what the last night showed. */
    private fun showBatteryNight() {
        val sw = findViewById<MaterialSwitch>(R.id.batteryWatchSwitch)
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = AppPrefs.batteryWatch(prefs)
        sw.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(AppPrefs.BATTERY_WATCH, on).apply(); showBatteryNight() }
        val night = findViewById<TextView>(R.id.batteryNight)
        val samples = com.obdreader.app.obd.BatteryRest.decode(prefs.getString(AppPrefs.BATTERY_NIGHT, null))
        val off = prefs.getLong(AppPrefs.BATTERY_NIGHT_OFF, 0L)
        val drain = com.obdreader.app.obd.BatteryRest.drain(samples, off)
        night.text = when {
            drain != null -> "Last time the car was off: " + drain.summary()
            samples.isNotEmpty() -> "Watching: ${samples.size} readings so far, latest ${com.obdreader.app.obd.BatteryRest.describe(samples.last().volts)}. A result needs about 5 hours with the car off."
            else -> ""
        }
        night.setTextColor(themeColor(if (drain?.draining == true) R.attr.obdWarn else R.attr.obdTextPrimary))
        night.visibility = if (night.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun showVin(carVin: String?) {
        val userVin = AppPrefs.userVin(prefs)
        val vin = userVin ?: carVin
        val checked = vin == null || vin.length < 17 || VinDecoder.isValid(vin)
        val mode = vinMode()
        vinText.text = when {
            vin == null -> "Not reported"
            mode == com.obdreader.app.obd.VinPrivacy.Mode.HIDDEN -> getString(R.string.vin_hidden)
            else -> com.obdreader.app.obd.VinPrivacy.mask(vin, mode)
        }
        findViewById<TextView>(R.id.vinLabel).setText(if (userVin != null) R.string.vin_entered else R.string.vin)
        findViewById<MaterialButton>(R.id.vinEnter).apply {
            setText(if (userVin != null) R.string.vin_change else R.string.vin_enter)
            setOnClickListener { askForVin(carVin) }
        }
        val note = findViewById<TextView>(R.id.vinNote)
        note.text = when {
            userVin != null && carVin != null && carVin != userVin ->
                "Your car's engine computer reports ${if (mode == com.obdreader.app.obd.VinPrivacy.Mode.HIDDEN) "a different VIN" else com.obdreader.app.obd.VinPrivacy.mask(carVin, mode)}, which doesn't match your VIN plate. That happens on some cars: the VIN stored in the computer can have a typo from when it was programmed, or come from another car if the computer was replaced. It doesn't affect live data or trouble codes."
            userVin == null && carVin != null && !checked ->
                "This VIN fails its check digit, so it may not be your car's. If it doesn't match your VIN plate, enter the right one below."
            else -> null
        }
        note.visibility = if (note.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        val info = vin?.let { VinDecoder.decode(it) }
        showVinMismatch(info)
        vehicleText.text = when {
            !checked -> "Make and year not taken from this VIN"
            info?.make != null && info.modelYear != null -> "${info.make}, ${info.modelYear} model year"
            info?.make != null -> info.make
            vin != null -> "Make not recognised from VIN"
            savedCar() != null -> "Your car: ${savedCar()?.label}. It doesn't report a VIN."
            else -> "Older cars often don't report a VIN. Live data and codes still work."
        }
    }

    /** Lets the user type their VIN from the plate, or go back to the one the car sends. */
    private fun askForVin(carVin: String?) {
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            // Filled in only when Hide VIN lets the whole VIN show.
            setText(if (vinMode() == com.obdreader.app.obd.VinPrivacy.Mode.FULL) AppPrefs.userVin(prefs) ?: carVin ?: "" else "")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(android.text.InputFilter.AllCaps(), android.text.InputFilter.LengthFilter(20))
            typeface = uiTypeface(500)
            setSelection(text?.length ?: 0)
        }
        val box = com.google.android.material.textfield.TextInputLayout(this).apply {
            setPadding(dp(20f).toInt(), dp(8f).toInt(), dp(20f).toInt(), 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vin_dialog_title)
            .setMessage(R.string.vin_dialog_text)
            .setView(box)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .apply { if (AppPrefs.userVin(prefs) != null) setNeutralButton(R.string.vin_use_car) { _, _ -> AppPrefs.setUserVin(prefs, null); showVin(ObdSession.state.value.vin) } }
            .show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val vin = VinDecoder.normalise(input.text?.toString().orEmpty())
            if (vin == null) { box.error = getString(R.string.vin_dialog_bad); return@setOnClickListener }
            AppPrefs.setUserVin(prefs, if (vin == ObdSession.state.value.vin) null else vin)
            dialog.dismiss()
            showVin(ObdSession.state.value.vin)
        }
    }

    /**
     * The VIN is the car's own word on what it is. If it disagrees with the car the user chose,
     * say so and offer to switch, because the chosen car decides how manufacturer codes are explained.
     */
    private fun showVinMismatch(info: VinDecoder.Info?) {
        val note = findViewById<TextView>(R.id.vinMismatch)
        val fix = findViewById<MaterialButton>(R.id.vinMismatchFix)
        val car = savedCar()
        val vinMake = info?.make
        val makeDiffers = car != null && vinMake != null && !VinDecoder.sameMake(vinMake, car.make)
        val yearDiffers = car != null && vinMake != null && !makeDiffers && info.modelYear != null && kotlin.math.abs(info.modelYear - car.year) > 1
        if (car == null || (!makeDiffers && !yearDiffers)) {
            note.visibility = View.GONE; fix.visibility = View.GONE
            return
        }
        val fromVin = listOfNotNull(info?.modelYear?.toString(), vinMake?.substringBefore(" (")).joinToString(" ")
        note.text = "This car's VIN says it's a $fromVin, but your car is set to ${car.label}. Fault codes are explained for the car you choose."
        note.visibility = View.VISIBLE
        fix.text = "Set my car to $fromVin"
        fix.visibility = View.VISIBLE
        fix.setOnClickListener {
            val make = VehicleCatalog.makeNames.firstOrNull { VinDecoder.sameMake(vinMake!!, it) } ?: vinMake!!.substringBefore(" (")
            prefs.edit().putString(PREF_CAR_MAKE, make).putInt(PREF_CAR_YEAR, info?.modelYear ?: car.year)
                .remove(PREF_CAR_MODEL).remove(PREF_CAR_ENGINE).remove(PREF_LAST_PROTOCOL).apply()
            updateCarSetting(); applyRedline()
            showVinMismatch(info)
            lastCodes?.let { renderCodes(it) }
            toast("Your car is now set to $make. Add the model under Your car if you like.")
        }
    }

    // ---------- Appearance ----------

    private fun appVersion(): String =
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

    private val tabOrder = listOf(R.id.nav_dashboard, R.id.nav_graphs, R.id.nav_diagnostics)
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var swipeDownTime = 0L
    private var swipeTracking = false
    private var swiping = false

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
    private fun switchTabBySwipe(step: Int) {
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
        super.onPause()
    }

    private fun showInstallPrompt(confirm: Intent) {
        try {
            androidx.core.app.NotificationManagerCompat.from(this).cancel(4101)
            startActivity(confirm)
        } catch (e: Exception) {
            android.util.Log.i("OBD2Updater", "couldn't open the install prompt: ${e.message}")
            updateProblem("Android didn't open its Install screen." + (e.message?.let { "\n\nAndroid said: $it" } ?: ""))
        }
    }

    /** Why an update didn't go through, said plainly, with a way to get the APK by hand. */
    private fun updateProblem(why: String) {
        if (isFinishing || isDestroyed) return
        androidx.core.app.NotificationManagerCompat.from(this).cancel(4101)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.updates_problem_title)
            .setMessage(why + (if (Updater.isSamsung) "\n\n" + getString(R.string.updates_samsung) else "") + "\n\n" + getString(R.string.updates_problem_manual))
            .setPositiveButton(R.string.updates_open_page) { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Updater.RELEASES_PAGE))) } catch (e: Exception) {}
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ---------- App updates (from the GitHub releases) ----------

    private var availableUpdate: com.obdreader.app.obd.UpdateCheck.Release? = null
    /** A check is under way or the Update window is up, so the daily check doesn't open a second one. */
    private var updateBusy = false

    private fun showUpdateRow(release: com.obdreader.app.obd.UpdateCheck.Release?) {
        val sub = findViewById<TextView>(R.id.updatesSub)
        sub.text = when {
            Updater.isTestBuild(this) -> getString(R.string.updates_sub_test, appVersion())
            release != null -> getString(R.string.updates_sub_available, release.version)
            else -> getString(R.string.updates_sub, appVersion())
        }
        sub.setTextColor(if (release != null) themeColor(R.attr.obdAccent) else themeColor(R.attr.obdTextSecondary))
    }

    /**
     * [manual]: the user tapped App updates, so say what was found. Otherwise it's the daily check:
     * silent unless there's a new version, and never while connected to the car.
     */
    private fun checkForUpdate(manual: Boolean) {
        val now = System.currentTimeMillis()
        if (!manual && (updateBusy || !com.obdreader.app.obd.UpdateCheck.dueForCheck(prefs.getLong(Updater.LAST_CHECK, 0L), now))) {
            return
        }
        if (manual) toast(getString(R.string.updates_checking))
        updateBusy = true
        lifecycleScope.launch {
            val release = try { Updater.latest() } catch (e: Exception) { android.util.Log.i("OBD2Updater", "check failed: ${e.message}"); null }
            updateBusy = false
            if (release == null) { if (manual) toast(getString(R.string.updates_failed)); return@launch }
            android.util.Log.i("OBD2Updater", "latest build ${release.build}, installed ${Updater.installedBuild(this@MainActivity)}")
            prefs.edit().putLong(Updater.LAST_CHECK, now).apply()
            val newer = com.obdreader.app.obd.UpdateCheck.isNewer(release, Updater.installedBuild(this@MainActivity))
            availableUpdate = release.takeIf { newer }
            showUpdateRow(availableUpdate)
            if (isFinishing || isDestroyed) return@launch
            when {
                Updater.isTestBuild(this@MainActivity) -> if (manual) offerReleasePage(release)
                !newer -> { if (manual) toast(getString(R.string.updates_latest, appVersion())) }
                manual -> offerUpdate(release)
                // The daily check: once per new version, and not while driving.
                !ObdSession.state.value.isConnected && prefs.getInt(Updater.SNOOZED_BUILD, 0) != release.build -> offerUpdate(release)
            }
        }
    }

    /** The changelog, but only the versions after the one installed: what an update brings. */
    private fun showChangesSince() {
        val installed = appVersion()
        val sheet = BottomSheetDialog(this)
        val pad = dp(20f).toInt()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8f).toInt(), pad, dp(28f).toInt())
        }
        list.addView(TextView(this).apply {
            text = getString(R.string.updates_changes_title, installed)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); typeface = uiTypeface(600)
            setTextColor(themeColor(R.attr.obdTextPrimary))
        })
        val status = TextView(this).apply {
            setText(R.string.updates_changes_loading)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(10f).toInt(), 0, dp(4f).toInt())
        }
        list.addView(status)
        fun entry(e: com.obdreader.app.obd.Changelog.Entry) {
            list.addView(TextView(this).apply {
                text = listOf(e.version, e.date).filter { it.isNotBlank() }.joinToString("  ·  ")
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); typeface = uiTypeface(600)
                setTextColor(themeColor(R.attr.obdTextPrimary)); setPadding(0, dp(16f).toInt(), 0, dp(4f).toInt())
            })
            list.addView(TextView(this).apply {
                text = e.text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setLineSpacing(0f, 1.15f)
                setTextColor(themeColor(R.attr.obdTextPrimary))
            })
        }
        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(list) })
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.show()
        lifecycleScope.launch {
            val entries = try { com.obdreader.app.obd.Changelog.parse(Updater.changelog()) } catch (e: Exception) { null }
            if (!sheet.isShowing) return@launch
            if (entries == null) { status.setText(R.string.updates_changes_failed); return@launch }
            val newer = com.obdreader.app.obd.Changelog.since(entries, installed)
            if (newer.isEmpty()) {
                status.text = getString(R.string.updates_changes_none, installed)
                entries.firstOrNull { com.obdreader.app.obd.Changelog.compare(it.version, installed) == 0 }?.let { entry(it) }
            } else {
                status.visibility = View.GONE
                newer.forEach { entry(it) }
                if (!Updater.isTestBuild(this@MainActivity)) list.addView(MaterialButton(this@MainActivity).apply {
                    text = getString(R.string.updates_changes_get, newer.first().version)
                    setOnClickListener {
                        sheet.dismiss()
                        availableUpdate?.let { offerUpdate(it) } ?: checkForUpdate(manual = true)
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f).toInt() })
            }
            list.applyTitleFonts()
        }
    }

    private fun offerReleasePage(release: com.obdreader.app.obd.UpdateCheck.Release) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.updates_available_title, release.version))
            .setMessage(R.string.updates_test_body)
            .setPositiveButton(R.string.updates_open_page) { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(release.pageUrl))) } catch (e: Exception) { toast(Updater.RELEASES_PAGE) }
            }
            .setNegativeButton(R.string.updates_later, null)
            .show()
    }

    private fun offerUpdate(release: com.obdreader.app.obd.UpdateCheck.Release) {
        val pad = dp(20f).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8f).toInt(), pad, 0)
            addView(TextView(context).apply {
                text = getString(R.string.updates_available_body, appVersion()) + (if (Updater.isSamsung) "\n\n" + getString(R.string.updates_samsung) else "")
                setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            })
            if (release.notes.isNotBlank()) {
                addView(TextView(context).apply {
                    setText(R.string.updates_whats_new); typeface = uiTypeface(600)
                    setTextColor(themeColor(R.attr.obdTextPrimary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setPadding(0, dp(14f).toInt(), 0, dp(4f).toInt())
                })
                addView(TextView(context).apply {
                    text = release.notes
                    setTextColor(themeColor(R.attr.obdTextPrimary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setLineSpacing(0f, 1.15f)
                })
            }
        }
        // Seen here, so the notification about it isn't needed any more.
        com.obdreader.app.session.UpdateCheckJob.dismiss(this)
        updateBusy = true
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.updates_available_title, release.version))
            .setView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
            .setPositiveButton(R.string.updates_update) { _, _ -> downloadAndInstall(release) }
            .setNegativeButton(R.string.updates_later) { _, _ -> prefs.edit().putInt(Updater.SNOOZED_BUILD, release.build).apply() }
            .setOnDismissListener { updateBusy = false }
            .show()
    }

    private fun downloadAndInstall(release: com.obdreader.app.obd.UpdateCheck.Release) {
        if (!Updater.canInstall(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.updates_permission_title)
                .setMessage(R.string.updates_permission_body)
                .setPositiveButton(R.string.updates_open_settings) { _, _ ->
                    try { startActivity(Updater.permissionIntent(this)) } catch (e: Exception) { toast(getString(R.string.updates_permission_body)) }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val bar = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply { isIndeterminate = true; max = 100 }
        val pad = dp(24f).toInt()
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.updates_downloading, release.version))
            .setView(android.widget.FrameLayout(this).apply { setPadding(pad, dp(12f).toInt(), pad, dp(8f).toInt()); addView(bar) })
            .setCancelable(false)
            .show()
        var downloadError: String? = null
        lifecycleScope.launch {
            val file = try {
                Updater.download(this@MainActivity, release) { f ->
                    if (bar.isIndeterminate) bar.isIndeterminate = false
                    bar.setProgressCompat((f * 100).toInt(), false)
                }
            } catch (e: Exception) { android.util.Log.i("OBD2Updater", "download failed: ${e.message}"); downloadError = e.message; null }
            dialog.dismiss()
            if (file == null) {
                updateProblem(getString(R.string.updates_download_failed) + (downloadError?.let { "\n\nWhat happened: $it" } ?: ""))
                return@launch
            }
            android.util.Log.i("OBD2Updater", "downloaded ${file.length()} bytes")
            val handedOver = android.os.SystemClock.elapsedRealtime()
            try {
                Updater.install(this@MainActivity, file, release.build)
                // Some phones (Samsung's Auto Blocker) refuse an app's own update without a word.
                kotlinx.coroutines.delay(20_000)
                if (Updater.lastAnswerMs < handedOver && !isFinishing && !isDestroyed) {
                    android.util.Log.i("OBD2Updater", "no answer from the installer")
                    updateProblem(getString(R.string.updates_no_answer))
                }
            } catch (e: Exception) {
                android.util.Log.i("OBD2Updater", "install failed: ${e.message}")
                updateProblem("Android didn't accept the update." + (e.message?.let { "\n\nAndroid said: $it" } ?: ""))
            }
        }
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
            typeface = uiTypeface(600)
        })
        val sections = listOf(
            R.string.help_start_title to R.string.help_start_body,
            R.string.help_adapters_title to R.string.help_adapters_body,
            R.string.help_car_title to R.string.help_car_body,
            R.string.help_auto_title to R.string.help_auto_body,
            R.string.help_codes_title to R.string.help_codes_body,
            R.string.help_battery_title to R.string.help_battery_body,
            R.string.help_location_title to R.string.help_location_body,
            R.string.help_safety_title to R.string.help_safety_body,
        )
        for ((title, body) in sections) {
            list.addView(TextView(this).apply {
                setText(title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(themeColor(R.attr.obdAccent))
                typeface = uiTypeface(600)
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
        val mode = com.obdreader.app.ui.StyleClock.mode(prefs)
        val auto = mode != com.obdreader.app.obd.DayNight.Mode.OFF
        // With day and night switching on, the list sets the day or the night style; it starts on the one showing now.
        var slot = when {
            !auto -> PREF_THEME
            com.obdreader.app.ui.StyleClock.isNight(this) -> AppPrefs.STYLE_NIGHT
            else -> AppPrefs.STYLE_DAY
        }
        fun selectedId() = AppThemes.byId(prefs.getString(slot, null) ?: prefs.getString(PREF_THEME, null)).id
        val pad = dp(20f).toInt()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8f).toInt(), pad, dp(28f).toInt())
        }
        list.addView(TextView(this).apply {
            setText(R.string.appearance)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(themeColor(R.attr.obdTextPrimary))
            typeface = uiTypeface(600)
        })
        list.addView(TextView(this).apply {
            setText(R.string.appearance_subtitle)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(4f).toInt(), 0, dp(4f).toInt())
        })

        // Day and night: off, or switching between two styles.
        sheetRow(list, getString(R.string.day_night_title), mode.label) { sheet.dismiss(); showDayNight() }
        val cards = ArrayList<Triple<AppThemes.AppTheme, MaterialCardView, ImageView>>()
        fun markSelected() {
            val id = selectedId()
            cards.forEach { (theme, card, check) ->
                val on = theme.id == id
                card.strokeWidth = dp(if (on) 2f else 1f).toInt()
                card.strokeColor = themeColor(if (on) R.attr.obdAccent else R.attr.obdOutline)
                check.visibility = if (on) View.VISIBLE else View.INVISIBLE
            }
        }
        if (auto) {
            list.addView(TextView(this).apply {
                text = dayNightNow(mode); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.15f)
                setTextColor(themeColor(R.attr.obdTextSecondary)); setPadding(dp(2f).toInt(), dp(8f).toInt(), 0, 0)
            })
            val toggle = MaterialButtonToggleGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
            val dayBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { id = View.generateViewId(); text = getString(R.string.day_style) }
            val nightBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { id = View.generateViewId(); text = getString(R.string.night_style) }
            toggle.addView(dayBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            toggle.addView(nightBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            toggle.check(if (slot == AppPrefs.STYLE_NIGHT) nightBtn.id else dayBtn.id)
            toggle.addOnButtonCheckedListener { _, id, checked ->
                if (!checked) return@addOnButtonCheckedListener
                slot = if (id == nightBtn.id) AppPrefs.STYLE_NIGHT else AppPrefs.STYLE_DAY
                markSelected()
            }
            list.addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f).toInt() })
        }
        list.addView(View(this), LinearLayout.LayoutParams(1, dp(8f).toInt()))

        for (theme in AppThemes.ALL) {
            if (theme == AppThemes.ALL.first() || theme.ownLayout && theme == AppThemes.ALL.first { it.ownLayout }) {
                list.addView(TextView(this).apply {
                    setText(if (theme.ownLayout) R.string.themes_layouts else R.string.themes_liveries)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(themeColor(R.attr.obdTextSecondary))
                    typeface = uiTypeface(600)
                    setPadding(dp(2f).toInt(), dp(10f).toInt(), 0, dp(8f).toInt())
                })
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f).toInt(), dp(12f).toInt(), dp(14f).toInt(), dp(12f).toInt())
            }
            row.addView(ThemeSwatchView(this, theme), LinearLayout.LayoutParams(dp(72f).toInt(), dp(54f).toInt()))
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14f).toInt(), 0, dp(8f).toInt(), 0)
                addView(TextView(context).apply {
                    text = theme.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(themeColor(R.attr.obdTextPrimary))
                    typeface = uiTypeface(600)
                })
                addView(TextView(context).apply {
                    text = theme.description
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(themeColor(R.attr.obdTextSecondary))
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val check = ImageView(this).apply {
                setImageResource(R.drawable.ic_check)
                imageTintList = ColorStateList.valueOf(themeColor(R.attr.obdAccent))
                contentDescription = "Selected"
            }
            row.addView(check, LinearLayout.LayoutParams(dp(24f).toInt(), dp(24f).toInt()))
            val card = MaterialCardView(this).apply {
                shapeAppearanceModel = themeShape()
                cardElevation = 0f
                setCardBackgroundColor(themeColor(R.attr.obdSurfaceHigh))
                isClickable = true
                isFocusable = true
                addView(row)
                setOnClickListener {
                    sheet.dismiss()
                    if (theme.id != selectedId()) applyTheme(theme, slot)
                }
            }
            cards += Triple(theme, card, check)
            list.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10f).toInt()
            })
        }
        markSelected()

        sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(list) })
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.show()
    }

    /** Saves a style: the chosen one, or with switching on, the day or night one ([slot]). */
    private fun applyTheme(theme: AppThemes.AppTheme, slot: String = PREF_THEME) {
        prefs.edit().putString(slot, theme.id).apply()
        // Redraws every screen in the new style, if it's the one showing now. The car connection lives
        // outside this screen, so it carries on uninterrupted.
        if (com.obdreader.app.ui.StyleClock.styleId(this) != shownStyle) switchStyle()
        else toast(getString(if (slot == AppPrefs.STYLE_NIGHT) R.string.night_style_set else R.string.day_style_set, theme.name))
    }

    // ---------- Day and night styles ----------

    /** Day and night: off, at sunset and sunrise, with the phone's dark mode, or by the light sensor. */
    private fun showDayNight() {
        val modes = com.obdreader.app.obd.DayNight.Mode.entries
        val current = com.obdreader.app.ui.StyleClock.mode(prefs)
        choiceSheet(getString(R.string.day_night_title)) { body, sheet ->
            sheetNote(body, getString(R.string.day_night_intro))
            sheetOptions(body, modes.map { it.label }, modes.indexOf(current)) { i ->
                sheet.dismiss()
                setDayNight(modes[i])
            }
            modes.drop(1).forEach { sheetNote(body, "${it.label}: ${it.help}") }
            if (getSystemService(android.hardware.SensorManager::class.java)?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT) == null) {
                sheetNote(body, getString(R.string.day_night_no_sensor))
            }
            sheetNote(body, getString(R.string.day_night_privacy))
            sheetNote(body, getString(R.string.day_night_car))
        }
    }

    private fun setDayNight(mode: com.obdreader.app.obd.DayNight.Mode) {
        val edit = prefs.edit().putString(AppPrefs.STYLE_AUTO, mode.key)
        val showing = AppThemes.byId(shownStyle)
        if (mode == com.obdreader.app.obd.DayNight.Mode.OFF) {
            // Off: keep the style showing now.
            edit.putString(PREF_THEME, showing.id)
        } else if (prefs.getString(AppPrefs.STYLE_DAY, null) == null || prefs.getString(AppPrefs.STYLE_NIGHT, null) == null) {
            // First time on: the chosen style for whichever it suits, and a light or dark one for the other.
            val light = android.view.ContextThemeWrapper(this, showing.style).themeBool(R.attr.obdIsLight)
            edit.putString(AppPrefs.STYLE_DAY, if (light) showing.id else if (showing.ownLayout) "rally" else "pitlane")
            edit.putString(AppPrefs.STYLE_NIGHT, if (light) "asphalt" else showing.id)
        }
        edit.apply()
        restartStyleWatch()
        // Back to the Style sheet, to pick the day and night styles (after the redraw, if the style changes now).
        if (com.obdreader.app.ui.StyleClock.styleId(this) != shownStyle) { reopenAppearance = true; switchStyle() } else showThemePicker()
    }

    /** "Now: day style. Night style from 19:42." */
    private fun dayNightNow(mode: com.obdreader.app.obd.DayNight.Mode): String {
        val night = com.obdreader.app.ui.StyleClock.isNight(this)
        val now = getString(if (night) R.string.day_night_now_night else R.string.day_night_now_day)
        val time = android.text.format.DateFormat.getTimeFormat(this)
        return when (mode) {
            com.obdreader.app.obd.DayNight.Mode.SUN -> {
                val place = com.obdreader.app.ui.StyleClock.place(this)
                val next = com.obdreader.app.obd.DayNight.nextChange(System.currentTimeMillis(), place.latitude, place.longitude)
                now + (next?.let { " " + getString(if (night) R.string.day_night_next_day else R.string.day_night_next_night, time.format(java.util.Date(it))) } ?: "") +
                    if (place.exact) "" else " " + getString(R.string.day_night_rough)
            }
            com.obdreader.app.obd.DayNight.Mode.PHONE -> now + " " + getString(if (night) R.string.day_night_phone_dark else R.string.day_night_phone_light)
            com.obdreader.app.obd.DayNight.Mode.LIGHT -> now + " " + getString(if (night) R.string.day_night_light_dark else R.string.day_night_light_bright)
            else -> ""
        }
    }

    /** Last light reading, fed again every few seconds: the sensor only reports changes, and a switch waits for the light to stay changed. */
    private var lastLux: Float? = null

    /** While the screen is open: the light sensor (if that's the mode) and a check every few seconds for sunset, sunrise or the light. */
    private fun startStyleWatch() {
        stopStyleWatch()
        val mode = com.obdreader.app.ui.StyleClock.mode(prefs)
        if (mode == com.obdreader.app.obd.DayNight.Mode.LIGHT) {
            val sm = getSystemService(android.hardware.SensorManager::class.java)
            val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)
            if (sm != null && sensor != null) {
                val l = object : android.hardware.SensorEventListener {
                    override fun onSensorChanged(e: android.hardware.SensorEvent) {
                        val lux = e.values.firstOrNull() ?: return
                        lastLux = lux
                        val before = com.obdreader.app.ui.StyleClock.light.isNight
                        if (com.obdreader.app.ui.StyleClock.light.feed(lux, android.os.SystemClock.elapsedRealtime()) != before) checkStyle()
                    }
                    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
                }
                sm.registerListener(l, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
                lightListener = l
            }
        }
        styleWatch = lifecycleScope.launch {
            var tick = 0
            while (true) {
                lastLux?.let { lux ->
                    val before = com.obdreader.app.ui.StyleClock.light.isNight
                    if (com.obdreader.app.ui.StyleClock.light.feed(lux, android.os.SystemClock.elapsedRealtime()) != before) checkStyle()
                }
                // Every 30 s: has the sun set (or risen), or a switch waited for the screen to be left alone?
                if (tick++ % 6 == 0) checkStyle()
                delay(5_000)
            }
        }
    }

    private fun stopStyleWatch() {
        styleWatch?.cancel(); styleWatch = null
        lightListener?.let { getSystemService(android.hardware.SensorManager::class.java)?.unregisterListener(it) }
        lightListener = null
        lastLux = null
    }

    private fun restartStyleWatch() { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startStyleWatch() }

    /**
     * Switches to the day or night style if it's time. It waits while the screen is in use (touched
     * in the last few seconds, a sheet or dialog open, or the floating window), so nothing changes
     * under a finger; the next check tries again.
     */
    private fun checkStyle() {
        val want = com.obdreader.app.ui.StyleClock.styleId(this)
        if (want == shownStyle) return
        if (isInPictureInPictureMode || !hasWindowFocus() || System.currentTimeMillis() - lastTouchMs < 8_000) return
        android.util.Log.i("OBD2Style", "day/night: $shownStyle -> $want")
        switchStyle()
    }

    /** Redraws the screen in the style that should show now, fading over from the old one (no flash). */
    private fun switchStyle() {
        fadeFrom = try { window.decorView.drawToBitmap() } catch (e: Exception) { null }
        com.obdreader.app.widget.ObdWidget.refresh(this)
        recreate()
    }

    /** After [switchStyle]: lays the old screen's picture over the new one and fades it out. */
    private fun fadeFromLastStyle() {
        val shot = fadeFrom ?: return
        fadeFrom = null
        val decor = window.decorView as? ViewGroup ?: return
        val cover = ImageView(this).apply {
            setImageBitmap(shot); scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        decor.addView(cover, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        cover.animate().alpha(0f).setStartDelay(120).setDuration(900).withEndAction { decor.removeView(cover) }.start()
    }

    // ---------- Back up and restore ----------

    /** Back up and restore: what goes in a backup, the routes and VIN choices, then the file picker. */
    private fun showBackup() {
        lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) { com.obdreader.app.session.Backups.summary(applicationContext) }
            backupVin = com.obdreader.app.session.Backups.vinByDefault(this@MainActivity)
            choiceSheet(getString(R.string.backup_title)) { body, sheet ->
                sheetNote(body, getString(R.string.backup_intro))
                sheetNote(body, resources.getQuantityString(R.plurals.backup_drives, summary.drives, summary.drives,
                    android.text.format.Formatter.formatShortFileSize(this@MainActivity, summary.driveBytes)))
                if (summary.anyRoute || summary.hasVin) sheetHeading(body, getString(R.string.backup_include))
                if (summary.anyRoute) sheetSwitch(body, getString(R.string.backup_routes), backupRoutes) { on -> backupRoutes = on }
                if (summary.hasVin) sheetSwitch(body, getString(R.string.backup_vin), backupVin) { on -> backupVin = on }
                sheetNote(body, getString(R.string.backup_privacy))
                sheetButtons(body,
                    getString(R.string.restore) to {
                        sheet.dismiss()
                        if (ObdSession.isActive || LogRecorder.active.value != null) toast(getString(R.string.restore_disconnect_first))
                        else restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    },
                    getString(R.string.back_up) to { sheet.dismiss(); backupLauncher.launch(com.obdreader.app.session.Backups.fileName(System.currentTimeMillis())) },
                )
            }
        }
    }

    private fun writeBackup(uri: android.net.Uri) {
        val routes = backupRoutes; val vin = backupVin
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { com.obdreader.app.session.Backups.write(applicationContext, uri, routes, vin) } }
            android.util.Log.i("OBD2Backup", "backup written: ${result.isSuccess}")
            toast(result.fold({ getString(R.string.backup_saved) }, { getString(R.string.backup_failed, it.message ?: "") }))
        }
    }

    private fun readBackup(uri: android.net.Uri) {
        if (ObdSession.isActive || LogRecorder.active.value != null) { toast(getString(R.string.restore_disconnect_first)); return }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { com.obdreader.app.session.Backups.read(applicationContext, uri) } }
            result.onSuccess { showRestorePreview(it) }.onFailure { e ->
                android.util.Log.i("OBD2Backup", "restore refused: ${e.message}")
                withContext(Dispatchers.IO) { com.obdreader.app.session.Backups.discard(applicationContext) }
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.restore_refused_title)
                    .setMessage((e as? com.obdreader.app.obd.BackupFile.BadBackup)?.message ?: getString(R.string.restore_unreadable))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    /** What a backup holds, before anything on the phone changes; Restore asks to go ahead. */
    private fun showRestorePreview(c: com.obdreader.app.obd.BackupFile.Contents) {
        var restoring = false
        val sheet = choiceSheet(getString(R.string.restore_title)) { body, sheet ->
            val day = java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            sheetNote(body, getString(R.string.restore_made, c.info.madeAtMs?.let { day.format(java.util.Date(it)) } ?: "?", c.info.appVersion ?: "?"))
            sheetHeading(body, getString(R.string.restore_contents))
            val car = (c.value(AppPrefs.CAR_MAKE) as? String)?.let { make ->
                VehicleCatalog.Car(make, c.value(AppPrefs.CAR_YEAR) as? Int ?: 0, c.value(AppPrefs.CAR_MODEL) as? String, c.value(AppPrefs.CAR_ENGINE) as? String).label
            }
            val theme = (c.value(AppPrefs.THEME) as? String)?.let { AppThemes.byId(it).name }
            val auto = com.obdreader.app.obd.DayNight.mode(c.value(AppPrefs.STYLE_AUTO) as? String)
            val custom = com.obdreader.app.obd.CustomReading.decodeAll(c.value(PREF_CUSTOM) as? String).size
            val trips = com.obdreader.app.obd.TripHistory.parse(c.value(DriveScorer.PREF_TRIP_HISTORY) as? String).size
            val codes = com.obdreader.app.obd.CodeHistory.parse(c.value(AppPrefs.CODE_HISTORY) as? String).size
            val lines = listOfNotNull(
                getString(R.string.restore_car, car ?: getString(R.string.restore_none)),
                theme?.let { getString(R.string.restore_style, it) + if (auto != com.obdreader.app.obd.DayNight.Mode.OFF) " · " + getString(R.string.day_night_title) + ": " + auto.label.lowercase() else "" },
                getString(R.string.restore_custom, custom),
                getString(R.string.restore_trips, trips),
                getString(R.string.restore_codes, codes),
                getString(if (c.info.hasRoutes) R.string.restore_drives_routes else R.string.restore_drives, c.drives.size),
                getString(if (c.has(AppPrefs.USER_VIN)) R.string.restore_vin_in else R.string.restore_vin_out),
                // Where Connect looks for a Wi-Fi adapter: shown, since it changes where the app connects.
                (c.value(AppPrefs.HOST) as? String)?.let { h -> getString(R.string.restore_wifi, h + ((c.value(AppPrefs.PORT) as? Int)?.let { ":$it" } ?: "")) },
                getString(R.string.restore_settings, c.settings.size),
                c.skipped.takeIf { it > 0 }?.let { getString(R.string.restore_skipped, it) },
            )
            lines.forEach { sheetNote(body, "• $it") }
            sheetNote(body, getString(R.string.restore_what_happens))
            sheetButtons(body,
                getString(android.R.string.cancel) to { sheet.dismiss() },
                getString(R.string.restore) to {
                    restoring = true
                    sheet.dismiss()
                    lifecycleScope.launch {
                        val done = withContext(Dispatchers.IO) { runCatching { com.obdreader.app.session.Backups.restore(applicationContext, c) } }
                        done.onSuccess { r ->
                            android.util.Log.i("OBD2Backup", "restored ${r.settings} settings, ${r.drivesAdded} drives added, ${r.drivesAlreadyThere} already there")
                            toast(getString(R.string.restore_done, r.settings, r.drivesAdded))
                            com.obdreader.app.widget.ObdWidget.refresh(this@MainActivity)
                            recreate()
                        }.onFailure { toast(getString(R.string.backup_failed, it.message ?: "")) }
                    }
                    Unit
                },
            )
        }
        // Closed without restoring: the unpacked copy is thrown away.
        sheet.setOnDismissListener { if (!restoring) com.obdreader.app.session.Backups.discard(applicationContext) }
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
        val max = VehicleCatalog.dialMax(redline).toFloat()
        this.redline = (redline ?: 6500).toFloat()
        DriveScorer.setRedline(this.redline)
        roundel.max = max
        roundel.warnAt = this.redline
        cockpitRing.rpmMax = max
        cockpitRing.warnAt = this.redline
        cyberHud.rpmMax = max
        cyberHud.warnAt = this.redline
        retroDash.rpmMax = max; retroDash.warnAt = this.redline
        rallyRev.max = max; rallyRev.warnAt = this.redline
        hairlineDial.rpmMax = max; hairlineDial.warnAt = this.redline
        trace.rpmMax = max
    }

    private fun updateCarSetting() {
        val car = savedCar()
        carSetting.text = car?.label ?: getString(R.string.car_not_set)
        findViewById<View>(R.id.carExpect).visibility = if (car != null && CarSupport.expectations(car).isNotEmpty()) View.VISIBLE else View.GONE
    }

    /** No car chosen yet, and the user hasn't said theirs isn't listed. */
    private fun needsCar(): Boolean = savedCar() == null && !prefs.getBoolean(PREF_CAR_ASKED, false)

    private var carPickerShowing = false

    private fun showCarPicker(firstRun: Boolean = false, onDone: (() -> Unit)? = null) {
        val sheet = BottomSheetDialog(this)
        carPickerShowing = true
        sheet.setOnDismissListener { carPickerShowing = false }
        val view = layoutInflater.inflate(R.layout.sheet_car, null)
        view.applyTitleFonts()
        if (firstRun) {
            view.findViewById<TextView>(R.id.carTitle).setText(R.string.car_first_title)
            view.findViewById<TextView>(R.id.carSubtitle).setText(R.string.car_first_subtitle)
            view.findViewById<com.google.android.material.button.MaterialButton>(R.id.carClear).setText(R.string.car_skip)
            sheet.setCanceledOnTouchOutside(false)
        }
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
            // Always lists every engine for this model and year, whatever is typed (a plain adapter
            // would only show the ones matching the text already in the box).
            engineInput.setAdapter(AllItemsAdapter(this, engines))
            engineLayout.endIconMode = if (engines.isEmpty()) com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
                else com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU
            val model = modelInput.text?.toString()?.trim().orEmpty()
            val electric = model.isNotEmpty() && VehicleCatalog.isElectric(VehicleCatalog.Car(make, year, model), model.lowercase(Locale.US))
            engineLayout.helperText = getString(when {
                engines.isNotEmpty() -> R.string.car_engine_help
                electric -> R.string.car_engine_electric
                else -> R.string.car_engine_none
            })
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
            redlineView.text = rec.redline?.let {
                val r = String.format(Locale.US, "%,d", it)
                val dial = "${rec.dialMax / 1000}k"
                if (rec.redlineExact) "RPM dial redline set to $r rpm for this engine (dial to $dial)."
                else "RPM dial redline set to $r rpm, the highest for this model (dial to $dial). Choose your engine for its own redline."
            }.orEmpty()
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
        // Open the list on the first tap, like the Manufacturer and Year boxes.
        engineInput.setOnClickListener { if ((engineInput.adapter?.count ?: 0) > 0) engineInput.showDropDown() }
        engineInput.setOnFocusChangeListener { _, focused -> if (focused && (engineInput.adapter?.count ?: 0) > 0) engineInput.post { engineInput.showDropDown() } }
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
            prefs.edit().putBoolean(PREF_CAR_ASKED, true).apply()
            updateCarSetting()
            applyRedline()
            sheet.dismiss()
            if (lastCodes != null) lastCodes?.let { renderCodes(it) } // re-explain codes for the new make
            showVin(ObdSession.state.value.vin) // an entered VIN belongs to the car it was entered for
            val after = {
                when {
                    onDone != null -> onDone()
                    firstRun -> toast("Saved. Tap Connect when you're in the car, or try the demo car.")
                    else -> toast(if (ObdSession.client != null) "Saved. Disconnect and connect again to use it." else "Saved. Tap Connect to try again.")
                }
            }
            // What may not work with this car, before anything else happens.
            if (!showExpectations(car, after)) after()
        }
        view.findViewById<View>(R.id.carClear).setOnClickListener {
            prefs.edit().remove(PREF_CAR_MAKE).remove(PREF_CAR_YEAR).remove(PREF_CAR_MODEL).remove(PREF_CAR_ENGINE)
                .putBoolean(PREF_CAR_ASKED, true).apply()
            updateCarSetting()
            applyRedline()
            sheet.dismiss()
            onDone?.invoke()
        }

        sheet.setContentView(view)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.show()
    }

    /**
     * A popup listing the readings and features that may not work with [car], each with why.
     * Returns false (and shows nothing) when there's nothing to warn about. [onClose] runs when
     * it's closed.
     */
    private fun showExpectations(car: VehicleCatalog.Car, onClose: (() -> Unit)? = null): Boolean {
        val items = CarSupport.expectations(car)
        if (items.isEmpty()) return false
        val pad = dp(24f).toInt()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(4f).toInt(), pad, dp(8f).toInt())
        }
        list.addView(TextView(this).apply {
            text = "Based on what cars like yours usually report. When you connect, the app asks your car and shows everything it does report."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
        })
        items.forEach { item ->
            val colour = when (item.level) {
                CarSupport.Level.UNLIKELY -> themeColor(R.attr.obdDanger)
                CarSupport.Level.MAYBE -> themeColor(R.attr.obdWarn)
                CarSupport.Level.LIMITED -> themeColor(R.attr.obdTextSecondary)
            }
            list.addView(TextView(this).apply {
                text = item.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = uiTypeface(600)
                setTextColor(themeColor(R.attr.obdTextPrimary))
                setPadding(0, dp(18f).toInt(), 0, 0)
            })
            list.addView(TextView(this).apply {
                text = item.level.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = uiTypeface(600)
                setTextColor(colour)
                setPadding(0, dp(2f).toInt(), 0, dp(2f).toInt())
            })
            list.addView(TextView(this).apply {
                text = item.reason
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(themeColor(R.attr.obdTextSecondary))
                setLineSpacing(0f, 1.15f)
            })
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("What may not work with your ${car.label}")
            .setView(android.widget.ScrollView(this).apply { addView(list) })
            .setPositiveButton("Got it", null)
            .setOnDismissListener { onClose?.invoke() }
            .show()
        return true
    }

    // ---------- Protocol setting ----------

    private var lastProtocolConnected = false
    private var lastProtocolCode: String? = null

    /** The protocol setting, and while connected on Automatic, the protocol it found: one name everywhere. */
    private fun updateProtocolSetting() {
        val chosen = prefs.getString(PREF_PROTOCOL, ObdProtocols.AUTO)
        val forced = ObdProtocols.byCode(chosen)
        val inUse = ObdSession.state.value.takeIf { it.isConnected }?.protocolCode?.let { ObdProtocols.byCode(it) }
        protocolSetting.text = when {
            forced != null -> forced.name
            inUse != null -> getString(R.string.protocol_auto_using, inUse.name)
            else -> getString(R.string.protocol_auto)
        }
    }

    private fun showProtocolPicker() {
        val options = listOf<Pair<String, String>>(ObdProtocols.AUTO to getString(R.string.protocol_auto_detail)) +
            ObdProtocols.ALL.map { it.code to "${it.name}\n${it.hint}" }
        val current = prefs.getString(PREF_PROTOCOL, ObdProtocols.AUTO)
        val checked = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
        choiceSheet(getString(R.string.protocol)) { body, sheet ->
            sheetOptions(body, options.map { it.second }, checked) { which ->
                prefs.edit().putString(PREF_PROTOCOL, options[which].first).apply()
                updateProtocolSetting()
                sheet.dismiss()
                if (ObdSession.client != null) toast("Disconnect and connect again to use the new protocol")
            }
        }
    }

    /** The check-engine banner: [state] ON turns it amber, OK marks it green. */
    private fun setDtcStatus(title: String, sub: String = "", state: MilBannerView.State = MilBannerView.State.NONE) {
        milBanner.state = state
        val ink = milBanner.inkColor(themeColor(R.attr.obdTextPrimary))
        val inkSub = milBanner.inkColor(themeColor(R.attr.obdTextSecondary))
        dtcStatus.text = title
        dtcStatus.setTextColor(ink)
        dtcSub.text = sub
        dtcSub.setTextColor(inkSub)
        dtcSub.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
        val lamp = findViewById<ImageView>(R.id.milLamp)
        lamp.imageTintList = ColorStateList.valueOf(
            when (state) {
                MilBannerView.State.ON -> if (dash == DashLayout.COCKPIT) themeColor(R.attr.obdWarn) else ink
                MilBannerView.State.OK -> themeColor(R.attr.obdOk)
                MilBannerView.State.NONE -> themeColor(R.attr.obdTextSecondary)
            },
        )
    }

    /**
     * One trouble code: its plate and name, then (tap to open) how urgent it is, what it means,
     * what you might notice, common causes and what to check first.
     */
    private fun addDtcRow(code: String, pending: Boolean) {
        val info = DtcLibrary.lookup(code, ObdSession.carMake(this), ObdSession.carYear(this), ObdSession.carModel(this), ObdSession.carEngine(this))
        val pad = dp(14f).toInt()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground()
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val names = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f).toInt(), 0, dp(6f).toInt(), 0)
            addView(TextView(context).apply {
                text = info.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = uiTypeface(500)
                setTextColor(context.themeColor(R.attr.obdTextPrimary))
            })
            addView(TextView(context).apply {
                text = (if (pending) "Pending, not yet confirmed" else "Stored, check-engine light on") + ". " + info.severity.label + "."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(context.themeColor(R.attr.obdTextSecondary))
                setPadding(0, dp(2f).toInt(), 0, 0)
            })
        }
        val chevron = ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_right)
            imageTintList = ColorStateList.valueOf(themeColor(R.attr.obdTextSecondary))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        head.addView(PlateView(this, code, stored = !pending))
        head.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(chevron, LinearLayout.LayoutParams(dp(22f).toInt(), dp(22f).toInt()))
        card.addView(head)

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12f).toInt(), 0, 0)
            visibility = View.GONE
        }
        val severityColour = when (info.severity) {
            DtcLibrary.Severity.HIGH -> themeColor(R.attr.obdDanger)
            DtcLibrary.Severity.MEDIUM -> themeColor(R.attr.obdWarn)
            DtcLibrary.Severity.LOW -> themeColor(R.attr.obdOk)
            DtcLibrary.Severity.UNKNOWN -> themeColor(R.attr.obdTextSecondary)
        }
        details.addView(TextView(this).apply {
            text = info.severity.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = uiTypeface(600)
            setTextColor(severityColour)
            background = GradientDrawable().apply {
                cornerRadius = dp(4f)
                setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(severityColour, 40))
            }
            setPadding(dp(10f).toInt(), dp(4f).toInt(), dp(10f).toInt(), dp(4f).toInt())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        fun para(text: String, secondary: Boolean = true) = details.addView(TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(0f, 1.15f)
            setTextColor(themeColor(if (secondary) R.attr.obdTextSecondary else R.attr.obdTextPrimary))
            setPadding(0, dp(6f).toInt(), 0, 0)
        })
        fun heading(text: String) = details.addView(TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = uiTypeface(600)
            setTextColor(themeColor(R.attr.obdTextPrimary))
            setPadding(0, dp(14f).toInt(), 0, 0)
        })
        para(info.severity.advice)
        heading("What it means")
        para(info.meaning, secondary = false)
        if (info.symptoms.isNotEmpty()) { heading("You might notice"); para(info.symptoms.joinToString("\n") { "•  $it" }) }
        if (info.causes.isNotEmpty()) { heading("Common causes"); para(info.causes.joinToString("\n") { "•  $it" }) }
        if (info.checks.isNotEmpty()) { heading("What to check"); para(info.checks.mapIndexed { i, c -> "${i + 1}.  $c" }.joinToString("\n")) }
        // The engine's snapshot from the moment this code was set, if the car kept one for it.
        lastCodes?.freezeFrame?.takeIf { it.code == code && !pending }?.let { frame ->
            heading("When it was set (freeze frame)")
            para(frame.lines().joinToString("\n"), secondary = false)
            para("The car's own snapshot of the engine when this code was stored. Show it to your mechanic: it says what the engine was doing when the fault happened.")
        }
        if (info.system.isNotEmpty()) para("$code: ${info.system}.")
        card.addView(details)

        fun setOpen(open: Boolean) {
            details.visibility = if (open) View.VISIBLE else View.GONE
            chevron.rotation = if (open) 90f else 0f
            card.contentDescription = "$code, ${info.title}. ${if (open) "Tap to hide details" else "Tap for details"}"
        }
        setOpen(dtcContainer.childCount == 0) // the first code opens straight away
        card.setOnClickListener { setOpen(details.visibility != View.VISIBLE) }
        dtcContainer.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8f).toInt()
        })
    }

    // ---------- UI helpers ----------

    // ---------- Landscape ----------

    private var landscape = false
    private var landRow: LinearLayout? = null
    /** Rows built for landscape, removed again in portrait. */
    private val landRows = ArrayList<LinearLayout>()
    /** Views moved for landscape, with where they came from, so portrait can put them back exactly. */
    private val moved = ArrayList<Triple<View, ViewGroup, Pair<Int, ViewGroup.LayoutParams>>>()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientation()
        // The phone's dark mode may have changed.
        checkStyle()
    }

    private fun move(view: View, to: ViewGroup, lp: ViewGroup.LayoutParams? = null) {
        val from = view.parent as ViewGroup
        moved += Triple(view, from, from.indexOfChild(view) to view.layoutParams)
        from.removeView(view)
        to.addView(view, lp ?: view.layoutParams)
    }

    /**
     * Turning the phone sideways keeps the title across the top (with the livery stripes behind
     * it at full width) and puts the Dashboard in two columns underneath: the style's main dial or
     * display on the left, sized to fit the screen's height, and its other readouts, Connect and
     * status on the right. Race telemetry instead has its numbers beside the trace, with coolant,
     * fuel and Connect on one line below. The driving score and the live-data list (in two
     * columns) follow. Turning back restores the portrait layout exactly.
     */
    private fun applyOrientation() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (land == landscape) return
        landscape = land
        // Back to portrait first: undo every move, newest first.
        for ((view, parent, where) in moved.asReversed()) {
            (view.parent as? ViewGroup)?.removeView(view)
            parent.addView(view, where.first, where.second)
        }
        moved.clear()
        landRows.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        landRows.clear()
        landRow = null

        val content = findViewById<LinearLayout>(R.id.content)
        val topBar = findViewById<View>(R.id.topBar)
        fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }.also { landRows += it }
        fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (land) {
            val row = row()
            val left = column().apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
            val right = column()
            row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(20f).toInt() })
            content.addView(row, content.indexOfChild(topBar) + 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            landRow = row

            val active = findViewById<ViewGroup>(when (dash) {
                DashLayout.LIVERY -> R.id.layoutLivery
                DashLayout.TELEMETRY -> R.id.layoutTelemetry
                DashLayout.COCKPIT -> R.id.layoutCockpit
                DashLayout.CYBERPUNK -> R.id.layoutCyberpunk
                DashLayout.RETRO -> R.id.layoutRetro
                DashLayout.RALLY -> R.id.layoutRally
                DashLayout.HAIRLINE -> R.id.layoutHairline
            })
            val connectRow = connectBtn.parent as View
            if (dash == DashLayout.TELEMETRY) {
                // Shift lights and the big numbers on the left, the trace beside them on the right.
                val panel = findViewById<View>(R.id.telePanel)
                val pair = active.getChildAt(active.indexOfChild(panel) + 1)
                move(active, left, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                move(panel, right, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f).toInt() })
                // Coolant, fuel and Connect on one line underneath both.
                val line = row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
                content.addView(line, content.indexOfChild(row) + 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10f).toInt() })
                move(pair, line, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                move(connectRow, line, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12f).toInt() })
            } else {
                // The main dial goes left; the style's other readouts, Connect and status go right.
                val heroCount = if (dash == DashLayout.LIVERY) 2 else 1 // roundel and start lights; the ring or display
                move(active, left, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                (heroCount until active.childCount).map { active.getChildAt(it) }.forEach { move(it, right) }
                val from = content.indexOfChild(connectRow)
                val to = content.indexOfChild(chooseCarButton)
                (from..to).map { content.getChildAt(it) }.forEach { move(it, right) }
            }
        }
        // Slimmer livery stripes sideways, so the title and readouts sit higher.
        if (dash == DashLayout.LIVERY) {
            listOf(R.id.stripesDash, R.id.stripesDiag, R.id.stripesGraphs).forEach { findViewById<LiveryStripesView>(it).compact = land }
            val top = dp(if (land) 60f else 92f).toInt()
            listOf(R.id.topBar, R.id.titleDiagnostics, R.id.titleGraphs).forEach { findViewById<View>(it).setPadding(0, top, 0, 0) }
        }
        // Icons only on the tab bar sideways, so it's slimmer still.
        bottomNav.labelVisibilityMode = if (land) NavigationBarView.LABEL_VISIBILITY_UNLABELED else NavigationBarView.LABEL_VISIBILITY_LABELED
        bottomNav.minimumHeight = dp(if (land) 44f else 56f).toInt()
        layoutTiles(if (land) 2 else 1)
        fitHero()
    }

    private var heroFitter: View.OnLayoutChangeListener? = null

    /** In landscape, sizes the main dial to the visible height under the title, and keeps it fitted as that changes. */
    private fun fitHero() {
        fun fit() {
            val h = pageDashboard.height
            val below = findViewById<View>(R.id.topBar).height
            val max = if (landscape && h > 0) (h - below - dp(24f).toInt()).coerceAtLeast(dp(160f).toInt()) else Int.MAX_VALUE
            roundel.maxSize = max
            cockpitRing.maxSize = max
            cyberHud.maxSize = max
            retroDash.maxSize = max
            rallyRev.maxSize = max
            hairlineDial.maxSize = max
            // Telemetry: the trace beside the numbers is as tall as they are.
            trace.fixedHeight = if (landscape && dash == DashLayout.TELEMETRY) {
                val numbers = findViewById<View>(R.id.layoutTelemetry)
                val panel = findViewById<View>(R.id.telePanel)
                if (numbers.height > 0) (numbers.height - dp(4f).toInt() - (panel.height - trace.height)).coerceIn(dp(56f).toInt(), dp(220f).toInt()) else null
            } else null
        }
        // Fit again once the new arrangement has been laid out.
        pageDashboard.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                pageDashboard.viewTreeObserver.removeOnGlobalLayoutListener(this)
                fit()
            }
        })
        if (heroFitter == null) {
            heroFitter = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top != oldBottom - oldTop) pageDashboard.post { fit() }
            }.also { pageDashboard.addOnLayoutChangeListener(it) }
        }
        fit()
    }

    /** The live-data list: one column in portrait, two side by side in landscape. */
    private fun layoutTiles(columns: Int) {
        val all = tiles.values.toList()
        all.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        towerList.removeAllViews()
        if (columns == 1) {
            all.forEach { tile ->
                towerList.addView(tile, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(4f).toInt()
                })
            }
            return
        }
        all.chunked(columns).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEachIndexed { i, tile ->
                row.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(8f).toInt() })
            }
            repeat(columns - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(8f).toInt() }) }
            towerList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4f).toInt()
            })
        }
    }

    private fun addTile(key: String, tile: TowerRowView) {
        towerList.addView(tile, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4f).toInt()
        })
        tiles[key] = tile
    }

    private fun clearReadings() {
        tiles.values.forEach { it.setValue(null) }
        setRpm(null)
        setSpeed(null)
        slots.forEach { setBigReading(it, null) }
        trace.clear()
        roundel.checkEngine = false
        vinText.setText(R.string.vin_unknown)
        vehicleText.setText(R.string.vehicle_unknown)
        if (AppPrefs.userVin(prefs) != null) showVin(null)
        protocolText.setText(R.string.protocol_unknown)
        dtcContainer.removeAllViews()
        setCodeBadge(0)
        setDtcStatus(getString(R.string.engine_not_checked), getString(R.string.dtc_hint))
    }

    private fun setStatus(status: Status, title: String, detail: String) {
        statusText.setIfChanged(title)
        statusText2.setIfChanged(title)
        chooseCarButton.visibility = if (status == Status.ERROR) View.VISIBLE else View.GONE
        statusDetail.setIfChanged(detail)
        statusDetail.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateButtons(connected: Boolean, busy: Boolean = false) {
        val active = connected || busy
        connectBtn.text = getString(if (active) R.string.disconnect else R.string.connect)
        connectBtn.kind = if (active) SkewButton.Kind.OUTLINE else SkewButton.Kind.PRIMARY
        demoBtn.isEnabled = !active
        // While connected (or connecting), Demo car is hidden and Disconnect takes the whole row.
        demoBtn.visibility = if (active) View.GONE else View.VISIBLE
        readDtcBtn.isEnabled = connected
        vinBtn.isEnabled = connected
    }

    private fun showPage(itemId: Int) {
        pageDashboard.visibility = if (itemId == R.id.nav_dashboard) View.VISIBLE else View.GONE
        pageGraphs.visibility = if (itemId == R.id.nav_graphs) View.VISIBLE else View.GONE
        pageDiagnostics.visibility = if (itemId == R.id.nav_diagnostics) View.VISIBLE else View.GONE
        if (itemId != R.id.nav_diagnostics && ::graphRate.isInitialized) {
            lastReadings = emptyMap() // redraw every reading now that the Dashboard is back
            render(ObdSession.state.value)
        }
        updateFocus()
        graphStrips.forEach { it.invalidate() }
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

    /** Raw adapter traffic stays muted; the plain-English lines and the code summary stand out. */
    private fun styledLog(lines: List<String>): CharSequence {
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

    private val logShown = MutableStateFlow(false)

    private fun toggleLog() {
        val show = logText.visibility != View.VISIBLE
        logText.visibility = if (show) View.VISIBLE else View.GONE
        logShown.value = show
        logToggle.setText(if (show) R.string.hide else R.string.show)
    }

    /** Setting the same text again still makes Android lay the screen out again; skip it. */
    private fun TextView.setIfChanged(value: CharSequence) {
        if (text.toString() != value.toString()) text = value
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        /** The EV battery's charge while its sheet is open, kept across reopening (a gap of 10 minutes starts it again). */
        private val evLog = EvBattery.ChargeLog()
        /** Recordings bigger than this (several hours of driving) are left out of the same-route comparison. */
        const val COMPARE_MAX_BYTES = 16L * 1024 * 1024
        /** The old screen's picture, faded out over the new one after a style switch. */
        private var fadeFrom: android.graphics.Bitmap? = null
        /** Open the Style sheet again once the screen is redrawn (after turning day and night on). */
        private var reopenAppearance = false
        private const val VOLTAGE_KEY = ObdSession.VOLTAGE_KEY
        private val RPM_KEY = String.format(Locale.US, "%02X", Pids.RPM)
        private val SPEED_KEY = String.format(Locale.US, "%02X", Pids.SPEED)
        private const val COOLANT_KEY = "05"
        private const val THROTTLE_KEY = "11"
        private const val PREF_GRAPHS = "graph_channels"
        private const val PREF_GRAPH_TAB = "graph_tab"
        private const val MAX_GRAPHS = 6  // six is what a CAN car answers in one request (ElmClient.MAX_MULTI_PIDS)
        private const val FUEL_KEY = "2F"
        private const val BLE_SCAN_MS = 12_000L
        private const val LOG_REFRESH_MS = 250L
        /** Engine speed, road speed, throttle, engine load, manifold pressure and airflow. */
        private val DASHBOARD_FOCUS = setOf("0C", "0D", "11", "04", "0B", "10")
        private const val PREF_HOST = AppPrefs.HOST
        private const val PREF_PORT = AppPrefs.PORT
        private const val DEFAULT_HOST = AppPrefs.DEFAULT_HOST
        private const val DEFAULT_PORT = AppPrefs.DEFAULT_PORT
        private const val PREF_THEME = AppPrefs.THEME
        private const val PREF_CAR_MAKE = AppPrefs.CAR_MAKE
        private const val PREF_CAR_YEAR = AppPrefs.CAR_YEAR
        private const val PREF_CAR_MODEL = AppPrefs.CAR_MODEL
        private const val PREF_CAR_ENGINE = AppPrefs.CAR_ENGINE
        private const val PREF_CAR_ASKED = "car_asked"
        private const val PREF_SCORE_STYLE = "score_style"
        private const val PREF_LAST_TRIP = "last_trip"
        /** Picture-in-picture when leaving the app while connected (off until you turn it on). */
        private const val PREF_PIP = "pip"
        private const val PREF_CUSTOM = "custom_readings"
        private const val PREF_SLOTS = "dash_slots"
        private const val PREF_PERF_HISTORY = "perf_history"
        private const val PREF_PERF_MODE = "perf_mode"
        private const val PREF_LAST_TRIP_SHOWN = "last_trip_shown"
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
