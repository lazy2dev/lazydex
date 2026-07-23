package app.lazydex.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaCategory

@Composable
fun CategoryDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    selectedCategory: MediaCategory?,
    perCategoryCounts: Map<MediaCategory, Int>,
    onSelectCategory: (MediaCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        val totalCount = perCategoryCounts.values.sum()
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "All",
                        tint = if (selectedCategory == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "All",
                        fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$totalCount",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            onClick = {
                onSelectCategory(null)
                onDismissRequest()
            }
        )

        MediaCategory.entries.forEach { category ->
            val icon = when (category) {
                MediaCategory.NOVEL -> Icons.Default.Book
                MediaCategory.MANGA -> Icons.Default.MenuBook
                MediaCategory.ANIME -> Icons.Default.Casino
                MediaCategory.GAME -> Icons.Default.SportsEsports
                MediaCategory.MOVIE -> Icons.Default.Movie
                MediaCategory.TV -> Icons.Default.Tv
            }
            val count = perCategoryCounts[category] ?: 0
            val isSelected = selectedCategory == category

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = category.displayName,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = category.displayName,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$count",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                onClick = {
                    onSelectCategory(category)
                    onDismissRequest()
                }
            )
        }
    }
}
