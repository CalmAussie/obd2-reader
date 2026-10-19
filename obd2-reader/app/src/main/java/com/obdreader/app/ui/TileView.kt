package com.obdreader.app.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.obdreader.app.R
import java.util.Locale

/** Compact live-data card: label, big number with unit, and a thin range bar. */
class TileView(
    context: Context,
    label: String,
    unit: String,
    private val rangeMin: Double,
    private val rangeMax: Double,
    private val warnAbove: Double?,
    private val decimals: Int,
) : MaterialCardView(context) {

    private val valueView: TextView
    private val bar: LinearProgressIndicator
    private val accent = context.themeColor(R.attr.obdAccent)
    private val danger = context.themeColor(R.attr.obdDanger)
    private val primaryText = context.themeColor(R.attr.obdTextPrimary)

    init {
        shapeAppearanceModel = context.themeShape()
        cardElevation = 0f
        setCardBackgroundColor(context.themeColor(R.attr.obdSurface))
        strokeColor = context.themeColor(R.attr.obdOutline)
        strokeWidth = context.themeDimen(R.attr.obdCardStroke).toInt()

        val pad = dp(16f).toInt()
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(14f).toInt(), pad, pad)
        }

        inner.addView(TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.03f
            setTextColor(context.themeColor(R.attr.obdTextSecondary))
            maxLines = 1
        })

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(4f).toInt(), 0, 0)
        }
        valueView = TextView(context).apply {
            text = "—"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = context.numberTypeface()
            fontFeatureSettings = "tnum"
            setTextColor(primaryText)
            includeFontPadding = false
        }
        row.addView(valueView)
        row.addView(TextView(context).apply {
            text = unit
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(context.themeColor(R.attr.obdTextSecondary))
            setPadding(dp(4f).toInt(), 0, 0, dp(2f).toInt())
        })
        inner.addView(row)

        bar = LinearProgressIndicator(context).apply {
            this.max = 1000
            isIndeterminate = false
            trackThickness = dp(4f).toInt()
            trackCornerRadius = dp(2f).toInt()
            setIndicatorColor(accent)
            trackColor = context.themeColor(R.attr.obdTrack)
        }
        inner.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12f).toInt()
        })

        addView(inner)
    }

    fun setValue(value: Double?) {
        alpha = 1f
        valueView.text = value?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: "—"
        val frac = value?.let { ((it - rangeMin) / (rangeMax - rangeMin)).coerceIn(0.0, 1.0) } ?: 0.0
        bar.setProgressCompat((frac * 1000).toInt(), true)
        val warn = value != null && warnAbove != null && value >= warnAbove
        bar.setIndicatorColor(if (warn) danger else accent)
        valueView.setTextColor(if (warn) danger else primaryText)
    }

    fun setUnsupported() {
        valueView.text = "n/a"
        bar.setProgressCompat(0, false)
        alpha = 0.45f
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
