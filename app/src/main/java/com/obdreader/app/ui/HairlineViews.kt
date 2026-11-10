package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.obdreader.app.R
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun View.dp(v: Float) = v * resources.displayMetrics.density

/**
 * The Hairline layout's main display, like a watch face: a rev counter drawn in fine lines (a tick
 * every 500 rpm, a longer one and its number every 1,000, red past the redline), one orange needle on
 * the rim, and the speed huge and thin in the middle, with the exact engine speed under it.
 *
 * Each number sits on its own point inside the ticks and is centred on it both ways, so the
 * numbers stay evenly placed round the dial whatever their width.
 */
class HairlineDialView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var rpmMax = 8000f
        set(v) { field = v; invalidate() }
    var warnAt = 6500f
        set(v) { field = v; invalidate() }
    var speedUnit = "km/h"

    /** The drive's top speed, beside the unit. */
    var top: Float? = null
        set(v) { if (field != v) { field = v; invalidate() } }

    /** Largest height in pixels; landscape sets it so the dial fits the screen's height. */
    var maxSize = Int.MAX_VALUE
        set(value) { if (field != value) { field = value; requestLayout() } }

    private var speed: Float? = null
    private var rpm: Float? = null
    private val smoothSpeed = SmoothValue(tauMs = 120f, settle = 0.05f)
    private val smoothRpm = SmoothValue(tauMs = 110f, settle = 2f)
    private val fg = context.themeColor(R.attr.obdTextPrimary)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val needle = context.themeColor(R.attr.obdStripe1)
    private val red = context.themeColor(R.attr.obdDanger)
    private val track = context.themeColor(R.attr.obdTrack)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val numbers = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(400); textAlign = Paint.Align.CENTER; fontFeatureSettings = "tnum" }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(300); textAlign = Paint.Align.CENTER; fontFeatureSettings = "tnum"; letterSpacing = -0.04f }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(400); textAlign = Paint.Align.CENTER }
    private val rpmText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(500); textAlign = Paint.Align.CENTER; fontFeatureSettings = "tnum" }

    init {
        contentDescription = "Speed and engine speed"
    }

    fun set(speed: Float?, rpm: Float?) {
        this.speed = speed
        this.rpm = rpm
        smoothSpeed.set(speed); smoothRpm.set(rpm)
        if (!isShown) { smoothSpeed.snap(); smoothRpm.snap() }
        contentDescription = "Speed ${speed?.roundToInt() ?: "unknown"} $speedUnit, engine ${rpm?.roundToInt() ?: "unknown"} rpm"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, ((w.coerceAtMost(dp(340f).toInt()) * H / W).toInt()).coerceAtMost(maxSize))
    }

    override fun onDraw(canvas: Canvas) {
        val moving = smoothSpeed.step() or smoothRpm.step()
        val speed = this.speed?.let { smoothSpeed.shown }
        val rpm = this.rpm?.let { smoothRpm.shown }
        val u = height / H
        canvas.save()
        canvas.translate((width - W * u) / 2f, 0f)
        canvas.scale(u, u)
        val cx = W / 2f; val cy = 150f; val r = 136f

        // Ticks: one every 500 rpm, longer every 1,000; red from the redline on.
        val halves = (rpmMax / 500f).roundToInt().coerceAtLeast(2)
        for (i in 0..halves) {
            val at = i * 500f
            val a = Math.toRadians((225.0 - 270.0 * at / rpmMax))
            val major = i % 2 == 0
            val len = if (major) 14f else 7f
            line.strokeWidth = if (major) 2.2f else 1.2f
            line.color = if (at >= warnAt) red else if (major) fg else muted
            canvas.drawLine(cx + r * cos(a).toFloat(), cy - r * sin(a).toFloat(), cx + (r - len) * cos(a).toFloat(), cy - (r - len) * sin(a).toFloat(), line)
        }
        // Numbers in thousands, each centred on its own point a fixed gap inside the long ticks.
        numbers.textSize = 14f
        val fm = numbers.fontMetrics
        val mid = -(fm.ascent + fm.descent) / 2f
        val nr = r - 14f - 16f
        val step = if (rpmMax > 10000) 2 else 1
        var k = 0
        while (k * 1000f <= rpmMax + 1f) {
            val a = Math.toRadians((225.0 - 270.0 * k * 1000f / rpmMax))
            numbers.color = if (k * 1000f >= warnAt) red else muted
            canvas.drawText(k.toString(), cx + nr * cos(a).toFloat(), cy - nr * sin(a).toFloat() + mid, numbers)
            k += step
        }

        // The speed, huge and thin in the middle.
        val s = speed?.roundToInt()?.toString() ?: "—"
        // Three digits (a car reports up to 255 km/h) shrink just enough to stay clear of the dial's
        // numbers either side, which sit about 104 from the centre at the speed's height.
        big.textSize = 104f; big.color = fg
        while (big.measureText(s) > 2f * (nr - 22f) && big.textSize > 60f) big.textSize -= 2f
        val bm = big.fontMetrics
        canvas.drawText(s, cx, cy - (bm.ascent + bm.descent) / 2f - 6f, big)
        small.textSize = 14f; small.color = muted
        canvas.drawText(listOfNotNull(speedUnit, top?.let { "top ${it.roundToInt()}" }).joinToString(" · "), cx, cy + 52f, small)
        rpmText.textSize = 15f; rpmText.color = if ((rpm ?: 0f) >= warnAt) red else fg
        canvas.drawText(rpm?.let { String.format(java.util.Locale.US, "%,d rpm", ((it / 10f).roundToInt() * 10)) } ?: "— rpm", cx, cy + 112f, rpmText)

        // The needle: a short orange line on the rim, over the ticks.
        val f = ((rpm ?: 0f) / rpmMax).coerceIn(0f, 1f)
        val a = Math.toRadians(225.0 - 270.0 * f)
        line.color = ColorUtils.setAlphaComponent(needle, if (rpm == null) 90 else 255); line.strokeWidth = 3.4f
        canvas.drawLine(cx + (r - 40f) * cos(a).toFloat(), cy - (r - 40f) * sin(a).toFloat(), cx + (r + 4f) * cos(a).toFloat(), cy - (r + 4f) * sin(a).toFloat(), line)
        line.color = track
        canvas.restore()
        keepAnimating(moving)
    }

    private companion object {
        const val W = 320f
        const val H = 290f
    }
}
