package app.lazydex.ui.dex

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.data.local.LibraryDisplayMode
import app.lazydex.domain.model.StatusFilter
import app.lazydex.ui.components.DexSettingsSheet
import app.lazydex.ui.components.EmptyState
import app.lazydex.ui.components.MediaCard
import app.lazydex.ui.components.VerticalFastScroller
import app.lazydex.ui.components.VerticalGridFastScroller
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexScreen(
    onNavigateToAddItem: () -> Unit,
    onNavigateToEditItem: (String) -> Unit,
    viewModel: DexViewModel,
    modifier: Modifier = Modifier,
    onSearchClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState()

    val searchFocusRequester = remember { FocusRequester() }

    val activeParent = remember(uiState.topLevelTypes, uiState.selectedTypeId) {
        uiState.topLevelTypes.firstOrNull { it.id == uiState.selectedTypeId }
    }

    LaunchedEffect(searchQuery != null) {
        if (searchQuery != null) {
            searchFocusRequester.requestFocus()
        }
    }

    val isFilterActive = uiState.selectedTypeId != null ||
            uiState.selectedFormatId != null ||
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
                    if (searchQuery != null) {
                        BasicTextField(
                            value = searchQuery.orEmpty(),
                            onValueChange = { viewModel.setSearchQuery(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isNullOrEmpty()) {
                                    Text(
                                        text = "Search dex...",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        )
                    } else {
                        Text(
                            text = "Dex",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    if (searchQuery != null) {
                        IconButton(onClick = { viewModel.setSearchQuery(null) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search"
                            )
                        }
                    }
                },
                actions = {
                    if (searchQuery != null) {
                        if (!searchQuery.isNullOrEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = {
                            if (onSearchClick != null) onSearchClick() else viewModel.setSearchQuery("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter and Sort",
                                tint = if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddItem
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        val listState = rememberLazyListState()
        val gridState = rememberLazyGridState()
        val onCardClick: (String) -> Unit = remember(onNavigateToEditItem) {
            { id -> onNavigateToEditItem(id) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Adaptive Two-Tier Type & Format Rail
            if (uiState.showTypeTabs) {
                // Tier 1: Top-level type pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.selectedTypeId == null,
                        onClick = { viewModel.selectTypeId(null) },
                        label = { Text("All (${uiState.totalCount})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    uiState.topLevelTypes.forEach { typeConfig ->
                        FilterChip(
                            selected = uiState.selectedTypeId == typeConfig.id,
                            onClick = { viewModel.selectTypeId(typeConfig.id) },
                            label = { Text(typeConfig.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // Tier 2: Formats Rail with smooth animation
                AnimatedVisibility(
                    visible = uiState.selectedTypeId != null && uiState.formatsForSelectedType.isNotEmpty(),
                    enter = expandVertically(tween(200)),
                    exit = shrinkVertically(tween(200))
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = uiState.selectedFormatId == null,
                                onClick = { viewModel.selectFormatId(null) },
                                label = { Text("All ${activeParent?.name ?: ""}") }
                            )

                            uiState.formatsForSelectedType.forEach { formatConfig ->
                                FilterChip(
                                    selected = uiState.selectedFormatId == formatConfig.id,
                                    onClick = { viewModel.selectFormatId(formatConfig.id) },
                                    label = { Text(formatConfig.name) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
            }

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
                        val currentQuery = searchQuery?.trim()
                        val isSearchActive = !currentQuery.isNullOrEmpty()
                        val message = when {
                            isSearchActive -> "No items matching '$currentQuery'"
                            isFilterActive -> "No items match your filters"
                            else -> "Nothing here yet. Tap [+] to add your first tracking item."
                        }
                        val actionLabel = when {
                            isSearchActive -> "Clear Search"
                            isFilterActive -> "Clear Filters"
                            else -> null
                        }
                        val actionCallback = when {
                            isSearchActive -> { { viewModel.setSearchQuery("") } }
                            isFilterActive -> { { viewModel.clearFilters() } }
                            else -> null
                        }

                        EmptyState(
                            message = message,
                            actionLabel = actionLabel,
                            onActionClick = actionCallback
                        )
                    }
                    else -> {
                        if (uiState.displayMode == LibraryDisplayMode.LIST) {
                            VerticalFastScroller(
                                listState = listState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(
                                        items = uiState.items,
                                        key = { it.id },
                                        contentType = { uiState.displayMode }
                                    ) { item ->
                                        MediaCard(
                                            item = item,
                                            onClick = { onCardClick(item.id) },
                                            displayMode = uiState.displayMode,
                                            showProgressBadge = uiState.showProgressBadge,
                                            showStatusBadge = uiState.showStatusBadge,
                                            showScoreBadge = uiState.showScoreBadge,
                                            blurAdultCovers = uiState.blurAdultCovers,
                                        )
                                    }
                                }
                            }
                        } else {
                            val gridCells = when {
                                uiState.gridColumns > 0 -> GridCells.Fixed(uiState.gridColumns)
                                uiState.displayMode == LibraryDisplayMode.PANORAMA_COMFORTABLE_GRID -> GridCells.Adaptive(minSize = 160.dp)
                                else -> GridCells.Adaptive(minSize = 128.dp)
                            }
                            val horizontalArrangement = Arrangement.spacedBy(4.dp)
                            val gridContentPadding = PaddingValues(8.dp)

                            VerticalGridFastScroller(
                                state = gridState,
                                columns = gridCells,
                                arrangement = horizontalArrangement,
                                contentPadding = gridContentPadding,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyVerticalGrid(
                                    columns = gridCells,
                                    state = gridState,
                                    contentPadding = gridContentPadding,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalArrangement = horizontalArrangement,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(
                                        items = uiState.items,
                                        key = { it.id },
                                        contentType = { uiState.displayMode }
                                    ) { item ->
                                        MediaCard(
                                            item = item,
                                            onClick = { onCardClick(item.id) },
                                            displayMode = uiState.displayMode,
                                            showProgressBadge = uiState.showProgressBadge,
                                            showStatusBadge = uiState.showStatusBadge,
                                            showScoreBadge = uiState.showScoreBadge,
                                            blurAdultCovers = uiState.blurAdultCovers,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        DexSettingsSheet(
            sheetState = filterSheetState,
            onDismissRequest = {
                coroutineScope.launch { filterSheetState.hide() }.invokeOnCompletion {
                    if (!filterSheetState.isVisible) {
                        showFilterSheet = false
                    }
                }
            },
            selectedType = uiState.selectedType,
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
            onSelectType = { viewModel.selectType(it) },
            onSelectStatus = { viewModel.selectStatus(it) },
            onToggleGenre = { viewModel.toggleGenre(it) },
            onToggleTag = { viewModel.toggleTag(it) },
            onSetAuthorQuery = { viewModel.setAuthorQuery(it) },
            onSetRatingRange = { min, max -> viewModel.setRatingRange(min, max) },
            onSelectSortField = { viewModel.selectSortField(it) },
            onSelectSortDirection = { viewModel.selectSortDirection(it) },
            displayMode = uiState.displayMode,
            onSelectDisplayMode = { viewModel.setDisplayMode(it) },
            gridColumns = uiState.gridColumns,
            onSelectGridColumns = { viewModel.setGridColumns(it) },
            showTypeTabs = uiState.showTypeTabs,
            onToggleTypeTabs = { viewModel.setShowTypeTabs(it) },
            showProgressBadge = uiState.showProgressBadge,
            onToggleProgressBadge = { viewModel.setShowProgressBadge(it) },
            showStatusBadge = uiState.showStatusBadge,
            onToggleStatusBadge = { viewModel.setShowStatusBadge(it) },
            showScoreBadge = uiState.showScoreBadge,
            onToggleScoreBadge = { viewModel.setShowScoreBadge(it) },
            showItemCount = uiState.showItemCount,
            onToggleShowItemCount = { viewModel.setShowItemCount(it) },
            onClearAll = { viewModel.clearFilters() }
        )
    }
}
