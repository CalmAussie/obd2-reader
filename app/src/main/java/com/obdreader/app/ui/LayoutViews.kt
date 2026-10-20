package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import com.obdreader.app.R
import java.util.Locale
import kotlin.math.roundToInt

private fun View.dp(v: Float) = v * resources.displayMetrics.density

/** Data-logger channel colours, one per reading, like real logging software. */
object Channels {
    val RPM = Color.parseColor("#FF3B30")
    val SPEED = Color.parseColor("#3D9BFF")
    private val byPid = mapOf(
        "11" to "#34D17A", "04" to "#FFB000", "0F" to "#34C8E0", "0B" to "#B57CFF", "10" to "#FF7AC6",
        "0E" to "#E6E15A", "46" to "#9AA3AD", "5C" to "#FF8A3D", "VOLT" to "#7DD3A0",
    ).mapValues { Color.parseColor(it.value) }

    fun forKey(key: String, fallback: Int): Int = byPid[key] ?: fallback
}

/** Fifteen shift lights: five green, five amber, five red, then all flash blue at the redline. */
class ShiftLightsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val off = context.themeColor(R.attr.obdTrack)
    private val colours = intArrayOf(Color.parseColor("#34D17A"), Color.parseColor("#FFB000"), Color.parseColor("#FF3B30"))
    private val blue = Color.parseColor("#3D9BFF")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val glow = RectF()
    private var lit = 0
    private var flash = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setRpm(rpm: Float?, redline: Float) {
        val newLit = ShiftLights.lit(rpm, redline)
        val newFlash = rpm != null && rpm >= redline
        if (newLit != lit || newFlash != flash) { lit = newLit; flash = newFlash; invalidate() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(22f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val gap = dp(4f)
        val w = (width - gap * 14) / 15f
        val top = dp(4f)
        for (i in 0 until 15) {
            val x = i * (w + gap)
            rect.set(x, top, x + w, top + dp(14f))
            val on = flash || i < lit
            val c = if (flash) blue else colours[i / 5]
            if (on) {
                paint.color = ColorUtils.setAlphaComponent(c, 60)
                glow.set(rect.left - dp(2f), rect.top - dp(2f), rect.right + dp(2f), rect.bottom + dp(2f))
                canvas.drawRoundRect(glow, dp(4f), dp(4f), paint)
            }
            paint.color = if (on) c else off
            canvas.drawRoundRect(rect, dp(2f), dp(2f), paint)
        }
    }
}

/** A 30-second trace of engine speed and road speed, like a data logger's main graph. */
class TraceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var rpmMax = 8000f
    private val rpm = ArrayDeque<Float>()
    private val speed = ArrayDeque<Float>()
    private val grid = Paint().apply { color = context.themeColor(R.attr.obdOutline); strokeWidth = dp(1f) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2f); strokeJoin = Paint.Join.ROUND }
    private val path = Path()

    init {
        contentDescription = "Engine speed and road speed over the last 30 seconds"
    }

    private var lastPushMs = 0L

    fun push(r: Float?, s: Float?) {
        // One point every 320 ms however fast readings arrive, so the trace always spans 30 seconds.
        val now = android.os.SystemClock.uptimeMillis()
        if (rpm.isNotEmpty() && now - lastPushMs < TowerRowView.TRACE_STEP_MS) {
            rpm[rpm.size - 1] = r ?: 0f; speed[speed.size - 1] = s ?: 0f
            invalidate()
            return
        }
        lastPushMs = now
        rpm.addLast(r ?: 0f); speed.addLast(s ?: 0f)
        while (rpm.size > TowerRowView.HISTORY) rpm.removeFirst()
        while (speed.size > TowerRowView.HISTORY) speed.removeFirst()
        invalidate()
    }

    fun clear() { rpm.clear(); speed.clear(); invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(96f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 1..3) canvas.drawLine(0f, h * i / 4f, w, h * i / 4f, grid)
        draw(canvas, rpm, rpmMax, Channels.RPM, w, h)
        draw(canvas, speed, 160f, Channels.SPEED, w, h)
    }

    private fun draw(canvas: Canvas, data: ArrayDeque<Float>, max: Float, colour: Int, w: Float, h: Float) {
        if (data.size < 2) return
        val dx = w / (TowerRowView.HISTORY - 1)
        val x0 = w - (data.size - 1) * dx
        path.reset()
        data.forEachIndexed { i, v ->
            val y = h - dp(2f) - (v / max).coerceIn(0f, 1f) * (h - dp(4f))
            if (i == 0) path.moveTo(x0 + i * dx, y) else path.lineTo(x0 + i * dx, y)
        }
        line.color = colour
        canvas.drawPath(path, line)
    }
}

