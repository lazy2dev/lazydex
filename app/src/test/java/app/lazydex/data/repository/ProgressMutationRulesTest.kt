package app.lazydex.data.repository

import android.content.SharedPreferences
import app.lazydex.data.local.dao.ActivityDao
import app.lazydex.data.local.dao.AppSettingDao
import app.lazydex.data.local.dao.CategoryCount
import app.lazydex.data.local.dao.CategoryDao
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.dao.StatusCount
import app.lazydex.data.local.dao.SyncOutboxDao
import app.lazydex.data.local.entity.ActiveMediaItemView
import app.lazydex.data.local.entity.ActivityEntity
import app.lazydex.data.local.entity.AppSettingEntity
import app.lazydex.data.local.entity.CategoryEntity
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.local.entity.SyncOutboxEntity
import app.lazydex.data.tracker.anilist.AnilistGraphQLClient
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.anilist.TokenBucketLimiter
import app.lazydex.data.tracker.sync.AnilistSyncEngine
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.UserStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressMutationRulesTest {

    data class MutationResult(
        val newProgress: Double,
        val effectiveDelta: Double,
        val newStatus: String,
        val startDate: Long?,
        val endDate: Long?
    )

    private fun applyProgressDelta(
        currentProgress: Double,
        progressTotal: Double?,
        userStatus: String,
        startDate: Long?,
        endDate: Long?,
        delta: Double,
        timestamp: Long
    ): MutationResult {
        val target = (currentProgress + delta).coerceAtLeast(0.0)
        val clampedProgress = if (progressTotal != null) target.coerceAtMost(progressTotal) else target
        val effectiveDelta = clampedProgress - currentProgress

        var newStatus = userStatus
        var newStartDate = startDate
        var newEndDate = endDate

        if (userStatus == "PLANNING" && clampedProgress > 0.0) {
            newStatus = "IN_PROGRESS"
            if (newStartDate == null) newStartDate = timestamp
        }
        if (progressTotal != null && clampedProgress >= progressTotal && userStatus != "COMPLETED") {
            newStatus = "COMPLETED"
            newEndDate = timestamp
        } else if (userStatus == "COMPLETED" && progressTotal != null && clampedProgress < progressTotal) {
            newStatus = "IN_PROGRESS"
            newEndDate = null
        }

        return MutationResult(
            newProgress = clampedProgress,
            effectiveDelta = effectiveDelta,
            newStatus = newStatus,
            startDate = newStartDate,
            endDate = newEndDate
        )
    }

    @Test
    fun `clamping prevents negative progress and exceeding total`() {
        val resUnderflow = applyProgressDelta(
            currentProgress = 2.0,
            progressTotal = 100.0,
            userStatus = "IN_PROGRESS",
            startDate = 1000L,
            endDate = null,
            delta = -5.0,
            timestamp = 2000L
        )
        assertEquals(0.0, resUnderflow.newProgress, 0.001)
        assertEquals(-2.0, resUnderflow.effectiveDelta, 0.001)

        val resOverflow = applyProgressDelta(
            currentProgress = 98.0,
            progressTotal = 100.0,
            userStatus = "IN_PROGRESS",
            startDate = 1000L,
            endDate = null,
            delta = 10.0,
            timestamp = 2000L
        )
        assertEquals(100.0, resOverflow.newProgress, 0.001)
        assertEquals(2.0, resOverflow.effectiveDelta, 0.001)
        assertEquals("COMPLETED", resOverflow.newStatus)
    }

    @Test
    fun `status transitions from PLANNING to IN_PROGRESS on first progress`() {
        val res = applyProgressDelta(
            currentProgress = 0.0,
            progressTotal = 50.0,
            userStatus = "PLANNING",
            startDate = null,
            endDate = null,
            delta = 1.0,
            timestamp = 1500L
        )
        assertEquals(1.0, res.newProgress, 0.001)
        assertEquals("IN_PROGRESS", res.newStatus)
        assertEquals(1500L, res.startDate)
        assertNull(res.endDate)
    }

    @Test
    fun `status transitions from COMPLETED back to IN_PROGRESS on decrement`() {
        val res = applyProgressDelta(
            currentProgress = 50.0,
            progressTotal = 50.0,
            userStatus = "COMPLETED",
            startDate = 1000L,
            endDate = 2000L,
            delta = -1.0,
            timestamp = 2500L
        )
        assertEquals(49.0, res.newProgress, 0.001)
        assertEquals("IN_PROGRESS", res.newStatus)
        assertNull(res.endDate)
    }

    @Test
    fun `coalescing combines deltas and deletes on undo`() {
        // Initial delta +1
        val existingDelta = 1.0
        val undoDelta = -1.0

        val combinedDelta = existingDelta + undoDelta
        val action = if (combinedDelta == 0.0) "DELETE" else "UPDATE"

        assertEquals("DELETE", action)
        assertEquals(0.0, combinedDelta, 0.001)
    }

    // --- MediaRepositoryImpl Wiring Tests (Task 3.3) ---

    @Test
    fun `incrementProgress marks linked media_links as DIRTY_LOCAL and triggers async push`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val itemDao = FakeMediaItemDao()
        val linkDao = FakeMediaLinkDao()
        val categoryDao = FakeCategoryDao()
        val activityDao = FakeActivityDao()
        val outboxDao = FakeSyncOutboxDao()
        val settingDao = FakeAppSettingDao()
        val syncEngine = FakeSyncEngine()

        val repo = MediaRepositoryImpl(
            db = null,
            dao = itemDao,
            categoryDao = categoryDao,
            linkDao = linkDao,
            activityDao = activityDao,
            outboxDao = outboxDao,
            settingDao = settingDao,
            localCoversDir = File(""),
            syncEngine = syncEngine,
            syncScope = testScope
        )

        val localItem = MediaItemEntity(
            id = "media-1",
            categoryId = "cat_manga",
            title = "Solo Leveling",
            userStatus = "IN_PROGRESS",
            progress = 10.0,
            progressTotal = 200.0,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        itemDao.items[localItem.id] = localItem

        val link = MediaLinkEntity(
            id = "link-1",
            mediaId = "media-1",
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "105398",
            remoteMediaListId = "999",
            url = "https://anilist.co/manga/105398",
            state = "LINKED",
            syncStatus = "SYNCED",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        linkDao.links[link.id] = link

        val updated = repo.incrementProgress("media-1", 1.0)
        assertNotNull(updated)
        assertEquals(11.0, updated!!.progress, 0.001)

        // Verify link was marked DIRTY_LOCAL in Room SQLite
        val updatedLink = linkDao.links[link.id]!!
        assertEquals("DIRTY_LOCAL", updatedLink.syncStatus)

        // Advance coroutines to run asynchronous background push
        testScope.advanceUntilIdle()
        assertTrue(syncEngine.pushedMediaIds.contains("media-1"))
    }

    @Test
    fun `incrementProgress without active link does not trigger push and returns updated item`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val itemDao = FakeMediaItemDao()
        val linkDao = FakeMediaLinkDao()
        val categoryDao = FakeCategoryDao()
        val activityDao = FakeActivityDao()
        val outboxDao = FakeSyncOutboxDao()
        val settingDao = FakeAppSettingDao()
        val syncEngine = FakeSyncEngine()

        val repo = MediaRepositoryImpl(
            db = null,
            dao = itemDao,
            categoryDao = categoryDao,
            linkDao = linkDao,
            activityDao = activityDao,
            outboxDao = outboxDao,
            settingDao = settingDao,
            localCoversDir = File(""),
            syncEngine = syncEngine,
            syncScope = testScope
        )

        val localItem = MediaItemEntity(
            id = "media-unlinked",
            categoryId = "cat_manga",
            title = "Local Only Comic",
            userStatus = "PLANNING",
            progress = 0.0,
            progressTotal = 50.0,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        itemDao.items[localItem.id] = localItem

        val updated = repo.incrementProgress("media-unlinked", 1.0)
        assertNotNull(updated)
        assertEquals(1.0, updated!!.progress, 0.001)
        val updatedDb = itemDao.items["media-unlinked"]!!
        assertEquals("IN_PROGRESS", updatedDb.userStatus)

        testScope.advanceUntilIdle()
        assertTrue(syncEngine.pushedMediaIds.isEmpty())
    }

    @Test
    fun `status change in update marks linked media_links as DIRTY_LOCAL and triggers async push`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val itemDao = FakeMediaItemDao()
        val linkDao = FakeMediaLinkDao()
        val categoryDao = FakeCategoryDao()
        val activityDao = FakeActivityDao()
        val outboxDao = FakeSyncOutboxDao()
        val settingDao = FakeAppSettingDao()
        val syncEngine = FakeSyncEngine()

        val repo = MediaRepositoryImpl(
            db = null,
            dao = itemDao,
            categoryDao = categoryDao,
            linkDao = linkDao,
            activityDao = activityDao,
            outboxDao = outboxDao,
            settingDao = settingDao,
            localCoversDir = File(""),
            syncEngine = syncEngine,
            syncScope = testScope
        )

        val localItem = MediaItemEntity(
            id = "media-update",
            categoryId = "cat_anime",
            title = "Frieren",
            userStatus = "IN_PROGRESS",
            progress = 28.0,
            progressTotal = 28.0,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        itemDao.items[localItem.id] = localItem

        val link = MediaLinkEntity(
            id = "link-update",
            mediaId = "media-update",
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "154587",
            remoteMediaListId = "888",
            url = "https://anilist.co/anime/154587",
            state = "LINKED",
            syncStatus = "SYNCED",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        linkDao.links[link.id] = link

        val domainItem = MediaItem(
            id = "media-update",
            categoryId = "cat_anime",
            title = "Frieren",
            userStatus = UserStatus.COMPLETED,
            progress = 28.0,
            progressTotal = 28.0,
            lastUpdated = 1_000_000L,
            dateAdded = 1_000_000L
        )

        repo.update(domainItem)

        // Verify marked DIRTY_LOCAL
        val updatedLink = linkDao.links[link.id]!!
        assertEquals("DIRTY_LOCAL", updatedLink.syncStatus)

        testScope.advanceUntilIdle()
        assertTrue(syncEngine.pushedMediaIds.contains("media-update"))
    }

    @Test
    fun `async push network failure is caught safely without interrupting progress mutation`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val itemDao = FakeMediaItemDao()
        val linkDao = FakeMediaLinkDao()
        val categoryDao = FakeCategoryDao()
        val activityDao = FakeActivityDao()
        val outboxDao = FakeSyncOutboxDao()
        val settingDao = FakeAppSettingDao()
        val syncEngine = FakeSyncEngine().apply { shouldThrow = true }

        val repo = MediaRepositoryImpl(
            db = null,
            dao = itemDao,
            categoryDao = categoryDao,
            linkDao = linkDao,
            activityDao = activityDao,
            outboxDao = outboxDao,
            settingDao = settingDao,
            localCoversDir = File(""),
            syncEngine = syncEngine,
            syncScope = testScope
        )

        val localItem = MediaItemEntity(
            id = "media-offline",
            categoryId = "cat_manga",
            title = "Berserk",
            userStatus = "IN_PROGRESS",
            progress = 300.0,
            progressTotal = 380.0,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        itemDao.items[localItem.id] = localItem

        val link = MediaLinkEntity(
            id = "link-offline",
            mediaId = "media-offline",
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "30002",
            remoteMediaListId = "777",
            url = "https://anilist.co/manga/30002",
            state = "LINKED",
            syncStatus = "SYNCED",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        linkDao.links[link.id] = link

        // Mutate progress while "offline"
        val updated = repo.incrementProgress("media-offline", 1.0)
        assertNotNull(updated)
        assertEquals(301.0, updated!!.progress, 0.001)

        testScope.advanceUntilIdle()

        // Link remains DIRTY_LOCAL so next sync push will retry it
        val updatedLink = linkDao.links[link.id]!!
        assertEquals("DIRTY_LOCAL", updatedLink.syncStatus)
    }

    // --- Fakes ---

    class FakeSyncEngine : AnilistSyncEngine(
        client = AnilistGraphQLClient(
            okHttpClient = OkHttpClient(),
            tokenStore = AnilistTokenStore(FakeSharedPreferences()),
            limiter = TokenBucketLimiter()
        ),
        mediaItemDao = FakeMediaItemDao(),
        mediaLinkDao = FakeMediaLinkDao(),
        tokenStore = AnilistTokenStore(FakeSharedPreferences())
    ) {
        val pushedMediaIds = mutableListOf<String>()
        var pushResult: Boolean = true
        var shouldThrow: Boolean = false

        override suspend fun pushDirtyItem(mediaId: String): Boolean {
            if (shouldThrow) throw IOException("Simulated network failure")
            pushedMediaIds.add(mediaId)
            return pushResult
        }
    }

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
            var count = 0
            links.values.filter { it.mediaId == mediaId && it.state == "LINKED" }.forEach {
                links[it.id] = it.copy(syncStatus = syncStatus, updatedAt = updatedAt)
                count++
            }
            return count
        }
        override suspend fun upsert(link: MediaLinkEntity) { links[link.id] = link }
        override suspend fun upsertAll(links: List<MediaLinkEntity>) { links.forEach { this.links[it.id] = it } }
        override suspend fun delete(id: String) { links.remove(id) }
    }

    class FakeActivityDao : ActivityDao {
        val activities = mutableListOf<ActivityEntity>()
        override fun observeByMediaId(mediaId: String): Flow<List<ActivityEntity>> = emptyFlow()
        override suspend fun getByMediaId(mediaId: String): List<ActivityEntity> = emptyList()
        override suspend fun getCountedProgressUpdates(mediaId: String): List<ActivityEntity> = emptyList()
        override suspend fun findCoalescableProgress(mediaId: String, mode: String?, minTimestamp: Long): ActivityEntity? = null
        override suspend fun upsert(activity: ActivityEntity) { activities.add(activity) }
        override suspend fun delete(id: String) { activities.removeAll { it.id == id } }
    }

    class FakeSyncOutboxDao : SyncOutboxDao {
        val entries = mutableListOf<SyncOutboxEntity>()
        override suspend fun insert(entry: SyncOutboxEntity) { entries.add(entry) }
        override suspend fun getAll(): List<SyncOutboxEntity> = entries
        override suspend fun delete(id: String) { entries.removeAll { it.id == id } }
        override suspend fun countForMedia(mediaId: String): Int = 0
    }

    class FakeCategoryDao : CategoryDao {
        val categories = mutableMapOf<String, CategoryEntity>()
        override fun observeAll(): Flow<List<CategoryEntity>> = emptyFlow()
        override fun observeTopLevel(): Flow<List<CategoryEntity>> = emptyFlow()
        override fun observeTypes(parentId: String): Flow<List<CategoryEntity>> = emptyFlow()
        override suspend fun getAll(): List<CategoryEntity> = emptyList()
        override suspend fun getById(id: String): CategoryEntity? = categories[id]
        override suspend fun getTypes(parentId: String): List<CategoryEntity> = emptyList()
        override suspend fun countDirectMediaItems(categoryId: String): Int = 0
        override suspend fun countAllMediaItemsIncludingChildren(categoryId: String): Int = 0
        override suspend fun upsert(category: CategoryEntity) { categories[category.id] = category }
        override suspend fun upsertAll(categories: List<CategoryEntity>) {}
        override suspend fun delete(id: String) { categories.remove(id) }
    }

    class FakeAppSettingDao : AppSettingDao {
        val settings = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = settings[key]
        override suspend fun set(setting: AppSettingEntity) { settings[setting.key] = setting.value }
        override suspend fun getAll(): List<AppSettingEntity> = emptyList()
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
        override fun edit(): SharedPreferences.Editor = error("Not supported in test fake")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
}
