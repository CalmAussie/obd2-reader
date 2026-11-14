package com.obdreader.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.obdreader.app.obd.DayNight
import com.obdreader.app.session.AppPrefs
import java.util.TimeZone

/**
 * Which style the app shows right now: the one chosen, or, with Day and night switching on, the
 * day or the night style. Used by the phone screens, the widgets and Android Auto.
 *
 * At sunset and sunrise: the sun's position is worked out on the phone from the phone's last known
 * location (only if the app already may use location; it never asks for a new fix) or, without
 * it, a rough guess from the time zone. The position is only used for that sum, then dropped:
 * it's never saved or sent anywhere.
 */
object StyleClock {

    /** Day or night by the light sensor, while the app's screen is open (see MainActivity). */
    val light = DayNight.LightSwitch()

    fun mode(prefs: SharedPreferences): DayNight.Mode = DayNight.mode(prefs.getString(AppPrefs.STYLE_AUTO, null))

    /** Whether it counts as night now, for the switching mode chosen. */
    fun isNight(context: Context, now: Long = System.currentTimeMillis()): Boolean = when (mode(AppPrefs.of(context))) {
        DayNight.Mode.OFF -> false
        DayNight.Mode.SUN -> place(context).let { DayNight.isNightBySun(now, it.latitude, it.longitude) }
        DayNight.Mode.PHONE -> phoneDark(context)
        // Until the sensor has said anything (the screen isn't open yet): go by the sun.
        DayNight.Mode.LIGHT -> light.isNight ?: place(context).let { DayNight.isNightBySun(now, it.latitude, it.longitude) }
    }

    fun phoneDark(context: Context): Boolean =
        (context.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** The style id to show on the phone and the widgets now. */
    fun styleId(context: Context): String {
        val prefs = AppPrefs.of(context)
        val chosen = prefs.getString(AppPrefs.THEME, null)
        val mode = mode(prefs)
        return AppThemes.byId(DayNight.styleFor(mode, chosen, prefs.getString(AppPrefs.STYLE_DAY, null), prefs.getString(AppPrefs.STYLE_NIGHT, null),
            mode != DayNight.Mode.OFF && isNight(context))).id
    }

    /**
     * The style for Android Auto's screen. With switching on, the car decides whether it's day or
     * night (its own light sensor or headlights), whichever way the phone switches.
     */
    fun carStyleId(context: Context, carNight: Boolean): String {
        val prefs = AppPrefs.of(context)
        val chosen = prefs.getString(AppPrefs.THEME, null)
        return AppThemes.byId(DayNight.styleFor(mode(prefs), chosen, prefs.getString(AppPrefs.STYLE_DAY, null), prefs.getString(AppPrefs.STYLE_NIGHT, null), carNight)).id
    }

    /** The phone's last known position, if the app may use location, else a guess from the time zone. */
    @SuppressLint("MissingPermission") // checked first
    fun place(context: Context): DayNight.Place {
        val app = context.applicationContext
        val allowed = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            .any { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }
        if (allowed) {
            try {
                val lm = app.getSystemService(LocationManager::class.java)
                val last = lm?.getProviders(true)?.mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } }
                    ?.maxByOrNull { it.time }
                if (last != null) return DayNight.Place(last.latitude, last.longitude, true)
            } catch (e: Exception) {
                // Fall back to the time zone below.
            }
        }
        return DayNight.guessFromTimeZone(TimeZone.getDefault())
    }
}
