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
                        if (item.coverImagePath.isNotEmpty()) {
                            val coverFile = File(item.coverImagePath)
                            if (coverFile.exists()) {
                                val coverEntry = ZipEntry("covers/${item.id}")
                                zos.putNextEntry(coverEntry)
                                FileInputStream(coverFile).use { input ->
                                    input.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }

            // Copy temp file to SAF URI using "wt" write+truncate mode to prevent trailing garbage
            context.contentResolver.openOutputStream(uri, "wt").use { outputStream ->
                if (outputStream == null) throw IOException("Failed to open SAF output stream")
                FileInputStream(tempFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Imports and unzips a .lazydex ZIP archive from SAF.
     * Extracts backup.json and extracts cover files to a temporary cache folder.
     */
    suspend fun import(
        context: Context,
        uri: Uri
    ): ImportedBackup = withContext(Dispatchers.IO) {
        // Enforce 50MB limit
        context.contentResolver.openFileDescriptor(uri, "r").use { pfd ->
            val size = pfd?.statSize ?: 0L
            if (size > 50L * 1024 * 1024) {
                throw IllegalArgumentException("Backup file exceeds the 50MB size limit")
            }
        }

        val tempCoversDir = File(context.cacheDir, "temp_covers_${UUID.randomUUID()}")
        if (!tempCoversDir.exists()) tempCoversDir.mkdirs()

        var items: List<MediaItem> = emptyList()

        context.contentResolver.openInputStream(uri).use { inputStream ->
            if (inputStream == null) throw IOException("Failed to open SAF input stream")
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "backup.json") {
                        val jsonBytes = zis.readBytes()
                        val jsonString = jsonBytes.decodeToString()
                        items = BackupProcessor.deserialize(jsonString)
                    } else if (entry.name.startsWith("covers/")) {
                        val coverId = entry.name.removePrefix("covers/")
                        if (coverId.isNotEmpty()) {
                            val coverFile = File(tempCoversDir, coverId)
                            // Create parent dirs just in case
                            coverFile.parentFile?.mkdirs()
                            FileOutputStream(coverFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        if (items.isEmpty()) {
            throw IllegalArgumentException("Invalid backup: backup.json not found or contains no items")
        }

        ImportedBackup(items, tempCoversDir)
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
