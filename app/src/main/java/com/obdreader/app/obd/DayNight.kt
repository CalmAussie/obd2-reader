package com.obdreader.app.obd

import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Automatic day and night styles: the app shows one style by day and another by night, switching
 * at sunset and sunrise, with the phone's dark mode, or with the light around the phone.
 *
 * Everything here is worked out on the phone. The sun's position comes from the date, the time and
 * a rough position (the phone's last known location if the app may use location, else a guess
 * from the time zone). No position is ever saved or sent anywhere.
 */
object DayNight {

    enum class Mode(val key: String, val label: String, val help: String) {
        OFF("off", "Off", "One style all the time."),
        SUN("sun", "At sunset and sunrise", "Worked out on the phone from the date, the time and roughly where you are."),
        PHONE("phone", "With the phone's dark mode", "Night style whenever the phone's dark theme is on."),
        LIGHT("light", "With the light around the phone", "Night style when it's dark around the phone, by its light sensor."),
    }

    fun mode(key: String?): Mode = Mode.entries.firstOrNull { it.key == key } ?: Mode.OFF

    /** The style to show: [chosen] when switching is off, else the day or night style. */
    fun styleFor(mode: Mode, chosen: String?, day: String?, night: String?, isNight: Boolean): String? =
        if (mode == Mode.OFF) chosen else (if (isNight) night else day) ?: chosen

    // ---------- The sun ----------

    /** Where the sun counts as set: its centre 0.833° below the horizon (its size plus the air's bending of light). */
    private const val SUNSET_DEG = -0.833

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI

    /**
     * The sun's height above the horizon in degrees at [timeMs] (UTC), seen from [latitude],
     * [longitude] (east positive). Accurate to a fraction of a degree, a minute or two of sunset
     * time, which is plenty for changing a screen's colours.
     */
    fun sunElevation(timeMs: Long, latitude: Double, longitude: Double): Double {
        // Days since noon on 1 January 2000 (UTC).
        val d = timeMs / 86_400_000.0 + 2440587.5 - 2451545.0
        // The sun's mean anomaly and its ecliptic longitude, with the equation of centre.
        val m = rad((357.5291 + 0.98560028 * d) % 360.0)
        val c = 1.9148 * sin(m) + 0.0200 * sin(2 * m) + 0.0003 * sin(3 * m)
        val lambda = rad((deg(m) + c + 180.0 + 102.9372) % 360.0)
        val tilt = rad(23.4397)
        // Declination and right ascension.
        val dec = asin(sin(lambda) * sin(tilt))
        val ra = atan2(sin(lambda) * cos(tilt), cos(lambda))
        // Local sidereal time, then the hour angle.
        val sidereal = rad((280.16 + 360.9856235 * d + longitude) % 360.0)
        val h = sidereal - ra
        val lat = rad(latitude)
        return deg(asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(h)))
    }

    fun isNightBySun(timeMs: Long, latitude: Double, longitude: Double): Boolean =
        sunElevation(timeMs, latitude, longitude) < SUNSET_DEG

    /**
     * When the next sunset or sunrise is after [timeMs], to the minute, or null if there's none in
     * the next 24 hours (midnight sun or polar night).
     */
    fun nextChange(timeMs: Long, latitude: Double, longitude: Double): Long? {
        val now = isNightBySun(timeMs, latitude, longitude)
        val start = timeMs - timeMs % 60_000L
        for (i in 1..24 * 60) {
            val t = start + i * 60_000L
            if (isNightBySun(t, latitude, longitude) != now) return t
        }
        return null
    }

    /** A rough position. [exact] is false when it's only a guess from the time zone. */
    data class Place(val latitude: Double, val longitude: Double, val exact: Boolean)

    /**
     * A rough position from the phone's time zone, for when the app may not use location: the
     * longitude from the zone's offset from UTC (15° an hour) and a typical latitude for its region.
     * Sunset can be up to about an hour off this way, mostly in summer far from the equator.
     */
    fun guessFromTimeZone(zone: TimeZone): Place {
        val longitude = (zone.rawOffset / 3_600_000.0 * 15.0).coerceIn(-180.0, 180.0)
        return Place(typicalLatitude(zone.id), longitude, false)
    }

    /** A typical latitude for a time zone's region, by its name ("Australia/Melbourne", "Europe/Berlin"). */
    fun typicalLatitude(id: String): Double {
        SOUTH[id]?.let { return it }
        return when (id.substringBefore('/')) {
            "Europe" -> 50.0
            "America" -> 38.0
            "Asia" -> 30.0
            "Australia" -> -33.0
            "Africa" -> 5.0
            "Antarctica" -> -75.0
            "Pacific" -> -15.0
            "Atlantic" -> 35.0
            "Indian" -> -10.0
            else -> 0.0
        }
    }

    /** Southern-hemisphere zones whose region's typical latitude would put them in the north. */
    private val SOUTH = mapOf(
        "Pacific/Auckland" to -40.0, "Pacific/Chatham" to -44.0, "Pacific/Fiji" to -18.0,
        "America/Santiago" to -33.0, "America/Sao_Paulo" to -23.0, "America/Montevideo" to -35.0,
        "America/Asuncion" to -25.0, "America/Lima" to -12.0, "America/La_Paz" to -16.0,
        "America/Argentina/Buenos_Aires" to -35.0, "America/Buenos_Aires" to -35.0,
        "Africa/Johannesburg" to -26.0, "Africa/Maputo" to -26.0, "Africa/Harare" to -18.0,
        "Africa/Windhoek" to -22.0, "Africa/Lusaka" to -15.0, "Indian/Mauritius" to -20.0,
        "Asia/Jakarta" to -6.0,
    )

    // ---------- The light sensor ----------

    /**
     * Turns the light sensor's readings into day or night, without flicking back and forth: it has
     * to stay dark (under [darkLux]) for [holdMs] before it counts as night, and bright (over
     * [brightLux]) as long before it counts as day again. So a tunnel, a bridge's shadow or a
     * passing headlight doesn't change the screen. The very first reading decides straight away.
     */
    class LightSwitch(
        private val darkLux: Float = 10f,
        private val brightLux: Float = 60f,
        private val holdMs: Long = 30_000L,
    ) {
        var isNight: Boolean? = null
            private set
        private var candidateSince: Long? = null

        /** Feeds one reading; returns whether it's night now (null until the first reading). */
        fun feed(lux: Float, timeMs: Long): Boolean? {
            val night = isNight
            if (night == null) {
                isNight = lux < (darkLux + brightLux) / 2
                return isNight
            }
            val wantsChange = if (night) lux > brightLux else lux < darkLux
            if (!wantsChange) {
                candidateSince = null
            } else {
                val since = candidateSince ?: timeMs.also { candidateSince = it }
                if (timeMs - since >= holdMs) {
                    isNight = !night
                    candidateSince = null
                }
            }
            return isNight
        }
    }
}
