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
import android.view.animation.DecelerateInterpolator
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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(110f).toInt())
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
            paint.color = s1
            canvas.drawRect(left, y, right, y + dp(30f), paint)
        }
        y += dp(37f)
        paint.color = s2
        canvas.drawRect(left, y, right, y + dp(12f), paint)
        y += dp(19f)
        paint.color = s1
        canvas.drawRect(left, y, right, y + dp(6f), paint)
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
    private var animator: ValueAnimator? = null
    private var sweeping = false

    private val ring = context.themeColor(R.attr.obdRing)
    private val track = context.themeColor(R.attr.obdTrack)
    private val red = context.themeColor(R.attr.obdDanger)
    private val amber = context.themeColor(R.attr.obdWarn)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val redTrack = ColorUtils.blendARGB(track, red, 0.28f)
    private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT; strokeWidth = 18f }
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
        if (sweeping) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, (value ?: 0f).coerceIn(0f, max)).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
        contentDescription = value?.let { "Engine speed ${it.roundToInt()} rpm" } ?: "Engine speed"
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
        val text = target?.let { ((it / 10f).roundToInt() * 10).toString() } ?: "—"
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
    }
}

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
