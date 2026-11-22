package com.obdreader.app

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.obdreader.app.MainActivity.Companion.RPM_KEY
import com.obdreader.app.MainActivity.Companion.SPEED_KEY
import com.obdreader.app.MainActivity.Companion.THROTTLE_KEY
import com.obdreader.app.MainActivity.Companion.VOLTAGE_KEY
import com.obdreader.app.MainActivity.Readout
import com.obdreader.app.obd.Boost
import com.obdreader.app.obd.ChargingStatus
import com.obdreader.app.obd.GForce
import com.obdreader.app.obd.Pids
import com.obdreader.app.obd.Units
import com.obdreader.app.session.LiveHistory
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.Channels
import com.obdreader.app.ui.CyberChipView
import com.obdreader.app.ui.DashLayout
import com.obdreader.app.ui.DecalView
import com.obdreader.app.ui.FitTextView
import com.obdreader.app.ui.LiveryStripesView
import com.obdreader.app.ui.SmoothText
import com.obdreader.app.ui.TowerRowView
import com.obdreader.app.ui.VinDecalView
import com.obdreader.app.ui.dashLayout
import com.obdreader.app.ui.panelBackground
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.uiTypeface
import java.util.Locale

// The chosen style's dashboard and its readings.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

/** Shows the chosen style's dashboard and builds its readings. */
internal fun MainActivity.setupDashboard() {
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
    gMeter.legend = findViewById(R.id.teleLegend)
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
            append(label).append("   ")
        }
        key("rpm", Channels.RPM); key(Units.speedUnit, Channels.SPEED); key("throttle", Channels.THROTTLE); append("Last\u00A030\u00A0s") // non-breaking spaces: it wraps as one piece
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
        addTile(p.hex, TowerRowView(this, r.label, r.unit, r.min, r.max, r.warn, r.decimals, onPanels(Channels.forKey(p.hex, themeColor(R.attr.obdStripe1)))))
    }
    if (Boost.KEY !in shownBig) readout(Boost.KEY).let { r ->
        addTile(Boost.KEY, TowerRowView(this, r.label, r.unit, r.min, r.max, r.warn, r.decimals, onPanels(Channels.forKey(Boost.KEY, themeColor(R.attr.obdStripe1)))))
    }
    if (VOLTAGE_KEY !in shownBig) addTile(VOLTAGE_KEY, TowerRowView(this, "Battery", "V", 11.0, 15.0, 15.0, 1, onPanels(Channels.forKey(VOLTAGE_KEY, themeColor(R.attr.obdStripe1)))))
    // G-force from the phone, at the end of the list.
    GForce.KEYS.filter { it !in shownBig }.forEach { k ->
        val r = readout(k)
        addTile(k, TowerRowView(this, r.label, r.unit, r.min, r.max, r.warn, r.decimals, onPanels(Channels.forKey(k, themeColor(R.attr.obdStripe1)))))
    }
}
/**
 * Fills the Dashboard's traces (Race telemetry's trace, the Cyberpunk bars and each reading's little
 * trace) from the last minute of readings kept outside the screen. Before, they lived only in the
 * screen, so changing style (which draws the screen again) or a spell on another tab started them empty.
 */
internal fun MainActivity.seedDashTraces() {
    if (!ObdSession.state.value.isConnected) return
    val now = android.os.SystemClock.elapsedRealtime()
    fun points(key: String): List<Double?> {
        val ser = LiveHistory.series(key)
        val (times, values) = synchronized(LiveHistory) { ser.times.toList() to ser.values.toList() }
        return com.obdreader.app.obd.TraceSeed.steps(times, values, now, TowerRowView.TRACE_STEP_MS, TowerRowView.HISTORY)
    }
    val rpm = points(RPM_KEY).map { it?.toFloat() }
    val speed = points(SPEED_KEY).map { v -> Units.value(SPEED_KEY, v)?.toFloat() }
    trace.seed(rpm, speed, points(THROTTLE_KEY).map { it?.toFloat() })
    // The trace takes the speed from the last speed reading shown. A screen drawn again has none
    // until the next one arrives, which drew a drop to zero in the middle of the carried-on trace.
    if (lastSpeed == null) lastSpeed = speed.lastOrNull()
    cyberHud.seed(com.obdreader.app.obd.TraceSeed.latestRun(rpm))
    tiles.forEach { (key, tile) -> tile.seedTrace(com.obdreader.app.obd.TraceSeed.latestRun(points(key).map { Units.value(key, it) })) }
}
internal fun MainActivity.setRpm(value: Float?) {
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
internal fun MainActivity.setSpeed(value: Float?) {
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
internal fun MainActivity.showMotionStatus(status: com.obdreader.app.session.PhoneMotion.Status) {
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
internal fun MainActivity.showTopSpeed(top: Double?) {
    val t = top?.let { Units.speed(it) }
    decals[SPEED_KEY]?.top = t
    cockpitRing.top = t?.toFloat()
    cyberHud.top = t?.toFloat()
    retroDash.top = t?.toFloat()
    hairlineDial.top = t?.toFloat()
    rallyBlocks[SPEED_KEY]?.note = t?.let { "Top ${it.roundToInt()}" }
    findViewById<TextView>(R.id.teleSpeedUnit).setIfChanged(t?.let { "${Units.speedUnit} · top ${it.roundToInt()}" } ?: Units.speedUnit)
}
/** Shows the estimated charging status beside the battery voltage, wherever the battery is shown. */
internal fun MainActivity.showCharging(state: ChargingStatus.State?) {
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
internal fun MainActivity.showNoReading(key: String) {
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
internal fun MainActivity.setBigReading(key: String, value: Double?) {
    if (value != null) glassUnits[key]?.visibility = View.VISIBLE
    decals[key]?.setValue(value)
    glass[key]?.set(value?.toFloat())
    cyberChips[key]?.setValue(value)
    retroChips[key]?.setValue(value)
    rallyBlocks[key]?.setValue(value)
    hairlineRows[key]?.setValue(value)
    teleTexts[key]?.set(value?.toFloat())
}
