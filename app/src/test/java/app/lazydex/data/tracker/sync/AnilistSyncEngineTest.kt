package app.lazydex.data.tracker.sync

import android.content.SharedPreferences
import app.lazydex.data.local.dao.CategoryCount
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.dao.StatusCount
import app.lazydex.data.local.entity.ActiveMediaItemView
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.tracker.anilist.AnilistApi
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.anilist.TokenBucketLimiter
import app.lazydex.domain.model.MediaStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
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
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AnilistSyncEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var tokenStore: AnilistTokenStore
    private lateinit var limiter: TokenBucketLimiter
    private lateinit var fakeMediaItemDao: FakeMediaItemDao
    private lateinit var fakeMediaLinkDao: FakeMediaLinkDao

    private val recordedRequests = mutableListOf<Request>()
    private val recordedBodies = mutableListOf<String>()
    private var responseHandler: (Request, String) -> Response = { _, _ ->
        throw IllegalStateException("No mock response provided")
    }

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var client: AnilistApi
    private lateinit var engine: AnilistSyncEngine

    private var virtualTimeMs: Long = 1_700_000_000_000L
    private var transactionCount = 0

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        tokenStore = AnilistTokenStore(fakePrefs)
        tokenStore.accessToken = "valid_token"
        tokenStore.userId = 12345

        limiter = TokenBucketLimiter(capacity = 60, refillRatePerMinute = 60, reservedTokens = 15)
        fakeMediaItemDao = FakeMediaItemDao()
        fakeMediaLinkDao = FakeMediaLinkDao()
        recordedRequests.clear()
        recordedBodies.clear()
        transactionCount = 0
        virtualTimeMs = 1_700_000_000_000L

        okHttpClient = OkHttpClient.Builder()
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

        client = AnilistApi(
            okHttpClient = okHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = testDispatcher
        )

        engine = AnilistSyncEngine(
            client = client,
            db = null,
            mediaItemDao = fakeMediaItemDao,
            mediaLinkDao = fakeMediaLinkDao,
            tokenStore = tokenStore,
            ioDispatcher = testDispatcher,
            batchSize = 50,
            timeProvider = { virtualTimeMs },
            transactionRunner = { block ->
                transactionCount++
                block()
            }
        )
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
    fun `syncAll fails if not logged in`() = runTest(testDispatcher) {
        tokenStore.accessToken = null
        val result = engine.syncAll()
        assertTrue(result is SyncResult.NotLoggedIn)
    }

    @Test
    fun `inbound sync inserts new entries with proper category and ongoing metadata`() = runTest(testDispatcher) {
        val mangaJson = """
        {
          "data": {
            "MediaListCollection": {
              "hasNextChunk": false,
              "lists": [
                {
                  "name": "Reading",
                  "isCustomList": false,
                  "status": "CURRENT",
                  "entries": [
                    {
                      "id": 901,
                      "mediaId": 1001,
                      "status": "CURRENT",
                      "scoreRaw": 85,
                      "progress": 25,
                      "progressVolumes": 3,
                      "repeat": 0,
                      "private": false,
                      "notes": "Great read",
                      "updatedAt": 1700000050,
                      "media": {
                        "id": 1001,
                        "format": "MANGA",
                        "status": "RELEASING",
                        "isAdult": false,
                        "chapters": 100,
                        "volumes": 10,
                        "genres": ["Action", "Adventure"],
                        "coverImage": { "extraLarge": "https://img.anilist.co/cover1.jpg" },
                        "bannerImage": "https://img.anilist.co/banner1.jpg",
                        "title": {
                          "userPreferred": "One Piece",
                          "english": "One Piece",
                          "romaji": "Wan Pīsu",
                          "native": "ワンピース"
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

        val emptyAnimeJson = """
        {
          "data": {
            "MediaListCollection": {
              "hasNextChunk": false,
              "lists": []
            }
          }
        }
        """.trimIndent()

        responseHandler = { _, body ->
            if (body.contains("\"type\":\"MANGA\"")) {
                createResponse(200, mangaJson)
            } else {
                createResponse(200, emptyAnimeJson)
            }
        }

        val result = engine.syncAll()
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.pulledCount)

        // Verify media item
        assertEquals(1, fakeMediaItemDao.items.size)
        val item = fakeMediaItemDao.items.values.first()
        assertEquals("One Piece", item.title)
        assertEquals("cat_manga", item.categoryId)
        assertEquals("IN_PROGRESS", item.userStatus)
        assertEquals(25.0, item.progress, 0.001)
        assertEquals(100.0, item.progressTotal!!, 0.001)
        assertEquals(3.0, item.secondaryProgress, 0.001)
        assertEquals(10.0, item.secondaryProgressTotal!!, 0.001)
        assertEquals(85, item.scoreRaw)
        assertEquals("https://img.anilist.co/cover1.jpg", item.coverImageUrl)
        assertEquals("https://img.anilist.co/banner1.jpg", item.bannerImage)

        // Verify media link
        assertEquals(1, fakeMediaLinkDao.links.size)
        val link = fakeMediaLinkDao.links.values.first()
        assertEquals("1001", link.remoteId)
        assertEquals("901", link.remoteMediaListId)
        assertEquals("LINKED", link.state)
        assertEquals("SYNCED", link.syncStatus)

        val snapshot = SyncSnapshot.fromJson(link.syncedSnapshot)
        assertNotNull(snapshot)
        assertEquals(25.0, snapshot!!.progress, 0.001)
        assertEquals("IN_PROGRESS", snapshot.status)
    }

    @Test
    fun `inbound sync suggests merge when local item matches title without duplicating`() = runTest(testDispatcher) {
        val existingItem = MediaItemEntity(
            id = "local_frieren",
            categoryId = "cat_manga",
            title = "Frieren: Beyond Journey's End",
            titleEnglish = "Frieren: Beyond Journey's End",
            titleRomaji = "Sousou no Frieren",
            titleNative = "葬送のフリーレン",
            coverImagePath = "",
            coverImageUrl = null,
            bannerImage = null,
            description = "Local added manga",
            author = "",
            userStatus = "IN_PROGRESS",
            progress = 10.0,
            progressTotal = null,
            secondaryProgress = 0.0,
            secondaryProgressTotal = null,
            scoreRaw = null,
            repeat = 0,
            notes = "",
            genres = "[]",
            tags = "[]",
            isPrivate = false,
            isAdult = false,
            startYear = null,
            startMonth = null,
            startDay = null,
            endYear = null,
            endMonth = null,
            endDay = null,
            durationMinutes = null,
            avgChapterLength = null,
            lengthUnitOverride = null,
            playbackSpeed = 1.0,
            createdAt = 1700000000,
            updatedAt = 1700000000,
            deletedAt = null
        )
        fakeMediaItemDao.items[existingItem.id] = existingItem

        val mangaJson = """
        {
          "data": {
            "MediaListCollection": {
              "hasNextChunk": false,
              "lists": [
                {
                  "name": "Reading",
                  "isCustomList": false,
                  "status": "CURRENT",
                  "entries": [
                    {
                      "id": 905,
                      "mediaId": 2002,
                      "status": "CURRENT",
                      "scoreRaw": 90,
                      "progress": 15,
                      "progressVolumes": 2,
                      "repeat": 0,
                      "private": false,
                      "notes": "Great read",
                      "updatedAt": 1700000050,
                      "media": {
                        "id": 2002,
                        "format": "MANGA",
                        "status": "RELEASING",
                        "isAdult": false,
                        "chapters": 130,
                        "volumes": 12,
                        "genres": ["Fantasy"],
                        "coverImage": { "large": "https://img.anilist.co/frieren.jpg" },
                        "title": { "userPreferred": "Sousou no Frieren", "english": "Frieren: Beyond Journey's End", "romaji": "Sousou no Frieren" }
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val emptyAnimeJson = """
        { "data": { "MediaListCollection": { "hasNextChunk": false, "lists": [] } } }
        """.trimIndent()

        responseHandler = { req, body ->
            when {
              body.contains("\"type\":\"MANGA\"") -> createResponse(200, mangaJson)
              else -> createResponse(200, emptyAnimeJson)
            }
        }

        val result = engine.syncAll()
        assertTrue(result is SyncResult.Success)

        // Verifications:
        // 1. MUST NOT duplicate media item: size remains exactly 1
        assertEquals(1, fakeMediaItemDao.items.size)
        assertTrue(fakeMediaItemDao.items.containsKey("local_frieren"))

        // 2. Link created for local_frieren with state = "SUGGESTED"
        assertEquals(1, fakeMediaLinkDao.links.size)
        val link = fakeMediaLinkDao.links.values.first()
        assertEquals("local_frieren", link.mediaId)
        assertEquals("2002", link.remoteId)
        assertEquals("SUGGESTED", link.state)
    }

    @Test
    fun `novel format maps to cat_novel and anime maps to cat_anime`() = runTest(testDispatcher) {
        val mixedJson = """
        {
          "data": {
            "MediaListCollection": {
              "hasNextChunk": false,
              "lists": [
                {
                  "name": "Reading",
                  "isCustomList": false,
                  "status": "CURRENT",
                  "entries": [
                    {
                      "id": 902,
                      "mediaId": 2002,
                      "status": "CURRENT",
                      "progress": 5,
                      "media": {
                        "id": 2002,
                        "format": "NOVEL",
                        "title": { "userPreferred": "Overlord Light Novel" }
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val animeJson = """
        {
          "data": {
            "MediaListCollection": {
              "hasNextChunk": false,
              "lists": [
                {
                  "name": "Watching",
                  "isCustomList": false,
                  "status": "CURRENT",
                  "entries": [
                    {
                      "id": 903,
                      "mediaId": 3003,
                      "status": "CURRENT",
                      "progress": 12,
                      "media": {
                        "id": 3003,
                        "format": "TV",
                        "episodes": 24,
                        "title": { "userPreferred": "Frieren" }
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        responseHandler = { _, body ->
            if (body.contains("\"type\":\"MANGA\"")) createResponse(200, mixedJson)
            else createResponse(200, animeJson)
        }

        val result = engine.syncAll()
        assertTrue(result is SyncResult.Success)

        val novelItem = fakeMediaItemDao.items.values.find { it.title == "Overlord Light Novel" }
        assertNotNull(novelItem)
        assertEquals("cat_novel", novelItem!!.categoryId)

        val animeItem = fakeMediaItemDao.items.values.find { it.title == "Frieren" }
        assertNotNull(animeItem)
        assertEquals("cat_anime", animeItem!!.categoryId)
        assertEquals(24.0, animeItem.progressTotal!!, 0.001)
    }

    @Test
    fun `deduplication test handles duplicates across standard and custom lists`() = runTest(testDispatcher) {
        val duplicateJson = """
        {
          "data": {
            "MediaListCollection": {
              "hasNextChunk": false,
              "lists": [
                {
                  "name": "Custom Favorites",
                  "isCustomList": true,
                  "status": null,
                  "entries": [
                    {
                      "id": 999,
                      "mediaId": 1001,
                      "status": "CURRENT",
                      "progress": 20,
                      "media": {
                        "id": 1001,
                        "format": "MANGA",
                        "title": { "userPreferred": "One Piece" }
                      }
                    }
                  ]
                },
                {
                  "name": "Reading",
                  "isCustomList": false,
                  "status": "CURRENT",
                  "entries": [
                    {
                      "id": 901,
                      "mediaId": 1001,
                      "status": "CURRENT",
                      "progress": 25,
                      "media": {
                        "id": 1001,
                        "format": "MANGA",
                        "title": { "userPreferred": "One Piece" }
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        responseHandler = { _, body ->
            if (body.contains("\"type\":\"MANGA\"")) createResponse(200, duplicateJson)
            else createResponse(200, """{"data":{"MediaListCollection":{"hasNextChunk":false,"lists":[]}}}""")
        }

        val result = engine.syncAll()
        assertTrue(result is SyncResult.Success)

        // Only 1 item and 1 link should be ingested (standard list takes precedence)
        assertEquals(1, fakeMediaItemDao.items.size)
        assertEquals(1, fakeMediaLinkDao.links.size)
        val link = fakeMediaLinkDao.links.values.first()
        assertEquals("901", link.remoteMediaListId)
    }

    @Test
    fun `fractional progress invariance preserves decimal chapter when remote integer matches`() = runTest(testDispatcher) {
        // Local has 12.5 chapters
        val localItem = MediaItemEntity(
            id = "manga-fractional",
            categoryId = "cat_manga",
            title = "Chainsaw Man",
            userStatus = "IN_PROGRESS",
            progress = 12.5,
            progressTotal = 150.0,
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_010_000L
        )
        fakeMediaItemDao.items[localItem.id] = localItem

        val snapshot = SyncSnapshot(
            remoteUpdatedAt = 1600000010L,
            localUpdatedAt = 1_600_000_010_000L,
            progress = 12.5,
            status = "IN_PROGRESS"
        )
        val link = MediaLinkEntity(
            id = "link-fractional",
            mediaId = localItem.id,
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "5555",
            remoteMediaListId = "777",
            url = "https://anilist.co/manga/5555",
            state = "LINKED",
            syncStatus = "SYNCED",
            lastSyncedAt = 1_600_000_010_000L,
            syncedSnapshot = snapshot.toJson(),
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_010_000L
        )
        fakeMediaLinkDao.links[link.id] = link

        // AniList returns integer 12 with same remote timestamp
        val mangaJson = """
        {
          "data": {
            "MediaListCollection": {
              "hasNextChunk": false,
              "lists": [
                {
                  "name": "Reading",
                  "isCustomList": false,
                  "status": "CURRENT",
                  "entries": [
                    {
                      "id": 777,
                      "mediaId": 5555,
                      "status": "CURRENT",
                      "progress": 12,
                      "updatedAt": 1600000010,
                      "media": {
                        "id": 5555,
                        "format": "MANGA",
                        "title": { "userPreferred": "Chainsaw Man" }
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        responseHandler = { _, body ->
            if (body.contains("\"type\":\"MANGA\"")) createResponse(200, mangaJson)
            else createResponse(200, """{"data":{"MediaListCollection":{"hasNextChunk":false,"lists":[]}}}""")
        }

        engine.syncAll()

        val updated = fakeMediaItemDao.items[localItem.id]!!
        assertEquals(12.5, updated.progress, 0.001)
    }

    @Test
    fun `race condition fence prevents delayed network response from clobbering concurrent user tap`() = runTest(testDispatcher) {
        val localItem = MediaItemEntity(
            id = "item-race",
            categoryId = "cat_manga",
            title = "Jujutsu Kaisen",
            userStatus = "IN_PROGRESS",
            progress = 10.0,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaItemDao.items[localItem.id] = localItem

        val link = MediaLinkEntity(
            id = "link-race",
            mediaId = localItem.id,
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "8888",
            remoteMediaListId = "999",
            url = "https://anilist.co/manga/8888",
            state = "LINKED",
            syncStatus = "DIRTY_LOCAL",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaLinkDao.links[link.id] = link

        val saveResponseJson = """
        {
          "data": {
            "SaveMediaListEntry": {
              "id": 999,
              "mediaId": 8888,
              "status": "CURRENT",
              "progress": 10,
              "updatedAt": 1700000010
            }
          }
        }
        """.trimIndent()

        // When mutation request arrives, simulate concurrent user tap advancing progress to 11.0
        responseHandler = { _, _ ->
            // User taps +1 while mutation is in flight!
            fakeMediaItemDao.items[localItem.id] = localItem.copy(
                progress = 11.0,
                updatedAt = virtualTimeMs + 500L
            )
            createResponse(200, saveResponseJson)
        }

        val success = engine.pushDirtyItem(localItem.id)
        assertTrue(success)

        // Item progress should remain 11.0 (not clobbered by 10)
        val currentItem = fakeMediaItemDao.items[localItem.id]!!
        assertEquals(11.0, currentItem.progress, 0.001)

        // Link must remain DIRTY_LOCAL because it still has pending unpushed progress (11.0)
        val currentLink = fakeMediaLinkDao.links[link.id]!!
        assertEquals(ThreeWayMergeResolver.SYNC_STATUS_DIRTY_LOCAL, currentLink.syncStatus)
    }

    @Test
    fun `pagination fault tolerance aborts remote deletion reconciliation when chunk fails`() = runTest(testDispatcher) {
        // Pre-existing local linked item
        val localItem = MediaItemEntity(
            id = "local-manga-1",
            categoryId = "cat_manga",
            title = "Dorohedoro",
            userStatus = "IN_PROGRESS",
            progress = 50.0,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaItemDao.items[localItem.id] = localItem

        val link = MediaLinkEntity(
            id = "link-1",
            mediaId = localItem.id,
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "77777",
            remoteMediaListId = "111",
            url = "https://anilist.co/manga/77777",
            state = "LINKED",
            syncStatus = "SYNCED",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaLinkDao.links[link.id] = link

        // Chunk 1 succeeds, Chunk 2 throws IOException
        responseHandler = { _, body ->
            if (body.contains("\"chunk\":1")) {
                createResponse(200, """
                {
                  "data": {
                    "MediaListCollection": {
                      "hasNextChunk": true,
                      "lists": []
                    }
                  }
                }
                """)
            } else {
                throw IOException("Network connection severed on Chunk 2")
            }
        }

        val result = engine.syncAll()
        assertTrue(result is SyncResult.Error)

        // Because pagination aborted prematurely, remote deletion reconciliation MUST NOT run
        // Existing link must remain strictly LINKED
        val linkAfterAbortedSync = fakeMediaLinkDao.links[link.id]!!
        assertEquals("LINKED", linkAfterAbortedSync.state)
    }

    @Test
    fun `remote deletion reconciliation marks missing remote items as UNLINKED without deleting local data`() = runTest(testDispatcher) {
        val localItem = MediaItemEntity(
            id = "local-manga-deleted-on-remote",
            categoryId = "cat_manga",
            title = "Old Manga Deleted On AniList",
            notes = "My precious private notes",
            userStatus = "COMPLETED",
            progress = 100.0,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaItemDao.items[localItem.id] = localItem

        val link = MediaLinkEntity(
            id = "link-deleted-on-remote",
            mediaId = localItem.id,
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "99999",
            remoteMediaListId = "333",
            url = "https://anilist.co/manga/99999",
            state = "LINKED",
            syncStatus = "SYNCED",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaLinkDao.links[link.id] = link

        // Remote returns complete sync with 0 entries
        val emptyCollectionJson = """
        {
          "data": {
            "MediaListCollection": {
              "hasNextChunk": false,
              "lists": []
            }
          }
        }
        """.trimIndent()

        responseHandler = { _, _ -> createResponse(200, emptyCollectionJson) }

        val result = engine.syncAll()
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.unlinkedCount)

        // Link is UNLINKED
        val updatedLink = fakeMediaLinkDao.links[link.id]!!
        assertEquals("UNLINKED", updatedLink.state)

        // Local media item, notes, and progress are NEVER deleted
        val localItemAfterSync = fakeMediaItemDao.items[localItem.id]
        assertNotNull(localItemAfterSync)
        assertEquals("My precious private notes", localItemAfterSync!!.notes)
        assertEquals(100.0, localItemAfterSync.progress, 0.001)
        assertNull(localItemAfterSync.deletedAt)
    }

    @Test
    fun `chunk batching executes in batches of 50`() = runTest(testDispatcher) {
        // Generate 120 entries
        val entriesJson = (1..120).joinToString(",") { i ->
            """
            {
              "id": $i,
              "mediaId": ${1000 + i},
              "status": "CURRENT",
              "progress": 1,
              "media": {
                "id": ${1000 + i},
                "format": "MANGA",
                "title": { "userPreferred": "Title $i" }
              }
            }
            """
        }

        val mangaJson = """
        {
          "data": {
            "MediaListCollection": {
              "hasNextChunk": false,
              "lists": [
                {
                  "name": "Reading",
                  "isCustomList": false,
                  "status": "CURRENT",
                  "entries": [$entriesJson]
                }
              ]
            }
          }
        }
        """.trimIndent()

        responseHandler = { _, body ->
            if (body.contains("\"type\":\"MANGA\"")) createResponse(200, mangaJson)
            else createResponse(200, """{"data":{"MediaListCollection":{"hasNextChunk":false,"lists":[]}}}""")
        }

        val result = engine.syncAll()
        assertTrue(result is SyncResult.Success)

        // 120 items chunked in 50 -> 3 batches (50, 50, 20)
        assertEquals(3, transactionCount)
        assertEquals(120, fakeMediaItemDao.items.size)
    }

    @Test
    fun `concurrent monotonic progress resolution picks max progress and pushes if local won`() = runTest(testDispatcher) {
        virtualTimeMs = 1_700_000_100_000L
        // Local has progress 25
        val localItem = MediaItemEntity(
            id = "item-concurrent",
            categoryId = "cat_manga",
            title = "Bleach",
            userStatus = "IN_PROGRESS",
            progress = 25.0,
            createdAt = 1_000_000L,
            updatedAt = 1_700_000_000_000L
        )
        fakeMediaItemDao.items[localItem.id] = localItem

        val snapshot = SyncSnapshot(
            remoteUpdatedAt = 1690000000L,
            localUpdatedAt = 1_690_000_000_000L,
            progress = 10.0,
            status = "IN_PROGRESS"
        )
        val link = MediaLinkEntity(
            id = "link-concurrent",
            mediaId = localItem.id,
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "4004",
            remoteMediaListId = "5005",
            url = "https://anilist.co/manga/4004",
            state = "LINKED",
            syncStatus = "SYNCED",
            lastSyncedAt = 1_690_000_000_000L,
            syncedSnapshot = snapshot.toJson(),
            createdAt = 1_000_000L,
            updatedAt = 1_690_000_000_000L
        )
        fakeMediaLinkDao.links[link.id] = link

        // AniList returns progress 20 (both edited concurrently relative to snapshot 10)
        val mangaJson = """
        {
          "data": {
            "MediaListCollection": {
              "hasNextChunk": false,
              "lists": [
                {
                  "name": "Reading",
                  "isCustomList": false,
                  "status": "CURRENT",
                  "entries": [
                    {
                      "id": 5005,
                      "mediaId": 4004,
                      "status": "CURRENT",
                      "progress": 20,
                      "updatedAt": 1700000050,
                      "media": {
                        "id": 4004,
                        "format": "MANGA",
                        "title": { "userPreferred": "Bleach" }
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val saveResponseJson = """
        {
          "data": {
            "SaveMediaListEntry": {
              "id": 5005,
              "mediaId": 4004,
              "status": "CURRENT",
              "progress": 25,
              "updatedAt": 1700000060
            }
          }
        }
        """.trimIndent()

        responseHandler = { _, body ->
            when {
                body.contains("SaveMediaListEntry") -> createResponse(200, saveResponseJson)
                body.contains("\"type\":\"MANGA\"") -> createResponse(200, mangaJson)
                else -> createResponse(200, """{"data":{"MediaListCollection":{"hasNextChunk":false,"lists":[]}}}""")
            }
        }

        val result = engine.syncAll()
        assertTrue(result is SyncResult.Success)

        val updatedItem = fakeMediaItemDao.items[localItem.id]!!
        assertEquals(25.0, updatedItem.progress, 0.001)

        // Pushed back to AniList and marked SYNCED
        val updatedLink = fakeMediaLinkDao.links[link.id]!!
        assertEquals("SYNCED", updatedLink.syncStatus)
    }

    // --- Fakes ---

    class FakeMediaItemDao : MediaItemDao {
        val items = mutableMapOf<String, MediaItemEntity>()

        override fun observeAll(): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeByCategory(categoryId: String): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeCategoryCounts(): Flow<List<CategoryCount>> = emptyFlow()
        override fun observeStatusCounts(categoryId: String?): Flow<List<StatusCount>> = emptyFlow()
        override fun observeFiltered(categoryId: String?, filterType: String, exactStatus: String?): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeCount(): Flow<Int> = emptyFlow()
        override fun observeById(id: String): Flow<ActiveMediaItemView?> = emptyFlow()
        override suspend fun getById(id: String): MediaItemEntity? = items[id]
        override suspend fun getActiveById(id: String): ActiveMediaItemView? = null
        override suspend fun getAll(): List<ActiveMediaItemView> = emptyList()
        override suspend fun getActiveByCategoryId(categoryId: String): List<MediaItemEntity> = items.values.filter { it.categoryId == categoryId && it.deletedAt == null }
        override suspend fun upsert(item: MediaItemEntity) { items[item.id] = item }
        override suspend fun upsertAll(items: List<MediaItemEntity>) { items.forEach { this.items[it.id] = it } }
        override suspend fun atomicIncrementProgress(id: String, delta: Double, now: Long): Int = 1
        override suspend fun softDelete(id: String, now: Long) {}
        override suspend fun restore(id: String, now: Long) {}
        override suspend fun hardDelete(id: String) { items.remove(id) }
        override suspend fun deleteAll() { items.clear() }
        override fun getStats(): Flow<MediaStats> = emptyFlow()
    }

    class FakeMediaLinkDao : MediaLinkDao {
        val links = mutableMapOf<String, MediaLinkEntity>()

        override fun observeByMediaId(mediaId: String): Flow<List<MediaLinkEntity>> = emptyFlow()
        override suspend fun getByMediaId(mediaId: String): List<MediaLinkEntity> =
            links.values.filter { it.mediaId == mediaId }
        override suspend fun getByRemoteId(providerId: String, remoteId: String): MediaLinkEntity? =
            links.values.find { it.providerId == providerId && it.remoteId == remoteId }
        override suspend fun getDirtyLinks(providerId: String): List<MediaLinkEntity> =
            links.values.filter { it.providerId == providerId && it.syncStatus == "DIRTY_LOCAL" }
        override suspend fun getByProviderAndState(providerId: String, state: String): List<MediaLinkEntity> =
            links.values.filter { it.providerId == providerId && it.state == state }
        override suspend fun getByProviderId(providerId: String): List<MediaLinkEntity> =
            links.values.filter { it.providerId == providerId }
        override suspend fun updateSyncStatusByMediaId(mediaId: String, syncStatus: String, updatedAt: Long): Int {
            var updated = 0
            links.values.filter { it.mediaId == mediaId && it.state == "LINKED" }.forEach {
                links[it.id] = it.copy(syncStatus = syncStatus, updatedAt = updatedAt)
                updated++
            }
            return updated
        }
        override suspend fun upsert(link: MediaLinkEntity) { links[link.id] = link }
        override suspend fun upsertAll(links: List<MediaLinkEntity>) { links.forEach { this.links[it.id] = it } }
        override suspend fun delete(id: String) { links.remove(id) }
    }

    class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String> ?: defValues)
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
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
                if (clearRequested) backingData.clear()
                backingData.putAll(uncommitted)
                removed.forEach { backingData.remove(it) }
            }
        }
    }
}
