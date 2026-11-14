package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
import kotlin.math.abs

class DayNightTest {

    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y, mo - 1, d, h, mi) }.timeInMillis

    @Test fun middayIsDayMidnightIsNight() {
        // Melbourne (37.8 S, 145.0 E): 12:00 local is 02:00 UTC in November (daylight saving, UTC+11).
        assertFalse(DayNight.isNightBySun(utc(2026, 11, 13, 2, 0), -37.8, 145.0))
        assertTrue(DayNight.isNightBySun(utc(2026, 11, 13, 13, 0), -37.8, 145.0))
        // London at noon and midnight in winter.
        assertFalse(DayNight.isNightBySun(utc(2026, 12, 21, 12, 0), 51.5, -0.13))
        assertTrue(DayNight.isNightBySun(utc(2026, 12, 21, 0, 0), 51.5, -0.13))
    }

    @Test fun sunsetTimesAreClose() {
        // London's sunset on 21 June is about 21:21 BST (20:21 UTC); 21 December about 15:53 UTC.
        val june = DayNight.nextChange(utc(2026, 6, 21, 12, 0), 51.5, -0.13)!!
        assertTrue("June sunset ${(june - utc(2026, 6, 21, 20, 21)) / 60_000} min off", abs(june - utc(2026, 6, 21, 20, 21)) < 6 * 60_000)
        val dec = DayNight.nextChange(utc(2026, 12, 21, 12, 0), 51.5, -0.13)!!
        assertTrue("December sunset ${(dec - utc(2026, 12, 21, 15, 53)) / 60_000} min off", abs(dec - utc(2026, 12, 21, 15, 53)) < 6 * 60_000)
        // Then sunrise next: London 22 December about 08:05 UTC.
        val rise = DayNight.nextChange(dec + 60_000, 51.5, -0.13)!!
        assertTrue(abs(rise - utc(2026, 12, 22, 8, 5)) < 6 * 60_000)
    }

    @Test fun midnightSunHasNoChange() {
        // Tromsø (69.6 N) in late June: the sun doesn't set.
        val t = utc(2026, 6, 21, 23, 0)
        assertFalse(DayNight.isNightBySun(t, 69.6, 18.9))
        assertNull(DayNight.nextChange(t, 69.6, 18.9))
    }

    @Test fun timeZoneGuess() {
        val mel = DayNight.guessFromTimeZone(TimeZone.getTimeZone("Australia/Melbourne"))
        assertEquals(150.0, mel.longitude, 0.01)
        assertTrue(mel.latitude < 0)
        assertFalse(mel.exact)
        assertTrue(DayNight.typicalLatitude("Europe/Berlin") > 40)
        assertTrue(DayNight.typicalLatitude("America/Sao_Paulo") < 0)
        assertTrue(DayNight.typicalLatitude("Pacific/Auckland") < 0)
        assertEquals(0.0, DayNight.typicalLatitude("UTC"), 0.0)
    }

    @Test fun styleChoice() {
        assertEquals("kerb", DayNight.styleFor(DayNight.Mode.OFF, "kerb", "pitlane", "telemetry", true))
        assertEquals("telemetry", DayNight.styleFor(DayNight.Mode.SUN, "kerb", "pitlane", "telemetry", true))
        assertEquals("pitlane", DayNight.styleFor(DayNight.Mode.LIGHT, "kerb", "pitlane", "telemetry", false))
        // Night style not picked yet: the chosen style.
        assertEquals("kerb", DayNight.styleFor(DayNight.Mode.PHONE, "kerb", "pitlane", null, true))
        assertEquals(DayNight.Mode.OFF, DayNight.mode(null))
        assertEquals(DayNight.Mode.LIGHT, DayNight.mode("light"))
    }

    @Test fun lightSensorWaitsBeforeSwitching() {
        val s = DayNight.LightSwitch(darkLux = 10f, brightLux = 60f, holdMs = 30_000)
        assertNull(s.isNight)
        assertEquals(false, s.feed(400f, 0))
        // A tunnel: dark for 20 s, then bright again. Stays day.
        assertEquals(false, s.feed(3f, 1_000))
        assertEquals(false, s.feed(3f, 21_000))
        assertEquals(false, s.feed(500f, 22_000))
        // Dusk: dark for over 30 s.
        assertEquals(false, s.feed(4f, 100_000))
        assertEquals(false, s.feed(4f, 120_000))
        assertEquals(true, s.feed(4f, 130_000))
        // In between (30 lux) changes nothing either way.
        assertEquals(true, s.feed(30f, 200_000))
        assertEquals(true, s.feed(30f, 400_000))
        // Headlights for a moment don't count.
        assertEquals(true, s.feed(900f, 401_000))
        assertEquals(true, s.feed(5f, 402_000))
        assertEquals(true, s.feed(900f, 440_000))
        // A dark first reading starts as night.
        assertNotNull(DayNight.LightSwitch().feed(2f, 0)?.takeIf { it })
    }
}
