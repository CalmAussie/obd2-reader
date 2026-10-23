package com.obdreader.app.session

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * The phone's position, only while a drive is being recorded. The latest fix is kept in memory
 * for the recorder to write into that drive's CSV, and dropped when recording stops: location is
 * never saved anywhere else. Uses Android's own location service, so nothing else is needed.
 */
object LocationFeed {

    data class Fix(val latitude: Double, val longitude: Double, val accuracyM: Float?, val atElapsedMs: Long)

    /** Fixes older than this aren't written (the GPS has lost the sky, in a tunnel or car park). */
    private const val MAX_AGE_MS = 5_000L

    @Volatile private var latest: Fix? = null
    private var manager: LocationManager? = null
    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            latest = Fix(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else null, SystemClock.elapsedRealtime())
        }
        @Deprecated("Needed on Android 9 and older") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun permitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Starts listening; does nothing without the permission. Returns whether it started. */
    @SuppressLint("MissingPermission") // checked just below
    fun start(context: Context): Boolean {
        if (!permitted(context)) return false
        val lm = context.applicationContext.getSystemService(LocationManager::class.java) ?: return false
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return false
        stop()
        manager = lm
        latest = null
        // Android 12 and newer: the phone's fused location, which adds the phone's motion sensors
        // (and Wi-Fi and mobile networks) to GPS, so short dropouts are bridged. Everything stays
        // on the phone, the same as GPS. Older phones, or if it isn't there: GPS alone.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.hasProvider(LocationManager.FUSED_PROVIDER) &&
            lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            try {
                val request = android.location.LocationRequest.Builder(1000L)
                    .setQuality(android.location.LocationRequest.QUALITY_HIGH_ACCURACY)
                    .setMinUpdateIntervalMillis(500L)
                    .build()
                lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, request, ContextCompat.getMainExecutor(context), listener)
                provider = LocationManager.FUSED_PROVIDER
                return true
            } catch (e: Exception) {
                // Fall back to GPS below.
            }
        }
        return try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            provider = LocationManager.GPS_PROVIDER
            true
        } catch (e: Exception) {
            manager = null
            false
        }
    }

    /** Which of the phone's location sources is in use (for the adapter log), or null when off. */
    @Volatile var provider: String? = null
        private set

    /** The latest fix, or null if there's no recent one. */
    fun current(): Fix? = latest?.takeIf { SystemClock.elapsedRealtime() - it.atElapsedMs <= MAX_AGE_MS }

    fun stop() {
        try { manager?.removeUpdates(listener) } catch (ignored: Exception) {}
        manager = null
        provider = null
        latest = null
    }

    val running: Boolean get() = manager != null
}
