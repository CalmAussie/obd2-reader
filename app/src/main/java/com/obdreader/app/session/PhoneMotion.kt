package com.obdreader.app.session

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.obdreader.app.obd.GForce
import com.obdreader.app.obd.TripSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin

/**
 * G-force while the car is connected: the phone's accelerometer, turned into the car's forward and
 * sideways directions by [GForce], shown as two readings ([GForce.FORWARD_KEY], [GForce.SIDE_KEY])
 * like any other. Listens only while the car is connected with the ignition on. Nothing is saved
 * except the drive's peaks in its summary and how the phone sits in its mount (two directions, so the
 * next drive's readings show at once), and nothing leaves the phone. No permission is needed.
 */
object PhoneMotion {

    enum class Status {
        /** Not connected: the sensor is off. */
        OFF,
        /** The phone has no accelerometer: the readings say N/A. */
        NO_SENSOR,
        /** Working out how the phone sits in its mount: the readings wait. */
        LEARNING,
        READY,
    }

    private val _status = MutableStateFlow(Status.OFF)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val gforce = GForce()
    private var started = false
    private var appContext: Context? = null
    private var listener: SensorEventListener? = null
    private var demoJob: Job? = null
    /** The demo car: there's no mount to learn, so its readings show straight away. */
    private var demoMode = false
    private var lastPublish = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** How often the readings are passed on: ten times a second is plenty for a dial or a graph. */
    private const val PUBLISH_MS = 100L

    /** Starts following the connection; safe to call more than once. */
    fun start(context: Context) {
        appContext = context.applicationContext
        if (started) return
        started = true
        // The mount learned on an earlier drive, so the readings show from the start.
        gforce.restore(AppPrefs.of(context).getString(AppPrefs.GFORCE_MOUNT, null))
        scope.launch {
            var last: ObdSession.Phase? = null
            ObdSession.state.collect { s ->
                // A new connection may be another car or mount: the mount learned before is kept but
                // checked against the first readings, and learned again if the phone sits another way.
                // Ignition-off stops during a drive keep it as it is.
                if (s.phase == ObdSession.Phase.CONNECTING && (last == null || last == ObdSession.Phase.IDLE || last == ObdSession.Phase.ERROR)) gforce.newConnection()
                if (s.phase == ObdSession.Phase.CONNECTED) listen(s.isDemo) else quiet()
                last = s.phase
            }
        }
        scope.launch {
            ObdSession.samples.collect { s -> if (s.key == SPEED) gforce.speed(s.value, s.timeMs) }
        }
    }

    /** A new drive: its peaks start again (what's known about the mount stays). */
    fun newDrive() = gforce.resetDrive()

    /** This drive's peaks and harsh moments, for its summary; null if it had no g-force readings. */
    fun drive(): TripSummary.Motion? = gforce.drive()

    private fun listen(demo: Boolean) {
        if (listener != null || demoJob != null) return
        if (demo) { playDemo(); return }
        val context = appContext ?: return
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || sensor == null) {
            _status.value = Status.NO_SENSOR
            ObdSession.phoneReadings(mapOf(GForce.FORWARD_KEY to null, GForce.SIDE_KEY to null))
            return
        }
        _status.value = if (gforce.calibrated) Status.READY else Status.LEARNING
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // The sensor's clock is the same one the car's readings use (time since the phone started).
                val t = e.timestamp / 1_000_000
                gforce.sensor(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble(), t)
                publish(t)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        listener = l
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun quiet() {
        if (listener != null) saveMount()
        listener?.let { l -> appContext?.getSystemService(SensorManager::class.java)?.unregisterListener(l) }
        listener = null
        demoJob?.cancel(); demoJob = null
        demoMode = false
        if (_status.value != Status.NO_SENSOR) _status.value = Status.OFF
    }

    /** What was saved last, so it's only written when it changes. */
    private var savedMount: String? = null
    private var savedMountLoaded = false

    /** Keeps how the phone sits in its mount for the next drive (or forgets it, if it's being learned again). */
    private fun saveMount() {
        val prefs = appContext?.let { AppPrefs.of(it) } ?: return
        if (!savedMountLoaded) { savedMount = prefs.getString(AppPrefs.GFORCE_MOUNT, null); savedMountLoaded = true }
        val now = gforce.mount()
        if (now == savedMount) return
        savedMount = now
        prefs.edit().apply { if (now == null) remove(AppPrefs.GFORCE_MOUNT) else putString(AppPrefs.GFORCE_MOUNT, now) }.apply()
    }

    private fun publish(timeMs: Long) {
        if (timeMs - lastPublish < PUBLISH_MS) return
        lastPublish = timeMs
        // Learned just now: kept straight away, in case the app is closed before the drive ends.
        if (!demoMode && gforce.calibrated && _status.value == Status.LEARNING) saveMount()
        val ready = demoMode || gforce.calibrated
        _status.value = if (ready) Status.READY else Status.LEARNING
        // Until the mount is learned there's nothing to show yet: the readings stay blank, not N/A.
        if (!ready) return
        ObdSession.phoneReadings(mapOf(GForce.FORWARD_KEY to gforce.forwardG, GForce.SIDE_KEY to gforce.sideG), timeMs)
    }

    /**
     * The demo car has no phone mount to learn: its g-force follows its own speed (speeding up and
     * slowing down) with gentle bends, so the dial and graphs have something to show.
     */
    private fun playDemo() {
        demoMode = true
        _status.value = Status.READY
        demoJob = scope.launch {
            var lastSpeed: Double? = null
            var lastAt = 0L
            var accel = 0.0
            while (isActive) {
                delay(50)
                val now = SystemClock.elapsedRealtime()
                val v = ObdSession.state.value.readings[SPEED]
                if (v != null && v != lastSpeed) {
                    lastSpeed?.let { was ->
                        val dt = ((now - lastAt) / 1000.0).coerceAtLeast(0.05)
                        accel += (1 - exp(-dt / 0.6)) * ((v - was) / 3.6 / dt - accel)
                    }
                    lastSpeed = v; lastAt = now
                } else if (now - lastAt > 1_000) {
                    accel *= 0.9 // a steady speed
                }
                val moving = ((v ?: 0.0) / 60.0).coerceIn(0.0, 1.0)
                val bend = 0.32 * sin(now / 2_600.0) * sin(now / 7_100.0) * moving
                gforce.direct(accel / GForce.G, bend, now)
                publish(now)
            }
        }
    }

    private const val SPEED = "0D"
}
