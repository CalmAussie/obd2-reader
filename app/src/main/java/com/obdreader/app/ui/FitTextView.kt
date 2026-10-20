package com.obdreader.app.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView

/**
 * A one-line number that shrinks to fit its column instead of spilling into the next one.
 * It sizes itself for the widest value it will show ([sample], such as "8,888" rpm), not the
 * current one, so the digits don't change size as the reading changes.
 */
class FitTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AppCompatTextView(context, attrs) {
    private val fullSize = textSize
    var sample: String? = null
        set(value) { field = value; requestLayout() }

    init {
        maxLines = 1
        isSingleLine = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val avail = MeasureSpec.getSize(widthMeasureSpec) - totalPaddingLeft - totalPaddingRight
            val text = sample ?: text.toString()
            val p = paint
            val old = p.textSize
            p.textSize = fullSize
            val need = p.measureText(text)
            p.textSize = old
            val size = if (need > avail && avail > 0) fullSize * avail / need * 0.98f else fullSize
            if (kotlin.math.abs(size - textSize) > 0.5f) setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
