package com.obdreader.app

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.obdreader.app.MainActivity.Channel
import com.obdreader.app.MainActivity.Companion.DASHBOARD_FOCUS
import com.obdreader.app.MainActivity.Companion.MAX_GRAPHS
import com.obdreader.app.MainActivity.Companion.PREF_GRAPHS
import com.obdreader.app.MainActivity.Companion.PREF_GRAPH_TAB
import com.obdreader.app.MainActivity.Companion.RPM_KEY
import com.obdreader.app.MainActivity.Companion.SPEED_KEY
import com.obdreader.app.obd.GForce
import com.obdreader.app.obd.Pids
import com.obdreader.app.obd.Units
import com.obdreader.app.session.LiveHistory
import com.obdreader.app.session.LogRecorder
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.Channels
import com.obdreader.app.ui.GraphStripView
import com.obdreader.app.ui.themeBool
import com.obdreader.app.ui.themeColor
import java.util.Locale

// The Graphs tab.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

internal fun MainActivity.setupGraphs() {
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
internal fun MainActivity.graphLabel(key: String): String =
    ObdSession.state.value.relabels[key] ?: channels.firstOrNull { it.key == key }?.label ?: key
internal fun MainActivity.graphColour(key: String): Int = onPanels(when (key) {
    RPM_KEY -> Channels.RPM
    SPEED_KEY -> Channels.SPEED
    else -> Channels.forKey(key, themeColor(R.attr.obdAccent))
})
/**
 * A reading's channel colour as drawn on this style's panels. The channel colours are bright
 * for the dark styles; on the light ones (Pit lane, Rally stage) they're darkened, keeping
 * their hue, until they reach 3:1 on the page and its panels, so lines and bars stay visible.
 */
internal fun MainActivity.onPanels(colour: Int): Int =
    if (!themeBool(R.attr.obdIsLight)) colour
    else com.obdreader.app.obd.Contrast.darkenTo(colour, intArrayOf(themeColor(R.attr.obdBg), themeColor(R.attr.obdSurface), themeColor(R.attr.obdSurfaceHigh)))
/**
 * What can be graphed: once the car has said which readings it supports, only the extra readings
 * it reports (and any already chosen, which say N/A). Until then, or if the car won't say, all.
 */
internal fun MainActivity.graphChoices(supported: Set<Int>?): List<Channel> {
    val extraKeys = Pids.extraNumbers.map { it.hex }.toSet()
    return channels.filter { ch -> ch.key !in extraKeys || supported == null || ch.key.toInt(16) in supported || ch.key in graphChosen }
}
/** All, then each part of the car this car has readings for. Kept selected across visits. */
internal fun MainActivity.showGraphTabs(groups: List<com.obdreader.app.obd.ReadingGroups.Group>) {
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
internal fun MainActivity.showGraphChips() {
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
        setTextColor(themeColor(R.attr.obdAccentText))
        chipStrokeColor = android.content.res.ColorStateList.valueOf(themeColor(R.attr.obdAccent))
        setOnClickListener { showAddGraph() }
    })
}
/** Adds or removes one graph, up to [MAX_GRAPHS] and at least one. Returns whether it changed. */
internal fun MainActivity.toggleGraph(key: String): Boolean {
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
internal fun MainActivity.graphValueText(ch: Channel): String {
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
internal fun MainActivity.showAddGraph() {
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
                    addView(View(this@showAddGraph).apply {
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(graphColour(k)) }
                    }, LinearLayout.LayoutParams(dp(10f).toInt(), dp(10f).toInt()).apply { marginEnd = dp(12f).toInt() })
                    addView(TextView(this@showAddGraph).apply {
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
internal fun MainActivity.refreshGraphNotes(s: ObdSession.State) {
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
internal fun MainActivity.showGraphOrder(chosen: MutableList<String>) {
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
            fun move(by: Int, glyph: String, word: String) = MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
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
/**
 * Tap and hold a graph, then drag it up or down to move it. The others slide out of its way,
 * and the new order is saved when you let go. Also offered to screen readers as Move up / down.
 */
internal fun MainActivity.enableGraphDrag(strip: View, keys: MutableList<String>, label: String) {
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
internal fun MainActivity.buildGraphs(keys: List<String>) {
    graphList.removeAllViews()
    graphStrips.clear()
    if (::graphOrderBtn.isInitialized) graphOrderBtn.visibility = if (keys.size >= 2) View.VISIBLE else View.GONE
    if (::graphCount.isInitialized) showGraphChips()
    android.util.Log.i("OBD2Graphs", "order: ${keys.joinToString(",")}")
    val fallback = themeColor(R.attr.obdAccent)
    for (ch in keys.mapNotNull { k -> channels.firstOrNull { it.key == k } }) {
        val colour = onPanels(when (ch.key) {
            RPM_KEY -> Channels.RPM
            SPEED_KEY -> Channels.SPEED
            else -> Channels.forKey(ch.key, fallback)
        })
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
internal fun MainActivity.updateFocus() {
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
