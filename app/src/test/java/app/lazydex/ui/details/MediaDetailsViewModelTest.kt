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
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.TypeConfig
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.MetadataScraper
import app.lazydex.scraper.source.SourceRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        override fun observeHistory(): Flow<List<MediaItem>> = flowOf(items.filter { it.progressIncreasedAt != null }.sortedByDescending { it.progressIncreasedAt })
        override fun observeByType(mediaType: MediaType): Flow<List<MediaItem>> = flowOf(items.filter { it.mediaType == mediaType })
        override fun observeFiltered(mediaType: MediaType?, statusFilter: StatusFilter): Flow<List<MediaItem>> = flowOf(items)
        override fun observeById(id: String): Flow<MediaItem?> {
            return itemFlows.getOrPut(id) {
                kotlinx.coroutines.flow.MutableSharedFlow<MediaItem?>(replay = 1).apply {
                    tryEmit(items.find { it.id == id })
                }
            }
        }
        override fun observeCount(): Flow<Int> = flowOf(items.size)
        override fun observeStats(): Flow<MediaStats> = flowOf(MediaStats(0, 0, 0, null, 0, 0, 0, 0, 0))
        override fun observeTypeCounts(): Flow<Map<MediaType, Int>> = flowOf(emptyMap())
        override fun observeStatusCounts(mediaType: MediaType?): Flow<Map<StatusFilter, Int>> = flowOf(emptyMap())
        override fun observeDistinctGenres(mediaType: MediaType?): Flow<List<String>> = flowOf(emptyList())
        override fun observeDistinctTags(mediaType: MediaType?): Flow<List<String>> = flowOf(emptyList())
        override fun observeTopLevelTypes(): Flow<List<TypeConfig>> = flowOf(
            listOf(
                TypeConfig(id = "NOVEL", name = "Novel", unitName = "Chapter"),
                TypeConfig(id = "MANGA", name = "Manga", unitName = "Chapter"),
                TypeConfig(id = "ANIME", name = "Anime", unitName = "Episode"),
                TypeConfig(id = "GAME", name = "Game", unitName = "Hour"),
            )
        )
        override fun observeFormats(parentId: String): Flow<List<TypeConfig>> = flowOf(
            when (parentId.uppercase()) {
                "NOVEL", "TYP_NOVEL" -> listOf(
                    TypeConfig(id = "LIGHT_NOVEL", parentId = "NOVEL", name = "Light Novel", unitName = "Volume"),
                    TypeConfig(id = "WEB_NOVEL", parentId = "NOVEL", name = "Web Novel", unitName = "Chapter"),
                )
                "MANGA", "TYP_MANGA" -> listOf(
                    TypeConfig(id = "MANGA", parentId = "MANGA", name = "Manga", unitName = "Volume"),
                    TypeConfig(id = "MANHWA", parentId = "MANGA", name = "Manhwa", unitName = "Chapter"),
                    TypeConfig(id = "MANHUA", parentId = "MANGA", name = "Manhua", unitName = "Chapter"),
                    TypeConfig(id = "ONE_SHOT", parentId = "MANGA", name = "One Shot", unitName = "Chapter"),
                )
                "ANIME", "TYP_ANIME" -> listOf(
                    TypeConfig(id = "TV", parentId = "ANIME", name = "TV Series", unitName = "Episode", secondaryUnitName = "Season"),
                    TypeConfig(id = "MOVIE", parentId = "ANIME", name = "Movie", unitName = "Minute"),
                    TypeConfig(id = "OVA", parentId = "ANIME", name = "OVA / Special", unitName = "Episode"),
                )
                "GAME", "TYP_GAME" -> listOf(
                    TypeConfig(id = "PC", parentId = "GAME", name = "PC", unitName = "Hour"),
                )
                else -> emptyList()
            }
        )

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
    private val viewModelStore = androidx.lifecycle.ViewModelStore()
    private var vmCounter = 0

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        repository: MediaRepository = FakeRepository(),
        scraper: MetadataScraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
        okHttpClient: OkHttpClient = OkHttpClient(),
        cacheDir: File = File("."),
        mediaLinkDao: MediaLinkDao? = null,
        syncEngine: AnilistSyncEngine? = null,
        tokenStore: AnilistTokenStore? = null,
    ): MediaDetailsViewModel {
        val instance = MediaDetailsViewModel(
            savedStateHandle = savedStateHandle,
            repository = repository,
            scraper = scraper,
            okHttpClient = okHttpClient,
            cacheDir = cacheDir,
            mediaLinkDao = mediaLinkDao,
            syncEngine = syncEngine,
            tokenStore = tokenStore,
        )
        val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : androidx.lifecycle.ViewModel> create(modelClass: Class<V>): V = instance as V
        }
        return androidx.lifecycle.ViewModelProvider(viewModelStore, factory)["vm-${vmCounter++}", MediaDetailsViewModel::class.java]
    }

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun newEntry_startsBlank_saveWithoutTitleSetsError() = runTest {
        val repo = FakeRepository()
        val vm = createViewModel(
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
            mediaType = MediaType.NOVEL,
            title = "Omniscient Reader",
            currentProgress = 100,
            totalItems = 551,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = createViewModel(
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
        val vm = createViewModel(
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
    fun save_withMetadataAndFormat_preservesFormatAndSecondaryProgress() = runTest {
        val repo = FakeRepository()
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.updateTitle("The Beginning After the End")
        vm.updateMediaType(MediaType.MANGA)
        vm.updateFormatId("MANHWA")
        vm.updateCurrentProgress("175")
        vm.updateTotalItems("200")
        vm.updateSecondaryProgress("3")
        vm.updateBannerImage("https://example.com/tbate_banner.jpg")

        assertTrue(vm.uiState.value.hasSecondaryProgress)
        vm.save()

        assertTrue(vm.uiState.value.isDone)
        assertEquals(1, repo.items.size)
        val saved = repo.items[0]
        assertEquals("The Beginning After the End", saved.title)
        assertEquals("MANHWA", saved.format)
        assertEquals("KR", saved.countryOfOrigin)
        assertEquals("typ_manhwa", saved.typeId)
        assertEquals(175.0, saved.progress, 0.001)
        assertEquals(200.0, saved.progressTotal!!, 0.001)
        assertEquals(3.0, saved.secondaryProgress, 0.001)
        assertEquals("https://example.com/tbate_banner.jpg", saved.getExtra("banner_image"))
    }

    @Test
    fun autoSave_existingItem_updatesDatabaseWithoutExiting() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "existing-2",
            mediaType = MediaType.MANGA,
            title = "Solo Leveling",
            currentProgress = 50,
            totalItems = 200,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = createViewModel(
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
            mediaType = MediaType.ANIME,
            title = "Frieren",
            currentProgress = 28,
            totalItems = 28,
            userStatus = UserStatus.COMPLETED,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = createViewModel(
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
        val vm = createViewModel(
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
            mediaType = MediaType.NOVEL,
            title = "Original Title",
            currentProgress = 0,
            userStatus = UserStatus.PLANNING,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = createViewModel(
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
            mediaType = MediaType.NOVEL,
            title = "Notes Test",
            currentProgress = 10,
            userStatus = UserStatus.CURRENT,
            notes = "Initial",
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = createViewModel(
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
            mediaType = MediaType.NOVEL,
            title = "Flush Test",
            currentProgress = 10,
            userStatus = UserStatus.CURRENT,
            notes = "Initial",
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = createViewModel(
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
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.scrapeUrl("https://example.com/notfound")
        testScheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isScraping)
        assertNull(vm.uiState.value.errorMsg)
        assertEquals("Failed to scrape metadata from URL: https://example.com/notfound", vm.uiState.value.scrapeError)
    }

    @Test
    fun initialize_observesMediaLinkAndTokenStore() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "item-sync-1",
            mediaType = MediaType.NOVEL,
            title = "Sync Test",
            currentProgress = 10,
            userStatus = UserStatus.CURRENT,
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

        val vm = createViewModel(
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
            mediaType = MediaType.NOVEL,
            title = "Push Test",
            currentProgress = 10,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val syncEngine = TestSyncEngine()
        val vm = createViewModel(
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
            mediaType = MediaType.NOVEL,
            title = "Push Fail Test",
            currentProgress = 10,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val syncEngine = TestSyncEngine()
        syncEngine.shouldSucceed = false

        val vm = createViewModel(
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
            mediaType = MediaType.NOVEL,
            title = "Live Test",
            currentProgress = 10,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = createViewModel(
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
        val vm = createViewModel(
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
            mediaType = MediaType.MANGA,
            title = "Artwork Test",
            currentProgress = 1,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 100L,
            dateAdded = 100L
        )
        repo.items.add(existing)

        val vm = createViewModel(
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
            mediaType = MediaType.MANGA,
            currentProgress = 10,
            totalItems = 100,
            lastUpdated = 1000L,
            dateAdded = 1000L,
            userStatus = UserStatus.CURRENT,
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

        val vm = createViewModel(
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
            mediaType = MediaType.MANGA,
            currentProgress = 5,
            totalItems = 100,
            lastUpdated = 1000L,
            dateAdded = 1000L,
            userStatus = UserStatus.CURRENT,
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

        val vm = createViewModel(
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

    @Test
    fun coverTheming_defaultsToTrue_and_setSeedColorUpdatesState() = runTest {
        val repo = FakeRepository()
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        assertTrue(vm.uiState.value.coverTheming)
        assertNull(vm.uiState.value.seedColor)

        val vibrant = androidx.compose.ui.graphics.Color(0xFF3498DB)
        vm.setSeedColor(vibrant)
        assertEquals(vibrant, vm.uiState.value.seedColor)
    }

    @Test
    fun initialize_resetsSeedColor() = runTest {
        val repo = FakeRepository()
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        val vibrant = androidx.compose.ui.graphics.Color(0xFF3498DB)
        vm.setSeedColor(vibrant)
        assertEquals(vibrant, vm.uiState.value.seedColor)

        vm.initialize("new-id", null)
        assertNull(vm.uiState.value.seedColor)
    }

    @Test
    fun initialize_loadsAvailableTypesAndDynamicallyObservesFormats() = runTest {
        val repo = FakeRepository()
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        val state = vm.uiState.value
        assertEquals(listOf("NOVEL", "MANGA", "ANIME", "GAME"), state.availableTypes.map { it.id })
        assertEquals(listOf("LIGHT_NOVEL", "WEB_NOVEL"), state.availableFormats.map { it.id })

        val animeFormats = vm.observeFormatsForType("ANIME").first()
        assertEquals(listOf("TV", "MOVIE", "OVA"), animeFormats.map { it.id })
    }

    @Test
    fun commitMetadataEdits_existingItem_preservesDurationRepeatGenresTagsAndFormat() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "anime-1",
            type = "ANIME",
            format = "MOVIE",
            countryOfOrigin = "JP",
            title = "Your Name",
            genres = listOf("Drama", "Romance"),
            tags = listOf("Time Travel"),
            author = "Makoto Shinkai",
            description = "Old desc",
            currentProgress = 1,
            totalItems = 1,
            userStatus = UserStatus.COMPLETED,
            repeat = 2,
            durationMinutes = 106,
            extraData = """{"custom":"keep"}""",
            lengthUnitOverride = "WORDS",
            lastUpdated = 1000L,
            dateAdded = 500L,
        )
        repo.items.add(existing)

        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )
        vm.initialize("anime-1", null)

        val ok = vm.commitMetadataEdits(
            sourceUrl = "https://anilist.co/anime/21519",
            mediaType = MediaType.ANIME,
            formatId = "TV",
            title = "Your Name Edited",
            author = "CoMix Wave Films",
            totalItems = "12",
            description = "Updated desc",
        )

        assertTrue(ok)
        val saved = repo.items.single()
        assertEquals("Your Name Edited", saved.title)
        assertEquals("TV", saved.format)
        assertEquals("JP", saved.countryOfOrigin)
        assertEquals(2, saved.repeat)
        assertEquals(106, saved.durationMinutes)
        assertEquals(listOf("Drama", "Romance"), saved.genres)
        assertEquals(listOf("Time Travel"), saved.tags)
        assertEquals(500L, saved.dateAdded)
        assertEquals("""{"custom":"keep"}""", saved.extraData)
        assertEquals("WORDS", saved.lengthUnitOverride)
    }

    @Test
    fun commitMetadataEdits_updatesIsAdultSuccessfully() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "item-adult-1",
            type = "MANGA",
            format = "MANGA",
            title = "Berserk",
            currentProgress = 10,
            totalItems = 40,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 1000L,
            dateAdded = 500L,
            isAdult = false
        )
        repo.items.add(existing)

        val vm = createViewModel(
            repository = repo,
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )
        vm.initialize("item-adult-1", null)

        val ok = vm.commitMetadataEdits(
            sourceUrl = "",
            mediaType = MediaType.MANGA,
            formatId = "MANGA",
            title = "Berserk",
            author = "Kentaro Miura",
            totalItems = "40",
            description = "Dark fantasy",
            isAdult = true
        )

        assertTrue(ok)
        val saved = repo.items.single()
        assertTrue(saved.isAdult)
    }

    @Test
    fun commitMetadataEdits_invalidUrlOrProgressExceedsTotal_returnsFalseWithoutSaving() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "item-val-1",
            type = "NOVEL",
            format = "WEB_NOVEL",
            title = "Original",
            currentProgress = 25,
            totalItems = 100,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 1000L,
            dateAdded = 1000L,
        )
        repo.items.add(existing)

        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )
        vm.initialize("item-val-1", null)

        assertFalse(
            vm.commitMetadataEdits(
                sourceUrl = "not-a-url",
                mediaType = MediaType.NOVEL,
                formatId = "WEB_NOVEL",
                title = "Changed",
                author = "",
                totalItems = "100",
                description = "",
            )
        )
        assertEquals("Source URL must start with http:// or https://", vm.uiState.value.errorMsg)
        assertEquals("Original", repo.items.single().title)

        assertFalse(
            vm.commitMetadataEdits(
                sourceUrl = "",
                mediaType = MediaType.NOVEL,
                formatId = "WEB_NOVEL",
                title = "Changed",
                author = "",
                totalItems = "10",
                description = "",
            )
        )
        assertEquals("Progress cannot exceed total items", vm.uiState.value.errorMsg)
        assertEquals("Original", repo.items.single().title)
    }

    @Test
    fun commitMetadataEdits_newItem_updatesDraftWithoutImmediateDbWrite() = runTest {
        val repo = FakeRepository()
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        val ok = vm.commitMetadataEdits(
            sourceUrl = "https://example.com/novel",
            mediaType = MediaType.MANGA,
            formatId = "MANHWA",
            title = "New Manhwa",
            author = "Studio",
            totalItems = "50",
            description = "Synopsis",
        )

        assertTrue(ok)
        assertTrue(vm.uiState.value.isNew)
        assertEquals(0, repo.items.size)
        assertEquals("New Manhwa", vm.uiState.value.title)
        assertEquals("MANHWA", vm.uiState.value.formatId)

        vm.save()
        assertEquals(1, repo.items.size)
        assertEquals("MANHWA", repo.items.single().format)
        assertEquals("KR", repo.items.single().countryOfOrigin)
    }

    @Test
    fun observeItem_whileMetadataSheetOpen_ignoresDbEmissionsUntilClosed() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "sheet-guard-1",
            type = "NOVEL",
            title = "Initial Title",
            currentProgress = 5,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 1000L,
            dateAdded = 1000L,
        )
        repo.items.add(existing)

        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )
        vm.initialize("sheet-guard-1", null)
        assertEquals("Initial Title", vm.uiState.value.title)

        vm.setMetadataSheetOpen(true)
        repo.update(existing.copy(title = "Background Update"))
        assertEquals("Initial Title", vm.uiState.value.title)

        vm.setMetadataSheetOpen(false)
        repo.update(existing.copy(title = "Background Update 2"))
        assertEquals("Background Update 2", vm.uiState.value.title)
    }

    @Test
    fun observeItem_mangaKr_normalizesFormatToManhwa_andSwitchingToMangaClearsKr() = runTest {
        val repo = FakeRepository()
        val existing = MediaItem(
            id = "manhwa-kr-1",
            type = "MANGA",
            format = "MANGA",
            countryOfOrigin = "KR",
            title = "Omniscient Reader",
            userStatus = UserStatus.CURRENT,
            lastUpdated = 1000L,
            dateAdded = 1000L,
        )
        repo.items.add(existing)

        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )
        vm.initialize("manhwa-kr-1", null)
        assertEquals("MANHWA", vm.uiState.value.formatId)

        assertTrue(
            vm.commitMetadataEdits(
                sourceUrl = "",
                mediaType = MediaType.MANGA,
                formatId = "MANGA",
                title = "Omniscient Reader",
                author = "",
                totalItems = "",
                description = "",
            )
        )
        val saved = repo.items.single()
        assertEquals("MANGA", saved.format)
        assertNull(saved.countryOfOrigin)
        assertEquals("typ_manga", saved.typeId)
    }

    @Test
    fun scrapeUrl_failure_setsScrapeErrorWithoutSettingScreenErrorMsg() = runTest {
        val repo = FakeRepository()
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList()), ioDispatcher = testDispatcher),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.scrapeUrl("http://127.0.0.1/blocked")
        testScheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isScraping)
        assertNotNull(vm.uiState.value.scrapeError)
        assertNull(vm.uiState.value.errorMsg)
    }
}

