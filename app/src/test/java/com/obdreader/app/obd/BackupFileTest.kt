package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupFileTest {

    // The well-known sample VIN from VIN documentation, not any real car of ours.
    private val vin = "1HGBH41JXMN109186"

    private val routeDrive = "Time,Seconds,Engine speed (rpm),\"Fuel trim, short (%)\",Latitude,Longitude,Location accuracy (m)\n" +
        "2026-11-13 08:00:00.000,0.2,812.00,1.50,-37.814100,144.963300,5\n" +
        "2026-11-13 08:00:00.200,0.4,820.00,,,,\n"

    private val settings: Map<String, Any?> = mapOf(
        "theme" to "telemetry", "style_auto" to "sun", "style_day" to "pitlane", "style_night" to "telemetry",
        "car_make" to "Mazda", "car_year" to 2006, "car_model" to "Tribute", "car_engine" to "2.3 L",
        "units_mph" to false, "alerts" to true, "wifi_port" to 35000, "wifi_host" to "192.168.0.10",
        "protocol" to "0", "vin_privacy" to "hidden", "user_vin" to vin, "user_vin_car" to "2006 Mazda Tribute",
        "custom_readings" to "Trans temp\u001F221E1C\u001F7E0\u001F(A*256+B)/16\u001F°C",
        "trip_history" to "start=1000;end=2000;km=12.0;drive=900;conn=1000;idle=60",
        "throttle_closed_2006 Mazda Tribute" to 14.5f, "group_car" to true,
        // Per-phone state: never in a backup.
        "update_last_check" to 123L, "last_address" to "00:11:22:33:44:55", "widget_codes" to "0|0|1",
    )

    private fun tmp(): File = Files.createTempDirectory("backup").toFile()

    private fun make(includeRoutes: Boolean, includeVin: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        BackupFile.write(
            out, settings,
            listOf(BackupFile.Drive("OBD2Reader_2026-11-13_080000.csv") { routeDrive.byteInputStream() }),
            "2.5.2", 1_700_000_000_000L, includeRoutes, includeVin,
        )
        return out.toByteArray()
    }

    @Test fun roundTrip() {
        val dir = tmp()
        val c = BackupFile.read(ByteArrayInputStream(make(includeRoutes = true, includeVin = true)), dir)
        assertEquals(1, c.info.format)
        assertEquals("2.5.2", c.info.appVersion)
        assertTrue(c.info.hasRoutes)
        assertTrue(c.info.hasVin)
        assertEquals("telemetry", c.value("theme"))
        assertEquals(2006, c.value("car_year"))
        assertEquals(false, c.value("units_mph"))
        assertEquals(14.5f, c.value("throttle_closed_2006 Mazda Tribute"))
        assertEquals(vin, c.value("user_vin"))
        assertTrue((c.value("custom_readings") as String).contains("221E1C"))
        assertFalse(c.has("update_last_check"))
        assertFalse(c.has("last_address"))
        assertFalse(c.has("widget_codes"))
        assertEquals(0, c.skipped)
        assertEquals(listOf("OBD2Reader_2026-11-13_080000.csv"), c.drives.map { it.name })
        assertEquals(routeDrive, c.drives.single().readText())
    }

    @Test fun vinAndRoutesLeftOutWhenAsked() {
        val c = BackupFile.read(ByteArrayInputStream(make(includeRoutes = false, includeVin = false)), tmp())
        assertFalse(c.info.hasRoutes)
        assertFalse(c.info.hasVin)
        assertFalse(c.has("user_vin"))
        assertFalse(c.has("user_vin_car"))
        val drive = c.drives.single().readText()
        assertFalse(drive.contains("Latitude"))
        assertFalse(drive.contains("-37.8"))
        assertEquals(
            "Time,Seconds,Engine speed (rpm),\"Fuel trim, short (%)\"\n2026-11-13 08:00:00.000,0.2,812.00,1.50\n2026-11-13 08:00:00.200,0.4,820.00,\n",
            drive,
        )
        // And the VIN isn't anywhere in the file's bytes.
        assertFalse(String(make(false, false), Charsets.ISO_8859_1).contains(vin))
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
        return out.toByteArray()
    }

    private val info = "OBD2 Reader backup\nformat=1\napp=2.5.2\n"

    private fun rejects(bytes: ByteArray, words: String) {
        val dir = tmp()
        try {
            BackupFile.read(ByteArrayInputStream(bytes), dir)
            fail("should have been refused")
        } catch (e: BackupFile.BadBackup) {
            assertTrue("'${e.message}' should mention '$words'", e.message!!.contains(words))
        }
        assertEquals("nothing left behind", 0, dir.listFiles()!!.size)
    }

    @Test fun refusesWhatTheAppDidntMake() {
        rejects("not a zip at all".toByteArray(), "isn't an OBD2 Reader backup")
        rejects(zip("settings.txt" to "", "backup.txt" to info), "isn't an OBD2 Reader backup")
        rejects(zip("backup.txt" to "Something else\nformat=1\n", "settings.txt" to ""), "isn't an OBD2 Reader backup")
        rejects(zip("backup.txt" to "OBD2 Reader backup\nformat=2\n", "settings.txt" to ""), "newer version")
        rejects(zip("backup.txt" to info), "missing its settings")
        rejects(zip("backup.txt" to info, "settings.txt" to "", "drives/../../shared_prefs/obd_reader.xml" to "x"), "didn't make")
        rejects(zip("backup.txt" to info, "settings.txt" to "", "drives/evil.sh" to "x"), "didn't make")
        rejects(zip("backup.txt" to info, "settings.txt" to "", "classes.dex" to "x"), "doesn't know")
        // The same name twice (Java won't write that, so the second name is changed in the bytes).
        val twice = String(zip("backup.txt" to info, "settings.txt" to "", "settings.txX" to ""), Charsets.ISO_8859_1).replace("settings.txX", "settings.txt")
        rejects(twice.toByteArray(Charsets.ISO_8859_1), "twice")
        rejects(zip("backup.txt" to info, "settings.txt" to "", "drives/OBD2Reader_2026-11-13_080000.csv" to "not a drive"), "isn't a recorded drive")
        rejects(zip("backup.txt" to info, "settings.txt" to "", "drives/OBD2Reader_2026-11-13_080000.csv" to "Time,Seconds,x\n\u0000\u0001"), "isn't a recorded drive")
    }

    @Test fun refusesOversizedEntries() {
        // A zip bomb: a small file that unpacks to more than the settings may be.
        val big = ByteArrayOutputStream()
        ZipOutputStream(big).use { z ->
            z.putNextEntry(ZipEntry("backup.txt")); z.write(info.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("settings.txt"))
            val zeros = ByteArray(1024 * 1024) { 'a'.code.toByte() }
            repeat(5) { z.write(zeros) }
            z.closeEntry()
        }
        assertTrue(big.size() < 100_000)
        rejects(big.toByteArray(), "bigger than")
    }

    private fun one(key: String, value: Any): Any? = BackupFile.check(key, value)

    @Test fun settingsAreChecked() {
        // Custom readings must still be reads: a "clear codes" (04) or "write" (2E) one is dropped.
        assertNull(one("custom_readings", "Clear\u001F04\u001F\u001FA\u001F"))
        assertNull(one("custom_readings", "Write\u001F2EF190\u001F7E0\u001FA\u001F"))
        assertEquals("Ok\u001F221E1C\u001F7E0\u001FA\u001Fx\nOk2\u001F010C\u001F\u001FA\u001Frpm",
            one("custom_readings", "Ok\u001F221E1C\u001F7E0\u001FA\u001Fx\nBad\u001F3101\u001F\u001FA\u001F\nOk2\u001F010C\u001F\u001FA\u001Frpm"))
        // Protocol: only the adapter's own protocol numbers, never a command hidden after a line break.
        assertEquals("6", one("protocol", "6"))
        assertNull(one("protocol", "6\rATWM0000"))
        assertNull(one("protocol", "Z"))
        assertNull(one("wifi_host", "10.0.0.1\nATZ"))
        assertNull(one("wifi_port", 70000))
        assertNull(one("car_year", "2006"))           // wrong type
        assertNull(one("theme", "../../x"))
        assertNull(one("user_vin", "NOT A VIN"))
        assertNull(one("vin_privacy", "everyone"))
        assertNull(one("style_auto", "always"))
        assertNull(one("car_model", "Tribute\u0000"))
        assertNull(one("car_model", "x".repeat(81)))
        assertNull(one("throttle_closed_x", Float.NaN))
        assertNull(one("unknown_setting", "x"))
        assertNull(one("update_installing_build", 120))
        assertEquals(true, one("group_car", true))
        assertNull(one("group_../x", true))
    }

    @Test fun unknownAndBrokenSettingsAreSkipped() {
        val s = listOf(
            BackupFile.encodeSetting(BackupFile.Setting("theme", "kerb")),
            BackupFile.encodeSetting(BackupFile.Setting("something_new", "x")),
            BackupFile.encodeSetting(BackupFile.Setting("protocol", "6\rATMA")),
            "s\ttheme\t!!!not base64",
            "nonsense",
        ).joinToString("\n")
        val c = BackupFile.read(ByteArrayInputStream(zip("backup.txt" to info, "settings.txt" to s)), tmp())
        assertEquals(listOf("theme"), c.settings.map { it.key })
        assertEquals(4, c.skipped)
    }

    @Test fun historiesMerge() {
        val phone = "start=3000;end=4000;km=5.0;drive=300;conn=400;idle=10"
        val backup = "start=1000;end=2000;km=12.0;drive=900;conn=1000;idle=60\nstart=3000;end=4000;km=5.0;drive=300;conn=400;idle=10"
        assertEquals(listOf(3000L, 1000L), TripHistory.parse(BackupFile.merge("trip_history", phone, backup)).map { it.startedAt })
        val codesPhone = "P0420|s|500|900||2006 Mazda Tribute"
        val codesBackup = "P0420|s|100|300|400|2006 Mazda Tribute\nP0171|p|50|60||2006 Mazda Tribute"
        val merged = CodeHistory.parse(BackupFile.merge("code_history", codesPhone, codesBackup))
        assertEquals(2, merged.size)
        val p0420 = merged.first { it.code == "P0420" }
        assertEquals(100L, p0420.firstSeen)
        assertEquals(900L, p0420.lastSeen)
        assertNull(p0420.goneSince)
        assertEquals("kerb", BackupFile.merge("theme", "asphalt", "kerb"))
    }

    @Test fun driveNames() {
        assertTrue(BackupFile.isDriveName("OBD2Reader_2026-11-13_142530.csv"))
        assertTrue(BackupFile.isDriveName("OBD2Reader_2026-11-13_1425.csv"))
        assertTrue(BackupFile.isDriveName("OBD2Reader_2026-11-13_142530_2.csv"))
        assertFalse(BackupFile.isDriveName("../OBD2Reader_2026-11-13_142530.csv"))
        assertFalse(BackupFile.isDriveName("OBD2Reader_2026-11-13_142530.csv/"))
        assertFalse(BackupFile.isDriveName("OBD2Reader_2026-11-13_142530.csv.apk"))
    }
}
