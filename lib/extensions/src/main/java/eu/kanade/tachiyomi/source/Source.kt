package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

interface Source {
    val id: Long
    val name: String
    val lang: String
    val supportsLatest: Boolean get() = false

    suspend fun getMangaDetails(manga: SManga): SManga = manga
    suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
}

interface CatalogueSource : Source {
    suspend fun getPopularManga(page: Int): MangasPage
    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage
    suspend fun getLatestUpdates(page: Int): MangasPage
}

typealias HttpSource = eu.kanade.tachiyomi.source.online.HttpSource
