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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.data.local.LibraryDisplayMode
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaType
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
    val userStatus: UserStatus = UserStatus.PLAN_TO,
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
    if (secondaryProgress > 0.0 || (secondaryProgressTotal != null && secondaryProgressTotal > 0.0)) {
        return true
    }
    return when (typeId) {
        "typ_novel_ln", "typ_manga_jp", "typ_tv", "typ_anime_series" -> true
        else -> false
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
    modifier: Modifier = Modifier,
) {
    val displayProgress = if (item.progress > 0.0) item.progress else item.currentProgress.toDouble()
    if (!showProgressBadge || displayProgress <= 0.0) return

    BadgeGroup(modifier = modifier) {
        Badge(
            text = formatProgress(displayProgress),
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
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
    displayMode: LibraryDisplayMode? = null,
    isGridView: Boolean = true,
    showProgressBadge: Boolean = true,
    showStatusBadge: Boolean = true,
    showScoreBadge: Boolean = true,
    blurAdultCovers: Boolean = false,
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
        blurAdultCovers = blurAdultCovers,
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
    blurAdultCovers: Boolean = false,
) {
    val effectiveMode = displayMode ?: if (isGridView) LibraryDisplayMode.COMPACT_GRID else LibraryDisplayMode.LIST
    val isAdultObscured = item.isAdult && blurAdultCovers

    val statusColor = when (item.userStatus) {
        UserStatus.READING, UserStatus.WATCHING, UserStatus.PLAYING -> StatusInProgress
        UserStatus.COMPLETED -> StatusCompleted
        UserStatus.ON_HOLD -> StatusOnHold
        UserStatus.DROPPED -> StatusDropped
        UserStatus.PLAN_TO -> StatusPlanTo
    }

    when (effectiveMode) {
        LibraryDisplayMode.LIST -> {
            val relativeTime = remember(item.lastUpdated) { getRelativeTime(item.lastUpdated) }
            val resolvedFormat = remember(item.typeId, item.extraData, item.mediaType, formatName) {
                resolveFormatName(item.typeId, item.getExtra("format"), item.mediaType, formatName)
            }
            val resolvedHasSecondary = remember(item.typeId, item.secondaryProgress, item.secondaryProgressTotal, hasSecondaryProgress) {
                resolveHasSecondaryProgress(
                    typeId = item.typeId,
                    secondaryProgress = item.secondaryProgress,
                    secondaryProgressTotal = item.secondaryProgressTotal,
                    hasSecondaryOverride = hasSecondaryProgress,
                )
            }
            val displayProgress = if (item.progress > 0.0) item.progress else item.currentProgress.toDouble()
            val displayProgressTotal = item.progressTotal ?: item.totalItems?.toDouble()
            val displaySecondaryProgress = item.secondaryProgress
            val displaySecondaryProgressTotal = item.secondaryProgressTotal

            val primaryUnitPrefix = remember(item.mediaType) { resolveUnitPrefix(item.mediaType, isSecondary = false) }
            val secondaryUnitPrefix = remember(item.mediaType) { resolveUnitPrefix(item.mediaType, isSecondary = true) }

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
                    isObscured = isAdultObscured,
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

                    // Row 3: Primary Counter (Chapters/Episodes)
                    val primaryText = "$primaryUnitPrefix ${formatProgress(displayProgress)} / ${displayProgressTotal?.takeIf { it > 0 }?.let { formatProgress(it) } ?: "?"}"
                    Text(
                        text = primaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Row 4: Secondary Counter (Volumes/Seasons) - ONLY if hasSecondaryProgress == true
                    if (resolvedHasSecondary) {
                        val secondaryText = "$secondaryUnitPrefix ${formatProgress(displaySecondaryProgress)} / ${displaySecondaryProgressTotal?.takeIf { it > 0 }?.let { formatProgress(it) } ?: "?"}"
                        Text(
                            text = secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
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
                        isObscured = isAdultObscured,
                        modifier = Modifier.fillMaxSize()
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
                        modifier = Modifier.align(Alignment.TopStart),
                    )

                    CardEndBadges(
                        statusColor = statusColor,
                        showStatusBadge = showStatusBadge,
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
                        isObscured = isAdultObscured,
                        modifier = Modifier.fillMaxSize()
                    )

                    CardStartBadges(
                        item = item,
                        showProgressBadge = showProgressBadge,
                        modifier = Modifier.align(Alignment.TopStart),
                    )

                    CardEndBadges(
                        statusColor = statusColor,
                        showStatusBadge = showStatusBadge,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
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
