package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View

/**
 * The strip behind the status bar. The pages scroll edge to edge, so without it their text slid
 * under the clock and icons. It shows the page's own backdrop (the plain page colour, or the
 * Digital cockpit and Cyberpunk glow), so at rest it can't be seen and scrolled text goes behind it.
 *
 * The backdrop is the strip's own background, not drawn in onDraw: the first version drew the
 * root's background in onDraw and the 2.5.10 tour showed it painted nothing, so this uses the
 * plain background path every view uses.
 */
class StatusBarScrim @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    /** The view whose background is the backdrop: the activity's root, whose top-left corner this strip shares.
     *  Set it after the style has given the root its background. */
    var backdropOf: View? = null
        set(value) {
            field = value
            background = when (val src = value?.background) {
                null -> null
                is ColorDrawable -> ColorDrawable(src.color)
                else -> BackdropSlice(src.constantState?.newDrawable(resources)?.mutate() ?: src, value)
            }
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}

/** Draws [src] at [root]'s full size, so the strip shows exactly the part of a gradient behind it. */
private class BackdropSlice(private val src: Drawable, private val root: View) : Drawable() {
    override fun draw(canvas: Canvas) {
        if (root.width == 0 || root.height == 0) return
        src.setBounds(0, 0, root.width, root.height)
        src.draw(canvas)
    }
    override fun setAlpha(alpha: Int) { src.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { src.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
