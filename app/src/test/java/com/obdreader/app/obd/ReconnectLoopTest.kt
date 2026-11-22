package com.obdreader.app.obd

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The connection's reconnect behaviour, on a fake clock: link drops and comes back, giving up,
 * Disconnect while waiting, Bluetooth turned off. Each "link" is a number; each session is scripted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectLoopTest {

    private class NotAnAdapter : Exception("This device isn't an OBD2 adapter.")

    /** What each session does in turn: reach the car or not, then end with an error or not. */
    private data class Step(val reachesCar: Boolean, val error: Exception? = IOException("Link dropped"))

    private class Script(steps: List<Step>, val after: Step = Step(reachesCar = false)) {
        private val steps = ArrayDeque(steps)
        val sessions = mutableListOf<Int>()
        val closed = mutableListOf<Int>()
        val waits = mutableListOf<Boolean>()
        var links = 1
        var bluetoothOffFor = 0
        var retry = true

        suspend fun session(link: Int, connected: () -> Unit) {
            sessions += link
            val step = steps.removeFirstOrNull() ?: after
            if (step.reachesCar) connected()
            step.error?.let { throw it }
        }

        fun freshLink(): Int? = if (bluetoothOffFor > 0) { bluetoothOffFor--; null } else ++links
    }

    private fun TestScope.loop(s: Script, startedByApp: Boolean = false) = ReconnectLoop(
        now = { testScheduler.currentTime },
        mayRetry = { s.retry },
        startedByApp = startedByApp,
        neverWorks = { it is NotAnAdapter },
        freshLink = { s.freshLink() },
        waiting = { s.waits += it },
    )

    private suspend fun ReconnectLoop<Int>.runOn(s: Script) = run(1, { link, connected -> s.session(link, connected) }, { s.closed += it })

    @Test
    fun aLinkThatDropsMidDriveIsRetriedQuicklyOnAFreshLink() = runTest {
        val s = Script(listOf(Step(reachesCar = true)), after = Step(reachesCar = true, error = NotAnAdapter()))
        val end = loop(s).runOn(s)
        assertEquals(listOf(1, 2), s.sessions)
        assertEquals(listOf(1, 2), s.closed)
        assertEquals(listOf(false), s.waits)
        // The first retry comes half a second after the drop.
        assertEquals(ReconnectPolicy.delayFor(0), testScheduler.currentTime)
        assertTrue(end is ReconnectLoop.End.Stopped && end.error is NotAnAdapter)
    }

    @Test
    fun aCarThatStaysAwayIsGivenUpOnAfter30Minutes() = runTest {
        val s = Script(listOf(Step(reachesCar = true)))
        val end = loop(s).runOn(s)
        assertEquals(ReconnectLoop.End.GaveUp(everConnected = true), end)
        val t = testScheduler.currentTime
        assertTrue("gave up at $t ms", t >= ReconnectPolicy.GIVE_UP_AFTER_MS && t < ReconnectPolicy.GIVE_UP_AFTER_MS + 10_000)
        assertEquals(s.sessions.size, s.closed.size)
    }

    @Test
    fun reachingTheCarAgainRestartsThe30Minutes() = runTest {
        // Away for 20 minutes, back for a moment, then away again: it keeps trying past the first 30 minutes.
        val s = Script(listOf(Step(reachesCar = true)))
        val looping = loop(s)
        var reconnectedAt = -1L
        val end = looping.run(1, { link, connected ->
            s.sessions += link
            if (reconnectedAt < 0 && testScheduler.currentTime >= 20 * 60_000L) {
                reconnectedAt = testScheduler.currentTime
                connected()
            } else if (link == 1) {
                connected()
            }
            throw IOException("Link dropped")
        }, { s.closed += it })
        assertTrue(end is ReconnectLoop.End.GaveUp)
        assertTrue(testScheduler.currentTime >= reconnectedAt + ReconnectPolicy.GIVE_UP_AFTER_MS)
    }

    @Test
    fun anAutomaticStartThatNeverReachesTheCarGivesUpAfterThreeMinutes() = runTest {
        val s = Script(emptyList())
        val end = loop(s, startedByApp = true).runOn(s)
        assertEquals(ReconnectLoop.End.GaveUp(everConnected = false), end)
        val t = testScheduler.currentTime
        assertTrue("gave up at $t ms", t >= ReconnectPolicy.AUTO_START_GIVE_UP_MS && t < ReconnectPolicy.AUTO_START_GIVE_UP_MS + 10_000)
    }

    @Test
    fun aConnectTheUserTappedThatFailsIsNotRetried() = runTest {
        val s = Script(listOf(Step(reachesCar = false, error = IOException("Couldn't reach the adapter"))))
        val end = loop(s).runOn(s)
        assertEquals(listOf(1), s.sessions)
        assertEquals(listOf(1), s.closed)
        assertTrue(end is ReconnectLoop.End.Stopped && !end.everConnected && end.error?.message == "Couldn't reach the adapter")
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun aDeviceThatIsntAnAdapterIsNotRetriedEvenOnAnAutomaticStart() = runTest {
        val s = Script(listOf(Step(reachesCar = false, error = NotAnAdapter())))
        val end = loop(s, startedByApp = true).runOn(s)
        assertEquals(listOf(1), s.sessions)
        assertTrue(end is ReconnectLoop.End.Stopped)
    }

    @Test
    fun withReconnectingOffADroppedLinkEndsTheConnection() = runTest {
        val s = Script(listOf(Step(reachesCar = true)))
        s.retry = false
        val end = loop(s).runOn(s)
        assertEquals(listOf(1), s.sessions)
        assertTrue(end is ReconnectLoop.End.Stopped && end.everConnected)
    }

    @Test
    fun disconnectWhileWaitingStopsQuietly() = runTest {
        val s = Script(listOf(Step(reachesCar = true)))
        var end: ReconnectLoop.End? = null
        val job = launch { end = loop(s).runOn(s) }
        // The link drops; while the app waits for the car, the user taps Disconnect.
        runCurrent()
        advanceTimeBy(ReconnectPolicy.delayFor(0) + 100)
        val triedSoFar = s.sessions.size
        job.cancel()
        advanceTimeBy(60_000)
        assertTrue(job.isCancelled)
        assertNull(end)
        assertEquals(triedSoFar, s.sessions.size)
        assertEquals(s.sessions, s.closed)
    }

    @Test
    fun withBluetoothOffItWaitsForBluetoothThenCarriesOn() = runTest {
        val s = Script(listOf(Step(reachesCar = true)), after = Step(reachesCar = true, error = NotAnAdapter()))
        s.bluetoothOffFor = 2
        loop(s).runOn(s)
        assertEquals(listOf(false, true, true), s.waits)
        assertEquals(listOf(1, 2), s.sessions)
        assertFalse(s.closed.isEmpty())
    }
}