/** The digital cockpit's centrepiece: one big speed in a glowing ring, engine speed on a fine outer arc. */
class CockpitRingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var rpmMax = 8000f
    var warnAt = 6500f
    private var speed: Float? = null
    private var rpm: Float? = null
    private val smoothSpeed = SmoothValue(tauMs = 120f, settle = 0.05f)
    private val smoothRpm = SmoothValue(tauMs = 110f, settle = 2f)
    private val s1 = context.themeColor(R.attr.obdStripe1)
    private val s2 = context.themeColor(R.attr.obdStripe2)
    private val fg = context.themeColor(R.attr.obdTextPrimary)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val track = context.themeColor(R.attr.obdTrack)
    private val red = context.themeColor(R.attr.obdDanger)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = context.uiTypeface(300); textSize = 78f; color = fg; fontFeatureSettings = "tnum" }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = context.uiTypeface(400); textSize = 14f; color = muted }
    private val rpmText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = context.uiTypeface(400); textSize = 15f; color = fg; fontFeatureSettings = "tnum" }
    private val outer = RectF(26f, 16f, 234f, 224f)
    private val inner = RectF(42f, 32f, 218f, 208f)
    private val gradient = SweepGradient(130f, 120f, intArrayOf(s1, s2, s1), floatArrayOf(0f, 0.72f, 1f)).apply {
        setLocalMatrix(Matrix().apply { setRotate(140f, 130f, 120f) })
    }

    fun set(speed: Float?, rpm: Float?) {
        this.speed = speed
        this.rpm = rpm
        smoothSpeed.set(speed); smoothRpm.set(rpm)
        if (!isShown) { smoothSpeed.snap(); smoothRpm.snap() }
        contentDescription = "Speed ${speed?.roundToInt() ?: "unknown"} kilometres an hour, engine ${rpm?.roundToInt() ?: "unknown"} rpm"
        invalidate()
    }

    /** Largest height in pixels; landscape sets it so the ring fits the screen's height. */
    var maxSize = Int.MAX_VALUE
        set(value) { if (field != value) { field = value; requestLayout() } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, ((w.coerceAtMost(dp(320f).toInt()) * 230f / 260f).toInt()).coerceAtMost(maxSize))
    }

    override fun onDraw(canvas: Canvas) {
        val moving = smoothSpeed.step() or smoothRpm.step()
        val speed = this.speed?.let { smoothSpeed.shown }
        val rpm = this.rpm?.let { smoothRpm.shown }
        val scale = height / 230f
        canvas.save()
        canvas.translate((width - 260f * scale) / 2f, 0f)
        canvas.scale(scale, scale)
        val a0 = 140f
        val sweep = 260f
        arc.shader = null
        arc.strokeWidth = 3f
        arc.color = track
        canvas.drawArc(outer, a0, sweep, false, arc)
        val fw = (warnAt / rpmMax).coerceIn(0f, 1f)
        arc.color = ColorUtils.setAlphaComponent(red, 140)
        canvas.drawArc(outer, a0 + sweep * fw, sweep * (1 - fw), false, arc)
        val fr = ((rpm ?: 0f) / rpmMax).coerceIn(0f, 1f)
        if (fr > 0.003f) {
            arc.color = if ((rpm ?: 0f) >= warnAt) red else fg
            canvas.drawArc(outer, a0, sweep * fr, false, arc)
        }
        arc.strokeWidth = 8f
        arc.color = track
        canvas.drawArc(inner, a0, sweep, false, arc)
        val fs = ((speed ?: 0f) / 240f).coerceIn(0f, 1f)
        if (fs > 0.003f) {
            arc.shader = gradient
            arc.alpha = 70
            arc.strokeWidth = 16f
            canvas.drawArc(inner, a0, sweep * fs, false, arc)
            arc.alpha = 255
            arc.strokeWidth = 8f
            canvas.drawArc(inner, a0, sweep * fs, false, arc)
            arc.shader = null
        }
        canvas.drawText(speed?.roundToInt()?.toString() ?: "—", 130f, 138f, big)
        canvas.drawText("km/h", 130f, 164f, small)
        canvas.drawText(rpm?.let { String.format(Locale.US, "%,d rpm", ((it / 10f).roundToInt() * 10)) } ?: "— rpm", 130f, 212f, rpmText)
        canvas.restore()
        keepAnimating(moving)
    }
}

