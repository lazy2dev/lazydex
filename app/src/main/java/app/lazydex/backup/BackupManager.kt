package app.lazydex.backup

import android.content.Context
import android.net.Uri
import app.lazydex.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportedBackup(
    val schemaVersion: Int,
    val items: List<MediaItem>,
    val tempCoversDir: File
)

object BackupManager {

    /**
     * Exports backup data to a .lazydex ZIP file at the target SAF URI.
     * Packages the backup.json metadata and optionally the local covers/ folder.
     */
    suspend fun export(
        context: Context,
        uri: Uri,
        items: List<MediaItem>,
        includeCovers: Boolean,
        localCoversDir: File
    ): Unit = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "backup_${UUID.randomUUID()}.tmp")
        try {
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                // Write backup.json
                val jsonString = BackupProcessor.serialize(items)
                val jsonEntry = ZipEntry("backup.json")
                zos.putNextEntry(jsonEntry)
                zos.write(jsonString.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                if (includeCovers) {
                    items.forEach { item ->
                        val coverFile = if (item.coverImagePath.isNotBlank()) File(item.coverImagePath) else File(localCoversDir, item.id)
                        val fileToExport = if (coverFile.exists()) coverFile else File(localCoversDir, item.id)
                        if (fileToExport.exists()) {
                            val coverEntry = ZipEntry("covers/${item.id}")
                            zos.putNextEntry(coverEntry)
                            FileInputStream(fileToExport).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }

            // Copy output to target SAF URI
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                FileInputStream(tempFile).use { input ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Failed to open SAF output stream for writing")
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    /**
     * Inspects a .lazydex ZIP file from a SAF URI.
     * Extracts backup.json and unpacks covers into a temporary cache folder.
     * Returns an ImportedBackup object containing deserialized items and temp covers location.
     */
    suspend fun readZipContent(context: Context, uri: Uri): ImportedBackup = withContext(Dispatchers.IO) {
        val tempCoversDir = File(context.cacheDir, "import_covers_${UUID.randomUUID()}")
        tempCoversDir.mkdirs()

        var schemaVersion = 1
        var items: List<MediaItem> = emptyList()

        context.contentResolver.openInputStream(uri).use { inputStream ->
            if (inputStream == null) throw IOException("Failed to open SAF input stream")
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "backup.json") {
                        val jsonBytes = zis.readBytes()
                        val jsonString = jsonBytes.decodeToString()
                        val deserialized = BackupProcessor.deserialize(jsonString)
                        schemaVersion = deserialized.schemaVersion
                        items = deserialized.items
                    } else if (entry.name.startsWith("covers/")) {
                        val coverId = entry.name.removePrefix("covers/")
                        if (coverId.isNotEmpty()) {
                            val coverFile = File(tempCoversDir, File(coverId).name)
                            if (coverFile.canonicalPath.startsWith(tempCoversDir.canonicalPath)) {
                                coverFile.parentFile?.mkdirs()
                                FileOutputStream(coverFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        if (items.isEmpty()) {
            cleanTempDir(tempCoversDir)
            throw IllegalArgumentException("No valid media tracker items found in backup file")
        }

        ImportedBackup(schemaVersion = schemaVersion, items = items, tempCoversDir = tempCoversDir)
    }

    /**
     * Restores all covers from temp covers directory to local covers directory (Overwrite strategy).
     */
    fun restoreAllCovers(tempCoversDir: File, localCoversDir: File) {
        if (!localCoversDir.exists()) localCoversDir.mkdirs()
        tempCoversDir.listFiles()?.forEach { file ->
            val destFile = File(localCoversDir, file.name)
            file.copyTo(destFile, overwrite = true)
        }
    }

    /**
     * Clean up temporary covers directory.
     */
    fun cleanTempDir(tempCoversDir: File) {
        try {
            if (tempCoversDir.exists()) {
                tempCoversDir.deleteRecursively()
            }
        } catch (e: Exception) {
            // Ignore clean up errors
        }
    }
}
