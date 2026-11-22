package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import kotlin.concurrent.thread

/** The reply buffer the Bluetooth LE link uses: replies arrive in pieces on another thread. */
class PromptBufferTest {

    private fun sendLater(b: PromptBuffer, delayMs: Long, vararg pieces: String) = thread {
        Thread.sleep(delayMs)
        for (p in pieces) {
            b.append(p.toByteArray(Charsets.US_ASCII))
            Thread.sleep(10)
        }
    }

    @Test
    fun aReplyInPiecesComesBackWhole() {
        val b = PromptBuffer(lateReplyWaitMs = 400)
        b.prepare { null }
        sendLater(b, 20, "41 0C", " 1A F8\r", "\r>")
        assertEquals("41 0C 1A F8\r\r", b.awaitReply("010C", 2000) { null })
    }

    @Test(expected = AdapterTimeoutException::class)
    fun noPromptMeansATimeout() {
        val b = PromptBuffer(lateReplyWaitMs = 400)
        b.prepare { null }
        sendLater(b, 20, "41 0C 1A F8\r\r")
        b.awaitReply("010C", 300) { null }
    }

    @Test
    fun aLateReplyIsDroppedBeforeTheNextCommand() {
        val b = PromptBuffer(lateReplyWaitMs = 400)
        b.prepare { null }
        try {
            b.awaitReply("010D", 100) { null }
            fail("Expected a timeout")
        } catch (expected: AdapterTimeoutException) {
        }
        // Speed's reply turns up after the app gave up on it.
        val late = sendLater(b, 150, "41 0D 32\r\r>")
        b.prepare { null }
        late.join()
        sendLater(b, 10, "41 0C 1A F8\r\r>")
        assertEquals("41 0C 1A F8\r\r", b.awaitReply("010C", 2000) { null })
    }

    @Test
    fun aDroppedLinkStopsTheWait() {
        val b = PromptBuffer(lateReplyWaitMs = 400)
        var failure: String? = null
        thread {
            Thread.sleep(50)
            failure = "Bluetooth LE link dropped"
            b.wake()
        }
        try {
            b.awaitReply("010C", 5000) { failure }
            fail("Expected the link to drop")
        } catch (e: IOException) {
            assertEquals("Bluetooth LE link dropped", e.message)
        }
    }
}
