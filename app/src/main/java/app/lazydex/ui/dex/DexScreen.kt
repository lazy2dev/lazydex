package app.lazydex.ui.dex

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.StatusFilter
import app.lazydex.ui.components.EmptyState
import app.lazydex.ui.components.LibraryBottomSheet
import app.lazydex.ui.components.MediaCard
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexScreen(
    onNavigateToAddItem: () -> Unit,
    onNavigateToEditItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DexViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showLibrarySheet by rememberSaveable { mutableStateOf(false) }
    var librarySheetTab by rememberSaveable { mutableStateOf(0) }
    var isGridView by rememberSaveable { mutableStateOf(true) } // Default to grid
    val librarySheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dex",
                            fontWeight = MaterialTheme.typography.titleLarge.fontWeight,
                            fontSize = 20.sp
                        )
                        if (uiState.totalCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${uiState.totalCount}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { /* Search — not yet wired */ }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search items"
                        )
                    }
                    IconButton(onClick = {
                        librarySheetTab = 0
                        showLibrarySheet = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter and sort items",
                            tint = if (uiState.selectedCategory != null || uiState.selectedStatus != StatusFilter.ALL) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddItem,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add new tracker"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Pills Area
            val hasActiveCategory = uiState.selectedCategory != null
            val hasActiveStatus = uiState.selectedStatus != StatusFilter.ALL

            if (hasActiveCategory || hasActiveStatus) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.selectedCategory?.let { category ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.selectCategory(null) },
                            label = { Text(category.displayName, fontSize = 11.sp) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear category filter",
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        )
                    }
                    if (uiState.selectedStatus != StatusFilter.ALL) {
                        InputChip(
                            selected = true,
                            onClick = { viewModel.selectStatus(StatusFilter.ALL) },
                            label = { Text(uiState.selectedStatus.displayName, fontSize = 11.sp) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear status filter",
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        )
                    }
                }
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
                        val isFiltered = hasActiveCategory || hasActiveStatus
                        val message = if (isFiltered) {
                            "No items match your filters"
                        } else {
                            "Nothing here yet. Tap [+] to add your first tracking item."
                        }
                        val actionLabel = if (isFiltered) "Clear Filters" else null
                        val actionCallback = if (isFiltered) {
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

    // Modal Bottom Sheet
    if (showLibrarySheet) {
        LibraryBottomSheet(
            selectedCategory = uiState.selectedCategory,
            selectedStatus = uiState.selectedStatus,
            selectedField = uiState.sortField,
            selectedDirection = uiState.sortDirection,
            isGridView = isGridView,
            onCategorySelected = { cat -> viewModel.selectCategory(cat) },
            onStatusSelected = { stat -> viewModel.selectStatus(stat) },
            onFieldSelected = { field -> viewModel.selectSortField(field) },
            onDirectionSelected = { dir -> viewModel.selectSortDirection(dir) },
            onLayoutToggled = { grid -> isGridView = grid },
            onClearFilters = { viewModel.clearFilters() },
            onDismissRequest = {
                coroutineScope.launch { librarySheetState.hide() }.invokeOnCompletion {
                    if (!librarySheetState.isVisible) {
                        showLibrarySheet = false
                    }
                }
            },
            sheetState = librarySheetState,
            initialTab = librarySheetTab
        )
    }
}
