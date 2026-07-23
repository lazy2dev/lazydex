package app.lazydex.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.statusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusDropdown(
    selectedStatus: StatusFilter,
    selectedCategory: MediaCategory?,
    perStatusCounts: Map<StatusFilter, Int>,
    onSelectStatus: (StatusFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        val label = selectedCategory.statusLabel(selectedStatus)
        val count = perStatusCounts[selectedStatus] ?: 0

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.menuAnchor()
        ) {
            Text(
                text = "$label ($count)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            StatusFilter.entries.forEach { status ->
                val itemLabel = selectedCategory.statusLabel(status)
                val itemCounts = perStatusCounts[status] ?: 0
                val isSelected = selectedStatus == status

                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = itemLabel,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "$itemCounts",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelectStatus(status)
                        expanded = false
                    }
                )
            }
        }
    }
}
