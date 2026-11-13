package app.lazydex.ui.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.UserStatus
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TrackStatusSelector(
    currentStatus: UserStatus,
    mediaType: MediaType = MediaType.NOVEL,
    onStatusSelected: (UserStatus) -> Unit,
    onDismissRequest: () -> Unit
) {
    var selected by remember { mutableStateOf(currentStatus) }
    val availableStatuses = remember { UserStatus.entries }

    BaseSelector(
        title = "Status",
        content = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(availableStatuses) { status ->
                    val isSelected = status == selected

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selected = status }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selected = status }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = status.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        onConfirm = {
            onStatusSelected(selected)
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest
    )
}

@Composable
fun TrackProgressSelector(
    title: String,
    currentProgress: Int,
    total: Int?,
    hasSecondaryProgress: Boolean = false,
    secondaryUnitName: String? = null,
    currentVolume: Double = 0.0,
    totalVolumes: Double? = null,
    repeatCount: Int = 0,
    repeatLabel: String = "Rewatches",
    onConfirmed: (progress: Int, total: Int?, volume: Double, totalVolumes: Double?, repeat: Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var progress by remember { mutableIntStateOf(currentProgress.coerceAtLeast(0)) }
    var textValue by remember { mutableStateOf(progress.toString()) }
    var totalTextValue by remember { mutableStateOf(total?.toString() ?: "") }

    var volume by remember { mutableDoubleStateOf(currentVolume.coerceAtLeast(0.0)) }
    var volumeTextValue by remember {
        mutableStateOf(if (volume % 1.0 == 0.0) volume.toInt().toString() else volume.toString())
    }
    var totalVolumesTextValue by remember {
        mutableStateOf(totalVolumes?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
    }

    var count by remember { mutableIntStateOf(repeatCount.coerceAtLeast(0)) }

    BaseSelector(
        title = title,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- 1. PRIMARY PROGRESS (CHAPTERS / EPISODES) ---
                val parsedTotal = totalTextValue.trim().toIntOrNull()
                Text(
                    text = if (parsedTotal != null && parsedTotal > 0) "$progress / $parsedTotal" else "$progress",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val next = (progress - 5).coerceAtLeast(0)
                            progress = next
                            textValue = next.toString()
                        }
                    ) {
                        Text("-5")
                    }

                    FilledTonalIconButton(
                        onClick = {
                            val next = (progress - 1).coerceAtLeast(0)
                            progress = next
                            textValue = next.toString()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease 1")
                    }

                    FilledTonalIconButton(
                        onClick = {
                            val max = parsedTotal ?: Int.MAX_VALUE
                            val next = (progress + 1).coerceAtMost(max)
                            progress = next
                            textValue = next.toString()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase 1")
                    }

                    FilledTonalButton(
                        onClick = {
                            val max = parsedTotal ?: Int.MAX_VALUE
                            val next = (progress + 5).coerceAtMost(max)
                            progress = next
                            textValue = next.toString()
                        }
                    ) {
                        Text("+5")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }
                            textValue = filtered
                            val parsed = filtered.toIntOrNull()
                            if (parsed != null) {
                                val max = parsedTotal ?: Int.MAX_VALUE
                                progress = parsed.coerceIn(0, max)
                            }
                        },
                        label = { Text("Exact Progress") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = totalTextValue,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }
                            totalTextValue = filtered
                        },
                        label = { Text("Total Units") },
                        placeholder = { Text("—") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // --- 2. SECONDARY VOLUME PROGRESS (IF APPLICABLE) ---
                if (hasSecondaryProgress) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    val unitName = secondaryUnitName ?: "Volume"
                    val parsedTotalVol = totalVolumesTextValue.trim().toDoubleOrNull()
                    val displayVol = if (volume % 1.0 == 0.0) volume.toInt().toString() else volume.toString()
                    val displayTotalVol = parsedTotalVol?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }

                    Text(
                        text = "$unitName Progress: ${if (displayTotalVol != null) "$displayVol / $displayTotalVol" else displayVol}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val next = (volume - 5.0).coerceAtLeast(0.0)
                                volume = next
                                volumeTextValue = if (next % 1.0 == 0.0) next.toInt().toString() else next.toString()
                            }
                        ) {
                            Text("-5")
                        }

                        FilledTonalIconButton(
                            onClick = {
                                val next = (volume - 1.0).coerceAtLeast(0.0)
                                volume = next
                                volumeTextValue = if (next % 1.0 == 0.0) next.toInt().toString() else next.toString()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease 1 Volume")
                        }

                        FilledTonalIconButton(
                            onClick = {
                                val max = parsedTotalVol ?: Double.MAX_VALUE
                                val next = (volume + 1.0).coerceAtMost(max)
                                volume = next
                                volumeTextValue = if (next % 1.0 == 0.0) next.toInt().toString() else next.toString()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Increase 1 Volume")
                        }

                        FilledTonalButton(
                            onClick = {
                                val max = parsedTotalVol ?: Double.MAX_VALUE
                                val next = (volume + 5.0).coerceAtMost(max)
                                volume = next
                                volumeTextValue = if (next % 1.0 == 0.0) next.toInt().toString() else next.toString()
                            }
                        ) {
                            Text("+5")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = volumeTextValue,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() || it == '.' }
                                volumeTextValue = filtered
                                val parsed = filtered.toDoubleOrNull()
                                if (parsed != null) {
                                    val max = parsedTotalVol ?: Double.MAX_VALUE
                                    volume = parsed.coerceIn(0.0, max)
                                }
                            },
                            label = { Text("Exact $unitName") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = totalVolumesTextValue,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() || it == '.' }
                                totalVolumesTextValue = filtered
                            },
                            label = { Text("Total ${unitName}s") },
                            placeholder = { Text("—") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // --- 3. REPEAT COUNT ---
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = repeatLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Completed repeat cycles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                val next = (count - 1).coerceAtLeast(0)
                                count = next
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease repeat")
                        }

                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        FilledTonalIconButton(
                            onClick = {
                                val next = count + 1
                                count = next
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Increase repeat")
                        }
                    }
                }
            }
        },
        onConfirm = {
            onConfirmed(
                progress,
                totalTextValue.trim().toIntOrNull(),
                volume,
                totalVolumesTextValue.trim().toDoubleOrNull(),
                count
            )
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest
    )
}

