package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.obdreader.app.R
import java.util.Locale
import kotlin.math.roundToInt

private fun View.dp(v: Float) = v * resources.displayMetrics.density

/**
 * A reading laid out on an LCD's digit cells: right-aligned in [cells] digits (blank cells are "!",
 * the font's empty cell), or every cell's middle segment lit ("--") when there's no reading.
 */
internal fun lcdCells(text: String?, cells: Int): String {
    if (text == null) return "-".repeat(cells)
    val digits = text.count { it != '.' }
    return "!".repeat((cells - digits).coerceAtLeast(0)) + text
}

/** The unlit cells behind a reading: an "8" for every cell, with the decimal point where it falls. */
internal fun lcdGhost(cells: Int, decimals: Int): String {
    val whole = (cells - decimals).coerceAtLeast(1)
    return "8".repeat(whole) + if (decimals > 0) "." + "8".repeat(decimals) else ""
}

/**
 * The Retro digital layout's main display, an 80s digital dash: a stepped bar-graph rev counter
 * that climbs from teal through amber to red at the redline, the speed in big glowing digits with
 * the unlit "88" showing faintly behind them, and the exact engine speed in amber beside it.
 */
class RetroDashView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var rpmMax = 8000f
        set(v) { field = v; invalidate() }
    var warnAt = 6500f
        set(v) { field = v; invalidate() }

    /** The speed unit beside the digits: km/h or mph. */
    var speedUnit = "km/h"

    /** The drive's top speed, under the engine speed. */
    var top: Float? = null
        set(v) { if (field != v) { field = v; invalidate() } }

    /** Largest height in pixels; landscape sets it so the display fits the screen's height. */
    var maxSize = Int.MAX_VALUE
        set(value) { if (field != value) { field = value; requestLayout() } }

    private var speed: Float? = null
    private var rpm: Float? = null
    private val smoothSpeed = SmoothValue(tauMs = 120f, settle = 0.05f)
    private val smoothRpm = SmoothValue(tauMs = 110f, settle = 2f)
    private val glow = context.themeColor(R.attr.obdStripe1)
    private val amber = context.themeColor(R.attr.obdStripe2)
    private val red = context.themeColor(R.attr.obdDanger)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val track = context.themeColor(R.attr.obdTrack)
    private val surface = context.themeColor(R.attr.obdSurface)
    private val outline = context.themeColor(R.attr.obdOutline)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(600); letterSpacing = 0.2f }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.lcdTypeface() }
    private val rect = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
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
        setMeasuredDimension(w, ((w.coerceAtMost(dp(420f).toInt()) * H / W).toInt()).coerceAtMost(maxSize))
    }

    override fun onDraw(canvas: Canvas) {
        val moving = smoothSpeed.step() or smoothRpm.step()
        val speed = this.speed?.let { smoothSpeed.shown }
        val rpm = this.rpm?.let { smoothRpm.shown }
        val u = height / H
        canvas.save()
        canvas.translate((width - W * u) / 2f, 0f)
        canvas.scale(u, u)

        // The display glass: near-black, with a faint teal rim.
        rect.set(1f, 1f, W - 1f, H - 1f)
        fill.color = surface; canvas.drawRoundRect(rect, 8f, 8f, fill)
        stroke.color = outline; stroke.strokeWidth = 1.2f; canvas.drawRoundRect(rect, 8f, 8f, stroke)

        // Header: the rev counter's scale, in thousands.
        label.textSize = 9f; label.color = muted; label.textAlign = Paint.Align.LEFT
        canvas.drawText("RPM ×1000", 16f, 22f, label)
        val left = 16f; val right = W - 16f; val bottom = 92f
        label.textAlign = Paint.Align.CENTER
        var k = 1
        while (k * 1000 <= rpmMax) {
            if (k * 1000f / rpmMax > 0.18f) canvas.drawText(k.toString(), left + (right - left) * (k * 1000f / rpmMax), 22f, label)
            k += if (rpmMax > 10000) 2 else 1
        }

        // The bar graph: bars that step up to the right; teal, then amber near the redline, red past it.
        val gap = 3f
        val barW = (right - left - gap * (BARS - 1)) / BARS
        val fr = ((rpm ?: 0f) / rpmMax).coerceIn(0f, 1f)
        for (i in 0 until BARS) {
            val x = left + i * (barW + gap)
            val h = 24f + 38f * i / (BARS - 1)
            val at = (i + 1f) / BARS * rpmMax
            val colour = when { at >= warnAt -> red; at >= warnAt * 0.8f -> amber; else -> glow }
            val lit = fr > 0.003f && (i + 0.5f) / BARS <= fr + 0.5f / BARS
            if (lit) {
                fill.color = ColorUtils.setAlphaComponent(colour, 60)
                canvas.drawRect(x - 2f, bottom - h - 2f, x + barW + 2f, bottom + 2f, fill)
                fill.color = colour
            } else fill.color = track
            canvas.drawRect(x, bottom - h, x + barW, bottom, fill)
        }

        // The speed: big glowing LCD digits over their unlit "88"; no reading lights the middle segments.
        val s = speed?.roundToInt()?.toString()
        val cells = maxOf(2, s?.length ?: 0)
        label.textSize = 9f; label.color = muted; label.textAlign = Paint.Align.LEFT
        canvas.drawText("SPEED ${speedUnit.uppercase(Locale.US)}", 16f, 116f, label)
        number.textAlign = Paint.Align.LEFT
        number.textSize = 74f
        while (number.measureText("8".repeat(cells)) > 196f && number.textSize > 40f) number.textSize -= 2f
        number.clearShadowLayer()
        number.color = track
        canvas.drawText(lcdGhost(cells, 0), 18f, 202f, number)
        number.color = glow
        number.setShadowLayer(12f, 0f, 0f, ColorUtils.setAlphaComponent(glow, 200))
        canvas.drawText(lcdCells(s, cells), 18f, 202f, number)

        // Engine speed in amber on the right, on its own four LCD cells, with the drive's top speed under it.
        label.textAlign = Paint.Align.RIGHT
        canvas.drawText("ENGINE", W - 16f, 150f, label)
        val r = rpm?.let { ((it / 10f).roundToInt() * 10).toString() }
        val rCells = maxOf(4, r?.length ?: 0)
        number.textSize = 26f; number.textAlign = Paint.Align.RIGHT
        val hot = (rpm ?: 0f) >= warnAt
        number.clearShadowLayer()
        number.color = track
        canvas.drawText(lcdGhost(rCells, 0), W - 16f, 186f, number)
        number.color = if (hot) red else amber
        number.setShadowLayer(8f, 0f, 0f, ColorUtils.setAlphaComponent(if (hot) red else amber, 180))
        canvas.drawText(lcdCells(r, rCells), W - 16f, 186f, number)
        number.clearShadowLayer()
        top?.let { canvas.drawText("TOP ${it.roundToInt()}", W - 16f, 206f, label) }
        canvas.restore()
        keepAnimating(moving)
    }

    private companion object {
        const val W = 360f
        const val H = 222f
        const val BARS = 28
    }
}

