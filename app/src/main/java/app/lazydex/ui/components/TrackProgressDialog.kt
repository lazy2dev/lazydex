package app.lazydex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val ROW_COUNT = 3
private const val UNSET_TEXT_ALPHA = 0.5f

/**
 * Format progress double: removes trailing .0 for whole integers (e.g. 179 -> "179"),
 * while preserving decimals (e.g. 12.5 -> "12.5").
 */
fun formatProgress(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toString().removeSuffix(".0")
    }
}

/**
 * Clickable container for showing track metadata / progress in lists and dialogs.
 * Ported directly from Komikku's TrackInfoDialogHome.kt.
 */
@Composable
fun TrackDetailsItem(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text?.takeIf { it.isNotBlank() } ?: placeholder,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (text.isNullOrBlank()) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = UNSET_TEXT_ALPHA)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * Inline dual stepper row with [-] decrement, clickable value, and [+] increment.
 */
@Composable
fun ProgressStepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    minValue: Double = 0.0,
    maxValue: Double? = null,
    label: String? = null,
    onValueClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = {
                val next = maxOf(minValue, value - step)
                onValueChange(next)
            },
            enabled = value > minValue,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrement",
                modifier = Modifier.size(16.dp),
            )
        }

        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (onValueClick != null) Modifier.clickable(onClick = onValueClick)
                    else Modifier
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val formatted = formatProgress(value)
            val displayText = if (maxValue != null && maxValue > 0) {
                "$formatted / ${formatProgress(maxValue)}"
            } else {
                formatted
            }
            Text(
                text = if (label != null) "$label $displayText" else displayText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        IconButton(
            onClick = {
                val next = (value + step).let {
                    if (maxValue != null && maxValue > 0) minOf(it, maxValue) else it
                }
                onValueChange(next)
            },
            enabled = maxValue == null || maxValue <= 0 || value < maxValue,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increment",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Main progress dialog supporting Komikku wheel number picking, fractional decimal input (e.g. 12.5),
 * and quick +/- 1 steppers.
 */
@Composable
fun TrackProgressDialog(
    initialValue: Double,
    maxValue: Double? = null,
    title: String = "Progress",
    unitLabel: String? = null,
    onConfirm: (Double) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var currentValue by remember { mutableDoubleStateOf(initialValue) }
    val initialHasFraction = (initialValue % 1.0 != 0.0)
    var manualInputMode by remember { mutableStateOf(initialHasFraction) }
    var manualText by remember { mutableStateOf(formatProgress(initialValue)) }

    val rangeEnd = remember(maxValue, currentValue) {
        val baseMax = maxValue?.toInt()?.takeIf { it > 0 } ?: 1000
        maxOf(baseMax, currentValue.toInt() + 100, 100)
    }
    val rangeItems = remember(rangeEnd) {
        (0..rangeEnd).toList()
    }

    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(
                    onClick = { manualInputMode = !manualInputMode },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (manualInputMode) Icons.Default.UnfoldMore else Icons.Default.Keyboard,
                        contentDescription = if (manualInputMode) "Switch to Wheel" else "Switch to Manual Input",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header displaying Current / Total
                val totalStr = maxValue?.takeIf { it > 0 }?.let { "/ ${formatProgress(it)}" } ?: ""
                Text(
                    text = "${unitLabel?.let { "$it " } ?: ""}${formatProgress(currentValue)} $totalStr".trim(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (manualInputMode) {
                    // Manual Decimal Input Field
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() || it == '.' || it == ',' }
                            if (clean.count { it == '.' || it == ',' } <= 1) {
                                manualText = clean
                                val parsed = clean.replace(',', '.').toDoubleOrNull()
                                if (parsed != null && parsed >= 0.0) {
                                    currentValue = parsed
                                }
                            }
                        },
                        label = { Text(unitLabel ?: "Value (e.g. 12.5)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val parsed = manualText.replace(',', '.').toDoubleOrNull() ?: currentValue
                                val confirmed = parsed.coerceAtLeast(0.0).let {
                                    if (maxValue != null && maxValue > 0) minOf(it, maxValue) else it
                                }
                                onConfirm(confirmed)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                } else {
                    // Komikku Wheel Picker + Stepper Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconButton(
                            onClick = {
                                val next = maxOf(0.0, currentValue - 1.0)
                                currentValue = next
                                manualText = formatProgress(next)
                            },
                            enabled = currentValue > 0.0,
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrement")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        WheelNumberPicker(
                            items = rangeItems,
                            startIndex = currentValue.toInt().coerceIn(0, rangeItems.size - 1),
                            onSelectionChanged = { index ->
                                if (index in rangeItems.indices) {
                                    val next = rangeItems[index].toDouble()
                                    currentValue = next
                                    manualText = formatProgress(next)
                                }
                            },
                            onClick = {
                                manualInputMode = true
                            },
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                val next = (currentValue + 1.0).let {
                                    if (maxValue != null && maxValue > 0) minOf(it, maxValue) else it
                                }
                                currentValue = next
                                manualText = formatProgress(next)
                            },
                            enabled = maxValue == null || maxValue <= 0 || currentValue < maxValue,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increment")
                        }
                    }
                }

                // Quick fractional & step chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AssistChip(
                        onClick = {
                            val next = (currentValue + 0.5).let {
                                if (maxValue != null && maxValue > 0) minOf(it, maxValue) else it
                            }
                            currentValue = next
                            manualText = formatProgress(next)
                            manualInputMode = true
                        },
                        label = { Text("+0.5") },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    AssistChip(
                        onClick = {
                            val next = (currentValue + 1.0).let {
                                if (maxValue != null && maxValue > 0) minOf(it, maxValue) else it
                            }
                            currentValue = next
                            manualText = formatProgress(next)
                        },
                        label = { Text("+1") },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    if (maxValue != null && maxValue > 0) {
                        AssistChip(
                            onClick = {
                                currentValue = maxValue
                                manualText = formatProgress(maxValue)
                            },
                            label = { Text("Max") },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = manualText.replace(',', '.').toDoubleOrNull() ?: currentValue
                    val finalVal = parsed.coerceAtLeast(0.0).let {
                        if (maxValue != null && maxValue > 0) minOf(it, maxValue) else it
                    }
                    onConfirm(finalVal)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Compatible wrapper matching Komikku's TrackChapterSelector.
 */
@Composable
fun TrackChapterSelector(
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    range: Iterable<Int>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    title: String = "Chapters",
) {
    val items = remember(range) { range.toList() }
    var currentSelection by remember { mutableIntStateOf(selection) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                WheelNumberPicker(
                    items = items,
                    startIndex = currentSelection.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                    onSelectionChanged = { index ->
                        if (index in items.indices) {
                            currentSelection = items[index]
                            onSelectionChange(currentSelection)
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelectionChange(currentSelection)
                    onConfirm()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Wheel number picker ported directly from Komikku's WheelPicker.kt.
 */
@Composable
fun WheelNumberPicker(
    items: List<Number>,
    modifier: Modifier = Modifier,
    startIndex: Int = 0,
    size: DpSize = DpSize(120.dp, 128.dp),
    onSelectionChanged: (index: Int) -> Unit = {},
    onClick: (() -> Unit)? = null,
    backgroundContent: (@Composable (size: DpSize) -> Unit)? = {
        WheelPickerDefaults.Background(size = it)
    },
) {
    val haptic = LocalHapticFeedback.current
    val safeStartIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = safeStartIndex)

    var internalIndex by remember { mutableIntStateOf(safeStartIndex) }

    LaunchedEffect(lazyListState, onSelectionChanged) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .map { calculateSnappedItemIndex(lazyListState) }
            .distinctUntilChanged()
            .collectLatest { snappedIndex ->
                if (snappedIndex != internalIndex) {
                    internalIndex = snappedIndex
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (_: Exception) {}
                    onSelectionChanged(snappedIndex)
                }
            }
    }

    Box(
        modifier = modifier
            .height(size.height)
            .width(size.width)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        backgroundContent?.invoke(size)

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(vertical = size.height / ROW_COUNT * ((ROW_COUNT - 1) / 2)),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(items) { index, item ->
                Box(
                    modifier = Modifier
                        .height(size.height / ROW_COUNT)
                        .fillMaxWidth()
                        .alpha(
                            calculateAnimatedAlpha(
                                lazyListState = lazyListState,
                                index = index,
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    WheelPickerDefaults.Item(text = item.toString())
                }
            }
        }
    }
}

private fun LazyListState.snapOffsetForItem(itemInfo: LazyListItemInfo): Int {
    val startScrollOffset = 0
    val endScrollOffset = layoutInfo.let { it.viewportEndOffset - it.afterContentPadding }
    return startScrollOffset + (endScrollOffset - startScrollOffset - itemInfo.size) / 2
}

private fun LazyListState.distanceToSnapForIndex(index: Int): Int {
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (itemInfo != null) {
        return itemInfo.offset - snapOffsetForItem(itemInfo)
    }
    return 0
}

private fun calculateAnimatedAlpha(
    lazyListState: LazyListState,
    index: Int,
): Float {
    val distanceToIndexSnap = lazyListState.distanceToSnapForIndex(index).absoluteValue
    val viewPortHeight = lazyListState.layoutInfo.viewportSize.height.toFloat()
    if (viewPortHeight <= 0f) return 1f
    val singleViewPortHeight = viewPortHeight / ROW_COUNT
    return if (distanceToIndexSnap in 0..singleViewPortHeight.toInt()) {
        (1.2f - (distanceToIndexSnap / singleViewPortHeight)).coerceIn(0.2f, 1f)
    } else {
        0.2f
    }
}

private fun calculateSnappedItemIndex(lazyListState: LazyListState): Int {
    return lazyListState.layoutInfo.visibleItemsInfo
        .maxByOrNull { calculateAnimatedAlpha(lazyListState, it.index) }
        ?.index ?: 0
}

object WheelPickerDefaults {
    @Composable
    fun Background(size: DpSize) {
        Surface(
            modifier = Modifier.size(size.width, size.height / ROW_COUNT),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            content = {},
        )
    }

    @Composable
    fun Item(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

