package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinPrivacyTest {

    // The well-known sample VIN from VIN documentation, not any real car of ours.
    private val vin = "1HGBH41JXMN109186"

    @Test fun hiddenByDefault() {
        assertEquals(VinPrivacy.Mode.HIDDEN, VinPrivacy.of(null))
        assertEquals(VinPrivacy.Mode.HIDDEN, VinPrivacy.of("nonsense"))
        assertEquals(VinPrivacy.Mode.PARTIAL, VinPrivacy.of("partial"))
        assertEquals("1HGBH41JXMN******", VinPrivacy.mask(vin, VinPrivacy.Mode.PARTIAL))
        assertEquals(vin, VinPrivacy.mask(vin, VinPrivacy.Mode.FULL))
        assertEquals("hidden", VinPrivacy.mask(vin, VinPrivacy.Mode.HIDDEN))
        assertEquals("****", VinPrivacy.mask("AB12", VinPrivacy.Mode.PARTIAL))
    }

    @Test fun scrubsKnownAndVinShapedText() {
        assertEquals("VIN: 1HGBH41JXMN******", VinPrivacy.scrub("VIN: $vin", VinPrivacy.Mode.PARTIAL, listOf(vin)))
        // Not one the app knows, but shaped like a VIN.
        assertEquals("module VIN=JM8YC5XXX5Y******", VinPrivacy.scrub("module VIN=JM8YC5XXX5Y123456", VinPrivacy.Mode.PARTIAL))
        // Hex readings are left alone, however long.
        assertEquals("41 0C 1A F8 0D 3C 0B 64 0F 4E", VinPrivacy.scrub("41 0C 1A F8 0D 3C 0B 64 0F 4E", VinPrivacy.Mode.HIDDEN))
        assertEquals("410C1AF80D3C0B640", VinPrivacy.scrub("410C1AF80D3C0B640", VinPrivacy.Mode.HIDDEN))
        assertEquals("VIN: $vin", VinPrivacy.scrub("VIN: $vin", VinPrivacy.Mode.FULL, listOf(vin)))
    }

    @Test fun adapterLogLosesTheVinReplies() {
        val log = listOf(
            "> 0902  < 014 | 0: 49 02 01 31 46 4D | 1: 43 55 39 33 5A 32 38 | 2: 4B 41 31 32 33 34 35",
            "= VIN: $vin",
            "> 22F190  < 62F190314D...",
            "> 010C  < 410C1AF8",
        )
        val out = VinPrivacy.scrubLog(log, VinPrivacy.Mode.PARTIAL, listOf(vin))
        assertEquals("> 0902  < (reply hidden: it holds the VIN)", out[0])
        assertEquals("= VIN: 1HGBH41JXMN******", out[1])
        assertEquals("> 22F190  < (reply hidden: it holds the VIN)", out[2])
        assertEquals("> 010C  < 410C1AF8", out[3])
        assertFalse(out.any { "109186" in it })
        assertTrue(VinPrivacy.scrubLog(log, VinPrivacy.Mode.FULL, listOf(vin)) == log)
    }

    @Test fun demoCarVinIsMaskedInItsOwnLog() = kotlinx.coroutines.runBlocking {
        val lines = mutableListOf<String>()
        val demoVin = VinDecoder.sampleVin(null, null)
        val c = ElmClient(DemoTransport(demoVin).apply { open() }) { lines += it }
        c.readVin()
        val out = VinPrivacy.scrubLog(lines, VinPrivacy.Mode.PARTIAL, listOf(demoVin))
        assertFalse(out.any { demoVin in it })
        assertFalse(out.any { demoVin.takeLast(6) in it })
    }
}
