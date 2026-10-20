package com.obdreader.app.obd

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A score out of 100 for the current drive, in three styles the driver picks between:
 *
 * - **Efficiency:** gentle acceleration, low revs, little idling, anticipating stops and
 *   staying under motorway speeds, for the least fuel.
 * - **Everyday:** smooth, relaxed driving: no harsh acceleration or braking and no high revs.
 * - **Racing:** for track days: committed throttle, using the top of the rev range without
 *   hitting the limiter, not labouring the engine, and keeping temperatures in check.
 *
 * It works only from what the car reports over OBD2 (speed, engine speed, throttle, load and
 * temperatures), so it can't see steering or speed limits. Each part of the score is the share
 * of driving time spent doing the right thing, so a single moment doesn't spoil a whole drive.
 * All three styles are worked out together, so switching styles shows a score straight away.
 */
class DrivingScore(redline: Float = 6500f) {

    enum class Style(val label: String, val blurb: String) {
        EFFICIENCY("Efficiency", "Gentle acceleration, low revs, little idling and anticipating stops."),
        EVERYDAY("Everyday", "Smooth, relaxed driving: no harsh acceleration, braking or high revs."),
        RACING("Racing", "For track days: full commitment, high revs without the limiter, cool engine."),
    }

    /** One part of the score. [score] is null until the drive has included what it measures. */
    data class Part(val name: String, val score: Int?, val tip: String)

    data class Result(
        val style: Style,
        /** Null until there's been enough driving to score (about half a minute). */
        val score: Int?,
        val parts: List<Part>,
        val drivingSeconds: Int,
        /** The quickest 0 to 100 km/h run this drive, in seconds. */
        val best0to100: Double?,
        /** The highest speed this drive, in km/h. */
        val topSpeed: Double? = null,
        /** Distance covered this drive, worked out from speed over time, in km. */
        val distanceKm: Double = 0.0,
        /** Time stopped with the engine running, in seconds. */
        val idleSeconds: Int = 0,
        /** Time connected to the car (moving or not), in seconds. */
        val connectedSeconds: Int = 0,
    ) {
        /** Average speed while moving, in km/h; null until the car has moved. */
        val averageSpeed: Double? get() = if (drivingSeconds > 0) distanceKm / (drivingSeconds / 3600.0) else null
    }

    var redline: Float = redline
        set(value) { field = value.coerceIn(3000f, 12000f) }

    // Time totals in milliseconds.
    private var connected = 0L
    private var driving = 0L
    private var idling = 0L
    private var harshAccel = 0L      // everyday: above 10 km/h per second
    private var brisk = 0L           // efficiency: above 6 km/h per second, or a wide-open throttle
    private var harshBrake = 0L      // everyday: below -12 km/h per second
    private var lateBrake = 0L       // efficiency: below -8 km/h per second
    private var highRevs = 0L        // everyday: above 4,000 rpm
    private var revsOver2500 = 0L    // efficiency
    private var fast = 0L            // efficiency: above 105 km/h
    private var fullThrottle = 0L    // racing: throttle above 80%
    private var fullThrottleInBand = 0L
    private var accelerating = 0L    // racing: gaining more than 3 km/h per second
    private var acceleratingCommitted = 0L
    private var overRev = 0L
    private var lugging = 0L
    private var hot = 0L

    private var distanceKm = 0.0
    private var lastMs = -1L
    private var lastSpeed: Double? = null
    private var accel = 0.0          // km/h per second, smoothed
    private var runStartMs: Long? = null
    var best0to100: Double? = null
        private set

    /**
     * The highest speed this drive, in km/h. A speed only counts once two readings in a row
     * reach it, so a single glitched reading can't set a record nobody drove.
     */
    var topSpeed: Double? = null
        private set

    fun reset() {
        connected = 0; driving = 0; idling = 0; harshAccel = 0; brisk = 0; harshBrake = 0; lateBrake = 0
        highRevs = 0; revsOver2500 = 0; fast = 0; fullThrottle = 0; fullThrottleInBand = 0
        accelerating = 0; acceleratingCommitted = 0; overRev = 0; lugging = 0; hot = 0
        lastMs = -1; lastSpeed = null; accel = 0.0; runStartMs = null; best0to100 = null; topSpeed = null; distanceKm = 0.0
    }

