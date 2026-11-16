@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.lazydex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.data.local.LibraryDisplayMode
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
import app.lazydex.ui.theme.StatusCompleted
import app.lazydex.ui.theme.StatusDropped
import app.lazydex.ui.theme.StatusInProgress
import app.lazydex.ui.theme.StatusOnHold
import app.lazydex.ui.theme.StatusPlanTo
import app.lazydex.ui.theme.getRatingColor

@Immutable
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
    val userStatus: UserStatus = UserStatus.PLANNING,
    val averageScore: Int? = null,
    val lastUpdated: Long = 0L,
    val mediaType: MediaType = MediaType.NOVEL,
)

fun MediaItem.toMediaCardUiState(
    formatName: String? = null,
    hasSecondaryProgress: Boolean? = null,
    isDirtyLocal: Boolean = false,
): MediaCardUiState {
    val resolvedFormat = resolveFormatName(typeId, getExtra("format"), mediaType, formatName)
    val resolvedSecondary = resolveHasSecondaryProgress(
        typeId = typeId,
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
        averageScore = averageScore,
        lastUpdated = lastUpdated,
        mediaType = mediaType,
    )
}

fun MediaCardUiState.toMediaItem(): MediaItem {
    val calcRating = scoreRaw?.let { it / 20.0 }
    return MediaItem(
        id = id,
        type = when (mediaType) {
            MediaType.NOVEL -> "NOVEL"
            MediaType.MANGA -> "MANGA"
            MediaType.ANIME, MediaType.MOVIE, MediaType.TV -> "ANIME"
            MediaType.GAME -> "GAME"
        },
        format = when (mediaType) {
            MediaType.NOVEL -> "NOVEL"
            MediaType.MANGA -> "MANGA"
            MediaType.MOVIE -> "MOVIE"
            MediaType.TV, MediaType.ANIME -> "TV"
            MediaType.GAME -> "PC"
        },
        countryOfOrigin = null,
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
        averageScore = averageScore,
        lastUpdated = lastUpdated,
        dateAdded = lastUpdated
    )
}

fun resolveFormatName(
    typeId: String,
    extraFormat: String? = null,
    mediaType: MediaType? = null,
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
            "WEB_NOVEL" -> "Web Novel"
            "LIGHT_NOVEL" -> "Light Novel"
            "MANHWA" -> "Manhwa"
            "MANHUA" -> "Manhua"
            "PC" -> "PC"
            "ANDROID" -> "Android"
            "CONSOLE" -> "Console"
            "CROSS_PLATFORM" -> "Cross Platform"
            else -> extraFormat.replace('_', ' ').split(" ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
    }
    return when (typeId.uppercase()) {
        "TYP_NOVEL_LN", "LIGHT_NOVEL" -> "Light Novel"
        "TYP_NOVEL_CN", "TYP_NOVEL_KR", "WEB_NOVEL" -> "Web Novel"
        "TYP_NOVEL", "NOVEL" -> "Novel"
        "TYP_MANHWA", "MANHWA" -> "Manhwa"
        "TYP_MANHUA", "MANHUA" -> "Manhua"
        "TYP_MANGA_JP", "TYP_MANGA", "MANGA" -> "Manga"
        "TYP_ANIME_SERIES", "TV" -> "TV Series"
        "TYP_ANIME_MOVIE", "MOVIE" -> "Movie"
        "TYP_ANIME_OVA", "OVA" -> "OVA"
        "TYP_ANIME", "ANIME" -> "Anime"
        "TYP_MOVIE" -> "Movie"
        "TYP_TV" -> "TV"
        "TYP_GAME", "GAME", "PC" -> "Game"
        else -> mediaType?.displayName ?: ""
    }
}

fun resolveHasSecondaryProgress(
    typeId: String,
    secondaryProgress: Double = 0.0,
    secondaryProgressTotal: Double? = null,
    hasSecondaryOverride: Boolean? = null,
): Boolean {
    if (hasSecondaryOverride != null) return hasSecondaryOverride
    if (typeId.startsWith("typ_anime", ignoreCase = true) || typeId.equals("anime", ignoreCase = true)) {
        return false
    }
    if (secondaryProgress > 0.0 || (secondaryProgressTotal != null && secondaryProgressTotal > 0.0)) {
        return true
    }
    return when (typeId) {
        "typ_novel_ln", "typ_manga_jp", "typ_tv" -> true
        else -> false
    }
}

