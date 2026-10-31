package com.obdreader.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import com.obdreader.app.HomeActivity
import com.obdreader.app.R
import com.obdreader.app.obd.ShareScore
import com.obdreader.app.obd.TopSpeeds
import com.obdreader.app.obd.TripSummary
import com.obdreader.app.obd.VehicleCatalog
import com.obdreader.app.obd.WidgetContent
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.session.DriveScorer
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.AppIcons
import com.obdreader.app.ui.AppThemes
import com.obdreader.app.ui.themeColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The home-screen widget: live speed, coolant and battery while connected; otherwise the last
 * battery reading, trouble-code check and drive. Its button connects to the last adapter (opening
 * the app) or disconnects; tapping anywhere else opens the app. Drawn in the chosen style's colours.
 */
class ObdWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Feed.start(context)
        refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle?) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISCONNECT) {
            android.util.Log.i("OBD2Widget", "disconnect from the widget")
            ObdSession.disconnect()
            refresh(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_DISCONNECT = "com.obdreader.app.widget.DISCONNECT"
        /** Asks the app's screen to connect to the last adapter, as its Connect button would. */
        const val EXTRA_CONNECT = "widget_connect"
        private const val PREF_BATTERY = "widget_battery"
        private const val PREF_CODES = "widget_codes"
        /** The readings chosen under Diagnostics → Widget readings, in order. */
        const val PREF_READINGS = "widget_readings"

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ObdWidget::class.java))

        /** Redraws every widget on the home screen (cheap when there are none), each in the layout for its size. */
        fun refresh(context: Context, readings: Map<String, Double?>? = null) {
            val app = context.applicationContext
            val ids = ids(app)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(app)
            val content = content(app, readings)
            // Nothing on it changed (the eased numbers already landed): leave the widget as it is.
            if (content == lastDrawn && readings != null) return
            lastDrawn = content
            for (id in ids) {
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    // Android 12 and newer: the launcher picks the layout for the widget's actual size
                    // (the biggest that fits), including after it's resized.
                    manager.updateAppWidget(id, RemoteViews(Size.values().associate { android.util.SizeF(it.minWidth, it.minHeight) to views(app, content, it) }))
                } else {
                    manager.updateAppWidget(id, views(app, content, Size.of(manager.getAppWidgetOptions(id))))
                }
            }
            android.util.Log.i("OBD2Widget", "widget: ${content.status}; " + content.cells.joinToString("; ") { "${it.label}=${it.value} ${it.sub}".trim() } + "; button ${content.button}")
        }

        /** The widget's size on the home screen, in cells: 2 × 1, 4 × 1, 2 × 2 or 4 × 2 (and anything bigger). */
        /**
         * The widget's layouts, with the smallest size (in dp) each needs, and what each has. A
         * one-row widget is under 120 dp tall on every launcher (about 80 dp on Samsung's), a
         * one-column one under 100 dp wide, so the bigger layouts start there. [cells] is how many
         * readings it shows; [labels] whether they're named; [notes] whether each has a line
         * under it; [status] the connection's status; [button] Connect or Disconnect; [title] the
         * app's name. Ones without a button open the app when tapped.
         */
        enum class Size(
            val layout: Int, val minWidth: Float, val minHeight: Float,
            val cells: Int, val labels: Boolean, val notes: Boolean, val status: Boolean, val button: Boolean, val title: Boolean,
        ) {
            SMALL(R.layout.widget_obd_small, 100f, 40f, 1, false, false, true, false, false),          // 2 × 1
            WIDE(R.layout.widget_obd_wide, 230f, 40f, 3, true, false, false, true, false),             // 4 × 1
            SQUARE(R.layout.widget_obd_square, 100f, 120f, 3, true, false, true, true, false),        // 2 × 2
            LARGE(R.layout.widget_obd, 230f, 120f, 3, true, true, true, true, true),                  // 4 × 2
            NARROW(R.layout.widget_obd_narrow, 40f, 120f, 1, true, false, false, false, false),       // 1 × 2
            NARROW_TALL(R.layout.widget_obd_narrow_tall, 40f, 250f, 3, true, false, false, false, false), // 1 × 4
            TALL(R.layout.widget_obd_tall, 100f, 250f, 3, true, true, true, true, false);             // 2 × 4

            companion object {
                /** Before Android 12, from the size the launcher reports: the biggest layout that fits. */
                fun of(options: android.os.Bundle?): Size {
                    val w = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
                    val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
                    if (w == 0 || h == 0) return LARGE
                    return values().filter { w >= it.minWidth && h >= it.minHeight }.maxByOrNull { it.minWidth * it.minHeight } ?: SMALL
                }
            }
        }

        /**
         * The widget's colours, for the phone's light and dark mode. They're the chosen style's;
         * a light style (Pit lane) is shown dark while the phone is in dark mode, with the style's
         * own accent, so the widget never glares on a dark home screen.
         */
        private class Palette(val surface: Int, val text: Int, val muted: Int, val accent: Int, val onAccent: Int, val outline: Int, val second: Int) {
            /** Text on the style's second colour: near-black on a light one, white on a dark one. */
            val onSecond: Int get() = if (androidx.core.graphics.ColorUtils.calculateLuminance(second) > 0.3) 0xFF111214.toInt() else 0xFFFFFFFF.toInt()
        }

        private fun palettes(context: Context): Pair<Palette, Palette> {
            val themed = ContextThemeWrapper(context, AppThemes.byId(AppPrefs.of(context).getString(AppPrefs.THEME, null)).style)
            val light = android.util.TypedValue().let { themed.theme.resolveAttribute(R.attr.obdIsLight, it, true) && it.data != 0 }
            val style = Palette(
                themed.themeColor(R.attr.obdSurface), themed.themeColor(R.attr.obdTextPrimary), themed.themeColor(R.attr.obdTextSecondary),
                themed.themeColor(R.attr.obdAccent), themed.themeColor(R.attr.obdOnAccent), themed.themeColor(R.attr.obdOutline),
                themed.themeColor(R.attr.obdStripe2),
            )
            val dark = if (!light) style else {
                val base = ContextThemeWrapper(context, R.style.Theme_ObdReader)
                Palette(base.themeColor(R.attr.obdSurface), base.themeColor(R.attr.obdTextPrimary), base.themeColor(R.attr.obdTextSecondary),
                    style.accent, style.onAccent, base.themeColor(R.attr.obdOutline), style.second)
            }
            return style to dark
        }

        private fun nightNow(context: Context) =
            (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        /**
         * Sets a colour on one of the widget's views, for both light and dark mode on Android 12 and
         * newer (the launcher switches as the phone does), otherwise for the mode the phone is in now.
         * Shapes are tinted through their image tint; text through its text colour.
         */
        private class Painter(val views: RemoteViews, val night: Boolean) {
            fun tint(id: Int, day: Int, dark: Int) {
                if (android.os.Build.VERSION.SDK_INT >= 31) views.setColorStateList(id, "setImageTintList", android.content.res.ColorStateList.valueOf(day), android.content.res.ColorStateList.valueOf(dark))
                else views.setInt(id, "setColorFilter", if (night) dark else day)
            }
            /** A reading near its maximum: its cell takes the style's second colour; otherwise no background. */
            fun cell(id: Int, on: Boolean, day: Int, dark: Int) {
                views.setInt(id, "setBackgroundResource", if (on) R.drawable.widget_cell else 0)
                if (!on) return
                if (android.os.Build.VERSION.SDK_INT >= 31) views.setColorStateList(id, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(day), android.content.res.ColorStateList.valueOf(dark))
                else views.setInt(id, "setBackgroundColor", if (night) dark else day)
            }
            fun text(id: Int, day: Int, dark: Int) {
                if (android.os.Build.VERSION.SDK_INT >= 31) views.setColorInt(id, "setTextColor", day, dark)
                else views.setTextColor(id, if (night) dark else day)
            }
        }

        /** What the widget last showed, so an update that changes nothing isn't sent. */
        @Volatile private var lastDrawn: WidgetContent.Content? = null

        fun content(context: Context, shownReadings: Map<String, Double?>? = null): WidgetContent.Content {
            val prefs = AppPrefs.of(context)
            val s = ObdSession.state.value
            val phase = when (s.phase) {
                ObdSession.Phase.CONNECTING -> WidgetContent.Phase.CONNECTING
                ObdSession.Phase.CONNECTED -> WidgetContent.Phase.CONNECTED
                ObdSession.Phase.WAITING -> WidgetContent.Phase.IGNITION_OFF
                else -> WidgetContent.Phase.IDLE
            }
            val trip = TripSummary.decode(prefs.getString(DriveScorer.PREF_LAST_TRIP, null))
            val car = AppPrefs.savedCar(prefs)
            return WidgetContent.build(
                phase = phase,
                adapterName = s.adapterName,
                isDemo = s.isDemo,
                readings = shownReadings ?: s.readings,
                unsupported = s.unsupported,
                mph = prefs.getBoolean(AppPrefs.UNITS_MPH, false),
                fahrenheit = prefs.getBoolean(AppPrefs.UNITS_FAHRENHEIT, false),
                battery = WidgetContent.decodeSeen(prefs.getString(PREF_BATTERY, null)),
                codes = WidgetContent.decodeCodes(prefs.getString(PREF_CODES, null)),
                lastDrive = trip?.let { WidgetContent.LastDrive(it.distanceKm, ShareScore.of(it.scores, raceTelemetry = false).score, it.endedAt) },
                nowMs = System.currentTimeMillis(),
                live = WidgetContent.decodeReadings(prefs.getString(PREF_READINGS, null)),
                redline = car?.let { VehicleCatalog.recommend(it).redline },
                topSpeedKmh = car?.let { TopSpeeds.forCar(it)?.first },
            )
        }

        private fun views(context: Context, c: WidgetContent.Content, size: Size): RemoteViews {
            val (d, n) = palettes(context)
            return RemoteViews(context.packageName, size.layout).apply {
                val p = Painter(this, nightNow(context))
                p.tint(R.id.widgetBg, d.surface, n.surface)
                setImageViewResource(R.id.widgetIcon, AppIcons.current(context).icon)
                if (size.title) p.text(R.id.widgetTitle, d.text, n.text)
                if (size.status) {
                    // A one-reading size with no labels says which reading it is: "Speed · Connected".
                    setTextViewText(R.id.widgetStatus, if (!size.labels) "${c.cells[0].label} · ${c.status}" else c.status)
                    when {
                        // 2 × 1 has its status inside the reading's cell, so it follows the cell's highlight.
                        !size.labels && c.cells[0].highlight -> p.text(R.id.widgetStatus, d.onSecond, n.onSecond)
                        c.connected -> p.text(R.id.widgetStatus, d.accent, n.accent)
                        else -> p.text(R.id.widgetStatus, d.muted, n.muted)
                    }
                }
                // Sizes with a line under each reading show the unit there; the others with the value.
                fun valueText(cell: WidgetContent.Cell) = if (size.notes) cell.value else cell.withUnit
                val labels = listOf(R.id.widgetLabel1, R.id.widgetLabel2, R.id.widgetLabel3)
                val values = listOf(R.id.widgetValue1, R.id.widgetValue2, R.id.widgetValue3)
                val subs = listOf(R.id.widgetSub1, R.id.widgetSub2, R.id.widgetSub3)
                // Fewer than three readings chosen: the rest close up.
                if (size.cells > 1) listOf(R.id.widgetCell1, R.id.widgetCell2, R.id.widgetCell3).forEachIndexed { i, id ->
                    setViewVisibility(id, if (i < c.cells.size) android.view.View.VISIBLE else android.view.View.GONE)
                }
                val cellIds = listOf(R.id.widgetCell1, R.id.widgetCell2, R.id.widgetCell3)
                c.cells.take(size.cells).forEachIndexed { i, cell ->
                    // Near the redline or the car's top speed: the cell lights up in the style's second colour.
                    val hot = cell.highlight
                    p.cell(cellIds[i], hot, d.second, n.second)
                    val (mainD, mainN) = if (hot) d.onSecond to n.onSecond else if (cell.value == WidgetContent.NA) d.muted to n.muted else d.text to n.text
                    val (minorD, minorN) = if (hot) d.onSecond to n.onSecond else d.muted to n.muted
                    if (size.labels) { setTextViewText(labels[i], cell.label); p.text(labels[i], minorD, minorN) }
                    setTextViewText(values[i], valueText(cell))
                    p.text(values[i], mainD, mainN)
                    if (size.notes) { setTextViewText(subs[i], cell.sub); p.text(subs[i], minorD, minorN) }
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val open = PendingIntent.getActivity(context, 0, Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
                setOnClickPendingIntent(R.id.widgetRoot, open)
                if (size.button) {
                    // Connect is the style's accent; Disconnect an outline-coloured button, as in the app.
                    if (c.button == "Connect") p.tint(R.id.widgetButtonBg, d.accent, n.accent) else p.tint(R.id.widgetButtonBg, d.outline, n.outline)
                    setTextViewText(R.id.widgetButtonText, c.button)
                    if (c.button == "Connect") p.text(R.id.widgetButtonText, d.onAccent, n.onAccent) else p.text(R.id.widgetButtonText, d.text, n.text)
                    setContentDescription(R.id.widgetButton, c.button)
                    val button = if (c.button == "Connect") {
                        PendingIntent.getActivity(context, 1, Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_CONNECT, true), flags)
                    } else {
                        PendingIntent.getBroadcast(context, 2, Intent(context, ObdWidget::class.java).setAction(ACTION_DISCONNECT), flags)
                    }
                    setOnClickPendingIntent(R.id.widgetButton, button)
                }
            }
        }

        /** Keeps the last real (never demo) battery reading and code check, for when the car isn't connected. */
        internal fun remember(context: Context, s: ObdSession.State) {
            if (s.isDemo) return
            val prefs = AppPrefs.of(context)
            val now = System.currentTimeMillis()
            val edit = prefs.edit()
            s.readings[ObdSession.VOLTAGE_KEY]?.let { edit.putString(PREF_BATTERY, WidgetContent.encodeSeen(WidgetContent.Seen(it, now))) }
            s.codes?.let { edit.putString(PREF_CODES, WidgetContent.encodeCodes(WidgetContent.CodesSeen(it.stored.size, it.pending.size, now))) }
            edit.apply()
        }
    }

    /**
     * Follows the connection and redraws the widget: at once when the connection starts, ends or
     * the codes change, and every few seconds while live readings change.
     */
    object Feed {
        private var started = false
        /** How often the widget's live numbers move while connected: often enough to roll smoothly, without flooding the launcher. */
        private const val TICK_MS = 400L

        fun start(context: Context) {
            if (started) return
            started = true
            val app = context.applicationContext
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            // At once when the connection starts, ends or the codes change.
            scope.launch {
                var lastPhase: ObdSession.Phase? = null
                var lastCodes: ObdSession.Codes? = null
                var lastSave = 0L
                ObdSession.state.collect { s ->
                    val now = System.currentTimeMillis()
                    val changed = s.phase != lastPhase || s.codes != lastCodes
                    if (changed || now - lastSave >= 60_000) { remember(app, s); lastSave = now }
                    if (changed) {
                        lastPhase = s.phase; lastCodes = s.codes
                        refresh(app)
                    }
                }
            }
            // While connected, the live numbers ease towards each new reading a few times a second,
            // so they roll up and down instead of jumping. Paused while the phone's screen is off.
            scope.launch {
                val smoother = com.obdreader.app.obd.WidgetSmoother()
                val power = app.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                var last = android.os.SystemClock.uptimeMillis()
                while (true) {
                    kotlinx.coroutines.delay(TICK_MS)
                    val now = android.os.SystemClock.uptimeMillis()
                    val s = ObdSession.state.value
                    if (s.phase != ObdSession.Phase.CONNECTED || !power.isInteractive || ids(app).isEmpty()) {
                        smoother.reset(); last = now; continue
                    }
                    refresh(app, smoother.step(s.readings, now - last))
                    last = now
                }
            }
        }
    }
}