    /**
     * Adds one moment of the drive: the latest readings at [timeMs]. Call it regularly (every
     * reading or every few hundred milliseconds); readings the car doesn't report can be null.
     */
    fun update(timeMs: Long, speed: Double?, rpm: Double?, throttle: Double?, load: Double?, coolant: Double?, oil: Double? = null) {
        if (speed != null && lastSpeed != null) {
            val held = minOf(speed, lastSpeed!!)
            if (held > (topSpeed ?: 0.0)) topSpeed = held
        }
        if (lastMs < 0) { lastMs = timeMs; lastSpeed = speed; return }
        val dt = timeMs - lastMs
        if (dt <= 0) return
        lastMs = timeMs
        // A long gap (ignition off, lost link) isn't driving time.
        if (dt > 2000) { lastSpeed = speed; accel = 0.0; return }
        if (speed != null && lastSpeed != null) {
            // Distance: the average of the two speeds over the time between them.
            distanceKm += (speed + lastSpeed!!) / 2.0 * (dt / 3_600_000.0)
            val raw = (speed - lastSpeed!!) / (dt / 1000.0)
            // Smooth over about half a second so a jittery reading isn't a harsh stop.
            val k = min(1.0, dt / 500.0)
            accel += (raw - accel) * k
        }
        lastSpeed = speed
        connected += dt
        val v = speed ?: return
        val r = rpm ?: 0.0
        val thr = throttle ?: 0.0

        trackLaunch(timeMs, v)

        if (v < 3) {
            if (r > 400) idling += dt
            return
        }
        driving += dt
        if (accel > 10) harshAccel += dt
        if (accel > 6 || thr > 70) brisk += dt
        if (accel < -12) harshBrake += dt
        if (accel < -8) lateBrake += dt
        if (r > 4000) highRevs += dt
        if (r > 2500) revsOver2500 += dt
        if (v > 105) fast += dt
        if (thr > 80) {
            fullThrottle += dt
            if (r >= redline * 0.7 && r <= redline * 0.98) fullThrottleInBand += dt
        }
        if (accel > 3) {
            accelerating += dt
            if (thr > 70 || (load ?: 0.0) > 85) acceleratingCommitted += dt
        }
        if (r > redline) overRev += dt
        if (thr > 70 && r > 0 && r < redline * 0.3) lugging += dt
        if ((coolant ?: 0.0) >= 108 || (oil ?: 0.0) >= 130) hot += dt
    }

    private fun trackLaunch(timeMs: Long, v: Double) {
        when {
            v < 2 -> runStartMs = timeMs
            v >= 100 && runStartMs != null -> {
                val s = (timeMs - runStartMs!!) / 1000.0
                if (s in 2.0..30.0) best0to100 = best0to100?.let { min(it, s) } ?: s
                runStartMs = null
            }
        }
    }

    private fun share(part: Long, of: Long) = if (of <= 0) 0.0 else part.toDouble() / of
    private fun pct(x: Double) = (x * 100).roundToInt().coerceIn(0, 100)
    /** 100 with none of the habit, falling off smoothly: at weight 8, 2% of the time costs 15 points and 10% costs 55. */
    private fun penalty(share: Double, weight: Double) = pct(exp(-share * weight))

    fun result(style: Style): Result {
        val enough = driving >= MIN_DRIVING_MS
        val parts = when (style) {
            Style.EFFICIENCY -> listOf(
                Part("Gentle acceleration", penalty(share(brisk, driving), 4.0), "Build speed gradually; most fuel goes into hard acceleration."),
                Part("Low revs", penalty(share(revsOver2500, driving), 2.0), "Change up earlier, around 2,000 to 2,500 rpm."),
                Part("Idling", penalty(share(idling, connected), 2.5), "Switch off if you'll be stopped for more than a minute."),
                Part("Anticipation", penalty(share(lateBrake, driving), 8.0), "Look ahead and lift off early instead of braking late."),
                Part("Cruising speed", penalty(share(fast, driving), 2.0), "Above about 100 km/h, fuel use climbs quickly."),
            )
            Style.EVERYDAY -> listOf(
                Part("Smooth acceleration", penalty(share(harshAccel, driving), 8.0), "Squeeze the accelerator rather than stamping on it."),
                Part("Gentle braking", penalty(share(harshBrake, driving), 10.0), "Leave more room so you can brake gradually."),
                Part("Relaxed revs", penalty(share(highRevs, driving), 4.0), "Change up before 4,000 rpm in normal driving."),
            )
            Style.RACING -> listOf(
                Part("Rev range", if (fullThrottle < 1000) null else pct(share(fullThrottleInBand, fullThrottle)), "At full throttle, keep the engine between about 70% and 98% of the redline."),
                Part("Commitment", if (accelerating < 1000) null else pct(share(acceleratingCommitted, accelerating)), "When you accelerate, commit: a confident, full throttle."),
                Part("No limiter", penalty(share(overRev, driving), 20.0), "Change up just before the redline instead of hitting the limiter."),
                Part("Not labouring", penalty(share(lugging, driving), 6.0), "Drop a gear instead of flooring it at low revs."),
                Part("Engine temperature", penalty(share(hot, driving), 6.0), "Running hot: do a cool-down lap and check coolant and oil."),
            )
        }
        val weights = when (style) {
            Style.EFFICIENCY -> listOf(30, 25, 20, 15, 10)
            Style.EVERYDAY -> listOf(40, 40, 20)
            Style.RACING -> listOf(30, 25, 20, 15, 10)
        }
        val scored = parts.zip(weights).filter { it.first.score != null }
        val total = if (scored.isEmpty()) null else scored.sumOf { (p, w) -> p.score!! * w } / scored.sumOf { it.second }.toDouble()
        return Result(
            style, if (enough) total?.roundToInt() else null, parts, (driving / 1000).toInt(), best0to100, topSpeed,
            distanceKm, (idling / 1000).toInt(), (connected / 1000).toInt(),
        )
    }

    companion object {
        /** About half a minute of moving before a score means anything. */
        const val MIN_DRIVING_MS = 30_000L
    }
}
