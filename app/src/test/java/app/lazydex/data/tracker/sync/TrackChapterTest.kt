package app.lazydex.data.tracker.sync

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import app.lazydex.data.tracker.anilist.AnilistApi
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.service.DelayedTrackingStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackChapterTest {

    private lateinit var fakePrefs: AnilistSyncEngineTest.FakeSharedPreferences
    private lateinit var fakeContext: Context
    private lateinit var tokenStorePrefs: AnilistSyncEngineTest.FakeSharedPreferences
    private lateinit var tokenStore: AnilistTokenStore
    private lateinit var delayedTrackingStore: DelayedTrackingStore

    private var pushSuccess = true
    private val pushedIds = mutableListOf<String>()

    private class FakeTestContext(private val prefs: SharedPreferences) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    private class StubSyncEngine(
        private val onPush: (String) -> Boolean
    ) : AnilistSyncEngine(
        client = AnilistApi(
            okHttpClient = okhttp3.OkHttpClient(),
            tokenStore = AnilistTokenStore(AnilistSyncEngineTest.FakeSharedPreferences()),
            limiter = app.lazydex.data.tracker.anilist.TokenBucketLimiter()
        ),
        mediaItemDao = AnilistSyncEngineTest.FakeMediaItemDao(),
        mediaLinkDao = AnilistSyncEngineTest.FakeMediaLinkDao(),
        tokenStore = AnilistTokenStore(AnilistSyncEngineTest.FakeSharedPreferences())
    ) {
        override suspend fun pushDirtyItem(mediaId: String): Boolean {
            return onPush(mediaId)
        }
    }

    @Before
    fun setUp() {
        fakePrefs = AnilistSyncEngineTest.FakeSharedPreferences()
        fakeContext = FakeTestContext(fakePrefs)
        tokenStorePrefs = AnilistSyncEngineTest.FakeSharedPreferences()
        tokenStore = AnilistTokenStore(tokenStorePrefs)
        delayedTrackingStore = DelayedTrackingStore(fakeContext)
        pushedIds.clear()
        pushSuccess = true
    }

    @Test
    fun `delayed tracking store adds, retrieves, and removes items`() {
        delayedTrackingStore.add("media-1", 15.0)
        delayedTrackingStore.add("media-2", 42.0)

        val items = delayedTrackingStore.getItems()
        assertEquals(2, items.size)
        assertTrue(items.any { it.mediaId == "media-1" && it.lastProgressRead == 15f })
        assertTrue(items.any { it.mediaId == "media-2" && it.lastProgressRead == 42f })

        // Monotonic check: lower number ignored
        delayedTrackingStore.add("media-1", 10.0)
        val itemsAfterLower = delayedTrackingStore.getItems()
        assertEquals(15f, itemsAfterLower.first { it.mediaId == "media-1" }.lastProgressRead)

        delayedTrackingStore.remove("media-1")
        val itemsAfterRemove = delayedTrackingStore.getItems()
        assertEquals(1, itemsAfterRemove.size)
        assertEquals("media-2", itemsAfterRemove.first().mediaId)
    }

    @Test
    fun `when not logged in TrackChapter does not push`() = runTest {
        tokenStorePrefs.edit().clear().commit()
        assertFalse(tokenStore.isLoggedIn)

        val stubEngine = StubSyncEngine { id ->
            pushedIds.add(id)
            true
        }
        val tracker = TrackChapter(stubEngine, tokenStore, delayedTrackingStore)

        tracker.await(fakeContext, "media-1", 10.0, setupJobOnFailure = false)

        assertTrue(pushedIds.isEmpty())
    }

    @Test
    fun `when push succeeds item is removed from delayed tracking store`() = runTest {
        tokenStore.accessToken = "valid_token"
        tokenStore.userId = 1234
        delayedTrackingStore.add("media-1", 10.0)

        val stubEngine = StubSyncEngine { id ->
            pushedIds.add(id)
            true
        }
        val tracker = TrackChapter(stubEngine, tokenStore, delayedTrackingStore)

        tracker.await(fakeContext, "media-1", 12.0, setupJobOnFailure = false)

        assertEquals(listOf("media-1"), pushedIds)
        assertTrue(delayedTrackingStore.getItems().isEmpty())
    }

    @Test
    fun `when push fails item is retained in delayed tracking store`() = runTest {
        tokenStore.accessToken = "valid_token"
        tokenStore.userId = 1234

        val stubEngine = StubSyncEngine { id ->
            pushedIds.add(id)
            false
        }
        val tracker = TrackChapter(stubEngine, tokenStore, delayedTrackingStore)

        tracker.await(fakeContext, "media-1", 20.0, setupJobOnFailure = false)

        assertEquals(listOf("media-1"), pushedIds)
        val items = delayedTrackingStore.getItems()
        assertEquals(1, items.size)
        assertEquals("media-1", items.first().mediaId)
        assertEquals(20f, items.first().lastProgressRead)
    }
}
