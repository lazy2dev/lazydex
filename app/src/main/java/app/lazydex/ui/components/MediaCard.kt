@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.lazydex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.SentimentSatisfied
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
    val resolvedFormat = resolveFormatName(
        typeId = typeId,
        extraFormat = format.ifBlank { getExtra("format") },
        mediaType = mediaType,
        customFormat = formatName,
        countryOfOrigin = countryOfOrigin,
    )
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
    val resolvedDomainFormat = when (formatName?.uppercase()) {
        "TV" -> "TV"
        "TV SHORT" -> "TV_SHORT"
        "MOVIE" -> "MOVIE"
        "SPECIAL" -> "SPECIAL"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "LIGHT NOVEL" -> "LIGHT_NOVEL"
        "WEB NOVEL" -> "WEB_NOVEL"
        "ONE SHOT" -> "ONE_SHOT"
        "MANHWA" -> "MANHWA"
        "MANHUA" -> "MANHUA"
        "PC" -> "PC"
        "ANDROID" -> "ANDROID"
        "CONSOLE" -> "CONSOLE"
        "WEB" -> "WEB"
        "CROSS PLATFORM" -> "CROSS_PLATFORM"
        else -> when (mediaType) {
            MediaType.NOVEL -> "NOVEL"
            MediaType.MANGA -> "MANGA"
            MediaType.MOVIE -> "MOVIE"
            MediaType.TV, MediaType.ANIME -> "TV"
            MediaType.GAME -> "PC"
        }
    }
    return MediaItem(
        id = id,
        type = when (mediaType) {
            MediaType.NOVEL -> "NOVEL"
            MediaType.MANGA -> "MANGA"
            MediaType.ANIME, MediaType.MOVIE, MediaType.TV -> "ANIME"
            MediaType.GAME -> "GAME"
        },
        format = resolvedDomainFormat,
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
    countryOfOrigin: String? = null,
): String {
    if (!customFormat.isNullOrBlank()) return customFormat

    val isKorean = countryOfOrigin.equals("KR", ignoreCase = true)
    val isChinese = countryOfOrigin.equals("CN", ignoreCase = true) || countryOfOrigin.equals("TW", ignoreCase = true)

    val cleanExtra = extraFormat?.trim()?.takeIf { it.isNotEmpty() }

    if (cleanExtra != null) {
        return when (val upper = cleanExtra.uppercase()) {
            "TV" -> "TV"
            "TV_SHORT" -> "TV Short"
            "MOVIE" -> "Movie"
            "SPECIAL" -> "Special"
            "OVA" -> "OVA"
            "ONA" -> "ONA"
            "MUSIC" -> "Music"
            "MANGA" -> when {
                isKorean -> "Manhwa"
                isChinese -> "Manhua"
                else -> "Manga"
            }
            "MANHWA" -> "Manhwa"
            "MANHUA" -> "Manhua"
            "ONE_SHOT" -> "One Shot"
            "DOUJINSHI" -> "Doujinshi"
            "LIGHT_NOVEL", "LN" -> "Light Novel"
            "WEB_NOVEL", "WN" -> "Web Novel"
            "NOVEL" -> when {
                isKorean || isChinese -> "Web Novel"
                else -> "Light Novel"
            }
            "PC" -> "PC"
            "ANDROID" -> "Android"
            "CONSOLE" -> "Console"
            "CROSS_PLATFORM" -> "Cross Platform"
            "WEB" -> "Web"
            "ANIME" -> "TV"
            "GAME" -> "PC"
            else -> cleanExtra.replace('_', ' ').split(" ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
    }

    return when (typeId.uppercase()) {
        "TYP_NOVEL_LN", "LIGHT_NOVEL" -> "Light Novel"
        "TYP_NOVEL_CN", "TYP_NOVEL_KR", "WEB_NOVEL" -> "Web Novel"
        "TYP_NOVEL", "NOVEL" -> when {
            isKorean || isChinese -> "Web Novel"
            else -> "Novel"
        }
        "TYP_MANHWA", "MANHWA" -> "Manhwa"
        "TYP_MANHUA", "MANHUA" -> "Manhua"
        "TYP_MANGA_JP", "TYP_MANGA", "MANGA" -> when {
            isKorean -> "Manhwa"
            isChinese -> "Manhua"
            else -> "Manga"
        }
        "TYP_ANIME_SERIES", "TV" -> "TV"
        "TYP_ANIME_MOVIE", "MOVIE" -> "Movie"
        "TYP_ANIME_OVA", "OVA" -> "OVA"
        "TYP_ANIME", "ANIME" -> "TV"
        "TYP_MOVIE" -> "Movie"
        "TYP_TV" -> "TV"
        "TYP_GAME", "GAME", "PC" -> "PC"
        else -> when (mediaType) {
            MediaType.ANIME -> "TV"
            MediaType.GAME -> "PC"
            MediaType.MANGA -> when {
                isKorean -> "Manhwa"
                isChinese -> "Manhua"
                else -> "Manga"
            }
            MediaType.NOVEL -> when {
                isKorean || isChinese -> "Web Novel"
                else -> "Novel"
            }
            MediaType.MOVIE -> "Movie"
            MediaType.TV -> "TV"
            null -> ""
        }
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
    isGridView: Boolean = false,
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
    isGridView: Boolean = false,
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
            val resolvedFormat = remember(item.typeId, item.format, item.extraData, item.mediaType, item.countryOfOrigin, formatName) {
                resolveFormatName(
                    typeId = item.typeId,
                    extraFormat = item.format.ifBlank { item.getExtra("format") },
                    mediaType = item.mediaType,
                    customFormat = formatName,
                    countryOfOrigin = item.countryOfOrigin,
                )
            }
            val displayProgress = if (item.progress > 0.0) item.progress else item.currentProgress.toDouble()
            val displayProgressTotal = item.progressTotal ?: item.totalItems?.toDouble()
            val isCompleted = (item.userStatus == UserStatus.COMPLETED) ||
                (displayProgressTotal != null && displayProgressTotal > 0 && displayProgress >= displayProgressTotal)
            val isDropped = item.userStatus == UserStatus.DROPPED
            val isPaused = item.userStatus == UserStatus.PAUSED

            val rating = item.rating
            val avgScore = item.averageScore

            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Cover Image (Elevated, 72dp wide, fills exact card height so bg never protrudes)
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .fillMaxHeight()
                        .heightIn(min = 104.dp)
                        .shadow(3.dp, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 2.dp, bottomEnd = 2.dp))
                        .zIndex(1f)
                ) {
                    CoverImage(
                        coverImagePath = item.coverImagePath,
                        title = item.title,
                        coverImageUrl = item.coverImageUrl,
                        shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 2.dp, bottomEnd = 2.dp),
                        isObscured = isAdultObscured,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (isSelected) alpha = 0.76f
                            }
                    )

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

                // 2. Emerging Card Body Surface (Flat left edge 0.dp, rounded right edge 14.dp, matches project surface)
                Surface(
                    shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 14.dp, bottomEnd = 14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .offset(x = (-2).dp)
                        .zIndex(0f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Middle Column
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 88.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Row 1: Title (Full width, zero cutoff)
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

                            // Row 2: Format and Transparent Average Score (normal space, no dot)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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

                                if (showScoreBadge && avgScore != null) {
                                    val smileyColor = when {
                                        avgScore >= 75 -> Color(0xFF22C55E) // Green
                                        avgScore >= 60 -> Color(0xFFEAB308) // Amber
                                        else -> Color(0xFFEF4444) // Red
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.SentimentSatisfied,
                                            contentDescription = null,
                                            tint = smileyColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "$avgScore%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Row 3: Personal Score + Status (unboxed, same font size as score) + Progress Count
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
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
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

                                        if (showStatusBadge) {
                                            val statusColor = item.userStatus.color()
                                            Text(
                                                text = item.userStatus.displayName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = statusColor
                                            )
                                        }
                                    }

                                    val progressCountText = "${formatProgress(displayProgress)} / ${displayProgressTotal?.takeIf { it > 0 }?.let { formatProgress(it) } ?: "?"}"
                                    Text(
                                        text = progressCountText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Row 4: Progress Bar (Always render track if total > 0 or progress > 0)
                                val showProgressBar = (displayProgressTotal != null && displayProgressTotal > 0) || displayProgress > 0 || isCompleted
                                if (showProgressBar) {
                                    val progressFraction = remember(displayProgress, displayProgressTotal, isCompleted) {
                                        if (isCompleted) 1f
                                        else if (displayProgressTotal != null && displayProgressTotal > 0) {
                                            (displayProgress / displayProgressTotal).toFloat().coerceIn(0f, 1f)
                                        } else if (displayProgress > 0) 1f
                                        else 0f
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        if (progressFraction > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(progressFraction)
                                                    .fillMaxHeight()
                                                    .background(if (isCompleted) StatusCompleted else MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Right Action: Only render '+' button when item is not completed
                        if (!isCompleted && onIncrementProgress != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .combinedClickable(
                                        onClick = { onIncrementProgress(item.id) },
                                        onLongClick = { onOpenProgressDialog?.invoke(item) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increment progress",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
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
