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
import com.obdreader.app.obd.TripSummary
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

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ObdWidget::class.java))

        /** Redraws every widget on the home screen (cheap when there are none), each in the layout for its size. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val ids = ids(app)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(app)
            val content = content(app)
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
         * The widget's layouts, with the smallest size (in dp) each needs. A one-row widget is under
         * 120 dp tall on every launcher (about 80 dp on Samsung's), so the two-row layouts start there.
         */
        enum class Size(val layout: Int, val minWidth: Float, val minHeight: Float) {
            SMALL(R.layout.widget_obd_small, 100f, 40f), WIDE(R.layout.widget_obd_wide, 230f, 40f),
            SQUARE(R.layout.widget_obd_square, 100f, 120f), LARGE(R.layout.widget_obd, 230f, 120f);

            companion object {
                /** Before Android 12, from the size the launcher reports. */
                fun of(options: android.os.Bundle?): Size {
                    val w = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
                    val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
                    if (w == 0 || h == 0) return LARGE
                    val wide = w >= LARGE.minWidth
                    val tall = h >= LARGE.minHeight
                    return when { wide && tall -> LARGE; wide -> WIDE; tall -> SQUARE; else -> SMALL }
                }
            }
        }

        /**
         * The widget's colours, for the phone's light and dark mode. They're the chosen style's;
         * a light style (Pit lane) is shown dark while the phone is in dark mode, with the style's
         * own accent, so the widget never glares on a dark home screen.
         */
        private class Palette(val surface: Int, val text: Int, val muted: Int, val accent: Int, val onAccent: Int, val outline: Int)

        private fun palettes(context: Context): Pair<Palette, Palette> {
            val themed = ContextThemeWrapper(context, AppThemes.byId(AppPrefs.of(context).getString(AppPrefs.THEME, null)).style)
            val light = android.util.TypedValue().let { themed.theme.resolveAttribute(R.attr.obdIsLight, it, true) && it.data != 0 }
            val style = Palette(
                themed.themeColor(R.attr.obdSurface), themed.themeColor(R.attr.obdTextPrimary), themed.themeColor(R.attr.obdTextSecondary),
                themed.themeColor(R.attr.obdAccent), themed.themeColor(R.attr.obdOnAccent), themed.themeColor(R.attr.obdOutline),
            )
            val dark = if (!light) style else {
                val base = ContextThemeWrapper(context, R.style.Theme_ObdReader)
                Palette(base.themeColor(R.attr.obdSurface), base.themeColor(R.attr.obdTextPrimary), base.themeColor(R.attr.obdTextSecondary),
                    style.accent, style.onAccent, base.themeColor(R.attr.obdOutline))
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
            fun text(id: Int, day: Int, dark: Int) {
                if (android.os.Build.VERSION.SDK_INT >= 31) views.setColorInt(id, "setTextColor", day, dark)
                else views.setTextColor(id, if (night) dark else day)
            }
        }

        fun content(context: Context): WidgetContent.Content {
            val prefs = AppPrefs.of(context)
            val s = ObdSession.state.value
            val phase = when (s.phase) {
                ObdSession.Phase.CONNECTING -> WidgetContent.Phase.CONNECTING
                ObdSession.Phase.CONNECTED -> WidgetContent.Phase.CONNECTED
                ObdSession.Phase.WAITING -> WidgetContent.Phase.IGNITION_OFF
                else -> WidgetContent.Phase.IDLE
            }
            val trip = TripSummary.decode(prefs.getString(DriveScorer.PREF_LAST_TRIP, null))
            return WidgetContent.build(
                phase = phase,
                adapterName = s.adapterName,
                isDemo = s.isDemo,
                readings = s.readings,
                unsupported = s.unsupported,
                mph = prefs.getBoolean(AppPrefs.UNITS_MPH, false),
                fahrenheit = prefs.getBoolean(AppPrefs.UNITS_FAHRENHEIT, false),
                battery = WidgetContent.decodeSeen(prefs.getString(PREF_BATTERY, null)),
                codes = WidgetContent.decodeCodes(prefs.getString(PREF_CODES, null)),
                lastDrive = trip?.let { WidgetContent.LastDrive(it.distanceKm, ShareScore.of(it.scores, raceTelemetry = false).score, it.endedAt) },
                nowMs = System.currentTimeMillis(),
            )
        }

        private fun views(context: Context, c: WidgetContent.Content, size: Size): RemoteViews {
            val (d, n) = palettes(context)
            return RemoteViews(context.packageName, size.layout).apply {
                val p = Painter(this, nightNow(context))
                p.tint(R.id.widgetBg, d.surface, n.surface)
                setImageViewResource(R.id.widgetIcon, AppIcons.current(context).icon)
                if (size == Size.LARGE) p.text(R.id.widgetTitle, d.text, n.text)
                if (size != Size.WIDE) {
                    // 2 × 1 has one reading, so its status line says which: "Speed · Connected".
                    setTextViewText(R.id.widgetStatus, if (size == Size.SMALL) "${c.cells[0].label} · ${c.status}" else c.status)
                    if (c.connected) p.text(R.id.widgetStatus, d.accent, n.accent) else p.text(R.id.widgetStatus, d.muted, n.muted)
                }
                // Smaller sizes put the unit with the value; 4 × 2 has a line of its own for it.
                fun valueText(cell: WidgetContent.Cell) = if (size == Size.LARGE) cell.value else cell.withUnit
                val labels = listOf(R.id.widgetLabel1, R.id.widgetLabel2, R.id.widgetLabel3)
                val values = listOf(R.id.widgetValue1, R.id.widgetValue2, R.id.widgetValue3)
                val subs = listOf(R.id.widgetSub1, R.id.widgetSub2, R.id.widgetSub3)
                val shown = if (size == Size.SMALL) 1 else 3
                c.cells.take(shown).forEachIndexed { i, cell ->
                    if (size != Size.SMALL) { setTextViewText(labels[i], cell.label); p.text(labels[i], d.muted, n.muted) }
                    setTextViewText(values[i], valueText(cell))
                    if (cell.value == WidgetContent.NA) p.text(values[i], d.muted, n.muted) else p.text(values[i], d.text, n.text)
                    if (size == Size.LARGE) { setTextViewText(subs[i], cell.sub); p.text(subs[i], d.muted, n.muted) }
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val open = PendingIntent.getActivity(context, 0, Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
                setOnClickPendingIntent(R.id.widgetRoot, open)
                if (size != Size.SMALL) {
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

        fun start(context: Context) {
            if (started) return
            started = true
            val app = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                var lastPhase: ObdSession.Phase? = null
                var lastCodes: ObdSession.Codes? = null
                var lastDraw = 0L
                var lastSave = 0L
                ObdSession.state.collect { s ->
                    val now = System.currentTimeMillis()
                    val changed = s.phase != lastPhase || s.codes != lastCodes
                    if (changed || now - lastSave >= 60_000) { remember(app, s); lastSave = now }
                    if (changed || now - lastDraw >= 3_000) {
                        lastPhase = s.phase; lastCodes = s.codes; lastDraw = now
                        refresh(app)
                    }
                }
            }
        }
    }
}
