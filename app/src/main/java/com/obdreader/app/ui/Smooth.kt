package com.obdreader.app.ui

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.exp

/**
 * A number that glides to each new reading instead of jumping, like a real gauge's needle.
 * Readings arrive every few dozen milliseconds; the shown value follows them frame by frame
 * with a short time constant, so dials sweep and digits roll as smoothly as the Graphs tab.
 */
class SmoothValue(
    /** How quickly the shown value catches up: about 63% of the way in this many milliseconds. */
    private val tauMs: Float = 110f,
    /** Close enough to stop animating, in the reading's own units. */
    private val settle: Float = 0.01f,
) {
    var target: Float? = null
        private set
    var shown = 0f
        private set
    private var lastMs = 0L

    /** Sets a new reading. The first reading after a blank is shown straight away. */
    fun set(v: Float?) {
        if (v == null) { target = null; lastMs = 0L; return }
        if (target == null) shown = v
        target = v
    }

    /** Jumps straight to the reading, for when nobody can see the animation. */
    fun snap() {
        target?.let { shown = it }
        lastMs = 0L
    }

    /** Starts the glide from [v] (after the start-up sweep, from zero). */
    fun from(v: Float) {
        shown = v
        lastMs = 0L
    }

    /** Moves one frame towards the reading. Returns true while it's still moving. */
    fun step(nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        val t = target ?: return false
        if (abs(t - shown) <= settle) { shown = t; lastMs = 0L; return false }
        val dt = if (lastMs == 0L) 16L else (nowMs - lastMs).coerceIn(0L, 100L)
        lastMs = nowMs
        shown += (t - shown) * (1f - exp(-dt / tauMs))
        if (abs(t - shown) <= settle) { shown = t; lastMs = 0L; return false }
        return true
    }
}

/** Keeps redrawing a view each frame while any of its values are still gliding, and only while it's on screen. */
fun View.keepAnimating(moving: Boolean) {
    if (moving && isShown) postInvalidateOnAnimation()
}

/**
 * Rolls a TextView's number to each new reading, frame by frame, using [format] for the text.
 * Used for the big telemetry numbers and the cockpit's glass panels.
 */
class SmoothText(
    private val view: TextView,
    private val blank: String,
    tauMs: Float = 110f,
    settle: Float = 0.05f,
    private val format: (Float) -> String,
) {
    private val value = SmoothValue(tauMs, settle)
    private var running = false
    private val frame = object : Runnable {
        override fun run() {
            val moving = value.step()
            show()
            if (moving && view.isShown) view.postOnAnimation(this) else running = false
        }
    }

    fun set(v: Float?) {
        value.set(v)
        if (v == null || !view.isShown) {
            value.snap()
            show()
            return
        }
        if (!running) {
            running = true
            view.postOnAnimation(frame)
        }
    }

    private fun show() {
        val text = if (value.target == null) blank else format(value.shown)
        if (view.text.toString() != text) view.text = text
    }
}
