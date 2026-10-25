package com.obdreader.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.obdreader.app.R
import com.obdreader.app.obd.DrivingScore
import com.obdreader.app.obd.Route
import com.obdreader.app.obd.ShareScore
import com.obdreader.app.obd.TripSummary
import com.obdreader.app.obd.Units
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A picture of a drive to post: the trip summary drawn in the look of the Dashboard layout the
 * user shared it from (the liveries, Race telemetry, Digital cockpit or Cyberpunk), sized for
 * Shorts, TikTok and Reels (9:16) or an Instagram post (1:1), with a cheeky line about the score.
 */
class DriveCardRenderer(private val context: Context) {

    enum class Format(val label: String, val width: Int, val height: Int) {
        STORY("Story (9:16) for Shorts, TikTok and Reels", 1080, 1920),
        SQUARE("Square (1:1) for an Instagram post", 1080, 1080),
    }

    private val layout = context.dashLayout()
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
    private val uiLight = context.uiTypeface(300)
    private val uiBold = context.uiTypeface(600)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun scoreColour(s: Int) = when { s >= 80 -> ok; s >= 55 -> warn; else -> danger }

    /**
     * [route]: the drive's outline, from its recording when the route was saved; drawn as a line on
     * the card's own background with a start dot and an end dot. Null or empty: no outline.
     * [roast]: a short line about the score, under the date. [hideRoast] keeps its space but leaves
     * it blank, for the preview before sharing: the card looks the same, the roast stays a surprise.
     */
    fun render(trip: TripSummary, card: ShareScore, carLabel: String?, format: Format, route: Route? = null, roast: String? = null, hideRoast: Boolean = false): Bitmap {
        val w = format.width; val h = format.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val story = format == Format.STORY
        val showRoute = route != null && !route.isEmpty
        val score = card.score

        background(c, w, h, score)

        // Title, date and car, and the roast.
        var y = if (story) 360f else 330f
        when (layout) {
            DashLayout.TELEMETRY -> { paint.typeface = uiBold; paint.textSize = if (story) 84f else 70f }
            DashLayout.COCKPIT -> { paint.typeface = uiLight; paint.textSize = if (story) 92f else 76f }
            DashLayout.CYBERPUNK -> { paint.typeface = display; paint.textSize = if (story) 86f else 70f; paint.letterSpacing = 0.08f }
            DashLayout.LIVERY -> { paint.typeface = title; paint.textSize = if (story) 96f else 78f }
        }
        paint.textAlign = Paint.Align.LEFT
        val heading = if (layout == DashLayout.CYBERPUNK) "MY DRIVE" else "My drive"
        if (layout == DashLayout.CYBERPUNK) {
            // Split-colour digits, like the Cyberpunk speed.
            paint.color = s1; c.drawText(heading, 72f - 4f, y, paint)
            paint.color = s2; c.drawText(heading, 72f + 4f, y, paint)
        }
        paint.color = text; c.drawText(heading, 72f, y, paint)
        paint.letterSpacing = 0f
        y += if (story) 60f else 52f
        paint.typeface = ui; paint.color = muted; paint.textSize = 36f
        val day = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(trip.startedAt))
        val dateLine = listOfNotNull(day, carLabel).joinToString("  ·  ")
        c.drawText(if (layout == DashLayout.CYBERPUNK) dateLine.uppercase(Locale.getDefault()) else dateLine, 72f, y, paint)
        if (!roast.isNullOrBlank()) {
            paint.typeface = uiBold; paint.color = if (layout == DashLayout.CYBERPUNK) s2 else s1; paint.textSize = if (story) 42f else 36f
            for (line in wrap("“$roast”", w - 144f).take(2)) { y += paint.textSize * 1.3f; if (!hideRoast) c.drawText(line, 72f, y, paint) }
        }

        // The score. With a route, the score and the outline share one row at the same height.
        val r = if (story && !showRoute) 190f else if (story) 170f else if (showRoute) 130f else 150f
        val cx = if (story && !showRoute) w / 2f else if (story) 270f else if (showRoute) 72f + r + 8f else 240f
        val cy = y + (if (story) 60f else 40f) + r
        scoreBlock(c, cx, cy, r, score, card.kind.label, story)

