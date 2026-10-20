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
import com.obdreader.app.obd.DtcLibrary
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.session.Exports
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
import com.obdreader.app.obd.Pids
import com.obdreader.app.obd.ObdProtocols
import com.obdreader.app.obd.VehicleCatalog
import com.obdreader.app.obd.TripSummary
import com.obdreader.app.obd.Units
import com.obdreader.app.obd.CarCoverage
import com.obdreader.app.obd.PerformanceTimer
import com.obdreader.app.obd.DriveTrack
import com.obdreader.app.session.OverlayExporter
import com.obdreader.app.obd.VinDecoder
import com.obdreader.app.obd.WifiTransport
import com.google.android.material.card.MaterialCardView
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

class MainActivity : AppCompatActivity() {

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
    private lateinit var teleRpm: TextView
    private lateinit var teleSpeed: TextView
    private lateinit var teleCoolant: TextView
    private lateinit var teleFuel: TextView
    private lateinit var cockpitRing: CockpitRingView
    private lateinit var cyberHud: CyberHudView
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

    /** A reading the dashboard can show in one of its main readouts. */
    private data class Readout(val key: String, val label: String, val unit: String, val decimals: Int, val min: Double, val max: Double, val warn: Double?)

    private fun readout(key: String): Readout =
        (Pids.dashboard.firstOrNull { it.hex == key }?.let { Readout(it.hex, it.label, it.unit, it.decimals, it.min, it.max, it.warnAbove) }
            ?: Readout(VOLTAGE_KEY, "Battery", "V", 1, 11.0, 15.0, null)).inUnits()

    /** The readout in the chosen units (°F, mph) where that applies. */
    private fun Readout.inUnits(): Readout = if (!Units.isTemperature(key) && !Units.isSpeed(key)) this else copy(
        unit = Units.unit(key, unit), min = Units.value(key, min), max = Units.value(key, max), warn = warn?.let { Units.value(key, it) },
    )

