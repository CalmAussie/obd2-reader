package com.obdreader.app

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.obdreader.app.MainActivity.Companion.COMPARE_MAX_BYTES
import com.obdreader.app.obd.TripSummary
import com.obdreader.app.session.Exports
import com.obdreader.app.session.LogRecorder
import com.obdreader.app.ui.DriveCardRenderer
import com.obdreader.app.ui.dashLayout
import com.obdreader.app.ui.themeColor
import com.obdreader.app.ui.themeDimen
import com.obdreader.app.ui.uiTypeface
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The drive card and same-route comparison.
// Split out of MainActivity to keep it a readable size. These are MainActivity's own functions,
// unchanged apart from being written as extensions, so they run on the activity exactly as before.

/**
 * Says "Reading the drive…" if reading takes more than a moment (a long drive), so the tap
 * doesn't seem to have done nothing. Call what it returns once the reading is done.
 */
internal fun MainActivity.readingNotice(): () -> Unit {
    var shown: Toast? = null
    val job = lifecycleScope.launch {
        kotlinx.coroutines.delay(300)
        shown = Toast.makeText(this@readingNotice, "Reading the drive…", Toast.LENGTH_SHORT).also { it.show() }
    }
    return { job.cancel(); shown?.cancel() }
}
/**
 * A picture of the drive in this style, for Shorts, TikTok, Reels or an Instagram post. The
 * route is read from the drive's recording in the background first: a long drive's file can be
 * several megabytes, too slow to read while the screen waits.
 */
