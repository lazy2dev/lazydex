package app.lazydex.ui.settings

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.backup.BackupManager
import app.lazydex.backup.BackupProcessor
import app.lazydex.backup.ImportedBackup
import app.lazydex.data.local.ThemePreferences
import app.lazydex.data.local.ThemePreset
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class StorageStats(
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val coversBytes: Long = 0L
)

data class SettingsUiState(
    val themeMode: String = "DARK",
    val themePreset: ThemePreset = ThemePreset.DEFAULT,
    val amoledMode: Boolean = false,
    val coverTheming: Boolean = false,
    val storageStats: StorageStats = StorageStats(),
    val importedBackup: ImportedBackup? = null,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val errorMsg: String? = null,
    val successMsg: String? = null,
    val showExportCoversDialog: Boolean = false
)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        .coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}

class SettingsViewModel(
    private val repository: MediaRepository,
    private val themePreferences: ThemePreferences,
    private val localCoversDir: File,
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            themePreferences.themeMode.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            themePreferences.themePreset.collect { preset ->
                _uiState.value = _uiState.value.copy(themePreset = preset)
            }
        }
        viewModelScope.launch {
            themePreferences.amoledMode.collect { amoled ->
                _uiState.value = _uiState.value.copy(amoledMode = amoled)
            }
        }
        viewModelScope.launch {
            themePreferences.coverTheming.collect { coverTheme ->
                _uiState.value = _uiState.value.copy(coverTheming = coverTheme)
            }
        }
        refreshStorageMetrics()
    }

    // ==================== THEME PREFERENCES ====================

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun setThemePreset(preset: ThemePreset) {
        viewModelScope.launch {
            themePreferences.setThemePreset(preset)
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setAmoledMode(enabled)
        }
    }

    fun setCoverTheming(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setCoverTheming(enabled)
        }
    }

    // ==================== STORAGE & CACHE CLEANING ====================

    fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun refreshStorageMetrics() {
        viewModelScope.launch {
            val stats = withContext(ioDispatcher) {
                val (total, available) = try {
                    val statFs = StatFs(cacheDir.absolutePath)
                    statFs.totalBytes to statFs.availableBytes
                } catch (_: Throwable) {
                    cacheDir.totalSpace to cacheDir.usableSpace
                }

                val cacheSize = calculateDirectorySize(cacheDir)
                val coversSize = calculateDirectorySize(localCoversDir)

                StorageStats(
                    totalBytes = total,
                    availableBytes = available,
                    cacheBytes = cacheSize,
                    coversBytes = coversSize
                )
            }
            _uiState.value = _uiState.value.copy(storageStats = stats)
        }
    }

    fun clearTemporaryCache() {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    if (cacheDir.exists()) {
                        cacheDir.listFiles()?.forEach { file ->
                            file.deleteRecursively()
                        }
                    }
                }
                refreshStorageMetrics()
                _uiState.value = _uiState.value.copy(successMsg = "Temporary cache cleared")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMsg = "Failed to clear cache: ${e.message}")
            }
        }
    }

    fun cleanOrphanCovers() {
        viewModelScope.launch {
            try {
                val (deletedCount, freedBytes) = withContext(ioDispatcher) {
                    val items = repository.getAll()
                    val activeIds = items.map { it.id }.toSet()
                    val activeCoverFileNames = items.mapNotNull { item ->
                        item.coverImagePath.takeIf { it.isNotBlank() }?.let { File(it).name }
                    }.toSet()
                    val activePaths = items.mapNotNull { it.coverImagePath.takeIf { p -> p.isNotBlank() } }.toSet()
                    val activeCanonicalPaths = items.mapNotNull { item ->
                        item.coverImagePath.takeIf { it.isNotBlank() }?.let {
                            runCatching { File(it).canonicalPath }.getOrNull()
                        }
                    }.toSet()

                    var count = 0
                    var freed = 0L

                    if (localCoversDir.exists() && localCoversDir.isDirectory) {
                        val files = localCoversDir.listFiles() ?: emptyArray()
                        for (file in files) {
                            if (file.isFile) {
                                val canonical = runCatching { file.canonicalPath }.getOrNull()
                                val isReferenced = activeIds.contains(file.name) ||
                                        activeIds.contains(file.nameWithoutExtension) ||
                                        activeCoverFileNames.contains(file.name) ||
                                        activePaths.contains(file.absolutePath) ||
                                        (canonical != null && activeCanonicalPaths.contains(canonical))
                                if (!isReferenced) {
                                    val size = file.length()
                                    if (file.delete()) {
                                        count++
                                        freed += size
                                    }
                                }
                            }
                        }
                    }
                    count to freed
                }
                refreshStorageMetrics()
                if (deletedCount > 0) {
                    _uiState.value = _uiState.value.copy(
                        successMsg = "Cleaned $deletedCount orphaned covers (${formatBytes(freedBytes)} freed)"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        successMsg = "No orphaned covers found"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMsg = "Failed to clean orphan covers: ${e.message}")
            }
        }
    }

    // ==================== BACKUP & RESTORE ====================

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMsg = null, successMsg = null)
    }

    fun showExportCoversDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showExportCoversDialog = show)
    }

    fun exportBackup(context: Context, uri: Uri, includeCovers: Boolean) {
        _uiState.value = _uiState.value.copy(
            isExporting = true,
            errorMsg = null,
            successMsg = null,
            showExportCoversDialog = false
        )
        viewModelScope.launch {
            try {
                val items = repository.getAll()
                BackupManager.export(context, uri, items, includeCovers, localCoversDir)
                refreshStorageMetrics()
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    successMsg = "Backup exported successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    errorMsg = "Export failed: ${e.message}"
                )
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        _uiState.value = _uiState.value.copy(isImporting = true, errorMsg = null, successMsg = null)
        viewModelScope.launch {
            try {
                val imported = BackupManager.readZipContent(context, uri)

                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importedBackup = imported
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    errorMsg = "Import failed: ${e.message}"
                )
            }
        }
    }

    fun cancelImport() {
        val imported = _uiState.value.importedBackup
        if (imported != null) {
            BackupManager.cleanTempDir(imported.tempCoversDir)
        }
        _uiState.value = _uiState.value.copy(importedBackup = null)
    }

    fun executeMerge() {
        val imported = _uiState.value.importedBackup ?: return
        _uiState.value = _uiState.value.copy(isImporting = true)
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    val local = repository.getAll()
                    val mergeResult = BackupProcessor.merge(local, imported.items, imported.schemaVersion)

                    if (!localCoversDir.exists()) localCoversDir.mkdirs()

                    val finalItems = mergeResult.mergedItems.map { mergedItem ->
                        val matchedImported = imported.items.firstOrNull {
                            it.id == mergedItem.id || (it.sourceUrl != null && it.sourceUrl == mergedItem.sourceUrl)
                        }
                        if (matchedImported != null && mergeResult.coverIdsToRestore.contains(matchedImported.id)) {
                            val srcFile = File(imported.tempCoversDir, matchedImported.id)
                            if (srcFile.exists()) {
                                val destFile = File(localCoversDir, mergedItem.id)
                                srcFile.copyTo(destFile, overwrite = true)
                                return@map mergedItem.copy(coverImagePath = destFile.absolutePath)
                            }
                        }
                        mergedItem
                    }

                    repository.replaceAll(finalItems)
                }
                refreshStorageMetrics()

                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importedBackup = null,
                    successMsg = "Backup merged successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importedBackup = null,
                    errorMsg = "Merge failed: ${e.message}"
                )
            } finally {
                withContext(ioDispatcher) {
                    BackupManager.cleanTempDir(imported.tempCoversDir)
                }
            }
        }
    }

    fun executeOverwrite() {
        val imported = _uiState.value.importedBackup ?: return
        _uiState.value = _uiState.value.copy(isImporting = true)
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    if (localCoversDir.exists()) {
                        localCoversDir.deleteRecursively()
                    }
                    localCoversDir.mkdirs()

                    val finalItems = imported.items.map { item ->
                        val srcFile = File(imported.tempCoversDir, item.id)
                        if (srcFile.exists()) {
                            val destFile = File(localCoversDir, item.id)
                            srcFile.copyTo(destFile, overwrite = true)
                            item.copy(coverImagePath = destFile.absolutePath)
                        } else {
                            item
                        }
                    }

                    repository.replaceAll(finalItems)
                }
                refreshStorageMetrics()

                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importedBackup = null,
                    successMsg = "Backup restored successfully (database overwritten)"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importedBackup = null,
                    errorMsg = "Overwrite failed: ${e.message}"
                )
            } finally {
                withContext(ioDispatcher) {
                    BackupManager.cleanTempDir(imported.tempCoversDir)
                }
            }
        }
    }
}
