package com.obdreader.app.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.obdreader.app.MainActivity
import com.obdreader.app.R
import com.obdreader.app.obd.Pids
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Keeps the app alive while connected to the car (screen off, phone in a pocket,
 * Android Auto in front), and shows a notification with live readings and a
 * Disconnect button.
 */
class ObdConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastUpdate = 0L
    private var lastPhase: ObdSession.Phase? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(ObdSession.state.value), type)

        scope.launch {
            ObdSession.state.collect { s ->
                // Refresh at most every 2 seconds, plus whenever the connection state changes.
                val now = SystemClock.elapsedRealtime()
                if (s.phase != lastPhase || now - lastUpdate > 2000) {
                    lastPhase = s.phase
                    lastUpdate = now
                    getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(s))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) ObdSession.disconnect()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(s: ObdSession.State): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ObdConnectionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_speed)
            .setContentTitle(s.title)
            .setContentText(if (s.isConnected) liveSummary(s) else s.detail)
            .setContentIntent(open)
            .addAction(R.drawable.ic_close, "Disconnect", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun liveSummary(s: ObdSession.State): String {
        fun v(hex: String, unit: String) = s.readings[hex]?.let { String.format(Locale.US, "%,.0f %s", it, unit) }
        return listOfNotNull(
            v(Pids.dashboard.first { it.number == Pids.RPM }.hex, "rpm"),
            v(Pids.dashboard.first { it.number == Pids.SPEED }.hex, "km/h"),
            v("05", "°C coolant"),
        ).joinToString(" · ").ifEmpty { "Reading live data…" }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Car connection", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while the app is connected to your car"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "connection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISCONNECT = "com.obdreader.app.DISCONNECT"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ObdConnectionService::class.java))
            } catch (e: Exception) {
                // Android may refuse when the app is in the background (for example started from the car).
                // The connection still works while the app stays open in Android Auto.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ObdConnectionService::class.java))
        }
    }
}
