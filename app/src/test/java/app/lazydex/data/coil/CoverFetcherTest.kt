package app.lazydex.data.coil

import android.content.Context
import android.content.ContextWrapper
import app.lazydex.data.cache.CoverCache
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoverFetcherTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var coverCache: CoverCache
    private lateinit var testContext: Context
    private lateinit var imageLoader: ImageLoader
    private lateinit var options: Options

    private val dummyCallFactory = object : Call.Factory {
        override fun newCall(request: Request): Call = throw UnsupportedOperationException("No network expected in disk cache test")
    }

    private class FakeCoverContext(private val baseDir: File) : ContextWrapper(null) {
        override fun getFilesDir(): File = baseDir
        override fun getExternalFilesDir(type: String?): File? = null
        override fun getApplicationContext(): Context = this
    }

    @Before
    fun setUp() {
        val root = tempFolder.newFolder("cover_fetcher_test")
        testContext = FakeCoverContext(root)
        coverCache = CoverCache(testContext)
        imageLoader = ImageLoader.Builder(testContext).build()
        options = Options(testContext)
    }

    @Test
    fun fetch_returnsDiskDataSourceWhenLocalFilePathExists() = runTest {
        val testFile = File(testContext.filesDir, "local_cover.jpg").apply { writeText("img") }
        val model = CoverModel(localPath = testFile.absolutePath)

        val fetcher = CoverFetcher.CoverModelFactory(coverCache, dummyCallFactory)
            .create(model, options, imageLoader)

        val result = fetcher.fetch()
        assertTrue(result is SourceFetchResult)
        assertEquals(DataSource.DISK, (result as SourceFetchResult).dataSource)
    }

    @Test
    fun fetch_returnsDiskDataSourceWithoutNetworkWhenCacheFileExists() = runTest {
        val url = "https://example.com/cached_cover.jpg"
        val cachedFile = coverCache.getCoverFile(url)!!
        cachedFile.parentFile?.mkdirs()
        cachedFile.writeText("cached_bytes")

        val model = CoverModel(url = url)
        val fetcher = CoverFetcher.CoverModelFactory(coverCache, dummyCallFactory)
            .create(model, options, imageLoader)

        val result = fetcher.fetch()
        assertTrue(result is SourceFetchResult)
        assertEquals(DataSource.DISK, (result as SourceFetchResult).dataSource)
    }
}
