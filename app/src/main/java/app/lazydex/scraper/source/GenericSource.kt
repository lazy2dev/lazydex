package app.lazydex.scraper.source

import app.lazydex.domain.model.MediaCategory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class GenericSource(private val okHttpClient: OkHttpClient) : Source {
    override val id: String = "generic"
    override val name: String = "Web Source (Fallback)"
    override val baseUrl: String = ""
    override val urlPattern: Regex = Regex(""".*""")
    override val isSearchable: Boolean = false
    override val categoryHint: MediaCategory? = null

    override suspend fun scrape(url: String): ScrapedMetadata? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val doc = Jsoup.parse(body, url)

                val ogTitle = doc.select("meta[property=og:title]").attr("content").trim()
                val twTitle = doc.select("meta[name=twitter:title]").attr("content").trim()
                val h1Title = doc.select("h1").firstOrNull()?.text().orEmpty().trim()
                val docTitle = doc.title().trim()

                var title = ogTitle.ifBlank { twTitle.ifBlank { h1Title.ifBlank { docTitle } } }
                if (title.contains(" - ")) {
                    title = title.substringBeforeLast(" - ").trim()
                } else if (title.contains(" | ")) {
                    title = title.substringBeforeLast(" | ").trim()
                }

                if (title.isBlank() ||
                    title.equals("Untitled", ignoreCase = true) ||
                    title.contains("Just a moment", ignoreCase = true) ||
                    title.contains("Attention Required", ignoreCase = true)
                ) {
                    return null
                }

                val imageUrl = doc.select("meta[property=og:image]").attr("content").ifBlank {
                    doc.select("meta[name=twitter:image]").attr("content").ifBlank {
                        doc.select("img.cover, .novel-cover img, .book-cover img, .fiction-info img, [itemprop=image], .entry-thumb img, .post-thumb img")
                            .let { el ->
                                el.attr("abs:src").ifBlank { el.attr("abs:data-src").ifBlank { el.attr("abs:data-lazy-src") } }
                            }
                    }
                }.trim()

                val rawAuthor = doc.select("meta[name=author]").attr("content").ifBlank {
                    doc.select("meta[property=book:author]").attr("content").ifBlank {
                        doc.select("[itemprop=author], .author, .novel-author, .author-name, .author-content, span.author, .entry-author, .author a, .novel-author a, a[href*=/author/], .writer a").firstOrNull()?.text().orEmpty()
                    }
                }.trim()
                val author = rawAuthor.replace(Regex("(?i)^author\\s*[:\\-]?\\s*"), "").trim()

                val domDesc = listOf(
                    ".desc-text",
                    ".summary__content",
                    ".description-summary",
                    ".novel-desc",
                    ".novel-description",
                    ".novel-summary",
                    "#tab-description",
                    "#editdescription",
                    ".wi_fic_desc",
                    "#synopsis",
                    ".synopsis",
                    ".synopsis-content",
                    "#novel-synopsis",
                    "#description",
                    ".description",
                    "[itemprop=description]",
                    ".entry-content p"
                ).firstNotNullOfOrNull { selector ->
                    doc.select(selector).takeIf { it.isNotEmpty() }?.let { elements ->
                        val text = elements.joinToString("\n\n") { it.wholeText().trim() }
                            .lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n\n")
                        text.ifBlank { elements.text().trim() }
                    }?.takeIf { it.isNotBlank() }
                }
                val metaDesc = doc.select("meta[property=og:description]").attr("content").ifBlank {
                    doc.select("meta[name=description]").attr("content")
                }.trim()
                val description = (domDesc ?: metaDesc).trim()

                val domGenres = doc.select("[itemprop=genre], .genres a, .genre a, .genres-content a, .novel-tags a, .novel-genres a, a[href*=/genre/], a[href*=/tag/], a[href*=/genres/], a[href*=/tags/], .tag a, .tags a, .series-genre a")
                    .map { it.text().trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                val keywords = if (domGenres.isNotEmpty()) {
                    domGenres
                } else {
                    doc.select("meta[name=keywords]").attr("content")
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }

                val rawStatus = listOf(
                    "meta[property=og:novel:status]",
                    "meta[name=status]",
                    ".status-item",
                    ".post-status",
                    ".novel-status",
                    ".series-status",
                    ".meta-status",
                    "[itemprop=status]",
                    ".post-content_item:contains(Status) .summary-content",
                    ".completed",
                    ".ongoing"
                ).firstNotNullOfOrNull { selector ->
                    val el = doc.select(selector).firstOrNull() ?: return@firstNotNullOfOrNull null
                    val content = el.attr("content").takeIf { it.isNotBlank() } ?: el.text()
                    content.trim().takeIf { it.isNotBlank() }
                }
                val status = rawStatus?.replace(Regex("(?i)^status\\s*[:\\-]?\\s*"), "")?.trim()?.takeIf { it.isNotBlank() && it.length < 50 }

                ScrapedMetadata(
                    title = title,
                    imageUrl = imageUrl,
                    author = author,
                    description = description,
                    genres = keywords,
                    status = status
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResult> = emptyList()
}