/** A small readout for the Retro digital layout: a glowing number with its unit, over a ten-segment bar. */
class RetroChipView(
    context: Context,
    private val name: String,
    private val unit: String,
    private val decimals: Int,
    private val min: Float,
    private val max: Float,
    private val warnAt: Float?,
) : View(context) {
    private var value: Float? = null
    private val smooth = SmoothValue(tauMs = 110f, settle = if (decimals > 0) 0.01f else 0.05f)
    private val glow = context.themeColor(R.attr.obdStripe1)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val surface = context.themeColor(R.attr.obdSurface)
    private val outline = context.themeColor(R.attr.obdOutline)
    private val track = context.themeColor(R.attr.obdTrack)
    private val red = context.themeColor(R.attr.obdDanger)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(600); letterSpacing = 0.2f; textSize = dp(9f) }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.lcdTypeface(); textSize = dp(22f) }
    private val naPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.displayTypeface(); textSize = dp(22f) }
    /** Digit cells: enough for the largest reading (130 °C is three, 14.5 V is three with one after the point). */
    private val cells = maxOf(String.format(Locale.US, "%.${decimals}f", max).count { it != '.' }, 2)
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(500); textSize = dp(11f); color = muted }
    private val rect = RectF()
    private var unsupported = false

    init {
        contentDescription = name
    }

    /** Replaces the name above the number (the battery's charging status, say). */
    var labelOverride: String? = null
        set(v) { if (field != v) { field = v; invalidate() } }

    fun setUnsupported() {
        unsupported = true
        contentDescription = "$name not reported by this car"
        invalidate()
    }

    fun setValue(v: Double?) {
        unsupported = false
        value = v?.toFloat()
        smooth.set(value)
        if (!isShown) smooth.snap()
        contentDescription = if (v == null) name else "$name ${String.format(Locale.US, "%.${decimals}f", v)} $unit"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(78f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val moving = smooth.step()
        val w = width.toFloat(); val h = height.toFloat()
        rect.set(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f))
        fill.color = surface; canvas.drawRoundRect(rect, dp(6f), dp(6f), fill)
        stroke.color = outline; stroke.strokeWidth = dp(1f); canvas.drawRoundRect(rect, dp(6f), dp(6f), stroke)
        val pad = dp(10f)
        label.color = muted
        canvas.drawText(fitText(label, (labelOverride ?: name).uppercase(Locale.getDefault()), w - 2 * pad), pad, dp(18f), label)
        val v = value?.let { smooth.shown }
        val hot = v != null && warnAt != null && v >= warnAt
        val c = if (hot) red else glow
        if (unsupported) {
            naPaint.color = muted
            canvas.drawText("N/A", pad, dp(50f), naPaint)
        } else {
            val shown = v?.let { String.format(Locale.US, "%.${decimals}f", it) }
            val n = maxOf(cells, shown?.count { it != '.' } ?: 0)
            val room = w - 2 * pad - unitPaint.measureText(unit) - dp(6f)
            number.textSize = dp(22f)
            val ghost = lcdGhost(n, decimals)
            while (number.measureText(ghost) > room && number.textSize > dp(12f)) number.textSize -= dp(1f)
            number.clearShadowLayer()
            number.color = track
            canvas.drawText(ghost, pad, dp(50f), number)
            number.color = c
            number.setShadowLayer(dp(5f), 0f, 0f, ColorUtils.setAlphaComponent(c, 170))
            canvas.drawText(lcdCells(shown, n).let { if (shown == null && decimals > 0) it.dropLast(decimals) + "." + it.takeLast(decimals) else it }, pad, dp(50f), number)
            number.clearShadowLayer()
            canvas.drawText(unit, pad + number.measureText(ghost) + dp(6f), dp(50f), unitPaint)
        }
        // Ten segments, lit to the reading.
        val segs = 10; val gap = dp(2f)
        val sw = (w - 2 * pad - gap * (segs - 1)) / segs
        val f = v?.let { ((it - min) / (max - min)).coerceIn(0f, 1f) } ?: 0f
        val lit = (f * segs).roundToInt()
        for (i in 0 until segs) {
            fill.color = if (i < lit && !unsupported) c else track
            val x = pad + i * (sw + gap)
            canvas.drawRect(x, h - dp(16f), x + sw, h - dp(10f), fill)
        }
        keepAnimating(moving)
    }
}
