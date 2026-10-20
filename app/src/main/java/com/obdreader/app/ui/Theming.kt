package com.obdreader.app.ui

import android.content.Context
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.shape.ShapeAppearanceModel
import com.obdreader.app.R
import kotlin.math.tan

/** Reads the current app theme's design tokens (see res/values/attrs.xml). */

fun Context.themeColor(@AttrRes attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return tv.data
}

fun Context.themeDimen(@AttrRes attr: Int): Float {
    val tv = TypedValue()
    if (!theme.resolveAttribute(attr, tv, true)) return 0f
    return tv.getDimension(resources.displayMetrics)
}

fun Context.themeFloat(@AttrRes attr: Int, fallback: Float): Float {
    val tv = TypedValue()
    return if (theme.resolveAttribute(attr, tv, true) && tv.type == TypedValue.TYPE_FLOAT) tv.float else fallback
}

fun Context.themeInt(@AttrRes attr: Int, fallback: Int): Int {
    val tv = TypedValue()
    return if (theme.resolveAttribute(attr, tv, true)) tv.data else fallback
}

fun Context.themeBool(@AttrRes attr: Int): Boolean {
    val tv = TypedValue()
    return theme.resolveAttribute(attr, tv, true) && tv.data != 0
}

/** The dashboard this theme uses. */
enum class DashLayout { LIVERY, TELEMETRY, COCKPIT, CYBERPUNK }

fun Context.dashLayout(): DashLayout = when (themeInt(R.attr.obdLayout, 0)) {
    1 -> DashLayout.TELEMETRY
    2 -> DashLayout.COCKPIT
    3 -> DashLayout.CYBERPUNK
    else -> DashLayout.LIVERY
}

/** How far panels and buttons lean, in degrees. */
fun Context.skewDegrees(): Float = themeFloat(R.attr.obdSkew, 12f)

private fun Context.font(@FontRes res: Int, fallback: Typeface): Typeface =
    try {
        ResourcesCompat.getFont(this, res) ?: fallback
    } catch (e: Exception) {
        fallback
    }

/** The theme's font for big numbers and headings (wide black italic, condensed or light). */
fun Context.displayTypeface(): Typeface {
    val tv = TypedValue()
    val res = if (theme.resolveAttribute(R.attr.obdDisplayFont, tv, true)) tv.resourceId else 0
    return if (res != 0) font(res, Typeface.DEFAULT_BOLD) else font(R.font.archivo_expanded_black_italic, Typeface.DEFAULT_BOLD)
}

/** The theme's heading font (obdTitleFont). */
fun Context.titleTypeface(): Typeface {
    val tv = TypedValue()
    val res = if (theme.resolveAttribute(R.attr.obdTitleFont, tv, true)) tv.resourceId else 0
    return if (res != 0) font(res, Typeface.DEFAULT_BOLD) else displayTypeface()
}

/**
 * Gives every view tagged @string/tag_title_font the theme's heading font. Android doesn't
 * reliably resolve a font set through a theme attribute (?attr/obdTitleFont) in XML, which
 * left titles in the plain body font, so it's applied here as well.
 */
fun android.view.View.applyTitleFonts() {
    val tag = context.getString(R.string.tag_title_font)
    val face = context.titleTypeface()
    fun walk(v: android.view.View) {
        if (v is android.widget.TextView && v.tag == tag) v.typeface = face
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
    }
    walk(this)
}

/** Archivo at a normal width, for labels and body text. */
fun Context.uiTypeface(weight: Int = 400): Typeface = when {
    weight >= 600 -> font(R.font.archivo_semibold, Typeface.DEFAULT_BOLD)
    weight >= 500 -> font(R.font.archivo_medium, Typeface.DEFAULT)
    weight <= 300 -> font(R.font.archivo_light, Typeface.DEFAULT)
    else -> font(R.font.archivo_regular, Typeface.DEFAULT)
}

/** The theme's card shape, for Material views built in code. */
fun Context.themeShape(): ShapeAppearanceModel {
    val tv = TypedValue()
    val style = if (theme.resolveAttribute(R.attr.obdShapeAppearance, tv, true)) tv.resourceId else 0
    return if (style != 0) ShapeAppearanceModel.builder(this, style, 0).build()
    else ShapeAppearanceModel.builder().setAllCornerSizes(themeDimen(R.attr.obdCardRadius)).build()
}

/**
 * The outline of a panel: a parallelogram leaning right by [skewDeg] (like a livery decal),
 * or a rounded rectangle when the theme doesn't lean.
 */
fun Path.panel(r: RectF, skewDeg: Float, radius: Float) {
    reset()
    if (skewDeg == 0f) {
        addRoundRect(r, radius, radius, Path.Direction.CW)
        return
    }
    val o = tan(Math.toRadians(skewDeg.toDouble())).toFloat() * r.height()
    moveTo(r.left + o, r.top)
    lineTo(r.right, r.top)
    lineTo(r.right - o, r.bottom)
    lineTo(r.left, r.bottom)
    close()
}

/** Horizontal lean of a panel's top edge relative to its bottom edge, in pixels. */
fun skewOffset(skewDeg: Float, height: Float): Float = tan(Math.toRadians(skewDeg.toDouble())).toFloat() * height
