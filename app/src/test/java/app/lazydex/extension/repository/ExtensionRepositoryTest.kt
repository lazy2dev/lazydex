package app.lazydex.extension.repository

import android.content.Context
import android.content.ContextWrapper
import app.lazydex.data.local.dao.ExtensionRepoDao
import app.lazydex.data.local.dao.InstalledExtensionDao
import app.lazydex.data.local.entity.ExtensionRepoEntity
import app.lazydex.data.local.entity.InstalledExtensionEntity
import app.lazydex.domain.model.ExtensionEcosystem
import app.lazydex.domain.model.ExtensionPackage
import app.lazydex.domain.model.ExtensionRuntimeType
import app.lazydex.domain.model.MediaCategory
import app.lazydex.extension.manifest.ManifestParser
import app.lazydex.extension.runtime.DexRuntimeEngine
import app.lazydex.extension.runtime.JsRuntimeEngine
import app.lazydex.extension.runtime.LuaRuntimeEngine
import app.lazydex.extension.runtime.WasmRuntimeEngine
import app.lazydex.scraper.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URI

class TestExtensionContext(private val baseDir: File) : ContextWrapper(null) {
    override fun getFilesDir(): File = baseDir
    override fun getCodeCacheDir(): File = File(baseDir, "code_cache").apply { mkdirs() }
    override fun getClassLoader(): ClassLoader = javaClass.classLoader ?: ClassLoader.getSystemClassLoader()
    override fun getApplicationContext(): Context = this
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempDir: File
    private lateinit var testContext: Context
    private lateinit var fakeRepoDao: FakeExtensionRepoDao
    private lateinit var fakeInstalledDao: FakeInstalledExtensionDao
    private lateinit var fakeHttpClient: OkHttpClient
    private lateinit var repository: ExtensionRepository

    class FakeExtensionRepoDao : ExtensionRepoDao {
        val reposMap = mutableMapOf<String, ExtensionRepoEntity>()
        private val _reposFlow = MutableStateFlow<List<ExtensionRepoEntity>>(emptyList())

        override fun getAllReposFlow(): Flow<List<ExtensionRepoEntity>> = _reposFlow.asStateFlow()

        override suspend fun getAllRepos(): List<ExtensionRepoEntity> = reposMap.values.toList()

        override suspend fun getRepoById(id: String): ExtensionRepoEntity? = reposMap[id]

        override suspend fun getRepoByManifestUrl(manifestUrl: String): ExtensionRepoEntity? =
            reposMap.values.find { it.manifestUrl == manifestUrl }

        override suspend fun insertRepo(repo: ExtensionRepoEntity) {
            reposMap[repo.id] = repo
            _reposFlow.value = reposMap.values.toList()
        }

        override suspend fun insertRepos(repos: List<ExtensionRepoEntity>) {
            repos.forEach { insertRepo(it) }
        }

        override suspend fun updateRepo(repo: ExtensionRepoEntity) {
            reposMap[repo.id] = repo
            _reposFlow.value = reposMap.values.toList()
        }

        override suspend fun deleteRepo(repo: ExtensionRepoEntity) {
            reposMap.remove(repo.id)
            _reposFlow.value = reposMap.values.toList()
        }

        override suspend fun deleteRepoById(id: String) {
            reposMap.remove(id)
            _reposFlow.value = reposMap.values.toList()
        }
    }

    class FakeInstalledExtensionDao : InstalledExtensionDao {
        val installedMap = mutableMapOf<String, InstalledExtensionEntity>()
        private val _installedFlow = MutableStateFlow<List<InstalledExtensionEntity>>(emptyList())

        override fun getAllInstalledFlow(): Flow<List<InstalledExtensionEntity>> = _installedFlow.asStateFlow()

        override suspend fun getAllInstalled(): List<InstalledExtensionEntity> = installedMap.values.toList()

        override suspend fun getExtension(pkgName: String): InstalledExtensionEntity? = installedMap[pkgName]

        override suspend fun insertExtension(extension: InstalledExtensionEntity) {
            installedMap[extension.pkgName] = extension
            _installedFlow.value = installedMap.values.toList()
        }

        override suspend fun updateExtension(extension: InstalledExtensionEntity) {
            installedMap[extension.pkgName] = extension
            _installedFlow.value = installedMap.values.toList()
        }

        override suspend fun deleteExtension(extension: InstalledExtensionEntity) {
            installedMap.remove(extension.pkgName)
            _installedFlow.value = installedMap.values.toList()
        }

        override suspend fun deleteExtensionByPkg(pkgName: String) {
            installedMap.remove(pkgName)
            _installedFlow.value = installedMap.values.toList()
        }

