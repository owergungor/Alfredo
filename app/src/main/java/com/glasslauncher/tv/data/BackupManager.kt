package com.glasslauncher.tv.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Hardened backup & migration system for Alfredo.
 * Packages SharedPreferences, custom app icons, and backgrounds into an integrity-checked,
 * zip-slip protected, and resource-bounded ZIP archive with full rollback on failure.
 */
object BackupManager {
    const val CURRENT_BACKUP_VERSION = 1
    const val BACKUP_APP_NAME = "Alfredo"
    const val BACKUP_FILENAME = "alfredo_backup.zip"
    const val CHECKSUM_FILENAME = "alfredo_backup.zip.sha256"

    // Security bounds
    const val MAX_ENTRIES = 2000
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 500L * 1024 * 1024 // 500 MB
    const val MAX_SINGLE_FILE_BYTES = 100L * 1024 * 1024 // 100 MB

    val PREF_FILES = listOf(
        "glass",
        "glass_library",
        "glass_catalogs",
        "glass_cw",
        "glass_trakt",
        "glass_app_cache"
    )

    sealed class BackupResult {
        data class Success(val file: File, val sha256: String, val message: String) : BackupResult()
        data class Error(val message: String, val cause: Throwable? = null) : BackupResult()
    }

    fun getPreferredBackupFile(ctx: Context): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return if (downloads != null && (downloads.exists() || downloads.mkdirs())) {
            File(downloads, BACKUP_FILENAME)
        } else {
            val fallback = ctx.getExternalFilesDir("backup") ?: File(ctx.filesDir, "backup")
            fallback.mkdirs()
            File(fallback, BACKUP_FILENAME)
        }
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } > 0) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun exportBackup(ctx: Context, destinationFile: File? = null): BackupResult {
        return try {
            val dest = destinationFile ?: getPreferredBackupFile(ctx)
            dest.parentFile?.mkdirs()

            val tempFile = File.createTempFile("alfredo_export_", ".tmp", ctx.cacheDir)

            val iconFiles = mutableListOf<File>()
            val iconsDir = File(ctx.filesDir, "icons")
            if (iconsDir.exists() && iconsDir.isDirectory) {
                iconsDir.listFiles()?.filter { it.isFile }?.let { iconFiles.addAll(it) }
            }

            val bgFiles = mutableListOf<File>()
            val internalBgDir = File(ctx.filesDir, "backgrounds")
            if (internalBgDir.exists() && internalBgDir.isDirectory) {
                internalBgDir.listFiles()?.filter { it.isFile }?.let { bgFiles.addAll(it) }
            }
            val extBgDir = ctx.getExternalFilesDir("backgrounds")
            if (extBgDir != null && extBgDir.exists() && extBgDir.isDirectory) {
                extBgDir.listFiles()?.filter { it.isFile }?.let { files ->
                    files.forEach { f -> if (bgFiles.none { it.name == f.name }) bgFiles.add(f) }
                }
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { zip ->
                val manifest = JSONObject().apply {
                    put("app", BACKUP_APP_NAME)
                    put("version", CURRENT_BACKUP_VERSION)
                    put("timestamp", System.currentTimeMillis())
                    put("versionCode", 31)
                    put("versionName", "1.0")
                    put("prefs", JSONArray(PREF_FILES))
                    put("icons_count", iconFiles.size)
                    put("backgrounds_count", bgFiles.size)
                }
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                for (prefName in PREF_FILES) {
                    val sp = ctx.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    val all = sp.all
                    val prefJson = serializePreferences(all)
                    zip.putNextEntry(ZipEntry("prefs/$prefName.json"))
                    zip.write(prefJson.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }

                for (icon in iconFiles) {
                    val safeName = sanitizeFileName(icon.name)
                    zip.putNextEntry(ZipEntry("icons/$safeName"))
                    icon.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }

                for (bg in bgFiles) {
                    val safeName = sanitizeFileName(bg.name)
                    zip.putNextEntry(ZipEntry("backgrounds/$safeName"))
                    bg.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }

            if (dest.exists()) dest.delete()
            if (!tempFile.renameTo(dest)) {
                tempFile.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                tempFile.delete()
            }

            val sha256 = computeSha256(dest)
            val shaFile = File(dest.parentFile, "${dest.name}.sha256")
            shaFile.writeText("$sha256  ${dest.name}\n")

            Glog.i("Backup exported to ${dest.absolutePath} (SHA-256: $sha256)")
            BackupResult.Success(dest, sha256, "Backup exported successfully to ${dest.name}")
        } catch (t: Throwable) {
            Glog.w("Backup export failed: ${t.message}")
            BackupResult.Error("Export failed: ${t.message}", t)
        }
    }

    fun importBackup(ctx: Context, backupFile: File): BackupResult {
        if (!backupFile.exists() || !backupFile.canRead()) {
            return BackupResult.Error("Backup file does not exist or cannot be read: ${backupFile.absolutePath}")
        }

        // 1. Verify sidecar checksum if present
        val sidecarFile = File(backupFile.parentFile, "${backupFile.name}.sha256")
        if (sidecarFile.exists()) {
            val expectedHash = sidecarFile.readText().trim().split(Regex("\\s+")).firstOrNull()?.lowercase()
            if (!expectedHash.isNullOrBlank()) {
                val actualHash = computeSha256(backupFile).lowercase()
                if (actualHash != expectedHash) {
                    return BackupResult.Error("Integrity check failed: Expected SHA-256 $expectedHash but got $actualHash. Archive may be corrupted or tampered.")
                }
            }
        }

        val stagingDir = File(ctx.cacheDir, "restore_staging_${System.currentTimeMillis()}").apply { mkdirs() }

        return try {
            val zip = ZipFile(backupFile)
            var totalEntries = 0
            var totalUncompressedBytes = 0L

            // 2. Validate Manifest
            val manifestEntry = zip.getEntry("manifest.json")
                ?: return BackupResult.Error("Invalid backup: missing manifest.json")

            val manifestText = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val manifest = JSONObject(manifestText)

            val appName = manifest.optString("app")
            if (appName != BACKUP_APP_NAME && appName != "Glassio") {
                return BackupResult.Error("Incompatible backup: archive was created by '$appName', expected '$BACKUP_APP_NAME'")
            }

            val version = manifest.optInt("version", 0)
            if (version < 1 || version > CURRENT_BACKUP_VERSION) {
                return BackupResult.Error("Unsupported backup version: $version (Current version is $CURRENT_BACKUP_VERSION)")
            }

            // 3. Parse and validate all JSON prefs first (no mutation yet)
            val parsedPrefs = mutableMapOf<String, Map<String, Pair<String, Any>>>()
            for (prefName in PREF_FILES) {
                val entry = zip.getEntry("prefs/$prefName.json") ?: continue
                val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                parsedPrefs[prefName] = deserializePreferences(JSONObject(text))
            }

            // 4. Extract icons and backgrounds into staging directory with zip-slip & bounds protection
            val stagingIconsDir = File(stagingDir, "icons").apply { mkdirs() }
            val stagingBgDir = File(stagingDir, "backgrounds").apply { mkdirs() }

            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                totalEntries++
                if (totalEntries > MAX_ENTRIES) {
                    throw SecurityException("Security violation: Archive exceeds maximum allowed entry count ($MAX_ENTRIES).")
                }

                val entryName = entry.name
                // Zip-slip checks
                if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                    throw SecurityException("Security violation: Potential Zip Slip attack detected in entry '$entryName'.")
                }

                if (entry.isDirectory) continue

                val targetDir = when {
                    entryName.startsWith("icons/") -> stagingIconsDir
                    entryName.startsWith("backgrounds/") -> stagingBgDir
                    entryName == "manifest.json" || entryName.startsWith("prefs/") -> null
                    else -> null // ignore unexpected files safely
                }

                if (targetDir != null) {
                    val rawFileName = entryName.substringAfter('/')
                    val safeFileName = sanitizeFileName(rawFileName)
                    val targetFile = File(targetDir, safeFileName)

                    // Ensure target is canonical within targetDir
                    if (!targetFile.canonicalPath.startsWith(targetDir.canonicalPath + File.separator)) {
                        throw SecurityException("Security violation: Target path '$targetFile' escapes destination directory.")
                    }

                    zip.getInputStream(entry).use { input ->
                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var fileBytes = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                fileBytes += bytesRead
                                totalUncompressedBytes += bytesRead
                                if (fileBytes > MAX_SINGLE_FILE_BYTES) {
                                    throw SecurityException("Security violation: Single file '$entryName' exceeds $MAX_SINGLE_FILE_BYTES bytes.")
                                }
                                if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                    throw SecurityException("Security violation: Total uncompressed archive size exceeds $MAX_TOTAL_UNCOMPRESSED_BYTES bytes.")
                                }
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                }
            }
            zip.close()

            // 5. Transactional Rollback Snapshot
            // Take a complete snapshot of all existing SharedPreferences before modifying
            val rollbackSnapshot = mutableMapOf<String, Map<String, *>>()
            for (p in PREF_FILES) {
                rollbackSnapshot[p] = ctx.getSharedPreferences(p, Context.MODE_PRIVATE).all
            }

            try {
                // Apply all preferences atomically
                for ((prefName, entriesMap) in parsedPrefs) {
                    val sp = ctx.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    val editor = sp.edit().clear()
                    for ((key, pair) in entriesMap) {
                        val (type, value) = pair
                        when (type) {
                            "boolean" -> editor.putBoolean(key, value as Boolean)
                            "int" -> editor.putInt(key, (value as Number).toInt())
                            "long" -> editor.putLong(key, (value as Number).toLong())
                            "float" -> editor.putFloat(key, (value as Number).toFloat())
                            "string" -> editor.putString(key, value as String)
                            "set" -> {
                                @Suppress("UNCHECKED_CAST")
                                editor.putStringSet(key, value as Set<String>)
                            }
                        }
                    }
                    val ok = editor.commit()
                    if (!ok) throw IOException("Failed to commit SharedPreferences '$prefName'")
                }

                // If preferences committed successfully, copy staged files to live directories
                val iconsTargetDir = File(ctx.filesDir, "icons").apply { mkdirs() }
                stagingIconsDir.listFiles()?.forEach { file ->
                    val dest = File(iconsTargetDir, file.name)
                    file.copyTo(dest, overwrite = true)
                }

                val bgPrimaryDir = com.glasslauncher.tv.ui.CustomBackground.folder(ctx)
                val bgTargetDir = bgPrimaryDir
                val bgInternalDir = File(ctx.filesDir, "backgrounds").apply { mkdirs() }
                stagingBgDir.listFiles()?.forEach { file ->
                    val dest = File(bgTargetDir, file.name)
                    file.copyTo(dest, overwrite = true)
                    if (bgPrimaryDir.absolutePath != bgInternalDir.absolutePath) file.copyTo(File(bgInternalDir, file.name), overwrite = true)
                }

            } catch (t: Throwable) {
                // Rollback SharedPreferences immediately
                Glog.w("Error during SharedPreferences commit, rolling back: ${t.message}")
                for ((prefName, oldMap) in rollbackSnapshot) {
                    restoreSharedPreferencesFromMap(ctx.getSharedPreferences(prefName, Context.MODE_PRIVATE), oldMap)
                }
                throw t
            }

            val sha256 = computeSha256(backupFile)
            Glog.i("Backup successfully restored from ${backupFile.name} (version $version)")
            BackupResult.Success(backupFile, sha256, "Settings and media successfully restored.")
        } catch (t: Throwable) {
            Glog.w("Backup import failed: ${t.message}")
            BackupResult.Error("Import failed: ${t.message}", t)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    fun sanitizeFileName(name: String): String {
        return File(name).name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun restoreSharedPreferencesFromMap(sp: SharedPreferences, map: Map<String, *>) {
        val editor = sp.edit().clear()
        for ((k, v) in map) {
            if (v == null) continue
            when (v) {
                is Boolean -> editor.putBoolean(k, v)
                is Int -> editor.putInt(k, v)
                is Long -> editor.putLong(k, v)
                is Float -> editor.putFloat(k, v)
                is String -> editor.putString(k, v)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(k, v as Set<String>)
                }
            }
        }
        editor.commit()
    }

    fun serializePreferences(map: Map<String, *>): JSONObject {
        val root = JSONObject()
        val types = JSONObject()
        val values = JSONObject()

        for ((k, v) in map) {
            if (v == null) continue
            when (v) {
                is Boolean -> { types.put(k, "boolean"); values.put(k, v) }
                is Int -> { types.put(k, "int"); values.put(k, v) }
                is Long -> { types.put(k, "long"); values.put(k, v) }
                is Float -> { types.put(k, "float"); values.put(k, v.toDouble()) }
                is String -> { types.put(k, "string"); values.put(k, v) }
                is Set<*> -> {
                    types.put(k, "set")
                    val arr = JSONArray()
                    v.forEach { if (it is String) arr.put(it) }
                    values.put(k, arr)
                }
            }
        }
        root.put("types", types)
        root.put("values", values)
        return root
    }

    fun deserializePreferences(root: JSONObject): Map<String, Pair<String, Any>> {
        val result = mutableMapOf<String, Pair<String, Any>>()
        val types = root.optJSONObject("types") ?: return result
        val values = root.optJSONObject("values") ?: return result

        val keys = types.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val type = types.getString(key)
            when (type) {
                "boolean" -> result[key] = Pair(type, values.getBoolean(key))
                "int" -> result[key] = Pair(type, values.getInt(key))
                "long" -> result[key] = Pair(type, values.getLong(key))
                "float" -> result[key] = Pair(type, values.getDouble(key).toFloat())
                "string" -> result[key] = Pair(type, values.getString(key))
                "set" -> {
                    val arr = values.getJSONArray(key)
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) {
                        set.add(arr.getString(i))
                    }
                    result[key] = Pair(type, set)
                }
            }
        }
        return result
    }
}
