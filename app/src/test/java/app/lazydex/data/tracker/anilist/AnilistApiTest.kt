package app.lazydex.data.tracker.anilist

import android.content.SharedPreferences
import app.lazydex.data.tracker.anilist.dto.AniListApiException
import app.lazydex.data.tracker.anilist.dto.AniListCoverImage
import app.lazydex.data.tracker.anilist.dto.AniListFuzzyDate
import app.lazydex.data.tracker.anilist.dto.AniListFuzzyDateInput
import app.lazydex.data.tracker.anilist.dto.AniListMedia
import app.lazydex.data.tracker.anilist.dto.AniListMediaListEntry
import app.lazydex.data.tracker.anilist.dto.AniListTitle
import app.lazydex.data.tracker.anilist.dto.MediaListCollection
import app.lazydex.data.tracker.anilist.dto.MediaListGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AnilistApiTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var tokenStore: AnilistTokenStore
    private val recordedRequests = mutableListOf<Request>()
    private val recordedBodies = mutableListOf<String>()
    private var responseHandler: (Request, String) -> Response = { _, _ ->
        throw IllegalStateException("No mock response provided")
    }

    private lateinit var fakeHttpClient: OkHttpClient

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        tokenStore = AnilistTokenStore(fakePrefs)
        recordedRequests.clear()
        recordedBodies.clear()

        fakeHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val bodyString = request.body?.let { body ->
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    buffer.readUtf8()
                } ?: ""
                recordedRequests.add(request)
                recordedBodies.add(bodyString)
                responseHandler(request, bodyString)
            }
            .build()
    }

    private fun createResponse(
        code: Int,
        body: String,
        headers: Headers = Headers.headersOf("Content-Type", "application/json")
    ): Response {
        return Response.Builder()
            .request(Request.Builder().url(AnilistApi.DEFAULT_BASE_URL).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .headers(headers)
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    @Test
    fun `getMediaListCollection sends correct query variables and headers and parses response`() = runTest {
        tokenStore.accessToken = "test_access_token_123"

        val limiter = TokenBucketLimiter(timeProvider = { testScheduler.currentTime })
        val client = AnilistApi(
            okHttpClient = fakeHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        responseHandler = { req, body ->
            assertEquals("Bearer test_access_token_123", req.header("Authorization"))
            assertEquals("application/json", req.header("Content-Type"))
            assertEquals("application/json", req.header("Accept"))

            // Verify variables in request body
            val jsonBody = Json.parseToJsonElement(body).jsonObject
            val variables = jsonBody["variables"]?.jsonObject
            assertNotNull(variables)
            assertEquals("5001", variables?.get("userId")?.jsonPrimitive?.content)
            assertEquals("MANGA", variables?.get("type")?.jsonPrimitive?.content)
            assertEquals("1", variables?.get("chunk")?.jsonPrimitive?.content)

            createResponse(
                code = 200,
                body = """
                    {
                      "data": {
                        "MediaListCollection": {
                          "hasNextChunk": true,
                          "lists": [
                            {
                              "name": "Reading",
                              "isCustomList": false,
                              "status": "CURRENT",
                              "entries": [
                                {
                                  "id": 101,
                                  "mediaId": 999,
                                  "status": "CURRENT",
                                  "scoreRaw": 85,
                                  "progress": 42,
                                  "progressVolumes": 4,
                                  "repeat": 0,
                                  "private": false,
                                  "notes": "Peak fiction",
                                  "startedAt": { "year": 2023, "month": 4, "day": 1 },
                                  "completedAt": { "year": null, "month": null, "day": null },
                                  "updatedAt": 1700000000,
                                  "media": {
                                    "id": 999,
                                    "idMal": 888,
                                    "format": "MANGA",
                                    "status": "RELEASING",
                                    "isAdult": false,
                                    "episodes": null,
                                    "duration": null,
                                    "chapters": 120,
                                    "volumes": 12,
                                    "genres": ["Action", "Fantasy"],
                                    "coverImage": {
                                      "large": "https://img.anilist.co/large.jpg",
                                      "extraLarge": "https://img.anilist.co/xlarge.jpg"
                                    },
                                    "bannerImage": "https://img.anilist.co/banner.jpg",
                                    "title": {
                                      "userPreferred": "Solo Leveling",
                                      "english": "Only I Level Up",
                                      "romaji": "Na Honjaman Rebeleop",
                                      "native": "나 혼자만 레벨업"
                                    }
                                  }
                                }
                              ]
                            }
                          ]
                        }
                      }
                    }
                """.trimIndent()
            )
        }

        val collection = client.getMediaListCollection(
            userId = 5001,
            type = "MANGA",
            chunk = 1
        )

        assertTrue(collection.hasNextChunk)
        assertEquals(1, collection.lists.size)

        val group = collection.lists[0]
        assertEquals("Reading", group.name)
        assertFalse(group.isCustomList)
        assertEquals("CURRENT", group.status)
        assertEquals(1, group.entries.size)

        val entry = group.entries[0]
        assertEquals(101, entry.id)
        assertEquals(999, entry.mediaId)
        assertEquals("CURRENT", entry.status)
        assertEquals(85, entry.scoreRaw)
        assertEquals(42, entry.progress)
        assertEquals(4, entry.progressVolumes)
        assertEquals(false, entry.isPrivate)
        assertEquals("Peak fiction", entry.notes)
        assertEquals(2023, entry.startedAt?.year)
        assertEquals(1700000000L, entry.updatedAt)

        val media = entry.media
        assertNotNull(media)
        assertEquals(999, media?.id)
        assertEquals(888, media?.idMal)
        assertEquals("MANGA", media?.format)
        assertEquals("RELEASING", media?.status)
        assertEquals(listOf("Action", "Fantasy"), media?.genres)
        assertEquals("https://img.anilist.co/xlarge.jpg", media?.coverImage?.best)
        assertEquals("Solo Leveling", media?.title?.best)
        assertEquals("나 혼자만 레벨업", media?.title?.nativeTitle)
    }

    @Test
    fun `MediaListCollection deduplicates custom lists by media id and filters null media`() {
        val entry1 = AniListMediaListEntry(
            id = 1,
            mediaId = 1001,
            status = "CURRENT",
            media = AniListMedia(id = 1001, title = AniListTitle(userPreferred = "Manga A"))
        )
        val entry1InCustomList = AniListMediaListEntry(
            id = 1,
            mediaId = 1001,
            status = "CUSTOM_STATUS",
            media = AniListMedia(id = 1001, title = AniListTitle(userPreferred = "Manga A"))
        )
        val entry2 = AniListMediaListEntry(
            id = 2,
            mediaId = 1002,
            status = "COMPLETED",
            media = AniListMedia(id = 1002, title = AniListTitle(userPreferred = "Manga B"))
        )
        val entryNullMedia = AniListMediaListEntry(
            id = 3,
            mediaId = 1003,
            status = "DROPPED",
            media = null
        )

        val collection = MediaListCollection(
            hasNextChunk = false,
            lists = listOf(
                MediaListGroup(
                    name = "Favorites",
                    isCustomList = true,
                    status = null,
                    entries = listOf(entry1InCustomList)
                ),
                MediaListGroup(
                    name = "Reading",
                    isCustomList = false,
                    status = "CURRENT",
                    entries = listOf(entry1)
                ),
                MediaListGroup(
                    name = "Completed",
                    isCustomList = false,
                    status = "COMPLETED",
                    entries = listOf(entry2, entryNullMedia)
                )
            )
        )

        val deduplicated = collection.deduplicatedEntries
        // entryNullMedia filtered out; entry1 kept from standard status list (isCustomList = false); entry2 kept
        assertEquals(2, deduplicated.size)
        assertEquals(1001, deduplicated[0].media?.id)
        assertEquals("CURRENT", deduplicated[0].status)
        assertEquals(1002, deduplicated[1].media?.id)
        assertEquals("COMPLETED", deduplicated[1].status)
    }

    @Test
    fun `saveMediaListEntry sends correct mutation payload and parses saved entry`() = runTest {
        tokenStore.accessToken = "auth_token_xyz"

        val limiter = TokenBucketLimiter(timeProvider = { testScheduler.currentTime })
        val client = AnilistApi(
            okHttpClient = fakeHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        responseHandler = { req, body ->
            assertEquals("Bearer auth_token_xyz", req.header("Authorization"))
            val jsonBody = Json.parseToJsonElement(body).jsonObject
            val variables = jsonBody["variables"]?.jsonObject
            assertNotNull(variables)

            assertEquals("777", variables?.get("id")?.jsonPrimitive?.content)
            assertEquals("888", variables?.get("mediaId")?.jsonPrimitive?.content)
            assertEquals("COMPLETED", variables?.get("status")?.jsonPrimitive?.content)
            assertEquals("50", variables?.get("progress")?.jsonPrimitive?.content)
            assertEquals("5", variables?.get("progressVolumes")?.jsonPrimitive?.content)
            assertEquals("95", variables?.get("scoreRaw")?.jsonPrimitive?.content)
            assertEquals("true", variables?.get("private")?.jsonPrimitive?.content)

            val startedAt = variables?.get("startedAt")?.jsonObject
            assertEquals("2023", startedAt?.get("year")?.jsonPrimitive?.content)
            assertEquals("1", startedAt?.get("month")?.jsonPrimitive?.content)
            assertEquals("15", startedAt?.get("day")?.jsonPrimitive?.content)

            createResponse(
                code = 200,
                body = """
                    {
                      "data": {
                        "SaveMediaListEntry": {
                          "id": 777,
                          "mediaId": 888,
                          "status": "COMPLETED",
                          "score": 9.5,
                          "progress": 50,
                          "progressVolumes": 5,
                          "updatedAt": 1700100000
                        }
                      }
                    }
                """.trimIndent()
            )
        }

        val result = client.saveMediaListEntry(
            id = 777,
            mediaId = 888,
            status = "COMPLETED",
            progress = 50,
            progressVolumes = 5,
            scoreRaw = 95,
            isPrivate = true,
            startedAt = AniListFuzzyDateInput(year = 2023, month = 1, day = 15)
        )

        assertEquals(777, result.id)
        assertEquals(888, result.mediaId)
        assertEquals("COMPLETED", result.status)
        assertEquals(9.5, result.score ?: 0.0, 0.001)
        assertEquals(50, result.progress)
        assertEquals(5, result.progressVolumes)
        assertEquals(1700100000L, result.updatedAt)
    }

    @Test
    fun `getCurrentUser fetches viewer profile details`() = runTest {
        tokenStore.accessToken = "auth_token_viewer"

        val limiter = TokenBucketLimiter(timeProvider = { testScheduler.currentTime })
        val client = AnilistApi(
            okHttpClient = fakeHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        responseHandler = { _, _ ->
            createResponse(
                code = 200,
                body = """
                    {
                      "data": {
                        "Viewer": {
                          "id": 4242,
                          "name": "KageBunshin",
                          "mediaListOptions": {
                            "scoreFormat": "POINT_100"
                          },
                          "options": {
                            "displayAdultContent": true
                          }
                        }
                      }
                    }
                """.trimIndent()
            )
        }

        val viewer = client.getCurrentUser()

        assertEquals(4242, viewer.id)
        assertEquals("KageBunshin", viewer.name)
        assertEquals("POINT_100", viewer.mediaListOptions?.scoreFormat)
        assertEquals(true, viewer.options?.displayAdultContent)
    }

    @Test
    fun `searchMedia sends correct variables and parses media page results`() = runTest {
        val limiter = TokenBucketLimiter(timeProvider = { testScheduler.currentTime })
        val client = AnilistApi(
            okHttpClient = fakeHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        responseHandler = { req, body ->
            assertTrue(body.contains("\"search\":\"Frieren\""))
            assertTrue(body.contains("\"type\":\"MANGA\""))
            assertTrue(body.contains("\"perPage\":5"))
            createResponse(
                code = 200,
                body = """
                    {
                      "data": {
                        "Page": {
                          "media": [
                            {
                              "id": 140101,
                              "format": "MANGA",
                              "status": "RELEASING",
                              "chapters": 130,
                              "volumes": 13,
                              "title": {
                                "userPreferred": "Sousou no Frieren",
                                "english": "Frieren: Beyond Journey's End"
                              },
                              "coverImage": {
                                "large": "https://img.anilist.co/frieren.jpg"
                              }
                            }
                          ]
                        }
                      }
                    }
                """.trimIndent()
            )
        }

        val results = client.searchMedia(query = "Frieren", type = "MANGA", perPage = 5)
        assertEquals(1, results.size)
        assertEquals(140101, results[0].id)
        assertEquals("Sousou no Frieren", results[0].title?.userPreferred)
        assertEquals("Frieren: Beyond Journey's End", results[0].title?.english)
        assertEquals("https://img.anilist.co/frieren.jpg", results[0].coverImage?.large)
    }

    @Test
    fun `GraphQL errors on HTTP 200 throw typed AniListApiException`() = runTest {
        val limiter = TokenBucketLimiter(timeProvider = { testScheduler.currentTime })
        val client = AnilistApi(
            okHttpClient = fakeHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        responseHandler = { _, _ ->
            createResponse(
                code = 200,
                body = """
                    {
                      "errors": [
                        {
                          "message": "Invalid token provided",
                          "status": 401,
                          "locations": [{ "line": 2, "column": 3 }]
                        }
                      ],
                      "data": null
                    }
                """.trimIndent()
            )
        }

        try {
            client.getCurrentUser()
            fail("Expected AniListApiException")
        } catch (e: AniListApiException) {
            assertEquals(200, e.httpCode)
            assertEquals("Invalid token provided", e.message)
            assertTrue(e.isUnauthorized)
            assertEquals(1, e.errors.size)
            assertEquals(401, e.errors[0].status)
        }
    }

    @Test
    fun `HTTP 401 response throws AniListApiException with isUnauthorized true`() = runTest {
        val limiter = TokenBucketLimiter(timeProvider = { testScheduler.currentTime })
        val client = AnilistApi(
            okHttpClient = fakeHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        responseHandler = { _, _ ->
            createResponse(
                code = 401,
                body = """
                    {
                      "errors": [
                        {
                          "message": "Unauthorized",
                          "status": 401
                        }
                      ]
                    }
                """.trimIndent()
            )
        }

        try {
            client.getCurrentUser()
            fail("Expected AniListApiException")
        } catch (e: AniListApiException) {
            assertEquals(401, e.httpCode)
            assertTrue(e.isUnauthorized)
        }
    }

    @Test
    fun `HTTP 500 server error response throws AniListApiException`() = runTest {
        val limiter = TokenBucketLimiter(timeProvider = { testScheduler.currentTime })
        val client = AnilistApi(
            okHttpClient = fakeHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        responseHandler = { _, _ ->
            createResponse(
                code = 500,
                body = "Internal Server Error"
            )
        }

        try {
            client.getCurrentUser()
            fail("Expected AniListApiException")
        } catch (e: AniListApiException) {
            assertEquals(500, e.httpCode)
            assertFalse(e.isUnauthorized)
        }
    }

    @Test
    fun `HTTP 429 response triggers notifyRateLimited and retries until success`() = runTest {
        val limiter = TokenBucketLimiter(timeProvider = { testScheduler.currentTime })
        val client = AnilistApi(
            okHttpClient = fakeHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        var callCount = 0
        responseHandler = { _, _ ->
            callCount++
            if (callCount == 1) {
                createResponse(
                    code = 429,
                    body = """{"errors":[{"message":"Too many requests","status":429}]}""",
                    headers = Headers.headersOf("Retry-After", "5", "Content-Type", "application/json")
                )
            } else {
                createResponse(
                    code = 200,
                    body = """{"data":{"Viewer":{"id":1,"name":"RetriedUser"}}}"""
                )
            }
        }

        val viewer = client.getCurrentUser()
        assertEquals(2, callCount)
        assertEquals("RetriedUser", viewer.name)
    }

    @Test
    fun `HTTP 429 exceeding max retries throws AniListApiException with isRateLimited true`() = runTest {
        val limiter = TokenBucketLimiter(timeProvider = { testScheduler.currentTime })
        val client = AnilistApi(
            okHttpClient = fakeHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        var callCount = 0
        responseHandler = { _, _ ->
            callCount++
            createResponse(
                code = 429,
                body = """{"errors":[{"message":"Too many requests","status":429}]}""",
                headers = Headers.headersOf("Retry-After", "1", "Content-Type", "application/json")
            )
        }

        try {
            client.getCurrentUser()
            fail("Expected AniListApiException")
        } catch (e: AniListApiException) {
            assertEquals(429, e.httpCode)
            assertTrue(e.isRateLimited)
            // Initial attempt + 2 retries = 3 attempts total
            assertEquals(3, callCount)
        }
    }

    @Test
    fun `requests without access token omit Authorization header`() = runTest {
        tokenStore.accessToken = null

        val limiter = TokenBucketLimiter(timeProvider = { testScheduler.currentTime })
        val client = AnilistApi(
            okHttpClient = fakeHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        responseHandler = { req, _ ->
            assertNull(req.header("Authorization"))
            createResponse(
                code = 200,
                body = """{"data":{"Viewer":{"id":100,"name":"Anonymous"}}}"""
            )
        }

        val viewer = client.getCurrentUser()
        assertEquals(100, viewer.id)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data

        override fun getString(key: String, defValue: String?): String? =
            data[key] as? String ?: defValue

        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            data[key] as? MutableSet<String> ?: defValues

        override fun getInt(key: String, defValue: Int): Int =
            data[key] as? Int ?: defValue

        override fun getLong(key: String, defValue: Long): Long =
            data[key] as? Long ?: defValue

        override fun getFloat(key: String, defValue: Float): Float =
            data[key] as? Float ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            data[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean =
            data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(data)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val backingData: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val uncommitted = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clearRequested = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                uncommitted[key] = value
                removed.remove(key)
                return this
            }

            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                uncommitted[key] = values
                removed.remove(key)
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                uncommitted[key] = value
                removed.remove(key)
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                uncommitted[key] = value
                removed.remove(key)
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                uncommitted[key] = value
                removed.remove(key)
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                uncommitted[key] = value
                removed.remove(key)
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                removed.add(key)
                uncommitted.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearRequested = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearRequested) {
                    backingData.clear()
                }
                backingData.putAll(uncommitted)
                removed.forEach { backingData.remove(it) }
            }
        }
    }
}

