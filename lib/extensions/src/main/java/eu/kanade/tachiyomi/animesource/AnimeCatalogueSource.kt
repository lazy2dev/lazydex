package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.source.model.FilterList
interface AnimeSource {
    val id: Long
    val name: String
    val lang: String
    val supportsLatest: Boolean get() = false

    suspend fun getAnimeDetails(anime: SAnime): SAnime = anime
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = emptyList()
}

interface AnimeCatalogueSource : AnimeSource {
    suspend fun getPopularAnime(page: Int): AnimesPage
    suspend fun getSearchAnime(page: Int, query: String, filters: FilterList): AnimesPage
    suspend fun getLatestUpdates(page: Int): AnimesPage
}
