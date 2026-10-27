package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetContentTest {
    private val now = 1_800_000_000_000L

    private fun build(phase: WidgetContent.Phase, readings: Map<String, Double?> = emptyMap(), mph: Boolean = false,
                      battery: WidgetContent.Seen? = null, codes: WidgetContent.CodesSeen? = null, drive: WidgetContent.LastDrive? = null, demo: Boolean = false) =
        WidgetContent.build(phase, "OBDII", demo, readings, emptySet(), mph, false, battery, codes, drive, now)

    @Test
    fun liveWhileConnected() {
        val c = build(WidgetContent.Phase.CONNECTED, mapOf("0D" to 100.0, "05" to 88.0, "VOLT" to null))
        assertEquals("Connected · OBDII", c.status)
        assertEquals(listOf("100", "88", "N/A"), c.cells.map { it.value })   // the car didn't answer the battery: N/A, not a guess
        assertEquals(listOf("km/h", "°C", ""), c.cells.map { it.sub })
        assertEquals("Disconnect", c.button)
        assertEquals("62", build(WidgetContent.Phase.CONNECTED, mapOf("0D" to 100.0), mph = true).cells[0].value)
        assertEquals("—", build(WidgetContent.Phase.CONNECTED).cells[0].value)   // not read yet
        assertEquals("Demo car", build(WidgetContent.Phase.CONNECTED, demo = true).status)
    }

    @Test
    fun lastSeenWhileNotConnected() {
        val c = build(WidgetContent.Phase.IDLE,
            battery = WidgetContent.Seen(12.46, now - 2 * 3_600_000),
            codes = WidgetContent.CodesSeen(1, 1, now - 5 * 60_000),
            drive = WidgetContent.LastDrive(12.34, 78, now - 3 * 24 * 3_600_000))
        assertEquals("Not connected", c.status)
        assertEquals(listOf("12.5 V", "1 + 1", "12.3 km"), c.cells.map { it.value })
        assertEquals(listOf("2 h ago", "5 min ago", "Score 78 · 3 days ago"), c.cells.map { it.sub })
        assertEquals("Connect", c.button)
        // Nothing seen yet: N/A with why.
        assertEquals(listOf("N/A", "N/A", "N/A"), build(WidgetContent.Phase.IDLE).cells.map { it.value })
        assertEquals("None", build(WidgetContent.Phase.IDLE, codes = WidgetContent.CodesSeen(0, 0, now)).cells[1].value)
        // Ignition off: the battery is live.
        assertEquals("12.6", build(WidgetContent.Phase.IGNITION_OFF, mapOf("VOLT" to 12.6)).cells[0].value)
    }

    @Test
    fun storage() {
        val s = WidgetContent.Seen(12.4, 123L)
        assertEquals(s, WidgetContent.decodeSeen(WidgetContent.encodeSeen(s)))
        val c = WidgetContent.CodesSeen(2, 0, 456L)
        assertEquals(c, WidgetContent.decodeCodes(WidgetContent.encodeCodes(c)))
        assertEquals(null, WidgetContent.decodeSeen("rubbish"))
        assertEquals("just now", WidgetContent.ago(now - 10_000, now))
    }
}
