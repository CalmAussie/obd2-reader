package com.obdreader.app.obd

/**
 * Boost pressure: manifold pressure (PID 0B) minus the air pressure outside (barometric
 * pressure, PID 33). Above zero the turbo or supercharger is pushing air in; below zero is
 * the vacuum a non-boosted engine pulls. Cars that don't report the outside pressure use
 * sea level (101.3 kPa), so the reading is marked as an estimate.
 */
object Boost {
    const val KEY = "BOOST"
    const val LABEL = "Boost"
    const val ESTIMATE_LABEL = "Boost (est.)"
    const val SEA_LEVEL_KPA = 101.3
    const val MIN = -100.0
    const val MAX = 150.0

    /** Barometric pressure: read now and then, only to work out boost. */
    val BAROMETRIC = Pid(0x33, "Barometric pressure", "kPa", 0, 1, false, 70.0, 110.0, null) { it[0].toDouble() }

    /** Boost in kPa from manifold pressure and, if the car gives it, the outside pressure. */
    fun from(manifoldKpa: Double, baroKpa: Double?): Double {
        val outside = baroKpa?.takeIf { it in 50.0..115.0 } ?: SEA_LEVEL_KPA
        return manifoldKpa - outside
    }
}
