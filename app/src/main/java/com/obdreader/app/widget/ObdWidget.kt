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

        /** Redraws every widget on the home screen (cheap when there are none). */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val ids = ids(app)
            if (ids.isEmpty()) return
            val content = content(app)
            val views = views(app, content)
            AppWidgetManager.getInstance(app).updateAppWidget(ids, views)
            android.util.Log.i("OBD2Widget", "widget: ${content.status}; " + content.cells.joinToString("; ") { "${it.label}=${it.value} ${it.sub}".trim() } + "; button ${content.button}")
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

        private fun views(context: Context, c: WidgetContent.Content): RemoteViews {
            val themed = ContextThemeWrapper(context, AppThemes.byId(AppPrefs.of(context).getString(AppPrefs.THEME, null)).style)
            val surface = themed.themeColor(R.attr.obdSurface)
            val text = themed.themeColor(R.attr.obdTextPrimary)
            val muted = themed.themeColor(R.attr.obdTextSecondary)
            val accent = themed.themeColor(R.attr.obdAccent)
            val onAccent = themed.themeColor(R.attr.obdOnAccent)
            val outline = themed.themeColor(R.attr.obdOutline)
            return RemoteViews(context.packageName, R.layout.widget_obd).apply {
                setInt(R.id.widgetBg, "setColorFilter", surface)
                setImageViewResource(R.id.widgetIcon, AppIcons.current(context).icon)
                setTextColor(R.id.widgetTitle, text)
                setTextViewText(R.id.widgetStatus, c.status)
                setTextColor(R.id.widgetStatus, if (c.connected) accent else muted)
                val labels = listOf(R.id.widgetLabel1, R.id.widgetLabel2, R.id.widgetLabel3)
                val values = listOf(R.id.widgetValue1, R.id.widgetValue2, R.id.widgetValue3)
                val subs = listOf(R.id.widgetSub1, R.id.widgetSub2, R.id.widgetSub3)
                c.cells.forEachIndexed { i, cell ->
                    setTextViewText(labels[i], cell.label); setTextColor(labels[i], muted)
                    setTextViewText(values[i], cell.value); setTextColor(values[i], if (cell.value == WidgetContent.NA) muted else text)
                    setTextViewText(subs[i], cell.sub); setTextColor(subs[i], muted)
                }
                // Connect is the style's accent; Disconnect an outline-coloured button, as in the app.
                setInt(R.id.widgetButtonBg, "setColorFilter", if (c.button == "Connect") accent else outline)
                setTextViewText(R.id.widgetButtonText, c.button)
                setTextColor(R.id.widgetButtonText, if (c.button == "Connect") onAccent else text)
                setContentDescription(R.id.widgetButton, c.button)

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val open = PendingIntent.getActivity(context, 0, Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
                setOnClickPendingIntent(R.id.widgetRoot, open)
                val button = if (c.button == "Connect") {
                    PendingIntent.getActivity(context, 1, Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_CONNECT, true), flags)
                } else {
                    PendingIntent.getBroadcast(context, 2, Intent(context, ObdWidget::class.java).setAction(ACTION_DISCONNECT), flags)
                }
                setOnClickPendingIntent(R.id.widgetButton, button)
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
