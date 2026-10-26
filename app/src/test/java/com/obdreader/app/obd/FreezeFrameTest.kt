package com.obdreader.app.obd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeFrameTest {
    @Test
    fun theDemoCarsFreezeFrameIsReadAndDecoded() = runBlocking {
        val t = DemoTransport().apply { open() }
        val c = ElmClient(t) { }
        val f = c.readFreezeFrame()!!
        assertEquals("P0133", f.code)
        val byPid = f.readings.associate { it.first.pid to it.second }
        assertEquals(2140.0, byPid.getValue("0C"), 0.01)
        assertEquals(64.0, byPid.getValue("0D"), 0.01)
        assertEquals(88.0, byPid.getValue("05"), 0.01)
        assertEquals(12.5, byPid.getValue("07"), 0.5)
        assertTrue(f.fuelSystem!!.startsWith("Closed loop"))
        assertTrue(f.lines().any { it.startsWith("Long-term fuel trim (bank 1): +12") })
    }

    @Test
    fun noCodeMeansNoFreezeFrame() {
        assertNull(FreezeFrame.code(intArrayOf(0, 0)))
        assertNull(FreezeFrame.code(null))
        assertEquals("P0171", FreezeFrame.code(intArrayOf(0x01, 0x71)))
    }
}
