package app.lazydex.scraper.source

import app.lazydex.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class GenericSource(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : Source {

    override val id: String = "generic"
    override val name: String = "Universal Web Scraper"
    override val baseUrl: String = ""
    override val urlPattern: Regex = Regex("""^https?://.*""")
    override val isSearchable: Boolean = false
    override val typeHint: MediaType? = null

    override suspend fun search(query: String): List<SearchResult> = emptyList()

    override suspend fun scrape(url: String): ScrapedMetadata? = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull() ?: return@withContext null
        val host = httpUrl.host.lowercase()

        // Tier 1: 1-Hop Public API Interception for SPAs
        // 1. AniList Tier 1 GraphQL Interception
        if (host.contains("anilist.co")) {
            val match = Regex("""/(anime|manga)/(\d+)""").find(httpUrl.encodedPath)
            if (match != null) {
                val mediaTypeStr = match.groupValues[1]
                val mediaId = match.groupValues[2].toIntOrNull()
                if (mediaId != null) {
                    val result = scrapeAniListGraphQL(mediaId, mediaTypeStr)
                    if (result != null) return@withContext result
                }
            }
        }

        // 2. MangaDex Tier 1 REST Interception
        if (host.contains("mangadex.org")) {
            val match = Regex("""/title/([a-f0-9\-]+)""").find(httpUrl.encodedPath)
            if (match != null) {
                val mangaId = match.groupValues[1]
                val result = scrapeMangaDexApi(mangaId)
                if (result != null) return@withContext result
            }
        }

        // Build request with modern Chromium headers
        val request = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Sec-Ch-Ua", """"Chromium";v="124", "Google Chrome";v="124"""")
            .header("Sec-Ch-Ua-Mobile", "?0")
            .header("Sec-Ch-Ua-Platform", """"Windows"""")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-User", "?1")
            .header("Upgrade-Insecure-Requests", "1")
            .header("Referer", "${httpUrl.scheme}://${httpUrl.host}/")
            .build()

        client.newCall(request).execute().use { response ->
            val code = response.code
            val serverHeader = response.header("Server") ?: ""
            val cfRay = response.header("cf-ray")

            // Active Cloudflare & Anti-Bot Detection (HTTP 403, 429, 503)
            if (code in listOf(403, 429, 503)) {
                val sample = response.peekBody(1024 * 10).string().lowercase()
                if (cfRay != null || serverHeader.contains("cloudflare", ignoreCase = true) ||
                    sample.contains("just a moment") || sample.contains("cf-browser-verification") ||
                    sample.contains("challenge-error-title") || sample.contains("attention required")
                ) {
                    throw AntiBotChallengeException("Protected by Cloudflare bot challenge. Please use manual entry.")
                }
                throw HttpFailureException(code, "HTTP request failed with code $code")
            }

            if (!response.isSuccessful) {
                throw HttpFailureException(code, "HTTP request failed with code $code")
            }

            val html = response.body?.string() ?: throw HttpFailureException(code, "Empty response body")
            val document = Jsoup.parse(html, url)

            // Cloudflare Turnstile / Challenge Payload on HTTP 200 OK
            val docTitle = document.title().trim()
            val isChallengePayload = docTitle.contains("Just a moment...", ignoreCase = true) ||
                docTitle.contains("Attention Required", ignoreCase = true) ||
                docTitle.contains("Verify you are human", ignoreCase = true) ||
                document.select("#challenge-running, #cf-challenge-running, .cf-browser-verification").isNotEmpty()
            if (isChallengePayload) {
                throw AntiBotChallengeException("Protected by Cloudflare bot challenge (HTTP 200). Please use manual entry.")
            }

            // Tier 2: Schema.org JSON-LD Traversal (@graph and polymorphism unwrapping)
            val jsonLdResult = extractSchemaOrgJsonLd(document)
            if (jsonLdResult != null) return@withContext jsonLdResult

            // Tier 3: SPA SSR Hydration State Extraction (__NEXT_DATA__)
            val nextDataResult = extractNextDataHydration(document)
            if (nextDataResult != null) return@withContext nextDataResult

            // Tier 4 & 5: OpenGraph + Resilient DOM Selector Matrix
            return@withContext extractFromDomAndMeta(document, url)
        }
    }

    // --- Tier 1 Interceptions ---

    internal fun scrapeAniListGraphQL(mediaId: Int, mediaTypeStr: String): ScrapedMetadata? {
        val typeUpper = if (mediaTypeStr.equals("anime", ignoreCase = true)) "ANIME" else "MANGA"
        val query = """
            query (${'$'}id: Int, ${'$'}type: MediaType) {
              Media (id: ${'$'}id, type: ${'$'}type) {
                id
                title {
                  userPreferred
                  english
                  romaji
                  native
                }
                coverImage {
                  extraLarge
                  large
                }
                description(asHtml: false)
                format
                status
                episodes
                chapters
                averageScore
                startDate {
                  year
                  month
                  day
                }
                genres
                studios(isMain: true) {
                  nodes {
                    name
                  }
                }
                staff(perPage: 3) {
                  nodes {
                    name {
                      full
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val jsonBody = """
            {
              "query": ${json.encodeToString(kotlinx.serialization.serializer(), query)},
              "variables": {
                "id": $mediaId,
                "type": "$typeUpper"
              }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", "LazyDex/1.0")
            .header("Accept", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val media = root["data"]?.jsonObject?.get("Media")?.jsonObject ?: return null

                val titleObj = media["title"]?.jsonObject
                val title = titleObj?.get("userPreferred")?.jsonPrimitive?.contentOrNull
                    ?: titleObj?.get("english")?.jsonPrimitive?.contentOrNull
                    ?: titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull
                    ?: return null

                val altTitles = listOfNotNull(
                    titleObj?.get("english")?.jsonPrimitive?.contentOrNull,
                    titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull,
                    titleObj?.get("native")?.jsonPrimitive?.contentOrNull
                ).filter { it != title }.distinct()

                val coverObj = media["coverImage"]?.jsonObject
                val coverUrl = coverObj?.get("extraLarge")?.jsonPrimitive?.contentOrNull
                    ?: coverObj?.get("large")?.jsonPrimitive?.contentOrNull
                    ?: ""

                val desc = media["description"]?.jsonPrimitive?.contentOrNull
                    ?.replace(Regex("<[^>]*>"), "")
                    ?.trim()
                    ?: ""

                val author = media["studios"]?.jsonObject?.get("nodes")?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: media["staff"]?.jsonObject?.get("nodes")?.jsonArray
                        ?.firstOrNull()?.jsonObject?.get("name")?.jsonObject?.get("full")?.jsonPrimitive?.contentOrNull
                    ?: ""

                val genres = media["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val format = media["format"]?.jsonPrimitive?.contentOrNull?.uppercase()
                val inferredType = when (format) {
                    "MOVIE" -> MediaType.MOVIE
                    "TV", "TV_SHORT" -> MediaType.TV
                    "MANGA" -> MediaType.MANGA
                    "NOVEL" -> MediaType.NOVEL
                    else -> if (typeUpper == "ANIME") MediaType.ANIME else MediaType.MANGA
                }

                val statusToken = media["status"]?.jsonPrimitive?.contentOrNull
                val catalogStatus = when (statusToken?.uppercase()) {
                    "RELEASING", "NOT_YET_RELEASED" -> "ONGOING"
                    "FINISHED" -> "COMPLETED"
                    "HIATUS" -> "PAUSED"
                    "CANCELLED" -> "CANCELLED"
                    else -> statusToken
                }

                val totalItems = media["episodes"]?.jsonPrimitive?.intOrNull
                    ?: media["chapters"]?.jsonPrimitive?.intOrNull

                val score = media["averageScore"]?.jsonPrimitive?.doubleOrNull

                ScrapedMetadata(
                    title = title,
                    imageUrl = coverUrl,
                    alternativeTitles = altTitles,
                    author = author,
                    description = desc,
                    genres = genres,
                    type = inferredType,
                    status = catalogStatus,
                    totalItems = totalItems,
                    averageScore = score
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun scrapeMangaDexApi(mangaId: String): ScrapedMetadata? {
        val request = Request.Builder()
            .url("https://api.mangadex.org/manga/$mangaId?includes[]=cover_art&includes[]=author&includes[]=artist")
            .header("User-Agent", "LazyDex/1.0")
            .header("Accept", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val data = root["data"]?.jsonObject ?: return null
                val attrs = data["attributes"]?.jsonObject ?: return null

                val titleMap = attrs["title"]?.jsonObject
                val title = titleMap?.get("en")?.jsonPrimitive?.contentOrNull
                    ?: titleMap?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull
                    ?: return null

                val altTitlesList = attrs["altTitles"]?.jsonArray?.flatMap { elem ->
                    elem.jsonObject.values.mapNotNull { it.jsonPrimitive.contentOrNull }
                } ?: emptyList()

                val descMap = attrs["description"]?.jsonObject
                val desc = descMap?.get("en")?.jsonPrimitive?.contentOrNull
                    ?: descMap?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull
                    ?: ""

                var coverFileName: String? = null
                var authorName: String? = null
                val rels = data["relationships"]?.jsonArray ?: JsonArray(emptyList())
                for (rel in rels) {
                    val relObj = rel.jsonObject
                    val type = relObj["type"]?.jsonPrimitive?.contentOrNull
                    if (type == "cover_art") {
                        coverFileName = relObj["attributes"]?.jsonObject?.get("fileName")?.jsonPrimitive?.contentOrNull
                    } else if (type == "author" && authorName == null) {
                        authorName = relObj["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                    } else if (type == "artist" && authorName == null) {
                        authorName = relObj["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                    }
                }

                val coverUrl = if (!coverFileName.isNullOrBlank()) {
                    "https://uploads.mangadex.org/covers/$mangaId/$coverFileName"
                } else ""

                val tags = attrs["tags"]?.jsonArray?.mapNotNull { tagElem ->
                    tagElem.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonObject?.get("en")?.jsonPrimitive?.contentOrNull
                } ?: emptyList()

                val rawStatus = attrs["status"]?.jsonPrimitive?.contentOrNull
                val catalogStatus = when (rawStatus?.lowercase()) {
                    "ongoing" -> "ONGOING"
                    "completed" -> "COMPLETED"
                    "hiatus" -> "PAUSED"
                    "cancelled" -> "CANCELLED"
                    else -> rawStatus?.uppercase()
                }

                val lastChapter = attrs["lastChapter"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

                ScrapedMetadata(
                    title = title,
                    imageUrl = coverUrl,
                    alternativeTitles = altTitlesList.distinct(),
                    author = authorName ?: "",
                    description = desc,
                    genres = tags,
                    type = MediaType.MANGA,
                    status = catalogStatus,
                    totalItems = lastChapter
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    // --- Tier 2: Schema.org JSON-LD Traversal ---

    internal fun extractSchemaOrgJsonLd(document: Document): ScrapedMetadata? {
        val scripts = document.select("script[type=application/ld+json]")
        for (script in scripts) {
            val content = script.data().ifBlank { script.html() }.trim()
            if (content.isBlank()) continue

            try {
                val element = json.parseToJsonElement(content)
                val candidate = findSchemaCandidate(element)
                if (candidate != null) {
                    val meta = parseCandidateObject(candidate)
                    if (meta != null && meta.title.isNotBlank() && meta.title != "Untitled") {
                        return meta
                    }
                }
            } catch (_: Exception) {
                // Lenient parsing: proceed to next script or tier
            }
        }
        return null
    }

    private fun findSchemaCandidate(element: JsonElement): JsonObject? {
        return findRecognizedCandidate(element) ?: findGenericCandidate(element)
    }

    private fun findRecognizedCandidate(element: JsonElement): JsonObject? {
        return when (element) {
            is JsonObject -> {
                if (element.containsKey("@graph")) {
                    val graph = element["@graph"]
                    if (graph != null) findRecognizedCandidate(graph) else null
                } else {
                    val typeStr = when {
                        element["@type"] is JsonArray -> {
                            element["@type"]?.jsonArray?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.joinToString(" ") ?: ""
                        }
                        else -> runCatching { element["@type"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: ""
                    }
                    if (isRecognizedSchemaType(typeStr)) {
                        element
                    } else null
                }
            }
            is JsonArray -> {
                for (item in element) {
                    val candidate = findRecognizedCandidate(item)
                    if (candidate != null) return candidate
                }
                null
            }
            else -> null
        }
    }

    private fun findGenericCandidate(element: JsonElement): JsonObject? {
        return when (element) {
            is JsonObject -> {
                if (element.containsKey("@graph")) {
                    val graph = element["@graph"]
                    if (graph != null) findGenericCandidate(graph) else null
                } else {
                    if (element.containsKey("name") && (element.containsKey("image") || element.containsKey("description"))) {
                        element
                    } else null
                }
            }
            is JsonArray -> {
                for (item in element) {
                    val candidate = findGenericCandidate(item)
                    if (candidate != null) return candidate
                }
                null
            }
            else -> null
        }
    }

    private fun isRecognizedSchemaType(type: String): Boolean {
        val lower = type.lowercase()
        return lower.contains("book") || lower.contains("movie") || lower.contains("tvseries") ||
            lower.contains("tvepisode") || lower.contains("creativework") || lower.contains("visualartwork") ||
            lower.contains("videoobject") || lower.contains("animation")
    }

    private fun parseCandidateObject(obj: JsonObject): ScrapedMetadata? {
        val rawTitle = obj["name"]?.jsonPrimitive?.contentOrNull
            ?: obj["headline"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val title = cleanTitleNoise(rawTitle)

        val rawImage = when (val img = obj["image"]) {
            is JsonObject -> img["url"]?.jsonPrimitive?.contentOrNull
            is JsonArray -> {
                val first = img.firstOrNull()
                if (first is JsonObject) first["url"]?.jsonPrimitive?.contentOrNull
                else first?.jsonPrimitive?.contentOrNull
            }
            else -> img?.jsonPrimitive?.contentOrNull
        } ?: ""
        val imageUrl = cleanImageUrl(rawImage)

        val author = when (val a = obj["author"] ?: obj["creator"] ?: obj["director"]) {
            is JsonObject -> a["name"]?.jsonPrimitive?.contentOrNull ?: ""
            is JsonArray -> {
                a.mapNotNull { item ->
                    if (item is JsonObject) item["name"]?.jsonPrimitive?.contentOrNull
                    else item.jsonPrimitive.contentOrNull
                }.joinToString(", ")
            }
            else -> a?.jsonPrimitive?.contentOrNull ?: ""
        }

        val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""

        val genres = when (val g = obj["genre"]) {
            is JsonArray -> g.mapNotNull { it.jsonPrimitive.contentOrNull }
            else -> listOfNotNull(g?.jsonPrimitive?.contentOrNull)
        }

        val typeStr = obj["@type"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
        val inferredType = when {
            typeStr.contains("movie") -> MediaType.MOVIE
            typeStr.contains("tvseries") || typeStr.contains("tvepisode") -> MediaType.TV
            typeStr.contains("book") -> MediaType.NOVEL
            typeStr.contains("comic") -> MediaType.MANGA
            else -> null
        }

        return ScrapedMetadata(
            title = title,
            imageUrl = imageUrl,
            author = author,
            description = description,
            genres = genres,
            type = inferredType
        )
    }

    // --- Tier 3: SPA SSR Hydration State Extraction (__NEXT_DATA__) ---

    internal fun extractNextDataHydration(document: Document): ScrapedMetadata? {
        val script = document.select("script#__NEXT_DATA__").firstOrNull() ?: return null
        val dataStr = script.data().ifBlank { script.html() }.trim()
        if (dataStr.isBlank()) return null

        return try {
            val root = json.parseToJsonElement(dataStr).jsonObject
            val props = root["props"]?.jsonObject?.get("pageProps")?.jsonObject ?: return null

            // WTR-LAB Pattern: serie.serieData
            val serieData = props["serie"]?.jsonObject?.get("serieData")?.jsonObject
                ?: props["series"]?.jsonObject?.get("data")?.jsonObject
                ?: props["data"]?.jsonObject

            if (serieData != null) {
                val rawTitle = serieData["title"]?.jsonPrimitive?.contentOrNull
                    ?: serieData["name"]?.jsonPrimitive?.contentOrNull
                if (!rawTitle.isNullOrBlank()) {
                    val title = cleanTitleNoise(rawTitle)
                    val rawImg = serieData["image"]?.jsonPrimitive?.contentOrNull
                        ?: serieData["cover"]?.jsonPrimitive?.contentOrNull
                        ?: serieData["poster"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                    val imageUrl = cleanImageUrl(rawImg)

                    val author = serieData["author"]?.jsonPrimitive?.contentOrNull ?: ""
                    val desc = serieData["description"]?.jsonPrimitive?.contentOrNull
                        ?: serieData["summary"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                    val chapters = serieData["rawChapterCount"]?.jsonPrimitive?.intOrNull
                        ?: serieData["chapterCount"]?.jsonPrimitive?.intOrNull

                    return ScrapedMetadata(
                        title = title,
                        imageUrl = imageUrl,
                        author = author,
                        description = desc,
                        totalItems = chapters,
                        type = MediaType.NOVEL
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    // --- Tier 4 & 5: OpenGraph Meta Tags + Resilient DOM Selector Matrix ---

    internal fun extractFromDomAndMeta(document: Document, pageUrl: String): ScrapedMetadata? {
        val title = extractTitle(document) ?: return null
        val imageUrl = extractCoverImage(document)
        val author = extractAuthor(document)
        val description = extractDescription(document)
        val genres = extractGenres(document)
        val altTitles = extractAlternativeTitles(document)
        val status = extractStatus(document)

        return ScrapedMetadata(
            title = title,
            imageUrl = imageUrl,
            alternativeTitles = altTitles,
            author = author,
            description = description,
            genres = genres,
            status = status
        )
    }

    private fun extractTitle(document: Document): String? {
        val candidate = listOf(
            "h1.title-name",
            "span[itemprop=name]",
            ".seriestitlenew",
            "span.releasestitle",
            "h1.text-xl",
            ".post-title h1",
            "h1.font-bold",
            "h1.font-white",
            "h1.novel-title",
            "meta[property=og:title]",
            "meta[name=twitter:title]",
            "h1"
        ).firstNotNullOfOrNull { selector ->
            val el = document.select(selector).firstOrNull() ?: return@firstNotNullOfOrNull null
            val text = if (selector.startsWith("meta")) el.attr("content") else el.text()
            text.trim().takeIf { it.isNotBlank() }
        } ?: document.title().trim()

        if (candidate.isBlank()) return null
        val cleaned = cleanTitleNoise(candidate)
        if (cleaned.isBlank() || cleaned.equals("Untitled", ignoreCase = true)) return null
        return cleaned
    }

    private fun extractAlternativeTitles(document: Document): List<String> {
        val alts = mutableListOf<String>()

        // NovelUpdates associated names
        val nuAssociated = document.select("#editassociated").firstOrNull()
        if (nuAssociated != null) {
            val names = nuAssociated.html().split(Regex("""<br\s*/?>"""))
                .map { Jsoup.parse(it).text().trim() }
                .filter { it.isNotBlank() }
            alts.addAll(names)
        }

        // MangaUpdates associated names
        val muAssociated = document.select("div.sCat:contains(Associated Names) + div.sContent").firstOrNull()
        if (muAssociated != null) {
            val names = muAssociated.html().split(Regex("""<br\s*/?>"""))
                .map { Jsoup.parse(it).text().trim() }
                .filter { it.isNotBlank() }
            alts.addAll(names)
        }

        return alts.distinct()
    }

    private fun extractCoverImage(document: Document): String {
        // Priority 1: Site-specific high-priority cover selectors with lazyload attribute chains
        val imgElements = document.select(
            "img.lazyloaded, .cover img, div.seriesimg > img, div.sCat:contains(Image) + div.sContent img, " +
            "img[alt=poster], .summary_image img, div.cover-art-container img, figure.cover img, " +
            "img.cover, .novel-cover img, .book-cover img, .fiction-info img, [itemprop=image], .entry-thumb img, .post-thumb img"
        )

        for (el in imgElements) {
            val url = resolveImageAttributeChain(el)
            if (url.isNotBlank() && !isPlaceholderImage(url)) {
                return cleanImageUrl(url)
            }
        }

        // Priority 2: OpenGraph & Twitter tags
        val ogImage = document.select("meta[property=og:image]").attr("content").trim()
        if (ogImage.isNotBlank() && !isPlaceholderImage(ogImage)) {
            return cleanImageUrl(ogImage)
        }

        val twImage = document.select("meta[name=twitter:image]").attr("content").trim()
        if (twImage.isNotBlank() && !isPlaceholderImage(twImage)) {
            return cleanImageUrl(twImage)
        }

        return ""
    }

    private fun resolveImageAttributeChain(el: org.jsoup.nodes.Element): String {
        val attributes = listOf(
            "abs:data-src",
            "abs:data-lazy-src",
            "abs:data-original",
            "abs:data-srcset",
            "abs:src",
            "data-src",
            "data-lazy-src",
            "data-original",
            "src"
        )
        for (attr in attributes) {
            val v = el.attr(attr).trim()
            if (v.isNotBlank() && !isPlaceholderImage(v)) {
                return if (attr.startsWith("abs:")) v else el.absUrl(attr).ifBlank { v }
            }
        }
        return ""
    }

    private fun isPlaceholderImage(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("data:image/svg") ||
            lower.startsWith("data:image/gif") ||
            lower.contains("blank.gif") ||
            lower.contains("pixel.gif") ||
            lower.contains("placeholder")
    }

    private fun extractAuthor(document: Document): String {
        val raw = listOf(
            "span:contains(Studios:) ~ a",
            "span:contains(Authors:) ~ a",
            "#showauthors > a",
            "#showartists > a",
            "div.sCat:contains(Author) + div.sContent a",
            "div:contains(Author) + span",
            ".author-content a",
            "h4.font-white span a",
            "div.author a span",
            "meta[name=author]",
            "meta[property=book:author]",
            "[itemprop=author]",
            ".novel-author a",
            ".author a",
            ".author",
            ".writer a"
        ).firstNotNullOfOrNull { selector ->
            val el = document.select(selector).firstOrNull() ?: return@firstNotNullOfOrNull null
            val text = if (selector.startsWith("meta")) el.attr("content") else el.text()
            text.trim().takeIf { it.isNotBlank() }
        } ?: ""

        return raw.replace(Regex("(?i)^author\\s*[:\\-]?\\s*"), "").trim()
    }

    private fun extractDescription(document: Document): String {
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
            val elements = document.select(selector)
            if (elements.isNotEmpty()) {
                val text = elements.joinToString("\n\n") { it.wholeText().trim() }
                    .lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n\n")
                text.ifBlank { elements.text().trim() }
            } else null
        }?.takeIf { it.isNotBlank() }

        val metaDesc = document.select("meta[property=og:description]").attr("content").ifBlank {
            document.select("meta[name=description]").attr("content")
        }.trim()

        return (domDesc ?: metaDesc).trim()
    }

    private fun extractGenres(document: Document): List<String> {
        val domGenres = document.select(
            "[itemprop=genre], .genres a, .genre a, .genres-content a, .novel-tags a, .novel-genres a, " +
            "a[href*=/genre/], a[href*=/tag/], a[href*=/genres/], a[href*=/tags/], .tag a, .tags a, .series-genre a"
        ).map { it.text().trim() }.filter { it.isNotBlank() }.distinct()

        if (domGenres.isNotEmpty()) return domGenres

        val keywords = document.select("meta[name=keywords]").attr("content")
        return keywords.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun extractStatus(document: Document): String? {
        val raw = listOf(
            "div.post-status:contains(In COO)",
            "div.sCat:contains(Status in Country of Origin) + div.sContent",
            "span.label:contains(COMPLETED)",
            "span.label:contains(ONGOING)",
            "header-stats strong:contains(Completed)",
            "meta[property=og:novel:status]",
            "meta[name=status]",
            ".status-item",
            ".post-status",
            ".novel-status",
            ".series-status",
            ".meta-status",
            "[itemprop=status]",
            ".post-content_item:contains(Status) .summary-content",
            ".post-content_item .summary-content:contains(Ongoing)",
            ".post-content_item .summary-content:contains(Completed)",
            ".post-content_item .summary-content:contains(Hiatus)",
            ".summary-content:contains(Ongoing)",
            ".summary-content:contains(Completed)",
            ".completed",
            ".ongoing"
        ).firstNotNullOfOrNull { selector ->
            val el = document.select(selector).firstOrNull() ?: return@firstNotNullOfOrNull null
            val text = if (selector.startsWith("meta")) el.attr("content") else el.text()
            text.trim().takeIf { it.isNotBlank() }
        } ?: return null

        val clean = raw.replace(Regex("(?i)^status\\s*[:\\-]?\\s*"), "").trim()
        val lower = clean.lowercase()
        return when {
            lower.contains("complete") -> "COMPLETED"
            lower.contains("ongoing") || lower.contains("publishing") || lower.contains("releasing") -> "ONGOING"
            lower.contains("hiatus") -> "PAUSED"
            lower.contains("cancel") -> "CANCELLED"
            else -> clean.takeIf { it.length < 50 }
        }
    }

    // --- Helper Sanitizers ---

    private val TITLE_SUFFIXES = listOf(
        " - MyAnimeList.net",
        " - MangaUpdates",
        " - Novel Updates",
        " - NovelUpdates",
        " - Asura Scans",
        " - Reaper Scans",
        " - Light Novel Pub",
        " | Royal Road",
        " Novel - WTR-LAB",
        " - WTR-LAB",
        " - IMDb"
    )

    internal fun cleanTitleNoise(rawTitle: String): String {
        var title = rawTitle.trim()
        for (suffix in TITLE_SUFFIXES) {
            if (title.endsWith(suffix, ignoreCase = true)) {
                title = title.substring(0, title.length - suffix.length).trim()
            }
        }
        if (title.contains(" - ")) {
            val parts = title.split(" - ")
            if (parts.size > 1 && parts.last().length < 25) {
                // Check if last part is branding like "Read Light Novel"
                val lastLower = parts.last().lowercase()
                if (lastLower.contains("read") || lastLower.contains("novel") || lastLower.contains("manga") || lastLower.contains("scan")) {
                    title = title.substringBeforeLast(" - ").trim()
                }
            }
        }
        return title
    }

    internal fun cleanImageUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.startsWith("//")) {
            url = "https:$url"
        }
        // IMDb high-res poster extraction: strip ._V1_...
        if (url.contains("imdb.com") || url.contains("media-amazon.com")) {
            url = url.replace(Regex("""\._V1_.*(?=\.jpg)"""), "")
        }
        // MAL thumb regex: strip /r/\d+x\d+/ or _thumb
        if (url.contains("myanimelist.net")) {
            url = url.replace(Regex("""/r/\d+x\d+/"""), "/")
                .replace(Regex("""_thumb(?=\.\w+)"""), "")
        }
        return url
    }
}
