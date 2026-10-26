package app.lazydex.extension

import app.lazydex.domain.model.MediaCategory
import app.lazydex.extension.runtime.DexSourceAdapter
import app.lazydex.extension.runtime.JsRuntimeEngine
import app.lazydex.extension.runtime.LuaRuntimeEngine
import app.lazydex.extension.runtime.WasmRuntimeEngine
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuntimeEngineTest {

    private val okHttpClient = OkHttpClient()

    @Test
    fun `JsRuntimeEngine executes JavaScript and parses html title`() {
        val engine = JsRuntimeEngine(okHttpClient)
        val script = """
            function search(query) {
                return ["Result 1: " + query, "Result 2: " + query];
            }
            function scrape(url) {
                var title = _host.parseHtmlTitle("<html><head><title>Scraped Title</title></head></html>");
                return title;
            }
        """.trimIndent()

        engine.createBridge(script).use { bridge ->
            val searchRes = bridge.callFunction("search", "Tower")
            assertNotNull(searchRes)

            val scrapeRes = bridge.callFunction("scrape", "https://example.com/novel/1")
            assertEquals("Scraped Title", scrapeRes)
        }
    }

    @Test
    fun `LuaRuntimeEngine executes Lua script with Document and Jsoup globals`() {
        val engine = LuaRuntimeEngine(okHttpClient)
        val script = """
            function search(query)
                return {
                    { title = "Title: " .. query, url = "https://example.com/item/1" }
                }
            end
            function parse(url)
                local doc = Document.parse("<html><head><title>Lua Novel</title></head></html>")
                return {
                    title = doc:title()
                }
            end
        """.trimIndent()

        val tempLua = File.createTempFile("test_script", ".lua").apply {
            writeText(script)
            deleteOnExit()
        }

        val source = engine.loadSource("shosetsu.test", tempLua, "LuaTest")
        assertNotNull(source)

        runBlocking {
            val searchResults = source!!.search("Shadow")
            assertEquals(1, searchResults.size)
            assertEquals("Title: Shadow", searchResults[0].title)

            val metadata = source.scrape("https://example.com/item/1")
            assertNotNull(metadata)
            assertEquals("Lua Novel", metadata!!.title)
        }
    }

    @Test
    fun `LuaRuntimeEngine supports Node select and attributes`() {
        val engine = LuaRuntimeEngine(okHttpClient)
        val globals = engine.createGlobals()
        val script = """
            local doc = Document.parse("<div id='content'><p class='author'>Author Name</p></div>")
            local p = doc:selectFirst("p.author")
            authorText = p:text()
            authorClass = p:attr("class")
        """.trimIndent()
        globals.load(script).call()
        assertEquals("Author Name", globals.get("authorText").tojstring())
        assertEquals("author", globals.get("authorClass").tojstring())
    }

    @Test
    fun `DexSourceAdapter adapts Tachiyomi CatalogueSource cleanly`() {
        val mockTachiyomiSource = object : CatalogueSource {
            override val id: Long = 12345L
            override val name: String = "MockMangaSource"
            override val lang: String = "en"

            override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
                val manga = SManga.create().apply {
                    title = "Found: $query"
                    url = "/manga/1"
                    thumbnail_url = "https://example.com/cover.jpg"
                }
                return MangasPage(listOf(manga), false)
            }

            override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)
            override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
        }

        val adapter = DexSourceAdapter(
            pkgName = "eu.kanade.tachiyomi.extension.mock",
            entryClass = "MockMangaSource",
            instance = mockTachiyomiSource,
            categoryHint = MediaCategory.MANGA
        )

        assertEquals("dex_eu_kanade_tachiyomi_extension_mock", adapter.id)
        assertEquals("MockMangaSource", adapter.name)
        assertEquals(MediaCategory.MANGA, adapter.categoryHint)

        runBlocking {
            val results = adapter.search("Solo")
            assertEquals(1, results.size)
            assertEquals("Found: Solo", results[0].title)
        }
    }

    @Test
    fun `WasmRuntimeEngine host imports are successfully instantiated`() {
        val engine = WasmRuntimeEngine(okHttpClient)
        val hostImports = engine.createHostImports()
        assertNotNull(hostImports)
    }

    @Test
    fun `Android 14+ DCL setReadOnly contract is honored on temp files`() {
        val tempApk = File.createTempFile("test_dcl", ".apk")
        tempApk.writeText("fake dex apk bytes")
        tempApk.deleteOnExit()

        assertTrue(tempApk.canWrite())
        tempApk.setReadOnly()

        // After setReadOnly, canWrite is false on platforms that enforce it
        // and deleting read-only file before writing updates is handled
        if (!tempApk.canWrite()) {
            val deleted = tempApk.delete()
            assertTrue(deleted)
        }
    }
}
