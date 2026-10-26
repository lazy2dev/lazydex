package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

abstract class AnimeHttpSource : AnimeCatalogueSource {
    abstract val baseUrl: String

    open val client: OkHttpClient = OkHttpClient()
    open val headers: Headers = Headers.Builder().build()

    open fun popularAnimeRequest(page: Int): Request = Request.Builder().url("$baseUrl/popular/$page").headers(headers).build()
    open fun searchAnimeRequest(page: Int, query: String, filters: FilterList): Request = Request.Builder().url("$baseUrl/search?q=$query&page=$page").headers(headers).build()
    open fun latestUpdatesRequest(page: Int): Request = Request.Builder().url("$baseUrl/latest/$page").headers(headers).build()

    open fun popularAnimeParse(response: Response): AnimesPage = AnimesPage(emptyList(), false)
    open fun searchAnimeParse(response: Response): AnimesPage = AnimesPage(emptyList(), false)
    open fun latestUpdatesParse(response: Response): AnimesPage = AnimesPage(emptyList(), false)

    override suspend fun getPopularAnime(page: Int): AnimesPage = popularAnimeParse(client.newCall(popularAnimeRequest(page)).execute())
    override suspend fun getSearchAnime(page: Int, query: String, filters: FilterList): AnimesPage = searchAnimeParse(client.newCall(searchAnimeRequest(page, query, filters)).execute())
    override suspend fun getLatestUpdates(page: Int): AnimesPage = latestUpdatesParse(client.newCall(latestUpdatesRequest(page)).execute())
}
