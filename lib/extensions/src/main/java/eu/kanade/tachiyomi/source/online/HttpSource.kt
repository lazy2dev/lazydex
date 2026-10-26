package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

abstract class HttpSource : CatalogueSource {
    abstract val baseUrl: String

    open val client: OkHttpClient = OkHttpClient()
    open val headers: Headers = Headers.Builder().build()

    open fun popularMangaRequest(page: Int): Request = Request.Builder().url("$baseUrl/popular/$page").headers(headers).build()
    open fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = Request.Builder().url("$baseUrl/search?q=$query&page=$page").headers(headers).build()
    open fun latestUpdatesRequest(page: Int): Request = Request.Builder().url("$baseUrl/latest/$page").headers(headers).build()

    open fun popularMangaParse(response: Response): MangasPage = MangasPage(emptyList(), false)
    open fun searchMangaParse(response: Response): MangasPage = MangasPage(emptyList(), false)
    open fun latestUpdatesParse(response: Response): MangasPage = MangasPage(emptyList(), false)

    override suspend fun getPopularManga(page: Int): MangasPage = popularMangaParse(client.newCall(popularMangaRequest(page)).execute())
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = searchMangaParse(client.newCall(searchMangaRequest(page, query, filters)).execute())
    override suspend fun getLatestUpdates(page: Int): MangasPage = latestUpdatesParse(client.newCall(latestUpdatesRequest(page)).execute())
}
