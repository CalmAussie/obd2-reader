package com.obdreader.app.obd

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Waiting out the ignition being off, on a fake clock. */
@OptIn(ExperimentalCoroutinesApi::class)
class IgnitionWaitTest {

    @Test
    fun turningTheKeyBringsLiveDataStraightBack() = runTest {
        // Battery at rest for a minute, then the key turns at 60 s: the voltage jumps and the car answers.
        val keyOnAt = 60_000L
        var probes = 0
        val readings = mutableListOf<Double>()
        IgnitionWait(
            now = { testScheduler.currentTime },
            limitMs = ReconnectPolicy.GIVE_UP_AFTER_MS,
            readVoltage = { if (testScheduler.currentTime >= keyOnAt) 12.0 else 12.6 },
            carAwake = { probes++; testScheduler.currentTime >= keyOnAt },
            onVoltage = { v, _ -> readings += v },
        ).run()
        // Back within the second the voltage moved, not at the next five-second check.
        assertEquals(keyOnAt, testScheduler.currentTime)
        // While off it only asked the car every five seconds.
        assertEquals((keyOnAt / ReconnectPolicy.WAKE_PROBE_MS).toInt(), probes)
        assertEquals(12.0, readings.last(), 0.0)
    }

    @Test
    fun withASteadyBatteryItAsksTheCarEveryFiveSeconds() = runTest {
        var probes = 0
        IgnitionWait(
            now = { testScheduler.currentTime },
            limitMs = ReconnectPolicy.GIVE_UP_AFTER_MS,
            readVoltage = { 12.5 },
            carAwake = { ++probes == 3 },
        ).run()
        assertEquals(3 * ReconnectPolicy.WAKE_PROBE_MS, testScheduler.currentTime)
    }

    @Test
    fun anAdapterThatStopsReplyingDoesntEndTheWait() = runTest {
        var probes = 0
        IgnitionWait(
            now = { testScheduler.currentTime },
            limitMs = ReconnectPolicy.GIVE_UP_AFTER_MS,
            readVoltage = { throw AdapterTimeoutException("No reply to 'ATRV'") },
            carAwake = { if (++probes < 2) throw AdapterTimeoutException("No reply to '0100'") else true },
        ).run()
        assertEquals(2, probes)
    }

    @Test
    fun aCarThatNeverWakesIsGivenUpOn() = runTest {
        try {
            IgnitionWait(
                now = { testScheduler.currentTime },
                limitMs = ReconnectPolicy.GIVE_UP_AFTER_MS,
                readVoltage = { 12.5 },
                carAwake = { false },
            ).run()
            fail("Expected it to give up")
        } catch (e: IOException) {
            assertEquals("The car didn't answer for 30 minutes.", e.message)
            assertTrue(testScheduler.currentTime >= ReconnectPolicy.GIVE_UP_AFTER_MS)
        }
    }

    @Test
    fun watchingTheBatteryOvernightWaitsADay() = runTest {
        try {
            IgnitionWait(
                now = { testScheduler.currentTime },
                limitMs = ReconnectPolicy.WATCH_GIVE_UP_AFTER_MS,
                readVoltage = { 12.5 },
                carAwake = { false },
            ).run()
            fail("Expected it to give up")
        } catch (e: IOException) {
            assertEquals("The car didn't answer for 24 hours.", e.message)
        }
    }
}
