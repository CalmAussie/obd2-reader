package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.obdreader.app.R
import java.util.Locale
import kotlin.math.roundToInt

private fun View.dp(v: Float) = v * resources.displayMetrics.density

/**
 * The Rally stage layout's main display, like a rally co-driver's board: engine speed huge on a
 * black panel, a rev bar with the redline marked, and safety-orange hazard stripes down the edge.
 */
class RallyRevView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var max = 8000f
        set(v) { field = v; invalidate() }
    var warnAt = 6500f
        set(v) { field = v; invalidate() }

    /** Largest height in pixels; landscape sets it so the panel fits the screen's height. */
    var maxSize = Int.MAX_VALUE
        set(value) { if (field != value) { field = value; requestLayout() } }

    private var rpm: Float? = null
    private val smooth = SmoothValue(tauMs = 110f, settle = 2f)
    private val ink = context.themeColor(R.attr.obdStripe2)
    private val orange = context.themeColor(R.attr.obdStripe1)
    private val paper = context.themeColor(R.attr.obdBg)
    private val red = context.themeColor(R.attr.obdDanger)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.titleTypeface(); letterSpacing = 0.14f }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(600) }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.displayTypeface(); fontFeatureSettings = "tnum" }
    private val rect = RectF()
    private val path = Path()

    init {
        contentDescription = "Engine speed"
    }

    fun setValue(v: Float?) {
        rpm = v
        smooth.set(v)
        if (!isShown) smooth.snap()
        contentDescription = v?.let { "Engine speed ${it.roundToInt()} rpm" } ?: "Engine speed"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, ((w.coerceAtMost(dp(440f).toInt()) * H / W).toInt()).coerceAtMost(maxSize))
    }

    override fun onDraw(canvas: Canvas) {
        val moving = smooth.step()
        val v = rpm?.let { smooth.shown }
        val u = height / H
        canvas.save()
        canvas.translate((width - W * u) / 2f, 0f)
        canvas.scale(u, u)

        // The black board, with hazard stripes down its right edge.
        rect.set(0f, 0f, W, H)
        fill.color = ink; canvas.drawRoundRect(rect, 8f, 8f, fill)
        canvas.save()
        path.reset(); path.addRoundRect(rect, 8f, 8f, Path.Direction.CW); canvas.clipPath(path)
        fill.color = orange
        var x = W - 58f
        while (x < W + 60f) {
            path.reset()
            path.moveTo(x, 0f); path.lineTo(x + 14f, 0f); path.lineTo(x + 14f - 0.6f * H, H); path.lineTo(x - 0.6f * H, H); path.close()
            canvas.save(); canvas.clipRect(W - 58f, 0f, W, H); canvas.drawPath(path, fill); canvas.restore()
            x += 28f
        }
        canvas.restore()

        val left = 18f; val right = W - 76f
        label.textSize = 11f; label.color = 0xFFA8A59C.toInt(); label.textAlign = Paint.Align.LEFT
        canvas.drawText("ENGINE", left, 26f, label)
        val hot = (v ?: 0f) >= warnAt
        val text = v?.let { String.format(Locale.US, "%,d", ((it / 10f).roundToInt() * 10)) } ?: "—"
        number.textSize = 66f; number.textAlign = Paint.Align.LEFT
        while (number.measureText(text) > right - left - 40f && number.textSize > 30f) number.textSize -= 2f
        number.color = if (hot) red else paper
        canvas.drawText(text, left - 2f, 92f, number)
        small.textSize = 13f; small.color = 0xFFA8A59C.toInt(); small.textAlign = Paint.Align.LEFT
        canvas.drawText("rpm", left + number.measureText(text) + 6f, 92f, small)

        // The rev bar: paper white warming to orange, with the redline marked.
        val barTop = 108f; val barBottom = 122f
        fill.color = 0xFF2A2A2A.toInt(); canvas.drawRect(left, barTop, right, barBottom, fill)
        val f = ((v ?: 0f) / max).coerceIn(0f, 1f)
        if (f > 0f) {
            fill.shader = LinearGradient(left, 0f, right, 0f, paper, orange, Shader.TileMode.CLAMP)
            canvas.drawRect(left, barTop, left + (right - left) * f, barBottom, fill)
            fill.shader = null
        }
        val rx = left + (right - left) * (warnAt / max).coerceIn(0f, 1f)
        fill.color = orange; canvas.drawRect(rx - 1.5f, barTop - 4f, rx + 1.5f, barBottom + 4f, fill)
        small.textSize = 10f
        canvas.drawText("0", left, barBottom + 16f, small)
        small.textAlign = Paint.Align.CENTER
        canvas.drawText(String.format(Locale.US, "REDLINE %,d", warnAt.roundToInt()), (left + right) / 2f, barBottom + 16f, small)
        small.textAlign = Paint.Align.RIGHT
        canvas.drawText("${(max / 1000f).roundToInt()}k", right, barBottom + 16f, small)
        canvas.restore()
        keepAnimating(moving)
    }

    private companion object {
        const val W = 340f
        const val H = 150f
    }
}

