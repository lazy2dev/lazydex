package app.lazydex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.Category
import app.lazydex.domain.model.LengthUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryCreatorSheet(
    parentCategory: Category? = null,
    onDismiss: () -> Unit,
    onSaveCategory: (Category) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf("") }
    var unitName by remember { mutableStateOf(if (parentCategory != null) "" else "Chapter") }
    var secondaryUnitName by remember { mutableStateOf("") }
    var actionVerb by remember { mutableStateOf(if (parentCategory != null) "" else "Read") }
    var hasParts by remember { mutableStateOf(parentCategory == null && false) }
    var hasCharEstimate by remember { mutableStateOf(parentCategory == null && true) }
    var hasAudioSpeed by remember { mutableStateOf(parentCategory == null && true) }
    var lengthUnit by remember { mutableStateOf(LengthUnit.WORDS) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (parentCategory != null) "New Type under ${parentCategory.name}" else "New Category",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (e.g. Web Novel, Light Novel)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text(
                text = "Tracking Labels",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = unitName,
                    onValueChange = { unitName = it },
                    label = { Text(if (parentCategory != null) "Unit (Empty = Inherit)" else "Primary Unit") },
                    placeholder = { Text(parentCategory?.unitName ?: "Chapter") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = actionVerb,
                    onValueChange = { actionVerb = it },
                    label = { Text(if (parentCategory != null) "Verb (Empty = Inherit)" else "Action Verb") },
                    placeholder = { Text(parentCategory?.actionVerb ?: "Read") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = secondaryUnitName,
                onValueChange = { secondaryUnitName = it },
                label = { Text("Secondary Unit (e.g. Volume, Season - Optional)") },
                placeholder = { Text(parentCategory?.secondaryUnitName ?: "") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text(
                text = "Capabilities & Modules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Volumes & Multi-Part Structure", fontWeight = FontWeight.Medium)
                    Text("Breaks items into volumes or seasons", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = hasParts, onCheckedChange = { hasParts = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reading & Character Metrics", fontWeight = FontWeight.Medium)
                    Text("Calculates total length from average chapter size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = hasCharEstimate, onCheckedChange = { hasCharEstimate = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Audio & TTS Playback Tracker", fontWeight = FontWeight.Medium)
                    Text("Enables listen mode and speed multipliers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = hasAudioSpeed, onCheckedChange = { hasAudioSpeed = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Length Unit:", fontWeight = FontWeight.Medium)
                FilterChip(
                    selected = lengthUnit == LengthUnit.WORDS,
                    onClick = { lengthUnit = LengthUnit.WORDS },
                    label = { Text("Words (EN)") }
                )
                FilterChip(
                    selected = lengthUnit == LengthUnit.CHARS,
                    onClick = { lengthUnit = LengthUnit.CHARS },
                    label = { Text("Characters (CN/KR/JP)") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSaveCategory(
                            Category(
                                id = "",
                                parentId = parentCategory?.id,
                                name = name.trim(),
                                icon = if (parentCategory != null) null else "folder",
                                unitName = unitName.trim().ifEmpty { null },
                                secondaryUnitName = secondaryUnitName.trim().ifEmpty { if (parentCategory != null) null else "" },
                                actionVerb = actionVerb.trim().ifEmpty { null },
                                hasParts = if (parentCategory != null && !hasParts) null else hasParts,
                                hasCharEstimate = if (parentCategory != null && !hasCharEstimate) null else hasCharEstimate,
                                hasAudioSpeed = if (parentCategory != null && !hasAudioSpeed) null else hasAudioSpeed,
                                lengthUnit = lengthUnit
                            )
                        )
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

