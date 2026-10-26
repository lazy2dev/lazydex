package app.lazydex.ui.settings.extensions

import app.lazydex.domain.model.ExtensionEcosystem
import app.lazydex.domain.model.ExtensionPackage
import app.lazydex.domain.model.ExtensionRepo
import app.lazydex.domain.model.ExtensionRuntimeType
import app.lazydex.domain.model.InstalledExtension
import app.lazydex.domain.model.MediaCategory
import app.lazydex.extension.repository.ExtensionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val reposFlow = MutableStateFlow<List<ExtensionRepo>>(emptyList())
    private val installedFlow = MutableStateFlow<List<InstalledExtension>>(emptyList())
    private val availableFlow = MutableStateFlow<List<ExtensionPackage>>(emptyList())

    private lateinit var fakeRepository: ExtensionRepository
    private lateinit var viewModel: ExtensionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock ExtensionRepository via reflection proxy on a dummy instance or subclass
        // Since ExtensionRepository is a class, let's create a proxy by subclassing or delegating
        // ExtensionRepository constructor takes context, DAOs, etc.
        // Instead of real repository, we can instantiate ExtensionRepository with mock objects
        val repoDaoProxy = Proxy.newProxyInstance(
            app.lazydex.data.local.dao.ExtensionRepoDao::class.java.classLoader,
            arrayOf(app.lazydex.data.local.dao.ExtensionRepoDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllReposFlow" -> reposFlow.asStateFlow()
                "getAllRepos" -> reposFlow.value
                else -> null
            }
        } as app.lazydex.data.local.dao.ExtensionRepoDao

        val installedDaoProxy = Proxy.newProxyInstance(
            app.lazydex.data.local.dao.InstalledExtensionDao::class.java.classLoader,
            arrayOf(app.lazydex.data.local.dao.InstalledExtensionDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllInstalledFlow" -> installedFlow.asStateFlow()
                "getAllInstalled" -> installedFlow.value
                else -> null
            }
        } as app.lazydex.data.local.dao.InstalledExtensionDao

        val tempDir = java.io.File.createTempFile("test_vm_dir", "").apply {
            delete()
            mkdirs()
        }
        val context = app.lazydex.extension.repository.TestExtensionContext(tempDir)
        val okHttpClient = okhttp3.OkHttpClient()

        fakeRepository = ExtensionRepository(
            context = context,
            repoDao = repoDaoProxy,
            installedDao = installedDaoProxy,
            manifestParser = app.lazydex.extension.manifest.ManifestParser(),
            okHttpClient = okHttpClient,
            dexEngine = app.lazydex.extension.runtime.DexRuntimeEngine(context),
            jsEngine = app.lazydex.extension.runtime.JsRuntimeEngine(okHttpClient),
            luaEngine = app.lazydex.extension.runtime.LuaRuntimeEngine(okHttpClient),
            wasmEngine = app.lazydex.extension.runtime.WasmRuntimeEngine(okHttpClient),
            sourceRegistry = app.lazydex.scraper.source.SourceRegistry(emptyList())
        )

        viewModel = ExtensionViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testTabSelection() {
        assertEquals(ExtensionTab.INSTALLED, viewModel.uiState.value.selectedTab)
        viewModel.selectTab(ExtensionTab.AVAILABLE)
        assertEquals(ExtensionTab.AVAILABLE, viewModel.uiState.value.selectedTab)
        viewModel.selectTab(ExtensionTab.UPDATES)
        assertEquals(ExtensionTab.UPDATES, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun testSearchQueryUpdate() {
        viewModel.updateSearchQuery("MangaDex")
        assertEquals("MangaDex", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun testFilterToggles() {
        viewModel.selectLanguage("en")
        assertEquals("en", viewModel.uiState.value.selectedLanguage)
        // Selecting same language toggles it off
        viewModel.selectLanguage("en")
        assertNull(viewModel.uiState.value.selectedLanguage)

        viewModel.selectEcosystem(ExtensionEcosystem.MIHON)
        assertEquals(ExtensionEcosystem.MIHON, viewModel.uiState.value.selectedEcosystem)
        // Selecting same ecosystem toggles it off
        viewModel.selectEcosystem(ExtensionEcosystem.MIHON)
        assertNull(viewModel.uiState.value.selectedEcosystem)
    }

    @Test
    fun testDialogAndSheetToggles() {
        viewModel.openAddRepoDialog("https://example.com/repo")
        assertTrue(viewModel.uiState.value.showAddRepoDialog)
        assertEquals("https://example.com/repo", viewModel.uiState.value.addRepoInputUrl)

        viewModel.dismissAddRepoDialog()
        assertFalse(viewModel.uiState.value.showAddRepoDialog)

        viewModel.openRepoSheet()
        assertTrue(viewModel.uiState.value.showRepoSheet)

        viewModel.dismissRepoSheet()
        assertFalse(viewModel.uiState.value.showRepoSheet)
    }

    @Test
    fun testEmptyUrlValidationOnAddRepo() {
        viewModel.openAddRepoDialog("")
        viewModel.confirmAddRepo()
        assertEquals("URL cannot be empty", viewModel.uiState.value.addRepoError)
    }
}
