package app.lazydex.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.backup.BackupManager
import app.lazydex.backup.BackupProcessor
import app.lazydex.backup.ImportedBackup
import app.lazydex.data.local.ThemePreferences
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val themeMode: String = "DARK",
    val amoledMode: Boolean = false,
    val importedBackup: ImportedBackup? = null,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val errorMsg: String? = null,
    val successMsg: String? = null,
    val showExportCoversDialog: Boolean = false
)

class SettingsViewModel(
    private val repository: MediaRepository,
    private val themePreferences: ThemePreferences,
    private val localCoversDir: File
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
            themePreferences.amoledMode.collect { amoled ->
                _uiState.value = _uiState.value.copy(amoledMode = amoled)
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setAmoledMode(enabled)
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMsg = null, successMsg = null)
    }

    fun showExportCoversDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showExportCoversDialog = show)
    }

    fun exportBackup(context: Context, uri: Uri, includeCovers: Boolean) {
        _uiState.value = _uiState.value.copy(isExporting = true, errorMsg = null, successMsg = null, showExportCoversDialog = false)
        viewModelScope.launch {
            try {
                val items = repository.getAll()
                BackupManager.export(context, uri, items, includeCovers, localCoversDir)
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
                val imported = BackupManager.import(context, uri)
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
                val local = repository.getAll()
                val mergeResult = BackupProcessor.merge(local, imported.items)
                
                // Save database
                repository.replaceAll(mergeResult.mergedItems)

                // Restore covers
                if (!localCoversDir.exists()) localCoversDir.mkdirs()
                
                // Copy the covers that won or are new
                mergeResult.mergedItems.forEach { mergedItem ->
                    val matchedImported = imported.items.firstOrNull {
                        it.id == mergedItem.id || (it.sourceUrl != null && it.sourceUrl == mergedItem.sourceUrl)
                    }
                    if (matchedImported != null && mergeResult.coverIdsToRestore.contains(matchedImported.id)) {
                        val srcFile = File(imported.tempCoversDir, matchedImported.id)
                        if (srcFile.exists()) {
                            val destFile = File(localCoversDir, mergedItem.id)
                            srcFile.copyTo(destFile, overwrite = true)
                        }
                    }
                }

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
                BackupManager.cleanTempDir(imported.tempCoversDir)
            }
        }
    }

    fun executeOverwrite() {
        val imported = _uiState.value.importedBackup ?: return
        _uiState.value = _uiState.value.copy(isImporting = true)
        viewModelScope.launch {
            try {
                // Save database
                repository.replaceAll(imported.items)

                // Wipe existing covers
                if (localCoversDir.exists()) {
                    localCoversDir.deleteRecursively()
                }
                localCoversDir.mkdirs()

                // Copy all covers from temp folder
                BackupManager.restoreAllCovers(imported.tempCoversDir, localCoversDir)

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
                BackupManager.cleanTempDir(imported.tempCoversDir)
            }
        }
    }
}
