package app.lazydex.extension.runtime

import app.lazydex.domain.model.MediaCategory
import app.lazydex.scraper.source.ScrapedMetadata
import app.lazydex.scraper.source.SearchResult
import app.lazydex.scraper.source.Source
import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.types.Value
import com.dylibso.chicory.wasm.types.ValueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets

class WasmRuntimeEngine(
    private val okHttpClient: OkHttpClient
) {

    fun createHostImports(): ImportValues {
        val builder = ImportValues.builder()

        // 1. env.print_str(ptr: i32, len: i32)
        builder.addFunction(
            HostFunction(
                "env",
                "print_str",
                listOf(ValueType.I32, ValueType.I32),
                emptyList()
            ) { _, _ -> longArrayOf() }
        )

        // 2. aidoku.print(ptr: i32, len: i32)
        builder.addFunction(
            HostFunction(
                "aidoku",
                "print",
                listOf(ValueType.I32, ValueType.I32),
                emptyList()
            ) { _, _ -> longArrayOf() }
        )

        // 3. aidoku.request(url_ptr: i32, url_len: i32) -> i32 (descriptor)
        builder.addFunction(
            HostFunction(
                "aidoku",
                "request",
                listOf(ValueType.I32, ValueType.I32),
                listOf(ValueType.I32)
            ) { instance, args ->
                val ptr = args[0].toInt()
                val len = args[1].toInt()
                val bytes = instance.memory().readBytes(ptr, len)
                val url = String(bytes, StandardCharsets.UTF_8)
                val status = try {
                    val req = Request.Builder().url(url).build()
                    val resp = okHttpClient.newCall(req).execute()
                    resp.code
                } catch (_: Exception) {
                    500
                }
                longArrayOf(status.toLong())
            }
        )

        // 4. aidoku.destroy(descriptor: i32)
        builder.addFunction(
            HostFunction(
                "aidoku",
                "destroy",
                listOf(ValueType.I32),
                emptyList()
            ) { _, _ -> longArrayOf() }
        )

        return builder.build()
    }

    fun loadSource(
        pkgName: String,
        wasmFile: File,
        name: String,
        categoryHint: MediaCategory? = null
    ): Source? {
        if (!wasmFile.exists()) return null
        val bytes = wasmFile.readBytes()
        return try {
            val wasmModule = Parser.parse(ByteArrayInputStream(bytes))
            val imports = createHostImports()
            val instance = Instance.builder(wasmModule).withImportValues(imports).build()
            WasmSourceAdapter(
                pkgName = pkgName,
                name = name,
                instance = instance,
                categoryHint = categoryHint
            )
        } catch (_: Throwable) {
            null
        }
    }
}

class WasmSourceAdapter(
    val pkgName: String,
    override val name: String,
    private val instance: Instance,
    override val categoryHint: MediaCategory? = null
) : Source {

    override val id: String = "wasm_${pkgName.replace('.', '_')}"
    override val baseUrl: String = ""
    override val urlPattern: Regex = Regex("^https?://.*", RegexOption.IGNORE_CASE)
    override val isSearchable: Boolean = true

    override suspend fun scrape(url: String): ScrapedMetadata? = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(15_000L) {
                val exportFn = instance.export("get_manga_details")
                    ?: instance.export("scrape")
                    ?: instance.export("parse")

                if (exportFn != null) {
                    ScrapedMetadata(
                        title = name,
                        imageUrl = "",
                        category = categoryHint ?: MediaCategory.MANGA
                    )
                } else null
            }
        }.getOrNull()
    }

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(15_000L) {
                val exportFn = instance.export("get_manga_list")
                    ?: instance.export("search")

                if (exportFn != null) {
                    listOf(
                        SearchResult(
                            title = "$name: $query",
                            url = ""
                        )
                    )
                } else emptyList()
            }
        }.getOrElse { emptyList() }
    }
}
