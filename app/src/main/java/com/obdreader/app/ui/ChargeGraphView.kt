package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The EV battery's charge over the time the sheet has been open: a line of percent, scaled to
 * the range it moved through (at least 10 %), with the minutes along the bottom.
 */
class ChargeGraphView(context: Context, private val colour: Int, private val grid: Int, private val label: Int) : View(context) {

    private var points: List<Pair<Long, Double>> = emptyList()

    fun show(points: List<Pair<Long, Double>>) { this.points = points; invalidate() }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2.5f); color = colour; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour; alpha = 40 }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = grid; strokeWidth = dp(1f) }
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
    // Archivo in sp, like the rest of the app's labels, so it follows the font and text-size settings.
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = label; typeface = context.uiTypeface(400); textSize = sp(12f); fontFeatureSettings = "tnum" }

    private val emptyPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = label; typeface = context.uiTypeface(400); textSize = sp(13f) }

    init { contentDescription = "Battery charge over time" }

    private companion object { const val EMPTY = "The graph starts after a few readings." }

    override fun onDraw(canvas: Canvas) {
        // Room for the labels at any text size: percentages on the right, times along the bottom.
        val fm = text.fontMetrics
        val padL = dp(4f); val padR = text.measureText("100%") + dp(10f); val padT = dp(10f); val padB = fm.descent - fm.ascent + dp(6f)
        val w = width - padL - padR; val h = height - padT - padB
        if (w <= 0 || h <= 0) return
        if (points.size < 2) {
            // Centred and wrapped, so it fits at any text size.
            val msg = android.text.StaticLayout.Builder.obtain(EMPTY, 0, EMPTY.length, emptyPaint, width.coerceAtLeast(1))
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).build()
            canvas.save(); canvas.translate(0f, ((height - msg.height) / 2f).coerceAtLeast(0f)); msg.draw(canvas); canvas.restore()
            return
        }
        val lo0 = points.minOf { it.second }; val hi0 = points.maxOf { it.second }
        var lo = floor(lo0 / 5) * 5; var hi = ceil(hi0 / 5) * 5
        if (hi - lo < 10) { val mid = (lo + hi) / 2; lo = mid - 5; hi = mid + 5 }
        lo = lo.coerceAtLeast(0.0); hi = hi.coerceAtMost(100.0).coerceAtLeast(lo + 10)
        val t0 = points.first().first; val span = (points.last().first - t0).coerceAtLeast(1)
        fun x(t: Long) = padL + w * (t - t0) / span.toFloat()
        fun y(v: Double) = (padT + h * (1 - (v - lo) / (hi - lo))).toFloat()
        for (v in listOf(lo, (lo + hi) / 2, hi)) {
            canvas.drawLine(padL, y(v), padL + w, y(v), gridPaint)
            canvas.drawText(String.format(Locale.US, "%.0f%%", v), padL + w + dp(6f), y(v) - (fm.ascent + fm.descent) / 2, text)
        }
        val path = Path(); val area = Path()
        points.forEachIndexed { i, (t, v) ->
            if (i == 0) { path.moveTo(x(t), y(v)); area.moveTo(x(t), padT + h); area.lineTo(x(t), y(v)) }
            else { path.lineTo(x(t), y(v)); area.lineTo(x(t), y(v)) }
        }
        area.lineTo(x(points.last().first), padT + h); area.close()
        canvas.drawPath(area, fill)
        canvas.drawPath(path, line)
        val last = points.last()
        canvas.drawCircle(x(last.first), y(last.second), dp(4f), dot)
        val minutes = span / 60_000.0
        canvas.drawText("now", padL + w - text.measureText("now"), padT + h + dp(4f) - fm.ascent, text)
        canvas.drawText(if (minutes < 1) String.format(Locale.US, "%.0f s ago", span / 1000.0) else String.format(Locale.US, "%.0f min ago", minutes), padL, padT + h + dp(4f) - fm.ascent, text)
    }
}
