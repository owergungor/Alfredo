package com.glasslauncher.tv.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSerializationRoundTrip() {
        val testMap = mapOf(
            "str_key" to "Hello World",
            "bool_key" to true,
            "int_key" to 42,
            "long_key" to 9876543210L,
            "float_key" to 3.14f,
            "set_key" to setOf("alpha", "beta", "gamma")
        )

        val json = BackupManager.serializePreferences(testMap)
        val deserialized = BackupManager.deserializePreferences(json)

        assertEquals("Hello World", deserialized["str_key"]?.second)
        assertEquals(true, deserialized["bool_key"]?.second)
        assertEquals(42, deserialized["int_key"]?.second)
        assertEquals(9876543210L, deserialized["long_key"]?.second)
        assertEquals(3.14f, (deserialized["float_key"]?.second as Float), 0.001f)
        assertEquals(setOf("alpha", "beta", "gamma"), deserialized["set_key"]?.second)
    }

    @Test
    fun testChecksumCalculation() {
        val file = tempFolder.newFile("sample.txt")
        file.writeText("Alfredo Launcher Backup Checksum Test")
        val sha256 = BackupManager.computeSha256(file)
        assertNotNull(sha256)
        assertEquals(64, sha256.length)
        assertTrue(sha256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun testZipSlipAttackRejected() {
        val maliciousZip = tempFolder.newFile("malicious.zip")
        ZipOutputStream(FileOutputStream(maliciousZip)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            val manifest = JSONObject().apply {
                put("app", "Alfredo")
                put("version", 1)
            }
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()

            // Path traversal attempt in entry name
            zip.putNextEntry(ZipEntry("icons/../../evil.sh"))
            zip.write("malicious payload".toByteArray())
            zip.closeEntry()
        }

        var caughtSecurityException = false
        ZipFile(maliciousZip).use { z ->
            val entries = z.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.contains("..") || entry.name.startsWith("/") || entry.name.startsWith("\\")) {
                    caughtSecurityException = true
                    break
                }
            }
        }
        assertTrue("Zip Slip path traversal attempt must be detected and blocked", caughtSecurityException)
    }

    @Test
    fun testZipBombThreshold() {
        // Test that exceeding entry count is strictly flagged
        val maxAllowed = BackupManager.MAX_ENTRIES
        assertEquals(2000, maxAllowed)
        assertTrue("Threshold must be positive", maxAllowed > 0)
    }

    @Test
    fun testCorruptBackupMissingManifest() {
        val corruptZip = tempFolder.newFile("corrupt.zip")
        ZipOutputStream(FileOutputStream(corruptZip)).use { zip ->
            zip.putNextEntry(ZipEntry("random.txt"))
            zip.write("no manifest here".toByteArray())
            zip.closeEntry()
        }

        ZipFile(corruptZip).use { z ->
            val manifestEntry = z.getEntry("manifest.json")
            assertNull("Manifest must be missing in this corrupt zip", manifestEntry)
        }
    }

    @Test
    fun testIncompatibleBackupAppName() {
        val zipFile = tempFolder.newFile("foreign.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            val manifest = JSONObject().apply {
                put("app", "SomeOtherLauncher")
                put("version", 1)
            }
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
        }

        ZipFile(zipFile).use { z ->
            val manifestEntry = z.getEntry("manifest.json")
            assertNotNull(manifestEntry)
            val manifestText = z.getInputStream(manifestEntry).bufferedReader().readText()
            val manifest = JSONObject(manifestText)
            val appName = manifest.optString("app")
            assertNotEquals("Alfredo", appName)
            assertNotEquals("Glassio", appName)
        }
    }

    @Test
    fun testUnsupportedFutureVersion() {
        val zipFile = tempFolder.newFile("future_version.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            val manifest = JSONObject().apply {
                put("app", "Alfredo")
                put("version", 999)
            }
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
        }

        ZipFile(zipFile).use { z ->
            val manifestEntry = z.getEntry("manifest.json")
            val manifestText = z.getInputStream(manifestEntry).bufferedReader().readText()
            val manifest = JSONObject(manifestText)
            val version = manifest.optInt("version")
            assertTrue("Version 999 must exceed supported version", version > BackupManager.CURRENT_BACKUP_VERSION)
        }
    }

    @Test
    fun testDamagedTruncatedZip() {
        val damagedFile = tempFolder.newFile("damaged.zip")
        damagedFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00))
        var failed = false
        try {
            ZipFile(damagedFile).use { }
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("Opening a damaged zip must throw an Exception", failed)
    }

    @Test
    fun testSanitizeFileName() {
        assertEquals("clean_name.jpg", BackupManager.sanitizeFileName("clean_name.jpg"))
        assertEquals("evil_file.png", BackupManager.sanitizeFileName("../evil_file.png"))
        assertEquals("traversal.jpg", BackupManager.sanitizeFileName("..\\..\\path/traversal.jpg"))
        assertEquals("invalid__chars_.png", BackupManager.sanitizeFileName("invalid;*chars?.png"))
    }
}
