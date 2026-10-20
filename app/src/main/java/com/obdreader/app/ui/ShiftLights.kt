package com.obdreader.app.ui

/**
 * How many of the 15 shift lights are on. They cover the whole rev range, so they climb from the
 * first light at idle to all 15 just below the redline, and every change in revs shows.
 * (They used to start at 35% of the redline, about 2,300 rpm, so in everyday driving they
 * stayed dark.)
 */
object ShiftLights {
    const val COUNT = 15

    fun lit(rpm: Float?, redline: Float): Int {
        if (rpm == null || rpm <= 0f || redline <= 0f) return 0
        // All on at 97% of the redline; one more light every 1/15 of that.
        val perLight = redline * 0.97f / COUNT
        return (rpm / perLight).toInt().coerceIn(if (rpm >= 400f) 1 else 0, COUNT)
    }
}
