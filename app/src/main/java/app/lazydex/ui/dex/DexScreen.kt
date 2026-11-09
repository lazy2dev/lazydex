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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import app.lazydex.domain.model.Category
import app.lazydex.domain.model.StatusFilter
import app.lazydex.ui.components.CategoryCreatorSheet
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

    var showCategoryCreatorSheet by rememberSaveable { mutableStateOf(false) }
    var categoryToCreateTypeUnder by remember { mutableStateOf<Category?>(null) }
    var showOverflowMenu by rememberSaveable { mutableStateOf(false) }

    val searchFocusRequester = remember { FocusRequester() }

    val activeParent = remember(uiState.topLevelCategories, uiState.selectedCategoryId) {
        uiState.topLevelCategories.firstOrNull { it.id == uiState.selectedCategoryId }
    }

    LaunchedEffect(searchQuery != null) {
        if (searchQuery != null) {
            searchFocusRequester.requestFocus()
        }
    }

    val isFilterActive = uiState.selectedCategoryId != null ||
            uiState.selectedSubtypeId != null ||
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
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Type") },
                                    onClick = {
                                        showOverflowMenu = false
                                        categoryToCreateTypeUnder = null
                                        showCategoryCreatorSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("New Format") },
                                    onClick = {
                                        showOverflowMenu = false
                                        categoryToCreateTypeUnder = activeParent
                                        showCategoryCreatorSheet = true
                                    }
                                )
                            }
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
            // Adaptive Two-Tier Category Rail
            if (uiState.showCategoryTabs) {
                // Tier 1: Top-level category pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.selectedCategoryId == null,
                        onClick = { viewModel.selectCategoryId(null) },
                        label = { Text("All (${uiState.totalCount})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    uiState.topLevelCategories.forEach { cat ->
                        FilterChip(
                            selected = uiState.selectedCategoryId == cat.id,
                            onClick = { viewModel.selectCategoryId(cat.id) },
                            label = { Text(cat.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // Tier 2: Subtypes Rail with smooth animation
                AnimatedVisibility(
                    visible = uiState.selectedCategoryId != null && uiState.subtypesForSelectedCategory.isNotEmpty(),
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
                                selected = uiState.selectedSubtypeId == null,
                                onClick = { viewModel.selectSubtypeId(null) },
                                label = { Text("All ${activeParent?.name ?: ""}") }
                            )

                            uiState.subtypesForSelectedCategory.forEach { sub ->
                                FilterChip(
                                    selected = uiState.selectedSubtypeId == sub.id,
                                    onClick = { viewModel.selectSubtypeId(sub.id) },
                                    label = { Text(sub.name) }
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
            displayMode = uiState.displayMode,
            onSelectDisplayMode = { viewModel.setDisplayMode(it) },
            gridColumns = uiState.gridColumns,
            onSelectGridColumns = { viewModel.setGridColumns(it) },
            showCategoryTabs = uiState.showCategoryTabs,
            onToggleCategoryTabs = { viewModel.setShowCategoryTabs(it) },
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

    if (showCategoryCreatorSheet) {
        CategoryCreatorSheet(
            parentCategory = categoryToCreateTypeUnder,
            onDismiss = {
                showCategoryCreatorSheet = false
                categoryToCreateTypeUnder = null
            },
            onSaveCategory = { cat ->
                viewModel.createCategory(cat)
            }
        )
    }
}
