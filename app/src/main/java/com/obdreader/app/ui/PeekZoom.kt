package com.obdreader.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow

/**
 * Tap and hold a picture to see it big: it grows smoothly out of its place on screen to fill the
 * screen, and shrinks back into place when you let go. The same picture is scaled, not reloaded.
 */
object PeekZoom {

    private const val OPEN_MS = 260L
    private const val CLOSE_MS = 200L

    @SuppressLint("ClickableViewAccessibility")
    fun attach(thumb: ImageView, bitmap: () -> Bitmap?) {
        var peek: Peek? = null
        val detector = GestureDetector(thumb.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onLongPress(e: MotionEvent) {
                val bmp = bitmap() ?: return
                thumb.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                thumb.parent?.requestDisallowInterceptTouchEvent(true) // keep the scroll view from taking the finger
                peek = Peek(thumb, bmp).also { it.open() }
            }
        })
        thumb.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                peek?.close(); peek = null
                thumb.parent?.requestDisallowInterceptTouchEvent(false)
            }
            true
        }
        thumb.isLongClickable = true
    }

    private class Peek(private val thumb: ImageView, private val bmp: Bitmap) {
        private val shade = View(thumb.context).apply { setBackgroundColor(Color.argb(220, 0, 0, 0)); alpha = 0f }
        private val big = ImageView(thumb.context).apply {
            setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_XY; pivotX = 0f; pivotY = 0f; alpha = 0f
        }
        private val frame = FrameLayout(thumb.context).apply {
            addView(shade, FrameLayout.LayoutParams(-1, -1))
            addView(big, FrameLayout.LayoutParams(1, 1))
        }
        private val popup = PopupWindow(frame, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, false).apply {
            isClippingEnabled = false
            isTouchable = false // the finger stays with the thumbnail, so letting go closes it
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        private var dx = 0f; private var dy = 0f; private var s = 1f
        private var anim: AnimatorSet? = null
        private var opened = false
        private var closing = false

        fun open() {
            popup.showAtLocation(thumb.rootView, android.view.Gravity.NO_GRAVITY, 0, 0)
            frame.post { if (closing) { dismiss(); return@post }; begin() }
        }

        /** Once the full-screen layer is laid out: the big picture's place, and where it starts from. */
        private fun begin() {
            val origin = IntArray(2).also { frame.getLocationOnScreen(it) }
            val fw = frame.width.toFloat(); val fh = frame.height.toFloat()
            val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
            val margin = 16f * thumb.resources.displayMetrics.density
            val fit = minOf((fw - 2 * margin) / bw, (fh - 4 * margin) / bh)
            val w = bw * fit; val h = bh * fit
            val left = (fw - w) / 2; val top = (fh - h) / 2
            big.layoutParams = FrameLayout.LayoutParams(w.toInt(), h.toInt()).apply { leftMargin = left.toInt(); topMargin = top.toInt() }
            // Where the picture sits inside the thumbnail, in the layer's coordinates.
            val loc = IntArray(2).also { thumb.getLocationOnScreen(it) }
            val t = minOf(thumb.width / bw, thumb.height / bh)
            val tw = bw * t; val th = bh * t
            s = tw / w
            dx = loc[0] - origin[0] + (thumb.width - tw) / 2 - left.toInt()
            dy = loc[1] - origin[1] + (thumb.height - th) / 2 - top.toInt()
            big.scaleX = s; big.scaleY = s; big.translationX = dx; big.translationY = dy
            big.alpha = 1f
            thumb.alpha = 0f // the picture lifts out of its place
            opened = true
            animate(1f, 0f, 0f, 1f, OPEN_MS, null)
        }

        fun close() {
            if (closing) return
            closing = true
            if (!opened) return // open() hasn't laid out yet; it dismisses itself
            animate(s, dx, dy, 0f, CLOSE_MS) { dismiss() }
        }

        private fun dismiss() {
            thumb.alpha = 1f
            try { popup.dismiss() } catch (ignored: Exception) {}
        }

        private fun animate(scale: Float, tx: Float, ty: Float, shadeAlpha: Float, ms: Long, end: (() -> Unit)?) {
            anim?.cancel()
            anim = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(big, View.SCALE_X, scale),
                    ObjectAnimator.ofFloat(big, View.SCALE_Y, scale),
                    ObjectAnimator.ofFloat(big, View.TRANSLATION_X, tx),
                    ObjectAnimator.ofFloat(big, View.TRANSLATION_Y, ty),
                    ObjectAnimator.ofFloat(shade, View.ALPHA, shadeAlpha),
                )
                duration = ms
                interpolator = DecelerateInterpolator(1.6f)
                if (end != null) addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { end() }
                })
                start()
            }
        }
    }
}
