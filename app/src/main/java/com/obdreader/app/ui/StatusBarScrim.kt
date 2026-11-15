package com.obdreader.app.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * The strip behind the status bar. The pages scroll edge to edge, so without it their text slid
 * under the clock and icons. It paints the page's own backdrop (the plain page colour, or the
 * Digital cockpit and Cyberpunk glow), so at rest it can't be seen and scrolled text goes behind it.
 */
class StatusBarScrim @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    /** The view whose background is the backdrop: the activity's root, whose top-left corner this strip shares. */
    var backdropOf: View? = null
        set(value) { field = value; invalidate() }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        // The root's background is sized to the root, so drawing it here, clipped to this strip,
        // gives exactly the pixels that sit behind the status bar.
        backdropOf?.background?.draw(canvas)
    }
}
