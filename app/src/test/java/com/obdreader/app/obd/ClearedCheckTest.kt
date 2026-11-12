package com.obdreader.app.obd

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Codes recently cleared?": the verdict, its wording and N/A for what a car doesn't report. */
class ClearedCheckTest {

    @After fun metric() { Units.mph = false }

    /** Readiness with every monitor complete, or with the evaporative system not finished. */
    private val allDone = Readiness.decode(intArrayOf(0x00, 0x07, 0x65, 0x00))
    private val evapPending = Readiness.decode(intArrayOf(0x00, 0x07, 0x65, 0x04))

    private fun check(km: Int? = null, warm: Int? = null, min: Int? = null, milKm: Int? = null, milMin: Int? = null, r: Readiness? = allDone) =
        ClearedCheck(km, warm, min, milKm, milMin, r)

    @Test fun smallCountersMeanRecentlyCleared() {
        val c = check(km = 12, warm = 2, min = 30)
        assertEquals(ClearedCheck.Verdict.RECENT, c.verdict)
        assertEquals("Codes look recently cleared: only 12 km and 2 warm-ups since.", c.headline())
    }

    @Test fun anyOneRecentCounterIsEnough() {
        assertEquals(ClearedCheck.Verdict.RECENT, check(km = 40_000, warm = 4).verdict)
        assertEquals(ClearedCheck.Verdict.RECENT, check(km = 60, warm = 200).verdict)
    }

    @Test fun fairlyRecentAndNotRecent() {
        assertEquals(ClearedCheck.Verdict.FAIRLY_RECENT, check(km = 340, warm = 14).verdict)
        assertEquals("Codes were cleared fairly recently: 340 km and 14 warm-ups ago.", check(km = 340, warm = 14).headline())
        val old = check(km = 4210, warm = 87)
        assertEquals(ClearedCheck.Verdict.NOT_RECENT, old.verdict)
        assertEquals("No sign of a recent clear: 4,210 km and 87 warm-ups since the codes were last cleared.", old.headline())
    }

    @Test fun maxedCountersSayMoreThan() {
        assertEquals("No sign of a recent clear: more than 65,535 km and more than 255 warm-ups since the codes were last cleared.", check(km = 65_535, warm = 255).headline())
    }

    @Test fun onlyTimeReported() {
        val c = check(min = 125)
        assertEquals(ClearedCheck.Verdict.RECENT, c.verdict)
        assertEquals("Codes look recently cleared: only 2 h 5 min of engine running since.", c.headline())
    }

    @Test fun nothingReportedIsNotAGuess() {
        val c = check(r = evapPending)
        assertEquals(ClearedCheck.Verdict.NOT_REPORTED, c.verdict)
        assertEquals("Your car doesn't report how long since its codes were cleared. 1 readiness monitor is not finished yet, which also happens after a clear.", c.headline())
        assertEquals("Your car doesn't report how long since its codes were cleared.", check(r = null).headline())
        // Every counter it doesn't report says N/A.
        val lines = check(r = null).lines()
        assertEquals(6, lines.size)
        assertTrue(lines.all { it.second == ClearedCheck.NA })
    }

    @Test fun notesMentionMonitorsAndTheLight() {
        val c = check(km = 18, warm = 3, milKm = 6, milMin = 9, r = evapPending)
        assertEquals(listOf(
            "1 readiness monitor is not finished yet. They finish over a few days of normal driving after a clear, so this fits a recent one.",
            "The check-engine light has been on for 6 km and 9 min since the codes were cleared, and is off now.",
        ), c.notes())
        // With the light on now, the second note isn't needed.
        val lit = check(km = 18, milKm = 6, r = Readiness.decode(intArrayOf(0x82, 0x07, 0x65, 0x00)))
        assertTrue(lit.notes().isEmpty())
    }

    @Test fun milesWhereChosen() {
        Units.mph = true
        assertEquals("Codes look recently cleared: only 62 mi and 3 warm-ups since.", check(km = 100, warm = 3).headline())
    }

    @Test fun reportSection() {
        val lines = check(km = 18, warm = 3, r = evapPending).reportLines()
        assertEquals("Codes recently cleared? Codes look recently cleared: only 18 km and 3 warm-ups since.", lines.first())
        assertTrue(lines.contains("  Engine running time since codes cleared: N/A"))
        assertEquals(ClearedCheck.CAVEAT, lines.last())
    }

    @Test fun countersFromAnswers() {
        assertEquals(3, ClearedCheck.counter(0x30, intArrayOf(3)))
        assertEquals(1284, ClearedCheck.counter(0x31, intArrayOf(0x05, 0x04)))
        assertEquals(null, ClearedCheck.counter(0x31, intArrayOf(0x05)))
        assertEquals(null, ClearedCheck.counter(0x4E, null))
    }

    @Test fun durations() {
        assertEquals("41 min", ClearedCheck.durationText(41))
        assertEquals("3 h", ClearedCheck.durationText(180))
        assertEquals("1,000 h", ClearedCheck.durationText(60_000))
    }

    @Test fun theDemoCarLooksRecentlyCleared() = runBlocking {
        val c = ElmClient(DemoTransport().apply { open() })
        val counters = ClearedCheck.PIDS.associateWith { pid ->
            val req = String.format("01%02X", pid)
            assertTrue(ReadOnlyGuard.isAllowed(req))
            ClearedCheck.counter(pid, ElmParser.extractData(c.command(req), "41" + req.substring(2)))
        }
        val r = Readiness.decode(ElmParser.extractData(c.command("0101"), "4101"))
        val check = ClearedCheck(counters[0x31], counters[0x30], counters[0x4E], counters[0x21], counters[0x4D], r)
        assertEquals("Codes look recently cleared: only 18 km and 3 warm-ups since.", check.headline())
    }
}
