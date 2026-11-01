package com.obdreader.app.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import com.obdreader.app.R
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.ui.AppThemes
import com.obdreader.app.ui.CockpitRingView
import com.obdreader.app.ui.CyberChipView
import com.obdreader.app.ui.CyberHudView
import com.obdreader.app.ui.DashLayout
import com.obdreader.app.ui.DecalView
import com.obdreader.app.ui.PlateView
import com.obdreader.app.ui.RoundelView
import com.obdreader.app.ui.ShiftLightsView
import com.obdreader.app.ui.TowerRowView
import com.obdreader.app.ui.dashLayout
import com.obdreader.app.ui.themeInt

/**
 * The Android Auto tiles, drawn with the phone's own dashboard pieces in the style you've chosen,
 * so the car screen looks like the app's layout:
 * - Liveries: the race-number roundel for engine speed and angled decals with the leading stripe.
 * - Race telemetry: shift lights over the engine speed, and data-logger rows with a 30-second trace.
 * - Digital cockpit: the glowing speed ring, and soft glass rows.
 * - Cyberpunk: the neon heads-up display for speed, and glowing chips.
 *
 * Android Auto keeps its own background, fonts and grid (a safety rule for every app), so each tile
 * is a picture of the phone's piece. The tile's name stays in Android Auto's own text under it.
 * A light style (Pit lane) is drawn in its dark sibling while the car's screen is in night mode.
 */
class CarTiles(private val car: Context, private val isNight: () -> Boolean) {

    constructor(car: CarContext) : this(car, { car.isDarkMode })

    /** A style to draw in instead of the chosen one (for the install test's pictures of every style). */
    var themeOverride: String? = null

    /** One reading, as handed to [image]: already in the chosen units. */
    class Reading(
        val key: String, val unit: String, val decimals: Int, val min: Float, val max: Float, val warnAt: Float?,
        val value: Float?, val unsupported: Boolean,
    )

    private var themeId: String? = null
    private var night: Boolean? = null
    private lateinit var ctx: Context
    private val views = mutableMapOf<String, View>()

    /** The themed context for the chosen style; rebuilt (with fresh views) when the style or day/night changes. */
    private fun context(): Context {
        val id = AppThemes.byId(themeOverride ?: AppPrefs.of(car).getString(AppPrefs.THEME, null)).id
        val dark = isNight()
        if (id != themeId || dark != night || !::ctx.isInitialized) {
            themeId = id; night = dark
            var theme = AppThemes.byId(id)
            val probe = ContextThemeWrapper(car, theme.style)
            if (probe.themeInt(R.attr.obdIsLight, 0) != 0 && dark) theme = AppThemes.byId("kerb")
            ctx = ContextThemeWrapper(car, theme.style)
            views.clear()
        }
        return ctx
    }

    val layout: DashLayout get() = context().dashLayout()

    private fun dp(v: Float) = v * context().resources.displayMetrics.density

    /** The picture for one tile. [speed] and [rpm] feed the combined pieces (the cockpit ring and the HUD). */
    fun image(r: Reading, speed: Float?, rpm: Float?, rpmMax: Float, redline: Float, speedMax: Float, speedUnit: String, top: Float?, checkEngine: Boolean): CarIcon {
        val c = context()
        val isRpm = r.key == HEX_RPM
        val isSpeed = r.key == HEX_SPEED
        return when (c.dashLayout()) {
            DashLayout.LIVERY -> when {
                isRpm -> {
                    val v = views.getOrPut(r.key) { RoundelView(c) } as RoundelView
                    v.max = rpmMax; v.warnAt = redline; v.checkEngine = checkEngine
                    v.setValue(r.value)
                    draw(v, dp(200f))
                }
                else -> {
                    val v = views.getOrPut(r.key) {
                        DecalView(c, "", r.unit, r.min.toDouble(), r.max.toDouble(), r.warnAt?.toDouble(), r.decimals, wide = isSpeed)
                    } as DecalView
                    if (isSpeed) v.top = top?.toDouble()
                    if (r.unsupported) v.setUnsupported() else v.setValue(r.value?.toDouble())
                    draw(v, dp(if (isSpeed) 190f else 170f))
                }
            }
            DashLayout.TELEMETRY -> {
                val row = tower(c, r)
                if (isRpm) {
                    val lights = views.getOrPut("lights") { ShiftLightsView(c) } as ShiftLightsView
                    lights.setRpm(rpm, redline)
                    drawStack(listOf(lights, row), dp(190f), dp(6f))
                } else draw(row, dp(190f))
            }
            DashLayout.COCKPIT -> if (isSpeed) {
                val v = views.getOrPut(r.key) { CockpitRingView(c) } as CockpitRingView
                v.rpmMax = rpmMax; v.warnAt = redline; v.speedMax = speedMax; v.speedUnit = speedUnit; v.top = top
                v.set(speed, rpm)
                draw(v, dp(220f))
            } else draw(tower(c, r), dp(190f))
            DashLayout.CYBERPUNK -> if (isSpeed) {
                val v = views.getOrPut(r.key) { CyberHudView(c) } as CyberHudView
                v.rpmMax = rpmMax; v.warnAt = redline; v.speedUnit = speedUnit; v.top = top
                v.set(speed, rpm); v.push(rpm)
                draw(v, dp(260f))
            } else {
                val v = views.getOrPut(r.key) { CyberChipView(c, "", r.unit, r.decimals, r.min, r.max, r.warnAt) } as CyberChipView
                v.labelOverride = ""
                if (r.unsupported) v.setUnsupported() else v.setValue(r.value?.toDouble())
                draw(v, dp(170f))
            }
        }
    }

