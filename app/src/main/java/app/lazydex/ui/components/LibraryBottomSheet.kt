package app.lazydex.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.SortDirection
import app.lazydex.domain.model.SortField
import app.lazydex.domain.model.StatusFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryBottomSheet(
    selectedCategory: MediaCategory?,
    selectedStatus: StatusFilter,
    selectedField: SortField,
    selectedDirection: SortDirection,
    isGridView: Boolean,
    onCategorySelected: (MediaCategory?) -> Unit,
    onStatusSelected: (StatusFilter) -> Unit,
    onFieldSelected: (SortField) -> Unit,
    onDirectionSelected: (SortDirection) -> Unit,
    onLayoutToggled: (Boolean) -> Unit,
    onClearFilters: () -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val tabs = listOf("Filter", "Sort", "Display")

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 14.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> {
                    // ==================== FILTER TAB ====================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { onCategorySelected(null) },
                                    label = { Text("All") }
                                )
                            }
                            items(MediaCategory.entries) { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { onCategorySelected(category) },
                                    label = { Text(category.displayName) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(StatusFilter.entries) { status ->
                                FilterChip(
                                    selected = selectedStatus == status,
                                    onClick = { onStatusSelected(status) },
                                    label = { Text(status.displayName) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onClearFilters,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Filters")
                        }
                    }
                }
                1 -> {
                    // ==================== SORT TAB ====================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        SortField.entries.forEach { field ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onFieldSelected(field) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedField == field,
                                    onClick = { onFieldSelected(field) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (field) {
                                        SortField.TITLE -> "Title"
                                        SortField.LAST_ACTIVE -> "Last Active"
                                        SortField.PROGRESS -> "Progress"
                                        SortField.DATE_ADDED -> "Date Added"
                                    },
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Direction",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = selectedDirection == SortDirection.ASCENDING,
                                onClick = { onDirectionSelected(SortDirection.ASCENDING) },
                                label = { Text("Ascending") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Ascending",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            FilterChip(
                                selected = selectedDirection == SortDirection.DESCENDING,
                                onClick = { onDirectionSelected(SortDirection.DESCENDING) },
                                label = { Text("Descending") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Descending",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                2 -> {
                    // ==================== DISPLAY TAB ====================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Layout Mode",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = isGridView,
                                onClick = { onLayoutToggled(true) },
                                label = { Text("Grid") }
                            )
                            FilterChip(
                                selected = !isGridView,
                                onClick = { onLayoutToggled(false) },
                                label = { Text("List") }
                            )
                        }
                    }
                }
            }
        }
    }
}
