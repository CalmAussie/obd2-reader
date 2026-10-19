package com.obdreader.app.obd

/** Short names for trouble codes. The full explanations live in [DtcLibrary]. */
object DtcDescriptions {
    fun describe(code: String): String = DtcLibrary.title(code)
}
