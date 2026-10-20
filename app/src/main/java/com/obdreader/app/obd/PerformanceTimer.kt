package com.obdreader.app.obd

/**
 * Times 0–100 km/h, 80–120 km/h and the quarter mile from the car's own speed readings.
 *
 * The run starts by itself: 0–100 and the quarter mile from a standstill the moment the car
 * moves, 80–120 as it passes 80 km/h. Crossing times are worked out between readings (the car
 * reports speed a few to 20 times a second), so a run isn't rounded to the next reading.
 * For closed roads and track days only.
 */
class PerformanceTimer {

    /**
     * The runs, in km/h: [from] is null for a standing start, [to] is null for the quarter mile.
     * The mph runs (0–60 and 50–70) are for drivers who've chosen mph.
     */
    enum class Mode(val label: String, val from: Double?, val to: Double?, val mph: Boolean? = false) {
        ZERO_100("0–100 km/h", null, 100.0),
        MID_80_120("80–120 km/h", 80.0, 120.0),
        ZERO_60("0–60 mph", null, 96.5606, mph = true),
        MID_50_70("50–70 mph", 80.4672, 112.6541, mph = true),
        QUARTER("¼ mile", null, null, mph = null);

        /** The runs to offer: metric or mph ones, and the quarter mile either way. */
        companion object {
            fun offered(mph: Boolean) = values().filter { it.mph == null || it.mph == mph }
        }
    }

    sealed class State {
        /** Waiting to start: come to a stop (or drop below 80 km/h for 80–120). */
        data class Staging(val message: String) : State()
        /** Ready: the run starts when the car moves (or passes 80 km/h). */
        object Ready : State()
        data class Running(val seconds: Double, val metres: Double) : State()
        data class Done(val result: Result) : State()
        data class Aborted(val reason: String) : State()
    }

    data class Result(val mode: Mode, val seconds: Double, val trapSpeed: Double? = null, val atMs: Long = 0L)

    var mode: Mode = Mode.ZERO_100
        private set
    var state: State = State.Staging("")
        private set

    private var lastT = -1L
    private var lastV = 0.0
    private var stoppedSince: Long? = null
    private var startT = 0.0
    private var metres = 0.0
    private var wallClock: () -> Long = { System.currentTimeMillis() }

    fun arm(mode: Mode) {
        this.mode = mode
        lastT = -1L; stoppedSince = null; metres = 0.0
        state = State.Staging(if (mode.from != null) "Drop below ${mode.label.substringBefore("–")} ${mode.label.substringAfterLast(" ")}" else "Come to a stop")
    }

    /** Feeds one speed reading (km/h) taken at [timeMs]. Returns the new state. */
    fun update(timeMs: Long, speed: Double): State {
        val prevT = lastT; val prevV = lastV
        lastT = timeMs; lastV = speed
        if (prevT < 0) { stage(timeMs, speed); return state }
        val dt = (timeMs - prevT) / 1000.0
        if (dt <= 0) return state
        when (state) {
            is State.Staging, State.Ready -> stage(timeMs, speed, prevT, prevV)
            is State.Running -> {
                // Distance: average speed over the gap.
                val before = metres
                metres += (prevV + speed) / 2 / 3.6 * dt
                val elapsed = timeMs / 1000.0 - startT
                val to = mode.to
                if (to != null) {
                    if (speed >= to) finish(cross(prevT, prevV, timeMs, speed, to) - startT, null)
                } else if (metres >= QUARTER_MILE) {
                    val f = ((QUARTER_MILE - before) / (metres - before)).coerceIn(0.0, 1.0)
                    finish(prevT / 1000.0 + f * dt - startT, prevV + (speed - prevV) * f)
                }
                if (state is State.Running) {
                    state = when {
                        elapsed > MAX_RUN_S -> State.Aborted("Took longer than a minute")
                        mode.from != null && speed < mode.from!! - 5 -> State.Aborted("Slowed below the start speed")
                        mode.from == null && speed < 1 -> State.Aborted("Stopped before the finish")
                        else -> State.Running(elapsed, metres)
                    }
                }
            }
            is State.Done, is State.Aborted -> Unit
        }
        return state
    }

    private fun stage(timeMs: Long, speed: Double, prevT: Long = -1, prevV: Double = 0.0) {
        val from = mode.from
        if (from != null) {
            if (speed < from) { state = if (speed < from - 5) State.Ready else state; return }
            if (state == State.Ready && prevT >= 0 && prevV < from) {
                startT = cross(prevT, prevV, timeMs, speed, from); metres = 0.0
                state = State.Running(timeMs / 1000.0 - startT, 0.0)
            }
            return
        }
        if (speed < 1) {
            val since = stoppedSince ?: timeMs.also { stoppedSince = it }
            state = if (timeMs - since >= 1000) State.Ready else State.Staging("Hold still…")
            return
        }
        stoppedSince = null
        if (state == State.Ready && prevT >= 0) {
            // Moving off: the run started at the last moment the car was still.
            startT = prevT / 1000.0
            metres = (prevV + speed) / 2 / 3.6 * ((timeMs - prevT) / 1000.0)
            state = State.Running(timeMs / 1000.0 - startT, metres)
        } else if (state !is State.Ready) {
            state = State.Staging("Come to a stop")
        }
    }

    /** The moment between two readings when speed passed [target], assuming steady acceleration. */
    private fun cross(t1: Long, v1: Double, t2: Long, v2: Double, target: Double): Double {
        val f = if (v2 == v1) 1.0 else ((target - v1) / (v2 - v1)).coerceIn(0.0, 1.0)
        return (t1 + (t2 - t1) * f) / 1000.0
    }

    private fun finish(seconds: Double, trap: Double?) {
        state = State.Done(Result(mode, seconds, trap, wallClock()))
    }

    companion object {
        const val QUARTER_MILE = 402.336
        const val MAX_RUN_S = 60.0

        /** Results saved as one line: "mode:seconds:trap:time" joined by ";". */
        fun encode(results: List<Result>): String =
            results.joinToString(";") { "${it.mode.name}:${it.seconds}:${it.trapSpeed ?: ""}:${it.atMs}" }

        fun decode(text: String?): List<Result> = text.orEmpty().split(";").mapNotNull { e ->
            val p = e.split(":")
            if (p.size != 4) return@mapNotNull null
            val mode = Mode.values().firstOrNull { it.name == p[0] } ?: return@mapNotNull null
            val s = p[1].toDoubleOrNull() ?: return@mapNotNull null
            Result(mode, s, p[2].toDoubleOrNull(), p[3].toLongOrNull() ?: 0L)
        }
    }
}
