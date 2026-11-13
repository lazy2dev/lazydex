package app.lazydex.ui.settings

import android.content.SharedPreferences
import app.lazydex.data.local.DisplayPreferences
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import org.junit.Assert.assertNull
import app.lazydex.data.local.LibraryDisplayMode
import app.lazydex.data.local.ThemePreferences
import app.lazydex.data.local.ThemePreset
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var tempCoversDir: File
    private lateinit var tempCacheDir: File

    private class FakeRepository(
        val items: MutableList<MediaItem> = mutableListOf()
    ) : MediaRepository {
        override fun observeAll(): Flow<List<MediaItem>> = flowOf(items)
        override fun observeHistory(): Flow<List<MediaItem>> = flowOf(items)
        override fun observeByType(mediaType: MediaType): Flow<List<MediaItem>> = flowOf(items.filter { it.mediaType == mediaType })
        override fun observeFiltered(mediaType: MediaType?, statusFilter: StatusFilter): Flow<List<MediaItem>> = flowOf(items)
        override fun observeById(id: String): Flow<MediaItem?> = flowOf(items.find { it.id == id })
        override fun observeCount(): Flow<Int> = flowOf(items.size)
        override fun observeStats(): Flow<MediaStats> = flowOf(MediaStats(0, 0, 0, null, 0, 0, 0, 0, 0))
        override fun observeTypeCounts(): Flow<Map<MediaType, Int>> = flowOf(emptyMap())
        override fun observeStatusCounts(mediaType: MediaType?): Flow<Map<StatusFilter, Int>> = flowOf(emptyMap())
        override fun observeDistinctGenres(mediaType: MediaType?): Flow<List<String>> = flowOf(emptyList())
        override fun observeDistinctTags(mediaType: MediaType?): Flow<List<String>> = flowOf(emptyList())

        override suspend fun getById(id: String): MediaItem? = items.find { it.id == id }
        override suspend fun getAll(): List<MediaItem> = items.toList()
        override suspend fun add(item: MediaItem): MediaItem {
            items.add(item)
            return item
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

    private class FakeThemePreferences : ThemePreferences() {
        private val _themeMode = MutableStateFlow("DARK")
        override val themeMode: Flow<String> = _themeMode.asStateFlow()

        private val _themePreset = MutableStateFlow(ThemePreset.DEFAULT)
        override val themePreset: Flow<ThemePreset> = _themePreset.asStateFlow()

        private val _amoledMode = MutableStateFlow(false)
        override val amoledMode: Flow<Boolean> = _amoledMode.asStateFlow()

        private val _coverTheming = MutableStateFlow(false)
        override val coverTheming: Flow<Boolean> = _coverTheming.asStateFlow()

        private val _blurAdultCovers = MutableStateFlow(false)
        override val blurAdultCovers: Flow<Boolean> = _blurAdultCovers.asStateFlow()

        private val _displayPreferences = MutableStateFlow(DisplayPreferences())
        override val displayPreferences: Flow<DisplayPreferences> = _displayPreferences.asStateFlow()

        override suspend fun setThemeMode(mode: String) {
            _themeMode.value = mode
        }

        override suspend fun setThemePreset(preset: ThemePreset) {
            _themePreset.value = preset
        }

        override suspend fun setAmoledMode(enabled: Boolean) {
            _amoledMode.value = enabled
        }

        override suspend fun setCoverTheming(enabled: Boolean) {
            _coverTheming.value = enabled
        }

        override suspend fun setDisplayMode(mode: LibraryDisplayMode) {
            _displayPreferences.value = _displayPreferences.value.copy(displayMode = mode)
        }

        override suspend fun setGridColumns(columns: Int) {
            _displayPreferences.value = _displayPreferences.value.copy(gridColumns = columns)
        }

        override suspend fun setShowTypeTabs(enabled: Boolean) {
            _displayPreferences.value = _displayPreferences.value.copy(showTypeTabs = enabled)
        }

        override suspend fun setShowProgressBadge(enabled: Boolean) {
            _displayPreferences.value = _displayPreferences.value.copy(showProgressBadge = enabled)
        }

        override suspend fun setShowStatusBadge(enabled: Boolean) {
            _displayPreferences.value = _displayPreferences.value.copy(showStatusBadge = enabled)
        }

        override suspend fun setShowScoreBadge(enabled: Boolean) {
            _displayPreferences.value = _displayPreferences.value.copy(showScoreBadge = enabled)
        }

        override suspend fun setShowItemCount(enabled: Boolean) {
            _displayPreferences.value = _displayPreferences.value.copy(showItemCount = enabled)
        }

        override suspend fun setBlurAdultCovers(enabled: Boolean) {
            _blurAdultCovers.value = enabled
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempCoversDir = Files.createTempDirectory("lazydex_test_covers").toFile()
        tempCacheDir = Files.createTempDirectory("lazydex_test_cache").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempCoversDir.deleteRecursively()
        tempCacheDir.deleteRecursively()
    }

    @Test
    fun testThemePresetPersistenceAndUpdates() = runTest {
        val repo = FakeRepository()
        val prefs = FakeThemePreferences()
        val viewModel = SettingsViewModel(repo, prefs, tempCoversDir, tempCacheDir, testDispatcher)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertEquals(ThemePreset.DEFAULT, viewModel.uiState.value.themePreset)

        viewModel.setThemePreset(ThemePreset.MANGA)
        assertEquals(ThemePreset.MANGA, viewModel.uiState.value.themePreset)

        viewModel.setThemePreset(ThemePreset.GAME)
        assertEquals(ThemePreset.GAME, viewModel.uiState.value.themePreset)

        viewModel.setThemePreset(ThemePreset.MOVIE)
        assertEquals(ThemePreset.MOVIE, viewModel.uiState.value.themePreset)

        viewModel.setThemeMode("LIGHT")
        assertEquals("LIGHT", viewModel.uiState.value.themeMode)

        viewModel.setAmoledMode(true)
        assertTrue(viewModel.uiState.value.amoledMode)

        collectJob.cancel()
    }

    @Test
    fun testStorageMetricsAndCacheCleaning() = runTest {
        val repo = FakeRepository()
        val prefs = FakeThemePreferences()

        // Create dummy cache files
        val dummyCache1 = File(tempCacheDir, "temp1.bin").apply { writeText("Hello Cache World") }
        val dummyCache2 = File(tempCacheDir, "temp2.bin").apply { writeText("Second Cache File") }
        assertTrue(dummyCache1.exists())
        assertTrue(dummyCache2.exists())

        val viewModel = SettingsViewModel(repo, prefs, tempCoversDir, tempCacheDir, testDispatcher)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        // Cache bytes should be calculated and > 0
        assertTrue(viewModel.uiState.value.storageStats.cacheBytes > 0)

        // Clear temporary cache
        viewModel.clearTemporaryCache()

        assertFalse(dummyCache1.exists())
        assertFalse(dummyCache2.exists())
        assertEquals(0L, viewModel.uiState.value.storageStats.cacheBytes)
        assertNotNull(viewModel.uiState.value.successMsg)

        collectJob.cancel()
    }

    @Test
    fun testOrphanCoverCleaningLeavesActiveCoversIntact() = runTest {
        val activeFile1 = File(tempCoversDir, "item-1").apply { writeText("Cover 1 Data") }
        val activeFile2 = File(tempCoversDir, "item-2.jpg").apply { writeText("Cover 2 Data") }
        // item-3 has cover file named item-3.png matching ID by nameWithoutExtension even if coverImagePath is blank
        val activeFile3 = File(tempCoversDir, "item-3.png").apply { writeText("Cover 3 Data") }
        // item-4 has coverImagePath filename matching activeCoverFileNames
        val activeFile4 = File(tempCoversDir, "custom_named_cover.jpg").apply { writeText("Cover 4 Data") }

        val orphanFile1 = File(tempCoversDir, "orphan-1.png").apply { writeText("Orphan 1") }
        val orphanFile2 = File(tempCoversDir, "orphan-2.jpg").apply { writeText("Orphan 2") }

        val activeItems = mutableListOf(
            MediaItem(
                id = "item-1",
                title = "Active 1",
                mediaType = MediaType.NOVEL,
                coverImagePath = activeFile1.absolutePath,
                currentProgress = 10,
                totalItems = 100,
                userStatus = app.lazydex.domain.model.UserStatus.CURRENT,
                lastUpdated = 1000L,
                dateAdded = 1000L
            ),
            MediaItem(
                id = "item-2",
                title = "Active 2",
                mediaType = MediaType.MANGA,
                coverImagePath = activeFile2.absolutePath,
                currentProgress = 5,
                totalItems = 50,
                userStatus = app.lazydex.domain.model.UserStatus.COMPLETED,
                lastUpdated = 2000L,
                dateAdded = 2000L
            ),
            MediaItem(
                id = "item-3",
                title = "Active 3",
                mediaType = MediaType.ANIME,
                coverImagePath = "", // Blank cover path in DB, but file exists named item-3.png
                currentProgress = 1,
                totalItems = 12,
                userStatus = app.lazydex.domain.model.UserStatus.PLANNING,
                lastUpdated = 3000L,
                dateAdded = 3000L
            ),
            MediaItem(
                id = "item-4",
                title = "Active 4",
                mediaType = MediaType.GAME,
                coverImagePath = "/different/virtual/dir/custom_named_cover.jpg",
                currentProgress = 0,
                totalItems = 1,
                userStatus = app.lazydex.domain.model.UserStatus.PLANNING,
                lastUpdated = 4000L,
                dateAdded = 4000L
            )
        )

        val repo = FakeRepository(activeItems)
        val prefs = FakeThemePreferences()
        val viewModel = SettingsViewModel(repo, prefs, tempCoversDir, tempCacheDir, testDispatcher)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertTrue(activeFile1.exists())
        assertTrue(activeFile2.exists())
        assertTrue(activeFile3.exists())
        assertTrue(activeFile4.exists())
        assertTrue(orphanFile1.exists())
        assertTrue(orphanFile2.exists())

        // Clean orphaned covers
        viewModel.cleanOrphanCovers()

        // Active items must still exist!
        assertTrue("Active cover 1 must not be deleted", activeFile1.exists())
        assertTrue("Active cover 2 must not be deleted", activeFile2.exists())
        assertTrue("Active cover 3 (matched by nameWithoutExtension) must not be deleted", activeFile3.exists())
        assertTrue("Active cover 4 (matched by activeCoverFileNames) must not be deleted", activeFile4.exists())

        // Orphaned files must be deleted!
        assertFalse("Orphan cover 1 must be deleted", orphanFile1.exists())
        assertFalse("Orphan cover 2 must be deleted", orphanFile2.exists())

        assertNotNull(viewModel.uiState.value.successMsg)
        assertTrue(viewModel.uiState.value.successMsg!!.contains("Cleaned 2 orphaned covers"))

        collectJob.cancel()
    }

    @Test
    fun testCleanOrphanCoversWithNoOrphans() = runTest {
        val activeFile = File(tempCoversDir, "item-1").apply { writeText("Cover") }
        val activeItems = listOf(
            MediaItem(
                id = "item-1",
                title = "Active 1",
                mediaType = MediaType.NOVEL,
                coverImagePath = activeFile.absolutePath,
                currentProgress = 0,
                totalItems = 0,
                userStatus = app.lazydex.domain.model.UserStatus.PLANNING,
                lastUpdated = 1000L,
                dateAdded = 1000L
            )
        )
        val repo = FakeRepository(activeItems.toMutableList())
        val prefs = FakeThemePreferences()
        val viewModel = SettingsViewModel(repo, prefs, tempCoversDir, tempCacheDir, testDispatcher)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.cleanOrphanCovers()
        assertEquals("No orphaned covers found", viewModel.uiState.value.successMsg)
        assertTrue(activeFile.exists())

        collectJob.cancel()
    }

    @Test
    fun testClearTemporaryCacheEmptyDir() = runTest {
        val repo = FakeRepository()
        val prefs = FakeThemePreferences()
        val viewModel = SettingsViewModel(repo, prefs, tempCoversDir, tempCacheDir, testDispatcher)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.clearTemporaryCache()
        assertEquals("Temporary cache cleared", viewModel.uiState.value.successMsg)
        assertEquals(0L, viewModel.uiState.value.storageStats.cacheBytes)

        collectJob.cancel()
    }

    @Test
    fun testTrackingInitialStateAndLogout() = runTest {
        val repo = FakeRepository()
        val prefs = FakeThemePreferences()
        val fakePrefs = FakeTrackingSharedPreferences()
        val tokenStore = AnilistTokenStore(fakePrefs)
        tokenStore.accessToken = "test_access_token_123"
        tokenStore.saveUser(id = 999, name = "OtakuTester", adult = true)

        val viewModel = SettingsViewModel(
            repository = repo,
            themePreferences = prefs,
            localCoversDir = tempCoversDir,
            cacheDir = tempCacheDir,
            tokenStore = tokenStore,
            ioDispatcher = testDispatcher
        )

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertTrue(viewModel.uiState.value.tracking.isLoggedIn)
        assertEquals("OtakuTester", viewModel.uiState.value.tracking.username)
        assertFalse(viewModel.uiState.value.tracking.strictAdultContent)

        viewModel.logoutAniList()

        assertFalse(viewModel.uiState.value.tracking.isLoggedIn)
        assertNull(viewModel.uiState.value.tracking.username)
        assertFalse(tokenStore.isLoggedIn)

        collectJob.cancel()
    }

    @Test
    fun testTrackingBackgroundSyncAndAdultFilter() = runTest {
        val repo = FakeRepository()
        val prefs = FakeThemePreferences()
        val fakePrefs = FakeTrackingSharedPreferences()
        val tokenStore = AnilistTokenStore(fakePrefs)

        val viewModel = SettingsViewModel(
            repository = repo,
            themePreferences = prefs,
            localCoversDir = tempCoversDir,
            cacheDir = tempCacheDir,
            tokenStore = tokenStore,
            ioDispatcher = testDispatcher
        )

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        // Default background sync should be true
        assertTrue(viewModel.uiState.value.tracking.backgroundSyncEnabled)

        // Toggle background sync
        viewModel.setBackgroundSyncEnabled(false)
        assertFalse(viewModel.uiState.value.tracking.backgroundSyncEnabled)
        assertFalse(tokenStore.backgroundSyncEnabled)

        // Toggle strict adult content filter (strict filter ON means adult content OFF)
        viewModel.setStrictAdultContent(true)
        assertTrue(viewModel.uiState.value.tracking.strictAdultContent)
        assertFalse(tokenStore.displayAdultContent)

        // Toggle blur adult covers
        assertFalse(viewModel.uiState.value.tracking.blurAdultCovers)
        viewModel.setBlurAdultCovers(true)
        assertTrue(viewModel.uiState.value.tracking.blurAdultCovers)

        collectJob.cancel()
    }

    @Test
    fun testTrackingPreferencesOption2() = runTest {
        val repo = FakeRepository()
        val prefs = FakeThemePreferences()
        val fakePrefs = FakeTrackingSharedPreferences()
        val tokenStore = AnilistTokenStore(fakePrefs)

        val viewModel = SettingsViewModel(
            repository = repo,
            themePreferences = prefs,
            localCoversDir = tempCoversDir,
            cacheDir = tempCacheDir,
            tokenStore = tokenStore,
            ioDispatcher = testDispatcher
        )

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        // Verify default tracking preferences
        assertTrue(viewModel.uiState.value.tracking.trackOnAdd)
        assertTrue(viewModel.uiState.value.tracking.autoUpdateAfterReading)
        assertEquals("ALWAYS", viewModel.uiState.value.tracking.autoUpdateOnMarkRead)
        assertTrue(viewModel.uiState.value.tracking.autoSyncProgressFromTrackers)

        // Mutate preferences
        viewModel.setTrackOnAdd(false)
        assertFalse(viewModel.uiState.value.tracking.trackOnAdd)
        assertFalse(tokenStore.trackOnAdd)

        viewModel.setAutoUpdateAfterReading(false)
        assertFalse(viewModel.uiState.value.tracking.autoUpdateAfterReading)
        assertFalse(tokenStore.autoUpdateAfterReading)

        viewModel.setAutoUpdateOnMarkRead("ASK")
        assertEquals("ASK", viewModel.uiState.value.tracking.autoUpdateOnMarkRead)
        assertEquals("ASK", tokenStore.autoUpdateOnMarkRead)

        viewModel.setAutoSyncProgressFromTrackers(false)
        assertFalse(viewModel.uiState.value.tracking.autoSyncProgressFromTrackers)
        assertFalse(tokenStore.autoSyncProgressFromTrackers)

        collectJob.cancel()
    }

    private class FakeTrackingSharedPreferences : SharedPreferences {
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
                if (clearRequested) {
                    backingData.clear()
                }
                backingData.putAll(uncommitted)
                removed.forEach { backingData.remove(it) }
            }
        }
    }
}
