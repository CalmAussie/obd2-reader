package com.obdreader.app.obd

import java.util.Locale

/**
 * Adapters sold to work with their maker's own app. Some of them don't speak the standard ELM327
 * language that OBD2 Reader (and Torque, Car Scanner and similar apps) use. When one of these
 * doesn't answer like an ELM327, the app says why, instead of a vague "Couldn't connect".
 * If one does answer like an ELM327, it simply works.
 */
object LockedAdapters {

    data class Locked(val brand: String, val app: String)

    private val KNOWN = listOf(
        listOf("thinkdiag", "thinkcar", "think diag", "thinkobd") to Locked("ThinkDiag", "ThinkCar's ThinkDiag app"),
        listOf("bluedriver") to Locked("BlueDriver", "the BlueDriver app"),
    )

    /** The maker-locked adapter [deviceName] looks like, or null. */
    fun match(deviceName: String?): Locked? {
        val n = deviceName?.lowercase(Locale.US)?.replace("-", "")?.replace("_", " ") ?: return null
        return KNOWN.firstOrNull { (names, _) -> names.any { it in n || it.replace(" ", "") in n.replace(" ", "") } }?.second
    }

    /** What to tell the user when [locked] didn't answer like an ELM327. */
    fun message(locked: Locked): String =
        "This looks like a ${locked.brand} adapter, which didn't answer like an ELM327. ${locked.brand} adapters are made to work with " +
            "${locked.app}, so OBD2 Reader can't use them. Any ELM327-compatible adapter (Bluetooth, BLE or Wi-Fi) works."
}
