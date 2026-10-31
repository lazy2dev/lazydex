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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.data.local.LibraryDisplayMode
import app.lazydex.domain.model.Category
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.StatusFilter
import app.lazydex.ui.components.CategoryCreatorSheet
import app.lazydex.ui.components.DexSettingsSheet
import app.lazydex.ui.components.EmptyState
import app.lazydex.ui.components.MediaCard
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
    val coroutineScope = rememberCoroutineScope()

    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState()

    var showCategoryCreatorSheet by rememberSaveable { mutableStateOf(false) }
    var categoryToCreateTypeUnder by remember { mutableStateOf<Category?>(null) }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val displayedItems = remember(uiState.items, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            uiState.items
        } else {
            uiState.items.filter { item ->
                item.title.contains(query, ignoreCase = true) ||
                        (item.author.contains(query, ignoreCase = true))
            }
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
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search dex...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {}),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
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
                    if (isSearching) {
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    }
                },
                actions = {
                    if (isSearching) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        IconButton(onClick = {
                            if (onSearchClick != null) onSearchClick() else isSearching = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddItem,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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

                    AssistChip(
                        onClick = {
                            categoryToCreateTypeUnder = null
                            showCategoryCreatorSheet = true
                        },
                        label = { Text("+ Category") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                // Tier 2: Subtypes Rail with smooth animation
                AnimatedVisibility(
                    visible = uiState.selectedCategoryId != null && uiState.subtypesForSelectedCategory.isNotEmpty(),
                    enter = expandVertically(tween(200)),
                    exit = shrinkVertically(tween(200))
                ) {
                    val activeParent = uiState.topLevelCategories.firstOrNull { it.id == uiState.selectedCategoryId }
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

                            AssistChip(
                                onClick = {
                                    categoryToCreateTypeUnder = activeParent
                                    showCategoryCreatorSheet = true
                                },
                                label = { Text("+ Type") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
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
                    displayedItems.isEmpty() -> {
                        val message = when {
                            searchQuery.isNotBlank() -> "No items matching '$searchQuery'"
                            isFilterActive -> "No items match your filters"
                            else -> "Nothing here yet. Tap [+] to add your first tracking item."
                        }
                        val actionLabel = when {
                            searchQuery.isNotBlank() -> "Clear Search"
                            isFilterActive -> "Clear Filters"
                            else -> null
                        }
                        val actionCallback = when {
                            searchQuery.isNotBlank() -> { { searchQuery = "" } }
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
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = displayedItems,
                                    key = { it.id }
                                ) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = { onNavigateToEditItem(item.id) },
                                        displayMode = uiState.displayMode,
                                        showProgressBadge = uiState.showProgressBadge,
                                        showStatusBadge = uiState.showStatusBadge,
                                        showScoreBadge = uiState.showScoreBadge,
                                    )
                                }
                            }
                        } else {
                            val gridCells = when {
                                uiState.gridColumns > 0 -> GridCells.Fixed(uiState.gridColumns)
                                uiState.displayMode == LibraryDisplayMode.PANORAMA_COMFORTABLE_GRID -> GridCells.Adaptive(minSize = 160.dp)
                                else -> GridCells.Adaptive(minSize = 128.dp)
                            }

                            LazyVerticalGrid(
                                columns = gridCells,
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = displayedItems,
                                    key = { it.id }
                                ) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = { onNavigateToEditItem(item.id) },
                                        displayMode = uiState.displayMode,
                                        showProgressBadge = uiState.showProgressBadge,
                                        showStatusBadge = uiState.showStatusBadge,
                                        showScoreBadge = uiState.showScoreBadge,
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
