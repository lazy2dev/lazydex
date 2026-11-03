package app.lazydex.ui.details

import androidx.lifecycle.SavedStateHandle
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.repository.ProgressMutationRulesTest
import app.lazydex.data.tracker.anilist.AnilistApi
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.anilist.TokenBucketLimiter
import app.lazydex.data.tracker.sync.AnilistSyncEngine
import app.lazydex.data.tracker.sync.AnilistSyncEngineTest
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.MetadataScraper
import app.lazydex.scraper.source.SourceRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MediaDetailsViewModelTest {

    private class FakeRepository : MediaRepository {
        val items = mutableListOf<MediaItem>()
        val itemFlows = mutableMapOf<String, kotlinx.coroutines.flow.MutableSharedFlow<MediaItem?>>()

        override fun observeAll(): Flow<List<MediaItem>> = flowOf(items)
        override fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>> = flowOf(items.filter { it.category == category })
        override fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>> = flowOf(items)
        override fun observeById(id: String): Flow<MediaItem?> {
            return itemFlows.getOrPut(id) {
                kotlinx.coroutines.flow.MutableSharedFlow<MediaItem?>(replay = 1).apply {
                    tryEmit(items.find { it.id == id })
                }
            }
        }
        override fun observeCount(): Flow<Int> = flowOf(items.size)
        override fun observeStats(): Flow<MediaStats> = flowOf(MediaStats(0, 0, 0, null, 0, 0, 0, 0, 0))
        override fun observeCategoryCounts(): Flow<Map<MediaCategory, Int>> = flowOf(emptyMap())
        override fun observeStatusCounts(category: MediaCategory?): Flow<Map<StatusFilter, Int>> = flowOf(emptyMap())
        override fun observeDistinctGenres(category: MediaCategory?): Flow<List<String>> = flowOf(emptyList())
        override fun observeDistinctTags(category: MediaCategory?): Flow<List<String>> = flowOf(emptyList())

        override suspend fun getById(id: String): MediaItem? = items.find { it.id == id }
        override suspend fun getAll(): List<MediaItem> = items
        override suspend fun add(item: MediaItem): MediaItem {
            val toAdd = item.copy(id = "gen-id-123")
            items.add(toAdd)
            itemFlows[toAdd.id]?.emit(toAdd)
            return toAdd
        }
        override suspend fun update(item: MediaItem) {
            val idx = items.indexOfFirst { it.id == item.id }
            if (idx >= 0) items[idx] = item else items.add(item)
            itemFlows[item.id]?.emit(item)
        }
        override suspend fun delete(id: String) {
            items.removeIf { it.id == id }
            itemFlows[id]?.emit(null)
        }
        override suspend fun replaceAll(items: List<MediaItem>) {
            this.items.clear()
            this.items.addAll(items)
        }
    }

    private class FakeMediaLinkDao : MediaLinkDao {
        val links = mutableListOf<MediaLinkEntity>()
        override fun observeByMediaId(mediaId: String): Flow<List<MediaLinkEntity>> =
            flowOf(links.filter { it.mediaId == mediaId })
        override suspend fun getByMediaId(mediaId: String): List<MediaLinkEntity> =
            links.filter { it.mediaId == mediaId }
        override suspend fun getByRemoteId(providerId: String, remoteId: String): MediaLinkEntity? =
            links.find { it.providerId == providerId && it.remoteId == remoteId }
        override suspend fun getDirtyLinks(providerId: String): List<MediaLinkEntity> =
            links.filter { it.providerId == providerId && it.syncStatus == "DIRTY_LOCAL" }
        override suspend fun getByProviderAndState(providerId: String, state: String): List<MediaLinkEntity> =
            links.filter { it.providerId == providerId && it.state == state }
        override suspend fun getByProviderId(providerId: String): List<MediaLinkEntity> =
            links.filter { it.providerId == providerId }
        override suspend fun updateSyncStatusByMediaId(mediaId: String, syncStatus: String, updatedAt: Long): Int = 0
        override suspend fun upsert(link: MediaLinkEntity) {
            val idx = links.indexOfFirst { it.id == link.id }
            if (idx >= 0) links[idx] = link else links.add(link)
        }
        override suspend fun upsertAll(links: List<MediaLinkEntity>) {
            links.forEach { upsert(it) }
        }
        override suspend fun delete(id: String) {
            links.removeIf { it.id == id }
        }
    }

    private class TestSyncEngine : AnilistSyncEngine(
        client = AnilistApi(
            okHttpClient = OkHttpClient(),
            tokenStore = AnilistTokenStore(AnilistSyncEngineTest.FakeSharedPreferences()),
            limiter = TokenBucketLimiter(60, 60, 15)
        ),
        mediaItemDao = ProgressMutationRulesTest.FakeMediaItemDao(),
        mediaLinkDao = FakeMediaLinkDao(),
        tokenStore = AnilistTokenStore(AnilistSyncEngineTest.FakeSharedPreferences())
    ) {
        var pushedMediaId: String? = null
        var shouldSucceed: Boolean = true

        override suspend fun pushDirtyItem(mediaId: String): Boolean {
            pushedMediaId = mediaId
            return shouldSucceed
        }
    }

    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun newEntry_startsBlank_saveWithoutTitleSetsError() = runTest {
        val repo = FakeRepository()
        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        val state = vm.uiState.value
        assertTrue(state.isNew)
        assertTrue(state.isTitleBlank)
        assertFalse(state.canSave)

        // Attempt save without title
        vm.save()
        assertEquals("Title is required", vm.uiState.value.errorMsg)
        assertFalse(vm.uiState.value.isDone)

        // Enter title and save
        vm.updateTitle("Lord of the Mysteries")
        assertNull(vm.uiState.value.errorMsg)
        assertTrue(vm.uiState.value.canSave)

        vm.save()
        assertTrue(vm.uiState.value.isDone)
        assertEquals(1, repo.items.size)
        assertEquals("Lord of the Mysteries", repo.items[0].title)
    }

    @Test
    fun initialize_withItemId_loadsItem() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "existing-1",
            category = MediaCategory.NOVEL,
            title = "Omniscient Reader",
            currentProgress = 100,
            totalItems = 551,
            userStatus = UserStatus.READING,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.initialize("existing-1", null)
        val state = vm.uiState.value
        assertFalse(state.isNew)
        assertFalse(state.isLoading)
        assertEquals(100L, state.lastUpdated)
        assertEquals("Omniscient Reader", state.title)
        assertEquals("100", state.currentProgress)
        assertEquals("551", state.totalItems)
    }

    @Test
    fun initialize_withMissingItemId_clearsLoading() = runTest {
        val repo = FakeRepository()
        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.initialize("non-existent-id", null)
        val state = vm.uiState.value
        assertFalse(state.isNew)
        assertFalse(state.isLoading)
    }

    @Test
    fun save_withMetadataAndFormat_preservesRomajiNativeAndSecondaryProgress() = runTest {
        val repo = FakeRepository()
        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.updateTitle("The Beginning After the End")
        vm.updateTitleRomaji("The Beginning After the End")
        vm.updateTitleNative("끝이 아닌 시작")
        vm.updateCategory(MediaCategory.MANGA)
        vm.updateFormatId("typ_manhwa")
        vm.updateCurrentProgress("175")
        vm.updateTotalItems("200")
        vm.updateSecondaryProgress("3")
        vm.updateSecondaryProgressTotal("8")
        vm.updateBannerImage("https://example.com/tbate_banner.jpg")
        vm.addGenre("Action")
        vm.addGenre("Fantasy")
        vm.addTag("Magic")

        assertTrue(vm.uiState.value.hasSecondaryProgress)
        vm.save()

        assertTrue(vm.uiState.value.isDone)
        assertEquals(1, repo.items.size)
        val saved = repo.items[0]
        assertEquals("The Beginning After the End", saved.title)
        assertEquals("typ_manhwa", saved.categoryId)
        assertEquals("The Beginning After the End", saved.alternativeTitles.getOrNull(0))
        assertEquals("끝이 아닌 시작", saved.alternativeTitles.getOrNull(2))
        assertEquals(175.0, saved.progress, 0.001)
        assertEquals(200.0, saved.progressTotal!!, 0.001)
        assertEquals(3.0, saved.secondaryProgress, 0.001)
        assertEquals(8.0, saved.secondaryProgressTotal!!, 0.001)
        assertEquals("https://example.com/tbate_banner.jpg", saved.getExtra("banner_image"))
        assertTrue(saved.genres.contains("Action"))
        assertTrue(saved.tags.contains("Magic"))
    }

    @Test
    fun autoSave_existingItem_updatesDatabaseWithoutExiting() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "existing-2",
            category = MediaCategory.MANGA,
            title = "Solo Leveling",
            currentProgress = 50,
            totalItems = 200,
            userStatus = UserStatus.READING,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.initialize("existing-2", null)
        vm.updateCurrentProgress("55")
        vm.updateRating(4.5)
        vm.autoSave()

        assertFalse(vm.uiState.value.isDone) // Must stay on screen
        assertFalse(vm.uiState.value.isNew)
        val updated = repo.items.find { it.id == "existing-2" }
        assertEquals(55, updated?.currentProgress)
        assertEquals(4.5, updated?.rating)
    }

    @Test
    fun deleteItem_removesFromRepositoryAndSetsDone() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "existing-3",
            category = MediaCategory.ANIME,
            title = "Frieren",
            currentProgress = 28,
            totalItems = 28,
            userStatus = UserStatus.COMPLETED,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.initialize("existing-3", null)
        vm.deleteItem()

        assertTrue(vm.uiState.value.isDone)
        assertTrue(repo.items.isEmpty())
    }

    @Test
    fun checkBackPressAllowed_uninitializedExistingItem_allowsBackPressWithoutDiscardPrompt() = runTest {
        val repo = FakeRepository()
        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        // Initialize with missing id so originalItem is null and isNew is false
        vm.initialize("missing-id", null)
        val allowed = vm.checkBackPressAllowed()

        assertTrue(allowed)
        assertFalse(vm.uiState.value.showDiscardConfirm)
    }

    @Test
    fun checkBackPressAllowed_modifiedExistingItem_showsDiscardPrompt() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "existing-back",
            category = MediaCategory.NOVEL,
            title = "Original Title",
            currentProgress = 0,
            userStatus = UserStatus.PLAN_TO,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.initialize("existing-back", null)
        assertTrue(vm.checkBackPressAllowed()) // Unmodified -> allowed

        vm.updateTitle("Modified Title")
        assertFalse(vm.checkBackPressAllowed()) // Modified -> disallowed
        assertTrue(vm.uiState.value.showDiscardConfirm)
    }

    @Test
    fun updateNotes_debouncedAutoSave_persistsAfterDelay() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "existing-notes",
            category = MediaCategory.NOVEL,
            title = "Notes Test",
            currentProgress = 10,
            userStatus = UserStatus.READING,
            notes = "Initial",
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.initialize("existing-notes", null)
        vm.updateNotes("Updated debounced note")

        // Before debounce, repo still has initial
        assertEquals("Initial", repo.items.find { it.id == "existing-notes" }?.notes)

        // Advance debounce duration
        testScheduler.advanceTimeBy(600L)

        assertEquals("Updated debounced note", repo.items.find { it.id == "existing-notes" }?.notes)
    }

    @Test
    fun flushNotes_immediatelyPersistsPendingNotes() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "existing-flush",
            category = MediaCategory.NOVEL,
            title = "Flush Test",
            currentProgress = 10,
            userStatus = UserStatus.READING,
            notes = "Initial",
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.initialize("existing-flush", null)
        vm.updateNotes("Immediately flushed note")

        // Flush immediately without waiting 500ms
        vm.flushNotes()

        assertEquals("Immediately flushed note", repo.items.find { it.id == "existing-flush" }?.notes)
    }

    @Test
    fun scrapeUrl_onFailure_surfacesErrorMessage() = runTest {
        val repo = FakeRepository()
        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.scrapeUrl("https://example.com/notfound")
        testScheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isScraping)
        assertEquals("Failed to scrape metadata from URL: https://example.com/notfound", vm.uiState.value.errorMsg)
        assertEquals("Failed to scrape metadata from URL: https://example.com/notfound", vm.uiState.value.scrapeError)
    }

    @Test
    fun initialize_observesMediaLinkAndTokenStore() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "item-sync-1",
            category = MediaCategory.NOVEL,
            title = "Sync Test",
            currentProgress = 10,
            userStatus = UserStatus.READING,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val linkDao = FakeMediaLinkDao()
        linkDao.links.add(
            MediaLinkEntity(
                id = "link-1",
                mediaId = "item-sync-1",
                kind = "NOVEL",
                providerId = "anilist",
                remoteId = "12345",
                url = null,
                syncStatus = "SYNCED",
                lastSyncedAt = 999999L,
                createdAt = 100L,
                updatedAt = 100L
            )
        )

        val prefs = AnilistSyncEngineTest.FakeSharedPreferences()
        val tokenStore = AnilistTokenStore(prefs)
        tokenStore.accessToken = "mock_token"

        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File("."),
            mediaLinkDao = linkDao,
            tokenStore = tokenStore
        )

        vm.initialize("item-sync-1", null)
        val state = vm.uiState.value

        assertTrue(state.isAnilistLoggedIn)
        assertEquals(12345L, state.anilistMediaId)
        assertEquals("SYNCED", state.anilistSyncStatus)
        assertEquals(999999L, state.anilistLastSynced)
    }

    @Test
    fun syncWithAniList_callsPushDirtyItem() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "item-sync-2",
            category = MediaCategory.NOVEL,
            title = "Push Test",
            currentProgress = 10,
            userStatus = UserStatus.READING,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val syncEngine = TestSyncEngine()
        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File("."),
            syncEngine = syncEngine
        )

        vm.initialize("item-sync-2", null)
        vm.syncWithAniList()

        assertEquals("item-sync-2", syncEngine.pushedMediaId)
        assertFalse(vm.uiState.value.isSyncing)
        assertNull(vm.uiState.value.errorMsg)
    }

    @Test
    fun syncWithAniList_failure_surfacesErrorMessage() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "item-sync-3",
            category = MediaCategory.NOVEL,
            title = "Push Fail Test",
            currentProgress = 10,
            userStatus = UserStatus.READING,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val syncEngine = TestSyncEngine()
        syncEngine.shouldSucceed = false

        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File("."),
            syncEngine = syncEngine
        )

        vm.initialize("item-sync-3", null)
        vm.syncWithAniList()

        assertEquals("item-sync-3", syncEngine.pushedMediaId)
        assertFalse(vm.uiState.value.isSyncing)
        assertEquals("AniList sync failed.", vm.uiState.value.errorMsg)
    }

    @Test
    fun liveDatabaseUpdate_updatesUiStateWithoutDeadlock() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "live-item-1",
            category = MediaCategory.NOVEL,
            title = "Live Test",
            currentProgress = 10,
            userStatus = UserStatus.READING,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.initialize("live-item-1", null)
        assertEquals("10", vm.uiState.value.currentProgress)

        // Simulate database update while screen is open
        repo.update(existing.copy(currentProgress = 15))
        testScheduler.advanceUntilIdle()

        // UiState should reflect new progress from database without being deadlocked
        assertEquals("15", vm.uiState.value.currentProgress)
    }

    @Test
    fun linkAniListId_whenItemIsNew_setsError() = runTest {
        val repo = FakeRepository()
        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        // Item is new (not in library)
        vm.linkAniListId(12345L)
        assertEquals("Add media to library before linking tracker", vm.uiState.value.errorMsg)
    }

    @Test
    fun updateArtwork_setsArtworkUrlAndBanner() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "art-item-1",
            category = MediaCategory.MANGA,
            title = "Artwork Test",
            currentProgress = 1,
            userStatus = UserStatus.READING,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.initialize("art-item-1", null)
        vm.updateArtwork("https://example.com/cover.jpg", "https://example.com/banner.jpg")

        assertEquals("https://example.com/cover.jpg", vm.uiState.value.coverImageUrl)
        assertEquals("https://example.com/banner.jpg", vm.uiState.value.bannerImage)
    }

    @Test
    fun acceptTrackerSuggestion_updatesStateToLinkedAndAdvancesProgress() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "item-sugg-1",
            title = "Frieren",
            category = MediaCategory.MANGA,
            currentProgress = 10,
            totalItems = 100,
            lastUpdated = 1000L,
            dateAdded = 1000L,
            userStatus = UserStatus.READING,
            rating = null
        )
        repo.items.add(existing)

        val linkDao = FakeMediaLinkDao()
        val suggestedLink = MediaLinkEntity(
            id = "link-sugg-1",
            mediaId = "item-sugg-1",
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "140101",
            remoteMediaListId = "999",
            url = "https://anilist.co/manga/140101",
            state = "SUGGESTED",
            syncStatus = "SYNCED",
            lastSyncedAt = null,
            syncedSnapshot = """{"progress": 25.0, "status": "IN_PROGRESS"}""",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        linkDao.links.add(suggestedLink)

        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File("."),
            mediaLinkDao = linkDao
        )

        vm.initialize("item-sugg-1", null)
        testScheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.suggestedTracker)
        assertEquals("link-sugg-1", vm.uiState.value.suggestedTracker?.linkId)
        assertEquals(25.0, vm.uiState.value.suggestedTracker?.progress)

        vm.acceptTrackerSuggestion()
        testScheduler.advanceUntilIdle()

        // Verify link is now LINKED and marked DIRTY_LOCAL
        val updatedLink = linkDao.links.find { it.id == "link-sugg-1" }
        assertNotNull(updatedLink)
        assertEquals("LINKED", updatedLink?.state)
        assertEquals("DIRTY_LOCAL", updatedLink?.syncStatus)

        // Progress was fast-forwarded to remote 25
        assertEquals("25", vm.uiState.value.currentProgress)
        assertNull(vm.uiState.value.suggestedTracker)
    }

    @Test
    fun dismissTrackerSuggestion_deletesSuggestedLink() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "item-sugg-2",
            title = "Sousou no Frieren",
            category = MediaCategory.MANGA,
            currentProgress = 5,
            totalItems = 100,
            lastUpdated = 1000L,
            dateAdded = 1000L,
            userStatus = UserStatus.READING,
            rating = null
        )
        repo.items.add(existing)

        val linkDao = FakeMediaLinkDao()
        val suggestedLink = MediaLinkEntity(
            id = "link-sugg-2",
            mediaId = "item-sugg-2",
            kind = "TRACKER",
            providerId = "anilist",
            remoteId = "140101",
            state = "SUGGESTED",
            url = null,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        linkDao.links.add(suggestedLink)

        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File("."),
            mediaLinkDao = linkDao
        )

        vm.initialize("item-sugg-2", null)
        testScheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.suggestedTracker)

        vm.dismissTrackerSuggestion()
        testScheduler.advanceUntilIdle()

        assertTrue(linkDao.links.none { it.id == "link-sugg-2" })
        assertNull(vm.uiState.value.suggestedTracker)
    }
}

