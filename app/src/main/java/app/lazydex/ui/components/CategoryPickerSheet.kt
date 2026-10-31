package app.lazydex.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    selectedCategoryId: String?,
    totalItemCount: Int,
    categoryCounts: Map<String, Int> = emptyMap(),
    onSelectCategory: (String?) -> Unit,
    onCreateNewCategory: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library Categories",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${categories.size} categories",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            // Scrollable list of categories
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "All Library" option
                val isAllSelected = selectedCategoryId == null
                CategoryItemRow(
                    name = "All Library",
                    icon = Icons.Outlined.CollectionsBookmark,
                    count = totalItemCount,
                    isSelected = isAllSelected,
                    onClick = {
                        onSelectCategory(null)
                        onDismiss()
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Individual categories
                categories.forEach { category ->
                    val isSelected = selectedCategoryId == category.id
                    val count = categoryCounts[category.id]
                    val icon = getCategoryIcon(category)

                    CategoryItemRow(
                        name = category.name,
                        icon = icon,
                        count = count,
                        isSelected = isSelected,
                        onClick = {
                            onSelectCategory(category.id)
                            onDismiss()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // + New category button at bottom
            Button(
                onClick = {
                    onDismiss()
                    onCreateNewCategory()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New category",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CategoryItemRow(
    name: String,
    icon: ImageVector,
    count: Int?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

fun getCategoryIcon(category: Category): ImageVector {
    val id = category.id.lowercase()
    val name = category.name.lowercase()
    return when {
        id.contains("novel") || name.contains("novel") -> Icons.Default.Book
        id.contains("manga") || name.contains("manga") || name.contains("comic") -> Icons.AutoMirrored.Filled.MenuBook
        id.contains("anime") || name.contains("anime") -> Icons.Default.Tv
        id.contains("game") || name.contains("game") -> Icons.Default.SportsEsports
        id.contains("movie") || name.contains("movie") || id.contains("film") || name.contains("film") -> Icons.Default.Movie
        id.contains("tv") || name.contains("tv") || name.contains("show") || name.contains("drama") -> Icons.Default.LocalMovies
        else -> Icons.Default.Folder
    }
}

