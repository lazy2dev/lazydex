package app.lazydex.util

import okhttp3.HttpUrl

object UrlNormalizer {
    /**
     * Canonical URL normalization for duplicate detection + scrape validation.
     * Uses OkHttp's HttpUrl to parse and normalize.
     * - Lowercase scheme + host (done by HttpUrl automatically)
     * - Trim whitespace
     * - Remove trailing slash
     * - Strip fragment (#...)
     */
    fun normalize(url: String): String {
        val trimmed = url.trim()
        val httpUrl = HttpUrl.parse(trimmed) ?: return trimmed
        return httpUrl.newBuilder()
            .fragment(null)
            .build()
            .toString()
            .removeSuffix("/")
    }
}
