package app.lazydex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem

@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    isGridView: Boolean,
    modifier: Modifier = Modifier
) {
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

    if (isGridView) {
        // ==================== GRID VIEW CARD ====================
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
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
                // Background cover image
                CoverImage(
                    coverImagePath = item.coverImagePath,
                    coverImageUrl = item.coverImageUrl,
                    title = item.title,
                    modifier = Modifier.fillMaxSize()
                )

                // Black gradient at the bottom for text contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                startY = 120f
                            )
                        )
                )

                // Status Badge in top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    StatusBadge(status = item.userStatus)
                }

                // Info overlay container
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
                        Text(
                            text = progressLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.rating != null) {
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
    } else {
        // ==================== LIST VIEW CARD ====================
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
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
                    coverImageUrl = item.coverImageUrl,
                    title = item.title,
                    modifier = Modifier
                        .size(width = 70.dp, height = 95.dp)
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

                        if (item.rating != null) {
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
                        Text(
                            text = progressLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CategoryBadge(category = item.category)
                            StatusBadge(status = item.userStatus)
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
