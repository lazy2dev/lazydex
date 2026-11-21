@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package app.lazydex.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lazydex.data.local.LibraryDisplayMode
import app.lazydex.data.local.ListVariant
import app.lazydex.domain.model.SortDirection
import app.lazydex.domain.model.SortField
import app.lazydex.domain.model.TypeConfig
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private object SettingsItemsPaddings {
    val Horizontal = 24.dp
    val Vertical = 10.dp
}

@Composable
fun DexSettingsSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    selectedFormatId: String?,
    availableFormats: List<TypeConfig>,
    onSelectFormatId: (String?) -> Unit,
    selectedGenres: Set<String>,
    availableGenres: List<String>,
    selectedTags: Set<String>,
    availableTags: List<String>,
    authorQuery: String,
    sortField: SortField,
    sortDirection: SortDirection,
    onToggleGenre: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSetAuthorQuery: (String) -> Unit,
    onSelectSortField: (SortField) -> Unit,
    onSelectSortDirection: (SortDirection) -> Unit,
    displayMode: LibraryDisplayMode = LibraryDisplayMode.LIST,
    onSelectDisplayMode: (LibraryDisplayMode) -> Unit = {},
    listVariant: ListVariant = ListVariant.COMPACT,
    onSelectListVariant: (ListVariant) -> Unit = {},
    gridColumns: Int = 0,
    onSelectGridColumns: (Int) -> Unit = {},
    showProgressBadge: Boolean = true,
    onToggleProgressBadge: (Boolean) -> Unit = {},
    showStatusBadge: Boolean = true,
    onToggleStatusBadge: (Boolean) -> Unit = {},
    showScoreBadge: Boolean = true,
    onToggleScoreBadge: (Boolean) -> Unit = {},
    showItemCount: Boolean = false,
    onToggleShowItemCount: (Boolean) -> Unit = {},
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
                        selectedFormatId = selectedFormatId,
                        availableFormats = availableFormats,
                        onSelectFormatId = onSelectFormatId,
                        selectedGenres = selectedGenres,
                        availableGenres = availableGenres,
                        selectedTags = selectedTags,
                        availableTags = availableTags,
                        authorQuery = authorQuery,
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
                        listVariant = listVariant,
                        onSelectListVariant = onSelectListVariant,
                        gridColumns = gridColumns,
                        onSelectGridColumns = onSelectGridColumns,
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
    selectedFormatId: String?,
    availableFormats: List<TypeConfig>,
    onSelectFormatId: (String?) -> Unit,
    selectedGenres: Set<String>,
    availableGenres: List<String>,
    selectedTags: Set<String>,
    availableTags: List<String>,
    authorQuery: String,
    onToggleGenre: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSetAuthorQuery: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // Formats if present for active type
        if (availableFormats.isNotEmpty()) {
            SettingsChipRow(title = "Format") {
                FilterChip(
                    selected = selectedFormatId == null,
                    onClick = { onSelectFormatId(null) },
                    label = { Text("All") }
                )
                availableFormats.forEach { format ->
                    val isSelected = selectedFormatId == format.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onSelectFormatId(if (isSelected) null else format.id)
                        },
                        label = { Text(format.name) }
                    )
                }
            }
        }

        // Genres if present
        if (availableGenres.isNotEmpty()) {
            SettingsChipRow(title = "Genres") {
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
            SettingsChipRow(title = "Tags") {
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
        OutlinedTextField(
            value = authorQuery,
            onValueChange = onSetAuthorQuery,
            label = { Text("Filter by author") },
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
                .padding(
                    horizontal = SettingsItemsPaddings.Horizontal,
                    vertical = 4.dp
                )
        )

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = onClearAll,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsItemsPaddings.Horizontal)
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
    listVariant: ListVariant = ListVariant.COMPACT,
    onSelectListVariant: (ListVariant) -> Unit = {},
    gridColumns: Int,
    onSelectGridColumns: (Int) -> Unit,
    showProgressBadge: Boolean,
    onToggleProgressBadge: (Boolean) -> Unit,
    showStatusBadge: Boolean,
    onToggleStatusBadge: (Boolean) -> Unit,
    showScoreBadge: Boolean,
    onToggleScoreBadge: (Boolean) -> Unit,
    showItemCount: Boolean,
    onToggleShowItemCount: (Boolean) -> Unit,
) {
    val displayModes = remember {
        listOf(
            "List" to LibraryDisplayMode.LIST,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        SettingsChipRow(title = "Display mode") {
            displayModes.forEach { (text, mode) ->
                FilterChip(
                    selected = displayMode == mode,
                    onClick = { onSelectDisplayMode(mode) },
                    label = { Text(text) },
                )
            }
        }

        if (displayMode == LibraryDisplayMode.LIST) {
            SettingsChipRow(title = "List style") {
                FilterChip(
                    selected = listVariant == ListVariant.COMPACT,
                    onClick = { onSelectListVariant(ListVariant.COMPACT) },
                    label = { Text("Compact") }
                )
                FilterChip(
                    selected = listVariant == ListVariant.DETAILED,
                    onClick = { onSelectListVariant(ListVariant.DETAILED) },
                    label = { Text("Detailed") }
                )
            }
        }

        if (displayMode != LibraryDisplayMode.LIST) {
            SliderItem(
                label = "Items per row",
                value = gridColumns,
                onChange = onSelectGridColumns
            )
        }

        HeadingItem(text = "Badges")
        if (displayMode != LibraryDisplayMode.LIST) {
            CheckboxItem(
                label = "Progress badge",
                checked = showProgressBadge,
                onClick = { onToggleProgressBadge(!showProgressBadge) }
            )
        }
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

        HeadingItem(text = "Tabs")
        CheckboxItem(
            label = "Show number of items",
            checked = showItemCount,
            onClick = { onToggleShowItemCount(!showItemCount) }
        )
    }
}

@Composable
private fun SettingsChipRow(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    Column(modifier = modifier) {
        HeadingItem(text = title)
        FlowRow(
            modifier = Modifier.padding(
                start = SettingsItemsPaddings.Horizontal,
                top = 0.dp,
                end = SettingsItemsPaddings.Horizontal,
                bottom = SettingsItemsPaddings.Vertical,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
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
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical
            )
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
            onValueChange = { newValue ->
                val rounded = newValue.roundToInt()
                if (rounded != value) {
                    onChange(rounded)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HeadingItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical
            ),
    )
}

@Composable
private fun BaseSettingsItem(
    label: String,
    widget: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        widget()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CheckboxItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseSettingsItem(
        label = label,
        widget = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
            )
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun BaseSortItem(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit
) {
    BaseSettingsItem(
        label = label,
        widget = {
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
        },
        onClick = onClick,
    )
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
