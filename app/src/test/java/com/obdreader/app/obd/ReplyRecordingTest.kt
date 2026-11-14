package com.obdreader.app.obd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReplyRecordingTest {

    /** The sample VIN the demo car uses (not a real car's). */
    private val sampleVin = "1FAHP3F27CL123456"

    private fun record(block: suspend (ElmClient) -> Unit): ReplyRecording.Recording = runBlocking {
        val recorder = ReplyRecorder()
        val t = RecordingTransport(DemoTransport(sampleVin), recorder)
        t.open()
        block(ElmClient(t))
        t.close()
        ReplyRecording.Recording("2.5.3", "2026-11-13 10:42", "2012 Ford Focus", recorder.snapshot())
    }

    private fun parseFails(text: String, contains: String) {
        try {
            ReplyRecording.parse(text)
            fail("Expected the recording to be refused")
        } catch (e: ReplyRecording.BadRecording) {
            assertTrue(e.message, e.message!!.contains(contains))
        }
    }

    @Test
    fun recordsWhatTheCarSaid() {
        val rec = record { c ->
            c.initialize()
            c.command("010C")
            c.command("03")
        }
        assertTrue(rec.exchanges.any { it.command == "ATZ" })
        val codes = rec.exchanges.last { it.command == "03" }
        assertEquals(listOf("430201330420"), codes.lines)
    }

    @Test
    fun vinIsHiddenUnlessShownInFull() {
        val rec = record { c -> c.initialize(); c.command("0902") }
        val hidden = ReplyRecording.encode(rec, showVin = false)
        assertTrue(hidden.contains("> 0902\n< ${ReplyRecording.VIN_HIDDEN}\n"))
        assertFalse(hidden.contains(sampleVin))
        // The VIN in hex, as the reply spells it out.
        val hex = sampleVin.map { String.format("%02X", it.code) }.joinToString("")
        assertFalse(hidden.replace(" ", "").contains(hex.substring(0, 16)))

        val shown = ReplyRecording.encode(rec, showVin = true)
        assertFalse(shown.contains(ReplyRecording.VIN_HIDDEN))
    }

    @Test
    fun vinInAnyOtherReplyIsHiddenToo() {
        val vinHex = sampleVin.map { String.format("%02X", it.code) }.joinToString("")
        // A maker's own request that happens to answer with the VIN.
        assertTrue(ReplyRecording.holdsVin("22F1A0", listOf("62F1A0$vinHex"), emptyList()))
        // Split over CAN frames.
        val frames = listOf("014", "0: 62 F1 A0 " + vinHex.substring(0, 8), "1: " + vinHex.substring(8, 22), "2: " + vinHex.substring(22))
        assertTrue(ReplyRecording.holdsVin("22F1A0", frames, emptyList()))
        // The car's known VIN, even when it doesn't look like one to the pattern.
        assertTrue(ReplyRecording.holdsVin("2101", listOf("6101" + vinHex), listOf(sampleVin)))
        // With headers on, as the module search asks: 11-bit and 29-bit addresses before each frame.
        val v = vinHex.chunked(2)
        val h11 = listOf("7E8 10 14 62 F1 8C " + v.take(3).joinToString(" "), "7E8 21 " + v.subList(3, 10).joinToString(" "), "7E8 22 " + v.subList(10, 17).joinToString(" "))
        assertTrue(ReplyRecording.holdsVin("22F18C", h11, emptyList()))
        val h29 = h11.map { "18DAF110 " + it.removePrefix("7E8 ") }
        assertTrue(ReplyRecording.holdsVin("22F18C", h29, emptyList()))
        assertFalse(ReplyRecording.holdsVin("1902FF", listOf("7E8 07 59 02 FF C1 21 00 08"), listOf(sampleVin)))
        // Ordinary readings are kept.
        assertFalse(ReplyRecording.holdsVin("010C", listOf("410C1AF8"), listOf(sampleVin)))
        assertFalse(ReplyRecording.holdsVin("03", listOf("430201330420"), listOf(sampleVin)))
        assertFalse(ReplyRecording.holdsVin("ATI", listOf("ELM327 v1.5"), listOf(sampleVin)))
    }

    @Test
    fun moduleSerialNumbersAreHidden() {
        val rec = ReplyRecording.Recording(null, null, null, listOf(
            ReplyRecording.Exchange("ATSH7E0", listOf("OK")),
            ReplyRecording.Exchange("22F18C", listOf("62F18C3132333435363738")),
            ReplyRecording.Exchange("22F187", listOf("62F18741424331")),
        ))
        val hidden = ReplyRecording.encode(rec, showVin = false)
        assertTrue(hidden.contains("> 22F18C\n< ${ReplyRecording.SERIAL_HIDDEN}\n"))
        assertTrue(hidden.contains("< 62F18741424331"))
        assertTrue(ReplyRecording.encode(rec, showVin = true).contains("< 62F18C3132333435363738"))
        val r = ReplayTransport(ReplyRecording.parse(hidden), "test")
        r.open()
        r.exchange("ATSH7E0", 1000)
        assertEquals(listOf("NO DATA"), ElmParser.cleanLines(r.exchange("22F18C", 1000), "22F18C"))
    }

    @Test
    fun aMillionEmptyLinesIsJustEmpty() {
        parseFails(ReplyRecording.HEADER + "\n".repeat(ReplyRecording.MAX_BYTES - ReplyRecording.HEADER.length - 1), "empty")
        // A bad line stops it there, however long the file is.
        parseFails("${ReplyRecording.HEADER}\nbad\n" + "> 010C\n".repeat(100_000), "Line 2 isn't part of a recording")
    }

    @Test
    fun roundTripsThroughText() {
        val rec = record { c ->
            c.initialize()
            c.command("0101")
            c.command("ATSH7E0")
            c.command("03")
        }
        val text = ReplyRecording.encode(rec, showVin = false)
        val back = ReplyRecording.parse(text)
        assertEquals("2.5.3", back.app)
        assertEquals("2026-11-13 10:42", back.made)
        assertEquals("2012 Ford Focus", back.car)
        assertEquals(rec.exchanges, back.exchanges)
    }

    @Test
    fun noReplyIsKept() {
        val rec = ReplyRecording.Recording(null, null, null, listOf(ReplyRecording.Exchange("03", null), ReplyRecording.Exchange("07", emptyList())))
        val text = ReplyRecording.encode(rec, showVin = false)
        assertTrue(text.contains("> 03\n${ReplyRecording.NO_REPLY}\n> 07\n"))
        assertEquals(rec.exchanges, ReplyRecording.parse(text).exchanges)
    }

    @Test
    fun replayAnswersLikeTheCar() = runBlocking {
        val rec = record { c ->
            c.initialize()
            c.command("0101")
            c.command("03")
            c.command("0A")
            c.command("0902")
        }
        val replay = ReplayTransport(ReplyRecording.parse(ReplyRecording.encode(rec, showVin = false)), "test")
        replay.open()
        val c = ElmClient(replay)
        val info = c.initialize()
        assertTrue(info.summary, info.summary.contains("ELM327"))
        assertEquals(listOf("410182076504"), c.command("0101"))
        assertEquals(listOf("430201330420"), c.command("03"))
        assertEquals(listOf("4A010420"), c.command("0A"))
        // The VIN was hidden in the file, so replay has none to give.
        assertEquals(listOf("NO DATA"), c.command("0902"))
        // Something the car was never asked.
        assertEquals(listOf("NO DATA"), c.command("0105"))
        replay.close()
    }

    @Test
    fun replayGoesThroughEachRecordedReplyInTurn() {
        val rec = ReplyRecording.Recording(null, null, null, listOf(
            ReplyRecording.Exchange("010D", listOf("410D10")),
            ReplyRecording.Exchange("010D", listOf("410D20")),
            ReplyRecording.Exchange("010D", listOf("410D30")),
        ))
        val r = ReplayTransport(rec, "test")
        r.open()
        val got = (1..4).map { ElmParser.cleanLines(r.exchange("010D", 1000), "010D") }
        assertEquals(listOf(listOf("410D10"), listOf("410D20"), listOf("410D30"), listOf("410D10")), got)
    }

    @Test
    fun replayMatchesTheModuleAsked() {
        val rec = ReplyRecording.Recording(null, null, null, listOf(
            ReplyRecording.Exchange("ATSH7E0", listOf("OK")),
            ReplyRecording.Exchange("1902FF", listOf("5902FF")),
            ReplyRecording.Exchange("ATSH760", listOf("OK")),
            ReplyRecording.Exchange("1902FF", listOf("5902FFC1210008")),
        ))
        val r = ReplayTransport(rec, "test")
        r.open()
        r.exchange("ATSH760", 1000)
        assertEquals(listOf("5902FFC1210008"), ElmParser.cleanLines(r.exchange("1902FF", 1000), "1902FF"))
        r.exchange("ATSH7E0", 1000)
        assertEquals(listOf("5902FF"), ElmParser.cleanLines(r.exchange("1902FF", 1000), "1902FF"))
        // A module that wasn't in the recording.
        r.exchange("ATSH7E2", 1000)
        assertEquals(listOf("NO DATA"), ElmParser.cleanLines(r.exchange("1902FF", 1000), "1902FF"))
    }

    @Test
    fun replayPutsSeveralReadingsTogether() {
        val rec = ReplyRecording.Recording(null, null, null, listOf(
            ReplyRecording.Exchange("010C", listOf("41 0C 1A F8")),
            ReplyRecording.Exchange("010D", listOf("410D40")),
        ))
        val r = ReplayTransport(rec, "test")
        r.open()
        assertEquals(listOf("410C1AF80D40"), ElmParser.cleanLines(r.exchange("010C0D05", 1000), "010C0D05"))
        assertEquals(listOf("NO DATA"), ElmParser.cleanLines(r.exchange("010511", 1000), "010511"))
    }

    @Test
    fun replayOfNoReplyTimesOut() {
        val rec = ReplyRecording.Recording(null, null, null, listOf(ReplyRecording.Exchange("03", null)))
        val r = ReplayTransport(rec, "test")
        r.open()
        try {
            r.exchange("03", 1000)
            fail("Expected a timeout")
        } catch (e: AdapterTimeoutException) {
        }
    }

    @Test
    fun refusesFilesThatArentRecordings() {
        parseFails("hello\n> 010C\n< 410C1AF8\n", "isn't an OBD2 Reader recording")
        parseFails("", "isn't an OBD2 Reader recording")
        parseFails("${ReplyRecording.HEADER}\n", "empty")
        parseFails("${ReplyRecording.HEADER}\n< 410C1AF8\n", "no request before it")
        parseFails("${ReplyRecording.HEADER}\n> 010C\nrm -rf /\n", "isn't part of a recording")
        parseFails("${ReplyRecording.HEADER}\n> 010C\n< 41\u0000\n", "isn't plain text")
        parseFails("${ReplyRecording.HEADER}\n> 010C\n< é\n", "isn't plain text")
        parseFails("${ReplyRecording.HEADER}\n> 010C\n# app 1\n", "notes belong at the top")
        parseFails("${ReplyRecording.HEADER}\n> 01;0C\n", "isn't a request the app sends")
        parseFails("${ReplyRecording.HEADER}\n> ${"0".repeat(30)}\n", "isn't a request the app sends")
        parseFails("${ReplyRecording.HEADER}\n> 03\n< 43\n${ReplyRecording.NO_REPLY}\n", "has to follow a request")
    }

    @Test
    fun refusesRequestsThatWouldChangeTheCar() {
        for (cmd in listOf("04", "14FFFFFF", "2EF190", "3101FF00", "1101", "1003", "08", "STPX")) {
            parseFails("${ReplyRecording.HEADER}\n> $cmd\n< OK\n", "only reads")
        }
    }

    @Test
    fun refusesFilesOverTheLimits() {
        parseFails("${ReplyRecording.HEADER}\n> 010C\n< ${"4".repeat(400)}\n", "too long")
        val longReply = "${ReplyRecording.HEADER}\n> 0902\n" + "< 49\n".repeat(ReplyRecording.MAX_REPLY_LINES + 1)
        parseFails(longReply, "more than ${ReplyRecording.MAX_REPLY_LINES} lines")
        val many = "${ReplyRecording.HEADER}\n" + "> 010C\n< 410C1AF8\n".repeat(ReplyRecording.MAX_EXCHANGES + 1)
        parseFails(many, "more than 20,000 requests")
        parseFails(ReplyRecording.HEADER + "\n" + "#".repeat(ReplyRecording.MAX_BYTES), "larger than 2 MB")
    }

    @Test
    fun notesAreCleanedAndCut() {
        val rec = ReplyRecording.parse("${ReplyRecording.HEADER}\n# car ${"x".repeat(200)}\n# other ignored\n> 010C\n< 410C1AF8\n")
        assertNotNull(rec.car)
        assertEquals(80, rec.car!!.length)
    }

    @Test
    fun recorderStopsWhenFull() {
        val r = ReplyRecorder()
        repeat(ReplyRecording.MAX_EXCHANGES + 10) { r.add("010C", listOf("410C1AF8")) }
        assertEquals(ReplyRecording.MAX_EXCHANGES, r.size)
        assertTrue(r.full)
        val big = ReplyRecorder()
        repeat(5000) { big.add("0902", List(64) { "4".repeat(200) }) }
        assertTrue(big.full)
        assertTrue(ReplyRecording.encode(ReplyRecording.Recording(null, null, null, big.snapshot()), showVin = true).length <= ReplyRecording.MAX_BYTES)
    }

    @Test
    fun recorderKeepsOnlyReads() {
        val r = ReplyRecorder()
        r.add("04", listOf("44"))
        r.add("14FFFFFF", listOf("54"))
        r.add("010C", listOf("410C1AF8"))
        assertEquals(listOf("010C"), r.snapshot().map { it.command })
    }
}
