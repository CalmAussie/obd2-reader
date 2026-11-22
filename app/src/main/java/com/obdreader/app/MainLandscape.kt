package com.obdreader.app

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.navigation.NavigationBarView
import com.obdreader.app.ui.DashLayout
import com.obdreader.app.ui.LiveryStripesView
import com.obdreader.app.ui.TowerRowView

// Landscape layout and the live-data list.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

internal fun MainActivity.move(view: View, to: ViewGroup, lp: ViewGroup.LayoutParams? = null) {
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
internal fun MainActivity.applyOrientation() {
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
/** In landscape, sizes the main dial to the visible height under the title, and keeps it fitted as that changes. */
internal fun MainActivity.fitHero() {
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
internal fun MainActivity.layoutTiles(columns: Int) {
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
internal fun MainActivity.addTile(key: String, tile: TowerRowView) {
    towerList.addView(tile, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(4f).toInt()
    })
    tiles[key] = tile
}
