package app.lazydex.scraper.source

import app.lazydex.domain.model.MediaType

interface Source {
    val id: String
    val name: String
    val baseUrl: String
    val urlPattern: Regex
    val isSearchable: Boolean
    val typeHint: MediaType?

    suspend fun scrape(url: String): ScrapedMetadata?
    suspend fun search(query: String): List<SearchResult>
    suspend fun getPopular(page: Int = 1): List<SearchResult> = emptyList()
}
