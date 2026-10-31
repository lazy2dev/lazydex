package app.lazydex.scraper.source

data class SearchResult(
    val title: String,
    val url: String,
    val imageUrl: String? = null,
    val author: String? = null,
    val description: String? = null,
    val status: String? = null
)