fun formatProgress(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toString().removeSuffix(".0")
    }
}

fun resolveUnitPrefix(mediaType: MediaType, isSecondary: Boolean = false): String {
    return if (isSecondary) {
        when (mediaType) {
            MediaType.TV, MediaType.ANIME -> "Season"
            MediaType.GAME -> "Part"
            else -> "Vol."
        }
    } else {
        when (mediaType) {
            MediaType.ANIME, MediaType.TV -> "Ep."
            MediaType.MOVIE -> "Min."
            MediaType.GAME -> "Hr."
            else -> "Ch."
        }
    }
}

@Composable
private fun CardStartBadges(
    item: MediaItem,
    showProgressBadge: Boolean,
    showScoreBadge: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val displayProgress = if (item.progress > 0.0) item.progress else item.currentProgress.toDouble()
    val hasProgress = showProgressBadge && displayProgress > 0.0
    val rating = item.rating
    val hasScore = showScoreBadge && rating != null

    if (!hasProgress && !hasScore) return

    BadgeGroup(modifier = modifier) {
        if (hasProgress) {
            Badge(
                text = formatProgress(displayProgress),
                color = MaterialTheme.colorScheme.tertiary,
                textColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
        if (hasScore) {
            Badge(
                text = "★ %.1f".format(rating),
                color = getRatingColor(rating),
                textColor = Color.White,
            )
        }
    }
}

@Composable
private fun CardEndBadges(
    statusColor: Color,
    showStatusBadge: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!showStatusBadge) return

    Box(
        modifier = modifier
            .padding(6.dp)
            .size(8.dp)
            .background(color = statusColor, shape = CircleShape)
    )
}

@Composable
fun MediaCard(
    uiState: MediaCardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onIncrementProgress: ((String) -> Unit)? = null,
    onOpenProgressDialog: ((MediaItem) -> Unit)? = null,
    isSelected: Boolean = false,
    displayMode: LibraryDisplayMode? = null,
    isGridView: Boolean = true,
    showProgressBadge: Boolean = true,
    showStatusBadge: Boolean = true,
    showScoreBadge: Boolean = true,
    blurAdultCovers: Boolean = false,
    activeTypeName: String? = null,
    activeStatusFilter: StatusFilter? = null,
) {
    MediaCard(
        item = uiState.toMediaItem(),
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        onIncrementProgress = onIncrementProgress,
        onOpenProgressDialog = onOpenProgressDialog,
        isSelected = isSelected,
        displayMode = displayMode,
        isGridView = isGridView,
        showProgressBadge = showProgressBadge,
        showStatusBadge = showStatusBadge,
        showScoreBadge = showScoreBadge,
        formatName = uiState.formatName,
        hasSecondaryProgress = uiState.hasSecondaryProgress,
        isDirtyLocal = uiState.isDirtyLocal,
        blurAdultCovers = blurAdultCovers,
        activeTypeName = activeTypeName,
        activeStatusFilter = activeStatusFilter,
    )
}

@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onIncrementProgress: ((String) -> Unit)? = null,
    onOpenProgressDialog: ((MediaItem) -> Unit)? = null,
    isSelected: Boolean = false,
    displayMode: LibraryDisplayMode? = null,
    isGridView: Boolean = true,
    showProgressBadge: Boolean = true,
    showStatusBadge: Boolean = true,
    showScoreBadge: Boolean = true,
    formatName: String? = null,
    hasSecondaryProgress: Boolean? = null,
    isDirtyLocal: Boolean = false,
    blurAdultCovers: Boolean = false,
    activeTypeName: String? = null,
    activeStatusFilter: StatusFilter? = null,
) {
    val effectiveMode = displayMode ?: if (isGridView) LibraryDisplayMode.COMPACT_GRID else LibraryDisplayMode.LIST
    val isAdultObscured = item.isAdult && blurAdultCovers

    val statusColor = item.userStatus.color()

    when (effectiveMode) {
        LibraryDisplayMode.LIST -> {
            val resolvedFormat = remember(item.typeId, item.extraData, item.mediaType, formatName) {
                resolveFormatName(item.typeId, item.getExtra("format"), item.mediaType, formatName)
            }
            val displayProgress = if (item.progress > 0.0) item.progress else item.currentProgress.toDouble()
            val displayProgressTotal = item.progressTotal ?: item.totalItems?.toDouble()
            val isCompleted = (item.userStatus == UserStatus.COMPLETED) ||
                (displayProgressTotal != null && displayProgressTotal > 0 && displayProgress >= displayProgressTotal)
            val isDropped = item.userStatus == UserStatus.DROPPED
            val isPaused = item.userStatus == UserStatus.PAUSED

            val rating = item.rating
            val avgScore = item.averageScore

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                } else {
                    Color.Transparent
                },
                modifier = modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Cover Image (68 x 96dp vertical poster)
                    Box(
                        modifier = Modifier.size(width = 68.dp, height = 96.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        CoverImage(
                            coverImagePath = item.coverImagePath,
                            title = item.title,
                            coverImageUrl = item.coverImageUrl,
                            shape = RoundedCornerShape(8.dp),
                            isObscured = isAdultObscured,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (isSelected) alpha = 0.76f
                                }
                        )

                        // Capsule Badge: Community averageScore & user status indicator
                        if (avgScore != null || isCompleted || isDropped || isPaused) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.72f),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (avgScore != null) {
                                        Text(
                                            text = "🙂 $avgScore%",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    if (isCompleted) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Completed",
                                            tint = StatusCompleted,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    } else if (isDropped) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dropped",
                                            tint = StatusDropped,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    } else if (isPaused) {
                                        Icon(
                                            imageVector = Icons.Default.Pause,
                                            contentDescription = "Paused",
                                            tint = StatusOnHold,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 2. Middle Column: Title at top, Format & Score + Progress bar at bottom
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 2,
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

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (resolvedFormat.isNotBlank()) {
                                        Text(
                                            text = resolvedFormat,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    val scoreText = if (rating != null) {
                                        val formatted = if (rating % 1.0 == 0.0) rating.toInt().toString() else "%.1f".format(rating)
                                        "Score: $formatted"
                                    } else {
                                        "Score: ?"
                                    }
                                    Text(
                                        text = scoreText,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val progressCountText = "${formatProgress(displayProgress)} / ${displayProgressTotal?.takeIf { it > 0 }?.let { formatProgress(it) } ?: "?"}"
                                Text(
                                    text = progressCountText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Progress Bar ONLY in Middle Column (does NOT go below cover or right side)
                            val progressFraction = remember(displayProgress, displayProgressTotal, isCompleted) {
                                if (isCompleted) 1f
                                else if (displayProgressTotal != null && displayProgressTotal > 0) {
                                    (displayProgress / displayProgressTotal).toFloat().coerceIn(0f, 1f)
                                } else if (displayProgress > 0) 1f
                                else 0f
                            }
                            if (progressFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progressFraction)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 3. Right Column: dedicated '+' button centered vertically
                    Box(
                        modifier = Modifier
                            .height(96.dp)
                            .width(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isCompleted && onIncrementProgress != null) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .combinedClickable(
                                        onClick = { onIncrementProgress(item.id) },
                                        onLongClick = { onOpenProgressDialog?.invoke(item) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increment progress",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
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
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        else Modifier
                    )
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                        isObscured = isAdultObscured,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (isSelected) alpha = 0.76f
                            }
                    )

                    if (showTitle) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f),
                                        )
                                    )
                                )
                                .fillMaxHeight(0.33f)
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                        )
                        Text(
                            text = item.title,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
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

                    CardStartBadges(
                        item = item,
                        showProgressBadge = showProgressBadge,
                        showScoreBadge = showScoreBadge,
                        modifier = Modifier.align(Alignment.TopStart),
                    )

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        CardEndBadges(
                            statusColor = statusColor,
                            showStatusBadge = showStatusBadge,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
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
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        else Modifier
                    )
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                        isObscured = isAdultObscured,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (isSelected) alpha = 0.76f
                            }
                    )

                    CardStartBadges(
                        item = item,
                        showProgressBadge = showProgressBadge,
                        showScoreBadge = showScoreBadge,
                        modifier = Modifier.align(Alignment.TopStart),
                    )

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        CardEndBadges(
                            statusColor = statusColor,
                            showStatusBadge = showStatusBadge,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }

                Text(
                    text = item.title,
                    modifier = Modifier.padding(4.dp),
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