        // Stats.
        val stats = listOfNotNull(
            "Distance" to TripSummary.distance(Units.distance(trip.distanceKm)).replace(" km", " ${Units.distanceUnit}"),
            "Driving time" to TripSummary.duration(trip.drivingSeconds),
            trip.topSpeed?.let { "Top speed" to Units.speedText(it) },
            trip.averageSpeed?.let { "Average" to Units.speedText(it) },
            trip.best0to100?.let { "0–100 km/h" to String.format(Locale.US, "%.1f s", it) },
            "Idling" to TripSummary.duration(trip.idleSeconds),
        ).take(if (story) 6 else if (showRoute) 2 else 4)
        val gridLeft = if (story || showRoute) 72f else 470f
        val gridRight = w - 72f
        val gap = 24f
        val tileW = (gridRight - gridLeft - gap) / 2
        val tileH = if (story) 150f else 160f
        var gy = if (story) cy + r + 60f else if (showRoute) cy + r + 28f else y + 50f
        stats.chunked(2).forEachIndexed { rowIndex, row ->
            row.forEachIndexed { i, (label, value) ->
                tile(c, gridLeft + i * (tileW + gap), gy, tileW, tileH, label, value, rowIndex * 2 + i, story)
            }
            gy += tileH + gap
        }

        // The route: beside the score, exactly as tall as it.
        if (showRoute) drawRoute(c, route!!, cx + r + 48f, cy - r, w - 72f, cy + r)

        // What the drive score is made of: Everyday and Efficiency. The racing card shows Racing only.
        if (story && card.parts.isNotEmpty()) {
            gy += 16f
            panel(c, 72f, gy, w - 72f, gy + 190f)
            val colW = (w - 144f) / card.parts.size
            card.parts.forEachIndexed { i, (st, v) ->
                val sx = 72f + colW * i + colW / 2
                paint.textAlign = Paint.Align.CENTER; paint.typeface = numberFace(); paint.textSize = 72f
                paint.color = v?.let { scoreColour(it) } ?: muted
                c.drawText(v?.toString() ?: "—", sx, gy + 104f, paint)
                paint.typeface = ui; paint.color = muted; paint.textSize = 30f
                c.drawText(label(st.label), sx, gy + 152f, paint)
            }
        }