    /** Every reading that can go in a main readout (not engine speed or speed, which have their own). */
    private val readoutChoices: List<Readout> by lazy {
        Pids.dashboard.filter { it.hex != RPM_KEY && it.hex != SPEED_KEY }.map { readout(it.hex) } + readout(VOLTAGE_KEY)
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
        AppPrefs.applyUnits(prefs)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Dark status-bar icons on the light theme, light icons on the dark ones.
        val lightTheme = themeBool(R.attr.obdIsLight)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightTheme
            isAppearanceLightNavigationBars = lightTheme
        }
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).applyTitleFonts()

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
        findViewById<View>(R.id.themeButton).setOnClickListener { showThemePicker() }
        findViewById<View>(R.id.logShare).setOnClickListener {
            Exports.share(this, Exports.adapterLog(this, ObdSession.fullLog(), ObdSession.state.value), "text/plain", "OBD2 Reader adapter log")
        }
        shareReportBtn = findViewById(R.id.shareReportButton)
        shareReportBtn.setOnClickListener {
            Exports.share(this, Exports.codeReport(this, ObdSession.state.value, savedCar()?.label), "text/plain", "OBD2 Reader trouble code report")
        }
        findViewById<MaterialSwitch>(R.id.reconnectSwitch).apply {
            isChecked = AppPrefs.autoReconnect(prefs)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(AppPrefs.AUTO_RECONNECT, on).apply() }
        }
        findViewById<View>(R.id.reconnectRow).apply {
            background = panelBackground()
            setOnClickListener { findViewById<MaterialSwitch>(R.id.reconnectSwitch).toggle() }
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
        findViewById<TextView>(R.id.unitsSetting).text = "${Units.speedUnit}, ${Units.temperatureUnit}"
        findViewById<View>(R.id.unitsRow).apply {
            background = panelBackground()
            setOnClickListener { showUnitsPicker() }
        }
        findViewById<View>(R.id.scanRow).apply {
            background = panelBackground()
            setOnClickListener { runCarScan() }
        }
        findViewById<View>(R.id.slotsRow).apply {
            background = panelBackground()
            setOnClickListener { showSlotPicker(slots.toMutableList()) }
        }
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
                launch {
                    // Live polling adds dozens of lines a second; redraw the log at most four
                    // times a second, and only while it's open.
                    combine(ObdSession.codeLog, ObdSession.log, logShown) { codes, lines, shown -> if (shown) ObdSession.fullLog(codes, lines) else null }
                        .conflate()
                        .collect { lines ->
                            if (lines != null) logText.text = styledLog(lines)
                            delay(LOG_REFRESH_MS)
                        }
                }
                launch { LogRecorder.active.collect { renderRecording(it) } }
                launch { DriveScorer.results.collect { renderScore() } }
                launch { DriveScorer.topSpeed.collect { showTopSpeed(it) } }
                // When a drive ends, sum it up (once), and keep it as the last drive.
                launch {
                    DriveScorer.lastTrip.collect { trip ->
                        if (trip == null || trip.endedAt <= prefs.getLong(PREF_LAST_TRIP_SHOWN, 0L)) return@collect
                        prefs.edit().putString(PREF_LAST_TRIP, trip.encode()).putLong(PREF_LAST_TRIP_SHOWN, trip.endedAt).apply()
                        lastTripButton?.visibility = View.VISIBLE
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
        // The car comes first: it decides how fault codes are explained, the redline and the protocol.
        // Checked here rather than only on a fresh start, so it comes back if the screen is recreated.
        if (needsCar() && !carPickerShowing) showCarPicker(firstRun = true)
    }

    // ---------- Driving score ----------

    private lateinit var scoreCard: LinearLayout
    private var scoreStyle = DrivingScore.Style.EVERYDAY

    private fun setupScore() {
        scoreCard = findViewById(R.id.scoreCard)
        scoreCard.background = panelBackground()
        DriveScorer.start()
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
            // "Last drive" under the score card, once there's been one.
            val content = scoreCard.parent as ViewGroup
            lastTripButton = MaterialButton(this@MainActivity, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = getString(R.string.last_trip)
                setTextColor(themeColor(R.attr.obdAccent))
                setPaddingRelative(0, paddingTop, dp(8f).toInt(), paddingBottom)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                visibility = if (savedTrip() != null) View.VISIBLE else View.GONE
                setOnClickListener { savedTrip()?.let { showTripSummary(it, justEnded = false) } }
            }
            content.addView(lastTripButton, content.indexOfChild(scoreCard) + 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            content.addView(MaterialButton(this@MainActivity, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                id = R.id.perfButton
                text = getString(R.string.perf_title)
                setTextColor(themeColor(R.attr.obdAccent))
                setPaddingRelative(0, paddingTop, dp(8f).toInt(), paddingBottom)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener { showPerformance() }
            }, content.indexOfChild(lastTripButton) + 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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

    private var lastTripButton: MaterialButton? = null

    private fun savedTrip(): TripSummary? = TripSummary.decode(prefs.getString(PREF_LAST_TRIP, null))

    /** The trip summary: what the drive added up to, as a sheet of tiles. */
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
                    text = s?.toString() ?: getString(R.string.dash); setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f); typeface = displayTypeface()
                    setTextColor(s?.let { scoreColour(it) } ?: themeColor(R.attr.obdTextSecondary))
                })
                addView(TextView(context).apply { text = style.label; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        body.addView(scores, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (trip.scores.values.all { it == null }) body.addView(TextView(this).apply {
            setText(R.string.trip_too_short)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(6f).toInt(), 0, 0)
        })
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
        val view = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24f).toInt(), dp(4f).toInt(), dp(24f).toInt(), 0) }
        fun group(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
            view.addView(TextView(this).apply {
                text = title; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdTextPrimary))
                setPadding(0, dp(12f).toInt(), 0, dp(2f).toInt())
            })
            val radios = android.widget.RadioGroup(this)
            options.forEachIndexed { i, name ->
                radios.addView(com.google.android.material.radiobutton.MaterialRadioButton(this).apply {
                    id = View.generateViewId(); text = name; isChecked = i == selected
                    setOnCheckedChangeListener { _, on -> if (on) onPick(i) }
                })
            }
            view.addView(radios)
        }
        group("Speed and distance", listOf("Kilometres (km/h, km)", "Miles (mph, mi)"), if (mph) 1 else 0) { mph = it == 1 }
        group("Temperature", listOf("Celsius (°C)", "Fahrenheit (°F)"), if (fahrenheit) 1 else 0) { fahrenheit = it == 1 }
        view.addView(TextView(this).apply {
            text = "Recorded drives (CSV) and the adapter log stay in the car's own metric units."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)); setPadding(0, dp(10f).toInt(), 0, 0)
        })
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.units)
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                if (mph != Units.mph || fahrenheit != Units.fahrenheit) {
                    prefs.edit().putBoolean(AppPrefs.UNITS_MPH, mph).putBoolean(AppPrefs.UNITS_FAHRENHEIT, fahrenheit).apply()
                    AppPrefs.applyUnits(prefs)
                    recreate() // rebuilds every readout in the new units
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Car scan ----------

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
        line("${readings.size} standard readings. Ones marked ✓ are on the app's Dashboard or live-data list.")
        heading("Standard readings")
        readings.forEach { n -> line(String.format(Locale.US, "%s  %02X  %s", if (CarCoverage.shownByApp(n)) "✓" else "·", n, CarCoverage.name(n)), secondary = !CarCoverage.shownByApp(n)) }
        val missing = Pids.dashboard.filter { it.number !in scan.supported }
        if (missing.isNotEmpty()) {
            heading("Not reported by your car")
            missing.forEach { p -> line("${p.label}" + (CarCoverage.STAND_INS.firstOrNull { it.forHex == p.hex && it.pid.number in scan.supported }?.let { " (using ${it.note.substringBefore(",")})" } ?: "")) }
        }
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
                    ObdSession.state.value.vin?.let { appendLine("VIN: $it") }
                    appendLine("Protocol: ${ObdProtocols.byCode(ObdSession.state.value.protocolCode)?.name ?: "unknown"}")
                    appendLine(); appendLine("Standard readings:")
                    readings.forEach { appendLine(String.format(Locale.US, "  %02X  %s", it, CarCoverage.name(it))) }
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
        sheet.show()
    }

    // ---------- Dashboard readouts ----------

    /**
     * Choose the readings in the Dashboard's main readouts: two on the liveries and Race
     * telemetry, three on the Digital cockpit and Cyberpunk. Picking one that's already used swaps them.
     */
    private fun showSlotPicker(chosen: MutableList<String>) {
        val names = listOf("Readout 1", "Readout 2", "Readout 3 (Digital cockpit and Cyberpunk)")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.slots)
            .setItems(names.mapIndexed { i, n -> "$n: ${readout(chosen[i]).label}" }.toTypedArray()) { _, slot ->
                val choices = readoutChoices
                MaterialAlertDialogBuilder(this)
                    .setTitle(names[slot])
                    .setSingleChoiceItems(choices.map { it.label }.toTypedArray(), choices.indexOfFirst { it.key == chosen[slot] }) { d, i ->
                        val pick = choices[i].key
                        val other = chosen.indexOf(pick)
                        if (other >= 0 && other != slot) chosen[other] = chosen[slot] // swap rather than show one twice
                        chosen[slot] = pick
                        d.dismiss()
                        showSlotPicker(chosen)
                    }
                    .setNegativeButton("Back") { _, _ -> showSlotPicker(chosen) }
                    .show()
            }
            .setPositiveButton("Save") { _, _ ->
                if (chosen != slots) {
                    prefs.edit().putString(PREF_SLOTS, chosen.joinToString(",")).apply()
                    recreate() // rebuilds the Dashboard with the new readouts
                }
            }
            .setNeutralButton("Reset") { _, _ ->
                prefs.edit().remove(PREF_SLOTS).apply()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.share_picture)
            .setItems(formats.map { it.label }.toTypedArray()) { _, i ->
                val bmp = DriveCardRenderer(this).render(trip, scoreStyle, savedCar()?.label, formats[i])
                val name = "OBD2Reader_drive_" + java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(java.util.Date(trip.startedAt)) + "_${formats[i].name.lowercase(Locale.US)}.png"
                val file = Exports.exportFile(this, name)
                file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                Exports.saveToPictures(this, file)
                Exports.share(this, file, "image/png", "My drive")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

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
        Pids.dashboard.map { Channel(it.hex, if (it.number == Pids.RPM) "RPM" else it.label, Units.unit(it.hex, it.unit), it.decimals, Units.value(it.hex, it.min), Units.value(it.hex, it.max)) } +
            Channel(VOLTAGE_KEY, "Battery", "V", 1, 11.0, 15.0)
    }

    private fun setupGraphs() {
        graphList = findViewById(R.id.graphList)
        graphRate = findViewById(R.id.graphRate)
        recordBtn = findViewById(R.id.recordButton)
        recordStatus = findViewById(R.id.recordStatus)
        LiveHistory.start()

        val chips = findViewById<ChipGroup>(R.id.graphChips)
        val chosen = (prefs.getString(PREF_GRAPHS, null) ?: "0C,0D,11").split(",").filter { it.isNotEmpty() }.toMutableSet()
        for (ch in channels) {
            chips.addView(Chip(this).apply {
                text = ch.label
                isCheckable = true
                isChecked = ch.key in chosen
                setOnCheckedChangeListener { button, on ->
                    if (on && chosen.size >= MAX_GRAPHS) {
                        button.isChecked = false
                        toast("Up to $MAX_GRAPHS graphs at once, so each one stays fast")
                        return@setOnCheckedChangeListener
                    }
                    if (on) chosen += ch.key else chosen -= ch.key
                    prefs.edit().putString(PREF_GRAPHS, chosen.joinToString(",")).apply()
                    buildGraphs(chosen)
                }
            })
        }
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
            if (LogRecorder.active.value != null) LogRecorder.stop() else LogRecorder.start(this)
        }
        findViewById<View>(R.id.savedLogsButton).setOnClickListener { showSavedDrives() }
        buildGraphs(chosen)
    }

    private fun buildGraphs(keys: Set<String>) {
        graphList.removeAllViews()
        graphStrips.clear()
        val fallback = themeColor(R.attr.obdAccent)
        for (ch in channels.filter { it.key in keys }) {
            val colour = when (ch.key) {
                RPM_KEY -> Channels.RPM
                SPEED_KEY -> Channels.SPEED
                else -> Channels.forKey(ch.key, fallback)
            }
            val strip = GraphStripView(this, ch.key, ch.label, ch.unit, ch.decimals, ch.min, ch.max, colour).apply {
                windowMs = graphWindowMs
                paused = graphsPaused
            }
            graphList.addView(strip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8f).toInt()
            })
            graphStrips += strip
        }
        updateFocus()
    }

    /** While the Graphs tab is open, the graphed readings are polled first, so they update as fast as possible. */
    private fun updateFocus() {
        ObdSession.focus = when {
            pageGraphs.visibility == View.VISIBLE -> graphStrips.map { it.key }.toSet()
            // The Dashboard's moving readings, in one request per cycle (six is a CAN car's limit),
            // so the dials update as often as the graphs do.
            pageDashboard.visibility == View.VISIBLE -> DASHBOARD_FOCUS
            else -> emptySet()
        }
    }

    private fun renderRecording(r: LogRecorder.Recording?) {
        recordBtn.isEnabled = r != null || ObdSession.state.value.isConnected
        recordBtn.text = getString(if (r == null) R.string.record_drive else R.string.stop_recording)
        recordBtn.kind = if (r == null) SkewButton.Kind.PRIMARY else SkewButton.Kind.DANGER
        recordStatus.text = if (r == null) getString(R.string.record_hint) else {
            val secs = (System.currentTimeMillis() - r.startedMs) / 1000
            String.format(Locale.US, "Recording %d:%02d, %,d rows. Saved as %s", secs / 60, secs % 60, r.rows, r.file.name)
        }
    }

    /** Lists recorded drives: tap one to share it, or delete it. */
    private fun showSavedDrives() {
        val files = LogRecorder.saved(this)
        if (files.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.saved_drives)
                .setMessage("No drives recorded yet. Connect, open Graphs and tap Record drive.")
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
        val drive = DriveTrack.parse(file.readLines())
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                if (!ObdSession.state.value.isConnected) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        listOf(R.id.carRow, R.id.protocolRow, R.id.logCard, R.id.helpRow).forEach { findViewById<View>(it).background = panelBackground() }
        val vinDecal = findViewById<VinDecalView>(R.id.vinDecal)
        vinText.setTextColor(vinDecal.inkColor)
        findViewById<TextView>(R.id.vinLabel).setTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(vinDecal.inkColor, 180))

        roundel = findViewById(R.id.rpmRoundel)
        startLights = findViewById(R.id.startLights)
        shiftLights = findViewById(R.id.shiftLights)
        trace = findViewById(R.id.teleTrace)
        teleRpm = findViewById(R.id.teleRpm)
        teleSpeed = findViewById(R.id.teleSpeed)
        (teleRpm as FitTextView).sample = "8,888"
        (teleSpeed as FitTextView).sample = "888"
        (findViewById<View>(R.id.teleCoolant) as FitTextView).sample = "Coolant     888 °C"
        teleCoolant = findViewById(R.id.teleCoolant)
        teleFuel = findViewById(R.id.teleFuel)
        cockpitRing = findViewById(R.id.cockpitRing)
        cyberHud = findViewById(R.id.cyberHud)
        val blank = getString(R.string.dash)
        teleRpmText = SmoothText(teleRpm, blank, settle = 2f) { String.format(Locale.US, "%,d", ((it / 10f).roundToInt() * 10)) }
        teleSpeedText = SmoothText(teleSpeed, blank) { it.roundToInt().toString() }
        // The two readouts under the trace: the first two chosen readings.
        listOf(teleCoolant to slots[0], teleFuel to slots[1]).forEach { (view, key) ->
            val r = readout(key)
            val gap = if (r.label.length > 8) "   " else "     "
            (view as FitTextView).sample = "${r.label}${gap}888.8 ${r.unit}"
            teleLabels[key] = "${r.label}$gap"
            teleTexts[key] = SmoothText(view, "${r.label}$gap$blank ${r.unit}", settle = if (r.decimals > 0) 0.01f else 0.05f) {
                "${r.label}$gap${String.format(Locale.US, "%.${r.decimals}f", it)} ${r.unit}"
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
                append(label).append("     ")
            }
            key("rpm", Channels.RPM); key(Units.speedUnit, Channels.SPEED); append("Last 30 s")
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
            box.addView(TextView(this).apply { text = label; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
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
            row.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
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

        // The live-data list: everything except the readings the dashboard already shows big.
        val shownBig = setOf(RPM_KEY, SPEED_KEY, slots[0], slots[1])
        Pids.dashboard.filter { it.hex !in shownBig }.forEach { p ->
            val r = readout(p.hex)
            addTile(p.hex, TowerRowView(this, r.label, r.unit, r.min, r.max, r.warn, r.decimals, Channels.forKey(p.hex, themeColor(R.attr.obdStripe1))))
        }
        if (VOLTAGE_KEY !in shownBig) addTile(VOLTAGE_KEY, TowerRowView(this, "Battery", "V", 11.0, 15.0, 15.0, 1, Channels.forKey(VOLTAGE_KEY, themeColor(R.attr.obdStripe1))))
    }

    private fun setRpm(value: Float?) {
        lastRpm = value
        roundel.setValue(value)
        shiftLights.setRpm(value, redline)
        teleRpmText.set(value)
        cockpitRing.set(lastSpeed, value)
        cyberHud.set(lastSpeed, value)
        if (ObdSession.state.value.isConnected) {
            trace.push(value, lastSpeed)
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
    }

    /** The top speed this drive, beside the speed in every style. */
    private fun showTopSpeed(top: Double?) {
        val t = top?.let { Units.speed(it) }
        decals[SPEED_KEY]?.top = t
        cockpitRing.top = t?.toFloat()
        cyberHud.top = t?.toFloat()
        findViewById<TextView>(R.id.teleSpeedUnit).setIfChanged(t?.let { "${Units.speedUnit} · top ${it.roundToInt()}" } ?: Units.speedUnit)
    }

    /** No reading from the car for [key]: every place it's shown says N/A, never a stale or made-up number. */
    private fun showNoReading(key: String) {
        tiles[key]?.setUnsupported()
        if (key !in slots) return
        decals[key]?.setUnsupported()
        cyberChips[key]?.setUnsupported()
        glass[key]?.showText("N/A")
        glassUnits[key]?.visibility = View.GONE
        teleTexts[key]?.showText("${teleLabels[key].orEmpty()}N/A")
    }

    private fun setBigReading(key: String, value: Double?) {
        if (value != null) glassUnits[key]?.visibility = View.VISIBLE
        decals[key]?.setValue(value)
        glass[key]?.set(value?.toFloat())
        cyberChips[key]?.setValue(value)
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
            if (s.isConnected) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
            lastReadings = s.readings
        }
        // A stand-in reading is labelled for what it really is.
        if (s.standIns != lastStandIns) {
            tiles.forEach { (k, t) -> t.labelOverride = s.standIns[k] }
            decals.forEach { (k, d) -> d.labelOverride = s.standIns[k] }
            lastStandIns = s.standIns
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

    private fun showVin(vin: String?) {
        vinText.text = vin ?: "Not reported"
        val info = vin?.let { VinDecoder.decode(it) }
        showVinMismatch(info)
        vehicleText.text = when {
            info?.make != null && info.modelYear != null -> "${info.make}, ${info.modelYear} model year"
            info?.make != null -> info.make
            vin != null -> "Make not recognised from VIN"
            savedCar() != null -> "Your car: ${savedCar()?.label}. It doesn't report a VIN."
            else -> "Older cars often don't report a VIN. Live data and codes still work."
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
            typeface = uiTypeface(600)
        })
        list.addView(TextView(this).apply {
            setText(R.string.appearance_subtitle)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, dp(4f).toInt(), 0, dp(16f).toInt())
        })

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
            val selected = theme.id == current.id
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
        val max = if (redline == null) 8000f else ((redline / 1000 + 2) * 1000).toFloat()
        this.redline = (redline ?: 6500).toFloat()
        DriveScorer.setRedline(this.redline)
        roundel.max = max
        roundel.warnAt = this.redline
        cockpitRing.rpmMax = max
        cockpitRing.warnAt = this.redline
        cyberHud.rpmMax = max
        cyberHud.warnAt = this.redline
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
            prefs.edit().putBoolean(PREF_CAR_ASKED, true).apply()
            updateCarSetting()
            applyRedline()
            sheet.dismiss()
            if (lastCodes != null) lastCodes?.let { renderCodes(it) } // re-explain codes for the new make
            showVinMismatch(ObdSession.state.value.vin?.let { VinDecoder.decode(it) })
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
        val info = DtcLibrary.lookup(code, ObdSession.carMake(this))
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
        private const val VOLTAGE_KEY = ObdSession.VOLTAGE_KEY
        private val RPM_KEY = String.format(Locale.US, "%02X", Pids.RPM)
        private val SPEED_KEY = String.format(Locale.US, "%02X", Pids.SPEED)
        private const val COOLANT_KEY = "05"
        private const val PREF_GRAPHS = "graph_channels"
        private const val MAX_GRAPHS = 4
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
