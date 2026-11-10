package com.obdreader.app.car

import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.obdreader.app.obd.Boost
import com.obdreader.app.obd.CarReadings
import com.obdreader.app.obd.DemoTransport
import com.obdreader.app.obd.Pids
import com.obdreader.app.obd.Units
import com.obdreader.app.session.AppPrefs
import com.obdreader.app.session.DriveScorer
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.SmoothValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The Android Auto screen: big, glanceable live readings while driving, built from Android
 * Auto's driving-safe templates.
 *
 * Two Android Auto rules shape it:
 * - A screen may only send 5 *new* templates before the car closes the app with an error.
 *   An update only counts as a refresh (free) if the title, the number of tiles and each tile's
 *   title stay the same. So there's one grid for every state (not connected, connecting,
 *   live, ignition off) with fixed tile titles; the live numbers are drawn into the tiles'
 *   images, and the status goes in the engine-speed tile's text.
 * - The car throttles how often the screen redraws. Within that, the screen refreshes several
 *   times a second, and each reading glides between refreshes, like the phone's Dashboard.
 */
class DashboardScreen(carContext: CarContext) : Screen(carContext) {

    private class Tile(val key: String, val title: String, val unit: String, val decimals: Int, val min: Float, val max: Float, val warnAt: Float?) {
        val smooth = SmoothValue(tauMs = 140f, settle = if (decimals > 0) 0.05f else 0.5f, maxStepMs = 300L, firstStepMs = REFRESH_MS)
    }

    private var tilesFor: String? = null
    private var tiles: List<Tile> = emptyList()

    /** Speed and engine speed, then the four chosen readings (Diagnostics → Android Auto readings). */
    private fun buildTiles(chosen: List<String>): List<Tile> {
        // In the chosen units (mph, °F, psi), like the phone.
        fun u(k: String, v: Double) = Units.value(k, v).toFloat()
        fun pidTile(n: Int, title: String, max: Float? = null, warn: Float? = null) = Pids.dashboard.first { it.number == n }.let {
            val k = it.hex
            Tile(k, title, Units.unit(k, it.unit), Units.decimals(k, it.decimals), u(k, it.min), max?.let { m -> u(k, m.toDouble()) } ?: u(k, it.max), warn ?: it.warnAbove?.let { w -> u(k, w) })
        }
        fun tile(k: String): Tile = when (k) {
            ObdSession.VOLTAGE_KEY -> Tile(k, "Battery", "V", 1, 11f, 15f, null)
            Boost.KEY -> Tile(k, Boost.LABEL, Units.unit(k, "kPa"), Units.decimals(k, 0), u(k, Boost.MIN), u(k, Boost.MAX), null)
            else -> Pids.dashboard.first { it.hex == k }.let { pidTile(it.number, SHORT[k] ?: it.label) }
        }
        // The rev roundel follows the car's engine, as the phone's dial does.
        val rec = AppPrefs.savedCar(AppPrefs.of(carContext))?.let { com.obdreader.app.obd.VehicleCatalog.recommend(it) }
        return listOf(
            // Speed first, so engine speed sits top centre of the grid in every style.
            pidTile(Pids.SPEED, "Speed", 240f),
            pidTile(Pids.RPM, "Engine speed", max = com.obdreader.app.obd.VehicleCatalog.dialMax(rec?.redline).toFloat(), warn = (rec?.redline ?: 6500).toFloat()),
        ) + chosen.map { tile(it) }
    }

    /** The tiles, rebuilt when the chosen readings change (new titles count as a new template, so only then). */
    private fun currentTiles(): List<Tile> {
        val saved = AppPrefs.of(carContext).getString(AppPrefs.CAR_READINGS, null).orEmpty()
        if (saved != tilesFor || tiles.isEmpty()) {
            tiles = buildTiles(CarReadings.decode(saved))
            tilesFor = saved
            carTiles.reset()
        }
        return tiles
    }

