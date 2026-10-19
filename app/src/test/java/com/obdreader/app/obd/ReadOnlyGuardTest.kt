package com.obdreader.app.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyGuardTest {

    @Test
    fun everythingTheAppSendsIsAllowed() {
        listOf(
            "ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATI", "ATSP0", "ATSP6", "ATPC", "ATDPN", "ATDP", "ATRV",
            "0100", "0120", "0140", "010C", "010C0D11040B10", "03", "07", "0902",
        ).forEach { assertTrue(it, ReadOnlyGuard.isAllowed(it)) }
    }

    @Test
    fun anythingThatCouldChangeTheCarIsBlocked() {
        listOf(
            "04",          // clear trouble codes and the check-engine light
            "08",          // control on-board systems
            "14FFFFFF",    // UDS clear diagnostic information
            "2EF190",      // UDS write data
            "3101FF00",    // UDS routine control
            "1101",        // UDS ECU reset
            "1003",        // UDS session change
            "ATWM8110F101", // adapter wake-up message, sent to the car
            "", "hello",
        ).forEach { assertFalse(it, ReadOnlyGuard.isAllowed(it)) }
    }

    @Test
    fun spacesAndLowerCaseDontGetRoundIt() {
        assertFalse(ReadOnlyGuard.isAllowed(" 0 4 "))
        assertTrue(ReadOnlyGuard.isAllowed("01 0c"))
    }
}
