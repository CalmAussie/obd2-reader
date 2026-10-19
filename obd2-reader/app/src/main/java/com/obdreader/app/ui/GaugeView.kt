package com.obdreader.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.obdreader.app.R
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** A 270° dial gauge with an animated gradient arc, tick marks and a centred read-out. */
class GaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var label: String = ""
        set(value) { field = value; invalidate() }
    var unit: String = ""
        set(value) { field = value; invalidate() }
    var min: Float = 0f
    var max: Float = 100f
    var decimals: Int = 0
    /** Values at or above this turn the arc red (e.g. redline). */
    var warnAt: Float = Float.MAX_VALUE

    private var target: Float? = null
    private var shown = 0f
    private var animator: ValueAnimator? = null

    private val accent = context.themeColor(R.attr.obdAccent)
    private val accentAlt = context.themeColor(R.attr.obdAccentAlt)
    private val danger = context.themeColor(R.attr.obdDanger)
    private val textPrimary = context.themeColor(R.attr.obdTextPrimary)

    /** Dial look from the theme: 0 = gradient arc, 1 = solid arc, 2 = LCD-style segments, 3 = angular. */
    private val gaugeStyle = context.themeInt(R.attr.obdGaugeStyle, STYLE_GRADIENT)
    private val thickness = context.themeFloat(R.attr.obdGaugeThickness, 0.07f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = if (gaugeStyle == STYLE_SEGMENTED || gaugeStyle == STYLE_ANGULAR) Paint.Cap.BUTT else Paint.Cap.ROUND
        color = context.themeColor(R.attr.obdTrack)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = if (gaugeStyle == STYLE_SEGMENTED || gaugeStyle == STYLE_ANGULAR) Paint.Cap.BUTT else Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        color = context.themeColor(R.attr.obdOutline)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = context.numberTypeface()
        fontFeatureSettings = "tnum"
        color = textPrimary
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = context.themeColor(R.attr.obdTextSecondary)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", if (context.themeBool(R.attr.obdTitleItalic)) Typeface.ITALIC else Typeface.NORMAL)
        letterSpacing = 0.08f
        color = context.themeColor(R.attr.obdTextSecondary)
    }

    private val rect = RectF()
    private var gradient: SweepGradient? = null

    fun setValue(value: Float?) {
        target = value
        animateTo((value ?: min).coerceIn(min, max))
    }

    private fun animateTo(v: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, v).apply {
            duration = 450
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 0.92f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val stroke = w * thickness
        // Angular dials pair a thin track with a thick, square-ended value bar.
        trackPaint.strokeWidth = if (gaugeStyle == STYLE_ANGULAR) stroke * 0.35f else stroke
        arcPaint.strokeWidth = stroke
        tickPaint.strokeWidth = w * 0.012f
        val pad = stroke / 2f + w * 0.05f
        rect.set(pad, pad, w - pad, w - pad)

        gradient = SweepGradient(rect.centerX(), rect.centerY(), intArrayOf(accentAlt, accent, accent, accentAlt), floatArrayOf(0f, 0.6f, 0.75f, 1f)).apply {
            setLocalMatrix(Matrix().apply { setRotate(135f, rect.centerX(), rect.centerY()) })
        }
        valuePaint.textSize = w * 0.2f
        unitPaint.textSize = w * 0.075f
        labelPaint.textSize = w * 0.062f
    }

    override fun onDraw(canvas: Canvas) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = rect.width() / 2f

        // Tick marks inside the arc; angular dials use longer ticks on every other mark.
        val base = arcPaint.strokeWidth
        for (i in 0..10) {
            val long = gaugeStyle == STYLE_ANGULAR && i % 2 == 0
            val inner = radius - base * (if (long) 1.9f else 1.35f)
            val outer = radius - base * 0.95f
            val a = Math.toRadians((135.0 + 27.0 * i))
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            canvas.drawLine(cx + inner * c, cy + inner * s, cx + outer * c, cy + outer * s, tickPaint)
        }

        val frac = ((shown - min) / (max - min)).coerceIn(0f, 1f)
        val warn = (target ?: Float.MIN_VALUE) >= warnAt
        when {
            warn -> { arcPaint.shader = null; arcPaint.color = danger }
            gaugeStyle == STYLE_GRADIENT || gaugeStyle == STYLE_ANGULAR -> arcPaint.shader = gradient
            else -> { arcPaint.shader = null; arcPaint.color = accent }
        }

        if (gaugeStyle == STYLE_SEGMENTED) {
            // Like an old LCD bar graph: lit blocks up to the value, dim blocks after it.
            val step = 270f / SEGMENTS
            val lit = (frac * SEGMENTS).toInt()
            for (i in 0 until SEGMENTS) {
                canvas.drawArc(rect, 135f + i * step, step * 0.7f, false, if (i < lit) arcPaint else trackPaint)
            }
        } else {
            canvas.drawArc(rect, 135f, 270f, false, trackPaint)
            if (frac > 0.002f) canvas.drawArc(rect, 135f, 270f * frac, false, arcPaint)
        }

        val text = target?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: "—"
        valuePaint.color = if (warn) danger else textPrimary
        canvas.drawText(text, cx, cy + valuePaint.textSize * 0.33f, valuePaint)
        canvas.drawText(unit, cx, cy + valuePaint.textSize * 0.33f + unitPaint.textSize * 1.6f, unitPaint)
        canvas.drawText(label.uppercase(Locale.US), cx, rect.bottom + labelPaint.textSize * 0.2f, labelPaint)
    }

    companion object {
        private const val STYLE_GRADIENT = 0
        private const val STYLE_SEGMENTED = 2
        private const val STYLE_ANGULAR = 3
        private const val SEGMENTS = 27
    }
}
