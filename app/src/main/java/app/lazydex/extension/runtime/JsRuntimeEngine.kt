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
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File

class JsRuntimeEngine(
    private val okHttpClient: OkHttpClient
) {
    fun createBridge(scriptContent: String): JsExecutor {
        return try {
            // Test if QuickJS native library is available on current platform
            val qjsClass = Class.forName("app.cash.quickjs.QuickJs")
            val method = qjsClass.getMethod("create")
            val qjsInstance = method.invoke(null) as AutoCloseable
            QuickJsExecutor(qjsInstance, scriptContent, okHttpClient)
        } catch (_: Throwable) {
            // Pure JVM fallback (Rhino) for environments without QuickJS native .so
            RhinoJsExecutor(scriptContent, okHttpClient)
        }
    }

    fun loadSource(
        pkgName: String,
        scriptFile: File,
        name: String,
        categoryHint: MediaCategory? = null
    ): Source? {
        if (!scriptFile.exists()) return null
        val scriptContent = scriptFile.readText()
        return JsSourceAdapter(
            pkgName = pkgName,
            name = name,
            scriptContent = scriptContent,
            engine = this,
            categoryHint = categoryHint
        )
    }
}

interface JsExecutor : AutoCloseable {
    fun evaluate(script: String): Any?
    fun callFunction(name: String, vararg args: Any?): Any?
}

interface QuickJsHostBridge {
    fun fetch(url: String): String
    fun parseHtmlTitle(html: String): String
    fun selectText(html: String, css: String): String
    fun selectAttr(html: String, css: String, attr: String): String
}

class QuickJsExecutor(
    private val quickJs: AutoCloseable,
    script: String,
    private val okHttpClient: OkHttpClient
) : JsExecutor {

    init {
        val bridge = object : QuickJsHostBridge {
            override fun fetch(url: String): String {
                return try {
                    val req = Request.Builder().url(url).build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        resp.body?.string().orEmpty()
                    }
                } catch (_: Exception) {
                    ""
                }
            }

            override fun parseHtmlTitle(html: String): String {
                return try {
                    Jsoup.parse(html).title()
                } catch (_: Exception) {
                    ""
                }
            }

            override fun selectText(html: String, css: String): String {
                return try {
                    Jsoup.parse(html).selectFirst(css)?.text().orEmpty()
                } catch (_: Exception) {
                    ""
                }
            }

            override fun selectAttr(html: String, css: String, attr: String): String {
                return try {
                    Jsoup.parse(html).selectFirst(css)?.attr(attr).orEmpty()
                } catch (_: Exception) {
                    ""
                }
            }
        }

        runCatching {
            val setMethod = quickJs.javaClass.getMethod("set", String::class.java, Class::class.java, Any::class.java)
            setMethod.invoke(quickJs, "_host", QuickJsHostBridge::class.java, bridge)
        }

        // QuickJs reflection to stay decoupled from missing native symbols in test JVM
        val evalMethod = quickJs.javaClass.getMethod("evaluate", String::class.java)
        // Inject JS helper shim
        val shim = """
            var global = this;
            var console = { log: function() {} };
            function fetch(url) {
                var body = typeof _host !== 'undefined' ? _host.fetch(url) : "";
                return {
                    text: function() { return body; },
                    json: function() { return JSON.parse(body); },
                    status: 200
                };
            }
            function parseHtmlTitle(html) {
                return typeof _host !== 'undefined' ? _host.parseHtmlTitle(html) : "";
            }
        """.trimIndent()
        evalMethod.invoke(quickJs, shim)
        evalMethod.invoke(quickJs, script)
    }

    override fun evaluate(script: String): Any? {
        val evalMethod = quickJs.javaClass.getMethod("evaluate", String::class.java)
        return evalMethod.invoke(quickJs, script)
    }

    override fun callFunction(name: String, vararg args: Any?): Any? {
        val argsStr = args.joinToString(",") { arg ->
            when (arg) {
                is String -> "\"${arg.replace("\"", "\\\"")}\""
                null -> "null"
                else -> arg.toString()
            }
        }
        return evaluate("$name($argsStr)")
    }

    override fun close() {
        quickJs.close()
    }
}

