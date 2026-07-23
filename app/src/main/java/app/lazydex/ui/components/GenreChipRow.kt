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
fun GenreChipRow(
    availableGenres: List<String>,
    selectedGenres: Set<String>,
    onToggleGenre: (String) -> Unit,
    onClearGenres: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableGenres.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        item {
            FilterChip(
                selected = selectedGenres.isEmpty(),
                onClick = onClearGenres,
                label = { Text("All Genres") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

        items(availableGenres) { genre ->
            val isSelected = genre.lowercase() in selectedGenres
            FilterChip(
                selected = isSelected,
                onClick = { onToggleGenre(genre) },
                label = { Text(genre) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
