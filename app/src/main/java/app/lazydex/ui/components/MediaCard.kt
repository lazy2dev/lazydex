package app.lazydex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.data.local.LibraryDisplayMode
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.UserStatus
import app.lazydex.ui.theme.StatusCompleted
import app.lazydex.ui.theme.StatusDropped
import app.lazydex.ui.theme.StatusInProgress
import app.lazydex.ui.theme.StatusOnHold
import app.lazydex.ui.theme.StatusPlanTo



@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    displayMode: LibraryDisplayMode? = null,
    isGridView: Boolean = true,
    showProgressBadge: Boolean = true,
    showStatusBadge: Boolean = true,
    showScoreBadge: Boolean = true,
    showCategoryBadge: Boolean = false,
) {
    val effectiveMode = displayMode ?: if (isGridView) LibraryDisplayMode.COMPACT_GRID else LibraryDisplayMode.LIST
    val relativeTime = remember(item.lastUpdated) { getRelativeTime(item.lastUpdated) }

    val progressLabel = remember(item.currentProgress, item.totalItems, item.category) {
        val totalStr = item.totalItems?.toString() ?: "?"
        val unit = when (item.category) {
            MediaCategory.NOVEL, MediaCategory.MANGA -> "Ch."
            MediaCategory.ANIME, MediaCategory.TV -> "Ep."
            MediaCategory.GAME -> "Progress:"
            MediaCategory.MOVIE -> "Movie:"
        }
        "$unit ${item.currentProgress} / $totalStr"
    }

    val statusColor = when (item.userStatus) {
        UserStatus.READING, UserStatus.WATCHING, UserStatus.PLAYING -> StatusInProgress
        UserStatus.COMPLETED -> StatusCompleted
        UserStatus.ON_HOLD -> StatusOnHold
        UserStatus.DROPPED -> StatusDropped
        UserStatus.PLAN_TO -> StatusPlanTo
    }

    val hasStartBadges = showStatusBadge || (showProgressBadge && item.currentProgress > 0)
    val hasEndBadges = (showScoreBadge && item.rating != null) || showCategoryBadge

    when (effectiveMode) {
        LibraryDisplayMode.LIST -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    coverImagePath = item.coverImagePath,
                    title = item.title,
                    coverImageUrl = item.coverImageUrl,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .size(width = 48.dp, height = 72.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = relativeTime,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showProgressBadge) {
                            Text(
                                text = progressLabel,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showScoreBadge && item.rating != null) {
                                Text(
                                    text = "${item.rating}★",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF1C40F)
                                )
                            }
                            if (showCategoryBadge) {
                                CategoryBadge(category = item.category)
                            }
                            if (showStatusBadge) {
                                StatusBadge(status = item.userStatus)
                            }
                        }
                    }
                }
            }
        }

        LibraryDisplayMode.COMPACT_GRID, LibraryDisplayMode.COVER_ONLY_GRID -> {
            val showTitle = effectiveMode == LibraryDisplayMode.COMPACT_GRID
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClick)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                ) {
                    CoverImage(
                        coverImagePath = item.coverImagePath,
                        title = item.title,
                        coverImageUrl = item.coverImageUrl,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxSize()
                    )

                    if (showTitle) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        1f to Color(0xAA000000),
                                    )
                                )
                                .fillMaxHeight(0.33f)
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                        )
                        Row(
                            modifier = Modifier.align(Alignment.BottomStart),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = item.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color.White,
                                    shadow = Shadow(
                                        color = Color.Black,
                                        blurRadius = 4f,
                                    ),
                                ),
                            )
                        }
                    }

                    if (hasStartBadges) {
                        BadgeGroup(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                        ) {
                            if (showStatusBadge) {
                                Badge(
                                    imageVector = item.userStatus.icon(),
                                    color = statusColor,
                                    iconColor = Color.White,
                                )
                            }
                            if (showProgressBadge && item.currentProgress > 0) {
                                Badge(
                                    text = "${item.currentProgress}",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    textColor = MaterialTheme.colorScheme.onTertiary,
                                )
                            }
                        }
                    }

                    if (hasEndBadges) {
                        BadgeGroup(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        ) {
                            if (showScoreBadge && item.rating != null) {
                                Badge(
                                    text = "${item.rating}★",
                                    color = MaterialTheme.colorScheme.secondary,
                                    textColor = MaterialTheme.colorScheme.onSecondary,
                                )
                            }
                            if (showCategoryBadge) {
                                Badge(
                                    imageVector = item.category.icon(),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }

        LibraryDisplayMode.COMFORTABLE_GRID, LibraryDisplayMode.PANORAMA_COMFORTABLE_GRID -> {
            val isPanorama = effectiveMode == LibraryDisplayMode.PANORAMA_COMFORTABLE_GRID
            val coverRatio = if (isPanorama) (3f / 2f) else (2f / 3f)

            Column(
                modifier = modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClick)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(coverRatio)
                ) {
                    CoverImage(
                        coverImagePath = item.coverImagePath,
                        title = item.title,
                        coverImageUrl = item.coverImageUrl,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxSize()
                    )

                    if (hasStartBadges) {
                        BadgeGroup(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                        ) {
                            if (showStatusBadge) {
                                Badge(
                                    imageVector = item.userStatus.icon(),
                                    color = statusColor,
                                    iconColor = Color.White,
                                )
                            }
                            if (showProgressBadge && item.currentProgress > 0) {
                                Badge(
                                    text = "${item.currentProgress}",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    textColor = MaterialTheme.colorScheme.onTertiary,
                                )
                            }
                        }
                    }

                    if (hasEndBadges) {
                        BadgeGroup(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        ) {
                            if (showScoreBadge && item.rating != null) {
                                Badge(
                                    text = "${item.rating}★",
                                    color = MaterialTheme.colorScheme.secondary,
                                    textColor = MaterialTheme.colorScheme.onSecondary,
                                )
                            }
                            if (showCategoryBadge) {
                                Badge(
                                    imageVector = item.category.icon(),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = item.title,
                    modifier = Modifier.padding(4.dp),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    minLines = if (isPanorama) 1 else 2,
                    maxLines = if (isPanorama) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun getRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Never"
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 0) return "Just now"
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

