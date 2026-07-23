package app.lazydex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TagChipRow(
    availableTags: List<String>,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    onClearTags: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableTags.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        item {
            FilterChip(
                selected = selectedTags.isEmpty(),
                onClick = onClearTags,
                label = { Text("All Tags") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }

        items(availableTags) { tag ->
            val isSelected = tag.lowercase() in selectedTags
            FilterChip(
                selected = isSelected,
                onClick = { onToggleTag(tag) },
                label = { Text(tag) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}
