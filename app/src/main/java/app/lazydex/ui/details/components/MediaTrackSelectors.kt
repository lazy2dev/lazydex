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
import app.lazydex.domain.model.UserStatus
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TrackStatusSelector(
    currentStatus: UserStatus,
    onStatusSelected: (UserStatus) -> Unit,
    onDismissRequest: () -> Unit
) {
    var selected by remember { mutableStateOf(currentStatus) }

    BaseSelector(
        title = "Status",
        content = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(UserStatus.entries) { status ->
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
    onProgressConfirmed: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var progress by remember { mutableIntStateOf(currentProgress.coerceAtLeast(0)) }
    var textValue by remember { mutableStateOf(progress.toString()) }

    BaseSelector(
        title = title,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (total != null && total > 0) "$progress / $total" else "$progress",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

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
                            val max = total ?: Int.MAX_VALUE
                            val next = (progress + 1).coerceAtMost(max)
                            progress = next
                            textValue = next.toString()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase 1")
                    }

                    FilledTonalButton(
                        onClick = {
                            val max = total ?: Int.MAX_VALUE
                            val next = (progress + 5).coerceAtMost(max)
                            progress = next
                            textValue = next.toString()
                        }
                    ) {
                        Text("+5")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }
                        textValue = filtered
                        val parsed = filtered.toIntOrNull()
                        if (parsed != null) {
                            val max = total ?: Int.MAX_VALUE
                            progress = parsed.coerceIn(0, max)
                        }
                    },
                    label = { Text("Exact Progress") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        onConfirm = {
            onProgressConfirmed(progress)
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

                Spacer(modifier = Modifier.height(16.dp))

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
            Row {
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

