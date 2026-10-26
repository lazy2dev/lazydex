package app.lazydex.domain.model

enum class ExtensionEcosystem(val displayName: String, val defaultCategory: MediaCategory) {
    MIHON("Mihon", MediaCategory.MANGA),
    ANIYOMI("Aniyomi", MediaCategory.ANIME),
    IREADER("IReader", MediaCategory.NOVEL),
    LNREADER("LNReader", MediaCategory.NOVEL),
    SHOSETSU("Shosetsu", MediaCategory.NOVEL),
    MANGAYOMI("Mangayomi", MediaCategory.MANGA),
    TSUNDOKU("Tsundoku", MediaCategory.NOVEL),
    AIDOKU("Aidoku", MediaCategory.MANGA),
    UNKNOWN("Community", MediaCategory.MANGA)
}

enum class ExtensionRuntimeType {
    DEX_MIHON,
    DEX_ANIYOMI,
    DEX_IREADER,
    JS_LNREADER,
    JS_MANGAYOMI,
    LUA_SHOSETSU,
    WASM_TSUNDOKU,
    WASM_AIDOKU,
    UNSUPPORTED
}

enum class SyncStatus {
    SUCCESS,
    ERROR,
    SYNCING
}

data class ExtensionRepo(
    val id: String,
    val name: String,
    val baseUrl: String,
    val manifestUrl: String,
    val ecosystem: ExtensionEcosystem,
    val extensionCount: Int = 0,
    val lastSynced: Long = 0L,
    val syncStatus: SyncStatus = SyncStatus.SUCCESS,
    val syncError: String? = null
)

data class ExtensionPackage(
    val pkgName: String,
    val repoId: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String,
    val ecosystem: ExtensionEcosystem,
    val runtimeType: ExtensionRuntimeType,
    val apkOrArtifactUrl: String,
    val iconUrl: String? = null,
    val entryClassOrFile: String? = null,
    val nsfw: Boolean = false,
    val description: String? = null,
    val isInstalled: Boolean = false,
    val hasUpdate: Boolean = false,
    val installedVersionName: String? = null,
    val categoryOverride: MediaCategory? = null
)

data class InstalledExtension(
    val pkgName: String,
    val repoId: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String,
    val ecosystem: ExtensionEcosystem,
    val runtimeType: ExtensionRuntimeType,
    val apkOrArtifactPath: String,
    val iconUrl: String? = null,
    val entryClassOrFile: String? = null,
    val categoryOverride: MediaCategory? = null,
    val installedAt: Long = 0L,
    val hasUpdate: Boolean = false,
    val latestVersionName: String? = null,
    val latestVersionCode: Long? = null,
    val downloadUrl: String? = null
)
