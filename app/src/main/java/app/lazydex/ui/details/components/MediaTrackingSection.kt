package app.lazydex.ui.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.UserStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MediaTrackingSection(
    mediaType: MediaType,
    userStatus: UserStatus,
    currentProgress: Int,
    totalItems: Int?,
    rating: Double?,
    startDate: Long?,
    endDate: Long?,
    hasSecondaryProgress: Boolean,
    secondaryUnitName: String? = null,
    secondaryProgress: String,
    secondaryProgressTotal: String,
    notes: String,
    onStatusClick: () -> Unit,
    onProgressClick: () -> Unit,
    onScoreClick: () -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onSecondaryStep: (Double) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unitLabel = when (mediaType) {
        MediaType.MANGA, MediaType.NOVEL -> "Ch."
        MediaType.ANIME, MediaType.TV -> "Ep."
        MediaType.GAME -> "Hrs"
        MediaType.MOVIE -> "Part"
    }

    val progressDisplay = if (totalItems != null && totalItems > 0) {
        "$unitLabel $currentProgress / $totalItems"
    } else {
        "$unitLabel $currentProgress"
    }

    val scoreDisplay = rating?.let {
        String.format(Locale.US, "%.1f ★", it)
    }

    var localNotes by remember(notes) { mutableStateOf(notes) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header (Komikku ChapterHeader style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tracking & Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // 3x2 Matrix Card (Komikku TrackInfoDialogHome style)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Column {
                // Row 1: Status | Progress | Score
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    TrackMatrixCell(
                        label = "STATUS",
                        value = userStatus.displayName,
                        onClick = onStatusClick,
                        modifier = Modifier.weight(1f),
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    TrackMatrixCell(
                        label = "PROGRESS",
                        value = progressDisplay,
                        onClick = onProgressClick,
                        modifier = Modifier.weight(1f),
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    TrackMatrixCell(
                        label = "SCORE",
                        value = scoreDisplay,
                        placeholder = "—",
                        onClick = onScoreClick,
                        modifier = Modifier.weight(1f),
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Row 2: Started date | Finished date
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    TrackMatrixCell(
                        label = "STARTED",
                        value = startDate?.let { formatTrackDate(it) },
                        placeholder = "—",
                        onClick = onStartDateClick,
                        modifier = Modifier.weight(1f),
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    TrackMatrixCell(
                        label = "FINISHED",
                        value = endDate?.let { formatTrackDate(it) },
                        placeholder = "—",
                        onClick = onEndDateClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Secondary Volume Progress (if applicable)
        if (hasSecondaryProgress) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${secondaryUnitName ?: "Volume"} Progress",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalIconButton(
                            onClick = { onSecondaryStep(-1.0) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrement", modifier = Modifier.size(16.dp))
                        }

                        Text(
                            text = "${secondaryProgress.ifEmpty { "0" }} / ${secondaryProgressTotal.ifEmpty { "—" }}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )

                        FilledTonalIconButton(
                            onClick = { onSecondaryStep(1.0) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increment", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Personal Notes Section
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = localNotes,
                    onValueChange = {
                        localNotes = it
                        onNotesChange(it)
                    },
                    placeholder = { Text("Write personal notes or comments...") },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun TrackMatrixCell(
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "—",
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (!value.isNullOrBlank()) value else placeholder,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (!value.isNullOrBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatTrackDate(epochMillis: Long): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(epochMillis))
}
