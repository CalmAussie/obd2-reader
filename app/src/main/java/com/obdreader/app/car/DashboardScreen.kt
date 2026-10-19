package com.obdreader.app.car

import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.obdreader.app.R
import com.obdreader.app.obd.DemoTransport
import com.obdreader.app.obd.Pid
import com.obdreader.app.obd.Pids
import com.obdreader.app.session.ObdSession
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The Android Auto screen: big, glanceable live readings while driving, built from
 * Android Auto's driving-safe templates (no custom drawing is allowed in the car).
 */
class DashboardScreen(carContext: CarContext) : Screen(carContext) {

    private var lastPhase: ObdSession.Phase? = null
    private var lastRefresh = 0L

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ObdSession.state.collect { s ->
                    // Android Auto limits how often a screen may redraw, so refresh about once a second.
                    val now = SystemClock.elapsedRealtime()
                    if (s.phase != lastPhase || now - lastRefresh >= REFRESH_MS) {
                        lastPhase = s.phase
                        lastRefresh = now
                        invalidate()
                    }
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val s = ObdSession.state.value
        return if (s.isConnected) liveTemplate(s) else notConnectedTemplate(s)
    }

    private fun notConnectedTemplate(s: ObdSession.State): Template {
        val last = ObdSession.lastAdapterName(carContext)
        val message = when (s.phase) {
            ObdSession.Phase.CONNECTING -> "${s.title}\n${s.detail}"
            ObdSession.Phase.ERROR -> "${s.title}\n${s.detail}"
            else -> if (last != null) "Not connected. Tap Connect to use $last." else "Not connected. Pair your adapter in OBD2 Reader on your phone first."
        }
        val builder = MessageTemplate.Builder(message)
            .setTitle("OBD2 Reader")
            .setHeaderAction(Action.APP_ICON)
            .setIcon(CarLivery.roundel(carContext, 0f))

        if (s.phase != ObdSession.Phase.CONNECTING) {
            if (last != null) {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Connect")
                        .setBackgroundColor(CarLivery.accent(carContext))
                        .setOnClickListener { connectLast() }
                        .build(),
                )
            }
            builder.addAction(
                Action.Builder()
                    .setTitle("Demo")
                    .apply { if (last == null) setBackgroundColor(CarLivery.accent(carContext)) }
                    .setOnClickListener { ObdSession.connect(carContext, DemoTransport()) }
                    .build(),
            )
        }
        return builder.build()
    }

    private fun connectLast() {
        val t = ObdSession.lastAdapter(carContext)
        if (t == null) {
            CarToast.makeText(carContext, "Open OBD2 Reader on your phone to connect", CarToast.LENGTH_LONG).show()
        } else {
            ObdSession.connect(carContext, t)
        }
    }

    private fun liveTemplate(s: ObdSession.State): Template {
        val items = ItemList.Builder()
        for ((pid, icon) in CAR_READINGS) {
            items.addItem(gridItem(s, pid, icon))
        }
        items.addItem(
            GridItem.Builder()
                .setTitle(s.readings[ObdSession.VOLTAGE_KEY]?.let { String.format(Locale.US, "%.1f V", it) } ?: "—")
                .setText("Battery")
                .setImage(icon(R.drawable.ic_car_battery, false), GridItem.IMAGE_TYPE_ICON)
                .build(),
        )

        val codes = s.codes
        val codesLabel = when {
            codes == null -> "Codes"
            codes.total == 0 -> "No codes"
            else -> "Codes (${codes.total})"
        }
        val actions = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(codesLabel)
                    .setOnClickListener { screenManager.push(CodesScreen(carContext)) }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle("Disconnect")
                    .setOnClickListener { ObdSession.disconnect() }
                    .build(),
            )
            .build()

        return GridTemplate.Builder()
            .setTitle(s.adapterName ?: "Live data")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actions)
            .setSingleList(items.build())
            .build()
    }

    private fun gridItem(s: ObdSession.State, pid: Pid, iconRes: Int): GridItem {
        val value = s.readings[pid.hex]
        val unsupported = pid.hex in s.unsupported
        val warn = value != null && pid.warnAbove != null && value >= pid.warnAbove
        val title = when {
            unsupported -> "n/a"
            value == null -> "—"
            pid.number == Pids.RPM -> String.format(Locale.US, "%,.0f", value)
            else -> String.format(Locale.US, "%.${pid.decimals}f %s", value, pid.unit)
        }
        val label = if (pid.number == Pids.RPM) "rpm" else pid.label
        val builder = GridItem.Builder()
            .setTitle(title)
            .setText(if (warn) "$label, high" else label)
        // Engine speed gets the phone's rev roundel as a picture; the rest get livery-coloured icons.
        if (pid.number == Pids.RPM) {
            val fraction = ((value ?: 0.0) / pid.max).coerceIn(0.0, 1.0).toFloat()
            builder.setImage(CarLivery.roundel(carContext, fraction), GridItem.IMAGE_TYPE_LARGE)
        } else {
            builder.setImage(icon(iconRes, warn), GridItem.IMAGE_TYPE_ICON)
        }
        return builder.build()
    }

    private fun icon(res: Int, warn: Boolean) = CarIcon.Builder(IconCompat.createWithResource(carContext, res))
        .setTint(if (warn) CarColor.RED else CarLivery.accent(carContext))
        .build()

    companion object {
        private const val REFRESH_MS = 1000L

        /** The readings shown in the car, chosen for a glance while driving. */
        private val CAR_READINGS: List<Pair<Pid, Int>> by lazy {
            fun p(number: Int) = Pids.dashboard.first { it.number == number }
            listOf(
                p(Pids.RPM) to R.drawable.ic_car_speed,
                p(Pids.SPEED) to R.drawable.ic_car_speed,
                p(0x05) to R.drawable.ic_car_thermo,
                p(0x11) to R.drawable.ic_car_throttle,
                p(0x2F) to R.drawable.ic_car_fuel,
            )
        }
    }
}
