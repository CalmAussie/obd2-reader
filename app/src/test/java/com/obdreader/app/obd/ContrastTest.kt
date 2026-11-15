package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val pitLanePage = 0xFFEEEFF1.toInt()

    @Test fun blackOnWhiteIs21() {
        assertEquals(21.0, Contrast.ratio(black, white), 0.01)
        assertEquals(1.0, Contrast.ratio(white, white), 0.001)
    }

    @Test fun matchesKnownValues() {
        // The audit's numbers: throttle green 1.99:1 and amber 1.83:1 on white.
        assertEquals(1.99, Contrast.ratio(0xFF34D17A.toInt(), white), 0.01)
        assertEquals(1.83, Contrast.ratio(0xFFFFB000.toInt(), white), 0.01)
    }

    @Test fun darkensGraphColoursUntilTheyStandOutOnLightStyles() {
        for (c in listOf(0xFF34D17A, 0xFFFFB000, 0xFFE6E15A, 0xFF3D9BFF, 0xFFFF3B30, 0xFF34C8E0)) {
            val out = Contrast.darkenTo(c.toInt(), intArrayOf(white, pitLanePage))
            assertTrue("%08X".format(c), Contrast.ratio(out, white) >= 3.0 && Contrast.ratio(out, pitLanePage) >= 3.0)
        }
    }

    @Test fun leavesColoursThatAlreadyPassAlone() {
        val navy = 0xFF1F3A93.toInt()
        assertEquals(navy, Contrast.darkenTo(navy, intArrayOf(white)))
    }
}