        override suspend fun updateCategoryOverride(pkgName: String, categoryOverride: String?) {
            installedMap[pkgName]?.let {
                installedMap[pkgName] = it.copy(categoryOverride = categoryOverride)
                _installedFlow.value = installedMap.values.toList()
            }
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        tempDir = File.createTempFile("lazydex_repo_test", "").apply {
            delete()
            mkdirs()
        }
        testContext = TestExtensionContext(tempDir)
        fakeRepoDao = FakeExtensionRepoDao()
        fakeInstalledDao = FakeInstalledExtensionDao()

        // Create a fake HTTP client intercepting known test responses
        fakeHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                val (code, body) = when {
                    url.endsWith("index.min.json") -> {
                        200 to """
                            [
                              {
                                "name": "Test Manga",
                                "pkg": "eu.kanade.tachiyomi.extension.all.testmanga",
                                "apk": "testmanga.apk",
                                "lang": "en",
                                "code": 10,
                                "version": "1.0.0"
                              }
                            ]
                        """.trimIndent()
                    }
                    url.endsWith("testmanga.apk") -> {
                        200 to "fake_apk_binary_content"
                    }
                    else -> 404 to "Not Found"
                }

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Not Found")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val dexEngine = DexRuntimeEngine(testContext)
        val jsEngine = JsRuntimeEngine(fakeHttpClient)
        val luaEngine = LuaRuntimeEngine(fakeHttpClient)
        val wasmEngine = WasmRuntimeEngine(fakeHttpClient)
        val registry = SourceRegistry(emptyList())

        repository = ExtensionRepository(
            context = testContext,
            repoDao = fakeRepoDao,
            installedDao = fakeInstalledDao,
            manifestParser = ManifestParser(),
            okHttpClient = fakeHttpClient,
            dexEngine = dexEngine,
            jsEngine = jsEngine,
            luaEngine = luaEngine,
            wasmEngine = wasmEngine,
            sourceRegistry = registry
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    @Test
    fun testAddRepoSavesEntityAndManifestToDisk() = runTest {
        val result = repository.addRepo("https://example.com/repo/index.min.json")
        assertTrue(result.isSuccess)
        val repo = result.getOrThrow()

        assertEquals("https://example.com/repo/index.min.json", repo.manifestUrl)
        assertEquals(ExtensionEcosystem.MIHON, repo.ecosystem)
        assertEquals(1, repo.extensionCount)

        // Verify stored in DB
        val inDb = fakeRepoDao.getRepoById(repo.id)
        assertNotNull(inDb)

        // Verify manifest file persisted on disk
        val diskManifest = File(tempDir, "repos/${repo.id}.manifest")
        assertTrue("Manifest file should exist on disk", diskManifest.exists())
    }

    @Test
    fun testRemoveRepoDeletesFromDbAndDisk() = runTest {
        val addRes = repository.addRepo("https://example.com/repo/index.min.json")
        val repo = addRes.getOrThrow()

        val diskManifest = File(tempDir, "repos/${repo.id}.manifest")
        assertTrue(diskManifest.exists())

        repository.removeRepo(repo.id)
        assertNull(fakeRepoDao.getRepoById(repo.id))
        assertFalse(diskManifest.exists())
    }

    @Test
    fun testInstallAndUninstallExtensionLifecycle() = runTest {
        val pkg = ExtensionPackage(
            pkgName = "eu.kanade.tachiyomi.extension.all.testmanga",
            repoId = "repo-1",
            name = "Test Manga",
            versionName = "1.0.0",
            versionCode = 10L,
            lang = "en",
            ecosystem = ExtensionEcosystem.MIHON,
            runtimeType = ExtensionRuntimeType.DEX_MIHON,
            apkOrArtifactUrl = "https://example.com/repo/testmanga.apk",
            categoryOverride = MediaCategory.MANGA
        )

        val installRes = repository.installExtension(pkg)
        assertTrue(installRes.isSuccess)

        val installedEntity = fakeInstalledDao.getExtension(pkg.pkgName)
        assertNotNull(installedEntity)
        assertEquals("MANGA", installedEntity?.categoryOverride)

        val artifactFile = File(installedEntity!!.apkOrArtifactPath)
        assertTrue(artifactFile.exists())

        // Test Category Override persistence
        repository.setCategoryOverride(pkg.pkgName, MediaCategory.NOVEL)
        val updated = fakeInstalledDao.getExtension(pkg.pkgName)
        assertEquals("NOVEL", updated?.categoryOverride)

        // Updating the extension must preserve user's category override
        val updatePkg = pkg.copy(versionName = "1.1.0", versionCode = 11L, categoryOverride = null)
        val updateRes = repository.installExtension(updatePkg)
        assertTrue(updateRes.isSuccess)

        val afterUpdate = fakeInstalledDao.getExtension(pkg.pkgName)
        assertEquals("NOVEL", afterUpdate?.categoryOverride)

        // Test Uninstall
        val uninstallRes = repository.uninstallExtension(pkg.pkgName)
        assertTrue(uninstallRes.isSuccess)
        assertNull(fakeInstalledDao.getExtension(pkg.pkgName))
        assertFalse(artifactFile.exists())
    }

    @Test
    fun testGetAvailableExtensionsFlowCombinesInstalledState() = runTest {
        repository.addRepo("https://example.com/repo/index.min.json")
        advanceUntilIdle()

        val available = repository.getAvailableExtensionsFlow().first()
        assertEquals(1, available.size)
        assertFalse(available[0].isInstalled)

        // Simulate extension installed with older version
        fakeInstalledDao.insertExtension(
            InstalledExtensionEntity(
                pkgName = "eu.kanade.tachiyomi.extension.all.testmanga",
                repoId = "repo-1",
                name = "Test Manga",
                versionName = "0.9.0",
                versionCode = 5L,
                lang = "en",
                ecosystem = "MIHON",
                runtimeType = "DEX_MIHON",
                apkOrArtifactPath = "/fake/path",
                installedAt = 1000L,
                hasUpdate = false
            )
        )
        advanceUntilIdle()

        val availableAfterInstall = repository.getAvailableExtensionsFlow().first()
        assertEquals(1, availableAfterInstall.size)
        assertTrue(availableAfterInstall[0].isInstalled)
        assertTrue("Available should have update flag", availableAfterInstall[0].hasUpdate)
    }
}
