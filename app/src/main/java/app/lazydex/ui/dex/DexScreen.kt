package app.lazydex.ui.dex

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.data.local.LibraryDisplayMode
import app.lazydex.data.local.LibraryGroupMode
import app.lazydex.domain.model.StatusFilter
import app.lazydex.ui.components.ChangeStatusDialog
import app.lazydex.ui.components.DexBottomActionMenu
import app.lazydex.ui.components.DexSettingsSheet
import app.lazydex.ui.components.EmptyState
import app.lazydex.ui.components.MediaCard
import app.lazydex.ui.components.VerticalFastScroller
import app.lazydex.ui.components.VerticalGridFastScroller
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import app.lazydex.ui.components.DexTypeTabs
import app.lazydex.ui.dex.DexViewModel.Companion.getItemsForType
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
    var showQuickFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showChangeStatusDialog by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val filterSheetState = rememberModalBottomSheetState()

    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    val searchFocusRequester = remember { FocusRequester() }

    val activeParent = remember(uiState.topLevelTypes, uiState.selectedTypeId) {
        uiState.topLevelTypes.firstOrNull { it.id == uiState.selectedTypeId }
    }

    LaunchedEffect(searchQuery != null) {
        if (searchQuery != null) {
            searchFocusRequester.requestFocus()
        }
    }

    val isFilterActive = uiState.selectedFormatId != null ||
            uiState.selectedStatus != StatusFilter.ALL ||
            uiState.selectedGenres.isNotEmpty() ||
            uiState.selectedTags.isNotEmpty() ||
            uiState.authorQuery.isNotBlank() ||
            uiState.minRating != null ||
            uiState.maxRating != null ||
            uiState.dateRangeStart != null

    val filterPillText = remember(
        uiState.groupMode,
        uiState.selectedStatus,
        uiState.perStatusCounts,
        uiState.totalCount,
        uiState.selectedTypeId,
        uiState.selectedFormatId,
        uiState.formatsForSelectedType,
        uiState.items
    ) {
        val tabItems = getItemsForType(uiState.items, uiState.selectedTypeId)
        when (uiState.groupMode) {
            LibraryGroupMode.BY_STATUS -> {
                val count = if (uiState.selectedStatus == StatusFilter.ALL) {
                    uiState.perStatusCounts[StatusFilter.ALL] ?: tabItems.size
                } else {
                    uiState.perStatusCounts[uiState.selectedStatus] ?: 0
                }
                "${uiState.selectedStatus.displayName} $count"
            }
            LibraryGroupMode.BY_DEFAULT -> {
                val formatName = uiState.formatsForSelectedType.firstOrNull { it.id == uiState.selectedFormatId }?.name
                val label = formatName ?: "All"
                val count = if (uiState.selectedFormatId != null) {
                    tabItems.count { it.format.equals(uiState.selectedFormatId, ignoreCase = true) || it.typeId.equals(uiState.selectedFormatId, ignoreCase = true) }
                } else {
                    tabItems.size
                }
                "$label $count"
            }
            LibraryGroupMode.UNGROUPED -> {
                "All ${tabItems.size}"
            }
        }
    }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${uiState.selectedIds.size}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close selection mode"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(
                                imageVector = Icons.Outlined.SelectAll,
                                contentDescription = "Select all"
                            )
                        }
                        IconButton(onClick = { viewModel.invertSelection() }) {
                            Icon(
                                imageVector = Icons.Outlined.FlipToBack,
                                contentDescription = "Invert selection"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            } else {
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
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                Surface(
                    onClick = { showQuickFilterSheet = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = filterPillText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
            }
        },
        bottomBar = {
            DexBottomActionMenu(
                visible = uiState.isSelectionMode,
                onChangeStatusClicked = { showChangeStatusDialog = true }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        val onCardClick: (String) -> Unit = remember(onNavigateToEditItem) {
            { id -> onNavigateToEditItem(id) }
        }
        val onItemClick: (String) -> Unit = { id ->
            if (uiState.isSelectionMode) {
                viewModel.toggleSelection(id)
            } else {
                onCardClick(id)
            }
        }
        val onItemLongClick: (String) -> Unit = { id ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (uiState.isSelectionMode) {
                viewModel.toggleRangeSelection(id)
            } else {
                viewModel.toggleSelection(id)
            }
        }

        val tabs = uiState.topLevelTypes
        val initialTabIndex = remember(tabs, uiState.selectedTypeId) {
            if (uiState.selectedTypeId != null) {
                tabs.indexOfFirst { it.id == uiState.selectedTypeId }.takeIf { it != -1 } ?: 0
            } else {
                0
            }
        }
        val pagerState = rememberPagerState(
            initialPage = initialTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        ) { tabs.size }

        // Initial sync when tabs are loaded (matches Komikku LaunchedEffect(categories))
        LaunchedEffect(tabs) {
            if (tabs.isNotEmpty()) {
                val targetIndex = if (uiState.selectedTypeId != null) {
                    tabs.indexOfFirst { it.id == uiState.selectedTypeId }.takeIf { it != -1 } ?: 0
                } else {
                    0
                }
                if (targetIndex != pagerState.currentPage) {
                    pagerState.scrollToPage(targetIndex)
                }
            }
        }

        // Notify ViewModel when active page changes (unidirectional, never cancels running scroll animation)
        LaunchedEffect(pagerState.currentPage, tabs) {
            if (tabs.isNotEmpty()) {
                val activeTab = tabs.getOrNull(pagerState.currentPage)
                if (activeTab != null && activeTab.id != uiState.selectedTypeId) {
                    viewModel.selectTypeId(activeTab.id)
                }
            }
        }

        val itemsByTabId = remember(uiState.items, tabs) {
            tabs.associate { tab ->
                tab.id to getItemsForType(uiState.items, tab.id)
            }
        }

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.totalCount == 0 && searchQuery.isNullOrEmpty() && !isFilterActive -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        message = "Nothing here yet. Tap [+] to add your first tracking item.",
                        actionLabel = null,
                        onActionClick = null
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (uiState.showTypeTabs || !searchQuery.isNullOrEmpty()) {
                        DexTypeTabs(
                            tabs = tabs,
                            selectedIndex = pagerState.currentPage,
                            getItemCount = { tab ->
                                if (uiState.showItemCount || !searchQuery.isNullOrEmpty()) {
                                    itemsByTabId[tab.id]?.size ?: 0
                                } else {
                                    null
                                }
                            },
                            onTabItemClick = { index ->
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Top
                    ) { page ->
                        if (page !in ((pagerState.currentPage - 1)..(pagerState.currentPage + 1))) {
                            return@HorizontalPager
                        }
                        val tab = tabs[page]
                        val itemsForPage = itemsByTabId[tab.id].orEmpty()

                        val pageListState = rememberSaveable(tab.id, saver = LazyListState.Saver) {
                            LazyListState()
                        }
                        val pageGridState = rememberSaveable(tab.id, saver = LazyGridState.Saver) {
                            LazyGridState()
                        }

                        if (itemsForPage.isEmpty()) {
                            val currentQuery = searchQuery?.trim()
                            val isSearchActive = !currentQuery.isNullOrEmpty()
                            val message = when {
                                isSearchActive -> "No ${tab.name} matching '$currentQuery'"
                                isFilterActive -> "No ${tab.name} match your filters"
                                else -> "No ${tab.name} in your library yet."
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
                        } else {
                            if (uiState.displayMode == LibraryDisplayMode.LIST) {
                                VerticalFastScroller(
                                    listState = pageListState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    LazyColumn(
                                        state = pageListState,
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(
                                            items = itemsForPage,
                                            key = { it.id },
                                            contentType = { uiState.displayMode }
                                        ) { item ->
                                            MediaCard(
                                                item = item,
                                                onClick = { onItemClick(item.id) },
                                                onLongClick = { onItemLongClick(item.id) },
                                                isSelected = item.id in uiState.selectedIds,
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
                                    state = pageGridState,
                                    columns = gridCells,
                                    arrangement = horizontalArrangement,
                                    contentPadding = gridContentPadding,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    LazyVerticalGrid(
                                        columns = gridCells,
                                        state = pageGridState,
                                        contentPadding = gridContentPadding,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        horizontalArrangement = horizontalArrangement,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(
                                            items = itemsForPage,
                                            key = { it.id },
                                            contentType = { uiState.displayMode }
                                        ) { item ->
                                            MediaCard(
                                                item = item,
                                                onClick = { onItemClick(item.id) },
                                                onLongClick = { onItemLongClick(item.id) },
                                                isSelected = item.id in uiState.selectedIds,
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
            groupMode = uiState.groupMode,
            onSelectGroupMode = { viewModel.setGroupMode(it) },
            onClearAll = { viewModel.clearFilters() }
        )
    }

    if (showChangeStatusDialog) {
        ChangeStatusDialog(
            onConfirm = { status ->
                viewModel.updateStatusForSelected(status)
                showChangeStatusDialog = false
            },
            onDismissRequest = { showChangeStatusDialog = false }
        )
    }

    if (showQuickFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickFilterSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = when (uiState.groupMode) {
                        LibraryGroupMode.BY_STATUS -> "Filter by Status"
                        LibraryGroupMode.BY_DEFAULT -> "Filter by Format"
                        LibraryGroupMode.UNGROUPED -> "Library"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                )

                val quickFilterTabItems = getItemsForType(uiState.items, uiState.selectedTypeId)
                when (uiState.groupMode) {
                    LibraryGroupMode.BY_STATUS -> {
                        StatusFilter.entries.forEach { status ->
                            val count = if (status == StatusFilter.ALL) {
                                uiState.perStatusCounts[StatusFilter.ALL] ?: quickFilterTabItems.size
                            } else {
                                uiState.perStatusCounts[status] ?: 0
                            }
                            DexFilterSelectRow(
                                label = status.displayName,
                                isSelected = uiState.selectedStatus == status,
                                count = count,
                                onClick = {
                                    viewModel.selectStatus(status)
                                    showQuickFilterSheet = false
                                }
                            )
                        }
                    }
                    LibraryGroupMode.BY_DEFAULT -> {
                        val isAllSelected = uiState.selectedFormatId == null
                        DexFilterSelectRow(
                            label = "All ${activeParent?.name ?: ""}".trim(),
                            isSelected = isAllSelected,
                            count = quickFilterTabItems.size,
                            onClick = {
                                viewModel.selectFormatId(null)
                                showQuickFilterSheet = false
                            }
                        )

                        uiState.formatsForSelectedType.forEach { formatConfig ->
                            val isSelected = uiState.selectedFormatId == formatConfig.id
                            val formatCount = quickFilterTabItems.count {
                                it.format.equals(formatConfig.id, ignoreCase = true) ||
                                it.typeId.equals(formatConfig.id, ignoreCase = true)
                            }
                            DexFilterSelectRow(
                                label = formatConfig.name,
                                isSelected = isSelected,
                                count = formatCount,
                                onClick = {
                                    viewModel.selectFormatId(formatConfig.id)
                                    showQuickFilterSheet = false
                                }
                            )
                        }
                    }
                    LibraryGroupMode.UNGROUPED -> {
                        Text(
                            text = "Library ungrouped. Total items: ${quickFilterTabItems.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DexFilterSelectRow(
    label: String,
    isSelected: Boolean,
    count: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .selectable(
                selected = isSelected,
                onClick = onClick
            )
            .minimumInteractiveComponentSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = pillAlpha)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
