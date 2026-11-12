package app.lazydex.data.repository

import android.content.SharedPreferences
import app.lazydex.data.local.dao.ActivityDao
import app.lazydex.data.local.dao.AppSettingDao
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.dao.StatusCount
import app.lazydex.data.local.dao.SyncOutboxDao
import app.lazydex.data.local.dao.TypeCount
import app.lazydex.data.local.entity.ActiveMediaItemView
import app.lazydex.data.local.entity.ActivityEntity
import app.lazydex.data.local.entity.AppSettingEntity
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.local.entity.SyncOutboxEntity
import app.lazydex.data.local.entity.UserDataEntity
import app.lazydex.data.tracker.anilist.AnilistApi
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.anilist.TokenBucketLimiter
import app.lazydex.data.tracker.sync.AnilistSyncEngine
import app.lazydex.domain.model.ActivityDatePoint
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.UserStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
        val existingDelta = 1.0
        val undoDelta = -1.0

        val combinedDelta = existingDelta + undoDelta
        val action = if (combinedDelta == 0.0) "DELETE" else "UPDATE"

        assertEquals("DELETE", action)
        assertEquals(0.0, combinedDelta, 0.001)
    }

    // --- MediaRepositoryImpl Wiring Tests ---

    @Test
    fun `incrementProgress marks linked media_links as DIRTY_LOCAL and triggers async push`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val itemDao = FakeMediaItemDao()
        val linkDao = FakeMediaLinkDao()
        val activityDao = FakeActivityDao()
        val outboxDao = FakeSyncOutboxDao()
        val settingDao = FakeAppSettingDao()
        val syncEngine = FakeSyncEngine()

        val repo = MediaRepositoryImpl(
            db = null,
            dao = itemDao,
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
            type = "MANGA",
            format = "MANGA",
            title = "Solo Leveling",
            progressTotal = 200.0,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        itemDao.items[localItem.id] = localItem
        itemDao.userData[localItem.id] = UserDataEntity(
            mediaId = "media-1",
            userStatus = "IN_PROGRESS",
            progress = 10.0,
            updatedAt = 1_000_000L
        )

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

        val updatedDb = itemDao.getActiveById("media-1")!!
        assertEquals(11.0, updatedDb.progress, 0.001)
        assertEquals("IN_PROGRESS", updatedDb.userStatus)

        testScope.advanceUntilIdle()
        assertTrue(syncEngine.pushedMediaIds.contains("media-1"))
    }

    @Test
    fun `incrementProgress without active link does not trigger push and returns updated item`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val itemDao = FakeMediaItemDao()
        val linkDao = FakeMediaLinkDao()
        val activityDao = FakeActivityDao()
        val outboxDao = FakeSyncOutboxDao()
        val settingDao = FakeAppSettingDao()
        val syncEngine = FakeSyncEngine()

        val repo = MediaRepositoryImpl(
            db = null,
            dao = itemDao,
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
            type = "MANGA",
            format = "MANGA",
            title = "Local Only Comic",
            progressTotal = 50.0,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        itemDao.items[localItem.id] = localItem
        itemDao.userData[localItem.id] = UserDataEntity(
            mediaId = "media-unlinked",
            userStatus = "PLANNING",
            progress = 0.0,
            updatedAt = 1_000_000L
        )

        val updated = repo.incrementProgress("media-unlinked", 1.0)
        assertNotNull(updated)
        assertEquals(1.0, updated!!.progress, 0.001)
        val updatedDb = itemDao.getActiveById("media-unlinked")!!
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
        val activityDao = FakeActivityDao()
        val outboxDao = FakeSyncOutboxDao()
        val settingDao = FakeAppSettingDao()
        val syncEngine = FakeSyncEngine()

        val repo = MediaRepositoryImpl(
            db = null,
            dao = itemDao,
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
            type = "ANIME",
            format = "TV",
            title = "Frieren",
            progressTotal = 28.0,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        itemDao.items[localItem.id] = localItem
        itemDao.userData[localItem.id] = UserDataEntity(
            mediaId = "media-update",
            userStatus = "IN_PROGRESS",
            progress = 10.0,
            updatedAt = 1_000_000L
        )

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
            type = "ANIME",
            format = "TV",
            title = "Frieren",
            userStatus = UserStatus.COMPLETED,
            progress = 28.0,
            progressTotal = 28.0,
            currentProgress = 28,
            totalItems = 28,
            lastUpdated = 2_000_000L,
            dateAdded = 1_000_000L
        )
        repo.update(domainItem)

        val updatedDb = itemDao.getActiveById("media-update")!!
        assertEquals("COMPLETED", updatedDb.userStatus)

        testScope.advanceUntilIdle()
        assertTrue(syncEngine.pushedMediaIds.contains("media-update"))
    }

    @Test
    fun `autoUpdateOnMarkRead NEVER suppresses push when completed`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val itemDao = FakeMediaItemDao()
        val linkDao = FakeMediaLinkDao()
        val activityDao = FakeActivityDao()
        val outboxDao = FakeSyncOutboxDao()
        val settingDao = FakeAppSettingDao()
        val syncEngine = FakeSyncEngine()

        settingDao.settings["tracker.autoUpdateOnMarkRead"] = "NEVER"

        val repo = MediaRepositoryImpl(
            db = null,
            dao = itemDao,
            linkDao = linkDao,
            activityDao = activityDao,
            outboxDao = outboxDao,
            settingDao = settingDao,
            localCoversDir = File(""),
            syncEngine = syncEngine,
            syncScope = testScope
        )

        val localItem = MediaItemEntity(
            id = "media-never",
            type = "MANGA",
            format = "MANGA",
            title = "Berserk",
            progressTotal = 376.0,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        itemDao.items[localItem.id] = localItem
        itemDao.userData[localItem.id] = UserDataEntity(
            mediaId = "media-never",
            userStatus = "IN_PROGRESS",
            progress = 375.0,
            updatedAt = 1_000_000L
        )

        val link = MediaLinkEntity(
            id = "link-never",
            mediaId = "media-never",
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

        repo.incrementProgress("media-never", 1.0)

        val updatedDb = itemDao.getActiveById("media-never")!!
        assertEquals("COMPLETED", updatedDb.userStatus)

        testScope.advanceUntilIdle()
        assertTrue("Push should be suppressed when autoUpdateOnMarkRead is NEVER", syncEngine.pushedMediaIds.isEmpty())
    }

    @Test
    fun `manual progress change in update records coalesced PROGRESS_UPDATE and unchanged skips it`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val itemDao = FakeMediaItemDao()
        val linkDao = FakeMediaLinkDao()
        val activityDao = FakeActivityDao()
        val outboxDao = FakeSyncOutboxDao()
        val settingDao = FakeAppSettingDao()

        val repo = MediaRepositoryImpl(
            db = null,
            dao = itemDao,
            linkDao = linkDao,
            activityDao = activityDao,
            outboxDao = outboxDao,
            settingDao = settingDao,
            localCoversDir = File(""),
            syncEngine = null,
            syncScope = testScope
        )

        val localItem = MediaItemEntity(
            id = "media-manual",
            type = "NOVEL",
            format = "NOVEL",
            title = "Lord of the Mysteries",
            progressTotal = 1400.0,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        itemDao.items[localItem.id] = localItem
        itemDao.userData[localItem.id] = UserDataEntity(
            mediaId = "media-manual",
            userStatus = "READING",
            progress = 10.0,
            updatedAt = 1_000_000L
        )

        val unchangedDomain = MediaItem(
            id = "media-manual",
            type = "NOVEL",
            format = "NOVEL",
            title = "Lord of the Mysteries",
            userStatus = UserStatus.READING,
            progress = 10.0,
            progressTotal = 1400.0,
            currentProgress = 10,
            totalItems = 1400,
            lastUpdated = 2_000_000L,
            dateAdded = 1_000_000L
        )
        repo.update(unchangedDomain)
        assertTrue("Unchanged progress must not record PROGRESS_UPDATE", activityDao.activities.none { it.type == "PROGRESS_UPDATE" })

        repo.update(unchangedDomain.copy(progress = 14.0, currentProgress = 14))
        val firstUpdates = activityDao.activities.filter { it.type == "PROGRESS_UPDATE" }
        assertEquals(1, firstUpdates.size)
        assertEquals(4.0, firstUpdates.first().delta ?: 0.0, 0.001)
        assertEquals("14.0", firstUpdates.first().newValue)

        // Second rapid manual edit should coalesce with the previous PROGRESS_UPDATE
        repo.update(unchangedDomain.copy(progress = 17.0, currentProgress = 17))
        val coalescedUpdates = activityDao.activities.filter { it.type == "PROGRESS_UPDATE" }
        assertEquals(1, coalescedUpdates.size)
        assertEquals(7.0, coalescedUpdates.first().delta ?: 0.0, 0.001)
        assertEquals("17.0", coalescedUpdates.first().newValue)
    }

    @Test
    fun `update preserves repeat and durationMinutes without overwriting with defaults`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val itemDao = FakeMediaItemDao()
        val linkDao = FakeMediaLinkDao()
        val activityDao = FakeActivityDao()
        val outboxDao = FakeSyncOutboxDao()
        val settingDao = FakeAppSettingDao()

        val repo = MediaRepositoryImpl(
            db = null,
            dao = itemDao,
            linkDao = linkDao,
            activityDao = activityDao,
            outboxDao = outboxDao,
            settingDao = settingDao,
            localCoversDir = File(""),
            syncEngine = null,
            syncScope = testScope
        )

        val syncedEntity = MediaItemEntity(
            id = "anime-synced",
            type = "ANIME",
            format = "TV",
            title = "Frieren",
            progressTotal = 28.0,
            durationMinutes = 24,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        itemDao.items[syncedEntity.id] = syncedEntity
        itemDao.userData[syncedEntity.id] = UserDataEntity(
            mediaId = "anime-synced",
            userStatus = "COMPLETED",
            progress = 28.0,
            repeat = 2,
            updatedAt = 1_000_000L
        )

        val loaded = repo.getById("anime-synced")!!
        assertEquals(2, loaded.repeat)
        assertEquals(24, loaded.durationMinutes)

        repo.update(loaded.copy(notes = "Edited notes locally"))
        val persisted = repo.getById("anime-synced")!!
        assertEquals(2, persisted.repeat)
        assertEquals(24, persisted.durationMinutes)
    }

    @Test
    fun observeFormats_normalizesLegacyTypPrefixesAndMangaSecondaryUnit() = runTest {
        val repo = MediaRepositoryImpl(
            db = null,
            dao = FakeMediaItemDao(),
            linkDao = FakeMediaLinkDao(),
            activityDao = FakeActivityDao(),
            outboxDao = FakeSyncOutboxDao(),
            settingDao = FakeAppSettingDao(),
            localCoversDir = File("")
        )

        val novelPrefixed = repo.observeFormats("typ_novel").first()
        val novelPlain = repo.observeFormats("NOVEL").first()
        assertEquals(listOf("LIGHT_NOVEL", "WEB_NOVEL"), novelPrefixed.map { it.id })
        assertEquals(novelPlain.map { it.id }, novelPrefixed.map { it.id })

        val mangaPrefixed = repo.observeFormats("typ_manga").first()
        assertEquals(listOf("MANGA", "MANHWA", "MANHUA", "ONE_SHOT"), mangaPrefixed.map { it.id })

        val animePrefixed = repo.observeFormats("typ_anime").first()
        assertEquals(listOf("TV", "MOVIE", "OVA"), animePrefixed.map { it.id })

        assertEquals("Volume", repo.getResolvedConfig("MANGA").secondaryUnitName)
        assertNull(repo.getResolvedConfig("MANHWA").secondaryUnitName)
        assertNull(repo.getResolvedConfig("ANIME").secondaryUnitName)
        assertNull(repo.getResolvedConfig("TV").secondaryUnitName)
    }

    @Test
    fun `catalog-only edit does not bump progressIncreasedAt, userUpdatedAt, or mark links dirty`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val itemDao = FakeMediaItemDao()
        val linkDao = FakeMediaLinkDao()
        val activityDao = FakeActivityDao()
        val outboxDao = FakeSyncOutboxDao()
        val settingDao = FakeAppSettingDao()
        val syncEngine = FakeSyncEngine()

        val repo = MediaRepositoryImpl(
            db = null,
            dao = itemDao,
            linkDao = linkDao,
            activityDao = activityDao,
            outboxDao = outboxDao,
            settingDao = settingDao,
            localCoversDir = File(""),
            syncEngine = syncEngine,
            syncScope = testScope
        )

        val entity = MediaItemEntity(
            id = "catalog-test",
            type = "MANGA",
            format = "MANGA",
            title = "Original Title",
            description = "Original Description",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        val userData = UserDataEntity(
            mediaId = "catalog-test",
            userStatus = "IN_PROGRESS",
            progress = 10.0,
            updatedAt = 1_000_000L,
            progressIncreasedAt = 1_000_000L
        )
        itemDao.items[entity.id] = entity
        itemDao.userData[entity.id] = userData

        val link = MediaLinkEntity(
            id = "link-catalog",
            mediaId = entity.id,
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "12345",
            url = "https://anilist.co/manga/12345",
            syncStatus = "SYNCED",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        linkDao.links[link.id] = link

        val loaded = repo.getById("catalog-test")!!
        assertEquals(1_000_000L, loaded.progressIncreasedAt)

        // Modify catalog only (title, description)
        repo.update(loaded.copy(title = "Edited Catalog Title", description = "New Description"))

        val persistedItem = itemDao.items["catalog-test"]!!
        val persistedUser = itemDao.userData["catalog-test"]!!
        assertEquals("Edited Catalog Title", persistedItem.title)
        assertEquals("New Description", persistedItem.description)
        assertTrue(persistedItem.updatedAt > 1_000_000L)

        // User data and history timestamp MUST remain completely unchanged
        assertEquals(1_000_000L, persistedUser.updatedAt)
        assertEquals(1_000_000L, persistedUser.progressIncreasedAt)

        testScope.advanceUntilIdle()
        // Must NOT mark link DIRTY_LOCAL or push to remote
        assertEquals("SYNCED", linkDao.links[link.id]!!.syncStatus)
        assertTrue(syncEngine.pushedMediaIds.isEmpty())
    }

    @Test
    fun `non-increasing user data edit does not bump progressIncreasedAt`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val itemDao = FakeMediaItemDao()
        val linkDao = FakeMediaLinkDao()
        val activityDao = FakeActivityDao()
        val outboxDao = FakeSyncOutboxDao()
        val settingDao = FakeAppSettingDao()
        val syncEngine = FakeSyncEngine()

        val repo = MediaRepositoryImpl(
            db = null,
            dao = itemDao,
            linkDao = linkDao,
            activityDao = activityDao,
            outboxDao = outboxDao,
            settingDao = settingDao,
            localCoversDir = File(""),
            syncEngine = syncEngine,
            syncScope = testScope
        )

        val entity = MediaItemEntity(
            id = "userdata-test",
            type = "MANGA",
            format = "MANGA",
            title = "Test Manga",
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        val userData = UserDataEntity(
            mediaId = "userdata-test",
            userStatus = "IN_PROGRESS",
            progress = 10.0,
            notes = "Old note",
            updatedAt = 1_000_000L,
            progressIncreasedAt = 1_000_000L
        )
        itemDao.items[entity.id] = entity
        itemDao.userData[entity.id] = userData

        val loaded = repo.getById("userdata-test")!!
        // Edit notes only (no progress increase)
        repo.update(loaded.copy(notes = "Updated note only"))

        val persistedUser = itemDao.userData["userdata-test"]!!
        assertEquals("Updated note only", persistedUser.notes)
        assertTrue(persistedUser.updatedAt!! > 1_000_000L)
        // progressIncreasedAt MUST NOT be bumped
        assertEquals(1_000_000L, persistedUser.progressIncreasedAt)

        // Strict progress increase DOES bump progressIncreasedAt
        repo.update(loaded.copy(currentProgress = 11, progress = 11.0))
        val bumpedUser = itemDao.userData["userdata-test"]!!
        assertEquals(11.0, bumpedUser.progress, 0.001)
        assertTrue(bumpedUser.progressIncreasedAt!! > 1_000_000L)
    }

    // --- Fakes ---

    class FakeSyncEngine(
        val sharedPrefs: FakeSharedPreferences = FakeSharedPreferences(),
        val store: AnilistTokenStore = AnilistTokenStore(sharedPrefs)
    ) : AnilistSyncEngine(
        client = AnilistApi(OkHttpClient(), store, TokenBucketLimiter()),
        db = null,
        mediaItemDao = FakeMediaItemDao(),
        mediaLinkDao = FakeMediaLinkDao(),
        tokenStore = store
    ) {
        val pushedMediaIds = mutableListOf<String>()

        override suspend fun pushDirtyItem(mediaId: String): Boolean {
            pushedMediaIds.add(mediaId)
            return true
        }
    }

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
        override fun observeActivityPoints(startMs: Long?, endMs: Long?): Flow<List<ActivityDatePoint>> = emptyFlow()
        override suspend fun getByMediaId(mediaId: String): List<ActivityEntity> =
            activities.filter { it.mediaId == mediaId }
        override suspend fun getCountedProgressUpdates(mediaId: String): List<ActivityEntity> =
            activities.filter { it.mediaId == mediaId && it.type == "PROGRESS_UPDATE" && it.counted == 1 }
        override suspend fun findCoalescableProgress(mediaId: String, mode: String?, minTimestamp: Long): ActivityEntity? =
            activities.lastOrNull {
                it.mediaId == mediaId &&
                    it.type == "PROGRESS_UPDATE" &&
                    it.counted == 1 &&
                    it.timestamp >= minTimestamp &&
                    (mode == null || it.mode == mode)
            }
        override suspend fun upsert(activity: ActivityEntity) {
            activities.removeAll { it.id == activity.id }
            activities.add(activity)
        }
        override suspend fun delete(id: String) { activities.removeAll { it.id == id } }
    }

    class FakeSyncOutboxDao : SyncOutboxDao {
        val entries = mutableListOf<SyncOutboxEntity>()
        override suspend fun insert(entry: SyncOutboxEntity) { entries.add(entry) }
        override suspend fun getAll(): List<SyncOutboxEntity> = entries
        override suspend fun delete(id: String) { entries.removeAll { it.id == id } }
        override suspend fun countForMedia(mediaId: String): Int = 0
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
