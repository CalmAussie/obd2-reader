package com.obdreader.app.obd

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * G-force from the phone's own motion sensor: how hard the car is speeding up or braking
 * (forward g) and cornering (sideways g), without anything from the car beyond its speed.
 *
 * The phone can sit any way round in its mount, so it learns two things first:
 *  - which way is down: what the sensor reads while the car is parked (it reads only gravity then);
 *  - which way is forward: the direction the phone feels pushed while the car's speed changes,
 *    braking or pulling away in a straight line. Sideways is then at right angles to both.
 * Until it knows forward, both readings are null (N/A). It knows forward after the first pull-away
 * or stop (about 15 km/h of speed change) and keeps refining it as you drive. What it's learned is
 * kept for the next drive (see [mount]), so the readings show straight away while the phone sits
 * the same way. If the phone is moved in its mount (down changes), it learns again.
 *
 * Only the accelerometer is used: no new permission, and nothing leaves the phone.
 */
class GForce {

    companion object {
        /** The most a drive's summary keeps (it reads back 0 to 5 g). */
        const val MAX_G = 5.0
        const val FORWARD_KEY = "GLON"
        const val SIDE_KEY = "GLAT"
        val KEYS = listOf(FORWARD_KEY, SIDE_KEY)
        const val G = 9.80665

        /** Car still for this long, phone steady: what it reads is gravity. */
        private const val PARKED_MS = 2_000L
        /** The car's speed changing at least this fast (m/s²) counts towards learning forward. */
        private const val LEARN_ACCEL = 0.8
        /**
         * How much speed change (m/s, summed) it takes before forward counts as learned: 3 m/s counted,
         * which a gentle pull-away from a standstill to about 15 km/h (or one stop) gives. It was 12 m/s,
         * 43 km/h of firm speed changes, which could take minutes of town driving. Forward keeps being
         * refined as you drive anyway.
         */
        const val LEARNED_AFTER = 3.0
        /** Down moving more than this while parked means the phone was moved in its mount. */
        private const val MOVED_DEG = 12.0
        /**
         * At the start of a drive, a mount kept from the last one is checked against the first second
         * of readings: down that far out means the phone sits another way now, so it learns again. Wider
         * than [MOVED_DEG] because the car may already be moving (pulling away tilts it by up to ~17°).
         */
        private const val CHECK_DEG = 25.0
        private const val CHECK_READINGS = 40
        /** Smoothing for the readings shown (seconds): enough to calm road bumps. */
        private const val SHOW_TAU_S = 0.25

        /** Harsh moments, in g: braking, pulling away and cornering. */
        const val HARSH_BRAKE = 0.40
        const val HARSH_ACCEL = 0.35
        const val HARSH_CORNER = 0.45
        /** A harsh moment counts once it lasts this long. */
        private const val HARSH_MS = 300L
    }

    private class V(val x: Double, val y: Double, val z: Double) {
        operator fun plus(o: V) = V(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: V) = V(x - o.x, y - o.y, z - o.z)
        operator fun times(k: Double) = V(x * k, y * k, z * k)
        infix fun dot(o: V) = x * o.x + y * o.y + z * o.z
        infix fun cross(o: V) = V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
        val length get() = sqrt(this dot this)
        fun unit(): V? = length.takeIf { it > 1e-6 }?.let { this * (1 / it) }
    }

    /** "Up" in the phone's own axes (the sensor reads +1 g upwards while still). */
    private var up: V? = null
    /** Forward in the phone's axes, flat (at right angles to up), once learned. */
    private var forward: V? = null
    private var learnSum: V = V(0.0, 0.0, 0.0)
    private var learnAmount = 0.0

    // Gravity while still: averaged over the parked stretch. Readings only count once the next speed
    // reading says the car was still stopped, so the moment of pulling away never gets in.
    private var stillSince: Long? = null
    private var stillSum = V(0.0, 0.0, 0.0)
    private var stillCount = 0
    private var pendingSum = V(0.0, 0.0, 0.0)
    private var pendingCount = 0
    /** Before the first parked moment: a slow average stands in for down. */
    private var slowUp: V? = null

    private var speedMs: Double? = null
    private var speedAtMs = -1L
    private var carAccel: Double? = null
    private var lastSpeedMs: Double? = null
    private var lastSpeedAt = -1L

    private var lastAt = -1L
    private var fwdShown = 0.0
    private var sideShown = 0.0

    /** Latest readings in g, or null until forward is learned. Positive forward is speeding up; positive sideways is to the right. */
    var forwardG: Double? = null
        private set
    var sideG: Double? = null
        private set

    val calibrated: Boolean get() = forward != null

    /** The car's speed has been seen still for long enough to know which way is down. */
    val knowsDown: Boolean get() = up != null

