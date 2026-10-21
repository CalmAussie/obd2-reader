package com.obdreader.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.obdreader.app.R
import com.obdreader.app.obd.DrivingScore
import com.obdreader.app.obd.Route
import com.obdreader.app.obd.TripSummary
import com.obdreader.app.obd.Units
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A picture of a drive to post: the trip summary drawn in the chosen style's colours and
 * fonts, sized for Shorts, TikTok and Reels (9:16) or an Instagram post (1:1).
 */
class DriveCardRenderer(private val context: Context) {

    enum class Format(val label: String, val width: Int, val height: Int) {
        STORY("Story (9:16) for Shorts, TikTok and Reels", 1080, 1920),
        SQUARE("Square (1:1) for an Instagram post", 1080, 1080),
    }

    private val bg = context.themeColor(R.attr.obdBg)
    private val surface = context.themeColor(R.attr.obdSurface)
    private val s1 = context.themeColor(R.attr.obdStripe1)
    private val s2 = context.themeColor(R.attr.obdStripe2)
    private val text = context.themeColor(R.attr.obdTextPrimary)
    private val muted = context.themeColor(R.attr.obdTextSecondary)
    private val track = context.themeColor(R.attr.obdTrack)
    private val ok = context.themeColor(R.attr.obdOk)
    private val warn = context.themeColor(R.attr.obdWarn)
    private val danger = context.themeColor(R.attr.obdDanger)
    private val radius = context.themeDimen(R.attr.obdCardRadius).coerceAtMost(24f) * 2.5f
    private val title = context.titleTypeface()
    private val display = context.displayTypeface()
    private val ui = context.uiTypeface(500)
    private val uiBold = context.uiTypeface(600)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun scoreColour(s: Int) = when { s >= 80 -> ok; s >= 55 -> warn; else -> danger }

    /**
     * [route]: the drive's outline, from its recording when the route was saved; drawn as a line
     * on the card's own background with a start dot and an end dot. Null or empty: no outline.
     */
    fun render(trip: TripSummary, style: DrivingScore.Style, carLabel: String?, format: Format, route: Route? = null): Bitmap {
        val w = format.width; val h = format.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        val story = format == Format.STORY
        val showRoute = route != null && !route.isEmpty

        // Livery bands raked across the top.
        c.save()
        c.rotate(-7f, w / 2f, 120f)
        fill.color = s1; c.drawRect(-200f, 60f, w + 200f, 140f, fill)
        fill.color = s2; c.drawRect(-200f, 160f, w + 200f, 190f, fill)
        fill.color = s1; c.drawRect(-200f, 210f, w + 200f, 226f, fill)
        c.restore()

        var y = if (story) 380f else 340f
        paint.typeface = title; paint.color = text; paint.textSize = if (story) 96f else 78f; paint.textAlign = Paint.Align.LEFT
        c.drawText("My drive", 72f, y, paint)
        y += if (story) 62f else 54f
        paint.typeface = ui; paint.color = muted; paint.textSize = 36f
        val day = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(trip.startedAt))
        c.drawText(listOfNotNull(day, carLabel).joinToString("  ·  "), 72f, y, paint)

        // The driving score in the chosen style, in a ring.
        val score = trip.scores[style]
        // With a route, the story card puts the ring on the left and the outline beside it.
        val cx = if (story && !showRoute) w / 2f else if (story) 270f else 240f
        val cy = if (story) y + 260f else y + 230f
        val r = if (story && !showRoute) 190f else if (story) 170f else 150f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        stroke.strokeWidth = if (story) 34f else 26f
        stroke.shader = null; stroke.color = track
        c.drawArc(rect, 135f, 270f, false, stroke)
        if (score != null) {
            stroke.color = scoreColour(score)
            c.drawArc(rect, 135f, 270f * score / 100f, false, stroke)
        }
        paint.typeface = display; paint.color = text; paint.textAlign = Paint.Align.CENTER; paint.textSize = if (story) 170f else 120f
        c.drawText(score?.toString() ?: "—", cx, cy + paint.textSize * 0.32f, paint)
        paint.typeface = uiBold; paint.color = muted; paint.textSize = if (story) 38f else 30f
        c.drawText("${style.label} score", cx, cy + r * 0.78f, paint)

