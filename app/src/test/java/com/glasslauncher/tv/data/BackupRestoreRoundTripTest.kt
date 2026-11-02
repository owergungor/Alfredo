package com.glasslauncher.tv.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

class BackupRestoreRoundTripTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val goldenBackupFile = File("C:/Users/omer/.gemini/antigravity/brain/68a0c7c1-cc8a-44a4-9c20-97a4c5afb281/alfredo_backup_golden.zip")

    private val expectedImageHashes = mapOf(
        "green1.jpg" to "ab890937aad0cc555ed0f6e2d3c801c3e9542d716d9e0df6cbeb8788551da92a",
        "green3.jpg" to "7801b1e4102914c9a4772fb95c8fbc438faa65f0d7d29a07a0227de92ea7de24",
        "green4.jpg" to "bd87d7f73a14be28642d958edf2ae9dbf33f767a4a0cc7ffe7dc5c2957f50b05",
        "green5.jpg" to "97abf224c3cc3efa37236d5057592cbf53d57960745c7bbda2d068b5e0de61b6",
        "green6.jpg" to "ad0da24a9d6c7176f6ef3968858beda7159f1170229a61ffff877e3628f180bb",
        "green7.jpg" to "6b97e86787be34e1590c6cc91ecac15271d0f0d1798206772cbfbc90b5e63656",
        "lake1.jpg" to "e3575783b109659986458331c148cac1ca9b07d32098d437c37821eab4c8bfa0",
        "lake2.jpg" to "83a3acd8568f42998b929169818d53533cafcb46a1956c9ef072d953f64a689e",
        "lake3.jpg" to "1c79bde689f0f733507e3369caf45ee8f5bbf14d16ca0f2abb10b3e90dbad43c",
        "lake4.jpg" to "5bf95fb03c4ec5e7a23d7d175f810b622d7b1b3cb585bda0d73a3663ca3e46a0",
        "mountain1.jpg" to "b2d21955d98bb381a36f592df08c951faaf707a7a568072faf85d39b33427fcd",
        "north1.jpg" to "a8a1726d6b1ded78cc50a2e5400e08d9b77f8c89183baec9f7c69178da7427d6",
        "north2.jpg" to "0553cd3a7f1743d4eace81b7e0dcf3c3da435447b4ca9fb56028853fadd8c0c3",
        "pink1.jpg" to "92be4953a3b9a505e0225c66b601036069134e26f3e7712744f3907c5e5810f8",
        "pink2.jpg" to "8cd6ce9ab781cc5468e995424a4631f91241e34ea4b5e5546b73e4fbd3890eee",
        "pink3.jpg" to "f377104070d200a59d1d03a6b5c18348651b3894ccb2333b8674cc35017b4bee",
        "pink4.jpg" to "65426455d586cf27f7feb10bcb11096fbb15c7bb8780282797167191da23752d",
        "pink5.jpg" to "1bfbce169ccaf02e6f80041f024f00aefeda542f14d0b9e4c171011134bb47a2",
        "town1.jpg" to "82d871530a93547207dee25f7ebf973e5adb5614cdd2532c65676c3c57031f8d",
        "town2.jpg" to "d2f705d456513e18629b5e6c09d389dd0cc100cd6b4813b420c7f86e076c02eb",
        "white1.jpg" to "4c9a0372d85fe7939dfa42d21705ba580a792c767c8d15af54f2889d50b780e4"
    )

    @Test
    fun testGoldenBackupIntegrityAndManifest() {
        org.junit.Assume.assumeTrue("Golden backup file must exist on disk", goldenBackupFile.exists())
        assertEquals(
            "Golden backup SHA-256 must match verified value",
            "3e3c0b9db55c0913d9cf9a0f3f1e58d3eab4540f1560b8ea54544cfb2984ebb6",
            BackupManager.computeSha256(goldenBackupFile).lowercase()
        )

        ZipFile(goldenBackupFile).use { zip ->
            assertEquals("Zip entry count must be exactly 28", 28, zip.size())
            val manifestEntry = zip.getEntry("manifest.json")
            assertNotNull("manifest.json must exist in golden backup", manifestEntry)

            val manifestText = zip.getInputStream(manifestEntry).bufferedReader().readText()
            val manifest = JSONObject(manifestText)

            assertEquals("Alfredo", manifest.getString("app"))
            assertEquals(1, manifest.getInt("version"))
            assertEquals(31, manifest.getInt("versionCode"))
            assertEquals("1.0", manifest.getString("versionName"))
            assertEquals(0, manifest.getInt("icons_count"))
            assertEquals(21, manifest.getInt("backgrounds_count"))

            val prefsArray = manifest.getJSONArray("prefs")
            val prefsList = mutableListOf<String>()
            for (i in 0 until prefsArray.length()) {
                prefsList.add(prefsArray.getString(i))
            }
            val expectedPrefs = listOf("glass", "glass_library", "glass_catalogs", "glass_cw", "glass_trakt", "glass_app_cache")
            assertEquals(expectedPrefs, prefsList)
        }
    }

    @Test
    fun testPreferencesDeserializationAndDataTypes() {
        org.junit.Assume.assumeTrue("Golden backup file must exist on disk", goldenBackupFile.exists())
        ZipFile(goldenBackupFile).use { zip ->
            // 1. glass.json
            val glassEntry = zip.getEntry("prefs/glass.json")
            assertNotNull(glassEntry)
            val glassJson = zip.getInputStream(glassEntry).bufferedReader().readText()
            val glassPrefs = BackupManager.deserializePreferences(JSONObject(glassJson))

            // Test key values and types
            assertEquals("liquid", glassPrefs["theme"]?.second)
            assertEquals("liquid", glassPrefs["tileStyle"]?.second)
            assertEquals("liquid", glassPrefs["menuStyle"]?.second)
            assertEquals("liquid", glassPrefs["panelStyle"]?.second)
            assertEquals("liquid", glassPrefs["dockStyle"]?.second)
            assertEquals("Bursa", glassPrefs["weatherCity"]?.second)
            assertEquals("all", glassPrefs["weatherMode"]?.second)
            assertEquals("apple", glassPrefs["clockFont"]?.second)
            assertEquals("apple", glassPrefs["uiFont"]?.second)
            assertEquals(true, glassPrefs["showDate"]?.second)
            assertEquals(true, glassPrefs["clock24h"]?.second)
            assertEquals(true, glassPrefs["tileLift"]?.second)
            assertEquals(true, glassPrefs["cwLegacy"]?.second)
            assertEquals(false, glassPrefs["focusColor"]?.second)
            assertEquals(false, glassPrefs["heroGallery"]?.second)
            assertEquals(24, glassPrefs["cornerRadius"]?.second)
            assertEquals(480, glassPrefs["glassRes"]?.second)
            assertEquals(4, glassPrefs["blurRadius"]?.second)
            assertEquals(300, glassPrefs["transitionMs"]?.second)
            assertEquals(20, glassPrefs["glassTint"]?.second)
            assertEquals(-30, glassPrefs["clockVerticalOffset"]?.second)

            val pageScale = glassPrefs["pageScale"]?.second
            assertTrue("pageScale must be Float", pageScale is Float)
            assertEquals(0.667f, pageScale as Float, 0.01f)

            val dockOrder = glassPrefs["dockOrder"]?.second as String
            assertTrue("dockOrder contains netflix", dockOrder.contains("com.netflix.ninja"))
            assertTrue("dockOrder contains youtube", dockOrder.contains("com.google.android.youtube.tv"))
            assertTrue("dockOrder contains vending", dockOrder.contains("com.android.vending"))

            val hiddenApps = glassPrefs["hiddenApps"]?.second as String
            assertTrue("hiddenApps contains dream", hiddenApps.contains("com.mitv.dream"))
            assertTrue("hiddenApps contains tvhome", hiddenApps.contains("com.mitv.tvhome.atv"))

            // 2. glass_cw.json
            val cwEntry = zip.getEntry("prefs/glass_cw.json")
            assertNotNull(cwEntry)
            val cwPrefs = BackupManager.deserializePreferences(JSONObject(zip.getInputStream(cwEntry).bufferedReader().readText()))
            assertEquals(true, cwPrefs["access"]?.second)

            // 3. glass_app_cache.json
            val appCacheEntry = zip.getEntry("prefs/glass_app_cache.json")
            assertNotNull(appCacheEntry)
            val appCachePrefs = BackupManager.deserializePreferences(JSONObject(zip.getInputStream(appCacheEntry).bufferedReader().readText()))
            val appsStr = appCachePrefs["apps"]?.second as String
            assertTrue("apps cache contains Play Store", appsStr.contains("Play Store"))
            assertTrue("apps cache contains YouTube", appsStr.contains("YouTube"))
            assertTrue("apps cache contains Netflix", appsStr.contains("Netflix"))

            // 4. Remaining empty prefs
            for (p in listOf("glass_library", "glass_catalogs", "glass_trakt")) {
                val pEntry = zip.getEntry("prefs/$p.json")
                assertNotNull("prefs/$p.json must exist", pEntry)
                val pPrefs = BackupManager.deserializePreferences(JSONObject(zip.getInputStream(pEntry).bufferedReader().readText()))
                assertTrue("prefs/$p should be empty map", pPrefs.isEmpty())
            }
        }
    }

    @Test
    fun testAll21BackgroundImagesHashParity() {
        org.junit.Assume.assumeTrue("Golden backup file must exist on disk", goldenBackupFile.exists())
        val stagingDir = tempFolder.newFolder("restore_staging")
        val backgroundsDir = File(stagingDir, "backgrounds").apply { mkdirs() }

        ZipFile(goldenBackupFile).use { zip ->
            val entries = zip.entries()
            var extractedCount = 0

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.startsWith("backgrounds/") && !entry.isDirectory && entry.size > 0) {
                    val fileName = BackupManager.sanitizeFileName(entry.name)
                    val outFile = File(backgroundsDir, fileName)
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    extractedCount++
                }
            }

            assertEquals("Must have extracted exactly 21 background images", 21, extractedCount)
        }

        // Verify SHA-256 for all 21 extracted files
        for ((fileName, expectedHash) in expectedImageHashes) {
            val file = File(backgroundsDir, fileName)
            assertTrue("File $fileName must exist after restore extraction", file.exists())

            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(file.readBytes())
            val actualHash = digest.joinToString("") { "%02x".format(it) }

            assertEquals("SHA-256 parity mismatch for $fileName", expectedHash, actualHash)
        }
    }
}
