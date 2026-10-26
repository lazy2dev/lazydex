package app.lazydex.extension.runtime

import app.lazydex.domain.model.MediaCategory
import app.lazydex.scraper.source.ScrapedMetadata
import app.lazydex.scraper.source.SearchResult
import app.lazydex.scraper.source.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

class LuaRuntimeEngine(
    private val okHttpClient: OkHttpClient
) {
    private fun wrapElement(element: org.jsoup.nodes.Element): LuaTable {
        val nodeObj = LuaTable()
        nodeObj.set("text", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue = LuaValue.valueOf(element.text())
        })
        nodeObj.set("attr", object : TwoArgFunction() {
            override fun call(self: LuaValue, name: LuaValue): LuaValue {
                val value = element.attr(name.tojstring())
                return LuaValue.valueOf(value)
            }
        })
        nodeObj.set("html", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue = LuaValue.valueOf(element.html())
        })
        nodeObj.set("selectFirst", object : TwoArgFunction() {
            override fun call(self: LuaValue, css: LuaValue): LuaValue {
                val child = element.selectFirst(css.tojstring())
                return if (child != null) wrapElement(child) else LuaValue.NIL
            }
        })
        nodeObj.set("select", object : TwoArgFunction() {
            override fun call(self: LuaValue, css: LuaValue): LuaValue {
                val list = element.select(css.tojstring())
                val table = LuaTable()
                for (i in list.indices) {
                    table.set(i + 1, wrapElement(list[i]))
                }
                return table
            }
        })
        return nodeObj
    }

    fun createGlobals(): Globals {
        val globals = JsePlatform.standardGlobals()

        // Inject Request global
        val requestTable = LuaTable()
        requestTable.set("get", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val url = arg.tojstring()
                return try {
                    val req = Request.Builder().url(url).build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        val resTable = LuaTable()
                        resTable.set("body", LuaValue.valueOf(body))
                        resTable.set("code", LuaValue.valueOf(resp.code))
                        resTable
                    }
                } catch (e: Exception) {
                    val resTable = LuaTable()
                    resTable.set("body", LuaValue.valueOf(""))
                    resTable.set("code", LuaValue.valueOf(500))
                    resTable
                }
            }
        })
        globals.set("Request", requestTable)

        // Inject Node global
        val nodeTable = LuaTable()
        nodeTable.set("wrap", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val html = arg.tojstring()
                val doc = Jsoup.parse(html)
                return wrapElement(doc.body())
            }
        })
        globals.set("Node", nodeTable)

        // Inject Document / Jsoup global
        val docTable = LuaTable()
        docTable.set("parse", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val html = arg.tojstring()
                val doc = Jsoup.parse(html)
                val docObj = wrapElement(doc)
                docObj.set("title", object : OneArgFunction() {
                    override fun call(self: LuaValue): LuaValue = LuaValue.valueOf(doc.title())
                })
                return docObj
            }
        })
        globals.set("Document", docTable)
        globals.set("Jsoup", docTable)

        return globals
    }

    fun loadSource(
        pkgName: String,
        luaFile: File,
        name: String,
        categoryHint: MediaCategory? = null
    ): Source? {
        if (!luaFile.exists()) return null
        val scriptContent = luaFile.readText()
        return LuaSourceAdapter(
            pkgName = pkgName,
            name = name,
            scriptContent = scriptContent,
            engine = this,
            categoryHint = categoryHint
        )
    }
}

class LuaSourceAdapter(
    val pkgName: String,
    override val name: String,
    val scriptContent: String,
    private val engine: LuaRuntimeEngine,
    override val categoryHint: MediaCategory? = null
) : Source {

    override val id: String = "lua_${pkgName.replace('.', '_')}"
    override val baseUrl: String = ""
    override val urlPattern: Regex = Regex("^https?://.*", RegexOption.IGNORE_CASE)
    override val isSearchable: Boolean = true

    override suspend fun scrape(url: String): ScrapedMetadata? = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(15_000L) {
                val globals = engine.createGlobals()
                globals.load(scriptContent).call()
                val parseFn = globals.get("parse")
                val result = if (parseFn.isfunction()) {
                    parseFn.call(LuaValue.valueOf(url))
                } else null

                if (result != null && !result.isnil()) {
                    val title = if (result.istable()) result.get("title").tojstring() else result.tojstring()
                    ScrapedMetadata(
                        title = title,
                        imageUrl = "",
                        category = categoryHint ?: MediaCategory.NOVEL
                    )
                } else null
            }
        }.getOrNull()
    }

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(15_000L) {
                val globals = engine.createGlobals()
                globals.load(scriptContent).call()
                val searchFn = globals.get("search")
                if (searchFn.isfunction()) {
                    val result = searchFn.call(LuaValue.valueOf(query))
                    if (result.istable()) {
                        val table = result.checktable()
                        val results = mutableListOf<SearchResult>()
                        for (k in table.keys()) {
                            val item = table.get(k)
                            if (item.istable()) {
                                results.add(
                                    SearchResult(
                                        title = item.get("title").tojstring(),
                                        url = item.get("url").tojstring()
                                    )
                                )
                            } else if (!item.isnil()) {
                                results.add(SearchResult(title = item.tojstring(), url = ""))
                            }
                        }
                        results
                    } else if (!result.isnil()) {
                        listOf(SearchResult(title = result.tojstring(), url = ""))
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
        }.getOrElse { emptyList() }
    }
}
