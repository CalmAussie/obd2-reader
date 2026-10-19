package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectTest {

    @Test
    fun ignitionOffNeedsSeveralQuietCyclesInARow() {
        val w = IgnitionWatch(quietCycles = 3)
        assertFalse(w.cycle(answered = 0, asked = 4))
        assertFalse(w.cycle(answered = 0, asked = 4))
        assertFalse(w.cycle(answered = 1, asked = 4)) // one answer resets the count
        assertFalse(w.cycle(answered = 0, asked = 4))
        assertFalse(w.cycle(answered = 0, asked = 4))
        assertTrue(w.cycle(answered = 0, asked = 4))
    }

    @Test
    fun cyclesThatAskNothingDontCount() {
        val w = IgnitionWatch(quietCycles = 2)
        assertFalse(w.cycle(0, 0))
        assertFalse(w.cycle(0, 0))
        assertFalse(w.cycle(0, 3))
        assertTrue(w.cycle(0, 3))
    }

    @Test
    fun retriesStartQuickAndSettle() {
        assertEquals(500L, ReconnectPolicy.delayFor(0))
        assertTrue(ReconnectPolicy.delayFor(3) <= 2000L)
        assertEquals(5000L, ReconnectPolicy.delayFor(50))
        assertFalse(ReconnectPolicy.shouldGiveUp(0, 29 * 60 * 1000L))
        assertTrue(ReconnectPolicy.shouldGiveUp(0, 30 * 60 * 1000L))
    }

    @Test
    fun anAutomaticStartThatNeverReachedTheCarGivesUpSooner() {
        val limit = ReconnectPolicy.AUTO_START_GIVE_UP_MS
        assertFalse(ReconnectPolicy.shouldGiveUp(0, limit - 1, limit))
        assertTrue(ReconnectPolicy.shouldGiveUp(0, limit, limit))
        assertTrue(limit < ReconnectPolicy.GIVE_UP_AFTER_MS)
    }
}

class MultiPidTest {

    private val lengths = mapOf(0x0C to 2, 0x0D to 1, 0x11 to 1, 0x05 to 1)

    @Test
    fun singleFrameReply() {
        val r = ElmParser.parseMultiPid(listOf("410C1AF80D3C1180"), lengths)
        assertEquals(listOf(0x1A, 0xF8), r.getValue(0x0C).toList())
        assertEquals(listOf(0x3C), r.getValue(0x0D).toList())
        assertEquals(listOf(0x80), r.getValue(0x11).toList())
    }

    @Test
    fun multiFrameReplyIsJoined() {
        val lines = listOf("00A", "0:410C1AF80D3C", "1:11800573000000")
        val r = ElmParser.parseMultiPid(lines, lengths)
        assertEquals(4, r.size)
        assertEquals(listOf(0x73), r.getValue(0x05).toList())
    }

    @Test
    fun noDataGivesNothing() {
        assertTrue(ElmParser.parseMultiPid(listOf("NO DATA"), lengths).isEmpty())
    }

    @Test
    fun demoCarAnswersSeveralPidsAtOnce() {
        val demo = DemoTransport()
        demo.open()
        val raw = demo.exchange("010C0D11", 1000)
        val r = ElmParser.parseMultiPid(ElmParser.cleanLines(raw, "010C0D11"), lengths)
        assertEquals(setOf(0x0C, 0x0D, 0x11), r.keys)
    }
}
