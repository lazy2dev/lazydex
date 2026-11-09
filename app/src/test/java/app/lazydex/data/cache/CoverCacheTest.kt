package app.lazydex.data.cache

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class CoverCacheTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var coverCache: CoverCache

    private class FakeCoverContext(private val baseDir: File) : ContextWrapper(null) {
        override fun getFilesDir(): File = baseDir
        override fun getExternalFilesDir(type: String?): File? = null
        override fun getApplicationContext(): Context = this
    }

    @Before
    fun setUp() {
        val root = tempFolder.newFolder("cover_test")
        coverCache = CoverCache(FakeCoverContext(root))
    }

    @Test
    fun getCoverFile_returnsConsistentHashedFileForNonEmptyUrl() {
        val url = "https://example.com/cover.jpg"
        val file1 = coverCache.getCoverFile(url)
        val file2 = coverCache.getCoverFile(url)

        assertNotNull(file1)
        assertNotNull(file2)
        assertEquals(file1?.absolutePath, file2?.absolutePath)
        assertEquals(coverCache.cacheDir.absolutePath, file1?.parentFile?.absolutePath)
    }

    @Test
    fun getCoverFile_returnsNullForBlankUrl() {
        assertNull(coverCache.getCoverFile(null))
        assertNull(coverCache.getCoverFile(""))
        assertNull(coverCache.getCoverFile("   "))
    }

    @Test
    fun deleteFromCache_deletesExistingFileAndReturnsTrue() {
        val url = "https://example.com/cover.jpg"
        val file = coverCache.getCoverFile(url)!!
        file.parentFile?.mkdirs()
        file.writeText("test image data")
        assertTrue(file.exists())

        val deleted = coverCache.deleteFromCache(url)
        assertTrue(deleted)
        assertFalse(file.exists())
    }

    @Test
    fun deleteFromCache_returnsFalseForNonexistentFile() {
        val deleted = coverCache.deleteFromCache("https://example.com/nonexistent.jpg")
        assertFalse(deleted)
    }

    @Test
    fun setCustomCoverToCache_savesStreamAndDeletes() {
        val mediaId = "item_123"
        val stream = ByteArrayInputStream("custom-image-bytes".toByteArray())

        coverCache.setCustomCoverToCache(mediaId, stream)
        val customFile = coverCache.getCustomCoverFile(mediaId)
        assertTrue(customFile.exists())
        assertEquals("custom-image-bytes", customFile.readText())

        val deleted = coverCache.deleteCustomCover(mediaId)
        assertTrue(deleted)
        assertFalse(customFile.exists())
    }
}
