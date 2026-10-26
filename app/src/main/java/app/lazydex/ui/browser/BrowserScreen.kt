package app.lazydex.ui.browser

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.scraper.source.Source
import app.lazydex.ui.browser.components.BaseSourceItem
import app.lazydex.ui.browser.components.SourceCoverGridItem
import app.lazydex.ui.components.color
import app.lazydex.ui.components.icon
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onNavigateToCreateFromUrl: (String) -> Unit,
    onNavigateToManualAdd: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val selectedSource = uiState.selectedSource

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedSource?.name ?: "Add & Browse",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (selectedSource != null) {
                        IconButton(onClick = { viewModel.selectSource(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to directory")
                        }
                    }
                },
                actions = {
                    if (selectedSource != null && selectedSource.baseUrl.isNotBlank()) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(selectedSource.baseUrl))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in browser")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        if (selectedSource != null) {
            // Single Source Browse View
            SingleSourceBrowseView(
                source = selectedSource,
                uiState = uiState,
                onSearchQueryChanged = { viewModel.updateSearchQuery(it) },
                onSearch = { viewModel.performSearch() },
                onClearSearch = { viewModel.clearSearch() },
                onItemClick = { onNavigateToCreateFromUrl(it.url) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            // Main Unified Add & Browse Directory View
            MainBrowseView(
                uiState = uiState,
                onCategorySelected = { viewModel.setCategory(it) },
                onSearchQueryChanged = { viewModel.updateSearchQuery(it) },
                onSearch = { viewModel.performSearch() },
                onClearSearch = { viewModel.clearSearch() },
                onPasteClipboard = {
                    val clip = clipboardManager.getText()?.text
                    if (!clip.isNullOrBlank()) {
                        viewModel.updateSearchQuery(clip)
                    }
                },
                onTogglePin = { viewModel.togglePin(it) },
                onSelectSource = { viewModel.selectSource(it) },
                onNavigateToCreateFromUrl = onNavigateToCreateFromUrl,
                onNavigateToManualAdd = onNavigateToManualAdd,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Composable
private fun MainBrowseView(
    uiState: BrowserUiState,
    onCategorySelected: (MediaCategory?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onPasteClipboard: () -> Unit,
    onTogglePin: (String) -> Unit,
    onSelectSource: (Source) -> Unit,
    onNavigateToCreateFromUrl: (String) -> Unit,
    onNavigateToManualAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        null to "All",
        MediaCategory.NOVEL to "Novels",
        MediaCategory.MANGA to "Manga",
        MediaCategory.ANIME to "Anime",
        MediaCategory.GAME to "Games",
        MediaCategory.MOVIE to "Movies",
        MediaCategory.TV to "TV"
    )

    val selectedCategory = uiState.selectedCategory
    val categoryHasNoScrapers = selectedCategory in listOf(MediaCategory.GAME, MediaCategory.MOVIE, MediaCategory.TV)
    val detectedSource = uiState.detectedSourceForUrl

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Type / Category Filter Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { (cat, label) ->
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategorySelected(if (isSelected && cat != null) null else cat) },
                        label = { Text(label) },
                        leadingIcon = if (cat != null) {
                            {
                                Icon(
                                    imageVector = cat.icon(),
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else cat.color(),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }

        // 2. Unified Search & URL Input Bar
        item {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = {
                    Text(
                        if (categoryHasNoScrapers) "Enter title to add manually..."
                        else "Search title or paste URL..."
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear input")
                            }
                        } else {
                            IconButton(onClick = onPasteClipboard) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // 3. Contextual Quick Actions (URL detected or No Scrapers)
        if (categoryHasNoScrapers) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable(onClick = onNavigateToManualAdd)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        val cat = selectedCategory!!
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cat.color().copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = cat.icon(),
                                contentDescription = null,
                                tint = cat.color(),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Create ${cat.name.lowercase().replaceFirstChar { it.uppercase() }} Manually",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Offline entry — no web scrapers required",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        } else if (detectedSource != null) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onNavigateToCreateFromUrl(uiState.searchQuery.trim()) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        val iconVector = detectedSource.categoryHint?.icon() ?: Icons.Default.Search
                        val iconColor = detectedSource.categoryHint?.color() ?: MaterialTheme.colorScheme.primary

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(iconColor.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Import from ${detectedSource.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Detected source link. Tap to auto-fetch details.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // 4. Progress or Error indicator
        if (uiState.isSearching) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Searching online sources...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (uiState.searchError != null) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Search error: ${uiState.searchError}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 5. Active Search Results Grid
        if (uiState.hasSearched && !uiState.isSearching) {
            if (uiState.searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = "RESULTS (${uiState.searchResults.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
                    )
                }

                // 3-column chunked row items
                items(uiState.searchResults.chunked(3)) { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        for (result in rowItems) {
                            SourceCoverGridItem(
                                result = result,
                                isTracked = uiState.isTracked(result),
                                onClick = { onNavigateToCreateFromUrl(result.url) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // 0 Results fallback -> Add Manually
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 36.dp)
                    ) {
                        Text(
                            text = "No online results found for \"${uiState.searchQuery}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToManualAdd,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add \"${uiState.searchQuery.take(20)}\" Manually")
                        }
                    }
                }
            }
        }

        // 6. Komikku Source Directory (visible when not viewing search results)
        if (!uiState.hasSearched) {
            val allSources = if (selectedCategory != null) {
                uiState.sources.filter { it.categoryHint == selectedCategory || it.categoryHint == null }
            } else {
                uiState.sources
            }

            val pinnedSources = allSources.filter { uiState.pinnedSourceIds.contains(it.id) }
            val otherSources = allSources.filter { !uiState.pinnedSourceIds.contains(it.id) }

            // Pinned Section
            if (pinnedSources.isNotEmpty()) {
                item {
                    Text(
                        text = "PINNED",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                items(pinnedSources, key = { "pinned_${it.id}" }) { source ->
                    BaseSourceItem(
                        source = source,
                        isPinned = true,
                        onTogglePin = { onTogglePin(source.id) },
                        onClick = { onSelectSource(source) }
                    )
                }
            }

            // Other Sources Grouped by Category
            if (otherSources.isNotEmpty()) {
                if (pinnedSources.isNotEmpty()) {
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                item {
                    Text(
                        text = "SOURCES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                    )
                }

                items(otherSources, key = { "source_${it.id}" }) { source ->
                    BaseSourceItem(
                        source = source,
                        isPinned = false,
                        onTogglePin = { onTogglePin(source.id) },
                        onClick = { onSelectSource(source) }
                    )
                }
            }

            if (allSources.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No sources installed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add extension repositories in Settings -> Extensions to discover and install sources.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleSourceBrowseView(
    source: Source,
    uiState: BrowserUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onItemClick: (app.lazydex.scraper.source.SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("Search ${source.name}...") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (uiState.isSearching) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.searchError != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = uiState.searchError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (uiState.searchResults.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.searchResults) { result ->
                    SourceCoverGridItem(
                        result = result,
                        isTracked = uiState.isTracked(result),
                        onClick = { onItemClick(result) }
                    )
                }
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                Text(
                    text = if (uiState.hasSearched) "No results found" else "Type a search query and press Search",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