    // This drive's peaks and harsh moments.
    var peakBrake = 0.0; private set
    var peakAccel = 0.0; private set
    var peakSide = 0.0; private set
    var harshBrakes = 0; private set
    var harshAccels = 0; private set
    var harshCorners = 0; private set
    private var brakeSince: Long? = null; private var brakeCounted = false
    private var accelSince: Long? = null; private var accelCounted = false
    private var cornerSince: Long? = null; private var cornerCounted = false

    /** The car's speed in km/h (from OBD2), as it arrives. */
    fun speed(kmh: Double?, timeMs: Long) {
        val v = kmh?.div(3.6)
        if (v == 0.0) parkedFor(timeMs) else { stillSince = null; stillSum = V(0.0, 0.0, 0.0); stillCount = 0 }
        pendingSum = V(0.0, 0.0, 0.0); pendingCount = 0
        if (v == null) { speedMs = null; carAccel = null; return }
        val last = lastSpeedMs
        if (last != null && lastSpeedAt >= 0 && timeMs > lastSpeedAt) {
            val dt = (timeMs - lastSpeedAt) / 1000.0
            if (dt in 0.05..3.0) {
                val a = (v - last) / dt
                // Smoothed: one speed step is coarse (1 km/h), so it's averaged over about half a second.
                val k = 1 - exp(-dt / 0.5)
                carAccel = (carAccel ?: a) + k * (a - (carAccel ?: a))
            } else carAccel = null
        }
        lastSpeedMs = v; lastSpeedAt = timeMs; speedMs = v; speedAtMs = timeMs
    }

    /** The car is still stopped: what the sensor read since the last speed reading was gravity alone. */
    private fun parkedFor(timeMs: Long) {
        if (speedMs != 0.0) return // the first zero: start counting from here
        if (stillSince == null) stillSince = timeMs
        stillSum = stillSum + pendingSum; stillCount += pendingCount
        if (timeMs - stillSince!! < PARKED_MS || stillCount < 20) return
        val g = (stillSum * (1.0 / stillCount)).unit() ?: return
        val was = up
        if (was != null && angleDeg(was, g) > MOVED_DEG) {
            // The phone was moved in its mount: forward has to be learned again.
            forward = null; learnSum = V(0.0, 0.0, 0.0); learnAmount = 0.0
            forwardG = null; sideG = null
        }
        up = g
    }

    /** Whether this drive had readings at all (forward was known for some of it). */
    private var measured = false

    /** Forgets this drive's peaks and harsh moments (what it's learned about the mount stays). */
    fun resetDrive() {
        peakBrake = 0.0; peakAccel = 0.0; peakSide = 0.0; harshBrakes = 0; harshAccels = 0; harshCorners = 0
        brakeSince = null; accelSince = null; cornerSince = null; measured = false
    }

    /** This drive's peaks and harsh moments, or null if it never had a reading (the mount was still being learned). */
    fun drive(): TripSummary.Motion? = if (!measured) null else
        // A knock (the phone falling out of its mount) can read past 5 g: kept at 5, so the drive's summary still reads back.
        TripSummary.Motion(peakBrake.coerceIn(0.0, MAX_G), peakAccel.coerceIn(0.0, MAX_G), peakSide.coerceIn(0.0, MAX_G), harshBrakes, harshAccels, harshCorners)

    /**
     * Readings already in the car's directions, for the demo car (there's no mount to learn): forward
     * and sideways g, smoothed and counted just like the phone's own.
     */
    fun direct(fwd: Double, side: Double, timeMs: Long) {
        val dt = if (lastAt < 0) 0.0 else ((timeMs - lastAt) / 1000.0).coerceIn(0.0, 1.0)
        lastAt = timeMs
        val k = if (dt <= 0) 1.0 else 1 - exp(-dt / SHOW_TAU_S)
        fwdShown += k * (fwd - fwdShown)
        sideShown += k * (side - sideShown)
        forwardG = fwdShown; sideG = sideShown
        track(timeMs)
    }

    /** Forgets everything, as when the phone is put in another car or mount. */
    fun reset() {
        up = null; forward = null; slowUp = null; learnSum = V(0.0, 0.0, 0.0); learnAmount = 0.0
        stillSince = null; stillSum = V(0.0, 0.0, 0.0); stillCount = 0; pendingSum = V(0.0, 0.0, 0.0); pendingCount = 0
        forwardG = null; sideG = null; fwdShown = 0.0; sideShown = 0.0; lastAt = -1
        checkSum = V(0.0, 0.0, 0.0); checkCount = 0; checking = false
        resetDrive()
    }

    // Checking a kept mount at the start of a drive: the first readings, averaged.
    private var checking = false
    private var checkSum = V(0.0, 0.0, 0.0)
    private var checkCount = 0

    /**
     * A new connection (perhaps another car, or the phone put back in its mount another way). The mount
     * learned before is kept, so the readings show at once, and checked against the first second of
     * readings: if down has moved, it's forgotten and learned again.
     */
    fun newConnection() {
        val keptUp = up; val keptForward = forward
        reset()
        if (keptUp != null && keptForward != null) {
            up = keptUp; forward = keptForward
            // Refining carries on from here, weighted like a learned mount.
            learnSum = keptForward * LEARNED_AFTER; learnAmount = LEARNED_AFTER
            checking = true
        }
    }

