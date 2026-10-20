package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.obdreader.app.R
import java.util.Locale
import kotlin.math.roundToInt

private fun View.dp(v: Float) = v * resources.displayMetrics.density

/** A panel outline with two corners cut off, the cyberpunk HUD's frame shape. */
private fun Path.chamfer(l: Float, t: Float, r: Float, b: Float, cut: Float) {
    reset()
    moveTo(l + cut, t); lineTo(r, t); lineTo(r, b - cut); lineTo(r - cut, b); lineTo(l, b); lineTo(l, t + cut); close()
}

/**
 * The Cyberpunk layout's main display, a neon heads-up display: a segmented rev bar that
 * climbs from cyan to magenta (red past the redline), the speed in big split-colour digits,
 * and a strip of bars showing the last 15 seconds of engine speed. Everything glows.
 */
class CyberHudView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var rpmMax = 8000f
        set(v) { field = v; invalidate() }
    var warnAt = 6500f
        set(v) { field = v; invalidate() }

    private var speed: Float? = null
    private var rpm: Float? = null
    private val smoothSpeed = SmoothValue(tauMs = 120f, settle = 0.05f)
    private val smoothRpm = SmoothValue(tauMs = 110f, settle = 2f)
    private val history = FloatArray(HISTORY)
    private var count = 0
    private var lastPushMs = 0L

    private val magenta = context.themeColor(R.attr.obdStripe1)
    private val cyan = context.themeColor(R.attr.obdStripe2)
    private val fg = context.themeColor(R.attr.obdTextPrimary)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val track = context.themeColor(R.attr.obdTrack)
    private val red = context.themeColor(R.attr.obdDanger)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.MITER }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(600); letterSpacing = 0.22f }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.displayTypeface(); fontFeatureSettings = "tnum" }
    private val path = Path()
    private val seg = Path()
    private var scanlines = FloatArray(0)

    /** The drive's top speed, shown beside KM/H. */
    var top: Float? = null
        set(v) { if (field != v) { field = v; invalidate() } }

    fun set(speed: Float?, rpm: Float?) {
        this.speed = speed
        this.rpm = rpm
        smoothSpeed.set(speed); smoothRpm.set(rpm)
        if (!isShown) { smoothSpeed.snap(); smoothRpm.snap() }
        contentDescription = "Speed ${speed?.roundToInt() ?: "unknown"} kilometres an hour, engine ${rpm?.roundToInt() ?: "unknown"} rpm"
        invalidate()
    }

    /** Adds a point to the bar strip, at most one every 320 ms so the strip always spans the same time. */
    fun push(rpm: Float?) {
        if (rpm == null) { count = 0; invalidate(); return }
        val now = android.os.SystemClock.uptimeMillis()
        if (count == 0 || now - lastPushMs >= STEP_MS) {
            if (count == HISTORY) System.arraycopy(history, 1, history, 0, HISTORY - 1) else count++
            lastPushMs = now
        }
        history[count - 1] = rpm
        invalidate()
    }

    /** Largest height in pixels; landscape sets it so the display fits the screen's height. */
    var maxSize = Int.MAX_VALUE
        set(value) { if (field != value) { field = value; requestLayout() } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, ((w.coerceAtMost(dp(420f).toInt()) * H / W).toInt()).coerceAtMost(maxSize))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Faint horizontal scanlines, like an old monitor, every 3 dp.
        val step = dp(3f)
        val n = (h / step).toInt()
        scanlines = FloatArray(n * 4) { i ->
            when (i % 4) { 0 -> 0f; 2 -> w.toFloat(); else -> (i / 4) * step + step / 2 }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val moving = smoothSpeed.step() or smoothRpm.step()
        val speed = this.speed?.let { smoothSpeed.shown }
        val rpm = this.rpm?.let { smoothRpm.shown }
        val u = height / H
        canvas.save()
        canvas.translate((width - W * u) / 2f, 0f)
        canvas.scale(u, u)

        // Frame, with a soft glow behind the line.
        path.chamfer(1f, 1f, W - 1f, H - 1f, 16f)
        stroke.color = ColorUtils.setAlphaComponent(cyan, 36); stroke.strokeWidth = 6f
        canvas.drawPath(path, stroke)
        stroke.color = ColorUtils.setAlphaComponent(cyan, 170); stroke.strokeWidth = 1.2f
        canvas.drawPath(path, stroke)

        // Header: a label on the left, the exact rpm on the right.
        label.textSize = 10f; label.color = magenta; label.textAlign = Paint.Align.LEFT
        canvas.drawText("SYS//TACH", 22f, 28f, label)
        number.textSize = 14f; number.textAlign = Paint.Align.RIGHT
        number.color = if ((rpm ?: 0f) >= warnAt) red else cyan
        canvas.drawText(rpm?.let { String.format(Locale.US, "%,d RPM", ((it / 10f).roundToInt() * 10)) } ?: "— RPM", W - 22f, 29f, number)

        // The rev bar: segments that grow taller to the right and lean like italics.
        val left = 18f
        val right = W - 18f
        val bottom = 98f
        val gap = 3f
        val segW = (right - left - gap * (SEGMENTS - 1)) / SEGMENTS
        val fr = ((rpm ?: 0f) / rpmMax).coerceIn(0f, 1f)
        val fw = (warnAt / rpmMax).coerceIn(0f, 1f)
        for (i in 0 until SEGMENTS) {
            val x = left + i * (segW + gap)
            val h = 16f + 30f * i / (SEGMENTS - 1)
            val lean = h * 0.3f
            seg.reset()
            seg.moveTo(x + lean, bottom - h); seg.lineTo(x + segW + lean, bottom - h); seg.lineTo(x + segW, bottom); seg.lineTo(x, bottom); seg.close()
            val mid = (i + 0.5f) / SEGMENTS
            val lit = fr > 0.003f && mid <= fr + 0.5f / SEGMENTS
            if (lit) {
                val c = if (mid >= fw) red else ColorUtils.blendARGB(cyan, magenta, i / (SEGMENTS - 1f))
                stroke.color = ColorUtils.setAlphaComponent(c, 60); stroke.strokeWidth = 5f
                canvas.drawPath(seg, stroke)
                fill.color = c
            } else {
                fill.color = if (mid >= fw) ColorUtils.setAlphaComponent(red, 50) else track
            }
            canvas.drawPath(seg, fill)
        }
        // Scale under the bar, in thousands.
        label.textSize = 8f; label.color = muted; label.textAlign = Paint.Align.CENTER
        var k = 0
        while (k * 1000 <= rpmMax) {
            canvas.drawText(k.toString(), left + (right - left) * (k * 1000f / rpmMax), bottom + 13f, label)
            k += if (rpmMax > 10000) 2 else 1
        }

        // The speed: split into magenta and cyan ghosts behind the white digits.
        val s = speed?.roundToInt()?.toString() ?: "—"
        number.textSize = 86f; number.textAlign = Paint.Align.CENTER
        number.color = ColorUtils.setAlphaComponent(magenta, 190)
        canvas.drawText(s, W / 2f - 3f, 196f, number)
        number.color = ColorUtils.setAlphaComponent(cyan, 190)
        canvas.drawText(s, W / 2f + 3f, 196f, number)
        number.color = fg
        canvas.drawText(s, W / 2f, 196f, number)
        label.textSize = 10f; label.color = cyan
        canvas.drawText("KM/H", W / 2f, 214f, label)
        top?.let {
            label.color = magenta; label.textAlign = Paint.Align.RIGHT
            canvas.drawText("TOP ${it.roundToInt()}", W - 22f, 214f, label)
            label.textAlign = Paint.Align.CENTER
        }

        // The last 15 seconds of revs, as a strip of bars.
        val barTop = 226f
        val barBottom = H - 12f
        val bw = (right - left) / HISTORY
        for (i in 0 until HISTORY) {
            val j = i - (HISTORY - count)
            val f = if (j >= 0) (history[j] / rpmMax).coerceIn(0f, 1f) else 0f
            val top = barBottom - (barBottom - barTop) * f.coerceAtLeast(0.06f)
            fill.color = when {
                j < 0 -> track
                j == count - 1 -> magenta
                history[j] >= warnAt -> red
                else -> ColorUtils.setAlphaComponent(cyan, 170)
            }
            canvas.drawRect(left + i * bw + 0.6f, top, left + (i + 1) * bw - 0.6f, barBottom, fill)
        }
        canvas.restore()

        stroke.color = ColorUtils.setAlphaComponent(fg, 9); stroke.strokeWidth = 1f
        canvas.drawLines(scanlines, stroke)
        keepAnimating(moving)
    }

    private companion object {
        const val W = 360f
        const val H = 250f
        const val SEGMENTS = 26
        const val HISTORY = 48
        const val STEP_MS = 320L
    }
}