    /** A trouble code on the phone's plate for the chosen style. */
    fun plate(code: String, stored: Boolean): CarIcon {
        val v = PlateView(context(), code, stored)
        return draw(v, dp(150f))
    }

    private fun tower(c: Context, r: Reading): TowerRowView {
        val v = views.getOrPut(r.key) {
            TowerRowView(c, "", r.unit, r.min.toDouble(), r.max.toDouble(), r.warnAt?.toDouble(), r.decimals,
                com.obdreader.app.ui.Channels.forKey(r.key, c.getColor(android.R.color.white)))
        } as TowerRowView
        if (r.unsupported) v.setUnsupported() else v.setValue(r.value?.toDouble())
        return v
    }

    private fun measure(v: View, width: Float) {
        val w = width.toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight.coerceAtLeast(1))
    }

    private fun draw(v: View, width: Float): CarIcon {
        measure(v, width)
        val bmp = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        return CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
    }

    private fun drawStack(parts: List<View>, width: Float, gap: Float): CarIcon {
        parts.forEach { measure(it, width) }
        val h = parts.sumOf { it.height } + (gap * (parts.size - 1)).toInt()
        val bmp = Bitmap.createBitmap(width.toInt(), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        var y = 0f
        parts.forEach { canvas.save(); canvas.translate(0f, y); it.draw(canvas); canvas.restore(); y += it.height + gap }
        return CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
    }

    companion object {
        /**
         * Debug builds only: draws a set of tiles (as the car would show them mid-drive) for every style
         * into files/car-tiles, so the install test can check what the Android Auto screen looks like.
         */
        fun renderSamples(context: Context): List<java.io.File> {
            val dir = java.io.File(context.filesDir, "car-tiles").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val out = mutableListOf<java.io.File>()
            for (theme in AppThemes.ALL) for (night in listOf(true, false)) {
                if (!night && theme.id != "pitlane") continue
                val tiles = CarTiles(context) { night }.apply { themeOverride = theme.id }
                val readings = listOf(
                    Reading(HEX_RPM, "rpm", 0, 0f, 7000f, 6500f, 3240f, false),
                    Reading(HEX_SPEED, "km/h", 0, 0f, 240f, null, 87f, false),
                    Reading("05", "°C", 0, -40f, 130f, 110f, 91f, false),
                    Reading("11", "%", 0, 0f, 100f, null, 22f, false),
                    Reading("2F", "%", 0, 0f, 100f, null, 64f, false),
                    Reading("VOLT", "V", 1, 11f, 15f, null, 14.1f, false),
                )
                // Push a few seconds of readings so the traces have something to show.
                var icons = emptyList<CarIcon>()
                repeat(12) { step ->
                    icons = readings.map { r ->
                        val v = r.value?.let { it * (0.85f + 0.15f * kotlin.math.sin(step / 2f)) }
                        tiles.image(Reading(r.key, r.unit, r.decimals, r.min, r.max, r.warnAt, v, r.unsupported), 87f, 3240f, 7000f, 6500f, 240f, "km/h", 112f, true)
                    }
                    Thread.sleep(330)
                }
                // One sheet per style, laid out like the car's grid: three across, two down.
                val bmps = icons.mapNotNull { (it.icon?.let { ic -> drawableBitmap(context, ic) }) }
                if (bmps.isEmpty()) continue
                val cell = 420; val gap = 24
                val sheet = Bitmap.createBitmap(cell * 3 + gap * 4, cell * 2 / 3 * 2 + gap * 3 + 80, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(sheet)
                canvas.drawColor(if (night) 0xFF000000.toInt() else 0xFF202124.toInt())
                bmps.forEachIndexed { i, b ->
                    val col = i % 3; val row = i / 3
                    val boxW = cell.toFloat(); val boxH = cell * 2 / 3f
                    val scale = minOf(boxW / b.width, boxH / b.height)
                    val w = b.width * scale; val h = b.height * scale
                    val x = gap + col * (cell + gap) + (boxW - w) / 2; val y = gap + row * (boxH + gap + 40) + (boxH - h) / 2
                    canvas.drawBitmap(b, null, android.graphics.RectF(x, y, x + w, y + h), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                }
                val f = java.io.File(dir, "${theme.id}${if (night) "" else "-day"}.png")
                f.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
                out += f
            }
            return out
        }

        private fun drawableBitmap(context: Context, icon: IconCompat): Bitmap? {
            val d = icon.loadDrawable(context) ?: return null
            if (d is android.graphics.drawable.BitmapDrawable) return d.bitmap
            val b = Bitmap.createBitmap(d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, b.width, b.height); d.draw(Canvas(b)); return b
        }

        private val HEX_RPM = String.format(java.util.Locale.US, "%02X", com.obdreader.app.obd.Pids.RPM)
        private val HEX_SPEED = String.format(java.util.Locale.US, "%02X", com.obdreader.app.obd.Pids.SPEED)
    }
}
