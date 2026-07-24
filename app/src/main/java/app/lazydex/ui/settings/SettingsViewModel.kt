package app.lazydex.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.backup.BackupManager
import app.lazydex.backup.BackupProcessor
import app.lazydex.backup.ImportedBackup
import app.lazydex.data.anilist.AnilistSyncManager
import app.lazydex.data.anilist.AnilistTokenStore
import app.lazydex.data.anilist.model.ScoreFormat
import app.lazydex.data.local.ThemePreferences
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

import app.lazydex.data.anilist.AnilistSyncReport
import app.lazydex.data.anilist.SyncProgressState

data class SettingsUiState(
    val themeMode: String = "DARK",
    val amoledMode: Boolean = false,
    val coverTheming: Boolean = false,
    val importedBackup: ImportedBackup? = null,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val errorMsg: String? = null,
    val successMsg: String? = null,
    val showExportCoversDialog: Boolean = false,

    // AniList Sync State
    val isAnilistLoggedIn: Boolean = false,
    val anilistUsername: String? = null,
    val scoreFormat: ScoreFormat = ScoreFormat.POINT_5,
    val isSyncing: Boolean = false,
    val syncProgressState: SyncProgressState = SyncProgressState.Idle,
    val syncReport: AnilistSyncReport? = null,
    val pendingResolutionItems: List<MediaItemEntity> = emptyList()
)

class SettingsViewModel(
    private val repository: MediaRepository,
    private val themePreferences: ThemePreferences,
    private val localCoversDir: File,
    private val tokenStore: AnilistTokenStore,
    private val syncManager: AnilistSyncManager,
    private val dao: MediaItemDao
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
        viewModelScope.launch {
            themePreferences.coverTheming.collect { coverTheme ->
                _uiState.value = _uiState.value.copy(coverTheming = coverTheme)
            }
        }
        viewModelScope.launch {
            dao.getPendingResolutionItems().collect { items ->
                _uiState.value = _uiState.value.copy(pendingResolutionItems = items)
            }
        }
        refreshAnilistState()
    }

    fun refreshAnilistState() {
        viewModelScope.launch {
            val loggedIn = tokenStore.isLoggedIn()
            val username = tokenStore.getUsername()
            val format = tokenStore.getScoreFormat()
            _uiState.value = _uiState.value.copy(
                isAnilistLoggedIn = loggedIn,
                anilistUsername = username,
                scoreFormat = format
            )
        }
    }

    fun initiateAnilistAuth(context: Context) {
        viewModelScope.launch {
            val authUrl = "https://anilist.co/api/v2/oauth/authorize?client_id=${AnilistTokenStore.DEFAULT_CLIENT_ID}&response_type=token"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun logoutAnilist() {
        viewModelScope.launch {
            tokenStore.clearToken()
            refreshAnilistState()
            _uiState.value = _uiState.value.copy(
                successMsg = "Disconnected from AniList",
                syncProgressState = SyncProgressState.Idle,
                syncReport = null
            )
        }
    }

    fun performManualSync() {
        _uiState.value = _uiState.value.copy(
            isSyncing = true,
            errorMsg = null,
            successMsg = null,
            syncProgressState = SyncProgressState.Fetching("Initializing", 0, 0)
        )
        viewModelScope.launch {
            try {
                syncManager.performFullSyncFlow().collect { state ->
                    _uiState.value = _uiState.value.copy(syncProgressState = state)
                    when (state) {
                        is SyncProgressState.Completed -> {
                            val msg = "AniList sync completed: ${state.report.importedCount} imported, ${state.report.updatedCount} updated"
                            _uiState.value = _uiState.value.copy(
                                isSyncing = false,
                                syncReport = state.report,
                                successMsg = msg
                            )
                        }
                        is SyncProgressState.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isSyncing = false,
                                errorMsg = state.message
                            )
                        }
                        else -> {
                            _uiState.value = _uiState.value.copy(isSyncing = true)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncProgressState = SyncProgressState.Error(e.message ?: "Sync error"),
                    errorMsg = "Sync failed: ${e.message}"
                )
            }
        }
    }

    fun dismissSyncReport() {
        _uiState.value = _uiState.value.copy(syncReport = null)
    }

    fun setScoreFormat(format: ScoreFormat) {
        viewModelScope.launch {
            tokenStore.saveScoreFormat(format)
            _uiState.value = _uiState.value.copy(scoreFormat = format)
        }
    }

    fun resolveRemoteDeletion(itemId: String, deleteLocally: Boolean) {
        viewModelScope.launch {
            if (deleteLocally) {
                repository.delete(itemId)
            } else {
                val entity = dao.getById(itemId)
                if (entity != null) {
                    val updated = entity.copy(
                        anilistListEntryId = null,
                        syncPendingAction = null
                    )
                    dao.upsert(updated)
                }
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

    fun setCoverTheming(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setCoverTheming(enabled)
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
