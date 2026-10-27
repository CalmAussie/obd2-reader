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
     * Until this time, the app's screen closing is the icon switch at work, not the user closing
     * the app, so the connection is kept. (The screen itself isn't opened from the launcher entries,
     * see LauncherActivity, so switching them off doesn't close it.)
     */
    @Volatile var switchingUntil = 0L

    /** Switches the home-screen icon to [icon]: its entry on first, then every other one off. */
    fun choose(context: Context, icon: Icon) {
        val pm = context.packageManager
        switchingUntil = System.currentTimeMillis() + 5_000
        AppPrefs.of(context).edit().putString(AppPrefs.APP_ICON, icon.id).apply()
        ALL.sortedBy { if (it == icon) 0 else 1 }.forEach {
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, context.packageName + it.alias),
                if (it == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
