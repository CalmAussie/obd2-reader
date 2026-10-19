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
import com.obdreader.app.obd.DemoTransport
import com.obdreader.app.obd.Pids
import com.obdreader.app.session.ObdSession
import com.obdreader.app.ui.SmoothValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The Android Auto screen: big, glanceable live readings while driving, built from Android
 * Auto's driving-safe templates.
 *
 * Two Android Auto rules shape it:
 * - A screen may only send 5 *new* templates before the car closes the app with an error.
 *   An update only counts as a refresh (free) if the title, the number of tiles and each tile's
 *   title stay the same. So there's one grid for every state (not connected, connecting,
 *   live, ignition off) with fixed tile titles; the live numbers are drawn into the tiles'
 *   images, and the status goes in the first tile's text.
 * - The car throttles how often the screen redraws. Within that, the screen refreshes several
 *   times a second, and each reading glides between refreshes, like the phone's Dashboard.
 */
class DashboardScreen(carContext: CarContext) : Screen(carContext) {

    private class Tile(val key: String, val title: String, val unit: String, val decimals: Int, val min: Float, val max: Float, val warnAt: Float?) {
        val smooth = SmoothValue(tauMs = 140f, settle = if (decimals > 0) 0.05f else 0.5f, maxStepMs = 300L, firstStepMs = REFRESH_MS)
    }

    private val tiles: List<Tile> by lazy {
        fun pid(n: Int) = Pids.dashboard.first { it.number == n }
        fun tile(n: Int, title: String, max: Float? = null) = pid(n).let {
            Tile(it.hex, title, it.unit, it.decimals, it.min.toFloat(), max ?: it.max.toFloat(), it.warnAbove?.toFloat())
        }
        listOf(
            tile(Pids.RPM, "Engine speed"),
            tile(Pids.SPEED, "Speed", 240f),
            tile(0x05, "Coolant"),
            tile(0x11, "Throttle"),
            tile(0x2F, "Fuel"),
            Tile(ObdSession.VOLTAGE_KEY, "Battery", "V", 1, 11f, 15f, null),
        )
    }

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
        val items = ItemList.Builder()
        tiles.forEachIndexed { i, t ->
            val raw = s.readings[t.key]
            t.smooth.set(raw?.toFloat())
            t.smooth.step(now)
            val shown = if (raw == null) null else t.smooth.shown
            val unsupported = t.key in s.unsupported
            val high = raw != null && t.warnAt != null && raw >= t.warnAt
            val text = when {
                i == 0 && !s.isConnected -> statusLine(s)
                unsupported -> "Not supported"
                high -> "High"
                else -> t.unit
            }
            val image = if (t.key == HEX_RPM) {
                CarLivery.roundel(carContext, ((shown ?: 0f) / t.max).coerceIn(0f, 1f), shown?.let { String.format(Locale.US, "%,d", ((it / 10f).toInt() * 10)) } ?: "—")
            } else {
                CarLivery.readingTile(
                    carContext,
                    value = if (unsupported) "n/a" else shown?.let { String.format(Locale.US, "%.${t.decimals}f", it) } ?: "—",
                    fraction = shown?.let { ((it - t.min) / (t.max - t.min)).coerceIn(0f, 1f) },
                    high = high,
                )
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
    }
}
