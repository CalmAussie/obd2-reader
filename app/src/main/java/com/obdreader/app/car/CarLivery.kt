package com.obdreader.app.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import com.obdreader.app.R
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.ui.AppThemes

/**
 * The phone's style, carried into Android Auto. The car keeps Android Auto's own background,
 * fonts and layout (a safety rule for every app), so the style shows through the accent colour,
 * a rev-ring roundel image and the trouble-code plates. Accents are brightened where needed to
 * pass Android Auto's 4.5:1 contrast rule against black.
 */
object CarLivery {

    data class Colours(val accent: Int, val disc: Int)

    private val COLOURS = mapOf(
        "asphalt" to Colours(Color.parseColor("#FF5A1F"), Color.parseColor("#F4F4F1")),
        "endurance" to Colours(Color.parseColor("#FFD000"), Color.parseColor("#FFD000")),
        "kerb" to Colours(Color.parseColor("#FF4A4F"), Color.parseColor("#F5F5F3")),
        "pitlane" to Colours(Color.parseColor("#FF5A52"), Color.parseColor("#F1F2F4")),
        "drift" to Colours(Color.parseColor("#FF2E88"), Color.parseColor("#F6F4F7")),
        "apex" to Colours(Color.parseColor("#D7FF1F"), Color.parseColor("#D7FF1F")),
        "british" to Colours(Color.parseColor("#E3B84D"), Color.parseColor("#F3EBD3")),
        "cyberpunk" to Colours(Color.parseColor("#FF2BD6"), Color.parseColor("#00F0FF")),
        "telemetry" to Colours(Color.parseColor("#FF453A"), Color.parseColor("#E9EBEE")),
        "cockpit" to Colours(Color.parseColor("#7FA6FF"), Color.parseColor("#EEF2FA")),
    )
    private val TRACK = Color.parseColor("#3C4043")
    private val HIGH = Color.parseColor("#F28B82")
    private const val AMBER = 0xFFFFB000.toInt()
    private val TEXT = Color.parseColor("#E8EAED")
    private const val INK = 0xFF111214.toInt()

    fun colours(context: Context): Colours {
        val id = AppThemes.byId(AppPrefs.of(context).getString(AppPrefs.THEME, null)).id
        return COLOURS[id] ?: COLOURS.getValue("asphalt")
    }

    fun accent(context: Context): CarColor = colours(context).accent.let { CarColor.createCustom(it, it) }

    private fun displayFont(context: Context): android.graphics.Typeface = try {
        ResourcesCompat.getFont(context, R.font.archivo_expanded_black_italic)
    } catch (e: Exception) {
        null
    } ?: android.graphics.Typeface.DEFAULT_BOLD

    /** A small version of the phone's rev roundel: a segmented ring filled to [fraction], around the livery disc. */
    fun roundel(context: Context, fraction: Float): CarIcon = roundel(context, fraction, null)

    /** The rev roundel with the live engine speed on the disc, as on the phone. */
    fun roundel(context: Context, fraction: Float, number: String?): CarIcon {
        val c = colours(context)
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val s = size / 64f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7 * s; strokeCap = Paint.Cap.BUTT }
        val ring = RectF(4 * s, 4 * s, 60 * s, 60 * s)
        val n = 12
        val step = 270f / n
        for (i in 0 until n) {
            val lit = (i + 1f) / n <= fraction + 0.0001f
            paint.color = when {
                !lit -> TRACK
                i >= 10 -> HIGH
                else -> c.accent
            }
            canvas.drawArc(ring, 135f + i * step + 1.5f, step - 3f, false, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = c.disc
        canvas.drawCircle(32 * s, 32 * s, if (number == null) 18 * s else 22 * s, paint)
        if (number != null) {
            paint.color = INK
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = displayFont(context)
            paint.textSize = 15 * s
            // Shrink long numbers (8,000) to fit the disc.
            val w = paint.measureText(number)
            if (w > 38 * s) paint.textSize *= 38 * s / w
            val fm = paint.fontMetrics
            canvas.drawText(number, 32 * s, 32 * s - (fm.ascent + fm.descent) / 2f, paint)
        }
        return CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
    }

    /**
     * One live reading as a picture: the number big in the livery's display font, with a bar
     * underneath filled to [fraction]. Drawn as an image because Android Auto only lets a
     * screen's text change freely in some places, and a picture can update on every refresh.
     */
    fun readingTile(context: Context, value: String, fraction: Float?, high: Boolean): CarIcon {
        val c = colours(context)
        val w = 288
        val h = 192
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = if (high) HIGH else TEXT
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = displayFont(context)
        paint.textSize = 104f
        val tw = paint.measureText(value)
        if (tw > w - 24f) paint.textSize *= (w - 24f) / tw
        val fm = paint.fontMetrics
        canvas.drawText(value, w / 2f, 92f - (fm.ascent + fm.descent) / 2f, paint)
        val left = 28f
        val right = w - 28f
        val top = h - 26f
        paint.color = TRACK
        canvas.drawRoundRect(RectF(left, top, right, top + 12f), 6f, 6f, paint)
        if (fraction != null && fraction > 0.004f) {
            paint.color = if (high) HIGH else c.accent
            canvas.drawRoundRect(RectF(left, top, left + (right - left) * fraction, top + 12f), 6f, 6f, paint)
        }
        return CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
    }

    /** A trouble code on a plate, like the phone's Diagnostics tab: amber when stored, outlined when pending. */
    fun plate(context: Context, code: String, stored: Boolean): CarIcon {
        val w = 288
        val h = 108
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = RectF(4f, 4f, w - 4f, h - 4f)
        if (stored) {
            paint.color = AMBER
            canvas.drawRoundRect(r, 10f, 10f, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 7f
            paint.color = Color.parseColor("#9AA0A6")
            canvas.drawRoundRect(r, 10f, 10f, paint)
            paint.style = Paint.Style.FILL
        }
        paint.color = if (stored) 0xFF1A1100.toInt() else Color.parseColor("#E8EAED")
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 54f
        paint.typeface = try {
            ResourcesCompat.getFont(context, R.font.archivo_expanded_black_italic)
        } catch (e: Exception) {
            null
        } ?: android.graphics.Typeface.DEFAULT_BOLD
        val fm = paint.fontMetrics
        canvas.drawText(code, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, paint)
        return CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
    }
}
