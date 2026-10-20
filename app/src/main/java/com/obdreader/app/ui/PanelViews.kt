package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import androidx.core.graphics.ColorUtils
import com.obdreader.app.R
import java.util.Locale
import kotlin.math.roundToInt

private fun View.dp(v: Float) = v * resources.displayMetrics.density

/** Formats a reading, or a dash when there isn't one. */
/** [s] shortened with "…" so it fits in [avail] pixels. */
internal fun fitText(paint: Paint, s: String, avail: Float): String {
    if (avail <= 0f || paint.measureText(s) <= avail) return s
    var t = s
    while (t.length > 1 && paint.measureText(t.trimEnd() + "…") > avail) t = t.dropLast(1)
    return t.trimEnd() + "…"
}

internal fun fmt(value: Double?, decimals: Int): String =
    value?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: "—"

/** A plain panel background in the theme's surface colour and corner radius. */
fun Context.panelBackground(stroke: Boolean = dashLayout().let { it == DashLayout.COCKPIT || it == DashLayout.CYBERPUNK }): GradientDrawable =
    GradientDrawable().apply {
        cornerRadius = themeDimen(R.attr.obdCardRadius)
        setColor(themeColor(R.attr.obdSurface))
        if (stroke) setStroke((resources.displayMetrics.density).toInt().coerceAtLeast(1), themeColor(R.attr.obdOutline))
    }

/** A livery decal: an angled panel with a stripe down its leading edge, a label and a big number. */
class DecalView(
    context: Context,
    private val label: String,
    private val unit: String,
    private val min: Double,
    private val max: Double,
    private val warnAbove: Double?,
    private val decimals: Int,
    private val wide: Boolean,
) : View(context) {

    private var value: Double? = null
    private val smooth = SmoothValue(tauMs = 120f, settle = 0.4f * Math.pow(10.0, -decimals.toDouble()).toFloat())
    private val skew = context.skewDegrees()
    private val radius = context.themeDimen(R.attr.obdCardRadius)
    private val surface = context.themeColor(R.attr.obdSurface)
    private val stripe = context.themeColor(R.attr.obdStripe1)
    private val danger = context.themeColor(R.attr.obdDanger)
    private val text = context.themeColor(R.attr.obdTextPrimary)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val track = context.themeColor(R.attr.obdTrack)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(500); textSize = dp(13f); color = muted }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = context.displayTypeface(); textSize = dp(if (wide) 54f else 32f); fontFeatureSettings = "tnum"
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(500); textSize = dp(13f); color = muted }
    private val path = Path()
    private val stripePath = Path()
    private val rect = RectF()

    init {
        contentDescription = label
    }

    private var unsupported = false

    /** Shown instead of the usual label, for a stand-in reading ("Accelerator pedal" for throttle). */
    var labelOverride: String? = null
        set(v) { if (field != v) { field = v; invalidate() } }

    /** The drive's top speed, shown in the corner of the wide speed decal. */
    var top: Double? = null
        set(v) { if (field != v) { field = v; invalidate() } }

    fun setValue(v: Double?) {
        unsupported = false
        value = v
        smooth.set(v?.toFloat())
        if (!isShown) smooth.snap()
        contentDescription = if (v == null) label else "$label ${fmt(v, decimals)} $unit"
        invalidate()
    }

    /** The car doesn't report this reading: shows "N/A" rather than a dash that looks like it's still loading, or a made-up number. */
    fun setUnsupported() {
        unsupported = true
        contentDescription = "$label not reported by this car"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(if (wide) 104f else 92f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val o = skewOffset(skew, h)
        rect.set(0f, 0f, width.toFloat(), h)
        path.panel(rect, skew, radius)
        canvas.save()
        canvas.clipPath(path)
        paint.color = surface
        canvas.drawPath(path, paint)
        val hot = value != null && warnAbove != null && value!! >= warnAbove
        // The leading-edge stripe follows the lean of the panel.
        paint.color = if (hot) danger else stripe
        stripePath.reset()
        stripePath.moveTo(o, 0f); stripePath.lineTo(o + dp(6f), 0f); stripePath.lineTo(dp(6f), h); stripePath.lineTo(0f, h); stripePath.close()
        canvas.drawPath(stripePath, paint)
        canvas.restore()

        val moving = smooth.step()
        val shownValue = value?.let { smooth.shown.toDouble() }
        val x = o * 0.55f + dp(20f)
        canvas.drawText(fitText(labelPaint, labelOverride ?: label, width - x - dp(20f)), x, dp(26f), labelPaint)
        top?.takeIf { wide }?.let {
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Top ${it.roundToInt()} $unit", width - dp(20f), dp(26f), labelPaint)
            labelPaint.textAlign = Paint.Align.LEFT
        }
        val v = if (unsupported) "N/A" else fmt(shownValue, decimals)
        valuePaint.color = if (hot) danger else text
        val baseline = if (wide) h - dp(20f) else dp(64f)
        canvas.drawText(v, x - o * 0.25f, baseline, valuePaint)
        if (!unsupported) canvas.drawText(unit, x - o * 0.25f + valuePaint.measureText(v) + dp(5f), baseline, unitPaint)
        if (!wide) {
            val y = h - dp(14f)
            val left = x - o * 0.4f
            val right = width - o - dp(14f)
            paint.color = track
            canvas.drawRect(left, y, right, y + dp(4f), paint)
            val f = shownValue?.let { ((it - min) / (max - min)).coerceIn(0.0, 1.0).toFloat() } ?: 0f
            paint.color = text
            canvas.drawRect(left, y, left + (right - left) * f, y + dp(4f), paint)
        }
        keepAnimating(moving)
    }
}

