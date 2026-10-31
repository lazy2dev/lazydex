package app.lazydex.scraper.source

import app.lazydex.domain.model.MediaCategory

data class ScrapedMetadata(
    val title: String,
    val imageUrl: String,
    val alternativeTitles: List<String> = emptyList(),
    val author: String = "",
    val description: String = "",
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val category: MediaCategory? = null,
    val status: String? = null
)
