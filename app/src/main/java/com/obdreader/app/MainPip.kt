package com.obdreader.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.obdreader.app.MainActivity.Companion.PREF_PIP
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.displayTypeface
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.uiTypeface

// Picture-in-picture (the overrides stay in MainActivity).
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

// ---------- Picture-in-picture ----------

/** True when this phone can show a floating picture-in-picture window. */
internal fun MainActivity.pipSupported(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
/** Picture-in-picture when leaving: switched on, supported, and connected to a car (or the demo). */
internal fun MainActivity.pipWanted(): Boolean = pipSupported() && prefs.getBoolean(PREF_PIP, false) && ObdSession.state.value.isConnected
internal fun MainActivity.pipParams(): android.app.PictureInPictureParams {
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
internal fun MainActivity.updatePipParams() {
    if (!pipSupported()) return
    try { setPictureInPictureParams(pipParams()) } catch (e: Exception) { android.util.Log.w("OBD2Pip", "params: ${e.message}") }
}
internal fun MainActivity.onStateForPip(s: ObdSession.State) {
    if (s.isConnected != pipConnected) { pipConnected = s.isConnected; updatePipParams() }
    if (isInPictureInPictureMode) drawPip()
}
/** The floating window: your widget readings, the first one big, in your style's colours. */
internal fun MainActivity.buildPipView(): FrameLayout = FrameLayout(this).apply {
    setBackgroundColor(themeColor(R.attr.obdBg))
    val col = LinearLayout(this@buildPipView).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        val p = dp(8f).toInt(); setPadding(p, p, p, p)
    }
    pipStatus = TextView(this@buildPipView).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); gravity = Gravity.CENTER; maxLines = 1; setTextColor(themeColor(R.attr.obdTextSecondary)) }
    pipMainBox = LinearLayout(this@buildPipView).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; val p = dp(4f).toInt(); setPadding(p * 2, p, p * 2, p) }
    pipMainLabel = TextView(this@buildPipView).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); gravity = Gravity.CENTER; maxLines = 1 }
    pipMain = TextView(this@buildPipView).apply { typeface = displayTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f); gravity = Gravity.CENTER; maxLines = 1; fontFeatureSettings = "tnum" }
    pipMainBox.addView(pipMainLabel); pipMainBox.addView(pipMain)
    col.addView(pipStatus); col.addView(pipMainBox)
    val row = LinearLayout(this@buildPipView).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(4f).toInt(), 0, 0) }
    pipSmall.clear()
    repeat(2) {
        val cell = LinearLayout(this@buildPipView).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        // Centred in their half of the window, like the big reading above.
        val l = TextView(this@buildPipView).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); maxLines = 1; gravity = Gravity.CENTER; setTextColor(themeColor(R.attr.obdTextSecondary)) }
        val v = TextView(this@buildPipView).apply { typeface = uiTypeface(600); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); maxLines = 1; gravity = Gravity.CENTER; setTextColor(themeColor(R.attr.obdTextPrimary)); fontFeatureSettings = "tnum" }
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
internal fun MainActivity.sizePip(h: Int) {
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
internal fun MainActivity.drawPip() {
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
