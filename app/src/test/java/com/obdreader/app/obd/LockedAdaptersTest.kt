package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedAdaptersTest {

    @Test
    fun recognisesThinkDiagNames() {
        for (name in listOf("ThinkDiag", "THINKDIAG2", "Thinkcar-1234", "THINK_DIAG")) {
            assertEquals(name, "ThinkDiag", LockedAdapters.match(name)?.brand)
        }
        assertEquals("BlueDriver", LockedAdapters.match("BlueDriver 5A3F")?.brand)
    }

    @Test
    fun leavesElm327AdaptersAlone() {
        for (name in listOf("OBDII", "OBDLink MX+", "Veepeak", "vLinker MC-Android", "IOS-Vlink", "V-LINK", null)) {
            assertNull(name, LockedAdapters.match(name))
        }
    }

    @Test
    fun messageNamesTheAppAndTheFix() {
        val m = LockedAdapters.message(LockedAdapters.match("ThinkDiag")!!)
        assertTrue(m.contains("ThinkCar") && m.contains("ELM327-compatible"))
    }
}
