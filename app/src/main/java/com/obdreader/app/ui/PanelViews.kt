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

private fun View.dp(v: Float) = v * resources.displayMetrics.density

/** Formats a reading, or a dash when there isn't one. */
internal fun fmt(value: Double?, decimals: Int): String =
    value?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: "—"

/** A plain panel background in the theme's surface colour and corner radius. */
fun Context.panelBackground(stroke: Boolean = dashLayout() == DashLayout.COCKPIT): GradientDrawable =
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

    fun setValue(v: Double?) {
        value = v
        contentDescription = if (v == null) label else "$label ${fmt(v, decimals)} $unit"
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

        val x = o * 0.55f + dp(20f)
        canvas.drawText(label, x, dp(26f), labelPaint)
        val v = fmt(value, decimals)
        valuePaint.color = if (hot) danger else text
        val baseline = if (wide) h - dp(20f) else dp(64f)
        canvas.drawText(v, x - o * 0.25f, baseline, valuePaint)
        canvas.drawText(unit, x - o * 0.25f + valuePaint.measureText(v) + dp(5f), baseline, unitPaint)
        if (!wide) {
            val y = h - dp(14f)
            val left = x - o * 0.4f
            val right = width - o - dp(14f)
            paint.color = track
            canvas.drawRect(left, y, right, y + dp(4f), paint)
            val f = value?.let { ((it - min) / (max - min)).coerceIn(0.0, 1.0).toFloat() } ?: 0f
            paint.color = text
            canvas.drawRect(left, y, left + (right - left) * f, y + dp(4f), paint)
        }
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
    private var unsupported = false
    private val history = ArrayDeque<Double>()
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
        if (v == null) history.clear() else {
            history.addLast(v)
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
        if (layout == DashLayout.COCKPIT) {
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
        }
        val x = tabW + dp(14f)
        val right = w - dp(14f)
        val top = if (layout == DashLayout.TELEMETRY) dp(28f) else dp(30f)
        canvas.drawText(label, x, top, labelPaint)
        val unitW = unitPaint.measureText(unit)
        canvas.drawText(unit, right, top, unitPaint)
        valuePaint.color = if (hot) danger else text
        canvas.drawText(if (unsupported) "n/a" else fmt(value, decimals), right - unitW - dp(4f), top + dp(1f), valuePaint)

        val f = value?.let { ((it - min) / (max - min)).coerceIn(0.0, 1.0).toFloat() } ?: 0f
        when (layout) {
            DashLayout.TELEMETRY -> drawTrace(canvas, x, right, dp(40f), h - dp(8f), if (hot) danger else channel)
            else -> {
                val y = h - dp(12f)
                paint.color = track
                canvas.drawRect(x, y, right, y + dp(3f), paint)
                if (layout == DashLayout.COCKPIT) {
                    paint.shader = LinearGradient(x, 0f, right, 0f, stripe, stripe2, Shader.TileMode.CLAMP)
                } else {
                    paint.color = if (hot) danger else muted
                }
                canvas.drawRect(x, y, x + (right - x) * f, y + dp(3f), paint)
                paint.shader = null
            }
        }
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
        /** About 30 seconds of readings at the app's polling rate. */
        const val HISTORY = 94
    }
}

/** A button that matches the theme: an angled decal on liveries, a pill on the cockpit, a square tile on telemetry. */
class SkewButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Kind { PRIMARY, OUTLINE, DANGER }

    var text: String = ""
        set(value) { field = value; contentDescription = value; invalidate() }
    var kind = Kind.PRIMARY
        set(value) { field = value; invalidate() }
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

    fun setTextSizeSp(sp: Float) { textPaint.textSize = sp * resources.displayMetrics.scaledDensity; invalidate() }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.38f
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) (textPaint.measureText(text) + dp(48f)).toInt() else MeasureSpec.getSize(widthMeasureSpec)
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else dp(52f).toInt()
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
