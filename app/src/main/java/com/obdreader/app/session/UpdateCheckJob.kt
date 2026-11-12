package com.obdreader.app.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.obdreader.app.HomeActivity
import com.obdreader.app.R
import com.obdreader.app.obd.UpdateCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The background update check. About once a day, even when the app isn't open, the phone asks
 * GitHub for the latest release (the same single request the app makes when it's opened) and, when
 * there's a newer version, shows a quiet "Version x is available" notification. Tapping it opens
 * the app's Update window, so nothing downloads or installs until the person taps Update there.
 *
 * There's no server and no push service: Android's own JobScheduler runs it when the phone has
 * internet and the battery isn't low. It never asks GitHub when the notification couldn't be shown
 * (notifications off, or the "New versions" channel turned off), never while connected to the car,
 * and never on test builds, which can't install a release.
 */
class UpdateCheckJob : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            val again = try { check(applicationContext) } catch (e: Exception) {
                android.util.Log.i(TAG, "background check failed: ${e.message}"); true
            }
            jobFinished(params, again)
        }
        return true
    }

    /** Android stopped the job early (the phone went offline, say): try again later. */
    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OBD2Updater"
        const val JOB_ID = 4110
        const val CHANNEL = "new_versions"
        const val NOTE_ID = 4103
        /** On the notification's tap: open the Update window. */
        const val EXTRA_SHOW_UPDATE = "show_update"
        /** The build the notification was last shown for, so each version is announced once. */
        const val NOTIFIED_BUILD = "update_notified_build"

        /** Sets up the daily check, once. Android keeps it through restarts and app updates. */
        fun schedule(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            if (jobs.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckJob::class.java))
                .setPeriodic(UpdateCheck.CHECK_EVERY_MS, 6L * 60 * 60 * 1000)
                // Android only counts a network that really reaches the internet, so a Wi-Fi
                // adapter's own hotspot doesn't start it.
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresBatteryNotLow(true)
                .setBackoffCriteria(30L * 60 * 1000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true)
                .build()
            try {
                jobs.schedule(job)
                android.util.Log.i(TAG, "background check scheduled")
            } catch (e: Exception) {
                android.util.Log.i(TAG, "couldn't schedule the background check: ${e.message}")
            }
        }

        /**
         * One background check. Returns true when it should be tried again soon (GitHub didn't
         * answer, or the car is connected), false when it's done until tomorrow.
         */
        internal suspend fun check(context: Context): Boolean {
            if (Updater.isTestBuild(context)) return false
            if (!canNotify(context)) return false
            if (ObdSession.state.value.isConnected) return true
            val release = Updater.latest()
            val prefs = AppPrefs.of(context)
            val installed = Updater.installedBuild(context)
            android.util.Log.i(TAG, "background check: latest build ${release.build}, installed $installed")
            if (!UpdateCheck.isNewer(release, installed)) { dismiss(context); return false }
            if (!UpdateCheck.shouldNotify(release, installed, prefs.getInt(NOTIFIED_BUILD, 0), prefs.getInt(Updater.SNOOZED_BUILD, 0))) return false
            post(context, release)
            prefs.edit().putInt(NOTIFIED_BUILD, release.build).apply()
            return false
        }

        /** Notifications allowed (Android 13+ asks for them) and the "New versions" channel not turned off. */
        fun canNotify(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return false
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return false
            channel(context)
            val ch = manager.getNotificationChannelCompat(CHANNEL)
            return ch == null || ch.importance != NotificationManagerCompat.IMPORTANCE_NONE
        }

        private fun channel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.updates_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.updates_channel_about)
                },
            )
        }

        /** A quiet notification (no sound or pop-up) that waits in the list until the person looks. */
        @android.annotation.SuppressLint("MissingPermission") // canNotify checked it
        internal fun post(context: Context, release: UpdateCheck.Release) {
            if (!canNotify(context)) return
            val open = PendingIntent.getActivity(
                context, NOTE_ID,
                Intent(context, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_SHOW_UPDATE, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = context.getString(R.string.updates_notice_text)
            val notes = UpdateCheck.noticeNotes(release.notes)
            val note = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.updates_available_title, release.version))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(if (notes.isBlank()) text else "$text\n\n$notes"))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTE_ID, note)
                android.util.Log.i(TAG, "new version notification: ${release.version} (build ${release.build})")
            } catch (e: SecurityException) {} // notifications turned off
        }

        /** Takes the notification away: the Update window is showing, or the app is up to date. */
        fun dismiss(context: Context) = NotificationManagerCompat.from(context).cancel(NOTE_ID)
    }
}
