package com.obdreader.app.obd

/**
 * Metric or imperial, chosen separately for speed (km/h or mph, with distance in km or miles)
 * and temperature (°C or °F). The car always reports metric; this converts for display only,
 * so recorded drives (CSV) and the adapter log stay in the car's own units.
 */
object Units {
    var mph = false
    var fahrenheit = false

    /** Readings that are temperatures: coolant, intake air, ambient air and oil. */
    private val TEMPERATURES = setOf("05", "0F", "46", "5C")
    private const val SPEED_KEY = "0D"

    fun isTemperature(key: String) = key in TEMPERATURES
    fun isSpeed(key: String) = key == SPEED_KEY

    fun speed(kmh: Double): Double = if (mph) kmh * 0.621371 else kmh
    fun temperature(c: Double): Double = if (fahrenheit) c * 9.0 / 5.0 + 32.0 else c
    fun distance(km: Double): Double = if (mph) km * 0.621371 else km

    val speedUnit: String get() = if (mph) "mph" else "km/h"
    val temperatureUnit: String get() = if (fahrenheit) "°F" else "°C"
    val distanceUnit: String get() = if (mph) "mi" else "km"

    /** A reading converted for display; values that aren't speed or temperature pass through. */
    fun value(key: String, v: Double): Double = when {
        isSpeed(key) -> speed(v)
        isTemperature(key) -> temperature(v)
        else -> v
    }

    fun value(key: String, v: Double?): Double? = v?.let { value(key, it) }

    /** The display unit for a reading whose metric unit is [metric]. */
    fun unit(key: String, metric: String): String = when {
        isSpeed(key) -> speedUnit
        isTemperature(key) -> temperatureUnit
        else -> metric
    }

    /** "112 km/h" or "70 mph". */
    fun speedText(kmh: Double): String = "${Math.round(speed(kmh))} $speedUnit"

    /** "110 °C" or "230 °F". */
    fun temperatureText(c: Double): String = "${Math.round(temperature(c))} $temperatureUnit"
}
