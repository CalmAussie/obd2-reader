package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** A tiny picture of a theme for the picker: its background, a card, a dial and a line of text. */
class ThemeSwatchView(context: Context, private val theme: AppThemes.AppTheme) : View(context) {

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = if (theme.segmented || theme.cut) Paint.Cap.BUTT else Paint.Cap.ROUND
    }
    private val box = RectF()
    private val arc = RectF()
    private val path = Path()

    /** Draws [box] with the theme's corners: rounded, or cut on the top-left and bottom-right. */
    private fun shape(canvas: Canvas, r: Float, paint: Paint) {
        if (!theme.cut) {
            canvas.drawRoundRect(box, r, r, paint)
            return
        }
        path.reset()
        path.moveTo(box.left + r, box.top)
        path.lineTo(box.right, box.top)
        path.lineTo(box.right, box.bottom - r)
        path.lineTo(box.right - r, box.bottom)
        path.lineTo(box.left, box.bottom)
        path.lineTo(box.left, box.top + r)
        path.close()
        canvas.drawPath(path, paint)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = theme.previewRadiusDp * density

        fill.color = theme.previewBg
        box.set(0f, 0f, w, h)
        shape(canvas, r, fill)

        fill.color = theme.previewSurface
        box.set(w * 0.08f, h * 0.12f, w * 0.92f, h * 0.88f)
        shape(canvas, r * 0.8f, fill)

        // Dial
        val d = h * 0.5f
        arc.set(w * 0.16f, h * 0.24f, w * 0.16f + d, h * 0.24f + d)
        stroke.strokeWidth = h * 0.09f
        stroke.color = theme.previewSurface xor 0x00202020
        canvas.drawArc(arc, 135f, 270f, false, stroke)
        stroke.color = theme.previewAccent
        if (theme.segmented) {
            for (i in 0 until 6) canvas.drawArc(arc, 135f + i * 30f, 20f, false, stroke)
        } else {
            canvas.drawArc(arc, 135f, 170f, false, stroke)
        }

        // Text lines
        fill.color = theme.previewText
        val x = w * 0.16f + d + w * 0.08f
        box.set(x, h * 0.34f, w * 0.84f, h * 0.34f + h * 0.08f)
        canvas.drawRoundRect(box, h * 0.04f, h * 0.04f, fill)
        fill.color = theme.previewAccentAlt
        box.set(x, h * 0.52f, x + (w * 0.84f - x) * 0.6f, h * 0.52f + h * 0.08f)
        canvas.drawRoundRect(box, h * 0.04f, h * 0.04f, fill)
    }
}
