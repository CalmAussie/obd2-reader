package com.obdreader.app.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.annotation.AttrRes
import com.google.android.material.shape.ShapeAppearanceModel
import com.obdreader.app.R

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

/** The theme's font for big numbers on dials and tiles. */
fun Context.numberTypeface(): Typeface {
    val tv = TypedValue()
    val family = if (theme.resolveAttribute(R.attr.obdNumberFont, tv, true)) tv.string?.toString() else null
    val bold = themeBool(R.attr.obdNumberBold)
    val italic = themeBool(R.attr.obdNumberItalic)
    val style = when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    return Typeface.create(family ?: "sans-serif-medium", style)
}

/** The theme's card shape (rounded or cut corners), for views built in code. */
fun Context.themeShape(): ShapeAppearanceModel {
    val tv = TypedValue()
    val style = if (theme.resolveAttribute(R.attr.obdShapeAppearance, tv, true)) tv.resourceId else 0
    return if (style != 0) ShapeAppearanceModel.builder(this, style, 0).build()
    else ShapeAppearanceModel.builder().setAllCornerSizes(themeDimen(R.attr.obdCardRadius)).build()
}