internal fun MainActivity.shareDriveCard(trip: TripSummary) {
    if (readingDrive) return
    readingDrive = true
    lifecycleScope.launch {
        val done = readingNotice()
        val route = try { withContext(Dispatchers.IO) { routeFor(trip) } } finally { readingDrive = false; done() }
        showDriveCard(trip, route)
    }
}
internal fun MainActivity.showDriveCard(trip: TripSummary, route: com.obdreader.app.obd.Route?) {
    val formats = DriveCardRenderer.Format.values()
    val card = shareScore(trip)
    val roast = com.obdreader.app.obd.Roast.line(card, trip.startedAt)
    android.util.Log.i("OBD2Share", "drive card: route of ${route?.points?.size ?: 0} points")
    var format = DriveCardRenderer.Format.STORY
    var preview: android.graphics.Bitmap? = null
    val pad = dp(20f).toInt()
    val image = android.widget.ImageView(this).apply {
        adjustViewBounds = true
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        maxHeight = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        contentDescription = getString(R.string.share_preview_title)
    }
    // Tap and hold to see the card big.
    com.obdreader.app.ui.PeekZoom.attach(image) { preview }
    val use = TextView(this).apply {
        setTextColor(themeColor(R.attr.obdTextSecondary)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER; setPadding(0, dp(6f).toInt(), 0, dp(10f).toInt())
    }
    // The card as it'll look, drawn again whenever the shape changes. The roast's space stays blank.
    fun show(f: DriveCardRenderer.Format) {
        format = f
        image.setImageDrawable(null); preview?.recycle()
        preview = DriveCardRenderer(this).render(trip, card, savedCar()?.label, f, route, roast, hideRoast = true)
        image.setImageBitmap(preview)
        use.text = f.label.substringAfter(") ").replaceFirstChar { it.uppercase() }
    }
    val group = com.google.android.material.button.MaterialButtonToggleGroup(this).apply {
        isSingleSelection = true; isSelectionRequired = true
    }
    val ids = formats.associateWith { View.generateViewId() }
    formats.forEach { f ->
        group.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = ids.getValue(f); text = f.label.substringBefore(" for"); isAllCaps = false
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    group.check(ids.getValue(format))
    group.addOnButtonCheckedListener { _, id, checked -> if (checked) formats.firstOrNull { ids[it] == id }?.let { show(it) } }
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, dp(8f).toInt(), pad, 0)
        addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(use, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    show(format)
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.share_picture)
        .setView(androidx.core.widget.NestedScrollView(this).apply { addView(body) })
        .setPositiveButton(R.string.share_preview_share) { _, _ -> sendDriveCard(trip, card, format, route, roast) }
        .setNegativeButton("Cancel", null)
        .setOnDismissListener { image.setImageDrawable(null); preview?.recycle(); preview = null }
        .show()
}
/** Race telemetry shares the Racing score; every other layout the average of Everyday and Efficiency. */
internal fun MainActivity.shareScore(trip: TripSummary) = com.obdreader.app.obd.ShareScore.of(trip.scores, dashLayout() == com.obdreader.app.ui.DashLayout.TELEMETRY)
internal fun MainActivity.sendDriveCard(trip: TripSummary, card: com.obdreader.app.obd.ShareScore, format: DriveCardRenderer.Format, route: com.obdreader.app.obd.Route?, roast: String) {
    val bmp = DriveCardRenderer(this).render(trip, card, savedCar()?.label, format, route, roast)
    val name = "OBD2Reader_drive_" + java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(java.util.Date(trip.startedAt)) + "_${format.name.lowercase(Locale.US)}.png"
    val file = Exports.exportFile(this, name)
    file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    Exports.saveToPictures(this, file)
    // The post's text: just the score and the tag. The line on the card stays on the card.
    val score = card.score
    val text = listOfNotNull(score?.let { "My drive: $it/100 ${card.kind.label.lowercase(Locale.getDefault())} score." }, "#OBD2Reader").joinToString(" ")
    Exports.share(this, file, "image/png", "My drive", text = text)
}
/**
 * Same route as before? When [trip] was recorded with its route and an earlier recorded drive
 * followed the same roads the same way round, the summary shows how this one compared: time,
 * average speed, idling, top speed and score. Both routes are read from their own recordings
 * (the only place location is kept) and nothing new is saved.
 */
internal fun MainActivity.addRouteComparison(body: LinearLayout, trip: TripSummary) {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
    body.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    lifecycleScope.launch {
        val result = withContext(Dispatchers.IO) {
            try {
                val mineFiles = tripFiles(trip)
                if (mineFiles.isEmpty()) return@withContext null
                val mineLines = mineFiles.map { it.readLines() }
                val mine = com.obdreader.app.obd.Route.join(mineLines.map { com.obdreader.app.obd.Route.parse(it) })
                if (mine.isEmpty) return@withContext null
                val mineStats = com.obdreader.app.obd.RouteCompare.Stats.join(mineLines.mapNotNull { com.obdreader.app.obd.RouteCompare.Stats.parse(it) })
                val history = tripHistory()
                LogRecorder.saved(this@addRouteComparison).asSequence()
                    // A very long recording (hours) isn't read just to compare routes.
                    .filter { it !in mineFiles && (fileStart(it) ?: Long.MAX_VALUE) < trip.startedAt && it.length() <= COMPARE_MAX_BYTES }
                    .take(60)
                    .mapNotNull { f ->
                        val lines = f.readLines()
                        val route = com.obdreader.app.obd.Route.parse(lines)
                        if (!com.obdreader.app.obd.RouteCompare.sameRoute(mine, route)) return@mapNotNull null
                        val stats = com.obdreader.app.obd.RouteCompare.Stats.parse(lines) ?: return@mapNotNull null
                        val start = fileStart(f) ?: f.lastModified()
                        // The scores belong to whole drives: only compared when each recording covered its drive.
                        fun score(t: TripSummary?, st: com.obdreader.app.obd.RouteCompare.Stats) =
                            t?.takeIf { st.seconds >= it.connectedSeconds * 0.8 }?.let { com.obdreader.app.obd.ShareScore.of(it.scores, raceTelemetry = false).score }
                        val before = history.firstOrNull { start in (it.startedAt - 60_000)..it.endedAt }
                        start to com.obdreader.app.obd.RouteCompare.compare(mineStats, stats, score(trip, mineStats), score(before, stats))
                    }
                    .firstOrNull()
            } catch (e: Exception) { android.util.Log.w("OBD2Route", "compare: ${e.message}"); null
            } catch (e: OutOfMemoryError) { android.util.Log.w("OBD2Route", "compare: too big to read"); null }
        } ?: return@launch
        val (foundAt, lines) = result
        android.util.Log.i("OBD2Route", "same route as the drive of $foundAt: ${lines.joinToString { it.label + " " + it.change }}")
        val day = java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault())
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        box.addView(TextView(this@addRouteComparison).apply {
            setText(R.string.same_route_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = uiTypeface(600); setTextColor(themeColor(R.attr.obdTextPrimary))
            setPadding(0, dp(16f).toInt(), 0, dp(2f).toInt())
        })
        box.addView(TextView(this@addRouteComparison).apply {
            text = getString(R.string.same_route_since, "${day.format(java.util.Date(foundAt))}, ${time.format(java.util.Date(foundAt))}")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary))
            setPadding(0, 0, 0, dp(8f).toInt())
        })
        val table = LinearLayout(this@addRouteComparison).apply {
            orientation = LinearLayout.VERTICAL; background = tripTile()
            setPadding(dp(14f).toInt(), dp(6f).toInt(), dp(14f).toInt(), dp(6f).toInt())
        }
        lines.forEach { line ->
            table.addView(LinearLayout(this@addRouteComparison).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(7f).toInt(), 0, dp(7f).toInt())
                addView(TextView(context).apply { text = line.label; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(themeColor(R.attr.obdTextSecondary)) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = line.now; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = uiTypeface(600); fontFeatureSettings = "tnum"
                    setTextColor(themeColor(R.attr.obdTextPrimary)); setPadding(dp(8f).toInt(), 0, dp(12f).toInt(), 0)
                })
                addView(TextView(context).apply {
                    text = line.change; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); fontFeatureSettings = "tnum"; gravity = Gravity.END
                    setTextColor(themeColor(when (line.better) { true -> R.attr.obdOkText; false -> R.attr.obdDangerText; null -> R.attr.obdTextSecondary }))
                }, LinearLayout.LayoutParams(dp(118f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
            })
        }
        box.addView(table, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.visibility = View.VISIBLE
    }
}
/**
 * The route of [trip], from the recording(s) made during it with the route saved. Location
 * lives only in those files, so with no recording (or one that can't be read, too big for this
 * phone or deleted meanwhile) there's simply no outline. Reads files: call it off the main thread.
 */