        footer(c, w, h)
        return bmp
    }

    // ---------- Each layout's look ----------

    private fun background(c: Canvas, w: Int, h: Int, score: Int?) {
        when (layout) {
            DashLayout.LIVERY -> {
                c.drawColor(bg)
                // Livery bands raked across the top.
                c.save()
                c.rotate(-7f, w / 2f, 120f)
                fill.color = s1; c.drawRect(-200f, 60f, w + 200f, 140f, fill)
                fill.color = s2; c.drawRect(-200f, 160f, w + 200f, 190f, fill)
                fill.color = s1; c.drawRect(-200f, 210f, w + 200f, 226f, fill)
                c.restore()
            }
            DashLayout.TELEMETRY -> {
                c.drawColor(bg)
                // Fifteen shift lights across the top, lit by the score: green, amber, red.
                val lit = ((score ?: 0) * 15 / 100.0).roundToInt()
                val n = 15; val gap = 10f; val lw = (w - 144f - gap * (n - 1)) / n
                for (i in 0 until n) {
                    val x = 72f + i * (lw + gap)
                    fill.color = if (i < lit) (if (i < 5) Color.parseColor("#34D17A") else if (i < 10) Color.parseColor("#FFB000") else Color.parseColor("#FF3B30")) else track
                    rect.set(x, 120f, x + lw, 160f); c.drawRoundRect(rect, 6f, 6f, fill)
                }
                // Engine-speed and road-speed channel colours under the lights.
                fill.color = Channels.RPM; c.drawRect(72f, 190f, w / 2f - 8f, 196f, fill)
                fill.color = Channels.SPEED; c.drawRect(w / 2f + 8f, 190f, w - 72f, 196f, fill)
            }
            DashLayout.COCKPIT -> {
                fill.color = Color.BLACK; fill.shader = RadialGradient(w / 2f, h * 0.15f, h * 0.75f, intArrayOf(Color.parseColor("#13203F"), Color.parseColor("#070A12"), bg), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
                fill.shader = null
            }
            DashLayout.CYBERPUNK -> {
                fill.color = Color.BLACK; fill.shader = RadialGradient(w / 2f, 0f, h * 0.7f, intArrayOf(Color.parseColor("#2A0A3A"), Color.parseColor("#0B0716"), bg), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
                fill.shader = null
                // Scanlines and a neon frame.
                fill.color = ColorUtils.setAlphaComponent(Color.WHITE, 10)
                var sy = 0f
                while (sy < h) { c.drawRect(0f, sy, w.toFloat(), sy + 2f, fill); sy += 6f }
                chamfer(36f, 36f, w - 36f, h - 36f, 40f)
                stroke.shader = null; stroke.strokeWidth = 4f; stroke.color = ColorUtils.setAlphaComponent(s2, 200)
                c.drawPath(path, stroke)
            }
        }
    }

    private fun scoreBlock(c: Canvas, cx: Float, cy: Float, r: Float, score: Int?, kind: String, story: Boolean) {
        val label = label("$kind score")
        when (layout) {
            DashLayout.TELEMETRY -> {
                // A data-logger readout: the big number with a red channel bar, "/100" beside it.
                fill.color = Channels.RPM; c.drawRect(cx - r, cy - r * 0.62f, cx - r + 10f, cy + r * 0.62f, fill)
                paint.textAlign = Paint.Align.LEFT; paint.typeface = uiBold; paint.color = muted; paint.textSize = if (story) 40f else 32f
                c.drawText(label, cx - r + 40f, cy - r * 0.38f, paint)
                val n = score?.toString() ?: "—"
                val outOf = "/100"
                val outSize = if (story) 48f else 40f
                // Sized so the number and "/100" stay inside the score's own space.
                paint.typeface = ui; paint.textSize = outSize; val outW = paint.measureText(outOf)
                paint.typeface = numberFace(); paint.textSize = if (story) 220f else 170f
                while (paint.measureText(n) + outW + 56f > 2 * r && paint.textSize > 60f) paint.textSize -= 4f
                val numW = paint.measureText(n)
                paint.color = score?.let { scoreColour(it) } ?: text
                c.drawText(n, cx - r + 36f, cy + r * 0.5f, paint)
                paint.typeface = ui; paint.color = muted; paint.textSize = outSize
                c.drawText(outOf, cx - r + 46f + numW, cy + r * 0.5f, paint)
            }
            DashLayout.COCKPIT -> {
                rect.set(cx - r, cy - r, cx + r, cy + r)
                stroke.shader = null; stroke.strokeWidth = 8f; stroke.color = track
                c.drawArc(rect, 135f, 270f, false, stroke)
                if (score != null) {
                    // A soft glow under a fine bright arc.
                    stroke.color = ColorUtils.setAlphaComponent(s2, 110); stroke.strokeWidth = 26f
                    stroke.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
                    c.drawArc(rect, 135f, 270f * score / 100f, false, stroke)
                    stroke.maskFilter = null
                    stroke.color = s2; stroke.strokeWidth = 10f
                    c.drawArc(rect, 135f, 270f * score / 100f, false, stroke)
                }
                paint.typeface = uiLight; paint.color = text; paint.textAlign = Paint.Align.CENTER; paint.textSize = if (story) 160f else 116f
                c.drawText(score?.toString() ?: "—", cx, cy + paint.textSize * 0.34f, paint)
                paint.typeface = ui; paint.color = muted; paint.textSize = if (story) 36f else 28f
                c.drawText(label, cx, cy + r * 0.78f, paint)
            }
            DashLayout.CYBERPUNK -> {
                // A ring of neon segments, cyan to magenta.
                rect.set(cx - r, cy - r, cx + r, cy + r)
                val segs = 24; val each = 270f / segs
                stroke.strokeCap = Paint.Cap.BUTT; stroke.strokeWidth = if (story) 30f else 24f
                for (i in 0 until segs) {
                    val on = score != null && (i + 0.5f) / segs <= score / 100f
                    stroke.color = if (on) ColorUtils.blendARGB(s2, s1, i / (segs - 1f)) else track
                    c.drawArc(rect, 135f + i * each, each - 3f, false, stroke)
                }
                stroke.strokeCap = Paint.Cap.ROUND
                paint.typeface = display; paint.textAlign = Paint.Align.CENTER; paint.textSize = if (story) 160f else 116f
                val n = score?.toString() ?: "—"; val by = cy + paint.textSize * 0.32f
                paint.color = s1; c.drawText(n, cx - 5f, by, paint)
                paint.color = s2; c.drawText(n, cx + 5f, by, paint)
                paint.color = text; c.drawText(n, cx, by, paint)
                paint.typeface = uiBold; paint.color = s1; paint.textSize = if (story) 32f else 26f; paint.letterSpacing = 0.2f
                c.drawText(label, cx, cy + r * 0.78f, paint); paint.letterSpacing = 0f
            }
            DashLayout.LIVERY -> {
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
                c.drawText(label, cx, cy + r * 0.78f, paint)
            }
        }
    }

    private fun tile(c: Canvas, x: Float, y: Float, tw: Float, th: Float, label: String, value: String, index: Int, story: Boolean) {
        panel(c, x, y, x + tw, y + th)
        when (layout) {
            DashLayout.LIVERY -> { fill.color = s1; c.drawRect(x, y + 24f, x + 8f, y + th - 24f, fill) }
            DashLayout.TELEMETRY -> { fill.color = if (index % 2 == 0) Channels.RPM else Channels.SPEED; c.drawRect(x, y, x + 6f, y + th, fill) }
            else -> Unit
        }
        paint.textAlign = Paint.Align.LEFT; paint.typeface = if (layout == DashLayout.CYBERPUNK) uiBold else ui
        paint.color = if (layout == DashLayout.CYBERPUNK) s1 else muted; paint.textSize = if (layout == DashLayout.CYBERPUNK) 26f else 32f
        if (layout == DashLayout.CYBERPUNK) paint.letterSpacing = 0.2f
        fitText(label(label), tw - 56f)
        c.drawText(label(label), x + 36f, y + 58f, paint)
        paint.letterSpacing = 0f
        paint.typeface = numberFace(); paint.color = text; paint.textSize = if (story) 72f else 62f
        fitText(value, tw - 60f)
        c.drawText(value, x + 36f, y + th - 34f, paint)
    }

    /** A panel in the layout's style: livery card, flat logger panel, cockpit glass or a chamfered neon chip. */
    private fun panel(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        fill.shader = null
        when (layout) {
            DashLayout.LIVERY -> { rect.set(left, top, right, bottom); fill.color = surface; c.drawRoundRect(rect, radius, radius, fill) }
            DashLayout.TELEMETRY -> { fill.color = surface; c.drawRect(left, top, right, bottom, fill) }
            DashLayout.COCKPIT -> {
                rect.set(left, top, right, bottom)
                fill.color = ColorUtils.setAlphaComponent(Color.WHITE, 16); c.drawRoundRect(rect, 32f, 32f, fill)
                stroke.shader = null; stroke.strokeWidth = 2f; stroke.color = ColorUtils.setAlphaComponent(Color.WHITE, 40)
                c.drawRoundRect(rect, 32f, 32f, stroke)
            }
            DashLayout.CYBERPUNK -> {
                chamfer(left, top, right, bottom, 26f)
                fill.color = surface; c.drawPath(path, fill)
                stroke.shader = null; stroke.strokeWidth = 3f; stroke.color = ColorUtils.setAlphaComponent(s2, 170)
                c.drawPath(path, stroke)
            }
        }
    }

    private fun footer(c: Canvas, w: Int, h: Int) {
        if (layout == DashLayout.LIVERY || layout == DashLayout.CYBERPUNK) {
            fill.color = Color.BLACK; fill.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, s1, s2, Shader.TileMode.CLAMP)
            c.drawRect(0f, h - 14f, w.toFloat(), h.toFloat(), fill)
            fill.shader = null
        }
        paint.textAlign = Paint.Align.LEFT; paint.color = ColorUtils.setAlphaComponent(text, 200); paint.textSize = 40f
        paint.typeface = when (layout) { DashLayout.LIVERY -> title; DashLayout.COCKPIT -> uiLight; else -> uiBold }
        c.drawText(if (layout == DashLayout.CYBERPUNK) "OBD2 READER" else "OBD2 Reader", 72f, h - 64f, paint)
    }

    private fun numberFace() = when (layout) { DashLayout.COCKPIT -> uiLight; DashLayout.TELEMETRY -> uiBold; else -> display }
    private fun label(s: String) = if (layout == DashLayout.CYBERPUNK) s.uppercase(Locale.getDefault()) else s

    private val path = Path()
    private fun chamfer(l: Float, t: Float, r: Float, b: Float, cut: Float) {
        path.reset()
        path.moveTo(l + cut, t); path.lineTo(r, t); path.lineTo(r, b - cut); path.lineTo(r - cut, b); path.lineTo(l, b); path.lineTo(l, t + cut); path.close()
    }

    /** Splits [s] into lines that fit [maxWidth] at the paint's current size. */
    private fun wrap(s: String, maxWidth: Float): List<String> {
        val out = mutableListOf<String>(); var line = ""
        for (word in s.split(" ")) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(next) <= maxWidth || line.isEmpty()) line = next else { out += line; line = word }
        }
        if (line.isNotEmpty()) out += line
        return out
    }

    /** [colour] at a brightness for a stretch driven at [share] (0 slowest to 1 fastest; null: full). */
    internal fun speedShade(colour: Int, share: Double?): Int {
        if (share == null) return colour
        val hsv = FloatArray(3); Color.colorToHSV(colour, hsv)
        val full = hsv[2].coerceAtLeast(0.55f)
        hsv[2] = (full * (0.5f + 0.5f * share.toFloat())).coerceAtLeast(0.35f)
        return Color.HSVToColor(Color.alpha(colour), hsv)
    }

    /** The route as a line on the card's own background, with a start dot and an end dot. */
    private fun drawRoute(c: Canvas, route: Route, left: Float, top: Float, right: Float, bottom: Float) {
        val pts = route.fit(right - left, bottom - top, 28f)
        if (pts.size < 2) return
        val colour = if (layout == DashLayout.COCKPIT || layout == DashLayout.CYBERPUNK) s2 else s1
        // Each stretch in the same colour, brighter the faster it was driven and dimmer the slower,
        // but never darker than half of the colour (so never near black).
        val shares = route.segmentSpeedShares()
        stroke.shader = null; stroke.strokeWidth = 10f; stroke.strokeJoin = Paint.Join.ROUND
        val cap = stroke.strokeCap
        stroke.strokeCap = Paint.Cap.ROUND
        for (i in 1 until pts.size) {
            stroke.color = speedShade(colour, shares.getOrNull(i - 1))
            c.drawLine(left + pts[i - 1].first, top + pts[i - 1].second, left + pts[i].first, top + pts[i].second, stroke)
        }
        stroke.strokeCap = cap
        val (sx, sy) = pts.first(); val (ex, ey) = pts.last()
        // Start: a ring. End: a solid dot with a ring.
        fill.shader = null
        fill.color = bg; c.drawCircle(left + sx, top + sy, 20f, fill)
        stroke.color = if (colour == s1) s2 else s1; stroke.strokeWidth = 8f; c.drawCircle(left + sx, top + sy, 16f, stroke)
        fill.color = colour; c.drawCircle(left + ex, top + ey, 20f, fill)
        stroke.color = text; stroke.strokeWidth = 5f; c.drawCircle(left + ex, top + ey, 24f, stroke)
    }

    /** Shrinks the paint's text size until [s] fits [maxWidth]. */
    private fun fitText(s: String, maxWidth: Float) {
        while (paint.measureText(s) > maxWidth && paint.textSize > 24f) paint.textSize -= 2f
    }
}