        // Stats.
        val stats = listOfNotNull(
            "Distance" to TripSummary.distance(Units.distance(trip.distanceKm)).replace(" km", " ${Units.distanceUnit}"),
            "Driving time" to TripSummary.duration(trip.drivingSeconds),
            trip.topSpeed?.let { "Top speed" to Units.speedText(it) },
            trip.averageSpeed?.let { "Average" to Units.speedText(it) },
            trip.best0to100?.let { "0–100 km/h" to String.format(Locale.US, "%.1f s", it) },
            "Idling" to TripSummary.duration(trip.idleSeconds),
        ).take(if (story) 6 else if (showRoute) 2 else 4)
        val gridLeft = if (story) 72f else 470f
        val gridRight = w - 72f
        val gap = 24f
        val cols = 2
        val tileW = (gridRight - gridLeft - gap) / cols
        val tileH = 170f
        var gy = if (story) cy + r + 80f else y + 70f
        stats.chunked(cols).forEach { row ->
            row.forEachIndexed { i, (label, value) ->
                val x = gridLeft + i * (tileW + gap)
                rect.set(x, gy, x + tileW, gy + tileH)
                fill.shader = null; fill.color = surface
                c.drawRoundRect(rect, radius, radius, fill)
                fill.color = s1
                c.drawRect(x, gy + 24f, x + 8f, gy + tileH - 24f, fill)
                paint.textAlign = Paint.Align.LEFT; paint.typeface = ui; paint.color = muted; paint.textSize = 32f
                c.drawText(label, x + 36f, gy + 62f, paint)
                paint.typeface = display; paint.color = text; paint.textSize = if (story) 76f else 64f
                fitText(value, tileW - 60f)
                c.drawText(value, x + 36f, gy + tileH - 40f, paint)
            }
            gy += tileH + gap
        }

        // The route: on the story card beside the ring, on the square card under the stats.
        if (showRoute) {
            if (story) drawRoute(c, route!!, 520f, cy - 190f, w - 72f, cy + 190f)
            else drawRoute(c, route!!, gridLeft, gy + 10f, gridRight, h - 110f)
        }

        // All three styles.
        if (story) {
            gy += 20f
            rect.set(72f, gy, w - 72f, gy + 200f)
            fill.color = surface
            c.drawRoundRect(rect, radius, radius, fill)
            val colW = (w - 144f) / 3
            DrivingScore.Style.values().forEachIndexed { i, st ->
                val sx = 72f + colW * i + colW / 2
                val v = trip.scores[st]
                paint.textAlign = Paint.Align.CENTER; paint.typeface = display; paint.textSize = 76f
                paint.color = v?.let { scoreColour(it) } ?: muted
                c.drawText(v?.toString() ?: "—", sx, gy + 110f, paint)
                paint.typeface = ui; paint.color = muted; paint.textSize = 30f
                c.drawText(st.label, sx, gy + 160f, paint)
            }
        }

        // Footer.
        fill.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, s1, s2, Shader.TileMode.CLAMP)
        c.drawRect(0f, h - 14f, w.toFloat(), h.toFloat(), fill)
        fill.shader = null
        paint.textAlign = Paint.Align.LEFT; paint.typeface = title; paint.color = ColorUtils.setAlphaComponent(text, 200); paint.textSize = 40f
        c.drawText("OBD2 Reader", 72f, h - 60f, paint)
        return bmp
    }

    /** The route as a line on the card's own background, with a start dot and an end dot. */
    private fun drawRoute(c: Canvas, route: Route, left: Float, top: Float, right: Float, bottom: Float) {
        val pts = route.fit(right - left, bottom - top, 28f)
        if (pts.size < 2) return
        val path = android.graphics.Path()
        pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(left + x, top + y) else path.lineTo(left + x, top + y) }
        stroke.shader = null; stroke.color = s1; stroke.strokeWidth = 10f; stroke.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, stroke)
        val (sx, sy) = pts.first(); val (ex, ey) = pts.last()
        // Start: a ring in the second livery colour. End: a solid dot in the first, with a ring.
        fill.shader = null
        fill.color = bg; c.drawCircle(left + sx, top + sy, 20f, fill)
        stroke.color = s2; stroke.strokeWidth = 8f; c.drawCircle(left + sx, top + sy, 16f, stroke)
        fill.color = s1; c.drawCircle(left + ex, top + ey, 20f, fill)
        stroke.color = text; stroke.strokeWidth = 5f; c.drawCircle(left + ex, top + ey, 24f, stroke)
    }

    /** Shrinks the paint's text size until [s] fits [maxWidth]. */
    private fun fitText(s: String, maxWidth: Float) {
        while (paint.measureText(s) > maxWidth && paint.textSize > 24f) paint.textSize -= 2f
    }
}
