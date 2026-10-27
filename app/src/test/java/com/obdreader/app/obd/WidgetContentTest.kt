package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        // Smaller widget sizes show the unit with the value.
        assertEquals(listOf("100 km/h", "88°C", "N/A"), c.cells.map { it.withUnit })
        assertEquals("12.5 V", build(WidgetContent.Phase.IDLE, battery = WidgetContent.Seen(12.46, now)).cells[0].withUnit)
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
    fun chosenReadings() {
        fun live(keys: List<String>, r: Map<String, Double?>) =
            WidgetContent.build(WidgetContent.Phase.CONNECTED, null, false, r, emptySet(), false, false, null, null, null, now, keys).cells
        // RPM in thousands, one decimal.
        assertEquals("0.7k", WidgetContent.rpmText(700.0))
        assertEquals("3.2k", WidgetContent.rpmText(3200.0))
        val c = live(listOf("0C", "5C"), mapOf("0C" to 3216.0, "5C" to 96.0))
        assertEquals(listOf("RPM", "Oil temp"), c.map { it.label })
        assertEquals(listOf("3.2k", "96"), c.map { it.value })
        assertEquals(listOf("3.2k", "96°C"), c.map { it.withUnit })
        // One to three, no repeats, unknown ones dropped; nothing valid means the defaults.
        assertEquals(listOf("0C"), WidgetContent.decodeReadings("0C"))
        assertEquals(listOf("0C", "0D", "05"), WidgetContent.decodeReadings("0C,0C,XX,0D,05,VOLT"))
        assertEquals(WidgetContent.DEFAULT_READINGS, WidgetContent.decodeReadings(null))
        assertEquals("0C,VOLT", WidgetContent.encodeReadings(listOf("0C", "VOLT", "0C")))
        assertEquals(1, live(listOf("0C"), emptyMap()).size)
    }

    @Test
    fun nearTheMaximum() {
        fun cells(r: Map<String, Double?>) = WidgetContent.build(WidgetContent.Phase.CONNECTED, null, false, r, emptySet(), false, false, null, null, null, now,
            listOf("0C", "0D", "05"), redline = 6500, topSpeedKmh = 171).cells
        // The Tribute 2.3: redline 6,500, top speed 171 km/h. The last 10% lights up.
        assertEquals(listOf(false, false, false), cells(mapOf("0C" to 5800.0, "0D" to 150.0, "05" to 120.0)).map { it.highlight })
        assertEquals(listOf(true, true, false), cells(mapOf("0C" to 5900.0, "0D" to 155.0, "05" to 120.0)).map { it.highlight })
        // Unknown redline or top speed: nothing lights up rather than guessing.
        assertEquals(false, WidgetContent.nearMax("0D", 300.0, null, null))
        assertEquals(false, WidgetContent.nearMax("0C", 9000.0, null, null))
    }

    @Test
    fun topSpeeds() {
        assertTrue(TopSpeeds.engineCount > 1500)
        assertEquals(171 to true, TopSpeeds.forCar(VehicleCatalog.Car("Mazda", 2006, "Tribute", "2.3 I4")))
        assertEquals(171 to true, TopSpeeds.forCar(VehicleCatalog.Car("Mazda", 2006, "Tribute", "2.3")))   // typed by hand
        val noEngine = TopSpeeds.forCar(VehicleCatalog.Car("Mazda", 2006, "Tribute", null))!!
        assertEquals(false, noEngine.second); assertTrue(noEngine.first >= 171)
        assertEquals(null, TopSpeeds.forCar(VehicleCatalog.Car("Other make", 2010, null, null)))
        // Every figure is a plausible road-car top speed.
        assertTrue(TopSpeeds.engineCount > 0)
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