@Composable
fun TrackScoreSelector(
    currentScore: Double?,
    onScoreConfirmed: (Double?) -> Unit,
    onDismissRequest: () -> Unit
) {
    var score by remember { mutableDoubleStateOf(currentScore ?: 0.0) }

    BaseSelector(
        title = "Rating (5-Star Scale)",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = if (score > 0.0) String.format(Locale.US, "%.1f / 5.0", score) else "No Rating",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5-Star Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    (1..5).forEach { star ->
                        val isSelected = score == star.toDouble()
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { score = star.toDouble() },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = "$star ★",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Slider(
                    value = score.toFloat(),
                    onValueChange = {
                        val rounded = (it * 10f).roundToInt() / 10.0
                        score = rounded.coerceIn(0.0, 5.0)
                    },
                    valueRange = 0f..5f,
                    steps = 49,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "0.0", style = MaterialTheme.typography.bodySmall)
                    Text(text = "2.5", style = MaterialTheme.typography.bodySmall)
                    Text(text = "5.0", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { score = 0.0 }
                ) {
                    Text("Clear Rating")
                }
            }
        },
        onConfirm = {
            onScoreConfirmed(if (score > 0.0) score else null)
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDateSelector(
    title: String,
    initialDateMillis: Long?,
    onDateConfirmed: (Long?) -> Unit,
    onDismissRequest: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis ?: System.currentTimeMillis()
    )

    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDateConfirmed(datePickerState.selectedDateMillis)
                    onDismissRequest()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (initialDateMillis != null) {
                    TextButton(
                        onClick = {
                            onDateConfirmed(null)
                            onDismissRequest()
                        }
                    ) {
                        Text("Remove")
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        }
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        )
    }
}

@Composable
fun BaseSelector(
    title: String,
    content: @Composable BoxScope.() -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    thirdButton: @Composable (RowScope.() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                content = content
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("OK")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (thirdButton != null) {
                    thirdButton()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        }
    )
}

