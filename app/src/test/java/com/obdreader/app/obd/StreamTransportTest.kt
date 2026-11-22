package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The Bluetooth Classic and Wi-Fi links, on a fake adapter that answers one command at a time, as a
 * real ELM327 does, each reply after a set delay.
 */
class StreamTransportTest {

    /** Bytes the fake adapter has sent and the app hasn't read yet. */
    private class Incoming : InputStream() {
        private val bytes = ArrayDeque<Int>()

        @Synchronized fun add(text: String) = text.forEach { bytes.addLast(it.code) }
        @Synchronized override fun available(): Int = bytes.size
        @Synchronized override fun read(): Int = bytes.removeFirstOrNull() ?: -1
        @Synchronized override fun read(b: ByteArray, off: Int, len: Int): Int {
            var n = 0
            while (n < len && bytes.isNotEmpty()) b[off + n++] = bytes.removeFirst().toByte()
            return n
        }
    }

    /**
     * An adapter that takes commands in order and answers each one [delays] milliseconds after it
     * finished the previous one, with [replies] split into the given pieces.
     */
    private class FakeAdapter(
        private val replies: Map<String, List<String>>,
        private val delays: Map<String, Long>,
    ) : StreamTransport() {
        override val name = "fake"
        override val lateReplyWaitMs = 400L
        val incoming = Incoming()
        val sent = mutableListOf<String>()
        private val worker = Executors.newSingleThreadExecutor()

        override fun open() {
            input = incoming
            output = object : OutputStream() {
                private val line = StringBuilder()
                override fun write(b: Int) {
                    if (b.toChar() != '\r') { line.append(b.toChar()); return }
                    val cmd = line.toString().also { line.setLength(0) }
                    synchronized(sent) { sent += cmd }
                    // One command at a time: the next waits until this one is answered.
                    worker.execute {
                        Thread.sleep(delays[cmd] ?: 5)
                        for (piece in replies[cmd] ?: listOf("?\r\r>")) {
                            incoming.add(piece)
                            Thread.sleep(15)
                        }
                    }
                }
            }
        }

        override fun close() {
            worker.shutdownNow()
            worker.awaitTermination(1, TimeUnit.SECONDS)
            input = null
            output = null
        }
    }

    @Test
    fun aReplySplitAcrossSeveralReadsComesBackWhole() {
        val t = FakeAdapter(mapOf("010C" to listOf("41 0C", " 1A F8", "\r\r>")), emptyMap())
        t.open()
        try {
            assertEquals("41 0C 1A F8\r\r", t.exchange("010C", 2000))
        } finally {
            t.close()
        }
    }

    @Test
    fun noPromptMeansATimeout() {
        val t = FakeAdapter(mapOf("010C" to listOf("41 0C 1A F8\r\r")), emptyMap())
        t.open()
        val start = System.currentTimeMillis()
        try {
            t.exchange("010C", 300)
            fail("Expected a timeout")
        } catch (e: AdapterTimeoutException) {
            val took = System.currentTimeMillis() - start
            assertTrue("gave up after ${took} ms", took in 300..1500)
        } finally {
            t.close()
        }
    }

    @Test
    fun aLateReplyIsNotTakenForTheNextCommandsAnswer() {
        val t = FakeAdapter(
            mapOf("010D" to listOf("41 0D 32\r\r>"), "010C" to listOf("41 0C 1A F8\r\r>")),
            mapOf("010D" to 250L),
        )
        t.open()
        try {
            try {
                t.exchange("010D", 100)
                fail("Expected a timeout")
            } catch (expected: AdapterTimeoutException) {
            }
            // Speed's reply arrives late, while the app is already waiting for engine speed's.
            assertEquals("41 0C 1A F8\r\r", t.exchange("010C", 2000))
            assertEquals("41 0C 1A F8\r\r", t.exchange("010C", 2000))
        } finally {
            t.close()
        }
    }

    @Test
    fun aLateReplyThatNeverComesOnlyDelaysTheNextCommand() {
        val t = FakeAdapter(
            mapOf("010D" to listOf("41 0D 32\r\r"), "010C" to listOf("41 0C 1A F8\r\r>")),
            emptyMap(),
        )
        t.open()
        try {
            try {
                t.exchange("010D", 100)
                fail("Expected a timeout")
            } catch (expected: AdapterTimeoutException) {
            }
            val start = System.currentTimeMillis()
            assertEquals("41 0C 1A F8\r\r", t.exchange("010C", 2000))
            assertTrue(System.currentTimeMillis() - start < 2000)
        } finally {
            t.close()
        }
    }
}
