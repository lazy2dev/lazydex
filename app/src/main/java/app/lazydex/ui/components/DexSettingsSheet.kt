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
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.Schedule
import app.lazydex.data.local.LibraryDisplayMode
import app.lazydex.data.local.LibraryGroupMode
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.SortDirection
import app.lazydex.domain.model.SortField
import app.lazydex.domain.model.StatusFilter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun DexSettingsSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    selectedType: MediaType?,
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
    onSelectType: (MediaType?) -> Unit,
    onSelectStatus: (StatusFilter) -> Unit,
    onToggleGenre: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSetAuthorQuery: (String) -> Unit,
    onSetRatingRange: ((Double?, Double?) -> Unit)? = null,
    onSelectSortField: (SortField) -> Unit,
    onSelectSortDirection: (SortDirection) -> Unit,
    displayMode: LibraryDisplayMode = LibraryDisplayMode.LIST,
    onSelectDisplayMode: (LibraryDisplayMode) -> Unit = {},
    gridColumns: Int = 0,
    onSelectGridColumns: (Int) -> Unit = {},
    showTypeTabs: Boolean = true,
    onToggleTypeTabs: (Boolean) -> Unit = {},
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
    groupMode: LibraryGroupMode = LibraryGroupMode.BY_DEFAULT,
    onSelectGroupMode: (LibraryGroupMode) -> Unit = {},
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember { listOf("Filter", "Sort", "Display", "Group") }
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
                        selectedType = selectedType,
                        selectedStatus = selectedStatus,
                        selectedGenres = selectedGenres,
                        availableGenres = availableGenres,
                        selectedTags = selectedTags,
                        availableTags = availableTags,
                        authorQuery = authorQuery,
                        onSelectType = onSelectType,
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
                        showTypeTabs = showTypeTabs,
                        onToggleTypeTabs = onToggleTypeTabs,
                        showProgressBadge = showProgressBadge,
                        onToggleProgressBadge = onToggleProgressBadge,
                        showStatusBadge = showStatusBadge,
                        onToggleStatusBadge = onToggleStatusBadge,
                        showScoreBadge = showScoreBadge,
                        onToggleScoreBadge = onToggleScoreBadge,
                        showItemCount = showItemCount,
                        onToggleShowItemCount = onToggleShowItemCount,
                    )
                    3 -> GroupPage(
                        selectedGroupMode = groupMode,
                        onSelectGroupMode = onSelectGroupMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPage(
    selectedType: MediaType?,
    selectedStatus: StatusFilter,
    selectedGenres: Set<String>,
    availableGenres: List<String>,
    selectedTags: Set<String>,
    availableTags: List<String>,
    authorQuery: String,
    onSelectType: (MediaType?) -> Unit,
    onSelectStatus: (StatusFilter) -> Unit,
    onToggleGenre: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSetAuthorQuery: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    var showTypeDialog by remember { mutableStateOf(false) }

    if (showTypeDialog) {
        AlertDialog(
            onDismissRequest = { showTypeDialog = false },
            title = { Text("Types") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectType(null)
                                showTypeDialog = false
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == null,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("All")
                    }
                    MediaType.entries.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectType(type)
                                    showTypeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedType == type,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(type.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTypeDialog = false }) {
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
            StatusFilter.CURRENT,
            StatusFilter.PLANNING,
            StatusFilter.COMPLETED,
            StatusFilter.PAUSED,
            StatusFilter.DROPPED,
            StatusFilter.REPEATING
        )
        statusList.forEach { stat ->
            val isChecked = selectedStatus == stat
            val label = stat.displayName
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

        // Types Row with inline Edit button (matches Komikku)
        val typeLabel = if (selectedType == null) "Types" else "Types (${selectedType.displayName})"
        CheckboxItem(
            label = typeLabel,
            checked = selectedType != null,
            onClick = {
                if (selectedType != null) {
                    onSelectType(null)
                } else {
                    showTypeDialog = true
                }
            },
            trailingContent = {
                TextButton(onClick = { showTypeDialog = true }) {
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
    showTypeTabs: Boolean,
    onToggleTypeTabs: (Boolean) -> Unit,
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

        val displayModes = remember {
            listOf(
                "Compact grid" to LibraryDisplayMode.COMPACT_GRID,
                "Comfortable grid" to LibraryDisplayMode.COMFORTABLE_GRID,
                "List" to LibraryDisplayMode.LIST,
                "Cover-only grid" to LibraryDisplayMode.COVER_ONLY_GRID,
                "Panorama comfortable grid" to LibraryDisplayMode.PANORAMA_COMFORTABLE_GRID,
            ).sortedBy { it.first.length }
        }

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
            label = "Show type tabs",
            checked = showTypeTabs,
            onClick = { onToggleTypeTabs(!showTypeTabs) }
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

@Composable
private fun GroupPage(
    selectedGroupMode: LibraryGroupMode,
    onSelectGroupMode: (LibraryGroupMode) -> Unit,
) {
    val groups = listOf(
        Triple(LibraryGroupMode.BY_DEFAULT, "By Type (Default)", Icons.AutoMirrored.Outlined.Label),
        Triple(LibraryGroupMode.BY_STATUS, "By Status", Icons.Outlined.Schedule),
        Triple(LibraryGroupMode.UNGROUPED, "Ungrouped", Icons.Outlined.LayersClear)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Group Library",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        groups.forEach { (mode, label, icon) ->
            val isSelected = selectedGroupMode == mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectGroupMode(mode) }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelectGroupMode(mode) }
                )
            }
        }
    }
}


