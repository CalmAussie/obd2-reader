package com.obdreader.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.obdreader.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun View.dp(v: Float) = v * resources.displayMetrics.density

/** Three flat livery bands raked across the top of a page (kerb pattern on the Kerb livery). */
class LiveryStripesView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val s1 = context.themeColor(R.attr.obdStripe1)
    private val s2 = context.themeColor(R.attr.obdStripe2)
    private val kerb = context.themeBool(R.attr.obdKerb)
    private val glow = context.themeBool(R.attr.obdGlow)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(110f).toInt())
    }

    /** A band, with a soft neon halo behind it on glowing liveries (drawn as wider translucent layers, which stays fast). */
    private fun band(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, colour: Int) {
        if (glow) {
            for ((spread, alpha) in listOf(dp(6f) to 28, dp(3f) to 60)) {
                paint.color = ColorUtils.setAlphaComponent(colour, alpha)
                canvas.drawRect(l, t - spread, r, b + spread, paint)
            }
        }
        paint.color = colour
        canvas.drawRect(l, t, r, b, paint)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        canvas.save()
        canvas.rotate(-7f, w / 2f, dp(46f))
        val left = -w * 0.25f
        val right = w * 1.25f
        var y = dp(20f)
        // Main band
        if (kerb) {
            val block = dp(28f)
            var x = left
            var i = 0
            while (x < right) {
                paint.color = if (i % 2 == 0) s1 else s2
                canvas.drawRect(x, y, x + block, y + dp(30f), paint)
                x += block; i++
            }
        } else {
            band(canvas, left, y, right, y + dp(30f), s1)
        }
        y += dp(37f)
        band(canvas, left, y, right, y + dp(12f), s2)
        y += dp(19f)
        band(canvas, left, y, right, y + dp(6f), s1)
        canvas.restore()
    }
}

