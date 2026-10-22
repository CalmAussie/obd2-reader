package com.obdreader.app.session

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.obdreader.app.obd.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from the app's GitHub releases. The app asks GitHub for the latest release (the repo is
 * public, so no account is needed), downloads its APK into the app's own cache and hands it to
 * Android's installer. Nothing but the version check and the download goes to GitHub.
 */
object Updater {

    const val REPO = "CalmAussie/obd2-reader"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
    private const val APK_NAME = "OBD2Reader.apk"

    const val LAST_CHECK = "update_last_check"
    const val SNOOZED_BUILD = "update_snoozed_build"

    fun installedBuild(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    } catch (e: Exception) { 0L }

    /** Test (debug) builds are signed differently, so a release can't install over them. */
    fun isTestBuild(context: Context): Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private const val CHANGELOG = "https://raw.githubusercontent.com/$REPO/main/CHANGELOG.md"

    /** The app's changelog from GitHub, so it's never older than the latest release. */
    suspend fun changelog(): String = withContext(Dispatchers.IO) {
        val conn = (URL(CHANGELOG).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "OBD2Reader")
        }
        try {
            if (conn.responseCode != 200) throw IOException("GitHub answered ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    /**
     * The latest release on GitHub. Throws IOException when GitHub can't be reached. GitHub's API
     * allows 60 checks an hour per internet address, which a shared network can use up, so if it
     * says no, the release page itself is asked instead.
     */
    suspend fun latest(): UpdateCheck.Release = try { fromApi() } catch (e: IOException) { fromReleasePage() }

    /** github.com/<repo>/releases/latest redirects to the latest release's tag; the APK is at a fixed path under it. */
    private suspend fun fromReleasePage(): UpdateCheck.Release = withContext(Dispatchers.IO) {
        val conn = (URL(RELEASES_PAGE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000; instanceFollowRedirects = false
            setRequestProperty("User-Agent", "OBD2Reader")
        }
        val location = try {
            if (conn.responseCode !in 300..399) throw IOException("GitHub answered ${conn.responseCode}")
            conn.getHeaderField("Location") ?: throw IOException("No release found")
        } finally { conn.disconnect() }
        val tag = UpdateCheck.tagFromReleaseUrl(location) ?: throw IOException("No release found")
        val (version, build) = UpdateCheck.parseTag(tag) ?: throw IOException("Unexpected release tag $tag")
        val notes = try {
            com.obdreader.app.obd.Changelog.parse(changelog()).firstOrNull { it.version == version }?.text ?: ""
        } catch (e: Exception) { "" }
        UpdateCheck.Release(tag, version, build, notes, "https://github.com/$REPO/releases/download/$tag/$APK_NAME", 0L, location)
    }

    private suspend fun fromApi(): UpdateCheck.Release = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OBD2Reader")
        }
        try {
            if (conn.responseCode != 200) throw IOException("GitHub answered ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val (version, build) = UpdateCheck.parseTag(tag) ?: throw IOException("Unexpected release tag $tag")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == APK_NAME }
                ?: throw IOException("The release has no APK")
            UpdateCheck.Release(
                tag, version, build, UpdateCheck.notes(json.optString("body")),
                apk.getString("browser_download_url"), apk.optLong("size"), json.optString("html_url", RELEASES_PAGE),
            )
        } finally { conn.disconnect() }
    }

    private fun folder(context: Context) = File(context.cacheDir, "updates")

    /** Removes downloaded updates once they're installed (or replaced by a newer one). */
    fun cleanUp(context: Context) {
        folder(context).listFiles()?.forEach { it.delete() }
    }

    /** Downloads the release's APK into the app's cache. [progress] gets 0..1 as it goes. */
    suspend fun download(context: Context, release: UpdateCheck.Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = folder(context).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "OBD2Reader-${release.build}.apk")
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "OBD2Reader")
        }
        try {
            if (conn.responseCode != 200) throw IOException("Download failed (${conn.responseCode})")
            val total = release.apkSize.takeIf { it > 0 } ?: conn.contentLengthLong.takeIf { it > 0 } ?: 0L
            var done = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (total > 0) withContext(Dispatchers.Main) { progress((done.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                }
            }
            if (total > 0 && file.length() != total) { file.delete(); throw IOException("The download was incomplete") }
            file
        } finally { conn.disconnect() }
    }

    /** Whether Android lets this app install updates; if not, [permissionIntent] opens the switch. */
    fun canInstall(context: Context): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Hands [apk] to Android's installer. Android shows its own Install prompt; on Android 12 and
     * newer, once the app has installed its own update, later ones may go through without it.
     */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("OBD2Reader.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, Intent(context, UpdateReceiver::class.java), flags)
            session.commit(pending.intentSender)
        }
    }
}

/** Android's answer from the installer: show its Install prompt, or say what went wrong. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // Android restarts the app on the new version.
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit // The user tapped Cancel.
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(context, "The update didn't install" + (why?.let { ": $it" } ?: "."), Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** After the app updates itself: clear the downloaded file. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Updater.cleanUp(context)
    }
}

