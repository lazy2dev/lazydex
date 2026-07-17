package app.lazydex.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.StatusFilter
import app.lazydex.ui.components.EmptyState
import app.lazydex.ui.components.FilterBottomSheet
import app.lazydex.ui.components.MediaCard
import app.lazydex.ui.components.SortBottomSheet
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddItem: () -> Unit,
    onNavigateToEditItem: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }

    val filterSheetState = rememberModalBottomSheetState()
    val sortSheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "LazyDex",
                        fontWeight = MaterialTheme.typography.titleLarge.fontWeight,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter items",
                            tint = if (uiState.selectedCategory != null || uiState.selectedStatus != StatusFilter.ALL) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort items"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
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

            // Main List Content
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.items,
                                key = { it.id }
                            ) { item ->
                                MediaCard(
                                    item = item,
                                    onClick = { onNavigateToEditItem(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheets
    if (showFilterSheet) {
        FilterBottomSheet(
            selectedCategory = uiState.selectedCategory,
            selectedStatus = uiState.selectedStatus,
            onCategorySelected = { cat -> viewModel.selectCategory(cat) },
            onStatusSelected = { stat -> viewModel.selectStatus(stat) },
            onClearFilters = { viewModel.clearFilters() },
            onDismissRequest = {
                coroutineScope.launch { filterSheetState.hide() }.invokeOnCompletion {
                    if (!filterSheetState.isVisible) {
                        showFilterSheet = false
                    }
                }
            },
            sheetState = filterSheetState
        )
    }

    if (showSortSheet) {
        SortBottomSheet(
            selectedField = uiState.sortField,
            selectedDirection = uiState.sortDirection,
            onFieldSelected = { field -> viewModel.selectSortField(field) },
            onDirectionSelected = { dir -> viewModel.selectSortDirection(dir) },
            onDismissRequest = {
                coroutineScope.launch { sortSheetState.hide() }.invokeOnCompletion {
                    if (!sortSheetState.isVisible) {
                        showSortSheet = false
                    }
                }
            },
            sheetState = sortSheetState
        )
    }
}
