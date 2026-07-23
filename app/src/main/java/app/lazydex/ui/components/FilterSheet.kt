package app.lazydex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.SortDirection
import app.lazydex.domain.model.SortField
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.statusLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    selectedCategory: MediaCategory?,
    selectedStatus: StatusFilter,
    selectedGenres: Set<String>,
    availableGenres: List<String>,
    selectedTags: Set<String>,
    availableTags: List<String>,
    authorQuery: String,
    minRating: Double?,
    maxRating: Double?,
    sortField: SortField,
    sortDirection: SortDirection,
    onSelectCategory: (MediaCategory?) -> Unit,
    onSelectStatus: (StatusFilter) -> Unit,
    onToggleGenre: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSetAuthorQuery: (String) -> Unit,
    onSetRatingRange: (Double?, Double?) -> Unit,
    onSelectSortField: (SortField) -> Unit,
    onSelectSortDirection: (SortDirection) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Library Filter & Options",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearAll) {
                    Text("Clear All")
                }
            }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Filter") },
                    icon = { Icon(Icons.Default.FilterList, contentDescription = "Filter") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Sort") },
                    icon = { Icon(Icons.Default.Sort, contentDescription = "Sort") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> { // Filter Tab
                        Text("Category", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { onSelectCategory(null) },
                                label = { Text("All") }
                            )
                            MediaCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { onSelectCategory(cat) },
                                    label = { Text(cat.displayName) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusFilter.entries.forEach { stat ->
                                val label = selectedCategory.statusLabel(stat)
                                FilterChip(
                                    selected = selectedStatus == stat,
                                    onClick = { onSelectStatus(stat) },
                                    label = { Text(label) }
                                )
                            }
                        }

                        if (availableGenres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Genres", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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

                        if (availableTags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Tags", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Author", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    1 -> { // Sort Tab
                        Text("Sort By", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SortField.entries.forEach { field ->
                                FilterChip(
                                    selected = sortField == field,
                                    onClick = { onSelectSortField(field) },
                                    label = { Text(field.displayName) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Direction", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = sortDirection == SortDirection.DESCENDING,
                                onClick = { onSelectSortDirection(SortDirection.DESCENDING) },
                                label = { Text("Descending") },
                                leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = "Desc") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = sortDirection == SortDirection.ASCENDING,
                                onClick = { onSelectSortDirection(SortDirection.ASCENDING) },
                                label = { Text("Ascending") },
                                leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = "Asc") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
