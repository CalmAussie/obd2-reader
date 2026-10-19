package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.ColorUtils
import com.obdreader.app.R
import com.obdreader.app.session.LiveHistory
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * One live strip chart on the Graphs tab: the reading's name and current value, its minimum and
 * maximum over the window, and a scrolling line that redraws every frame for smooth motion.
 * The vertical scale follows the data, so small changes are easy to see.
 */
class GraphStripView(
    context: Context,
    val key: String,
    private val label: String,
    private val unit: String,
    private val decimals: Int,
    private val rangeMin: Double,
    private val rangeMax: Double,
    private val colour: Int,
) : View(context) {

    /** How much time the chart shows. */
    var windowMs = 30_000L
        set(value) { field = value; invalidate() }

    /** Freezes the chart so a moment can be looked at closely. */
    var paused = false
        set(value) {
            field = value
            if (value) frozenAt = SystemClock.elapsedRealtime()
            invalidate()
        }
    private var frozenAt = 0L

    private val d = resources.displayMetrics.density
    private val layout = context.dashLayout()
    private val radius = context.themeDimen(R.attr.obdCardRadius)
    private val surface = context.themeColor(R.attr.obdSurface)
    private val outline = context.themeColor(R.attr.obdOutline)
    private val text = context.themeColor(R.attr.obdTextPrimary)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint().apply { color = outline; strokeWidth = d }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * d; strokeJoin = Paint.Join.ROUND; color = colour }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(600); textSize = 13 * d; color = text }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.displayTypeface(); textSize = 26 * d; color = text; textAlign = Paint.Align.RIGHT; fontFeatureSettings = "tnum" }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(400); textSize = 11 * d; color = muted; fontFeatureSettings = "tnum" }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(500); textSize = 12 * d; color = muted; textAlign = Paint.Align.RIGHT }
    private val rect = RectF()
    private val path = Path()
    private val area = Path()

    init {
        contentDescription = "$label graph"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (150 * d).toInt())
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.${decimals}f", v)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        panel.color = surface
        canvas.drawRoundRect(rect, radius, radius, panel)
        if (layout == DashLayout.COCKPIT) {
            panel.style = Paint.Style.STROKE; panel.strokeWidth = d; panel.color = outline
            canvas.drawRoundRect(rect, radius, radius, panel)
            panel.style = Paint.Style.FILL
        }
        panel.color = colour
        canvas.drawRect(0f, 0f, 3 * d, h, panel)

        val pad = 14 * d
        val now = if (paused) frozenAt else SystemClock.elapsedRealtime()
        val from = now - windowMs

        // Copy the visible part of the history.
        val times = ArrayList<Long>()
        val values = ArrayList<Double>()
        val series = LiveHistory.series(key)
        synchronized(LiveHistory) {
            for (i in series.times.indices) {
                val t = series.times[i]
                if (t in from..now) { times += t; values += series.values[i] }
            }
        }

        canvas.drawText(label, pad, pad + 12 * d, labelPaint)
        val current = values.lastOrNull()
        val unitW = unitPaint.measureText(unit)
        canvas.drawText(unit, w - pad, pad + 20 * d, unitPaint)
        valuePaint.color = text
        canvas.drawText(current?.let { fmt(it) } ?: "—", w - pad - unitW - 5 * d, pad + 20 * d, valuePaint)
        if (values.isNotEmpty()) {
            canvas.drawText("Min ${fmt(values.min())}   Max ${fmt(values.max())}", pad, pad + 30 * d, smallPaint)
        }

        val top = pad + 42 * d
        val bottom = h - 22 * d
        val left = pad
        val right = w - pad
        for (i in 0..2) {
            val y = top + (bottom - top) * i / 2f
            canvas.drawLine(left, y, right, y, grid)
        }
        // Time marks every 5 or 10 seconds, counting back from now.
        val step = if (windowMs <= 10_000L) 2_000L else if (windowMs <= 30_000L) 5_000L else 10_000L
        smallPaint.textAlign = Paint.Align.CENTER
        var back = 0L
        while (back <= windowMs) {
            val x = right - (right - left) * back / windowMs.toFloat()
            canvas.drawText(if (back == 0L) "now" else "-${back / 1000}s", x.coerceIn(left + 10 * d, right - 10 * d), h - 7 * d, smallPaint)
            back += step
        }
        smallPaint.textAlign = Paint.Align.LEFT

        if (values.size >= 2) {
            var lo = values.min()
            var hi = values.max()
            if (hi - lo < (rangeMax - rangeMin) * 0.05) {
                val mid = (hi + lo) / 2
                val half = (rangeMax - rangeMin) * 0.025
                lo = mid - half; hi = mid + half
            }
            val span = hi - lo
            lo = max(rangeMin - span, lo - span * 0.1)
            hi = min(rangeMax + span, hi + span * 0.1)
            path.reset(); area.reset()
            for (i in times.indices) {
                val x = right - (right - left) * (now - times[i]) / windowMs.toFloat()
                val y = (bottom - (bottom - top) * ((values[i] - lo) / (hi - lo))).toFloat()
                if (i == 0) { path.moveTo(x, y); area.moveTo(x, bottom); area.lineTo(x, y) } else { path.lineTo(x, y); area.lineTo(x, y) }
            }
            val lastX = right - (right - left) * (now - times.last()) / windowMs.toFloat()
            area.lineTo(lastX, bottom); area.close()
            fill.shader = LinearGradient(0f, top, 0f, bottom, ColorUtils.setAlphaComponent(colour, 70), ColorUtils.setAlphaComponent(colour, 0), Shader.TileMode.CLAMP)
            canvas.drawPath(area, fill)
            canvas.drawPath(path, line)
        } else {
            smallPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Waiting for readings…", w / 2, (top + bottom) / 2, smallPaint)
            smallPaint.textAlign = Paint.Align.LEFT
        }
        // Keep scrolling smoothly while the chart is on screen.
        if (!paused && isShown) postInvalidateOnAnimation()
    }
}