/**
 * The check-engine banner on the Diagnostics tab. On liveries it turns solid amber when codes
 * are stored; on the cockpit it glows amber instead.
 */
class MilBannerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    enum class State { NONE, ON, OK }

    var state = State.NONE
        set(value) { field = value; onStateChanged?.invoke(value); invalidate() }
    /** Lets the activity recolour the lamp and text when the state changes. */
    var onStateChanged: ((State) -> Unit)? = null

    private val layout = context.dashLayout()
    private val radius = context.themeDimen(R.attr.obdCardRadius)
    private val surface = context.themeColor(R.attr.obdSurface)
    private val outline = context.themeColor(R.attr.obdOutline)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val amber = context.themeColor(R.attr.obdWarn)
    private val green = context.themeColor(R.attr.obdOk)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        setWillNotDraw(false)
    }

    /** Colour for text and the lamp on top of the banner in its current state. */
    fun inkColor(primary: Int): Int = if (state == State.ON && layout != DashLayout.COCKPIT) 0xFF1A1100.toInt() else primary

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val solidAmber = state == State.ON && layout != DashLayout.COCKPIT
        paint.style = Paint.Style.FILL
        paint.color = when {
            solidAmber -> amber
            state == State.ON -> ColorUtils.setAlphaComponent(amber, 36)
            else -> surface
        }
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (layout == DashLayout.COCKPIT) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = if (state == State.ON) ColorUtils.setAlphaComponent(amber, 130) else outline
            canvas.drawRoundRect(rect, radius, radius, paint)
        } else {
            paint.style = Paint.Style.FILL
            paint.color = when (state) {
                State.ON -> 0xFF1A1100.toInt()
                State.OK -> green
                State.NONE -> muted
            }
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
            canvas.drawRect(0f, 0f, dp(6f), height.toFloat(), paint)
            canvas.restore()
        }
    }
}

/** The VIN on a sticker-style decal, with a livery stripe across its end. */
class VinDecalView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val layout = context.dashLayout()
    private val skew = context.skewDegrees()
    private val radius = context.themeDimen(R.attr.obdCardRadius)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()
    private val stripe = Path()

    /** Text on the decal reads in the page background colour, except on the cockpit's glass. */
    val inkColor: Int = if (layout == DashLayout.COCKPIT) context.themeColor(R.attr.obdTextPrimary) else context.themeColor(R.attr.obdBg)

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        if (layout == DashLayout.COCKPIT) {
            paint.style = Paint.Style.FILL
            paint.color = context.themeColor(R.attr.obdSurface)
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = context.themeColor(R.attr.obdOutline)
            canvas.drawRoundRect(rect, radius, radius, paint)
            return
        }
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        paint.style = Paint.Style.FILL
        paint.color = context.themeColor(R.attr.obdTextPrimary)
        canvas.drawRect(rect, paint)
        val o = skewOffset(if (skew == 0f) 12f else skew, h)
        stripe.reset()
        stripe.moveTo(w - dp(50f), 0f); stripe.lineTo(w, 0f); stripe.lineTo(w, h); stripe.lineTo(w - dp(50f) - o, h); stripe.close()
        val s1 = context.themeColor(R.attr.obdStripe1)
        paint.color = if (s1 == context.themeColor(R.attr.obdTextPrimary)) context.themeColor(R.attr.obdStripe2) else s1
        canvas.drawPath(stripe, paint)
        canvas.restore()
    }
}
