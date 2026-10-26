package com.obdreader.app.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.obdreader.app.obd.Route

/**
 * [colour] at a brightness for a stretch of route driven at [share] of the drive's speed range
 * (0 slowest to 1 fastest; null keeps the full colour). Never below half brightness, so the
 * slowest stretches are a dimmer shade of the same colour and never near black.
 */
fun speedShade(colour: Int, share: Double?): Int {
    if (share == null) return colour
    val hsv = FloatArray(3); Color.colorToHSV(colour, hsv)
    val full = hsv[2].coerceAtLeast(0.55f)
    hsv[2] = (full * (0.5f + 0.5f * share.toFloat())).coerceAtLeast(0.35f)
    return Color.HSVToColor(Color.alpha(colour), hsv)
}

/** Draws [route] fitted into the box, each stretch shaded by speed (drive card and video overlay). */
fun drawShadedRoute(c: Canvas, route: Route, left: Float, top: Float, right: Float, bottom: Float, pad: Float, colour: Int, width: Float, paint: Paint) {
    val pts = route.fit(right - left, bottom - top, pad)
    if (pts.size < 2) return
    val shares = route.segmentSpeedShares()
    paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = width
    paint.strokeJoin = Paint.Join.ROUND
    val cap = paint.strokeCap
    paint.strokeCap = Paint.Cap.ROUND
    for (i in 1 until pts.size) {
        paint.color = speedShade(colour, shares.getOrNull(i - 1))
        c.drawLine(left + pts[i - 1].first, top + pts[i - 1].second, left + pts[i].first, top + pts[i].second, paint)
    }
    paint.strokeCap = cap
}
