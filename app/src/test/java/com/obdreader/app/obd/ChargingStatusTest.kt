package com.obdreader.app.obd

import com.obdreader.app.obd.ChargingStatus.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargingStatusTest {

    @Test
    fun runningEngineByVoltage() {
        assertEquals(State.CHARGING, ChargingStatus.of(14.2, 800.0))
        assertEquals(State.WEAK, ChargingStatus.of(12.9, 2000.0))
        assertEquals(State.NOT_CHARGING, ChargingStatus.of(12.4, 800.0))
        assertEquals(State.OVERCHARGING, ChargingStatus.of(15.4, 800.0))
    }

    @Test
    fun engineOff() {
        assertEquals(State.ENGINE_OFF, ChargingStatus.of(12.6, 0.0))
        assertEquals(State.ENGINE_OFF_LOW, ChargingStatus.of(11.9, 0.0))
    }

    @Test
    fun noGuessWithoutBothReadings() {
        assertNull(ChargingStatus.of(null, 800.0))
        assertNull(ChargingStatus.of(14.0, null))
    }

    @Test
    fun labelSaysItsAnEstimate() {
        assertEquals("Battery · charging (est.)", ChargingStatus.label(State.CHARGING))
        assertEquals("Battery · charging", ChargingStatus.label(State.CHARGING, roomy = false))
        assertEquals("Battery", ChargingStatus.label(null))
    }

    @Test
    fun watchWaitsBeforeChanging() {
        val w = ChargingStatus.Watch()
        assertNull(w.update(0, 14.2, 800.0))
        assertNull(w.update(4_000, 14.2, 800.0))
        assertEquals(State.CHARGING, w.update(5_000, 14.2, 800.0))
        // A short dip doesn't flip it to "not charging".
        assertEquals(State.CHARGING, w.update(6_000, 12.3, 800.0))
        assertEquals(State.CHARGING, w.update(10_000, 14.1, 800.0))
        // A dip that lasts 20 seconds does.
        w.update(11_000, 12.3, 800.0)
        assertEquals(State.CHARGING, w.update(30_000, 12.3, 800.0))
        assertEquals(State.NOT_CHARGING, w.update(31_000, 12.3, 800.0))
        // Losing a reading clears it rather than keeping an old status.
        assertNull(w.update(32_000, null, 800.0))
    }
}