/** The race-number roundel: live RPM on the disc, with a flat segmented rev ring around it. */
class RoundelView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var max = 8000f
        set(value) { field = value; invalidate() }
    var warnAt = 6500f
        set(value) { field = value; invalidate() }
    var checkEngine = false
        set(value) { if (field != value) { field = value; invalidate() } }

    private var target: Float? = null
    private var shown = 0f
    private val smooth = SmoothValue(tauMs = 110f, settle = 2f)
    private var animator: ValueAnimator? = null
    private var sweeping = false

    private val ring = context.themeColor(R.attr.obdRing)
    private val track = context.themeColor(R.attr.obdTrack)
    private val red = context.themeColor(R.attr.obdDanger)
    private val amber = context.themeColor(R.attr.obdWarn)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val redTrack = ColorUtils.blendARGB(track, red, 0.28f)
    private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT; strokeWidth = 18f }
    private val glow = context.themeBool(R.attr.obdGlow)
    private val edgeGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = context.displayTypeface(); textSize = 44f
        color = context.themeColor(R.attr.obdRoundelInk); fontFeatureSettings = "tnum"
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = context.uiTypeface(600); textSize = 13f
        color = ColorUtils.setAlphaComponent(context.themeColor(R.attr.obdRoundelInk), 150)
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = context.uiTypeface(600); textSize = 11f; color = muted
    }
    private val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = context.uiTypeface(600); textSize = 10.5f; color = 0xFF1A1100.toInt()
    }
    private val disc = context.themeColor(R.attr.obdRoundel)
    private val arcRect = RectF(12f, 12f, 228f, 228f)
    private val tag = Path().apply { moveTo(84f, 174f); lineTo(160f, 174f); lineTo(156f, 190f); lineTo(80f, 190f); close() }

    init {
        contentDescription = "Engine speed"
    }

    fun setValue(value: Float?) {
        target = value
        smooth.set(value)
        contentDescription = value?.let { "Engine speed ${it.roundToInt()} rpm" } ?: "Engine speed"
        if (sweeping) return
        // Off screen (another tab, or the screen is off): jump straight to the value, so nothing
        // keeps redrawing a dial nobody can see.
        if (!isShown) smooth.snap()
        invalidate()
    }

    /** Sweeps the ring to the end stop and back, like a car's self-test when you turn the key. */
    fun sweep() {
        animator?.cancel()
        sweeping = true
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            addUpdateListener {
                shown = max * sin(PI * (it.animatedValue as Float)).toFloat()
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    sweeping = false
                    smooth.from(0f) // the sweep ends at zero; glide up to the live reading from there
                    setValue(target)
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(dp(300f).toInt())
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), w)
    }

    override fun onDraw(canvas: Canvas) {
        val size = height.toFloat()
        canvas.save()
        canvas.translate((width - size) / 2f, 0f)
        canvas.scale(size / 240f, size / 240f)

        val moving = !sweeping && smooth.step()
        if (!sweeping) shown = if (target == null) 0f else smooth.shown.coerceIn(0f, max)
        val n = (max / 500f).roundToInt().coerceAtLeast(1)
        val step = 270f / n
        val frac = (shown / max).coerceIn(0f, 1f)
        val shift = (target ?: 0f) >= warnAt
        for (i in 0 until n) {
            val top = (i + 1) * 500f
            val inRed = top > warnAt
            val lit = (i + 1f) / n <= frac + 0.0001f
            segPaint.color = when {
                !lit -> if (inRed) redTrack else track
                inRed || shift -> red
                else -> ring
            }
            if (glow && lit) {
                // Neon: soft wider strokes behind each lit segment.
                val c = segPaint.color
                for ((width, alpha) in GLOW_PASSES) {
                    segPaint.strokeWidth = width
                    segPaint.color = ColorUtils.setAlphaComponent(c, alpha)
                    canvas.drawArc(arcRect, 135f + i * step + 0.9f, step - 1.8f, false, segPaint)
                }
                segPaint.strokeWidth = 18f
                segPaint.color = c
            }
            canvas.drawArc(arcRect, 135f + i * step + 0.9f, step - 1.8f, false, segPaint)
        }
        fill.color = muted
        var k = 1000f
        while (k < max) {
            val a = Math.toRadians((135f + 270f * k / max).toDouble())
            canvas.drawCircle(120f + 88f * cos(a).toFloat(), 120f + 88f * sin(a).toFloat(), 1.6f, fill)
            k += 1000f
        }
        fill.color = disc
        canvas.drawCircle(120f, 120f, 80f, fill)
        if (glow) {
            // A neon edge on the disc, in the number's colour.
            edgeGlow.color = ColorUtils.setAlphaComponent(numPaint.color, 70); edgeGlow.strokeWidth = 6f
            canvas.drawCircle(120f, 120f, 80f, edgeGlow)
            edgeGlow.color = numPaint.color; edgeGlow.strokeWidth = 1.5f
            canvas.drawCircle(120f, 120f, 80f, edgeGlow)
        }
        val text = if (target == null) "—" else ((smooth.shown / 10f).roundToInt() * 10).toString()
        canvas.drawText(text, 120f, 138f, numPaint)
        canvas.drawText("rpm", 120f, 162f, subPaint)
        if (checkEngine) {
            fill.color = amber
            canvas.drawPath(tag, fill)
            canvas.drawText("Check engine", 120f, 186f, tagText)
        }
        val a0 = Math.toRadians(135.0)
        val a1 = Math.toRadians(405.0)
        canvas.drawText("0", 120f + 118f * cos(a0).toFloat(), 232f, edgePaint)
        canvas.drawText("${(max / 1000).roundToInt()}k", 120f + 118f * cos(a1).toFloat(), 232f, edgePaint)
        canvas.restore()
        keepAnimating(moving)
    }
}

private val GLOW_PASSES = listOf(28f to 40, 23f to 70)

/** Five start lights: they come on one by one while the app talks to the car, then go out. */
class StartLightsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val off = context.themeColor(R.attr.obdTrack)
    private val red = context.themeColor(R.attr.obdDanger)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ticker = Handler(Looper.getMainLooper())
    private var lit = 0
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (lit < 5) { lit++; invalidate(); ticker.postDelayed(this, 320) }
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun start() {
        if (running) return
        running = true
        lit = 0
        invalidate()
        ticker.postDelayed(tick, 320)
    }

    fun out() {
        running = false
        ticker.removeCallbacks(tick)
        lit = 0
        invalidate()
    }

    override fun onDetachedFromWindow() {
        ticker.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(28f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val r = dp(8f)
        val gap = dp(10f)
        val total = 5 * 2 * r + 4 * gap
        var x = (width - total) / 2f + r
        val y = height / 2f
        for (i in 0 until 5) {
            if (i < lit) {
                paint.color = ColorUtils.setAlphaComponent(red, 70)
                canvas.drawCircle(x, y, r * 1.6f, paint)
                paint.color = red
            } else {
                paint.color = off
            }
            canvas.drawCircle(x, y, r, paint)
            x += 2 * r + gap
        }
    }
}
