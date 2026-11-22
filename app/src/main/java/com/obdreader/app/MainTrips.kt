package com.obdreader.app

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.obdreader.app.MainActivity.Companion.PREF_LAST_TRIP
import com.obdreader.app.MainActivity.Companion.PREF_SCORE_STYLE
import com.obdreader.app.obd.DrivingScore
import com.obdreader.app.obd.TripSummary
import com.obdreader.app.obd.Units
import com.obdreader.app.session.DriveScorer
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.SkewButton
import com.obdreader.app.ui.applyTitleFonts
import com.obdreader.app.ui.displayTypeface
import com.obdreader.app.ui.panelBackground
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.titleTypeface
import com.obdreader.app.ui.uiTypeface
import java.util.Locale

// Driving score, drive history and the trip summary.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

internal fun MainActivity.setupScore() {
    scoreCard = findViewById(R.id.scoreCard)
    scoreCard.background = panelBackground()
    DriveScorer.start(this)
    showBatteryCheck()
    scoreStyle = DrivingScore.Style.values().firstOrNull { it.name == prefs.getString(PREF_SCORE_STYLE, null) } ?: DrivingScore.Style.EVERYDAY
    val ids = mapOf(DrivingScore.Style.EFFICIENCY to R.id.styleEfficiency, DrivingScore.Style.EVERYDAY to R.id.styleEveryday, DrivingScore.Style.RACING to R.id.styleRacing)
    // Narrow padding and one line each, so "Efficiency" isn't cut short on small phones or with large text.
    ids.values.forEach { id ->
        findViewById<MaterialButton>(id).apply {
            maxLines = 1
            setPaddingRelative(dp(4f).toInt(), paddingTop, dp(4f).toInt(), paddingBottom)
        }
    }
    findViewById<MaterialButtonToggleGroup>(R.id.scoreStyle).apply {
        ids.values.forEach { id ->
            findViewById<MaterialButton>(id).apply {
                setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(themeColor(R.attr.obdOnAccent), themeColor(R.attr.obdTextPrimary))))
                backgroundTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(themeColor(R.attr.obdAccent), android.graphics.Color.TRANSPARENT))
                strokeColor = ColorStateList.valueOf(themeColor(R.attr.obdOutline))
            }
        }
        check(ids.getValue(scoreStyle))
        // Under the score card, three buttons across: Performance timers, Last drive summary
        // and Drive history. The last two show once there's been a drive; the row shares its
        // width between the ones showing.
        val content = scoreCard.parent as ViewGroup
        // The same buttons as Connect, so they take each style's own shape: angled on the
        // liveries, square tiles on telemetry, pills on the cockpit, cut corners on cyberpunk.
        fun quickButton(label: Int, onClick: () -> Unit) = SkewButton(this@setupScore).apply {
            text = getString(label)
            kind = SkewButton.Kind.OUTLINE
            setTextSizeSp(13f)
            setOnClickListener { onClick() }
        }
        val perfButton = quickButton(R.string.perf_title) { showPerformance() }.apply { id = R.id.perfButton }
        val lastButton = quickButton(R.string.last_trip) { savedTrip()?.let { showTripSummary(it, justEnded = false) } }.apply {
            visibility = if (savedTrip() != null) View.VISIBLE else View.GONE
        }
        val histButton = quickButton(R.string.history_title) { showHistory() }.apply {
            id = R.id.historyButton
            visibility = if (tripHistory().isNotEmpty()) View.VISIBLE else View.GONE
        }
        lastTripButton = lastButton
        historyButton = histButton
        val quickRow = LinearLayout(this@setupScore).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf(perfButton, lastButton, histButton).forEachIndexed { i, btn ->
                addView(btn, LinearLayout.LayoutParams(0, dp(52f).toInt(), 1f).apply { if (i > 0) marginStart = dp(8f).toInt() })
            }
        }
        content.addView(quickRow, content.indexOfChild(scoreCard) + 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10f).toInt() })
        addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            scoreStyle = ids.entries.first { it.value == id }.key
            prefs.edit().putString(PREF_SCORE_STYLE, scoreStyle.name).apply()
            renderScore()
        }
    }
    renderScore()
}
/** The chosen style's score: the number, what it means, each part with a bar, and the tip that would help most. */
internal fun MainActivity.renderScore() {
    if (!scoreCardReady) return
    val r = DriveScorer.results.value[scoreStyle]
    val connected = ObdSession.state.value.isConnected || ObdSession.state.value.phase == ObdSession.Phase.WAITING
    scoreCard.removeAllViews()
    val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
    head.addView(TextView(this).apply {
        text = r?.score?.toString() ?: getString(R.string.dash)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
        typeface = displayTypeface()
        includeFontPadding = false
        setTextColor(r?.score?.let { scoreColour(it) } ?: themeColor(R.attr.obdTextPrimary))
    })
    head.addView(TextView(this).apply {
        text = "/100"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(themeColor(R.attr.obdTextSecondary))
        setPadding(dp(6f).toInt(), 0, 0, dp(6f).toInt())
    })
    scoreCard.addView(head)
    scoreCard.addView(TextView(this).apply {
        text = when {
            r == null || (r.drivingSeconds == 0 && !connected) -> getString(R.string.score_not_connected)
            r.score == null -> getString(R.string.score_waiting)
            else -> scoreStyle.blurb
        }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(themeColor(R.attr.obdTextSecondary))
        setPadding(0, dp(4f).toInt(), 0, dp(6f).toInt())
    })
    val parts = r?.parts ?: DrivingScore().result(scoreStyle).parts
    for (p in parts) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8f).toInt(), 0, dp(3f).toInt()) }
        row.addView(TextView(this).apply {
            text = p.name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(themeColor(R.attr.obdTextPrimary))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = if (r?.score == null || p.score == null) getString(R.string.dash) else p.score.toString()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = uiTypeface(600)
            fontFeatureSettings = "tnum"
            setTextColor(themeColor(R.attr.obdTextPrimary))
        })
        scoreCard.addView(row)
        scoreCard.addView(com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
            max = 100
            progress = if (r?.score == null) 0 else p.score ?: 0
            trackCornerRadius = dp(2f).toInt()
            trackThickness = dp(4f).toInt()
            setIndicatorColor(p.score?.let { scoreColour(it) } ?: themeColor(R.attr.obdAccent))
            trackColor = themeColor(R.attr.obdTrack)
        })
    }
    val weakest = if (r?.score == null) null else r.parts.filter { it.score != null }.minByOrNull { it.score!! }?.takeIf { it.score!! < 90 }
    val extra = listOfNotNull(
        weakest?.let { "Tip: ${it.tip}" },
        r?.topSpeed?.let { "Top speed this drive: ${Units.speedText(it)}" },
        r?.best0to100?.takeIf { scoreStyle == DrivingScore.Style.RACING }?.let { String.format(Locale.US, "Best 0 to 100 km/h this drive: %.1f s", it) },
        if (scoreStyle == DrivingScore.Style.RACING) getString(R.string.score_racing_note) else null,
    )
    for (line in extra) {
        scoreCard.addView(TextView(this).apply {
            text = line
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(themeColor(R.attr.obdTextSecondary))
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(12f).toInt(), 0, 0)
        })
    }
}
internal fun MainActivity.savedTrip(): TripSummary? = TripSummary.decode(prefs.getString(PREF_LAST_TRIP, null))
/** Every drive kept on the phone, newest first. Before drive history, only the last drive was kept. */
internal fun MainActivity.tripHistory(): List<TripSummary> {
    val stored = prefs.getString(DriveScorer.PREF_TRIP_HISTORY, null)
    if (stored == null) savedTrip()?.let { last ->
        prefs.edit().putString(DriveScorer.PREF_TRIP_HISTORY, com.obdreader.app.obd.TripHistory.add(null, last)).apply()
    }
    return com.obdreader.app.obd.TripHistory.parse(prefs.getString(DriveScorer.PREF_TRIP_HISTORY, null))
}
/** Drive history: the drive score's trend, then every drive; tap one for its summary and card. */
internal fun MainActivity.showHistory() {
    val trips = tripHistory()
    val sheet = BottomSheetDialog(this)
    val pad = dp(20f).toInt()
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt()) }
    body.addView(TextView(this).apply {
        setText(R.string.history_title)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = titleTypeface(); setTextColor(themeColor(R.attr.obdTextPrimary))
    })
    val (km, secs) = com.obdreader.app.obd.TripHistory.totals(trips)
    body.addView(TextView(this).apply {
        text = "${trips.size} ${if (trips.size == 1) "drive" else "drives"} · ${Units.distanceText(km)} · ${TripSummary.duration(secs)} driving"
        setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, dp(4f).toInt(), 0, dp(12f).toInt())
    })
    val trend = com.obdreader.app.obd.TripHistory.scoreTrend(trips)
    if (trend.size >= 2) {
        body.addView(TextView(this).apply {
            text = getString(R.string.history_trend, trend.size)
            setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })
        body.addView(com.obdreader.app.ui.TrendView(this, trend, themeColor(R.attr.obdAccent), themeColor(R.attr.obdOutline), themeColor(R.attr.obdTextSecondary)).apply {
            contentDescription = "Drive score over your last ${trend.size} drives: from ${trend.first()} to ${trend.last()}"
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96f).toInt()).apply { bottomMargin = dp(12f).toInt() })
    }
    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
    val day = java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault())
    trips.forEach { trip ->
        val score = com.obdreader.app.obd.ShareScore.of(trip.scores, raceTelemetry = false).score
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = sheetPanel()
            setPadding(dp(16f).toInt(), dp(12f).toInt(), dp(16f).toInt(), dp(12f).toInt())
            addView(LinearLayout(this@showHistory).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@showHistory).apply {
                    text = "${day.format(java.util.Date(trip.startedAt))}, ${time.format(java.util.Date(trip.startedAt))}"
                    setTextColor(themeColor(R.attr.obdTextPrimary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                })
                addView(TextView(this@showHistory).apply {
                    text = "${Units.distanceText(trip.distanceKm)} · ${TripSummary.duration(trip.drivingSeconds)}" + (trip.topSpeed?.let { " · top ${Units.speedText(it)}" } ?: "")
                    setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@showHistory).apply {
                text = score?.toString() ?: "–"
                typeface = titleTypeface(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(themeColor(R.attr.obdAccentText))
                contentDescription = score?.let { "Drive score $it" } ?: "No score"
            })
            setOnClickListener { sheet.dismiss(); showTripSummary(trip, justEnded = false) }
            setOnLongClickListener {
                MaterialAlertDialogBuilder(this@showHistory)
                    .setTitle(R.string.history_delete_title)
                    .setMessage(R.string.history_delete_text)
                    .setPositiveButton(R.string.history_delete) { _, _ ->
                        prefs.edit().putString(DriveScorer.PREF_TRIP_HISTORY, com.obdreader.app.obd.TripHistory.remove(prefs.getString(DriveScorer.PREF_TRIP_HISTORY, null), trip.startedAt)).apply()
                        sheet.dismiss(); if (tripHistory().isNotEmpty()) showHistory() else historyButton?.visibility = View.GONE
                    }
                    .setNegativeButton(android.R.string.cancel, null).show()
                true
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8f).toInt() })
    }
    body.addView(TextView(this).apply {
        setText(R.string.history_hint)
        setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(0, dp(8f).toInt(), 0, 0)
    })
    sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
    sheet.show()
}
internal fun MainActivity.addTripMotion(body: LinearLayout, m: TripSummary.Motion) {
    body.addView(TextView(this).apply {
        setText(R.string.g_trip_title)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = uiTypeface(600)
        setTextColor(themeColor(R.attr.obdTextPrimary))
        setPadding(0, dp(14f).toInt(), 0, dp(6f).toInt())
    })
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = tripTile(); setPadding(0, dp(12f).toInt(), 0, dp(12f).toInt()) }
    listOf(R.string.g_trip_brake to m.peakBrake, R.string.g_trip_accel to m.peakAccel, R.string.g_trip_side to m.peakSide).forEach { (label, g) ->
        // Each line fills its cell's width, so the text itself is centred, not just the line.
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(context).apply {
                text = TripSummary.Motion.g(g); gravity = Gravity.CENTER_HORIZONTAL; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f); typeface = displayTypeface(); fontFeatureSettings = "tnum"
                shrinkToFit(24f, 18f)
                setTextColor(themeColor(R.attr.obdTextPrimary))
            })
            addView(TextView(context).apply { setText(label); gravity = Gravity.CENTER_HORIZONTAL; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    body.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    body.addView(TextView(this).apply {
        text = m.harshText() + ". " + getString(R.string.g_trip_note)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(themeColor(R.attr.obdTextSecondary))
        setPadding(0, dp(6f).toInt(), 0, 0)
    })
}
internal fun MainActivity.showTripSummary(trip: TripSummary, justEnded: Boolean) {
    val sheet = BottomSheetDialog(this)
    val pad = dp(20f).toInt()
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, dp(8f).toInt(), pad, dp(24f).toInt())
    }
    body.addView(TextView(this).apply {
        text = getString(if (justEnded) R.string.trip_title_ended else R.string.trip_title_last)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = titleTypeface()
        setTextColor(themeColor(R.attr.obdTextPrimary))
    })
    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
    val day = java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault())
    body.addView(TextView(this).apply {
        text = listOfNotNull(
            "${day.format(java.util.Date(trip.startedAt))}, ${time.format(java.util.Date(trip.startedAt))} to ${time.format(java.util.Date(trip.endedAt))}",
            savedCar()?.label,
        ).joinToString(" · ")
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(themeColor(R.attr.obdTextSecondary))
        setPadding(0, dp(2f).toInt(), 0, dp(14f).toInt())
    })
    val tiles = listOfNotNull(
        "Distance" to Units.distanceText(trip.distanceKm),
        "Driving time" to TripSummary.duration(trip.drivingSeconds),
        trip.topSpeed?.let { "Top speed" to Units.speedText(it) },
        trip.averageSpeed?.let { "Average speed" to Units.speedText(it) },
        "Idling" to TripSummary.duration(trip.idleSeconds),
        trip.best0to100?.let { "Best 0–100 km/h" to String.format(Locale.US, "%.1f s", it) },
    )
    tiles.chunked(2).forEach { pair ->
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pair.forEachIndexed { i, (label, value) ->
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = tripTile()
                setPadding(dp(14f).toInt(), dp(12f).toInt(), dp(14f).toInt(), dp(12f).toInt())
                addView(TextView(context).apply { text = label; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
                addView(TextView(context).apply {
                    text = value; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    typeface = displayTypeface(); fontFeatureSettings = "tnum"; setTextColor(themeColor(R.attr.obdTextPrimary))
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(8f).toInt() })
        }
        if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(8f).toInt() })
        body.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8f).toInt() })
    }
    // The driving score in each style.
    body.addView(TextView(this).apply {
        setText(R.string.driving_score)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = uiTypeface(600)
        setTextColor(themeColor(R.attr.obdTextPrimary))
        setPadding(0, dp(10f).toInt(), 0, dp(6f).toInt())
    })
    val scores = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = tripTile(); setPadding(0, dp(12f).toInt(), 0, dp(12f).toInt()) }
    DrivingScore.Style.values().forEach { style ->
        val s = trip.scores[style]
        scores.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(context).apply {
                text = s?.toString() ?: getString(R.string.dash); gravity = Gravity.CENTER_HORIZONTAL; setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f); typeface = displayTypeface(); fontFeatureSettings = "tnum"
                setTextColor(s?.let { scoreColour(it) } ?: themeColor(R.attr.obdTextSecondary))
            })
            addView(TextView(context).apply { text = style.label; gravity = Gravity.CENTER_HORIZONTAL; maxLines = 1; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(themeColor(R.attr.obdTextSecondary)) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    body.addView(scores, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    if (trip.scores.values.all { it == null }) body.addView(TextView(this).apply {
        setText(R.string.trip_too_short)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(themeColor(R.attr.obdTextSecondary))
        setPadding(0, dp(6f).toInt(), 0, 0)
    })
    trip.motion?.let { addTripMotion(body, it) }
    addRouteComparison(body, trip)
    body.addView(MaterialButton(this).apply {
        setText(R.string.done)
        setOnClickListener { sheet.dismiss() }
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f).toInt() })
    body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        setText(R.string.share_picture)
        setOnClickListener { shareDriveCard(trip) }
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f).toInt() })
    body.applyTitleFonts()
    sheet.setContentView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
    sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    sheet.behavior.skipCollapsed = true
    sheet.show()
}
