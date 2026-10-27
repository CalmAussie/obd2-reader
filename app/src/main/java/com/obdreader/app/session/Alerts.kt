package com.obdreader.app.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.core.app.NotificationCompat
import com.obdreader.app.HomeActivity
import com.obdreader.app.R
import com.obdreader.app.obd.AlertRules
import com.obdreader.app.obd.DtcLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Warns the driver with a sound and vibration when something needs attention while driving:
 * the engine running hot, the battery not charging (or overcharging), or a new trouble code.
 * Alerts go out as high-priority notifications, which Android Auto shows on the car's screen
 * too. They can be switched off under Diagnostics → Alerts.
 */
object Alerts {

    private val rules = AlertRules()
    private var started = false
    private var lastCheck = 0L

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        AppPrefs.applyUnits(AppPrefs.of(app))
        createChannel(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            var last: ObdSession.Phase? = null
            var lastCodes: ObdSession.Codes? = null
            ObdSession.state.collect { s ->
                if (s.phase == ObdSession.Phase.CONNECTING && (last == null || last == ObdSession.Phase.IDLE || last == ObdSession.Phase.ERROR)) {
                    rules.reset(); lastCodes = null
                }
                last = s.phase
                if (!s.isConnected) return@collect
                val codes = s.codes
                if (codes != null && codes != lastCodes) {
                    lastCodes = codes
                    val make = ObdSession.carMake(app)
                    val year = ObdSession.carYear(app)
                    rules.codes(codes.stored + codes.pending) { DtcLibrary.title(it, make, year) }.forEach { notify(app, it) }
                }
                // Readings arrive many times a second; once a second is plenty for these.
                val now = SystemClock.elapsedRealtime()
                if (now - lastCheck < 1000) return@collect
                lastCheck = now
                rules.readings(now, s.readings["0C"], s.readings["05"], s.readings["5C"], s.readings[ObdSession.VOLTAGE_KEY]).forEach { notify(app, it) }
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission") // checked just below
    private fun notify(context: Context, alert: AlertRules.Alert) {
        if (!AppPrefs.alerts(AppPrefs.of(context))) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 10 + alert.kind.ordinal, Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(open)
            // Android Auto shows it as a heads-up card on the car's screen.
            .extend(CarAppExtender.Builder().setContentTitle(alert.title).setContentText(alert.text).setContentIntent(open).build())
        val id = NOTIFICATION_BASE + alert.kind.ordinal * 100 + (alert.code?.hashCode()?.mod(100) ?: 0)
        try {
            CarNotificationManager.from(context).notify(id, builder)
        } catch (e: SecurityException) {
            // Notifications not allowed: nothing more to do.
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Driving alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Engine running hot, battery not charging and new trouble codes"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private const val CHANNEL_ID = "alerts"
    private const val NOTIFICATION_BASE = 1000
}
