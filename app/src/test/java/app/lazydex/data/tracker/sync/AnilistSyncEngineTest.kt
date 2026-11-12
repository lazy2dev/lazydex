package app.lazydex.data.tracker.sync

import android.content.SharedPreferences
import app.lazydex.data.local.dao.TypeCount
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.dao.StatusCount
import app.lazydex.data.local.entity.ActiveMediaItemView
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.local.entity.UserDataEntity
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
    fun `inbound sync inserts new entries with proper type and ongoing metadata`() = runTest(testDispatcher) {
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
        assertEquals("MANGA", item.type)
        assertEquals("MANGA", item.format)
        val userData = fakeMediaItemDao.userData[item.id]!!
        assertEquals("IN_PROGRESS", userData.userStatus)
        assertEquals(25.0, userData.progress, 0.001)
        assertEquals(100.0, item.progressTotal!!, 0.001)
        assertEquals(3.0, userData.secondaryProgress, 0.001)
        assertEquals(10.0, item.secondaryProgressTotal!!, 0.001)
        assertEquals(85, userData.scoreRaw)
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
    fun `inbound sync updates linked item by remoteId with monotonic progress and preserves status`() = runTest(testDispatcher) {
        val existingItem = MediaItemEntity(
            id = "local_frieren",
            type = "MANGA",
            format = "MANGA",
            title = "Frieren: Beyond Journey's End",
            titleEnglish = "Frieren: Beyond Journey's End",
            titleRomaji = "Sousou no Frieren",
            titleNative = "葬送のフリーレン",
            coverImagePath = "",
            coverImageUrl = null,
            bannerImage = null,
            description = "Local added manga",
            author = "",
            progressTotal = null,
            secondaryProgressTotal = null,
            genres = "[]",
            tags = "[]",
            isAdult = false,
            durationMinutes = null,
            avgChapterLength = null,
            lengthUnitOverride = null,
            playbackSpeed = 1.0,
            createdAt = 1700000000,
            updatedAt = 1700000000,
            deletedAt = null
        )
        fakeMediaItemDao.items[existingItem.id] = existingItem
        fakeMediaItemDao.userData[existingItem.id] = UserDataEntity(
            mediaId = existingItem.id,
            userStatus = "IN_PROGRESS",
            progress = 10.0,
            secondaryProgress = 0.0,
            scoreRaw = null,
            repeat = 0,
            notes = "",
            isPrivate = false,
            updatedAt = 1700000000
        )

        val existingLink = MediaLinkEntity(
            id = "link_frieren",
            mediaId = existingItem.id,
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "2002",
            remoteMediaListId = "905",
            url = "https://anilist.co/manga/2002",
            state = "LINKED",
            syncStatus = "SYNCED",
            createdAt = 1700000000,
            updatedAt = 1700000000
        )
        fakeMediaLinkDao.links[existingLink.id] = existingLink

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

        val updatedUserData = fakeMediaItemDao.userData["local_frieren"]!!
        // 2. Monotonic progress: advanced from 10.0 to 15.0
        assertEquals(15.0, updatedUserData.progress, 0.001)
        assertEquals("IN_PROGRESS", updatedUserData.userStatus)

        // 3. Link remains LINKED
        assertEquals(1, fakeMediaLinkDao.links.size)
        val link = fakeMediaLinkDao.links.values.first()
        assertEquals("local_frieren", link.mediaId)
        assertEquals("2002", link.remoteId)
        assertEquals("LINKED", link.state)
    }

    @Test
    fun `when autoSyncProgressFromTrackers is false inbound sync does not overwrite local progress`() = runTest(testDispatcher) {
        tokenStore.autoSyncProgressFromTrackers = false

        val existingItem = MediaItemEntity(
            id = "local_frieren",
            type = "MANGA",
            format = "MANGA",
            title = "Frieren: Beyond Journey's End",
            titleEnglish = "Frieren: Beyond Journey's End",
            titleRomaji = "Sousou no Frieren",
            titleNative = "葬送のフリーレン",
            coverImagePath = "",
            coverImageUrl = null,
            bannerImage = null,
            description = "Local added manga",
            author = "",
            progressTotal = null,
            secondaryProgressTotal = null,
            genres = "[]",
            tags = "[]",
            isAdult = false,
            durationMinutes = null,
            avgChapterLength = null,
            lengthUnitOverride = null,
            playbackSpeed = 1.0,
            createdAt = 1700000000,
            updatedAt = 1700000000,
            deletedAt = null
        )
        fakeMediaItemDao.items[existingItem.id] = existingItem
        fakeMediaItemDao.userData[existingItem.id] = UserDataEntity(
            mediaId = existingItem.id,
            userStatus = "IN_PROGRESS",
            progress = 10.0,
            secondaryProgress = 2.0,
            scoreRaw = null,
            repeat = 0,
            notes = "",
            isPrivate = false,
            updatedAt = 1700000000
        )

        val existingLink = MediaLinkEntity(
            id = "link_frieren",
            mediaId = "local_frieren",
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "2002",
            remoteMediaListId = "3003",
            url = "https://anilist.co/manga/2002",
            state = "LINKED",
            syncStatus = "SYNCED",
            lastSyncedAt = 1000L,
            syncedSnapshot = """{"progress":10.0,"secondaryProgress":2.0,"status":"CURRENT"}""",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        fakeMediaLinkDao.links[existingLink.id] = existingLink

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
                      "id": 3003,
                      "mediaId": 2002,
                      "status": "CURRENT",
                      "progress": 25,
                      "progressVolumes": 5,
                      "media": {
                        "id": 2002,
                        "format": "MANGA",
                        "countryOfOrigin": "JP",
                        "title": { "romaji": "Sousou no Frieren" },
                        "chapters": 130
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

        responseHandler = { _, body ->
            when {
              body.contains("\"type\":\"MANGA\"") -> createResponse(200, mangaJson)
              else -> createResponse(200, emptyAnimeJson)
            }
        }

        val result = engine.syncAll()
        assertTrue(result is SyncResult.Success)

        val updatedUserData = fakeMediaItemDao.userData["local_frieren"]!!
        // Local progress must remain preserved because autoSyncProgressFromTrackers = false
        assertEquals(10.0, updatedUserData.progress, 0.001)
        assertEquals(2.0, updatedUserData.secondaryProgress, 0.001)
    }

    @Test
    fun `novel format maps to typ_novel and anime maps to typ_anime`() = runTest(testDispatcher) {
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
        assertEquals("NOVEL", novelItem!!.type)
        assertEquals("LIGHT_NOVEL", novelItem.format)

        val animeItem = fakeMediaItemDao.items.values.find { it.title == "Frieren" }
        assertNotNull(animeItem)
        assertEquals("ANIME", animeItem!!.type)
        assertEquals("TV", animeItem.format)
        assertEquals(24.0, animeItem.progressTotal!!, 0.001)
    }

    @Test
    fun `mapToTypeId maps countries and formats to specific subtypes correctly`() {
        // Manga country mapping
        assertEquals("typ_manhwa", engine.mapToTypeId("MANGA", "MANGA", countryOfOrigin = "KR"))
        assertEquals("typ_manhua", engine.mapToTypeId("MANGA", "MANGA", countryOfOrigin = "CN"))
        assertEquals("typ_manhua", engine.mapToTypeId("MANGA", "MANGA", countryOfOrigin = "TW"))
        assertEquals("typ_manga_jp", engine.mapToTypeId("MANGA", "MANGA", countryOfOrigin = "JP"))

        // Fallback to tags when country is missing
        assertEquals("typ_manhwa", engine.mapToTypeId("MANGA", "MANGA", countryOfOrigin = null, tags = listOf("Action", "Manhwa")))
        assertEquals("typ_manhwa", engine.mapToTypeId("MANGA", "MANGA", countryOfOrigin = null, tags = listOf("Webtoon", "Romance")))
        assertEquals("typ_manhua", engine.mapToTypeId("MANGA", "MANGA", countryOfOrigin = null, tags = listOf("Cultivation", "Manhua")))
        assertEquals("typ_manga_jp", engine.mapToTypeId("MANGA", "MANGA", countryOfOrigin = null, tags = listOf("Action")))

        // Novel country mapping
        assertEquals("typ_novel_cn", engine.mapToTypeId("NOVEL", "MANGA", countryOfOrigin = "CN"))
        assertEquals("typ_novel_kr", engine.mapToTypeId("NOVEL", "MANGA", countryOfOrigin = "KR"))
        assertEquals("typ_novel_ln", engine.mapToTypeId("NOVEL", "MANGA", countryOfOrigin = "JP"))
        assertEquals("typ_novel_ln", engine.mapToTypeId("NOVEL", "MANGA", countryOfOrigin = null))

        // Anime format mapping
        assertEquals("typ_anime_movie", engine.mapToTypeId("MOVIE", "ANIME"))
        assertEquals("typ_anime_ova", engine.mapToTypeId("OVA", "ANIME"))
        assertEquals("typ_anime_ova", engine.mapToTypeId("SPECIAL", "ANIME"))
        assertEquals("typ_anime_series", engine.mapToTypeId("TV", "ANIME"))
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
            type = "MANGA",
            format = "MANGA",
            title = "Chainsaw Man",
            progressTotal = 150.0,
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_010_000L
        )
        fakeMediaItemDao.items[localItem.id] = localItem
        fakeMediaItemDao.userData[localItem.id] = UserDataEntity(
            mediaId = localItem.id,
            userStatus = "IN_PROGRESS",
            progress = 12.5,
            updatedAt = 1_600_000_010_000L
        )

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

        val updated = fakeMediaItemDao.userData[localItem.id]!!
        assertEquals(12.5, updated.progress, 0.001)
    }

    @Test
    fun `race condition fence prevents delayed network response from clobbering concurrent user tap`() = runTest(testDispatcher) {
        val localItem = MediaItemEntity(
            id = "item-race",
            type = "MANGA",
            format = "MANGA",
            title = "Jujutsu Kaisen",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaItemDao.items[localItem.id] = localItem
        fakeMediaItemDao.userData[localItem.id] = UserDataEntity(
            mediaId = localItem.id,
            userStatus = "IN_PROGRESS",
            progress = 10.0,
            updatedAt = 1_000_000L
        )

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
            fakeMediaItemDao.userData[localItem.id] = fakeMediaItemDao.userData[localItem.id]!!.copy(
                progress = 11.0,
                updatedAt = virtualTimeMs + 500L
            )
            createResponse(200, saveResponseJson)
        }

        val success = engine.pushDirtyItem(localItem.id)
        assertTrue(success)

        // Item progress should remain 11.0 (not clobbered by 10)
        val currentUserData = fakeMediaItemDao.userData[localItem.id]!!
        assertEquals(11.0, currentUserData.progress, 0.001)

        // Link must remain DIRTY_LOCAL because it still has pending unpushed progress (11.0)
        val currentLink = fakeMediaLinkDao.links[link.id]!!
        assertEquals(ThreeWayMergeResolver.SYNC_STATUS_DIRTY_LOCAL, currentLink.syncStatus)
    }

    @Test
    fun `pagination fault tolerance aborts remote deletion reconciliation when chunk fails`() = runTest(testDispatcher) {
        // Pre-existing local linked item
        val localItem = MediaItemEntity(
            id = "local-manga-1",
            type = "MANGA",
            format = "MANGA",
            title = "Dorohedoro",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaItemDao.items[localItem.id] = localItem
        fakeMediaItemDao.userData[localItem.id] = UserDataEntity(
            mediaId = localItem.id,
            userStatus = "IN_PROGRESS",
            progress = 50.0,
            updatedAt = 1_000_000L
        )

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
    fun `safe import preserves existing links without unlinking when items are missing on remote`() = runTest(testDispatcher) {
        val localItem = MediaItemEntity(
            id = "local-manga-deleted-on-remote",
            type = "MANGA",
            format = "MANGA",
            title = "Old Manga Deleted On AniList",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaItemDao.items[localItem.id] = localItem
        fakeMediaItemDao.userData[localItem.id] = UserDataEntity(
            mediaId = localItem.id,
            userStatus = "COMPLETED",
            progress = 100.0,
            notes = "My precious private notes",
            updatedAt = 1_000_000L
        )

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
        assertEquals(0, success.unlinkedCount)

        // Link remains LINKED in safe non-destructive import
        val updatedLink = fakeMediaLinkDao.links[link.id]!!
        assertEquals("LINKED", updatedLink.state)

        // Local media item, notes, and progress are NEVER deleted
        val localItemAfterSync = fakeMediaItemDao.items[localItem.id]
        assertNotNull(localItemAfterSync)
        assertNull(localItemAfterSync!!.deletedAt)
        val localUserDataAfterSync = fakeMediaItemDao.userData[localItem.id]
        assertNotNull(localUserDataAfterSync)
        assertEquals("My precious private notes", localUserDataAfterSync!!.notes)
        assertEquals(100.0, localUserDataAfterSync.progress, 0.001)
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
            type = "MANGA",
            format = "MANGA",
            title = "Bleach",
            createdAt = 1_000_000L,
            updatedAt = 1_700_000_000_000L
        )
        fakeMediaItemDao.items[localItem.id] = localItem
        fakeMediaItemDao.userData[localItem.id] = UserDataEntity(
            mediaId = localItem.id,
            userStatus = "IN_PROGRESS",
            progress = 25.0,
            updatedAt = 1_700_000_000_000L
        )

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

        val updatedUserData = fakeMediaItemDao.userData[localItem.id]!!
        assertEquals(25.0, updatedUserData.progress, 0.001)

        // Pushed back to AniList and marked SYNCED
        val updatedLink = fakeMediaLinkDao.links[link.id]!!
        assertEquals("SYNCED", updatedLink.syncStatus)
    }

    @Test
    fun `inbound sync extracts description html sanitized, all tags, and dates`() = runTest(testDispatcher) {
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
                      "mediaId": 2005,
                      "status": "CURRENT",
                      "scoreRaw": 80,
                      "progress": 37,
                      "progressVolumes": 0,
                      "repeat": 0,
                      "private": false,
                      "notes": "",
                      "startedAt": { "year": 2024, "month": 1, "day": 15 },
                      "completedAt": { "year": null, "month": null, "day": null },
                      "updatedAt": 1700000000,
                      "media": {
                        "id": 2005,
                        "format": "MANGA",
                        "status": "RELEASING",
                        "isAdult": false,
                        "chapters": 50,
                        "volumes": 0,
                        "genres": ["Action", "Drama", "Fantasy"],
                        "tags": [
                          { "name": "Military", "isMediaSpoiler": false },
                          { "name": "Reincarnation", "isMediaSpoiler": true }
                        ],
                        "description": "A veteran returns.<br><br><b>Enlistment</b> day awaits.",
                        "startDate": { "year": 2023, "month": 10, "day": 1 },
                        "coverImage": { "extraLarge": "https://img.anilist.co/cover2.jpg" },
                        "bannerImage": null,
                        "title": {
                          "userPreferred": "Gwihwanhaenneunde Ipdae Jeonnarida",
                          "english": null,
                          "romaji": "Gwihwanhaenneunde Ipdae Jeonnarida",
                          "native": "귀환했는데 입대전날이다"
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

        responseHandler = { _, body ->
            if (body.contains("\"type\":\"MANGA\"")) {
                createResponse(200, mangaJson)
            } else {
                createResponse(200, """{"data":{"MediaListCollection":{"hasNextChunk":false,"lists":[]}}}""")
            }
        }

        val result = engine.syncAll()
        assertTrue(result is SyncResult.Success)

        val insertedItem = fakeMediaItemDao.items.values.first { it.title == "Gwihwanhaenneunde Ipdae Jeonnarida" }
        assertEquals("A veteran returns.\n\nEnlistment day awaits.", insertedItem.description)
        assertTrue(insertedItem.tags.contains("Military"))
        assertTrue(insertedItem.tags.contains("Reincarnation"))
        val insertedUserData = fakeMediaItemDao.userData[insertedItem.id]!!
        assertEquals(2024, insertedUserData.startYear)
        assertEquals(1, insertedUserData.startMonth)
        assertEquals(15, insertedUserData.startDay)
    }

    @Test
    fun `pushDirtyItem for Anime omits progressVolumes even if secondaryProgress is set`() = runTest(testDispatcher) {
        val animeItem = MediaItemEntity(
            id = "anime-push-test",
            type = "ANIME",
            format = "TV",
            title = "Attack on Titan",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaItemDao.items[animeItem.id] = animeItem
        fakeMediaItemDao.userData[animeItem.id] = UserDataEntity(
            mediaId = animeItem.id,
            userStatus = "IN_PROGRESS",
            progress = 12.0,
            secondaryProgress = 2.0,
            updatedAt = 1_000_000L
        )

        val link = MediaLinkEntity(
            id = "link-anime-push",
            mediaId = animeItem.id,
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "16498",
            remoteMediaListId = "99901",
            url = "https://anilist.co/anime/16498",
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
              "id": 99901,
              "mediaId": 16498,
              "status": "CURRENT",
              "progress": 12,
              "updatedAt": 1700000020
            }
          }
        }
        """.trimIndent()

        responseHandler = { _, _ -> createResponse(200, saveResponseJson) }

        val success = engine.pushDirtyItem(animeItem.id)
        assertTrue(success)

        val lastPayload = recordedBodies.last()
        assertTrue("GraphQL mutation variables must NOT contain progressVolumes for Anime",
            !lastPayload.contains("\"progressVolumes\":2")
        )
    }

    @Test
    fun `pushDirtyItem for Manga includes progressVolumes when secondaryProgress is present`() = runTest(testDispatcher) {
        val mangaItem = MediaItemEntity(
            id = "manga-push-test",
            type = "MANGA",
            format = "MANGA",
            title = "Berserk",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaItemDao.items[mangaItem.id] = mangaItem
        fakeMediaItemDao.userData[mangaItem.id] = UserDataEntity(
            mediaId = mangaItem.id,
            userStatus = "IN_PROGRESS",
            progress = 40.0,
            secondaryProgress = 5.0,
            updatedAt = 1_000_000L
        )

        val link = MediaLinkEntity(
            id = "link-manga-push",
            mediaId = mangaItem.id,
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "30002",
            remoteMediaListId = "99902",
            url = "https://anilist.co/manga/30002",
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
              "id": 99902,
              "mediaId": 30002,
              "status": "CURRENT",
              "progress": 40,
              "updatedAt": 1700000020
            }
          }
        }
        """.trimIndent()

        responseHandler = { _, _ -> createResponse(200, saveResponseJson) }

        val success = engine.pushDirtyItem(mangaItem.id)
        assertTrue(success)

        val lastPayload = recordedBodies.last()
        assertTrue("GraphQL mutation variables must contain progressVolumes for Manga",
            lastPayload.contains("\"progressVolumes\":5")
        )
    }

    @Test
    fun `re-syncing AniList item with unchanged progress preserves local progressIncreasedAt`() = runTest(testDispatcher) {
        val mangaItem = MediaItemEntity(
            id = "resync-history-test",
            type = "MANGA",
            format = "MANGA",
            title = "Vagabond",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaItemDao.items[mangaItem.id] = mangaItem
        val originalHistoryTimestamp = 1_650_000_000_000L
        fakeMediaItemDao.userData[mangaItem.id] = UserDataEntity(
            mediaId = mangaItem.id,
            userStatus = "IN_PROGRESS",
            progress = 50.0,
            updatedAt = 1_000_000L,
            progressIncreasedAt = originalHistoryTimestamp
        )

        val link = MediaLinkEntity(
            id = "link-resync-test",
            mediaId = mangaItem.id,
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "30656",
            remoteMediaListId = "88888",
            url = "https://anilist.co/manga/30656",
            state = "LINKED",
            syncStatus = "SYNCED",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        fakeMediaLinkDao.links[link.id] = link

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
                      "id": 88888,
                      "mediaId": 30656,
                      "status": "CURRENT",
                      "progress": 50,
                      "updatedAt": 1700000088,
                      "media": {
                        "id": 30656,
                        "format": "MANGA",
                        "title": { "userPreferred": "Vagabond" }
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

        val result = engine.syncAll()
        assertTrue(result is SyncResult.Success)

        val userDataAfterSync = fakeMediaItemDao.userData[mangaItem.id]!!
        assertEquals(50.0, userDataAfterSync.progress, 0.001)
        // History timestamp must be completely preserved - not updated to remoteUpdatedMs or now
        assertEquals(originalHistoryTimestamp, userDataAfterSync.progressIncreasedAt)
    }

    // --- Fakes ---

    class FakeMediaItemDao : MediaItemDao {
        val items = mutableMapOf<String, MediaItemEntity>()
        val userData = mutableMapOf<String, UserDataEntity>()

        override fun observeAll(): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeHistory(): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeByType(type: String): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeTypeCounts(): Flow<List<TypeCount>> = emptyFlow()
        override fun observeStatusCounts(type: String?): Flow<List<StatusCount>> = emptyFlow()
        override fun observeFiltered(type: String?, format: String?, filterType: String, exactStatus: String?): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeFilteredLegacy(type: String?, filterType: String, exactStatus: String?): Flow<List<ActiveMediaItemView>> = emptyFlow()
        override fun observeCount(): Flow<Int> = emptyFlow()
        override fun observeById(id: String): Flow<ActiveMediaItemView?> = emptyFlow()
        override suspend fun getById(id: String): MediaItemEntity? = items[id]
        override suspend fun getUserData(mediaId: String): UserDataEntity? = userData[mediaId]
        override suspend fun upsertUserData(userData: UserDataEntity) { this.userData[userData.mediaId] = userData }
        override suspend fun upsertUserDataAll(items: List<UserDataEntity>) { items.forEach { this.userData[it.mediaId] = it } }
        override suspend fun getActiveById(id: String): ActiveMediaItemView? = items[id]?.let { e ->
            val u = userData[id]
            ActiveMediaItemView(
                id = e.id,
                type = e.type,
                format = e.format,
                countryOfOrigin = e.countryOfOrigin,
                title = e.title,
                titleEnglish = e.titleEnglish,
                titleRomaji = e.titleRomaji,
                titleNative = e.titleNative,
                coverImagePath = e.coverImagePath,
                coverImageUrl = e.coverImageUrl,
                bannerImage = e.bannerImage,
                description = e.description,
                author = e.author,
                userStatus = u?.userStatus ?: "PLAN_TO",
                progress = u?.progress ?: 0.0,
                progressTotal = e.progressTotal,
                secondaryProgress = u?.secondaryProgress ?: 0.0,
                secondaryProgressTotal = e.secondaryProgressTotal,
                scoreRaw = u?.scoreRaw,
                repeat = u?.repeat ?: 0,
                notes = u?.notes ?: "",
                genres = e.genres,
                tags = e.tags,
                isPrivate = u?.isPrivate ?: false,
                isAdult = e.isAdult,
                startYear = u?.startYear,
                startMonth = u?.startMonth,
                startDay = u?.startDay,
                endYear = u?.endYear,
                endMonth = u?.endMonth,
                endDay = u?.endDay,
                durationMinutes = e.durationMinutes,
                avgChapterLength = e.avgChapterLength,
                lengthUnitOverride = e.lengthUnitOverride,
                playbackSpeed = e.playbackSpeed,
                createdAt = e.createdAt,
                catalogUpdatedAt = e.updatedAt,
                userUpdatedAt = u?.updatedAt,
                progressIncreasedAt = u?.progressIncreasedAt,
                deletedAt = e.deletedAt
            )
        }
        override suspend fun getAll(): List<ActiveMediaItemView> = emptyList()
        override suspend fun getActiveByType(type: String): List<MediaItemEntity> = items.values.filter { it.type == type && it.deletedAt == null }
        override suspend fun upsert(item: MediaItemEntity) { items[item.id] = item }
        override suspend fun upsertAll(items: List<MediaItemEntity>) { items.forEach { this.items[it.id] = it } }
        override suspend fun atomicIncrementProgress(id: String, delta: Double, now: Long): Int = 1
        override suspend fun softDelete(id: String, now: Long) {}
        override suspend fun restore(id: String, now: Long) {}
        override suspend fun hardDelete(id: String) { items.remove(id); userData.remove(id) }
        override suspend fun deleteAll() { items.clear(); userData.clear() }
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
