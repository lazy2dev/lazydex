package app.lazydex.extension.repository

import android.content.Context
import app.lazydex.data.local.dao.ExtensionRepoDao
import app.lazydex.data.local.dao.InstalledExtensionDao
import app.lazydex.data.local.entity.ExtensionRepoEntity
import app.lazydex.data.local.entity.InstalledExtensionEntity
import app.lazydex.domain.model.ExtensionEcosystem
import app.lazydex.domain.model.ExtensionPackage
import app.lazydex.domain.model.ExtensionRepo
import app.lazydex.domain.model.ExtensionRuntimeType
import app.lazydex.domain.model.InstalledExtension
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.SyncStatus
import app.lazydex.extension.manifest.ManifestParser
import app.lazydex.extension.runtime.DexRuntimeEngine
import app.lazydex.extension.runtime.JsRuntimeEngine
import app.lazydex.extension.runtime.LuaRuntimeEngine
import app.lazydex.extension.runtime.WasmRuntimeEngine
import app.lazydex.scraper.source.Source
import app.lazydex.scraper.source.SourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.UUID

class ExtensionRepository(
    private val context: Context,
    private val repoDao: ExtensionRepoDao,
    private val installedDao: InstalledExtensionDao,
    private val manifestParser: ManifestParser,
    private val okHttpClient: OkHttpClient,
    private val dexEngine: DexRuntimeEngine,
    private val jsEngine: JsRuntimeEngine,
    private val luaEngine: LuaRuntimeEngine,
    private val wasmEngine: WasmRuntimeEngine,
    private val sourceRegistry: SourceRegistry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val syncSemaphore = Semaphore(3)
    private val reposDir = File(context.filesDir, "repos")
    private val cachedPackages = MutableStateFlow<Map<String, List<ExtensionPackage>>>(emptyMap())

    init {
        if (!reposDir.exists()) {
            reposDir.mkdirs()
        }

        // Preload cached packages from local disk manifests
        scope.launch {
            val repos = repoDao.getAllRepos()
            val initialMap = mutableMapOf<String, List<ExtensionPackage>>()
            repos.forEach { repo ->
                val manifestFile = File(reposDir, "${repo.id}.manifest")
                if (manifestFile.exists()) {
                    runCatching {
                        val bytes = manifestFile.readBytes()
                        val (_, packages) = manifestParser.parse(bytes, repo.manifestUrl, repo.id)
                        initialMap[repo.id] = packages
                    }
                }
            }
            if (initialMap.isNotEmpty()) {
                cachedPackages.value = initialMap
            }
        }

        // Observe installed extensions from DB and update SourceRegistry dynamically
        scope.launch {
            installedDao.getAllInstalledFlow()
                .distinctUntilChanged()
                .collect { entities ->
                    val sources = entities.mapNotNull { entity ->
                        loadSourceFromEntity(entity)
                    }
                    sourceRegistry.registerDynamicSources(sources)
                }
        }
    }

    fun getReposFlow(): Flow<List<ExtensionRepo>> {
        return repoDao.getAllReposFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getInstalledExtensionsFlow(): Flow<List<InstalledExtension>> {
        return installedDao.getAllInstalledFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getAvailableExtensionsFlow(): Flow<List<ExtensionPackage>> {
        return combine(
            cachedPackages,
            installedDao.getAllInstalledFlow()
        ) { repoMap, installedEntities ->
            val installedMap = installedEntities.associateBy { it.pkgName }
            repoMap.values.flatten().map { pkg ->
                val installed = installedMap[pkg.pkgName]
                if (installed != null) {
                    val hasUpdate = pkg.versionCode > installed.versionCode
                    pkg.copy(
                        isInstalled = true,
                        hasUpdate = hasUpdate,
                        installedVersionName = installed.versionName,
                        categoryOverride = installed.categoryOverride?.let { runCatching { MediaCategory.valueOf(it) }.getOrNull() }
                    )
                } else {
                    pkg
                }
            }
        }
    }

    suspend fun addRepo(rawUrl: String): Result<ExtensionRepo> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = probeManifestUrl(rawUrl.trim())
            val existing = repoDao.getRepoByManifestUrl(normalizedUrl)
            if (existing != null) {
                return@runCatching existing.toDomain()
            }

            val repoId = UUID.randomUUID().toString()
            val manifestBytes = fetchBytes(normalizedUrl)
            val (ecosystem, packages) = manifestParser.parse(manifestBytes, normalizedUrl, repoId)

            // Persist manifest to app-private disk for offline / startup recovery
            val manifestFile = File(reposDir, "$repoId.manifest")
            runCatching { manifestFile.writeBytes(manifestBytes) }

            val baseUri = URI(normalizedUrl)
            val repoName = if (packages.isNotEmpty()) {
                "${ecosystem.displayName} (${packages.size})"
            } else {
                baseUri.host ?: "Community Repo"
            }

            val entity = ExtensionRepoEntity(
                id = repoId,
                name = repoName,
                baseUrl = baseUri.resolve(".").toString(),
                manifestUrl = normalizedUrl,
                ecosystem = ecosystem.name,
                extensionCount = packages.size,
                lastSynced = System.currentTimeMillis(),
                syncStatus = "SUCCESS",
                syncError = null
            )
            repoDao.insertRepo(entity)

            // Cache discovered packages
            val currentMap = cachedPackages.value.toMutableMap()
            currentMap[repoId] = packages
            cachedPackages.value = currentMap

            entity.toDomain()
        }
    }

    suspend fun removeRepo(repoId: String) = withContext(Dispatchers.IO) {
        repoDao.deleteRepoById(repoId)
        val manifestFile = File(reposDir, "$repoId.manifest")
        if (manifestFile.exists()) {
            manifestFile.delete()
        }
        val currentMap = cachedPackages.value.toMutableMap()
        currentMap.remove(repoId)
        cachedPackages.value = currentMap
    }

    suspend fun refreshRepos(force: Boolean = false) = withContext(Dispatchers.IO) {
        val repos = repoDao.getAllRepos()
        val now = System.currentTimeMillis()
        val dayInMillis = 24 * 60 * 60 * 1000L

        coroutineScope {
            repos.map { repoEntity ->
                launch {
                    val manifestFile = File(reposDir, "${repoEntity.id}.manifest")
                    val needsSync = force || (now - repoEntity.lastSynced > dayInMillis) || !manifestFile.exists()
                    if (needsSync) {
                        syncSemaphore.withPermit {
                            try {
                                val manifestBytes = fetchBytes(repoEntity.manifestUrl)
                                val (ecosystem, packages) = manifestParser.parse(manifestBytes, repoEntity.manifestUrl, repoEntity.id)
                                runCatching { manifestFile.writeBytes(manifestBytes) }

                                repoDao.updateRepo(
                                    repoEntity.copy(
                                        ecosystem = ecosystem.name,
                                        extensionCount = packages.size,
                                        lastSynced = now,
                                        syncStatus = "SUCCESS",
                                        syncError = null
                                    )
                                )

                                val currentMap = cachedPackages.value.toMutableMap()
                                currentMap[repoEntity.id] = packages
                                cachedPackages.value = currentMap

                                // Check if any installed extensions have updates
                                checkUpdatesForRepo(repoEntity.id, packages)
                            } catch (e: Exception) {
                                repoDao.updateRepo(
                                    repoEntity.copy(
                                        syncStatus = "ERROR",
                                        syncError = e.message ?: "Failed to sync"
                                    )
                                )
                            }
                        }
                    }
                }
            }.joinAll()
        }
    }

    private suspend fun checkUpdatesForRepo(repoId: String, packages: List<ExtensionPackage>) {
        val installedList = installedDao.getAllInstalled()
        val pkgMap = packages.associateBy { it.pkgName }

        for (installed in installedList) {
            val remote = pkgMap[installed.pkgName] ?: continue
            if (remote.versionCode > installed.versionCode) {
                installedDao.updateExtension(
                    installed.copy(
                        hasUpdate = true,
                        latestVersionName = remote.versionName,
                        latestVersionCode = remote.versionCode,
                        downloadUrl = remote.apkOrArtifactUrl
                    )
                )
            }
        }
    }

    suspend fun installExtension(pkg: ExtensionPackage): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val extensionDir = File(context.filesDir, "extensions/${pkg.ecosystem.name.lowercase()}/${pkg.pkgName}")
            if (!extensionDir.exists()) {
                extensionDir.mkdirs()
            }

            val extensionExt = getFileExtensionForRuntime(pkg.runtimeType)
            val targetFile = File(extensionDir, "artifact.$extensionExt")
            val tempFile = File(extensionDir, "temp_${pkg.pkgName}.$extensionExt")

            try {
                downloadToFile(pkg.apkOrArtifactUrl, tempFile)

                // Delete old target file if exists (handling read-only files)
                if (targetFile.exists()) {
                    targetFile.setWritable(true)
                    targetFile.delete()
                }

                if (!tempFile.renameTo(targetFile)) {
                    // Fallback to copy & delete if rename across mount fails
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                // Android 14+ DCL Security: set read-only before classloader loads it
                if (isDexRuntime(pkg.runtimeType)) {
                    targetFile.setReadOnly()
                    dexEngine.unload(pkg.pkgName)
                }

                val entryClassOrFile = if (isDexRuntime(pkg.runtimeType)) {
                    dexEngine.extractEntryClassName(targetFile) ?: pkg.entryClassOrFile
                } else {
                    pkg.entryClassOrFile
                }

                val existing = installedDao.getExtension(pkg.pkgName)
                val preservedCategory = pkg.categoryOverride?.name ?: existing?.categoryOverride

                val installedEntity = InstalledExtensionEntity(
                    pkgName = pkg.pkgName,
                    repoId = pkg.repoId,
                    name = pkg.name,
                    versionName = pkg.versionName,
                    versionCode = pkg.versionCode,
                    lang = pkg.lang,
                    ecosystem = pkg.ecosystem.name,
                    runtimeType = pkg.runtimeType.name,
                    apkOrArtifactPath = targetFile.absolutePath,
                    iconUrl = pkg.iconUrl,
                    entryClassOrFile = entryClassOrFile,
                    categoryOverride = preservedCategory,
                    installedAt = existing?.installedAt ?: System.currentTimeMillis(),
                    hasUpdate = false,
                    latestVersionName = null,
                    latestVersionCode = null,
                    downloadUrl = null
                )

                installedDao.insertExtension(installedEntity)
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    suspend fun uninstallExtension(pkgName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dexEngine.unload(pkgName)
            val entity = installedDao.getExtension(pkgName) ?: return@runCatching
            val artifactFile = File(entity.apkOrArtifactPath)
            if (artifactFile.exists()) {
                artifactFile.setWritable(true)
                artifactFile.delete()
            }
            artifactFile.parentFile?.deleteRecursively()
            installedDao.deleteExtensionByPkg(pkgName)
        }
    }

    suspend fun setCategoryOverride(pkgName: String, category: MediaCategory?) = withContext(Dispatchers.IO) {
        installedDao.updateCategoryOverride(pkgName, category?.name)
    }

    private fun loadSourceFromEntity(entity: InstalledExtensionEntity): Source? {
        val file = File(entity.apkOrArtifactPath)
        if (!file.exists()) return null

        val categoryHint = entity.categoryOverride?.let {
            runCatching { MediaCategory.valueOf(it) }.getOrNull()
        } ?: runCatching { ExtensionEcosystem.valueOf(entity.ecosystem).defaultCategory }.getOrNull()

        val runtimeType = runCatching { ExtensionRuntimeType.valueOf(entity.runtimeType) }.getOrDefault(ExtensionRuntimeType.UNSUPPORTED)

        return when (runtimeType) {
            ExtensionRuntimeType.DEX_MIHON,
            ExtensionRuntimeType.DEX_ANIYOMI,
            ExtensionRuntimeType.DEX_IREADER -> {
                dexEngine.loadSource(entity.pkgName, file, entity.entryClassOrFile, categoryHint)
            }
            ExtensionRuntimeType.JS_LNREADER,
            ExtensionRuntimeType.JS_MANGAYOMI -> {
                jsEngine.loadSource(entity.pkgName, file, entity.name, categoryHint)
            }
            ExtensionRuntimeType.LUA_SHOSETSU -> {
                luaEngine.loadSource(entity.pkgName, file, entity.name, categoryHint)
            }
            ExtensionRuntimeType.WASM_TSUNDOKU,
            ExtensionRuntimeType.WASM_AIDOKU -> {
                wasmEngine.loadSource(entity.pkgName, file, entity.name, categoryHint)
            }
            ExtensionRuntimeType.UNSUPPORTED -> null
        }
    }

    private suspend fun probeManifestUrl(inputUrl: String): String = withContext(Dispatchers.IO) {
        val trimmed = inputUrl.trimEnd('/')
        if (trimmed.endsWith(".json", ignoreCase = true) || trimmed.endsWith(".pb", ignoreCase = true)) {
            return@withContext trimmed
        }

        // Automatic URL probing for repo base URLs
        val probes = listOf(
            "$trimmed/index.min.json",
            "$trimmed/index.pb",
            "$trimmed/index.json",
            "$trimmed/plugins.min.json"
        )

        for (probe in probes) {
            try {
                val req = Request.Builder().url(probe).head().build()
                val isSuccess = okHttpClient.newCall(req).execute().use { resp ->
                    resp.isSuccessful
                }
                if (isSuccess) {
                    return@withContext probe
                }
            } catch (_: Exception) {
                // Continue to next probe
            }
        }

        // Fallback default
        "$trimmed/index.min.json"
    }

    private fun fetchBytes(url: String): ByteArray {
        val req = Request.Builder().url(url).build()
        return okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
            }
            resp.body?.bytes() ?: throw IllegalStateException("Empty response body from $url")
        }
    }

    private fun downloadToFile(url: String, destFile: File) {
        val req = Request.Builder().url(url).build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Failed to download $url (HTTP ${resp.code})")
            }
            val body = resp.body ?: throw IllegalStateException("Empty body from $url")
            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun isDexRuntime(type: ExtensionRuntimeType): Boolean {
        return type == ExtensionRuntimeType.DEX_MIHON ||
                type == ExtensionRuntimeType.DEX_ANIYOMI ||
                type == ExtensionRuntimeType.DEX_IREADER
    }

    private fun getFileExtensionForRuntime(type: ExtensionRuntimeType): String {
        return when (type) {
            ExtensionRuntimeType.DEX_MIHON,
            ExtensionRuntimeType.DEX_ANIYOMI,
            ExtensionRuntimeType.DEX_IREADER -> "apk"
            ExtensionRuntimeType.JS_LNREADER,
            ExtensionRuntimeType.JS_MANGAYOMI -> "js"
            ExtensionRuntimeType.LUA_SHOSETSU -> "lua"
            ExtensionRuntimeType.WASM_TSUNDOKU,
            ExtensionRuntimeType.WASM_AIDOKU -> "wasm"
            ExtensionRuntimeType.UNSUPPORTED -> "bin"
        }
    }

    private fun ExtensionRepoEntity.toDomain() = ExtensionRepo(
        id = id,
        name = name,
        baseUrl = baseUrl,
        manifestUrl = manifestUrl,
        ecosystem = runCatching { ExtensionEcosystem.valueOf(ecosystem) }.getOrDefault(ExtensionEcosystem.UNKNOWN),
        extensionCount = extensionCount,
        lastSynced = lastSynced,
        syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.SUCCESS),
        syncError = syncError
    )

    private fun InstalledExtensionEntity.toDomain() = InstalledExtension(
        pkgName = pkgName,
        repoId = repoId,
        name = name,
        versionName = versionName,
        versionCode = versionCode,
        lang = lang,
        ecosystem = runCatching { ExtensionEcosystem.valueOf(ecosystem) }.getOrDefault(ExtensionEcosystem.UNKNOWN),
        runtimeType = runCatching { ExtensionRuntimeType.valueOf(runtimeType) }.getOrDefault(ExtensionRuntimeType.UNSUPPORTED),
        apkOrArtifactPath = apkOrArtifactPath,
        iconUrl = iconUrl,
        entryClassOrFile = entryClassOrFile,
        categoryOverride = categoryOverride?.let { runCatching { MediaCategory.valueOf(it) }.getOrNull() },
        installedAt = installedAt,
        hasUpdate = hasUpdate,
        latestVersionName = latestVersionName,
        latestVersionCode = latestVersionCode,
        downloadUrl = downloadUrl
    )
}