/** A small neon readout for the Cyberpunk layout: coolant, fuel or battery, with a segment bar. */
class CyberChipView(
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
    private val magenta = context.themeColor(R.attr.obdStripe1)
    private val cyan = context.themeColor(R.attr.obdStripe2)
    private val fg = context.themeColor(R.attr.obdTextPrimary)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val surface = context.themeColor(R.attr.obdSurface)
    private val track = context.themeColor(R.attr.obdTrack)
    private val red = context.themeColor(R.attr.obdDanger)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(600); letterSpacing = 0.2f; textSize = dp(9.5f) }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.displayTypeface(); fontFeatureSettings = "tnum"; textSize = dp(20f) }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(500); textSize = dp(11f); color = muted }
    private val path = Path()

    init {
        contentDescription = name
    }

    private var unsupported = false

    /** The car doesn't report this reading. */
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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(76f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val moving = smooth.step()
        val w = width.toFloat()
        val h = height.toFloat()
        val shown = value?.let { smooth.shown }
        val hot = shown != null && warnAt != null && shown >= warnAt
        val edge = if (hot) red else cyan
        path.chamfer(dp(1f), dp(1f), w - dp(1f), h - dp(1f), dp(10f))
        fill.color = surface
        canvas.drawPath(path, fill)
        stroke.strokeWidth = dp(4f); stroke.color = ColorUtils.setAlphaComponent(edge, 30)
        canvas.drawPath(path, stroke)
        stroke.strokeWidth = dp(1f); stroke.color = ColorUtils.setAlphaComponent(edge, 150)
        canvas.drawPath(path, stroke)

        val pad = dp(11f)
        label.color = magenta
        canvas.drawText(name.uppercase(Locale.US), pad, dp(20f), label)
        number.color = if (hot) red else fg
        val text = if (unsupported) "n/a" else shown?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: "—"
        canvas.drawText(text, pad, dp(46f), number)
        canvas.drawText(unit, pad + number.measureText(text) + dp(4f), dp(46f), unitPaint)

        // Ten segments along the bottom.
        val f = shown?.let { ((it - min) / (max - min)).coerceIn(0f, 1f) } ?: 0f
        val left = pad
        val right = w - pad
        val gap = dp(2f)
        val sw = (right - left - gap * 9) / 10
        val y = h - dp(17f)
        for (i in 0 until 10) {
            val lit = shown != null && (i + 0.5f) / 10 <= f + 0.05f
            fill.color = if (lit) (if (hot) red else ColorUtils.blendARGB(cyan, magenta, i / 9f)) else track
            val x = left + i * (sw + gap)
            canvas.drawRect(x, y, x + sw, y + dp(5f), fill)
        }
        keepAnimating(moving)
    }
}
