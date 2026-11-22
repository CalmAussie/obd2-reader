package com.obdreader.app

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.obdreader.app.MainActivity.Companion.PREF_PERF_HISTORY
import com.obdreader.app.MainActivity.Companion.PREF_PERF_MODE
import com.obdreader.app.MainActivity.Companion.SPEED_KEY
import com.obdreader.app.obd.PerformanceTimer
import com.obdreader.app.obd.Units
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.applyTitleFonts
import com.obdreader.app.ui.displayTypeface
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.titleTypeface
import com.obdreader.app.ui.uiTypeface
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Performance timers.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

// ---------- Performance timers ----------

/** 0–100 km/h, 80–120 km/h and the quarter mile, timed from the car's speed, with a history. */
internal fun MainActivity.showPerformance() {
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
        setText(R.string.perf_quarter_estimate); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdWarnText))
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
