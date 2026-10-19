package com.obdreader.app.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.obdreader.app.obd.DtcDescriptions
import com.obdreader.app.session.ObdSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Trouble codes in the car. Reading is allowed here; clearing codes is left to the
 * phone, because it should be done parked with the engine off.
 */
class CodesScreen(carContext: CarContext) : Screen(carContext) {

    private var loading = false
    private var error: String? = null

    init {
        read(firstTime = true)
    }

    private fun read(firstTime: Boolean = false) {
        loading = true
        error = null
        if (!firstTime) invalidate() // the first draw already shows the loading state
        lifecycleScope.launch {
            try {
                ObdSession.readCodes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't read codes"
            }
            loading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle("Trouble codes")
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setTitle("Read again").setOnClickListener { read() }.build())
                    .build(),
            )

        if (loading) return builder.setLoading(true).build()

        val list = ItemList.Builder()
        val codes = ObdSession.state.value.codes
        when {
            error != null -> list.setNoItemsMessage("Couldn't read codes: $error")
            codes == null || codes.total == 0 -> list.setNoItemsMessage("No trouble codes")
            else -> {
                codes.stored.forEach { list.addItem(row(it, "Stored, check-engine light")) }
                codes.pending.forEach { list.addItem(row(it, "Pending, not yet confirmed")) }
            }
        }
        if (codes != null && codes.total > 0) {
            list.addItem(
                Row.Builder()
                    .setTitle("Clearing codes")
                    .addText("Clear codes from your phone, parked with the engine off.")
                    .build(),
            )
        }
        return builder.setSingleList(list.build()).build()
    }

    private fun row(code: String, kind: String) = Row.Builder()
        .setTitle("$code · ${DtcDescriptions.describe(code)}")
        .addText(kind)
        .build()
}
