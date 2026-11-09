package app.lazydex.data.cache

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Cover cache based on Komikku's eu.kanade.tachiyomi.data.cache.CoverCache.
 * Stores covers permanently on disk. Files are named with MD5 hash of thumbnail URL.
 */
class CoverCache(private val context: Context) {

    companion object {
        private const val COVERS_DIR = "covers"
        private const val CUSTOM_COVERS_DIR = "covers/custom"
    }

    val cacheDir: File = getCacheDir(COVERS_DIR)
    val customCoverCacheDir: File = getCacheDir(CUSTOM_COVERS_DIR)

    fun getCoverFile(url: String?): File? {
        if (url.isNullOrBlank()) return null
        return File(cacheDir, md5(url.trim()))
    }

    fun getCustomCoverFile(mediaId: String): File {
        return File(customCoverCacheDir, md5(mediaId.trim()))
    }

    fun setCustomCoverToCache(mediaId: String, inputStream: InputStream) {
        val file = getCustomCoverFile(mediaId)
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            inputStream.copyTo(output)
        }
    }

    fun deleteFromCache(url: String?): Boolean {
        val file = getCoverFile(url) ?: return false
        return file.exists() && file.delete()
    }

    fun deleteCustomCover(mediaId: String): Boolean {
        val file = getCustomCoverFile(mediaId)
        return file.exists() && file.delete()
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