    private val carTiles = CarTiles(carContext)
    private var lastPhase: ObdSession.Phase? = null
    private var lastTitle: String? = null

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Status changes: redraw straight away, and say the important ones out loud as a toast.
                launch {
                    ObdSession.state.collect { s ->
                        if (s.phase != lastPhase || s.title != lastTitle) {
                            if (s.phase == ObdSession.Phase.ERROR && lastPhase != null) {
                                CarToast.makeText(carContext, "${s.title}. ${s.detail}", CarToast.LENGTH_LONG).show()
                            }
                            lastPhase = s.phase
                            lastTitle = s.title
                            invalidate()
                        }
                    }
                }
                // Live readings: refresh several times a second while connected, so values glide.
                launch {
                    while (isActive) {
                        delay(REFRESH_MS)
                        if (ObdSession.state.value.isConnected || tiles.any { it.smooth.target != null && it.smooth.shown != it.smooth.target }) invalidate()
                    }
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val s = ObdSession.state.value
        val now = SystemClock.uptimeMillis()
        val tiles = currentTiles()
        val items = ItemList.Builder()
        // Step every reading first: the cockpit ring and the HUD show speed and engine speed together.
        val shownAll = tiles.map { t ->
            val raw = Units.value(t.key, s.readings[t.key])
            t.smooth.set(raw?.toFloat())
            t.smooth.step(now)
            raw to (if (raw == null) null else t.smooth.shown)
        }
        val iRpm = tiles.indexOfFirst { it.key == HEX_RPM }
        val iSpeed = tiles.indexOfFirst { it.key == HEX_SPEED }
        val rpmTile = tiles[iRpm]
        val speedTile = tiles[iSpeed]
        val rpmShown = shownAll[iRpm].second
        val speedShown = shownAll[iSpeed].second
        val top = DriveScorer.topSpeed.value?.let { Units.speed(it).toFloat() }
        val checkEngine = (s.codes?.total ?: 0) > 0
        tiles.forEachIndexed { i, t ->
            val (raw, shown) = shownAll[i]
            val unsupported = t.key in s.unsupported
            val high = raw != null && t.warnAt != null && raw >= t.warnAt
            val text = when {
                i == iRpm && !s.isConnected -> statusLine(s)
                unsupported -> "Not supported"
                high -> "High"
                // The drive's top speed under the speed (tile text can change freely; only titles must stay fixed).
                t.key == HEX_SPEED && top != null -> "Top ${top.roundToInt()} ${t.unit}"
                // Battery: the estimated charging status under the voltage.
                t.key == ObdSession.VOLTAGE_KEY && s.isConnected && s.charging != null -> "${t.unit} · ${s.charging.short} (est.)"
                // Boost against sea level when the car doesn't give the outside air pressure.
                t.key == Boost.KEY && s.isConnected && Boost.KEY in s.standIns -> "${t.unit} (est.)"
                else -> t.unit
            }
            val image = try {
                carTiles.image(
                    CarTiles.Reading(t.key, t.unit, t.decimals, t.min, t.max, t.warnAt, shown, unsupported),
                    speed = speedShown, rpm = rpmShown, rpmMax = rpmTile.max, redline = rpmTile.warnAt ?: 6500f,
                    speedMax = speedTile.max, speedUnit = speedTile.unit, top = top, checkEngine = checkEngine, slot = i,
                )
            } catch (e: Exception) {
                // Never lose the car screen over a drawing problem: fall back to the plain tiles.
                android.util.Log.w("OBD2Car", "tile: ${e.message}")
                if (t.key == HEX_RPM) CarLivery.roundel(carContext, ((shown ?: 0f) / t.max).coerceIn(0f, 1f), shown?.let { String.format(Locale.US, "%,d", ((it / 10f).toInt() * 10)) } ?: "—")
                else CarLivery.readingTile(carContext, if (unsupported) "N/A" else shown?.let { String.format(Locale.US, "%.${t.decimals}f", it) } ?: "—", shown?.let { ((it - t.min) / (t.max - t.min)).coerceIn(0f, 1f) }, high)
            }
            items.addItem(
                GridItem.Builder()
                    .setTitle(t.title) // fixed, so every update is a free refresh
                    .setText(text)
                    .setImage(image, GridItem.IMAGE_TYPE_LARGE)
                    .build(),
            )
        }
        return GridTemplate.Builder()
            .setTitle("OBD2 Reader") // fixed for the same reason
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actions(s))
            .setSingleList(items.build())
            .build()
    }

    private fun statusLine(s: ObdSession.State): String = when (s.phase) {
        ObdSession.Phase.CONNECTING -> "Connecting…"
        ObdSession.Phase.WAITING -> s.title // "Ignition off" or "Waiting for the car"
        ObdSession.Phase.ERROR -> s.title
        else -> if (ObdSession.lastAdapterName(carContext) != null) "Not connected" else "Pair on phone"
    }

    private fun actions(s: ObdSession.State): ActionStrip {
        val strip = ActionStrip.Builder()
        when (s.phase) {
            ObdSession.Phase.CONNECTED -> {
                val codes = s.codes
                strip.addAction(
                    Action.Builder()
                        .setTitle(when { codes == null -> "Codes"; codes.total == 0 -> "No codes"; else -> "Codes (${codes.total})" })
                        .setOnClickListener { screenManager.push(CodesScreen(carContext)) }
                        .build(),
                )
                strip.addAction(Action.Builder().setTitle("Disconnect").setOnClickListener { ObdSession.disconnect() }.build())
            }
            ObdSession.Phase.CONNECTING, ObdSession.Phase.WAITING -> {
                strip.addAction(Action.Builder().setTitle("Stop").setOnClickListener { ObdSession.disconnect() }.build())
            }
            else -> {
                if (ObdSession.lastAdapterName(carContext) != null) {
                    strip.addAction(Action.Builder().setTitle("Connect").setOnClickListener { connectLast() }.build())
                }
                strip.addAction(Action.Builder().setTitle("Demo").setOnClickListener { ObdSession.connect(carContext, DemoTransport(com.obdreader.app.obd.VinDecoder.sampleVin(com.obdreader.app.session.AppPrefs.savedCar(com.obdreader.app.session.AppPrefs.of(carContext))?.make, com.obdreader.app.session.AppPrefs.savedCar(com.obdreader.app.session.AppPrefs.of(carContext))?.year))) }.build())
            }
        }
        return strip.build()
    }

    private fun connectLast() {
        val t = ObdSession.lastAdapter(carContext)
        if (t == null) {
            CarToast.makeText(carContext, "Open OBD2 Reader on your phone to connect", CarToast.LENGTH_LONG).show()
        } else {
            ObdSession.connect(carContext, t)
        }
    }

    companion object {
        /** About 5 refreshes a second: smooth to the eye, and within what Android Auto shows. */
        private const val REFRESH_MS = 200L
        private val HEX_RPM = String.format(Locale.US, "%02X", Pids.RPM)
        private val HEX_SPEED = String.format(Locale.US, "%02X", Pids.SPEED)
        /** Shorter tile titles where the phone's label is long. */
        private val SHORT = mapOf("2F" to "Fuel", "0B" to "Manifold", "10" to "Airflow", "0E" to "Timing", "04" to "Load")
    }
}
