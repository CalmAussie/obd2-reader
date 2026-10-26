package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View

/**
 * A small line of scores out of 100, oldest on the left, for Drive history. A faint line marks 50;
 * the latest score is a bigger dot, labelled.
 */
class TrendView(
    context: Context,
    private val scores: List<Int>,
    private val colour: Int,
    private val grid: Int,
    private val label: Int,
) : View(context) {

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2.5f); color = colour; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = grid; strokeWidth = dp(1f) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = label; textSize = dp(11f) }

    override fun onDraw(canvas: Canvas) {
        if (scores.size < 2) return
        val padL = dp(4f); val padR = dp(28f); val padT = dp(10f); val padB = dp(10f)
        val w = width - padL - padR; val h = height - padT - padB
        fun x(i: Int) = padL + w * i / (scores.size - 1)
        fun y(v: Int) = padT + h * (1f - v.coerceIn(0, 100) / 100f)
        canvas.drawLine(padL, y(50), padL + w, y(50), gridPaint)
        canvas.drawText("50", padL + w + dp(6f), y(50) + dp(4f), text)
        val path = Path()
        scores.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        canvas.drawPath(path, line)
        scores.forEachIndexed { i, v -> canvas.drawCircle(x(i), y(v), if (i == scores.lastIndex) dp(5f) else dp(2.5f), dot) }
        text.color = colour
        canvas.drawText(scores.last().toString(), padL + w + dp(6f), y(scores.last()) + dp(4f), text)
        text.color = label
    }
}