    /** What's learned about the mount, to keep for the next drive (up and forward in the phone's axes), or null. */
    fun mount(): String? {
        val u = up ?: return null
        val f = forward ?: return null
        return listOf(u.x, u.y, u.z, f.x, f.y, f.z).joinToString(",") { String.format(java.util.Locale.US, "%.4f", it) }
    }

    /** Takes back a mount kept by [mount]; checked at the start of the drive like any kept mount. Ignores anything malformed. */
    fun restore(saved: String?) {
        val n = saved?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.takeIf { it.size == 6 && it.all { v -> v.isFinite() } } ?: return
        val u = V(n[0], n[1], n[2]).unit() ?: return
        val f = V(n[3], n[4], n[5]).let { it - u * (it dot u) }.unit() ?: return
        reset()
        up = u; forward = f
        learnSum = f * LEARNED_AFTER; learnAmount = LEARNED_AFTER
        checking = true
    }

    /** One accelerometer reading (m/s², phone axes, gravity included). */
    fun sensor(ax: Double, ay: Double, az: Double, timeMs: Long) {
        val a = V(ax, ay, az)
        val dt = if (lastAt < 0) 0.0 else ((timeMs - lastAt) / 1000.0).coerceIn(0.0, 1.0)
        lastAt = timeMs

        // A mount kept from the last drive: does the phone still sit the same way?
        if (checking) {
            checkSum = checkSum + a; checkCount++
            if (checkCount >= CHECK_READINGS) {
                checking = false
                val now = checkSum.unit(); val was = up
                if (now != null && was != null && angleDeg(was, now) > CHECK_DEG) {
                    up = null; forward = null; learnSum = V(0.0, 0.0, 0.0); learnAmount = 0.0
                    forwardG = null; sideG = null; fwdShown = 0.0; sideShown = 0.0
                }
            }
        }

        // Parked (the car's last speed was 0): kept aside until the next speed reading confirms it.
        val parked = speedMs == 0.0 && timeMs - speedAtMs < 3_000
        if (parked) { pendingSum = pendingSum + a; pendingCount++ }
        // Until the car has been seen parked, a slow average of the sensor stands in for down.
        val slow = slowUp
        slowUp = if (slow == null) a.unit() else (slow + (a - slow) * (1 - exp(-dt / 8.0))).unit() ?: slow
        val u = up ?: slowUp ?: return

        // What's left after gravity, laid flat.
        val lin = a - u * G
        val flat = lin - u * (lin dot u)

        // Learning forward: the push the phone feels while the speed changes in a straight line.
        val ca = carAccel
        // Only while the push is about as big as the speed change: in a straight line, not braking into a bend.
        if (ca != null && abs(ca) >= LEARN_ACCEL && !parked && flat.length < abs(ca) * 1.4 + 0.4) {
            learnSum = learnSum + flat * (ca * dt)
            learnAmount += abs(ca) * dt
            if (learnAmount >= LEARNED_AFTER) {
                learnSum.unit()?.let { f ->
                    forward = (f - u * (f dot u)).unit()
                }
            }
        }

        val fw = forward
        if (fw == null) { forwardG = null; sideG = null; return }
        val right = (fw cross u).unit() ?: return
        val f = (flat dot fw) / G
        val s = (flat dot right) / G
        val k = if (dt <= 0) 1.0 else 1 - exp(-dt / SHOW_TAU_S)
        fwdShown += k * (f - fwdShown)
        sideShown += k * (s - sideShown)
        forwardG = fwdShown; sideG = sideShown
        track(timeMs)
    }

    private fun track(timeMs: Long) {
        measured = true
        val f = fwdShown; val s = sideShown
        if (-f > peakBrake) peakBrake = -f
        if (f > peakAccel) peakAccel = f
        if (abs(s) > peakSide) peakSide = abs(s)
        fun harsh(on: Boolean, since: Long?, counted: Boolean, count: () -> Unit): Pair<Long?, Boolean> = when {
            !on -> null to false
            since == null -> timeMs to false
            !counted && timeMs - since >= HARSH_MS -> { count(); since to true }
            else -> since to counted
        }
        harsh(-f >= HARSH_BRAKE, brakeSince, brakeCounted) { harshBrakes++ }.let { brakeSince = it.first; brakeCounted = it.second }
        harsh(f >= HARSH_ACCEL, accelSince, accelCounted) { harshAccels++ }.let { accelSince = it.first; accelCounted = it.second }
        harsh(abs(s) >= HARSH_CORNER, cornerSince, cornerCounted) { harshCorners++ }.let { cornerSince = it.first; cornerCounted = it.second }
    }

    private fun angleDeg(a: V, b: V): Double = Math.toDegrees(acos((a dot b).coerceIn(-1.0, 1.0)))
}
