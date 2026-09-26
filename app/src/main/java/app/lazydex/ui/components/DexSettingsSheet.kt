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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.material3.SliderDefaults
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
            if (field == SortField.RANDOM) {
                BaseSortItem(
                    label = field.displayName,
                    icon = Icons.Default.Refresh.takeIf { isSelected },
                    onClick = {
                        onSelectSortField(SortField.RANDOM)
                    }
                )
            } else {
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
                            val defaultDir = if (field == SortField.ALPHABETICAL) {
                                SortDirection.ASCENDING
                            } else {
                                SortDirection.DESCENDING
                            }
                            onSelectSortDirection(defaultDir)
                        }
                    }
                )
            }
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

        val displayModes = listOf(
            "Compact grid" to LibraryDisplayMode.COMPACT_GRID,
            "Comfortable grid" to LibraryDisplayMode.COMFORTABLE_GRID,
            "List" to LibraryDisplayMode.LIST,
            "Cover-only grid" to LibraryDisplayMode.COVER_ONLY_GRID,
            "Panorama comfortable grid" to LibraryDisplayMode.PANORAMA_COMFORTABLE_GRID,
        )

        FlowRow(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            displayModes.forEach { (text, mode) ->
                FilterChip(
                    selected = displayMode == mode,
                    onClick = { onSelectDisplayMode(mode) },
                    label = { Text(text) },
                )
            }
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
        HeadingItem(text = "Badges")
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
            .padding(horizontal = 24.dp, vertical = 10.dp)
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
            Pill(
                text = if (value == 0) "Auto" else value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                        inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    ),
                    sliderState = sliderState,
                    modifier = Modifier.height(16.dp),
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HeadingItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .padding(horizontal = 24.dp, vertical = 10.dp),
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailingContent?.invoke()
    }
}

@Composable
private fun BaseSortItem(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (icon != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
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
    BaseSortItem(
        label = label,
        icon = arrowIcon,
        onClick = onClick
    )
}

