package app.lazydex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
import app.lazydex.ui.theme.getRatingColor

data class MediaCardUiState(
    val id: String,
    val title: String,
    val formatName: String,
    val coverPath: String,
    val coverUrl: String?,
    val progress: Double,
    val progressTotal: Double?,
    val secondaryProgress: Double,
    val secondaryProgressTotal: Double?,
    val hasSecondaryProgress: Boolean,
    val isDirtyLocal: Boolean = false,
    val scoreRaw: Int? = null,
    val userStatus: UserStatus = UserStatus.PLAN_TO,
    val lastUpdated: Long = 0L,
    val category: MediaCategory = MediaCategory.NOVEL,
)

fun MediaItem.toMediaCardUiState(
    formatName: String? = null,
    hasSecondaryProgress: Boolean? = null,
    isDirtyLocal: Boolean = false,
): MediaCardUiState {
    val resolvedFormat = resolveFormatName(categoryId, getExtra("format"), category, formatName)
    val resolvedSecondary = resolveHasSecondaryProgress(
        categoryId = categoryId,
        secondaryProgress = secondaryProgress,
        secondaryProgressTotal = secondaryProgressTotal,
        hasSecondaryOverride = hasSecondaryProgress,
    )
    val calcScoreRaw = rating?.let { (it * 20).toInt() }
    val displayProgress = if (progress > 0.0) progress else currentProgress.toDouble()
    val displayProgressTotal = progressTotal ?: totalItems?.toDouble()
    return MediaCardUiState(
        id = id,
        title = title,
        formatName = resolvedFormat,
        coverPath = coverImagePath,
        coverUrl = coverImageUrl,
        progress = displayProgress,
        progressTotal = displayProgressTotal,
        secondaryProgress = secondaryProgress,
        secondaryProgressTotal = secondaryProgressTotal,
        hasSecondaryProgress = resolvedSecondary,
        isDirtyLocal = isDirtyLocal,
        scoreRaw = calcScoreRaw,
        userStatus = userStatus,
        lastUpdated = lastUpdated,
        category = category,
    )
}

fun MediaCardUiState.toMediaItem(): MediaItem {
    val calcRating = scoreRaw?.let { it / 20.0 }
    return MediaItem(
        id = id,
        title = title,
        coverImagePath = coverPath,
        coverImageUrl = coverUrl,
        currentProgress = progress.toInt(),
        totalItems = progressTotal?.toInt(),
        progress = progress,
        progressTotal = progressTotal,
        secondaryProgress = secondaryProgress,
        secondaryProgressTotal = secondaryProgressTotal,
        userStatus = userStatus,
        rating = calcRating,
        lastUpdated = lastUpdated,
        dateAdded = lastUpdated,
        category = category,
    )
}

