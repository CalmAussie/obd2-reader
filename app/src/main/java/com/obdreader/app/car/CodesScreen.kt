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
import com.obdreader.app.obd.DtcLibrary
import com.obdreader.app.session.ObdSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Trouble codes in the car. The app is read-only, so codes are only ever read, never cleared.
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
                codes.stored.forEach { list.addItem(row(it, "Stored, check-engine light on")) }
                codes.pending.forEach { list.addItem(row(it, "Pending, not yet confirmed")) }
            }
        }
        return builder.setSingleList(list.build()).build()
    }

    /** Each code on the same plate as the phone's Diagnostics tab, beside its description. */
    private fun row(code: String, kind: String) = Row.Builder()
        .setTitle(DtcLibrary.title(code, ObdSession.carMake(carContext)))
        .addText("$code, $kind")
        .setImage(CarLivery.plate(carContext, code, stored = kind.startsWith("Stored")), Row.IMAGE_TYPE_LARGE)
        .build()
}
