package app.lazydex.data.tracker.service

import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.repository.ProgressMutationRulesTest
import app.lazydex.data.tracker.anilist.AnilistApi
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.anilist.TokenBucketLimiter
import app.lazydex.data.tracker.sync.AnilistSyncEngineTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class MediaMetadataSyncWorkerTest {

    private lateinit var fakePrefs: AnilistSyncEngineTest.FakeSharedPreferences
    private lateinit var tokenStore: AnilistTokenStore
    private lateinit var fakeMediaItemDao: ProgressMutationRulesTest.FakeMediaItemDao
    private lateinit var fakeMediaLinkDao: ProgressMutationRulesTest.FakeMediaLinkDao
    private lateinit var fakeHttpClient: OkHttpClient
    private var responseHandler: (Request, String) -> Response = { _, _ ->
        throw IllegalStateException("No mock response")
    }

    @Before
    fun setup() {
        stopKoin()
        fakePrefs = AnilistSyncEngineTest.FakeSharedPreferences()
        tokenStore = AnilistTokenStore(fakePrefs)
        fakeMediaItemDao = ProgressMutationRulesTest.FakeMediaItemDao()
        fakeMediaLinkDao = ProgressMutationRulesTest.FakeMediaLinkDao()

        fakeHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                responseHandler(request, buffer.readUtf8())
            }
            .build()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun createResponse(code: Int, body: String): Response {
        return Response.Builder()
            .request(Request.Builder().url(AnilistApi.DEFAULT_BASE_URL).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .headers(Headers.headersOf("Content-Type", "application/json"))
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    @Test
    fun `worker marks metadataFetchedAt even when remote media returns null`() = runTest {
        tokenStore.accessToken = "test_token"

        val limiter = TokenBucketLimiter()
        val client = AnilistApi(
            okHttpClient = fakeHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val testModule = module {
            single { client }
            single<MediaItemDao> { fakeMediaItemDao }
            single<MediaLinkDao> { fakeMediaLinkDao }
            single { tokenStore }
        }

        startKoin {
            modules(testModule)
        }

        val item = MediaItemEntity(
            id = "media-1",
            type = "MANGA",
            title = "Test Manga",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        fakeMediaItemDao.items[item.id] = item

        val link = MediaLinkEntity(
            id = "link-1",
            mediaId = item.id,
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "5555",
            url = "https://anilist.co/manga/5555",
            state = "LINKED",
            createdAt = 1000L,
            updatedAt = 1000L,
            metadataFetchedAt = null
        )
        fakeMediaLinkDao.links[link.id] = link

        // AniList returns empty page (e.g. deleted/removed ID)
        responseHandler = { _, _ ->
            createResponse(
                code = 200,
                body = """{"data":{"Page":{"media":[]}}}"""
            )
        }

        // Run worker logic
        val pending = fakeMediaLinkDao.getLinksNeedingMetadata("anilist", 50)
        assertEquals(1, pending.size)

        val batchResult = client.getMediaBatchMetadata(listOf(5555), "MANGA")
        assertEquals(0, batchResult.size)

        // All requested IDs are stamped
        fakeMediaLinkDao.markMetadataFetched("anilist", listOf("5555"), 2000L)

        // Queue is drained: no longer needs metadata
        val afterPending = fakeMediaLinkDao.getLinksNeedingMetadata("anilist", 50)
        assertEquals(0, afterPending.size)
        assertEquals(2000L, fakeMediaLinkDao.links[link.id]?.metadataFetchedAt)
    }

    @Test
    fun `worker updates description, tags, and genres without touching updatedAt`() = runTest {
        tokenStore.accessToken = "test_token"

        val limiter = TokenBucketLimiter()
        val client = AnilistApi(
            okHttpClient = fakeHttpClient,
            tokenStore = tokenStore,
            limiter = limiter,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val item = MediaItemEntity(
            id = "media-2",
            type = "MANGA",
            title = "Solo Leveling",
            description = "",
            tags = "[]",
            genres = "[]",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        fakeMediaItemDao.items[item.id] = item

        val link = MediaLinkEntity(
            id = "link-2",
            mediaId = item.id,
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "105398",
            url = "https://anilist.co/manga/105398",
            state = "LINKED",
            createdAt = 1000L,
            updatedAt = 1000L,
            metadataFetchedAt = null
        )
        fakeMediaLinkDao.links[link.id] = link

        responseHandler = { _, _ ->
            createResponse(
                code = 200,
                body = """
                    {
                      "data": {
                        "Page": {
                          "media": [
                            {
                              "id": 105398,
                              "description": "Sung Jinwoo becomes the Shadow Monarch.",
                              "genres": ["Action", "Fantasy"],
                              "tags": [{ "name": "Monsters", "isMediaSpoiler": false }]
                            }
                          ]
                        }
                      }
                    }
                """.trimIndent()
            )
        }

        val remoteMetadataList = client.getMediaBatchMetadata(listOf(105398), "MANGA")
        val remote = remoteMetadataList.first()

        fakeMediaItemDao.updateMetadata(
            id = item.id,
            description = remote.description,
            tags = "[\"Monsters\"]",
            genres = "[\"Action\",\"Fantasy\"]"
        )
        fakeMediaLinkDao.markMetadataFetched("anilist", listOf("105398"), 3000L)

        val updated = fakeMediaItemDao.items[item.id]!!
        assertEquals("Sung Jinwoo becomes the Shadow Monarch.", updated.description)
        assertEquals("[\"Monsters\"]", updated.tags)
        assertEquals("[\"Action\",\"Fantasy\"]", updated.genres)
        // CRITICAL: updatedAt MUST remain unchanged!
        assertEquals(1000L, updated.updatedAt)
    }
}
