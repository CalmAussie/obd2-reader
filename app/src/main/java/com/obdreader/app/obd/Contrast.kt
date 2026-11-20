package com.obdreader.app.obd

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG contrast between two opaque colours (0xAARRGGBB ints), and a way to darken a colour until it
 * stands out enough on light backgrounds. Plain maths, no Android classes, so it can be unit tested.
 */
object Contrast {
    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** Relative luminance, 0 (black) to 1 (white). */
    fun luminance(colour: Int): Double =
        0.2126 * channel(colour shr 16 and 0xFF) + 0.7152 * channel(colour shr 8 and 0xFF) + 0.0722 * channel(colour and 0xFF)

    /** 1:1 (same colour) up to 21:1 (black on white). */
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /**
     * [colour], darkened toward black in small steps (keeping its hue) until it reaches [minRatio]
     * against every one of [backgrounds]. Returned unchanged when it already does.
     */
    fun darkenTo(colour: Int, backgrounds: IntArray, minRatio: Double = 3.0): Int {
        var c = colour
        var k = 1.0
        while (backgrounds.any { ratio(c, it) < minRatio } && k > 0.0) {
            k -= 0.02
            val r = ((colour shr 16 and 0xFF) * k).toInt()
            val g = ((colour shr 8 and 0xFF) * k).toInt()
            val b = ((colour and 0xFF) * k).toInt()
            c = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return c
    }
}
