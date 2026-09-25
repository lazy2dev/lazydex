package app.lazydex.ui.addedit

import androidx.lifecycle.SavedStateHandle
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.MetadataScraper
import app.lazydex.scraper.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UnifiedAddEditViewModelTest {

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

    @org.junit.Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    @org.junit.After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun newEntry_startsBlank_saveWithoutTitleSetsError() = runTest {
        val repo = FakeRepository()
        val vm = UnifiedAddEditViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList())),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        val state = vm.formState.value
        assertTrue(state.isNew)
        assertTrue(state.isTitleBlank)
        assertFalse(state.canSave)

        // Attempt save without title
        vm.save()
        assertEquals("Title is required", vm.formState.value.errorMsg)
        assertFalse(vm.formState.value.isDone)

        // Enter title and save
        vm.updateTitle("Lord of the Mysteries")
        assertNull(vm.formState.value.errorMsg)
        assertTrue(vm.formState.value.canSave)

        vm.save()
        assertTrue(vm.formState.value.isDone)
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

        val vm = UnifiedAddEditViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repo,
            scraper = MetadataScraper(SourceRegistry(emptyList())),
            okHttpClient = OkHttpClient(),
            cacheDir = File(".")
        )

        vm.initialize("existing-1", null)
        val state = vm.formState.value
        assertFalse(state.isNew)
        assertEquals("Omniscient Reader", state.title)
        assertEquals("100", state.currentProgress)
        assertEquals("551", state.totalItems)
    }
}
