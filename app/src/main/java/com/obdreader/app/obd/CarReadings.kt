package com.obdreader.app.obd

/**
 * The four readings on the Android Auto screen next to speed and engine speed (which always
 * stay, since several styles draw them together). Chosen under Diagnostics → Android Auto readings.
 */
object CarReadings {
    const val COUNT = 4

    /** Coolant, throttle, fuel and battery to start with. */
    val DEFAULT = listOf("05", "11", "2F", "VOLT")

    /** Everything that can go in a tile: the widget's readings without speed and engine speed. */
    val CHOICES: List<WidgetContent.Reading> = WidgetContent.READINGS.filter { it.key != "0C" && it.key != "0D" }

    /** The chosen readings from how they're kept: always four, known ones only, no repeats (gaps filled from the defaults). */
    fun decode(text: String?): List<String> {
        val known = CHOICES.map { it.key }.toSet()
        val picked = text.orEmpty().split(",").map { it.trim() }.filter { it in known }.distinct().take(COUNT)
        return picked + (DEFAULT + CHOICES.map { it.key }).filter { it !in picked }.distinct().take(COUNT - picked.size)
    }

    fun encode(keys: List<String>): String = keys.distinct().take(COUNT).joinToString(",")

    fun label(key: String): String = CHOICES.firstOrNull { it.key == key }?.label ?: key
}
