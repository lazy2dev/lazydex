package app.lazydex.extension.runtime

import android.content.Context
import android.content.pm.PackageManager
import app.lazydex.domain.model.MediaCategory
import app.lazydex.scraper.source.ScrapedMetadata
import app.lazydex.scraper.source.SearchResult
import app.lazydex.scraper.source.Source
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap

class DexRuntimeEngine(
    private val context: Context
) {
    private val classLoaderCache = ConcurrentHashMap<String, ClassLoader>()

    fun unload(pkgName: String) {
        classLoaderCache.remove(pkgName)
    }

    fun getOrLoadClassLoader(pkgName: String, apkFile: File): ClassLoader {
        return classLoaderCache.computeIfAbsent(pkgName) {
            // Android 14+ DCL Security requirement: File MUST be set to read-only
            if (apkFile.exists() && apkFile.canWrite()) {
                apkFile.setReadOnly()
            }
            val parentLoader = runCatching { context.classLoader }.getOrNull() ?: javaClass.classLoader
            createIsolatedClassLoader(apkFile, parentLoader)
        }
    }

    fun extractEntryClassName(apkFile: File): String? {
        return try {
            val pm = runCatching { context.packageManager }.getOrNull() ?: return null
            val packageInfo = pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_META_DATA
            )
            val meta = packageInfo?.applicationInfo?.metaData
            meta?.getString("tachiyomi.extension.class")
                ?: meta?.getString("ireader.extension.class")
                ?: meta?.getString("aniyomi.extension.class")
        } catch (_: Throwable) {
            null
        }
    }

    fun loadSource(
        pkgName: String,
        apkFile: File,
        entryClassHint: String? = null,
        categoryHint: MediaCategory? = null
    ): Source? {
        if (!apkFile.exists()) return null

        val classLoader = getOrLoadClassLoader(pkgName, apkFile)
        val entryClassName = entryClassHint ?: extractEntryClassName(apkFile) ?: return null

        return try {
            val loadedClass = Class.forName(entryClassName, false, classLoader)
            val instance = loadedClass.getDeclaredConstructor().newInstance()
            DexSourceAdapter(
                pkgName = pkgName,
                entryClass = entryClassName,
                instance = instance,
                categoryHint = categoryHint
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun createIsolatedClassLoader(file: File, parent: ClassLoader): ClassLoader {
        return try {
            val dexClass = Class.forName("dalvik.system.DexClassLoader")
            val constructor = dexClass.getConstructor(
                String::class.java,
                String::class.java,
                String::class.java,
                ClassLoader::class.java
            )
            val cacheDir = runCatching { context.codeCacheDir.absolutePath }.getOrNull() ?: file.parentFile?.absolutePath
            constructor.newInstance(
                file.absolutePath,
                cacheDir,
                null,
                parent
            ) as ClassLoader
        } catch (_: ClassNotFoundException) {
            // JVM host unit test environment fallback
            URLClassLoader(arrayOf(file.toURI().toURL()), parent)
        }
    }
}

class DexSourceAdapter(
    val pkgName: String,
    val entryClass: String,
    private val instance: Any,
    override val categoryHint: MediaCategory? = null
) : Source {

    override val id: String = "dex_${pkgName.replace('.', '_')}"

    override val name: String by lazy {
        when (instance) {
            is eu.kanade.tachiyomi.source.Source -> instance.name
            is eu.kanade.tachiyomi.animesource.AnimeSource -> instance.name
            is ireader.core.source.Source -> instance.name
            else -> {
                runCatching {
                    instance.javaClass.getMethod("getName").invoke(instance) as? String
                }.getOrNull()
                    ?: runCatching {
                        instance.javaClass.getMethod("name").invoke(instance) as? String
                    }.getOrNull()
                    ?: pkgName.substringAfterLast('.')
            }
        }
    }

    override val baseUrl: String by lazy {
        when (instance) {
            is eu.kanade.tachiyomi.source.online.HttpSource -> instance.baseUrl
            is eu.kanade.tachiyomi.animesource.online.AnimeHttpSource -> instance.baseUrl
            is ireader.core.source.HttpSource -> instance.baseUrl
            else -> {
                runCatching {
                    instance.javaClass.getMethod("getBaseUrl").invoke(instance) as? String
                }.getOrNull()
                    ?: runCatching {
                        instance.javaClass.getMethod("baseUrl").invoke(instance) as? String
                    }.getOrNull()
                    ?: ""
            }
        }
    }

    override val urlPattern: Regex by lazy {
        if (baseUrl.isNotBlank()) {
            val escaped = Regex.escape(baseUrl.trimEnd('/'))
            Regex("^$escaped/.*", RegexOption.IGNORE_CASE)
        } else {
            Regex("^https?://.*", RegexOption.IGNORE_CASE)
        }
    }

    override val isSearchable: Boolean = true

    override suspend fun scrape(url: String): ScrapedMetadata? = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(15_000L) {
                when (instance) {
                    is eu.kanade.tachiyomi.source.Source -> {
                        val manga = eu.kanade.tachiyomi.source.model.SManga.create()
                        manga.url = url.removePrefix(baseUrl)
                        val details = instance.getMangaDetails(manga)
                        ScrapedMetadata(
                            title = details.title.ifBlank { name },
                            imageUrl = details.thumbnail_url.orEmpty(),
                            author = details.author.orEmpty(),
                            description = details.description.orEmpty(),
                            genres = details.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                            category = categoryHint ?: MediaCategory.MANGA
                        )
                    }
                    is eu.kanade.tachiyomi.animesource.AnimeSource -> {
                        val anime = eu.kanade.tachiyomi.animesource.model.SAnime.create()
                        anime.url = url.removePrefix(baseUrl)
                        val details = instance.getAnimeDetails(anime)
                        ScrapedMetadata(
                            title = details.title.ifBlank { name },
                            imageUrl = details.thumbnail_url.orEmpty(),
                            author = details.author.orEmpty(),
                            description = details.description.orEmpty(),
                            genres = details.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                            category = categoryHint ?: MediaCategory.ANIME
                        )
                    }
                    is ireader.core.source.Source -> {
                        val book = ireader.core.source.model.BookInfo(title = "", key = url.removePrefix(baseUrl))
                        val details = instance.getBookDetails(book)
                        ScrapedMetadata(
                            title = details.title.ifBlank { name },
                            imageUrl = details.cover,
                            author = details.author,
                            description = details.description,
                            genres = details.genres,
                            category = categoryHint ?: MediaCategory.NOVEL
                        )
                    }
                    else -> null
                }
            }
        }.getOrNull()
    }

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(15_000L) {
                when (instance) {
                    is CatalogueSource -> {
                        val page = instance.getSearchManga(1, query, FilterList())
                        page.mangas.map { m ->
                            SearchResult(
                                title = m.title,
                                url = if (m.url.startsWith("http")) m.url else "$baseUrl${m.url}",
                                imageUrl = m.thumbnail_url,
                                author = m.author,
                                description = m.description
                            )
                        }
                    }
                    is eu.kanade.tachiyomi.animesource.AnimeCatalogueSource -> {
                        val page = instance.getSearchAnime(1, query, FilterList())
                        page.animes.map { a ->
                            SearchResult(
                                title = a.title,
                                url = if (a.url.startsWith("http")) a.url else "$baseUrl${a.url}",
                                imageUrl = a.thumbnail_url,
                                author = a.author,
                                description = a.description
                            )
                        }
                    }
                    is ireader.core.source.Source -> {
                        val page = instance.getSearch(1, query, ireader.core.source.model.CommandList())
                        page.books.map { b ->
                            SearchResult(
                                title = b.title,
                                url = if (b.key.startsWith("http")) b.key else "$baseUrl${b.key}",
                                imageUrl = b.cover,
                                author = b.author,
                                description = b.description
                            )
                        }
                    }
                    else -> emptyList()
                }
            }
        }.getOrElse { emptyList() }
    }
}
