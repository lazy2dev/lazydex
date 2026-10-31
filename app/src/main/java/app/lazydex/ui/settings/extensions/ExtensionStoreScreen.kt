@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.lazydex.ui.settings.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.ExtensionEcosystem
import app.lazydex.domain.model.ExtensionPackage
import app.lazydex.domain.model.InstalledExtension
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.SyncStatus
import app.lazydex.ui.components.CategoryBadge
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExtensionStoreScreen(
    onBack: () -> Unit,
    initialAddRepoUrl: String? = null,
    viewModel: ExtensionViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialAddRepoUrl) {
        if (!initialAddRepoUrl.isNullOrBlank()) {
            viewModel.openAddRepoDialog(initialAddRepoUrl)
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshRepos() },
                        enabled = !uiState.isSyncing
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync Repositories")
                        }
                    }
                    IconButton(onClick = { viewModel.openRepoSheet() }) {
                        Icon(Icons.Default.Folder, contentDescription = "Manage Repositories")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.navigationBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = uiState.selectedTab == ExtensionTab.INSTALLED,
                    onClick = { viewModel.selectTab(ExtensionTab.INSTALLED) },
                    text = {
                        Text("Installed (${uiState.installedExtensions.size})")
                    }
                )
                Tab(
                    selected = uiState.selectedTab == ExtensionTab.AVAILABLE,
                    onClick = { viewModel.selectTab(ExtensionTab.AVAILABLE) },
                    text = {
                        Text("Available (${uiState.availableExtensions.size})")
                    }
                )
                Tab(
                    selected = uiState.selectedTab == ExtensionTab.UPDATES,
                    onClick = { viewModel.selectTab(ExtensionTab.UPDATES) },
                    text = {
                        BadgedBox(
                            badge = {
                                if (uiState.updates.isNotEmpty()) {
                                    Badge { Text("${uiState.updates.size}") }
                                }
                            }
                        ) {
                            Text("Updates")
                        }
                    }
                )
            }

            // Search Bar & Filter Chips (on Available / Installed tabs)
            if (uiState.selectedTab != ExtensionTab.UPDATES) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search extensions...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Ecosystem Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExtensionEcosystem.entries.filter { it != ExtensionEcosystem.UNKNOWN }.forEach { eco ->
                        FilterChip(
                            selected = uiState.selectedEcosystem == eco,
                            onClick = { viewModel.selectEcosystem(eco) },
                            label = { Text(eco.displayName) }
                        )
                    }
                }

                // Language Filter Chips
                if (uiState.availableLanguages.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedLanguage == null,
                            onClick = { viewModel.selectLanguage(null) },
                            label = { Text("All Langs") }
                        )
                        uiState.availableLanguages.forEach { lang ->
                            FilterChip(
                                selected = uiState.selectedLanguage == lang,
                                onClick = { viewModel.selectLanguage(lang) },
                                label = { Text(lang.uppercase()) }
                            )
                        }
                    }
                }
            }

            // Tab Content
            when (uiState.selectedTab) {
                ExtensionTab.INSTALLED -> {
                    InstalledExtensionsTab(
                        installed = uiState.installedExtensions.filter {
                            (uiState.selectedEcosystem == null || it.ecosystem == uiState.selectedEcosystem) &&
                                    (uiState.selectedLanguage == null || it.lang.equals(uiState.selectedLanguage, ignoreCase = true)) &&
                                    (uiState.searchQuery.isBlank() || it.name.contains(uiState.searchQuery, ignoreCase = true))
                        },
                        onUninstall = { viewModel.uninstallExtension(it.pkgName, it.name) },
                        onOpenCategoryPicker = { viewModel.openCategoryPicker(it) },
                        onUpdate = { ext ->
                            val pkg = uiState.availableExtensions.firstOrNull { it.pkgName == ext.pkgName }
                            if (pkg != null) viewModel.installExtension(pkg)
                        }
                    )
                }
                ExtensionTab.AVAILABLE -> {
                    AvailableExtensionsTab(
                        available = uiState.availableExtensions.filter {
                            (uiState.selectedEcosystem == null || it.ecosystem == uiState.selectedEcosystem) &&
                                    (uiState.selectedLanguage == null || it.lang.equals(uiState.selectedLanguage, ignoreCase = true)) &&
                                    (uiState.searchQuery.isBlank() || it.name.contains(uiState.searchQuery, ignoreCase = true))
                        },
                        reposCount = uiState.repos.size,
                        onInstall = { viewModel.installExtension(it) },
                        onOpenAddRepo = { viewModel.openAddRepoDialog() }
                    )
                }
                ExtensionTab.UPDATES -> {
                    UpdatesTab(
                        updates = uiState.updates,
                        onUpdate = { viewModel.installExtension(it) },
                        onUpdateAll = {
                            uiState.updates.forEach { viewModel.installExtension(it) }
                        }
                    )
                }
            }
        }
    }

    // Add Repository Dialog
    if (uiState.showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAddRepoDialog() },
            title = { Text("Add Repository") },
            text = {
                Column {
                    Text(
                        "Enter manifest URL or repo base URL. Mihon, Aniyomi, LNReader, Shosetsu, Mangayomi, Tsundoku & Aidoku formats are auto-detected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.addRepoInputUrl,
                        onValueChange = { viewModel.updateAddRepoUrl(it) },
                        label = { Text("Manifest or Base URL") },
                        placeholder = { Text("https://example.com/repo") },
                        singleLine = true,
                        isError = uiState.addRepoError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AnimatedVisibility(uiState.addRepoError != null) {
                        Text(
                            text = uiState.addRepoError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (uiState.isAddingRepo) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Probing & parsing manifest...")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAddRepo() },
                    enabled = !uiState.isAddingRepo && uiState.addRepoInputUrl.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissAddRepoDialog() },
                    enabled = !uiState.isAddingRepo
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Repositories Management BottomSheet
    if (uiState.showRepoSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissRepoSheet() },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Repositories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    FilledTonalButton(onClick = { viewModel.openAddRepoDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.repos.isEmpty()) {
                    Text(
                        "No repositories added yet. Click Add to connect an extension repo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(uiState.repos, key = { it.id }) { repo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(repo.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${repo.ecosystem.displayName} • ${repo.extensionCount} extensions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (repo.syncStatus == SyncStatus.ERROR && repo.syncError != null) {
                                        Text(
                                            repo.syncError,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.removeRepo(repo.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete Repo",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Category Override Picker Dialog
    uiState.categoryPickerExtension?.let { ext ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCategoryPicker() },
            title = { Text("Category for ${ext.name}") },
            text = {
                Column {
                    Text(
                        "Override the default media category for this extension's search & scrape results:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val defaultCategory = ext.ecosystem.defaultCategory
                    val selectedCategory = ext.categoryOverride

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setCategoryOverride(null) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = selectedCategory == null, onClick = { viewModel.setCategoryOverride(null) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto (${defaultCategory.name})")
                    }

                    MediaCategory.entries.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setCategoryOverride(cat) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = selectedCategory == cat, onClick = { viewModel.setCategoryOverride(cat) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissCategoryPicker() }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun InstalledExtensionsTab(
    installed: List<InstalledExtension>,
    onUninstall: (InstalledExtension) -> Unit,
    onOpenCategoryPicker: (InstalledExtension) -> Unit,
    onUpdate: (InstalledExtension) -> Unit,
    modifier: Modifier = Modifier
) {
    if (installed.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No extensions installed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Install extensions from the Available tab",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(installed, key = { it.pkgName }) { ext ->
                ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!ext.iconUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ext.iconUrl,
                                contentDescription = ext.name,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    ext.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(ext.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "v${ext.versionName} (${ext.lang.uppercase()})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onOpenCategoryPicker(ext) }
                                ) {
                                    CategoryBadge(category = ext.categoryOverride ?: ext.ecosystem.defaultCategory)
                                }
                            }
                        }

                        if (ext.hasUpdate) {
                            FilledTonalButton(
                                onClick = { onUpdate(ext) },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Update")
                            }
                        }

                        IconButton(onClick = { onUninstall(ext) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Uninstall",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableExtensionsTab(
    available: List<ExtensionPackage>,
    reposCount: Int,
    onInstall: (ExtensionPackage) -> Unit,
    onOpenAddRepo: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (reposCount == 0) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No Repositories Configured",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Add extension repositories to discover and install community sources alongside built-in sources.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onOpenAddRepo) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Repository")
                }
            }
        }
    } else if (available.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No matching extensions found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(available, key = { it.pkgName }) { pkg ->
                ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!pkg.iconUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = pkg.iconUrl,
                                contentDescription = pkg.name,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    pkg.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(pkg.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${pkg.ecosystem.displayName} • v${pkg.versionName} • ${pkg.lang.uppercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (pkg.isInstalled) {
                            OutlinedButton(
                                onClick = {},
                                enabled = false
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Installed")
                            }
                        } else {
                            Button(onClick = { onInstall(pkg) }) {
                                Text("Install")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesTab(
    updates: List<ExtensionPackage>,
    onUpdate: (ExtensionPackage) -> Unit,
    onUpdateAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (updates.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "All extensions are up to date",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${updates.size} updates available",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onUpdateAll) {
                    Text("Update All")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(updates, key = { it.pkgName }) { pkg ->
                    ElevatedCard(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pkg.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "v${pkg.installedVersionName} -> v${pkg.versionName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Button(onClick = { onUpdate(pkg) }) {
                                Text("Update")
                            }
                        }
                    }
                }
            }
        }
    }
}
