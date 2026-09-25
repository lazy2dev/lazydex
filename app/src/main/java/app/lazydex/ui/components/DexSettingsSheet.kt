@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package app.lazydex.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lazydex.data.local.LibraryDisplayMode
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.SortDirection
import app.lazydex.domain.model.SortField
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.statusLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun DexSettingsSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    selectedCategory: MediaCategory?,
    selectedStatus: StatusFilter,
    selectedGenres: Set<String>,
    availableGenres: List<String>,
    selectedTags: Set<String>,
    availableTags: List<String>,
    authorQuery: String,
    minRating: Double? = null,
    maxRating: Double? = null,
    sortField: SortField,
    sortDirection: SortDirection,
    onSelectCategory: (MediaCategory?) -> Unit,
    onSelectStatus: (StatusFilter) -> Unit,
    onToggleGenre: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSetAuthorQuery: (String) -> Unit,
    onSetRatingRange: ((Double?, Double?) -> Unit)? = null,
    onSelectSortField: (SortField) -> Unit,
    onSelectSortDirection: (SortDirection) -> Unit,
    displayMode: LibraryDisplayMode = LibraryDisplayMode.COMPACT_GRID,
    onSelectDisplayMode: (LibraryDisplayMode) -> Unit = {},
    gridColumns: Int = 0,
    onSelectGridColumns: (Int) -> Unit = {},
    showCategoryTabs: Boolean = true,
    onToggleCategoryTabs: (Boolean) -> Unit = {},
    showProgressBadge: Boolean = true,
    onToggleProgressBadge: (Boolean) -> Unit = {},
    showStatusBadge: Boolean = true,
    onToggleStatusBadge: (Boolean) -> Unit = {},
    showScoreBadge: Boolean = true,
    onToggleScoreBadge: (Boolean) -> Unit = {},
    showCategoryBadge: Boolean = false,
    onToggleCategoryBadge: (Boolean) -> Unit = {},
    showItemCount: Boolean = false,
    onToggleShowItemCount: (Boolean) -> Unit = {},
    isGridView: Boolean = displayMode != LibraryDisplayMode.LIST,
    onToggleGridView: ((Boolean) -> Unit)? = null,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember { listOf("Filter", "Sort", "Display") }
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {},
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = tab,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()

            HorizontalPager(
                modifier = Modifier.animateContentSize(),
                state = pagerState,
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (page) {
                    0 -> FilterPage(
                        selectedCategory = selectedCategory,
                        selectedStatus = selectedStatus,
                        selectedGenres = selectedGenres,
                        availableGenres = availableGenres,
                        selectedTags = selectedTags,
                        availableTags = availableTags,
                        authorQuery = authorQuery,
                        onSelectCategory = onSelectCategory,
                        onSelectStatus = onSelectStatus,
                        onToggleGenre = onToggleGenre,
                        onToggleTag = onToggleTag,
                        onSetAuthorQuery = onSetAuthorQuery,
                        onClearAll = onClearAll,
                    )
                    1 -> SortPage(
                        sortField = sortField,
                        sortDirection = sortDirection,
                        onSelectSortField = onSelectSortField,
                        onSelectSortDirection = onSelectSortDirection,
                    )
                    2 -> DisplayPage(
                        displayMode = displayMode,
                        onSelectDisplayMode = onSelectDisplayMode,
                        gridColumns = gridColumns,
                        onSelectGridColumns = onSelectGridColumns,
                        showCategoryTabs = showCategoryTabs,
                        onToggleCategoryTabs = onToggleCategoryTabs,
                        showProgressBadge = showProgressBadge,
                        onToggleProgressBadge = onToggleProgressBadge,
                        showStatusBadge = showStatusBadge,
                        onToggleStatusBadge = onToggleStatusBadge,
                        showScoreBadge = showScoreBadge,
                        onToggleScoreBadge = onToggleScoreBadge,
                        showCategoryBadge = showCategoryBadge,
                        onToggleCategoryBadge = onToggleCategoryBadge,
                        showItemCount = showItemCount,
                        onToggleShowItemCount = onToggleShowItemCount,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPage(
    selectedCategory: MediaCategory?,
    selectedStatus: StatusFilter,
    selectedGenres: Set<String>,
    availableGenres: List<String>,
    selectedTags: Set<String>,
    availableTags: List<String>,
    authorQuery: String,
    onSelectCategory: (MediaCategory?) -> Unit,
    onSelectStatus: (StatusFilter) -> Unit,
    onToggleGenre: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSetAuthorQuery: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    var showCategoryDialog by remember { mutableStateOf(false) }

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Categories") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectCategory(null)
                                showCategoryDialog = false
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedCategory == null,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("All")
                    }
                    MediaCategory.entries.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectCategory(cat)
                                    showCategoryDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategory == cat,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(cat.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // Status Checkbox Rows (Komikku Filter style)
        val statusList = listOf(
            StatusFilter.IN_PROGRESS,
            StatusFilter.PLAN_TO,
            StatusFilter.COMPLETED,
            StatusFilter.ON_HOLD,
            StatusFilter.DROPPED
        )
        statusList.forEach { stat ->
            val isChecked = selectedStatus == stat
            val label = selectedCategory.statusLabel(stat)
            CheckboxItem(
                label = label,
                checked = isChecked,
                onClick = {
                    if (isChecked) {
                        onSelectStatus(StatusFilter.ALL)
                    } else {
                        onSelectStatus(stat)
                    }
                }
            )
        }

        // Categories Row with inline Edit button (matches Komikku)
        val categoryLabel = if (selectedCategory == null) "Categories" else "Categories (${selectedCategory.displayName})"
        CheckboxItem(
            label = categoryLabel,
            checked = selectedCategory != null,
            onClick = {
                if (selectedCategory != null) {
                    onSelectCategory(null)
                } else {
                    showCategoryDialog = true
                }
            },
            trailingContent = {
                TextButton(onClick = { showCategoryDialog = true }) {
                    Text("Edit")
                }
            }
        )

        // Genres if present
        if (availableGenres.isNotEmpty()) {
            HeadingItem(text = "Genres")
            FlowRow(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableGenres.forEach { genre ->
                    val isSelected = genre.lowercase() in selectedGenres
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleGenre(genre) },
                        label = { Text(genre) }
                    )
                }
            }
        }

        // Tags if present
        if (availableTags.isNotEmpty()) {
            HeadingItem(text = "Tags")
            FlowRow(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableTags.forEach { tag ->
                    val isSelected = tag.lowercase() in selectedTags
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleTag(tag) },
                        label = { Text(tag) }
                    )
                }
            }
        }

        // Author input
        HeadingItem(text = "Author")
        OutlinedTextField(
            value = authorQuery,
            onValueChange = onSetAuthorQuery,
            placeholder = { Text("Filter by author...") },
            trailingIcon = {
                if (authorQuery.isNotEmpty()) {
                    IconButton(onClick = { onSetAuthorQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = onClearAll,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text("Reset Filters")
        }
    }
}

@Composable
private fun SortPage(
    sortField: SortField,
    sortDirection: SortDirection,
    onSelectSortField: (SortField) -> Unit,
    onSelectSortDirection: (SortDirection) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        SortField.entries.forEach { field ->
            val isSelected = sortField == field
            val sortDescending = if (isSelected) (sortDirection == SortDirection.DESCENDING) else null
            SortItem(
                label = field.displayName,
                sortDescending = sortDescending,
                onClick = {
                    if (isSelected) {
                        val newDir = if (sortDirection == SortDirection.DESCENDING) {
                            SortDirection.ASCENDING
                        } else {
                            SortDirection.DESCENDING
                        }
                        onSelectSortDirection(newDir)
                    } else {
                        onSelectSortField(field)
                        onSelectSortDirection(SortDirection.DESCENDING)
                    }
                }
            )
        }
    }
}

@Composable
private fun DisplayPage(
    displayMode: LibraryDisplayMode,
    onSelectDisplayMode: (LibraryDisplayMode) -> Unit,
    gridColumns: Int,
    onSelectGridColumns: (Int) -> Unit,
    showCategoryTabs: Boolean,
    onToggleCategoryTabs: (Boolean) -> Unit,
    showProgressBadge: Boolean,
    onToggleProgressBadge: (Boolean) -> Unit,
    showStatusBadge: Boolean,
    onToggleStatusBadge: (Boolean) -> Unit,
    showScoreBadge: Boolean,
    onToggleScoreBadge: (Boolean) -> Unit,
    showCategoryBadge: Boolean,
    onToggleCategoryBadge: (Boolean) -> Unit,
    showItemCount: Boolean,
    onToggleShowItemCount: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        HeadingItem(text = "Display mode")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DisplayModeButton(
                text = "Compact grid",
                selected = displayMode == LibraryDisplayMode.COMPACT_GRID,
                onClick = { onSelectDisplayMode(LibraryDisplayMode.COMPACT_GRID) },
                modifier = Modifier.weight(1.3f)
            )
            DisplayModeButton(
                text = "Comfortable grid",
                selected = displayMode == LibraryDisplayMode.COMFORTABLE_GRID,
                onClick = { onSelectDisplayMode(LibraryDisplayMode.COMFORTABLE_GRID) },
                modifier = Modifier.weight(1.5f)
            )
            DisplayModeButton(
                text = "List",
                selected = displayMode == LibraryDisplayMode.LIST,
                onClick = { onSelectDisplayMode(LibraryDisplayMode.LIST) },
                modifier = Modifier.weight(0.9f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DisplayModeButton(
                text = "Cover-only grid",
                selected = displayMode == LibraryDisplayMode.COVER_ONLY_GRID,
                onClick = { onSelectDisplayMode(LibraryDisplayMode.COVER_ONLY_GRID) },
                modifier = Modifier.weight(1.2f)
            )
            DisplayModeButton(
                text = "Panorama comfortable grid",
                selected = displayMode == LibraryDisplayMode.PANORAMA_COMFORTABLE_GRID,
                onClick = { onSelectDisplayMode(LibraryDisplayMode.PANORAMA_COMFORTABLE_GRID) },
                modifier = Modifier.weight(2f)
            )
        }

        if (displayMode != LibraryDisplayMode.LIST) {
            Spacer(modifier = Modifier.height(8.dp))
            SliderItem(
                label = "Items per row",
                value = gridColumns,
                onChange = onSelectGridColumns
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HeadingItem(text = "Overlay")
        CheckboxItem(
            label = "Progress badge",
            checked = showProgressBadge,
            onClick = { onToggleProgressBadge(!showProgressBadge) }
        )
        CheckboxItem(
            label = "Status badge",
            checked = showStatusBadge,
            onClick = { onToggleStatusBadge(!showStatusBadge) }
        )
        CheckboxItem(
            label = "Score badge",
            checked = showScoreBadge,
            onClick = { onToggleScoreBadge(!showScoreBadge) }
        )
        CheckboxItem(
            label = "Category badge",
            checked = showCategoryBadge,
            onClick = { onToggleCategoryBadge(!showCategoryBadge) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        HeadingItem(text = "Tabs")
        CheckboxItem(
            label = "Show category tabs",
            checked = showCategoryTabs,
            onClick = { onToggleCategoryTabs(!showCategoryTabs) }
        )
        CheckboxItem(
            label = "Show number of items",
            checked = showItemCount,
            onClick = { onToggleShowItemCount(!showItemCount) }
        )
    }
}

@Composable
private fun SliderItem(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (value == 0) "Auto" else value.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DisplayModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun HeadingItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
    )
}

@Composable
private fun CheckboxItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(
            imageVector = if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailingContent?.invoke()
    }
}

@Composable
private fun SortItem(
    label: String,
    sortDescending: Boolean?,
    onClick: () -> Unit
) {
    val arrowIcon = when (sortDescending) {
        true -> Icons.Default.ArrowDownward
        false -> Icons.Default.ArrowUpward
        null -> null
    }

    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (arrowIcon != null) {
            Icon(
                imageVector = arrowIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (sortDescending != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

