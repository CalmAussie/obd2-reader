package com.obdreader.app.obd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHistoryTest {

    private val car = "2006 Mazda Tribute"
    private val s = CodeHistory.Kind.STORED
    private val p = CodeHistory.Kind.PENDING
    private val m = CodeHistory.Kind.PERMANENT

    @Test fun recordsFirstAndLastSeenAndWhenItWent() {
        var h = CodeHistory.record(emptyList(), car, mapOf(s to listOf("P0420"), p to listOf("P0171")), 100)
        assertEquals(2, h.size)
        h = CodeHistory.record(h, car, mapOf(s to listOf("P0420"), p to emptyList()), 200)
        val cat = h.first { it.code == "P0420" }
        assertEquals(100L, cat.firstSeen); assertEquals(200L, cat.lastSeen); assertNull(cat.goneSince)
        assertEquals(200L, h.first { it.code == "P0171" }.goneSince)
        // It comes back: no longer gone, first seen kept.
        h = CodeHistory.record(h, car, mapOf(p to listOf("P0171")), 300)
        val lean = h.first { it.code == "P0171" }
        assertNull(lean.goneSince); assertEquals(100L, lean.firstSeen); assertEquals(300L, lean.lastSeen)
    }

    @Test fun aKindThatWasntReadIsLeftAlone() {
        var h = CodeHistory.record(emptyList(), car, mapOf(m to listOf("P0420")), 100)
        // The two-minute check reads stored and pending only; permanent stays as it was.
        h = CodeHistory.record(h, car, mapOf(s to emptyList(), p to emptyList(), m to null), 200)
        assertNull(h.single().goneSince)
    }

    @Test fun eachCarHasItsOwnCodes() {
        var h = CodeHistory.record(emptyList(), car, mapOf(s to listOf("P0420")), 100)
        h = CodeHistory.record(h, "2015 Kia Cerato", mapOf(s to emptyList()), 200)
        assertNull(h.single().goneSince)
        h = CodeHistory.record(h, "2015 Kia Cerato", mapOf(s to listOf("P0300")), 300)
        assertEquals(listOf("2015 Kia Cerato", car), CodeHistory.cars(h, "2015 Kia Cerato"))
        assertEquals(listOf(car, "2015 Kia Cerato"), CodeHistory.cars(h, car))
    }

    @Test fun showsActiveFirst() {
        var h = CodeHistory.record(emptyList(), car, mapOf(s to listOf("P0420", "P0133")), 100)
        h = CodeHistory.record(h, car, mapOf(s to listOf("P0420")), 200)
        assertEquals(listOf("P0420", "P0133"), CodeHistory.forCar(h, car).map { it.code })
    }

    @Test fun savesAndReadsBack() {
        val h = CodeHistory.record(emptyList(), "Car | with a bar", mapOf(s to listOf("P0420"), m to listOf("U0100")), 1_700_000_000_000)
        assertEquals(h, CodeHistory.parse(CodeHistory.encode(h)))
        assertTrue(CodeHistory.parse("junk\nP0420|x|1|2||car").isEmpty())
        assertTrue(CodeHistory.parse(null).isEmpty())
    }

    @Test fun staysBounded() {
        var h = emptyList<CodeHistory.Entry>()
        for (i in 0 until CodeHistory.MAX_ENTRIES + 20) h = CodeHistory.record(h, "car $i", mapOf(s to listOf("P0420")), i.toLong())
        assertEquals(CodeHistory.MAX_ENTRIES, h.size)
        assertEquals("car ${CodeHistory.MAX_ENTRIES + 19}", h.first().car)
    }

    @Test fun describesDates() {
        val e = CodeHistory.Entry("P0420", s, car, 1, 2, 3)
        assertEquals("Stored. Seen day1 to day2, gone since day3", CodeHistory.describe(e) { "day$it" })
        assertEquals("Pending. First seen day1", CodeHistory.describe(e.copy(kind = p, lastSeen = 1, goneSince = null)) { "day$it" })
    }

    @Test fun demoCarReportsAPermanentCode() = runBlocking {
        val c = ElmClient(DemoTransport().apply { open() })
        // Not connected here, so the client doesn't know it's CAN yet: it answers, and on CAN that's P0420.
        assertTrue(c.readPermanentDtcs() != null)
        assertEquals(listOf("P0420"), ElmParser.parseDtcs(listOf("4A010420"), "4A", isCan = true))
        // A car without permanent codes says NO DATA: not reported, rather than none.
        assertNull(ElmClient(object : ObdTransport by DemoTransport() { override fun exchange(command: String, timeoutMs: Long) = "NO DATA" }).readPermanentDtcs())
        assertTrue(ReadOnlyGuard.isAllowed("0A"))
    }
}