/**
 * One of Rally stage's readout blocks: [Style.OUTLINE] (a black frame on paper), [Style.ORANGE] (a
 * solid safety-orange block) or [Style.CELL] (a cell of the strip under them, divided by hairlines).
 */
class RallyBlockView(
    context: Context,
    private val name: String,
    private val unit: String,
    private val decimals: Int,
    private val warnAt: Double?,
    private val style: Style,
) : View(context) {
    enum class Style { OUTLINE, ORANGE, CELL }

    private var value: Double? = null
    private val smooth = SmoothValue(tauMs = 120f, settle = 0.4f * Math.pow(10.0, -decimals.toDouble()).toFloat())
    private var unsupported = false
    private val ink = context.themeColor(R.attr.obdStripe2)
    private val orange = context.themeColor(R.attr.obdStripe1)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val outline = context.themeColor(R.attr.obdOutline)
    private val red = context.themeColor(R.attr.obdDanger)
    private val paper = context.themeColor(R.attr.obdBg)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.titleTypeface(); letterSpacing = 0.12f; textSize = dp(10f) }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.displayTypeface(); fontFeatureSettings = "tnum" }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(600); textSize = dp(12f) }
    private val rect = RectF()

    /** Shown instead of the name (the battery's charging status, say). */
    var labelOverride: String? = null
        set(v) { if (field != v) { field = v; invalidate() } }

    /** A line under the number: the drive's top speed under the speed. */
    var note: String? = null
        set(v) { if (field != v) { field = v; invalidate() } }

    init {
        contentDescription = name
    }

    fun setValue(v: Double?) {
        unsupported = false
        value = v
        smooth.set(v?.toFloat())
        if (!isShown) smooth.snap()
        contentDescription = if (v == null) name else "$name ${String.format(Locale.US, "%.${decimals}f", v)} $unit"
        invalidate()
    }

    fun setUnsupported() {
        unsupported = true
        contentDescription = "$name not reported by this car"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(if (style == Style.CELL) 62f else 104f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val moving = smooth.step()
        val w = width.toFloat(); val h = height.toFloat()
        val hot = value != null && warnAt != null && value!! >= warnAt
        val pad = dp(if (style == Style.CELL) 8f else 12f)
        when (style) {
            Style.OUTLINE -> {
                val s = dp(3f)
                rect.set(s / 2, s / 2, w - s / 2, h - s / 2)
                // Paper inside the frame, so it reads the same on any background (the car's screen, say).
                fill.color = paper; canvas.drawRoundRect(rect, dp(6f), dp(6f), fill)
                stroke.strokeWidth = s; stroke.color = if (hot) red else ink
                canvas.drawRoundRect(rect, dp(6f), dp(6f), stroke)
            }
            Style.ORANGE -> {
                rect.set(0f, 0f, w, h)
                fill.color = if (hot) red else orange
                canvas.drawRoundRect(rect, dp(6f), dp(6f), fill)
            }
            Style.CELL -> {
                // The strip: heavy black rules above and below, hairlines between the cells.
                fill.color = paper; canvas.drawRect(0f, 0f, w, h, fill)
                fill.color = ink
                canvas.drawRect(0f, 0f, w, dp(3f), fill); canvas.drawRect(0f, h - dp(3f), w, h, fill)
                stroke.strokeWidth = dp(1f); stroke.color = outline
                canvas.drawLine(w - dp(0.5f), dp(10f), w - dp(0.5f), h - dp(10f), stroke)
            }
        }
        val textColour = if (style == Style.ORANGE) ink else if (hot) red else ink
        label.color = if (style == Style.CELL) muted else ink
        canvas.drawText(fitText(label, (labelOverride ?: name).uppercase(Locale.getDefault()), w - 2 * pad), pad, pad + dp(10f), label)
        val v = value?.let { smooth.shown.toDouble() }
        val t = if (unsupported) "N/A" else v?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: "—"
        number.color = textColour
        number.textSize = dp(if (style == Style.CELL) 22f else 46f)
        val room = w - 2 * pad - (if (unsupported) 0f else unitPaint.measureText(unit) + dp(4f))
        while (number.measureText(t) > room && number.textSize > dp(14f)) number.textSize -= dp(1f)
        val baseline = if (style == Style.CELL) h - dp(12f) else pad + dp(10f) + dp(12f) + number.textSize * 0.8f
        canvas.drawText(t, pad, baseline, number)
        unitPaint.color = textColour
        if (!unsupported) canvas.drawText(unit, pad + number.measureText(t) + dp(4f), baseline, unitPaint)
        note?.takeIf { style != Style.CELL }?.let {
            unitPaint.color = if (style == Style.ORANGE) ink else muted
            canvas.drawText(it, pad, h - pad, unitPaint)
        }
        keepAnimating(moving)
    }
}
