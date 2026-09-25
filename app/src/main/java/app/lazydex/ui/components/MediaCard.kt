package app.lazydex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.data.local.LibraryDisplayMode
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem

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

    when (effectiveMode) {
        LibraryDisplayMode.LIST -> {
            Card(
                onClick = onClick,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoverImage(
                        coverImagePath = item.coverImagePath,
                        title = item.title,
                        coverImageUrl = item.coverImageUrl,
                        modifier = Modifier
                            .size(width = 70.dp, height = 95.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(95.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = relativeTime,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }

                            if (showScoreBadge && item.rating != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                StarRating(
                                    rating = item.rating,
                                    isEditable = false
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showProgressBadge) {
                                Text(
                                    text = progressLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        }

        LibraryDisplayMode.COMPACT_GRID, LibraryDisplayMode.COVER_ONLY_GRID -> {
            val showTitle = effectiveMode == LibraryDisplayMode.COMPACT_GRID
            Card(
                onClick = onClick,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = modifier
                    .padding(4.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    CoverImage(
                        coverImagePath = item.coverImagePath,
                        title = item.title,
                        coverImageUrl = item.coverImageUrl,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (showTitle) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                        startY = 110f
                                    )
                                )
                        )
                    }

                    if (showStatusBadge) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                        ) {
                            StatusBadge(status = item.userStatus)
                        }
                    }

                    if (showCategoryBadge) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                        ) {
                            CategoryBadge(category = item.category)
                        }
                    }

                    if (showTitle) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (showProgressBadge) {
                                    Text(
                                        text = progressLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                if (showScoreBadge && item.rating != null) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${item.rating}★",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF1C40F)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        LibraryDisplayMode.COMFORTABLE_GRID, LibraryDisplayMode.PANORAMA_COMFORTABLE_GRID -> {
            val isPanorama = effectiveMode == LibraryDisplayMode.PANORAMA_COMFORTABLE_GRID
            Card(
                onClick = onClick,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = modifier
                    .padding(4.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isPanorama) Modifier.aspectRatio(16f / 9f)
                                else Modifier.aspectRatio(3f / 4f)
                            )
                    ) {
                        CoverImage(
                            coverImagePath = item.coverImagePath,
                            title = item.title,
                            coverImageUrl = item.coverImageUrl,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (showStatusBadge) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            ) {
                                StatusBadge(status = item.userStatus)
                            }
                        }

                        if (showCategoryBadge) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                            ) {
                                CategoryBadge(category = item.category)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = if (isPanorama) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showProgressBadge) {
                                Text(
                                    text = progressLabel,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            if (showScoreBadge && item.rating != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${item.rating}★",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF1C40F)
                                )
                            }
                        }
                    }
                }
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