/**
 * One row of the live-data list. Livery themes show it as a timing-tower row with an angled tab;
 * the telemetry layout adds a scrolling 30-second trace; the cockpit layout a soft glass row.
 */
class TowerRowView(
    context: Context,
    private val label: String,
    private val unit: String,
    private val min: Double,
    private val max: Double,
    private val warnAbove: Double?,
    private val decimals: Int,
    private val channel: Int,
) : View(context) {

    private val layout = context.dashLayout()
    private var value: Double? = null
    private val smooth = SmoothValue(tauMs = 120f, settle = 0.4f * Math.pow(10.0, -decimals.toDouble()).toFloat())
    private var unsupported = false

    /** Shown instead of the usual label, for a stand-in reading ("Accelerator pedal" for throttle). */
    var labelOverride: String? = null
        set(v) { if (field != v) { field = v; invalidate() } }
    private val history = ArrayDeque<Double>()
    private var lastPushMs = 0L
    private val skew = context.skewDegrees()
    private val radius = context.themeDimen(R.attr.obdCardRadius)
    private val surface = context.themeColor(R.attr.obdSurface)
    private val outline = context.themeColor(R.attr.obdOutline)
    private val stripe = context.themeColor(R.attr.obdStripe1)
    private val stripe2 = context.themeColor(R.attr.obdStripe2)
    private val danger = context.themeColor(R.attr.obdDanger)
    private val text = context.themeColor(R.attr.obdTextPrimary)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val track = context.themeColor(R.attr.obdTrack)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.6f); strokeJoin = Paint.Join.ROUND }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(500); textSize = dp(14f); color = text }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = context.displayTypeface(); textSize = dp(if (layout == DashLayout.TELEMETRY) 22f else 21f)
        fontFeatureSettings = "tnum"; textAlign = Paint.Align.RIGHT
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.uiTypeface(500); textSize = dp(12f); color = muted; textAlign = Paint.Align.RIGHT }
    private val rect = RectF()
    private val path = Path()
    private val trace = Path()

    init {
        contentDescription = label
    }

    fun setValue(v: Double?) {
        unsupported = false
        value = v
        smooth.set(v?.toFloat())
        if (!isShown) smooth.snap()
        if (v == null) history.clear() else {
            // One trace point every 320 ms however fast readings arrive, so the trace always spans 30 seconds.
            val now = android.os.SystemClock.uptimeMillis()
            if (history.isEmpty() || now - lastPushMs >= TRACE_STEP_MS) {
                history.addLast(v); lastPushMs = now
            } else {
                history[history.size - 1] = v
            }
            while (history.size > HISTORY) history.removeFirst()
        }
        contentDescription = if (v == null) label else "$label ${fmt(v, decimals)} $unit"
        invalidate()
    }

    fun setUnsupported() {
        unsupported = true
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(if (layout == DashLayout.TELEMETRY) 74f else 58f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = surface
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (layout == DashLayout.COCKPIT || layout == DashLayout.CYBERPUNK) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f); paint.color = outline
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }
        val hot = value != null && warnAbove != null && value!! >= warnAbove
        val tabW: Float
        when (layout) {
            DashLayout.LIVERY -> {
                tabW = dp(12f)
                rect.set(-dp(4f), 0f, tabW, h)
                path.panel(rect, skew, 0f)
                paint.color = if (hot) danger else stripe
                canvas.save(); canvas.clipRect(0f, 0f, w, h); canvas.drawPath(path, paint); canvas.restore()
            }
            DashLayout.TELEMETRY -> {
                tabW = dp(3f)
                paint.color = if (hot) danger else channel
                canvas.drawRect(0f, 0f, tabW, h, paint)
            }
            DashLayout.COCKPIT -> tabW = 0f
            DashLayout.CYBERPUNK -> {
                tabW = dp(3f)
                paint.color = if (hot) danger else stripe2
                canvas.drawRect(0f, 0f, tabW, h, paint)
            }
        }
        val x = tabW + dp(14f)
        val right = w - dp(14f)
        val top = if (layout == DashLayout.TELEMETRY) dp(28f) else dp(30f)
        // Room for the label: the row less the unit and a typical number.
        val labelRoom = right - x - unitPaint.measureText(unit) - valuePaint.measureText("888.8") - dp(12f)
        canvas.drawText(fitText(labelPaint, labelOverride ?: label, labelRoom), x, top, labelPaint)
        val unitW = if (unsupported) 0f else unitPaint.measureText(unit)
        if (!unsupported) canvas.drawText(unit, right, top, unitPaint)
        valuePaint.color = if (hot) danger else text
        val moving = smooth.step()
        val shownValue = value?.let { smooth.shown.toDouble() }
        canvas.drawText(if (unsupported) "N/A" else fmt(shownValue, decimals), right - unitW - dp(4f), top + dp(1f), valuePaint)

        val f = shownValue?.let { ((it - min) / (max - min)).coerceIn(0.0, 1.0).toFloat() } ?: 0f
        when (layout) {
            DashLayout.TELEMETRY -> drawTrace(canvas, x, right, dp(40f), h - dp(8f), if (hot) danger else channel)
            else -> {
                val y = h - dp(12f)
                paint.color = track
                canvas.drawRect(x, y, right, y + dp(3f), paint)
                if (layout == DashLayout.COCKPIT || layout == DashLayout.CYBERPUNK) {
                    paint.shader = LinearGradient(x, 0f, right, 0f, if (layout == DashLayout.CYBERPUNK) stripe2 else stripe, if (layout == DashLayout.CYBERPUNK) stripe else stripe2, Shader.TileMode.CLAMP)
                } else {
                    paint.color = if (hot) danger else muted
                }
                canvas.drawRect(x, y, x + (right - x) * f, y + dp(3f), paint)
                paint.shader = null
            }
        }
        keepAnimating(moving)
    }

    private fun drawTrace(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float, color: Int) {
        if (history.size < 2) return
        val dx = (right - left) / (HISTORY - 1)
        val x0 = right - (history.size - 1) * dx
        trace.reset()
        history.forEachIndexed { i, v ->
            val f = ((v - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
            val px = x0 + i * dx
            val py = bottom - f * (bottom - top)
            if (i == 0) trace.moveTo(px, py) else trace.lineTo(px, py)
        }
        line.color = color
        canvas.drawPath(trace, line)
    }

    companion object {
        /** 30 seconds of trace: one point every [TRACE_STEP_MS]. */
        const val HISTORY = 94
        const val TRACE_STEP_MS = 320L
    }
}

/** A button that matches the theme: an angled decal on liveries, a pill on the cockpit, a square tile on telemetry. */
class SkewButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Kind { PRIMARY, OUTLINE, DANGER }

    var text: String = ""
        set(value) { if (value != field) { field = value; contentDescription = value; requestLayout(); invalidate() } }
    var kind = Kind.PRIMARY
        set(value) { if (value != field) { field = value; invalidate() } }
    /** Uses the theme's big display font, for the main Connect button. */
    var display = false
        set(value) { field = value; textPaint.typeface = if (value) context.displayTypeface() else context.uiTypeface(600); invalidate() }

    private val layout = context.dashLayout()
    private val skew = context.skewDegrees()
    private val radius = context.themeDimen(R.attr.obdButtonRadius)
    private val accent = context.themeColor(R.attr.obdAccent)
    private val onAccent = context.themeColor(R.attr.obdOnAccent)
    private val outline = context.themeColor(R.attr.obdOutline)
    private val fg = context.themeColor(R.attr.obdTextPrimary)
    private val danger = context.themeColor(R.attr.obdDanger)
    private val s1 = context.themeColor(R.attr.obdStripe1)
    private val s2 = context.themeColor(R.attr.obdStripe2)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = context.uiTypeface(600); textSize = dp(15f) }
    private val path = Path()
    private val rect = RectF()

    init {
        isClickable = true
        isFocusable = true
    }

    fun setTextSizeSp(sp: Float) {
        textPaint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        invalidate()
    }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    override fun setEnabled(enabled: Boolean) {
        if (enabled == isEnabled) return
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.38f
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // wrap_content (AT_MOST) means "as wide as the label", not "all the space offered": taking
        // it all squeezed the Connect button beside the Demo button to nothing on Android 9 and newer.
        val wanted = (textPaint.measureText(text) + dp(48f)).toInt()
        val w = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(wanted, MeasureSpec.getSize(widthMeasureSpec))
            else -> wanted
        }
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(dp(52f).toInt(), MeasureSpec.getSize(heightMeasureSpec))
            else -> dp(52f).toInt()
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = dp(2f)
        rect.set(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2)
        val r = if (layout == DashLayout.COCKPIT) h / 2 else radius
        path.panel(rect, skew, r)
        paint.shader = null
        if (kind == Kind.PRIMARY) {
            paint.style = Paint.Style.FILL
            if (layout == DashLayout.COCKPIT) paint.shader = LinearGradient(0f, 0f, w, 0f, s1, s2, Shader.TileMode.CLAMP) else paint.color = accent
            canvas.drawPath(path, paint)
            paint.shader = null
            textPaint.color = onAccent
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = outline
            canvas.drawPath(path, paint)
            textPaint.color = if (kind == Kind.DANGER) danger else fg
        }
        if (isPressed) {
            paint.style = Paint.Style.FILL
            paint.color = ColorUtils.setAlphaComponent(if (kind == Kind.PRIMARY) onAccent else fg, 36)
            canvas.drawPath(path, paint)
        }
        val fm = textPaint.fontMetrics
        canvas.drawText(text, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, textPaint)
    }
}

