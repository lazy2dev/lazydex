package app.lazydex.ui.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.UserStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MediaTrackingSection(
    category: MediaCategory,
    userStatus: UserStatus,
    currentProgress: Int,
    totalItems: Int?,
    rating: Double?,
    startDate: Long?,
    endDate: Long?,
    hasSecondaryProgress: Boolean,
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
    val unitLabel = when (category) {
        MediaCategory.MANGA, MediaCategory.NOVEL -> "Ch."
        MediaCategory.ANIME, MediaCategory.TV -> "Ep."
        MediaCategory.GAME -> "Hrs"
        MediaCategory.MOVIE -> "Part"
    }

    val progressDisplay = if (totalItems != null && totalItems > 0) {
        "$unitLabel $currentProgress / $totalItems"
    } else {
        "$unitLabel $currentProgress"
    }

    val scoreDisplay = rating?.let {
        String.format(Locale.US, "%.1f ★", it)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header (Komikku ChapterHeader style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Tracking & Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = progressDisplay,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
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
                        modifier = Modifier.weight(1f),
                        text = userStatus.displayName,
                        onClick = onStatusClick,
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    TrackMatrixCell(
                        modifier = Modifier.weight(1f),
                        text = progressDisplay,
                        onClick = onProgressClick,
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    TrackMatrixCell(
                        modifier = Modifier.weight(1f),
                        text = scoreDisplay,
                        placeholder = "Score",
                        onClick = onScoreClick,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Row 2: Started date | Finished date
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    TrackMatrixCell(
                        modifier = Modifier.weight(1f),
                        text = startDate?.let { formatTrackDate(it) },
                        placeholder = "Started date",
                        onClick = onStartDateClick,
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    TrackMatrixCell(
                        modifier = Modifier.weight(1f),
                        text = endDate?.let { formatTrackDate(it) },
                        placeholder = "Finished date",
                        onClick = onEndDateClick,
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
                        text = "Volume Progress",
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
                    value = notes,
                    onValueChange = onNotesChange,
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
    modifier: Modifier = Modifier,
    text: String?,
    placeholder: String = "",
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!text.isNullOrBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.alpha(0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatTrackDate(epochMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMillis))
}

