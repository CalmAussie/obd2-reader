package com.obdreader.app.obd

/**
 * The car's readiness monitors (service 01, PID 01): the self-tests the engine computer runs as
 * you drive, and whether each one has finished since the codes were last cleared or the battery
 * disconnected. An emissions inspection usually needs them complete, and checking them after a
 * repair shows whether the fix has been confirmed.
 */
data class Readiness(val milOn: Boolean, val codeCount: Int, val diesel: Boolean, val monitors: List<Monitor>) {

    enum class State(val label: String) { COMPLETE("Complete"), INCOMPLETE("Not complete yet") }

    data class Monitor(val name: String, val about: String, val state: State)

    val incomplete: List<Monitor> get() = monitors.filter { it.state == State.INCOMPLETE }

    /** "All 7 complete" or "2 of 7 not complete yet". */
    fun summary(): String = when {
        monitors.isEmpty() -> "The car doesn't list any monitors"
        incomplete.isEmpty() -> "All ${monitors.size} complete"
        else -> "${incomplete.size} of ${monitors.size} not complete yet"
    }

    companion object {
        private val CONTINUOUS = listOf(
            "Misfire" to "Checks every cylinder fires properly",
            "Fuel system" to "Checks the fuel mixture control",
            "Components" to "Checks sensors and parts the engine computer uses",
        )
        private val SPARK = listOf(
            "Catalytic converter" to "Checks the catalytic converter cleans the exhaust",
            "Heated catalyst" to "Checks the catalyst heater",
            "Evaporative system" to "Checks the fuel vapour system for leaks",
            "Secondary air" to "Checks the air pump that helps the catalyst warm up",
            "A/C refrigerant" to "Checks the air-conditioning refrigerant system",
            "Oxygen sensors" to "Checks the oxygen sensors respond properly",
            "Oxygen sensor heaters" to "Checks the oxygen sensor heaters",
            "EGR / variable valve timing" to "Checks exhaust gas recirculation or valve timing",
        )
        private val DIESEL = listOf(
            "NMHC catalyst" to "Checks the diesel oxidation catalyst",
            "NOx / SCR" to "Checks the NOx catalyst or AdBlue system",
            null,
            "Boost pressure" to "Checks turbo boost control",
            null,
            "Exhaust gas sensor" to "Checks the exhaust gas sensors",
            "Particulate filter" to "Checks the diesel particulate filter",
            "EGR / variable valve timing" to "Checks exhaust gas recirculation or valve timing",
        )

        /** From the four data bytes of a service 01 PID 01 answer, or null if there aren't four. */
        fun decode(bytes: IntArray?): Readiness? {
            if (bytes == null || bytes.size < 4) return null
            val (a, b, c, d) = listOf(bytes[0], bytes[1], bytes[2], bytes[3])
            val diesel = b and 0x08 != 0
            val monitors = mutableListOf<Monitor>()
            CONTINUOUS.forEachIndexed { i, (name, about) ->
                if (b and (1 shl i) != 0) monitors += Monitor(name, about, if (b and (1 shl (i + 4)) != 0) State.INCOMPLETE else State.COMPLETE)
            }
            (if (diesel) DIESEL else SPARK).forEachIndexed { i, entry ->
                val (name, about) = entry ?: return@forEachIndexed
                if (c and (1 shl i) != 0) monitors += Monitor(name, about, if (d and (1 shl i) != 0) State.INCOMPLETE else State.COMPLETE)
            }
            return Readiness(a and 0x80 != 0, a and 0x7F, diesel, monitors)
        }
    }
}
