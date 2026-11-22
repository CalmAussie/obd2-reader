package com.obdreader.app.obd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live readings several at a time, and the switch to one at a time for cars that refuse that. */
class LiveReadsTest {

    private val wanted = Pids.dashboard.filter { it.hex in setOf("0C", "0D", "05") }

    /** The demo car, except that it answers [multi] requests for several readings only if asked to. */
    private class Car(private val multi: Boolean, private val awake: Boolean = true) : ObdTransport {
        override val name = "fake"
        val asked = mutableListOf<String>()
        private val demo = DemoTransport().also { it.open() }

        override fun open() {}
        override fun close() = demo.close()

        override fun exchange(command: String, timeoutMs: Long): String {
            asked += command
            val severalReadings = command.startsWith("01") && command.length > 4
            return when {
                !awake -> "NO DATA\r\r"
                severalReadings && !multi -> "NO DATA\r\r"
                else -> demo.exchange(command, timeoutMs)
            }
        }
    }

    @Test
    fun aCarThatAnswersSeveralAtOnceIsAskedOnce() = runBlocking {
        val car = Car(multi = true)
        val reads = LiveReads(startMulti = true)
        val values = reads.read(ElmClient(car), wanted)
        assertTrue(reads.multi)
        assertEquals(wanted.map { it.hex }.toSet(), values.keys)
        assertTrue(values.values.all { it != null })
        assertEquals(1, car.asked.size)
    }

    @Test
    fun aCarThatRefusesSeveralAtOnceSwitchesToOneAtATime() = runBlocking {
        val car = Car(multi = false)
        val reads = LiveReads(startMulti = true)
        val c = ElmClient(car)
        val first = reads.read(c, wanted)
        assertFalse(reads.multi)
        assertEquals(wanted.map { it.hex }.toSet(), first.keys)
        assertTrue(first.values.all { it != null })
        // From then on every reading is asked for on its own.
        car.asked.clear()
        reads.read(c, wanted)
        assertEquals(wanted.map { "01" + it.hex }, car.asked)
    }

    @Test
    fun aQuietCarDoesntLookLikeOneThatRefusesSeveralAtOnce() = runBlocking {
        // Ignition off: nothing answers, single or several. Keep asking several at a time.
        val reads = LiveReads(startMulti = true)
        val values = reads.read(ElmClient(Car(multi = true, awake = false)), wanted)
        assertTrue(reads.multi)
        assertTrue(values.values.all { it == null })
    }

    @Test
    fun aCarThatIsntOnCanIsAskedOneAtATimeFromTheStart() = runBlocking {
        val car = Car(multi = true)
        val reads = LiveReads(startMulti = false)
        val values = reads.read(ElmClient(car), wanted)
        assertNotNull(values["0C"])
        assertEquals(wanted.map { "01" + it.hex }, car.asked)
    }
}
