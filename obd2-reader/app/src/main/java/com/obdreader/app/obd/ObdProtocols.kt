package com.obdreader.app.obd

import java.util.Locale

/**
 * The OBD2 protocols an ELM327 can speak, in the order the app tries them when
 * the adapter's own auto-search fails. The order favours Ford, Mazda and Honda:
 * CAN for 2008-on, J1850 PWM for 2000s Fords, ISO 9141-2 / KWP for 2000s Hondas and Mazdas.
 */
object ObdProtocols {

    const val AUTO = "0"

    data class Protocol(
        /** ELM327 protocol number used with ATSP / reported by ATDPN. */
        val code: String,
        val name: String,
        /** Which cars typically use it, shown in the protocol picker. */
        val hint: String,
        /** How long to wait for the first reply; K-line protocols need a slow wake-up. */
        val timeoutMs: Long,
    )

    val ALL: List<Protocol> = listOf(
        Protocol("6", "ISO 15765-4 CAN (11-bit, 500 kbaud)", "Most cars from 2008, including Ford, Mazda and Honda", 4000),
        Protocol("1", "SAE J1850 PWM", "Fords and Ford-based Mazdas, about 2000–2007", 5000),
        Protocol("3", "ISO 9141-2", "Hondas and Mazdas, about 2000–2007", 9000),
        Protocol("5", "ISO 14230-4 KWP (fast init)", "Some Mazdas and Hondas, about 2003–2008", 9000),
        Protocol("4", "ISO 14230-4 KWP (5-baud init)", "Some early-2000s Asian and European cars", 9000),
        Protocol("2", "SAE J1850 VPW", "Mainly GM, rarely needed for these makes", 5000),
        Protocol("7", "ISO 15765-4 CAN (29-bit, 500 kbaud)", "Some newer models", 4000),
        Protocol("8", "ISO 15765-4 CAN (11-bit, 250 kbaud)", "Uncommon", 4000),
        Protocol("9", "ISO 15765-4 CAN (29-bit, 250 kbaud)", "Uncommon", 4000),
    )

    fun byCode(code: String?): Protocol? {
        val c = code?.trim()?.uppercase(Locale.US)?.removePrefix("A") ?: return null
        return ALL.firstOrNull { it.code == c }
    }
}
