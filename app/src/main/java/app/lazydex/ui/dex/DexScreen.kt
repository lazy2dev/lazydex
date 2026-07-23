package app.lazydex.ui.dex

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.StatusFilter
import app.lazydex.ui.components.EmptyState
import app.lazydex.ui.components.FilterSheet
import app.lazydex.ui.components.GenreChipRow
import app.lazydex.ui.components.MediaCard
import app.lazydex.ui.components.StatusDropdown
import app.lazydex.ui.components.TagChipRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexScreen(
    onNavigateToAddItem: () -> Unit,
    onNavigateToEditItem: (String) -> Unit,
    viewModel: DexViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    val filterSheetState = rememberModalBottomSheetState()

    val isFilterActive = uiState.selectedCategory != null ||
            uiState.selectedStatus != StatusFilter.ALL ||
            uiState.selectedGenres.isNotEmpty() ||
            uiState.selectedTags.isNotEmpty() ||
            uiState.authorQuery.isNotBlank() ||
            uiState.minRating != null ||
            uiState.maxRating != null ||
            uiState.dateRangeStart != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    StatusDropdown(
                        selectedStatus = uiState.selectedStatus,
                        selectedCategory = uiState.selectedCategory,
                        perStatusCounts = uiState.perStatusCounts,
                        onSelectStatus = { viewModel.selectStatus(it) }
                    )
                },
                actions = {
                    IconButton(onClick = { /* Search */ }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter and Sort",
                            tint = if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Genre & Tag chip rows (visible when available)
            if (uiState.availableGenres.isNotEmpty()) {
                GenreChipRow(
                    availableGenres = uiState.availableGenres,
                    selectedGenres = uiState.selectedGenres,
                    onToggleGenre = { viewModel.toggleGenre(it) },
                    onClearGenres = { viewModel.selectGenres(emptySet()) }
                )
            }

            if (uiState.availableTags.isNotEmpty()) {
                TagChipRow(
                    availableTags = uiState.availableTags,
                    selectedTags = uiState.selectedTags,
                    onToggleTag = { viewModel.toggleTag(it) },
                    onClearTags = { viewModel.selectTags(emptySet()) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main List/Grid Content
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator()
                    }
                    uiState.items.isEmpty() -> {
                        val message = if (isFilterActive) {
                            "No items match your filters"
                        } else {
                            "Nothing here yet. Tap [+] to add your first tracking item."
                        }
                        val actionLabel = if (isFilterActive) "Clear Filters" else null
                        val actionCallback = if (isFilterActive) {
                            { viewModel.clearFilters() }
                        } else null

                        EmptyState(
                            message = message,
                            actionLabel = actionLabel,
                            onActionClick = actionCallback
                        )
                    }
                    else -> {
                        if (isGridView) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = uiState.items,
                                    key = { it.id }
                                ) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = { onNavigateToEditItem(item.id) },
                                        isGridView = true
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = uiState.items,
                                    key = { it.id }
                                ) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = { onNavigateToEditItem(item.id) },
                                        isGridView = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            sheetState = filterSheetState,
            onDismissRequest = {
                coroutineScope.launch { filterSheetState.hide() }.invokeOnCompletion {
                    if (!filterSheetState.isVisible) {
                        showFilterSheet = false
                    }
                }
            },
            selectedCategory = uiState.selectedCategory,
            selectedStatus = uiState.selectedStatus,
            selectedGenres = uiState.selectedGenres,
            availableGenres = uiState.availableGenres,
            selectedTags = uiState.selectedTags,
            availableTags = uiState.availableTags,
            authorQuery = uiState.authorQuery,
            minRating = uiState.minRating,
            maxRating = uiState.maxRating,
            sortField = uiState.sortField,
            sortDirection = uiState.sortDirection,
            onSelectCategory = { viewModel.selectCategory(it) },
            onSelectStatus = { viewModel.selectStatus(it) },
            onToggleGenre = { viewModel.toggleGenre(it) },
            onToggleTag = { viewModel.toggleTag(it) },
            onSetAuthorQuery = { viewModel.setAuthorQuery(it) },
            onSetRatingRange = { min, max -> viewModel.setRatingRange(min, max) },
            onSelectSortField = { viewModel.selectSortField(it) },
            onSelectSortDirection = { viewModel.selectSortDirection(it) },
            onClearAll = { viewModel.clearFilters() }
        )
    }
}
