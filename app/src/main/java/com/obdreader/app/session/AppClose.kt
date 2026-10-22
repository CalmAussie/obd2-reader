package com.obdreader.app.session

import android.content.Context

/**
 * What happens when the app is closed completely (swiped away from Recents), as opposed to
 * minimised: any recording is finished and saved, the car is disconnected and the background
 * service stops. Minimised, everything keeps running as normal.
 *
 * The exception is Android Auto: while the car screen is showing the app, closing the phone app
 * leaves the connection running, because the car screen still needs it.
 */
object AppClose {

    /** Set while Android Auto is showing the app on the car screen. */
    @Volatile var carScreenOpen = false

    fun closed(context: Context) {
        if (carScreenOpen) { android.util.Log.i("OBD2Close", "closed, but Android Auto is open: keeping the connection"); return }
        android.util.Log.i("OBD2Close", "closed: stopping recording, connection and service")
        LogRecorder.stop()
        ObdSession.disconnect()
        ObdConnectionService.stop(context)
    }
}