/** A trouble code on a bold plate: amber when stored, outlined when pending. */
class PlateView(context: Context, private val code: String, private val stored: Boolean) : View(context) {
    private val layout = context.dashLayout()
    private val radius = context.themeDimen(R.attr.obdCardRadius)
    private val amber = context.themeColor(R.attr.obdWarn)
    private val fg = context.themeColor(R.attr.obdTextPrimary)
    private val bg = context.themeColor(R.attr.obdBg)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = context.displayTypeface(); textSize = dp(21f)
    }
    private val rect = RectF()

    init {
        contentDescription = "$code, ${if (stored) "stored" else "pending"}"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(112f).toInt(), dp(42f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = if (layout == DashLayout.COCKPIT) h / 2 else radius
        rect.set(0f, 0f, w, h)
        paint.style = Paint.Style.FILL
        paint.color = if (stored) amber else fg
        canvas.drawRoundRect(rect, r, r, paint)
        val inset = dp(3f)
        rect.inset(inset, inset)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.5f)
        paint.color = if (stored) amber else bg
        canvas.drawRoundRect(rect, r, r, paint)
        textPaint.color = if (stored) 0xFF1A1100.toInt() else bg
        val fm = textPaint.fontMetrics
        canvas.drawText(code, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, textPaint)
    }
}
