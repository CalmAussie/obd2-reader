package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.ContextThemeWrapper
import android.view.View
import com.obdreader.app.R

/** A small picture of a style for the picker, drawn with that style's own colours. */
class ThemeSwatchView(context: Context, theme: AppThemes.AppTheme) : View(context) {

    private val t = ContextThemeWrapper(context, theme.style)
    private val layout = t.dashLayout()
    private val bg = t.themeColor(R.attr.obdBg)
    private val s1 = t.themeColor(R.attr.obdStripe1)
    private val s2 = t.themeColor(R.attr.obdStripe2)
    private val kerb = t.themeBool(R.attr.obdKerb)
    private val roundel = t.themeColor(R.attr.obdRoundel)
    private val ink = t.themeColor(R.attr.obdRoundelInk)
    private val fg = t.themeColor(R.attr.obdTextPrimary)
    private val display = t.displayTypeface()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val u = w / 72f
        rect.set(0f, 0f, w, h)
        paint.shader = if (layout == DashLayout.COCKPIT) RadialGradient(w / 2, h * 0.3f, w * 0.8f, Color.parseColor("#16244A"), bg, Shader.TileMode.CLAMP) else null
        paint.color = bg
        canvas.drawRoundRect(rect, 4 * u, 4 * u, paint)
        paint.shader = null
        when (layout) {
            DashLayout.LIVERY -> {
                canvas.save()
                canvas.clipRect(rect)
                canvas.skew(0f, -0.21f)
                if (kerb) {
                    for (i in 0 until 6) { paint.color = if (i % 2 == 0) s1 else s2; canvas.drawRect(i * 14 * u, 18 * u, (i + 1) * 14 * u, 27 * u, paint) }
                } else {
                    paint.color = s1; canvas.drawRect(0f, 18 * u, w, 27 * u, paint)
                    paint.color = s2; canvas.drawRect(0f, 30 * u, w, 34 * u, paint)
                }
                canvas.restore()
                paint.color = roundel
                canvas.drawCircle(50 * u, 34 * u, 13 * u, paint)
                text.typeface = display; text.textSize = 13 * u; text.color = ink
                canvas.drawText("42", 50 * u, 39 * u, text)
            }
            DashLayout.TELEMETRY -> {
                val leds = intArrayOf(Color.parseColor("#34D17A"), Color.parseColor("#FFB000"), Color.parseColor("#FF3B30"))
                for (i in 0 until 9) { paint.color = leds[i / 3]; canvas.drawRoundRect(RectF((6 + i * 6.8f) * u, 7 * u, (11 + i * 6.8f) * u, 11 * u), u, u, paint) }
                text.typeface = display; text.textSize = 15 * u; text.color = fg; text.textAlign = Paint.Align.LEFT
                canvas.drawText("3420", 7 * u, 31 * u, text)
                text.textAlign = Paint.Align.CENTER
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.6f * u
                paint.color = Channels.RPM
                canvas.drawLines(floatArrayOf(6f, 46f, 16f, 40f, 16f, 40f, 24f, 43f, 24f, 43f, 34f, 33f, 34f, 33f, 44f, 38f, 44f, 38f, 54f, 30f, 54f, 30f, 66f, 35f).map { it * u }.toFloatArray(), paint)
                paint.color = Channels.SPEED
                canvas.drawLines(floatArrayOf(6f, 49f, 18f, 46f, 18f, 46f, 30f, 47f, 30f, 47f, 42f, 42f, 42f, 42f, 54f, 44f, 54f, 44f, 66f, 41f).map { it * u }.toFloatArray(), paint)
                paint.style = Paint.Style.FILL
            }
            DashLayout.COCKPIT -> {
                val ring = RectF(17 * u, 10 * u, 55 * u, 48 * u)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * u; paint.strokeCap = Paint.Cap.ROUND
                paint.color = Color.argb(30, 255, 255, 255)
                canvas.drawArc(ring, 140f, 260f, false, paint)
                paint.color = Color.BLACK; paint.shader = LinearGradient(17 * u, 0f, 55 * u, 0f, s1, s2, Shader.TileMode.CLAMP)
                canvas.drawArc(ring, 140f, 160f, false, paint)
                paint.shader = null; paint.style = Paint.Style.FILL; paint.strokeCap = Paint.Cap.BUTT
                text.typeface = t.uiTypeface(300); text.textSize = 14 * u; text.color = fg
                canvas.drawText("87", 36 * u, 34 * u, text)
            }
            DashLayout.CYBERPUNK -> {
                // A climbing neon rev bar over split-colour digits.
                for (i in 0 until 9) {
                    val x = (7 + i * 6.6f) * u
                    val top = (17 - i * 1.1f) * u
                    paint.color = if (i < 6) androidx.core.graphics.ColorUtils.blendARGB(s2, s1, i / 5f) else Color.argb(40, 255, 255, 255)
                    canvas.drawRect(x, top, x + 5 * u, 19 * u, paint)
                }
                text.typeface = display; text.textSize = 17 * u
                text.color = s1; canvas.drawText("88", 35 * u, 42 * u, text)
                text.color = s2; canvas.drawText("88", 37 * u, 42 * u, text)
                text.color = fg; canvas.drawText("88", 36 * u, 42 * u, text)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = u; paint.color = s2
                canvas.drawLine(6 * u, 49 * u, 30 * u, 49 * u, paint)
                paint.color = s1
                canvas.drawLine(42 * u, 49 * u, 66 * u, 49 * u, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }
}