class RhinoJsExecutor(
    private val script: String,
    private val okHttpClient: OkHttpClient
) : JsExecutor {
    private val cx: Context = Context.enter().apply {
        optimizationLevel = -1 // Interpreted mode for quick execution
        languageVersion = Context.VERSION_ES6
    }
    private val scope: Scriptable = cx.initStandardObjects()

    init {
        // Inject host bridge: fetch & parseHtml
        val hostBridge = object {
            @Suppress("unused")
            fun fetch(url: String): String {
                return try {
                    val req = Request.Builder().url(url).build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        resp.body?.string().orEmpty()
                    }
                } catch (_: Exception) {
                    ""
                }
            }

            @Suppress("unused")
            fun parseHtmlTitle(html: String): String {
                return try {
                    Jsoup.parse(html).title()
                } catch (_: Exception) {
                    ""
                }
            }

            @Suppress("unused")
            fun selectText(html: String, css: String): String {
                return try {
                    Jsoup.parse(html).selectFirst(css)?.text().orEmpty()
                } catch (_: Exception) {
                    ""
                }
            }

            @Suppress("unused")
            fun selectAttr(html: String, css: String, attr: String): String {
                return try {
                    Jsoup.parse(html).selectFirst(css)?.attr(attr).orEmpty()
                } catch (_: Exception) {
                    ""
                }
            }
        }

        ScriptableObject.putProperty(scope, "_host", Context.javaToJS(hostBridge, scope))
        cx.evaluateString(
            scope,
            """
            var global = this;
            var console = { log: function() {} };
            function fetch(url) {
                var body = _host.fetch(url);
                return {
                    text: function() { return body; },
                    json: function() { return JSON.parse(body); },
                    status: 200
                };
            }
            function parseHtmlTitle(html) {
                return _host.parseHtmlTitle(html);
            }
            """.trimIndent(),
            "bridge.js",
            1,
            null
        )
        cx.evaluateString(scope, script, "script.js", 1, null)
    }

    override fun evaluate(script: String): Any? {
        return cx.evaluateString(scope, script, "eval.js", 1, null)
    }

    override fun callFunction(name: String, vararg args: Any?): Any? {
        val fn = scope.get(name, scope)
        if (fn is Function) {
            val jsArgs = args.map { Context.javaToJS(it, scope) }.toTypedArray()
            val raw = fn.call(cx, scope, scope, jsArgs)
            return if (raw is org.mozilla.javascript.Wrapper) {
                raw.unwrap()
            } else {
                Context.jsToJava(raw, Any::class.java)
            }
        }
        return null
    }

    override fun close() {
        Context.exit()
    }
}

class JsSourceAdapter(
    val pkgName: String,
    override val name: String,
    val scriptContent: String,
    private val engine: JsRuntimeEngine,
    override val categoryHint: MediaCategory? = null
) : Source {

    override val id: String = "js_${pkgName.replace('.', '_')}"
    override val baseUrl: String = ""
    override val urlPattern: Regex = Regex("^https?://.*", RegexOption.IGNORE_CASE)
    override val isSearchable: Boolean = true

    override suspend fun scrape(url: String): ScrapedMetadata? = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(15_000L) {
                engine.createBridge(scriptContent).use { executor ->
                    val result = executor.callFunction("scrape", url)
                        ?: executor.callFunction("parseNovel", url)
                        ?: executor.callFunction("parse", url)

                    if (result != null) {
                        ScrapedMetadata(
                            title = result.toString(),
                            imageUrl = "",
                            category = categoryHint ?: MediaCategory.NOVEL
                        )
                    } else null
                }
            }
        }.getOrNull()
    }

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(15_000L) {
                engine.createBridge(scriptContent).use { executor ->
                    val result = executor.callFunction("search", query)
                        ?: executor.callFunction("searchNovels", query, 1)

                    if (result is List<*>) {
                        result.mapNotNull { item ->
                            if (item != null) {
                                SearchResult(
                                    title = item.toString(),
                                    url = ""
                                )
                            } else null
                        }
                    } else if (result != null) {
                        listOf(SearchResult(title = result.toString(), url = ""))
                    } else {
                        emptyList()
                    }
                }
            }
        }.getOrElse { emptyList() }
    }
}
