package com.obdreader.app.obd

/** When a drive counts as over. */
object TripRules {
    /**
     * With the adapter still connected but the ignition off this long, the drive is over and its
     * summary is shown. A shorter stop (fuel, a shop) carries on as the same drive.
     */
    const val ENDS_AFTER_OFF_MS = 3 * 60 * 1000L

    fun endedByIgnitionOff(offSinceMs: Long, nowMs: Long): Boolean = nowMs - offSinceMs >= ENDS_AFTER_OFF_MS
}
