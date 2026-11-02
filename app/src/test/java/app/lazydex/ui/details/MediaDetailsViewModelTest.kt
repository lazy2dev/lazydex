package app.lazydex.ui.details

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MediaDetailsViewModelTest {

    private class FakeRepository : MediaRepository {
        val items = mutableListOf<MediaItem>()
        override fun observeAll(): Flow<List<MediaItem>> = flowOf(items)
        override fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>> = flowOf(items.filter { it.category == category })
        override fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>> = flowOf(items)
        override fun observeById(id: String): Flow<MediaItem?> = flowOf(items.find { it.id == id })
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
            return toAdd
        }
        override suspend fun update(item: MediaItem) {
            val idx = items.indexOfFirst { it.id == item.id }
            if (idx >= 0) items[idx] = item else items.add(item)
        }
        override suspend fun delete(id: String) {
            items.removeIf { it.id == id }
        }
        override suspend fun replaceAll(items: List<MediaItem>) {
            this.items.clear()
            this.items.addAll(items)
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
            scraper = MetadataScraper(SourceRegistry(emptyList())),
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
            scraper = MetadataScraper(SourceRegistry(emptyList())),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.initialize("existing-1", null)
        val state = vm.uiState.value
        assertFalse(state.isNew)
        assertEquals("Omniscient Reader", state.title)
        assertEquals("100", state.currentProgress)
        assertEquals("551", state.totalItems)
    }

    @Test
    fun save_withMetadataAndFormat_preservesRomajiNativeAndSecondaryProgress() = runTest {
        val repo = FakeRepository()
        val vm = MediaDetailsViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList())),
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
            scraper = MetadataScraper(SourceRegistry(emptyList())),
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
            scraper = MetadataScraper(SourceRegistry(emptyList())),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.initialize("existing-3", null)
        vm.deleteItem()

        assertTrue(vm.uiState.value.isDone)
        assertTrue(repo.items.isEmpty())
    }
}

