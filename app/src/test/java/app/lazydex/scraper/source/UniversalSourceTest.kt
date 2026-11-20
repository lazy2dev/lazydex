package app.lazydex.scraper.source

import app.lazydex.domain.model.MediaType
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UniversalSourceTest {

    private fun createClientWithHandler(handler: (okhttp3.Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                handler(chain.request())
            })
            .build()
    }

    private fun successfulHtmlResponse(request: okhttp3.Request, html: String, code: Int = 200, headers: Map<String, String> = emptyMap()): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(html.toResponseBody("text/html; charset=utf-8".toMediaType()))
        headers.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    private fun jsonResponse(request: okhttp3.Request, json: String, code: Int = 200): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(json.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    // --- Tier 1 Interceptions ---

    @Test
    fun `scrapeAniListGraphQL correctly parses AniList payload for anime`() {
        val jsonPayload = """
            {
              "data": {
                "Media": {
                  "id": 154587,
                  "title": {
                    "userPreferred": "Sousou no Frieren",
                    "english": "Frieren: Beyond Journey's End",
                    "romaji": "Sousou no Frieren",
                    "native": "葬送のフリーレン"
                  },
                  "coverImage": {
                    "extraLarge": "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx154587.jpg",
                    "large": "https://s4.anilist.co/file/anilistcdn/media/anime/cover/medium/bx154587.jpg"
                  },
                  "description": "An elf mage sets on a journey.<br><br>Memories remain.",
                  "format": "TV",
                  "status": "FINISHED",
                  "episodes": 28,
                  "averageScore": 91.0,
                  "genres": ["Adventure", "Drama", "Fantasy"],
                  "studios": {
                    "nodes": [
                      { "name": "Madhouse" }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val client = createClientWithHandler { request -> jsonResponse(request, jsonPayload) }
        val source = GenericSource(client)

        val metadata = source.scrapeAniListGraphQL(154587, "anime")
        assertNotNull(metadata)
        assertEquals("Sousou no Frieren", metadata!!.title)
        assertEquals("https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx154587.jpg", metadata.imageUrl)
        assertEquals("Madhouse", metadata.author)
        assertEquals(MediaType.TV, metadata.type)
        assertEquals("COMPLETED", metadata.status)
        assertEquals(28, metadata.totalItems)
        assertEquals(91.0, metadata.averageScore!!, 0.001)
        assertTrue(metadata.alternativeTitles.contains("Frieren: Beyond Journey's End"))
        assertTrue(metadata.genres.contains("Adventure"))
    }

    @Test
    fun `scrapeMangaDexApi correctly parses MangaDex REST payload`() {
        val jsonPayload = """
            {
              "result": "ok",
              "data": {
                "id": "32d76d19-8a05-4db0-9fc2-e0b0648fe9d0",
                "type": "manga",
                "attributes": {
                  "title": {
                    "en": "Solo Leveling"
                  },
                  "altTitles": [
                    { "ko": "나 혼자만 레벨업" },
                    { "en": "I Alone Level Up" }
                  ],
                  "description": {
                    "en": "10 years ago, after the Gate opened..."
                  },
                  "status": "completed",
                  "lastChapter": "200",
                  "tags": [
                    {
                      "attributes": {
                        "name": { "en": "Action" }
                      }
                    }
                  ]
                },
                "relationships": [
                  {
                    "id": "author-uuid",
                    "type": "author",
                    "attributes": {
                      "name": "Chugong"
                    }
                  },
                  {
                    "id": "cover-uuid",
                    "type": "cover_art",
                    "attributes": {
                      "fileName": "cover-hash.jpg"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val client = createClientWithHandler { request -> jsonResponse(request, jsonPayload) }
        val source = GenericSource(client)

        val metadata = source.scrapeMangaDexApi("32d76d19-8a05-4db0-9fc2-e0b0648fe9d0")
        assertNotNull(metadata)
        assertEquals("Solo Leveling", metadata!!.title)
        assertEquals("https://uploads.mangadex.org/covers/32d76d19-8a05-4db0-9fc2-e0b0648fe9d0/cover-hash.jpg", metadata.imageUrl)
        assertEquals("Chugong", metadata.author)
        assertEquals("COMPLETED", metadata.status)
        assertEquals(200, metadata.totalItems)
        assertEquals(MediaType.MANGA, metadata.type)
        assertTrue(metadata.alternativeTitles.contains("I Alone Level Up"))
        assertTrue(metadata.genres.contains("Action"))
    }

    // --- Tier 2: Schema.org JSON-LD Traversal ---

    @Test
    fun `extractSchemaOrgJsonLd parses flat Book JSON-LD`() {
        val html = """
            <html>
            <head>
              <script type="application/ld+json">
              {
                "@context": "https://schema.org",
                "@type": "Book",
                "name": "Dune",
                "image": "https://example.com/dune.jpg",
                "author": {
                  "@type": "Person",
                  "name": "Frank Herbert"
                },
                "description": "Science fiction epic set on Arrakis.",
                "genre": ["Sci-Fi", "Space Opera"]
              }
              </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val source = GenericSource(OkHttpClient())
        val meta = source.extractSchemaOrgJsonLd(doc)

        assertNotNull(meta)
        assertEquals("Dune", meta!!.title)
        assertEquals("https://example.com/dune.jpg", meta.imageUrl)
        assertEquals("Frank Herbert", meta.author)
        assertEquals(MediaType.NOVEL, meta.type)
        assertTrue(meta.genres.contains("Sci-Fi"))
    }

    @Test
    fun `extractSchemaOrgJsonLd traverses @graph container for Movie`() {
        val html = """
            <html>
            <head>
              <script type="application/ld+json">
              {
                "@context": "https://schema.org",
                "@graph": [
                  {
                    "@type": "WebSite",
                    "url": "https://imdb.com"
                  },
                  {
                    "@type": "Movie",
                    "name": "Inception - IMDb",
                    "image": "https://m.media-amazon.com/images/M/poster._V1_QL75_UX380_CR0,0,380,562_.jpg",
                    "director": [
                      { "@type": "Person", "name": "Christopher Nolan" }
                    ],
                    "description": "A thief who steals corporate secrets through dream-sharing tech."
                  }
                ]
              }
              </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val source = GenericSource(OkHttpClient())
        val meta = source.extractSchemaOrgJsonLd(doc)

        assertNotNull(meta)
        assertEquals("Inception", meta!!.title)
        assertEquals("https://m.media-amazon.com/images/M/poster.jpg", meta.imageUrl)
        assertEquals("Christopher Nolan", meta.author)
        assertEquals(MediaType.MOVIE, meta.type)
    }

    // --- Tier 3: Next.js SSR Hydration Extraction ---

    @Test
    fun `extractNextDataHydration extracts WTR-LAB payload`() {
        val html = """
            <html>
            <head>
              <script id="__NEXT_DATA__" type="application/json">
              {
                "props": {
                  "pageProps": {
                    "serie": {
                      "serieData": {
                        "title": "Shadow Slave Novel - WTR-LAB",
                        "image": "//images.wtr-lab.com/covers/shadow-slave.jpg",
                        "author": "Guiltythree",
                        "description": "Growing up in poverty, Sunny was not entirely unprepared...",
                        "rawChapterCount": 1600
                      }
                    }
                  }
                }
              }
              </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val source = GenericSource(OkHttpClient())
        val meta = source.extractNextDataHydration(doc)

        assertNotNull(meta)
        assertEquals("Shadow Slave", meta!!.title)
        assertEquals("https://images.wtr-lab.com/covers/shadow-slave.jpg", meta.imageUrl)
        assertEquals("Guiltythree", meta.author)
        assertEquals(1600, meta.totalItems)
        assertEquals(MediaType.NOVEL, meta.type)
    }

    // --- Tier 4 & 5: HTML DOM & Lazyload Extraction ---

    @Test
    fun `extractFromDomAndMeta resolves lazyload data-src over transparent placeholder`() {
        val html = """
            <html>
            <head>
              <title>Omniscient Reader's Viewpoint - Reaper Scans</title>
            </head>
            <body>
              <h1 class="font-bold">Omniscient Reader's Viewpoint - Reaper Scans</h1>
              <div class="summary_image">
                <img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz..." data-src="https://reaperscans.com/covers/orv.jpg" alt="poster" />
              </div>
              <div class="author-content">
                <a href="/author/sing-shong">Sing-Shong</a>
              </div>
              <div class="summary__content">
                <p>Only I know the end of this world.</p>
              </div>
              <div class="post-content_item">
                <h5>Status</h5>
                <div class="summary-content">Ongoing</div>
              </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://reaperscans.com/series/orv")
        val source = GenericSource(OkHttpClient())
        val meta = source.extractFromDomAndMeta(doc, "https://reaperscans.com/series/orv")

        assertNotNull(meta)
        assertEquals("Omniscient Reader's Viewpoint", meta!!.title)
        assertEquals("https://reaperscans.com/covers/orv.jpg", meta.imageUrl)
        assertEquals("Sing-Shong", meta.author)
        assertEquals("ONGOING", meta.status)
        assertEquals("Only I know the end of this world.", meta.description)
    }

    @Test
    fun `extractFromDomAndMeta handles NovelUpdates COO status and alt titles`() {
        val html = """
            <html>
            <head>
              <title>The Beginning After The End - Novel Updates</title>
            </head>
            <body>
              <div class="seriestitlenew">The Beginning After The End - Novel Updates</div>
              <div id="editassociated">TBATE<br>After The End<br>El Comienzo Despues del Fin</div>
              <div class="seriesimg">
                <img src="https://www.novelupdates.com/img/tbate.jpg" />
              </div>
              <div id="showauthors"><a href="/author/turtleme">TurtleMe</a></div>
              <div class="post-status">In COO : Completed</div>
              <div id="editdescription">King Grey has unrivaled strength, wealth, and prestige.</div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://www.novelupdates.com/series/tbate")
        val source = GenericSource(OkHttpClient())
        val meta = source.extractFromDomAndMeta(doc, "https://www.novelupdates.com/series/tbate")

        assertNotNull(meta)
        assertEquals("The Beginning After The End", meta!!.title)
        assertEquals("COMPLETED", meta.status)
        assertEquals("TurtleMe", meta.author)
        assertTrue(meta.alternativeTitles.contains("TBATE"))
        assertTrue(meta.alternativeTitles.contains("After The End"))
    }

    // --- Noise Cleaning & URL Sanitization ---

    @Test
    fun `cleanTitleNoise strips branding suffixes across all 11 target platforms`() {
        val source = GenericSource(OkHttpClient())
        assertEquals("Steins;Gate", source.cleanTitleNoise("Steins;Gate - MyAnimeList.net"))
        assertEquals("Berserk", source.cleanTitleNoise("Berserk - MangaUpdates"))
        assertEquals("Overlord", source.cleanTitleNoise("Overlord - Novel Updates"))
        assertEquals("Solo Bug Player", source.cleanTitleNoise("Solo Bug Player - Asura Scans"))
        assertEquals("Overgeared", source.cleanTitleNoise("Overgeared - Reaper Scans"))
        assertEquals("Primal Hunter", source.cleanTitleNoise("Primal Hunter | Royal Road"))
        assertEquals("Lord of Mysteries", source.cleanTitleNoise("Lord of Mysteries Novel - WTR-LAB"))
        assertEquals("The Dark Knight", source.cleanTitleNoise("The Dark Knight - IMDb"))
        assertEquals("Shadow Slave", source.cleanTitleNoise("Shadow Slave - Light Novel Pub"))
    }

    @Test
    fun `cleanImageUrl removes MAL thumb dimensions and IMDb _V1_ filters`() {
        val source = GenericSource(OkHttpClient())
        assertEquals("https://cdn.myanimelist.net/images/anime/4/19644.jpg", source.cleanImageUrl("https://cdn.myanimelist.net/r/100x140/images/anime/4/19644.jpg"))
        assertEquals("https://m.media-amazon.com/images/M/poster.jpg", source.cleanImageUrl("https://m.media-amazon.com/images/M/poster._V1_QL75_UX380_CR0,0,380,562_.jpg"))
        assertEquals("https://example.com/cover.jpg", source.cleanImageUrl("//example.com/cover.jpg"))
    }

    // --- Anti-Bot & Error Handling ---

    @Test
    fun `scrape throws AntiBotChallengeException on 403 Cloudflare challenge`() = runBlocking {
        val client = createClientWithHandler { request ->
            successfulHtmlResponse(
                request = request,
                html = "<html><title>Just a moment...</title><body>Checking your browser</body></html>",
                code = 403,
                headers = mapOf("Server" to "cloudflare", "cf-ray" to "87654321-SJC")
            )
        }
        val source = GenericSource(client)

        try {
            source.scrape("https://example.com/protected")
            fail("Expected AntiBotChallengeException")
        } catch (e: AntiBotChallengeException) {
            assertTrue(e.message!!.contains("Cloudflare"))
        }
    }

    @Test
    fun `scrape throws AntiBotChallengeException on HTTP 200 challenge payload`() = runBlocking {
        val client = createClientWithHandler { request ->
            successfulHtmlResponse(
                request = request,
                html = "<html><head><title>Just a moment...</title></head><body><div id=\"challenge-running\"></div></body></html>",
                code = 200
            )
        }
        val source = GenericSource(client)

        try {
            source.scrape("https://example.com/turnstile")
            fail("Expected AntiBotChallengeException")
        } catch (e: AntiBotChallengeException) {
            assertTrue(e.message!!.contains("HTTP 200"))
        }
    }

    @Test
    fun `scrape throws HttpFailureException on 500 internal server error`() = runBlocking {
        val client = createClientWithHandler { request ->
            successfulHtmlResponse(
                request = request,
                html = "Internal Server Error",
                code = 500
            )
        }
        val source = GenericSource(client)

        try {
            source.scrape("https://example.com/server-error")
            fail("Expected HttpFailureException")
        } catch (e: HttpFailureException) {
            assertEquals(500, e.code)
        }
    }
}
