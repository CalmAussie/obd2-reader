package com.obdreader.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.obdreader.app.R
import com.obdreader.app.session.AppPrefs

/**
 * The app icon's colour, chosen under Diagnostics → App icon, from the Spark colourways. Each is a
 * launcher entry (an activity alias in the manifest) and exactly one is enabled; Android then shows
 * that one's icon on the home screen and in the app drawer.
 */
object AppIcons {

    data class Icon(val id: String, val name: String, val note: String, val alias: String, @DrawableRes val icon: Int)

    val ALL: List<Icon> = listOf(
        Icon("racing_green", "Racing green", "Cream on British racing green", ".MainActivity", R.mipmap.ic_launcher),
        Icon("app_orange", "App orange", "Orange on near-black, the app's own colours", ".IconAppOrange", R.mipmap.ic_launcher_app_orange),
        Icon("racing_red", "Racing red", "White on red, like a race-team badge", ".IconRacingRed", R.mipmap.ic_launcher_racing_red),
        Icon("hivis_yellow", "Hi-vis yellow", "Near-black on safety yellow", ".IconHivisYellow", R.mipmap.ic_launcher_hivis_yellow),
        Icon("cobalt", "Cobalt", "White on bright blue", ".IconCobalt", R.mipmap.ic_launcher_cobalt),
        Icon("graphite", "Graphite", "Silver on dark grey, like a metal plug", ".IconGraphite", R.mipmap.ic_launcher_graphite),
        Icon("electric", "Electric", "Cyan on deep navy, like a spark", ".IconElectric", R.mipmap.ic_launcher_electric),
        Icon("clean_white", "Clean white", "Black on white, simplest of all", ".IconCleanWhite", R.mipmap.ic_launcher_clean_white),
    )

    fun current(context: Context): Icon =
        ALL.firstOrNull { it.id == AppPrefs.of(context).getString(AppPrefs.APP_ICON, null) } ?: ALL.first()

    /**
     * Until this time, the app's screen closing is the icon switch at work (Android closes a
     * screen opened from a launcher entry that's switched off), not the user closing the app, so
     * the connection is kept.
     */
    @Volatile var switchingUntil = 0L

    /** Asks the app's screen to open on the Diagnostics tab, where the App icon setting is. */
    const val EXTRA_OPEN_DIAGNOSTICS = "open_diagnostics"

    /**
     * Switches the home-screen icon to [icon]. Android closes any screen opened from a launcher
     * entry that's switched off, so the app reopens itself from the new entry first (on
     * Diagnostics, where the user was) and only then switches the old ones off. The connection
     * carries on throughout.
     */
    fun choose(context: Context, icon: Icon) {
        val pm = context.packageManager
        switchingUntil = System.currentTimeMillis() + 10_000
        AppPrefs.of(context).edit().putString(AppPrefs.APP_ICON, icon.id).apply()
        fun set(it: Icon, on: Boolean) = pm.setComponentEnabledSetting(
            ComponentName(context.packageName, context.packageName + it.alias),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        set(icon, true)
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(context.packageName, context.packageName + icon.alias))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_OPEN_DIAGNOSTICS, true),
        )
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            ALL.filter { it != icon }.forEach { set(it, false) }
            switchingUntil = System.currentTimeMillis() + 3_000
            android.util.Log.i("OBD2Icon", "app icon: ${icon.id} (others off)")
        }, 1_500)
    }
}
