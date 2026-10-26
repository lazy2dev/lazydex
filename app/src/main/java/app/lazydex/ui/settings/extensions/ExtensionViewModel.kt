package app.lazydex.ui.settings.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lazydex.domain.model.ExtensionEcosystem
import app.lazydex.domain.model.ExtensionPackage
import app.lazydex.domain.model.ExtensionRepo
import app.lazydex.domain.model.InstalledExtension
import app.lazydex.domain.model.MediaCategory
import app.lazydex.extension.repository.ExtensionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExtensionTab {
    INSTALLED,
    AVAILABLE,
    UPDATES
}

data class ExtensionUiState(
    val selectedTab: ExtensionTab = ExtensionTab.INSTALLED,
    val installedExtensions: List<InstalledExtension> = emptyList(),
    val availableExtensions: List<ExtensionPackage> = emptyList(),
    val updates: List<ExtensionPackage> = emptyList(),
    val repos: List<ExtensionRepo> = emptyList(),
    val searchQuery: String = "",
    val selectedLanguage: String? = null,
    val selectedEcosystem: ExtensionEcosystem? = null,
    val availableLanguages: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val showAddRepoDialog: Boolean = false,
    val addRepoInputUrl: String = "",
    val addRepoError: String? = null,
    val isAddingRepo: Boolean = false,
    val categoryPickerExtension: InstalledExtension? = null,
    val showRepoSheet: Boolean = false,
    val userMessage: String? = null
)

class ExtensionViewModel(
    private val extensionRepository: ExtensionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtensionUiState())
    val uiState: StateFlow<ExtensionUiState> = _uiState.asStateFlow()

    init {
        // Collect installed extensions
        viewModelScope.launch {
            extensionRepository.getInstalledExtensionsFlow().collect { installed ->
                _uiState.update { state ->
                    val langs = (installed.map { it.lang } + state.availableExtensions.map { it.lang })
                        .distinct()
                        .filter { it.isNotBlank() }
                        .sorted()
                    state.copy(installedExtensions = installed, availableLanguages = langs)
                }
            }
        }

        // Collect available extensions
        viewModelScope.launch {
            extensionRepository.getAvailableExtensionsFlow().collect { packages ->
                _uiState.update { state ->
                    val langs = (state.installedExtensions.map { it.lang } + packages.map { it.lang })
                        .distinct()
                        .filter { it.isNotBlank() }
                        .sorted()
                    state.copy(
                        availableExtensions = packages,
                        updates = packages.filter { it.isInstalled && it.hasUpdate },
                        availableLanguages = langs
                    )
                }
            }
        }

        // Collect repos
        viewModelScope.launch {
            extensionRepository.getReposFlow().collect { repos ->
                _uiState.update { state ->
                    state.copy(repos = repos)
                }
            }
        }

        // Auto-refresh repos on startup if >24 hours
        viewModelScope.launch {
            extensionRepository.refreshRepos(force = false)
        }
    }

    fun selectTab(tab: ExtensionTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectLanguage(lang: String?) {
        _uiState.update { it.copy(selectedLanguage = if (it.selectedLanguage == lang) null else lang) }
    }

    fun selectEcosystem(eco: ExtensionEcosystem?) {
        _uiState.update { it.copy(selectedEcosystem = if (it.selectedEcosystem == eco) null else eco) }
    }

    fun openAddRepoDialog(initialUrl: String? = null) {
        _uiState.update {
            it.copy(
                showAddRepoDialog = true,
                addRepoInputUrl = initialUrl ?: "",
                addRepoError = null
            )
        }
    }

    fun dismissAddRepoDialog() {
        _uiState.update { it.copy(showAddRepoDialog = false, addRepoError = null, isAddingRepo = false) }
    }

    fun updateAddRepoUrl(url: String) {
        _uiState.update { it.copy(addRepoInputUrl = url, addRepoError = null) }
    }

    fun confirmAddRepo() {
        val url = _uiState.value.addRepoInputUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(addRepoError = "URL cannot be empty") }
            return
        }

        _uiState.update { it.copy(isAddingRepo = true, addRepoError = null) }
        viewModelScope.launch {
            val result = extensionRepository.addRepo(url)
            result.onSuccess { repo ->
                _uiState.update {
                    it.copy(
                        showAddRepoDialog = false,
                        isAddingRepo = false,
                        addRepoInputUrl = "",
                        userMessage = "Added ${repo.name}"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAddingRepo = false,
                        addRepoError = error.message ?: "Failed to add repository"
                    )
                }
            }
        }
    }

    fun removeRepo(repoId: String) {
        viewModelScope.launch {
            extensionRepository.removeRepo(repoId)
            _uiState.update { it.copy(userMessage = "Repository removed") }
        }
    }

    fun refreshRepos() {
        _uiState.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            extensionRepository.refreshRepos(force = true)
            _uiState.update { it.copy(isSyncing = false, userMessage = "Repositories synced") }
        }
    }

    fun installExtension(pkg: ExtensionPackage) {
        viewModelScope.launch {
            val result = extensionRepository.installExtension(pkg)
            result.onSuccess {
                _uiState.update { it.copy(userMessage = "Installed ${pkg.name}") }
            }.onFailure { error ->
                _uiState.update { it.copy(userMessage = "Failed to install ${pkg.name}: ${error.message}") }
            }
        }
    }

    fun uninstallExtension(pkgName: String, name: String) {
        viewModelScope.launch {
            val result = extensionRepository.uninstallExtension(pkgName)
            result.onSuccess {
                _uiState.update { it.copy(userMessage = "Uninstalled $name") }
            }.onFailure { error ->
                _uiState.update { it.copy(userMessage = "Failed to uninstall $name: ${error.message}") }
            }
        }
    }

    fun openCategoryPicker(ext: InstalledExtension) {
        _uiState.update { it.copy(categoryPickerExtension = ext) }
    }

    fun dismissCategoryPicker() {
        _uiState.update { it.copy(categoryPickerExtension = null) }
    }

    fun setCategoryOverride(category: MediaCategory?) {
        val ext = _uiState.value.categoryPickerExtension ?: return
        viewModelScope.launch {
            extensionRepository.setCategoryOverride(ext.pkgName, category)
            _uiState.update { it.copy(categoryPickerExtension = null, userMessage = "Updated category override") }
        }
    }

    fun openRepoSheet() {
        _uiState.update { it.copy(showRepoSheet = true) }
    }

    fun dismissRepoSheet() {
        _uiState.update { it.copy(showRepoSheet = false) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