fun resolveFormatName(
    categoryId: String,
    extraFormat: String? = null,
    category: MediaCategory? = null,
    customFormat: String? = null,
): String {
    if (!customFormat.isNullOrBlank()) return customFormat
    if (!extraFormat.isNullOrBlank()) {
        return when (extraFormat.uppercase()) {
            "TV" -> "TV"
            "TV_SHORT" -> "TV Short"
            "MOVIE" -> "Movie"
            "SPECIAL" -> "Special"
            "OVA" -> "OVA"
            "ONA" -> "ONA"
            "MUSIC" -> "Music"
            "MANGA" -> "Manga"
            "NOVEL" -> "Light Novel"
            "ONE_SHOT" -> "One Shot"
            else -> extraFormat.replace('_', ' ').split(" ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
    }
    return when (categoryId) {
        "typ_novel_ln" -> "Light Novel"
        "typ_novel_cn", "typ_novel_kr" -> "Web Novel"
        "cat_novel" -> "Novel"
        "typ_manhwa" -> "Manhwa"
        "typ_manhua" -> "Manhua"
        "typ_manga_jp" -> "Manga"
        "cat_manga" -> "Manga"
        "typ_anime_series" -> "Anime Series"
        "typ_anime_movie" -> "Anime Movie"
        "typ_anime_ova" -> "OVA"
        "cat_anime" -> "Anime"
        "cat_movie" -> "Movie"
        "cat_tv" -> "TV"
        "cat_game" -> "Game"
        else -> category?.displayName ?: ""
    }
}

fun resolveHasSecondaryProgress(
    categoryId: String,
    secondaryProgress: Double = 0.0,
    secondaryProgressTotal: Double? = null,
    hasSecondaryOverride: Boolean? = null,
): Boolean {
    if (hasSecondaryOverride != null) return hasSecondaryOverride
    if (secondaryProgress > 0.0 || (secondaryProgressTotal != null && secondaryProgressTotal > 0.0)) {
        return true
    }
    return when (categoryId) {
        "typ_novel_ln", "typ_manga_jp", "cat_tv", "typ_anime_series" -> true
        else -> false
    }
}

fun resolveUnitPrefix(category: MediaCategory, isSecondary: Boolean = false): String {
    return if (isSecondary) {
        when (category) {
            MediaCategory.TV, MediaCategory.ANIME -> "Season"
            MediaCategory.GAME -> "Part"
            else -> "Vol."
        }
    } else {
        when (category) {
            MediaCategory.ANIME, MediaCategory.TV -> "Ep."
            MediaCategory.MOVIE -> "Min."
            MediaCategory.GAME -> "Hr."
            else -> "Ch."
        }
    }
}

private fun Color.darken(factor: Float = 0.65f): Color = Color(
    red = red * factor,
    green = green * factor,
    blue = blue * factor,
    alpha = alpha,
)

@Composable
private fun CardStartBadges(
    item: MediaItem,
    formatName: String,
    showStatusBadge: Boolean,
    showProgressBadge: Boolean,
    statusColor: Color,
    modifier: Modifier = Modifier,
) {
    val displayProgress = if (item.progress > 0.0) item.progress else item.currentProgress.toDouble()
    val displayTotal = item.progressTotal ?: item.totalItems?.toDouble()
    val hasProgress = showProgressBadge && (displayProgress > 0 || (displayTotal != null && displayTotal > 0))
    val hasFormat = formatName.isNotBlank()
    val hasStartBadges = showStatusBadge || hasProgress || hasFormat
    if (!hasStartBadges) return

    val baseColor = if (showStatusBadge) statusColor else MaterialTheme.colorScheme.tertiary
    val baseColorDark = remember(baseColor) { baseColor.darken(0.65f) }

    BadgeGroup(modifier = modifier) {
        if (hasFormat) {
            Badge(
                text = formatName,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hasProgress) {
            Badge(
                text = formatProgress(displayProgress),
                color = baseColor,
                textColor = Color.White,
            )
            if (displayTotal != null && displayTotal > 0) {
                Badge(
                    text = formatProgress(displayTotal),
                    color = baseColorDark,
                    textColor = Color.White.copy(alpha = 0.9f),
                )
            }
        } else if (showStatusBadge) {
            Badge(
                imageVector = item.userStatus.icon(),
                color = statusColor,
                iconColor = Color.White,
            )
        }
    }
}

@Composable
private fun CardEndBadges(
    item: MediaItem,
    showScoreBadge: Boolean,
    isDirtyLocal: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val rating = item.rating
    if ((!showScoreBadge || rating == null) && !isDirtyLocal) return

    val ratingText = remember(rating) {
        rating?.let { "%.1f".format(it) }
    }

    BadgeGroup(modifier = modifier) {
        if (isDirtyLocal) {
            Badge(
                imageVector = Icons.Default.Sync,
                color = MaterialTheme.colorScheme.tertiary,
                iconColor = Color.White,
            )
        }
        if (showScoreBadge && rating != null && ratingText != null) {
            Badge(
                text = ratingText,
                color = getRatingColor(rating),
                textColor = Color.White,
            )
        }
    }
}

@Composable
fun MediaCard(
    uiState: MediaCardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    displayMode: LibraryDisplayMode? = null,
    isGridView: Boolean = true,
    showProgressBadge: Boolean = true,
    showStatusBadge: Boolean = true,
    showScoreBadge: Boolean = true,
    onIncrementProgress: (() -> Unit)? = null,
    onSetProgress: ((Double) -> Unit)? = null,
    onSetSecondaryProgress: ((Double) -> Unit)? = null,
) {
    MediaCard(
        item = uiState.toMediaItem(),
        onClick = onClick,
        modifier = modifier,
        displayMode = displayMode,
        isGridView = isGridView,
        showProgressBadge = showProgressBadge,
        showStatusBadge = showStatusBadge,
        showScoreBadge = showScoreBadge,
        formatName = uiState.formatName,
        hasSecondaryProgress = uiState.hasSecondaryProgress,
        isDirtyLocal = uiState.isDirtyLocal,
        onIncrementProgress = onIncrementProgress,
        onSetProgress = onSetProgress,
        onSetSecondaryProgress = onSetSecondaryProgress,
    )
}

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
    formatName: String? = null,
    hasSecondaryProgress: Boolean? = null,
    isDirtyLocal: Boolean = false,
    onIncrementProgress: (() -> Unit)? = null,
    onSetProgress: ((Double) -> Unit)? = null,
    onSetSecondaryProgress: ((Double) -> Unit)? = null,
) {
    val effectiveMode = displayMode ?: if (isGridView) LibraryDisplayMode.COMPACT_GRID else LibraryDisplayMode.LIST
    val relativeTime = remember(item.lastUpdated) { getRelativeTime(item.lastUpdated) }

    val statusColor = when (item.userStatus) {
        UserStatus.READING, UserStatus.WATCHING, UserStatus.PLAYING -> StatusInProgress
        UserStatus.COMPLETED -> StatusCompleted
        UserStatus.ON_HOLD -> StatusOnHold
        UserStatus.DROPPED -> StatusDropped
        UserStatus.PLAN_TO -> StatusPlanTo
    }

    val resolvedFormat = remember(item.categoryId, item.extraData, item.category, formatName) {
        resolveFormatName(item.categoryId, item.getExtra("format"), item.category, formatName)
    }
    val resolvedHasSecondary = remember(item.categoryId, item.secondaryProgress, item.secondaryProgressTotal, hasSecondaryProgress) {
        resolveHasSecondaryProgress(
            categoryId = item.categoryId,
            secondaryProgress = item.secondaryProgress,
            secondaryProgressTotal = item.secondaryProgressTotal,
            hasSecondaryOverride = hasSecondaryProgress,
        )
    }

    val displayProgress = if (item.progress > 0.0) item.progress else item.currentProgress.toDouble()
    val displayProgressTotal = item.progressTotal ?: item.totalItems?.toDouble()
    val displaySecondaryProgress = item.secondaryProgress
    val displaySecondaryProgressTotal = item.secondaryProgressTotal

    val primaryUnitPrefix = remember(item.category) { resolveUnitPrefix(item.category, isSecondary = false) }
    val secondaryUnitPrefix = remember(item.category) { resolveUnitPrefix(item.category, isSecondary = true) }

    var showPrimaryDialog by remember { mutableStateOf(false) }
    var showSecondaryDialog by remember { mutableStateOf(false) }

    if (showPrimaryDialog) {
        TrackProgressDialog(
            initialValue = displayProgress,
            maxValue = displayProgressTotal,
            title = "Set $primaryUnitPrefix",
            unitLabel = primaryUnitPrefix,
            onConfirm = { newProgress ->
                onSetProgress?.invoke(newProgress)
                showPrimaryDialog = false
            },
            onDismissRequest = { showPrimaryDialog = false },
        )
    }

    if (showSecondaryDialog && resolvedHasSecondary) {
        TrackProgressDialog(
            initialValue = displaySecondaryProgress,
            maxValue = displaySecondaryProgressTotal,
            title = "Set $secondaryUnitPrefix",
            unitLabel = secondaryUnitPrefix,
            onConfirm = { newSecProgress ->
                onSetSecondaryProgress?.invoke(newSecProgress)
                showSecondaryDialog = false
            },
            onDismissRequest = { showSecondaryDialog = false },
        )
    }

    when (effectiveMode) {
        LibraryDisplayMode.LIST -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    coverImagePath = item.coverImagePath,
                    title = item.title,
                    coverImageUrl = item.coverImageUrl,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.size(width = 54.dp, height = 78.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 78.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Row 1: Title + Sync Status Affordance + Relative Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isDirtyLocal) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Pending sync",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = relativeTime,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Row 2: Badges: Format Badge [ Manhwa ] [ In Progress ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (resolvedFormat.isNotBlank()) {
                            Badge(
                                text = resolvedFormat,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = RoundedCornerShape(4.dp),
                            )
                        }
                        if (showStatusBadge) {
                            Badge(
                                text = item.userStatus.displayName,
                                color = statusColor.copy(alpha = 0.15f),
                                textColor = statusColor,
                                shape = RoundedCornerShape(4.dp),
                            )
                        }
                        val rating = item.rating
                        if (showScoreBadge && rating != null) {
                            Badge(
                                text = "%.1f".format(rating),
                                color = getRatingColor(rating).copy(alpha = 0.15f),
                                textColor = getRatingColor(rating),
                                shape = RoundedCornerShape(4.dp),
                            )
                        }
                    }

                    // Row 3: Primary Counter (Chapters/Episodes) + [+1] Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val primaryText = "$primaryUnitPrefix ${formatProgress(displayProgress)} / ${displayProgressTotal?.takeIf { it > 0 }?.let { formatProgress(it) } ?: "?"}"
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showPrimaryDialog = true }
                                .padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = primaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        if (onIncrementProgress != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onIncrementProgress() },
                            ) {
                                Text(
                                    text = "+1",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    // Row 4: Secondary Counter (Volumes/Seasons) - ONLY if hasSecondaryProgress == true
                    if (resolvedHasSecondary) {
                        val secondaryText = "$secondaryUnitPrefix ${formatProgress(displaySecondaryProgress)} / ${displaySecondaryProgressTotal?.takeIf { it > 0 }?.let { formatProgress(it) } ?: "?"}"
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showSecondaryDialog = true }
                                .padding(vertical = 1.dp),
                        ) {
                            Text(
                                text = secondaryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                                    .weight(1f)
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
                            if (onIncrementProgress != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(end = 6.dp, bottom = 6.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onIncrementProgress() },
                                ) {
                                    Text(
                                        text = "+1",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }

                    CardStartBadges(
                        item = item,
                        formatName = resolvedFormat,
                        showStatusBadge = showStatusBadge,
                        showProgressBadge = showProgressBadge,
                        statusColor = statusColor,
                        modifier = Modifier.align(Alignment.TopStart),
                    )

                    CardEndBadges(
                        item = item,
                        showScoreBadge = showScoreBadge,
                        isDirtyLocal = isDirtyLocal,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
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

                    CardStartBadges(
                        item = item,
                        formatName = resolvedFormat,
                        showStatusBadge = showStatusBadge,
                        showProgressBadge = showProgressBadge,
                        statusColor = statusColor,
                        modifier = Modifier.align(Alignment.TopStart),
                    )

                    CardEndBadges(
                        item = item,
                        showScoreBadge = showScoreBadge,
                        isDirtyLocal = isDirtyLocal,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )

                    if (onIncrementProgress != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onIncrementProgress() },
                        ) {
                            Text(
                                text = "+1",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
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
