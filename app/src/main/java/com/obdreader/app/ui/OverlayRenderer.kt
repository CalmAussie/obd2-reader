package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import com.obdreader.app.R
import com.obdreader.app.obd.DriveTrack
import com.obdreader.app.obd.Route
import com.obdreader.app.obd.Units
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Draws one frame of the video overlay: the readings at a moment of a recorded drive, in the
 * chosen style's colours and fonts, on a plain background the video editor removes (green
 * screen) or blends away (black). Everything sits in the bottom corners, out of the way of the
 * road. Sizes are for a 1920 × 1080 frame and scale to any other size.
 */
class OverlayRenderer(context: Context, private val background: Int, private val redline: Float, private val rpmMax: Float) {

    private val accent = context.themeColor(R.attr.obdStripe1)
    private val accent2 = context.themeColor(R.attr.obdStripe2)
    private val panel = context.themeColor(R.attr.obdSurface)
    private val track = context.themeColor(R.attr.obdTrack)
    private val text = context.themeColor(R.attr.obdTextPrimary)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val danger = context.themeColor(R.attr.obdDanger)
    private val radius = context.themeDimen(R.attr.obdCardRadius).coerceAtMost(24f) * 2f
    private val leds = intArrayOf(Color.parseColor("#34D17A"), Color.parseColor("#FFB000"), Color.parseColor("#FF3B30"))
    private val display = context.displayTypeface()
    private val ui = context.uiTypeface(500)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; fontFeatureSettings = "tnum" }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ui }
    private val rect = RectF()
    private val path = Path()

    fun draw(canvas: Canvas, drive: DriveTrack, seconds: Double) {
        val u = canvas.width / 1920f
        canvas.drawColor(background)
        canvas.save()
        canvas.scale(u, u)
        val s = drive.at(seconds)
        val rpm = s.rpm?.toFloat()
        val speed = s.speed?.let { Units.speed(it) }?.toFloat()

        // ---- Bottom left: shift lights, rev arc with rpm, speed, throttle ----
        rect.set(60f, 700f, 900f, 1020f)
        fill.color = panel
        canvas.drawRoundRect(rect, radius, radius, fill)

        // Shift lights: the whole rev range, all lit just below the redline, flashing past it.
        val lit = if (rpm == null || rpm <= 0f) 0 else (rpm / (redline * 0.97f / 15f)).toInt().coerceIn(1, 15)
        val over = rpm != null && rpm >= redline && ((seconds * 8).toInt() % 2 == 0)
        for (i in 0 until 15) {
            val x = 100f + i * 50f
            rect.set(x, 724f, x + 40f, 744f)
            fill.color = when {
                over -> Color.parseColor("#3D9BFF")
                i < lit -> leds[i / 5]
                else -> track
            }
            canvas.drawRoundRect(rect, 5f, 5f, fill)
        }

        // Rev arc.
        val cx = 230f; val cy = 885f; val r = 105f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        stroke.strokeWidth = 22f
        stroke.color = track
        canvas.drawArc(rect, 135f, 270f, false, stroke)
        val fw = (redline / rpmMax).coerceIn(0f, 1f)
        stroke.color = ColorUtils.setAlphaComponent(danger, 110)
        canvas.drawArc(rect, 135f + 270f * fw, 270f * (1 - fw), false, stroke)
        val fr = ((rpm ?: 0f) / rpmMax).coerceIn(0f, 1f)
        if (fr > 0.003f) {
            stroke.color = if ((rpm ?: 0f) >= redline) danger else accent
            canvas.drawArc(rect, 135f, 270f * fr, false, stroke)
        }
        big.textAlign = Paint.Align.CENTER; big.color = text; big.textSize = 58f
        // Sized for the widest reading, so it fits inside the arc and doesn't jump about.
        while (big.measureText("8,888") > r * 1.45f && big.textSize > 30f) big.textSize -= 2f
        canvas.drawText(rpm?.let { String.format(Locale.US, "%,d", (it / 10f).roundToInt() * 10) } ?: "—", cx, cy + 18f, big)
        small.textAlign = Paint.Align.CENTER; small.color = muted; small.textSize = 26f
        canvas.drawText("rpm", cx, cy + 56f, small)

        // Speed.
        big.textAlign = Paint.Align.LEFT; big.textSize = 170f
        val sp = speed?.roundToInt()?.toString() ?: "—"
        canvas.drawText(sp, 380f, 950f, big)
        small.textAlign = Paint.Align.LEFT; small.color = muted; small.textSize = 32f
        canvas.drawText(Units.speedUnit, 380f + big.measureText(sp) + 16f, 950f, small)
        drive.topSpeedUntil(seconds)?.let {
            small.textSize = 28f
            canvas.drawText("Top ${Units.speedText(it)}", 384f, 996f, small)
        }

        // Throttle bar.
        rect.set(840f, 772f, 868f, 996f)
        fill.color = track
        canvas.drawRoundRect(rect, 8f, 8f, fill)
        val ft = ((s.throttle ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
        rect.set(840f, 996f - 224f * ft, 868f, 996f)
        fill.color = accent2.takeIf { it != panel && ColorUtils.calculateContrast(it, panel) > 1.5 } ?: accent
        canvas.drawRoundRect(rect, 8f, 8f, fill)
        small.textAlign = Paint.Align.CENTER; small.textSize = 20f
        canvas.drawText("THR", 854f, 764f, small)

        // ---- Bottom right: the last 30 seconds of speed and revs ----
        rect.set(1180f, 820f, 1860f, 1020f)
        fill.color = panel
        canvas.drawRoundRect(rect, radius, radius, fill)
        val left = 1210f; val right = 1830f; val top = 850f; val bottom = 990f
        stroke.strokeWidth = 1.5f; stroke.color = track
        for (k in 1..2) { val y = top + (bottom - top) * k / 3f; canvas.drawLine(left, y, right, y, stroke) }
        fun trace(color: Int, value: (DriveTrack.Sample) -> Double?, max: Double) {
            path.reset()
            var started = false
            val steps = 60
            for (i in 0..steps) {
                val t = seconds - 30.0 + 30.0 * i / steps
                if (t < 0) continue
                val v = value(drive.at(t)) ?: continue
                val x = left + (right - left) * i / steps
                val y = bottom - (bottom - top) * (v / max).coerceIn(0.0, 1.0).toFloat()
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            stroke.strokeWidth = 5f; stroke.color = color; stroke.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, stroke)
        }
        trace(Channels.RPM, { it.rpm }, rpmMax.toDouble())
        trace(Channels.SPEED, { it.speed }, 200.0)
        small.textAlign = Paint.Align.RIGHT; small.textSize = 22f; small.color = muted
        canvas.drawText("Last 30 s", right, 844f, small)
        small.textAlign = Paint.Align.LEFT
        small.color = Channels.RPM; canvas.drawText("■ rpm", left, 844f, small)
        small.color = Channels.SPEED; canvas.drawText("■ ${Units.speedUnit}", left + 90f, 844f, small)

        // ---- Right, above the trace: a mini-map of the route with a dot where the car is now ----
        drive.route?.let { route -> drawMiniMap(canvas, drive, route, seconds, u) }

        // Drive clock, top centre of the trace panel.
        val clock = seconds.toInt()
        small.textAlign = Paint.Align.CENTER; small.textSize = 22f; small.color = muted
        canvas.drawText(String.format(Locale.US, "%d:%02d", clock / 60, clock % 60), (left + right) / 2f, 844f, small)
        canvas.restore()
    }

    /** The map's box, in 1920 × 1080 units: above the trace panel, on the right. */
    private val mapBox = RectF(1560f, 500f, 1860f, 800f)
    private val mapPad = 26f
    private var mapCache: android.graphics.Bitmap? = null
    private var mapCacheFor: Pair<Route, Int>? = null

    private fun drawMiniMap(canvas: Canvas, drive: DriveTrack, route: Route, seconds: Double, u: Float) {
        fill.color = panel
        canvas.drawRoundRect(mapBox, radius, radius, fill)
        // The outline is the same every frame, so it's drawn once and reused.
        val px = (mapBox.width() * u).roundToInt().coerceAtLeast(1)
        if (mapCacheFor != route to px) {
            mapCache?.recycle()
            mapCache = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888).also { bmp ->
                val c = Canvas(bmp); c.scale(u, u)
                drawShadedRoute(c, route, 0f, 0f, mapBox.width(), mapBox.height(), mapPad, accent, 6f, Paint(Paint.ANTI_ALIAS_FLAG))
            }
            mapCacheFor = route to px
        }
        mapCache?.let { canvas.drawBitmap(it, null, mapBox, null) }
        val here = drive.position(seconds) ?: return
        val project = route.projector(mapBox.width(), mapBox.height(), mapPad) ?: return
        val (x, y) = project(here.first, here.second)
        fill.color = text
        canvas.drawCircle(mapBox.left + x, mapBox.top + y, 13f, fill)
        fill.color = accent
        canvas.drawCircle(mapBox.left + x, mapBox.top + y, 8f, fill)
    }
}