internal fun MainActivity.routeFor(trip: TripSummary): com.obdreader.app.obd.Route? = try {
    com.obdreader.app.obd.Route.join(tripFiles(trip).map { com.obdreader.app.obd.Route.parse(it.readLines()) }).takeIf { !it.isEmpty }
} catch (e: OutOfMemoryError) { null } catch (e: java.io.IOException) { null }
/** When a recording started, from its name. */
internal fun MainActivity.fileStart(f: java.io.File): Long? = try {
    java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).parse(f.nameWithoutExtension.removePrefix("OBD2Reader_"))?.time
} catch (e: Exception) { null }
/** The recordings made during [trip], in order. */
internal fun MainActivity.tripFiles(trip: TripSummary): List<java.io.File> = LogRecorder.saved(this)
    .filter { f -> val start = fileStart(f) ?: return@filter false; start <= trip.endedAt && f.lastModified() >= trip.startedAt }
    .sortedBy { it.name }
/** A tile one shade lighter than the sheet behind it. */
internal fun MainActivity.tripTile() = GradientDrawable().apply {
    setColor(themeColor(R.attr.obdSurfaceHigh))
    cornerRadius = themeDimen(R.attr.obdCardRadius)
}
internal fun MainActivity.scoreColour(score: Int): Int = when {
    score >= 80 -> themeColor(R.attr.obdOkText)
    score >= 55 -> themeColor(R.attr.obdWarnText)
    else -> themeColor(R.attr.obdDangerText)
}
