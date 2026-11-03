package app.lazydex.ui.details.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.ui.components.icon
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File

@Composable
fun MediaInfoHeader(
    title: String,
    author: String,
    category: MediaCategory,
    formatName: String?,
    sourceUrl: String? = null,
    totalItems: Int? = null,
    coverImagePath: String,
    coverImageUrl: String?,
    bannerUrl: String?,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier,
    isUpdatingCover: Boolean = false,
) {
    Box(modifier = modifier) {
        val backdropGradientColors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.background,
        )
        val backdropPath = if (coverImagePath.isNotBlank() && File(coverImagePath).exists()) coverImagePath else ""
        val backdropUrl = bannerUrl?.takeIf { it.isNotBlank() } ?: coverImageUrl?.takeIf { it.isNotBlank() }

        if (backdropPath.isNotBlank() || backdropUrl != null) {
            DetailCoverImage(
                coverImagePath = backdropPath,
                title = title,
                coverImageUrl = backdropUrl,
                shape = RectangleShape,
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = backdropGradientColors,
                                startY = size.height / 2,
                            ),
                        )
                    }
                    .background(MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.4f))
                    .blur(8.dp)
                    .alpha(0.25f),
            )
        }

        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .sizeIn(maxWidth = 100.dp)
                        .aspectRatio(2f / 3f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = onCoverClick)
                ) {
                    DetailCoverImage(
                        coverImagePath = coverImagePath,
                        title = title,
                        coverImageUrl = coverImageUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isUpdatingCover) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (author.isNotBlank()) {
                        Row(
                            modifier = Modifier.alpha(0.78f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = author,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Type, Format & Source/Units row styled matching the Author row
                    val formatDisplayText = when {
                        !formatName.isNullOrBlank() &&
                            !formatName.equals(category.displayName, ignoreCase = true) &&
                            !formatName.startsWith("cat_", ignoreCase = true) -> formatName
                        else -> category.displayName
                    }

                    val secondaryInfo = buildString {
                        val host = sourceUrl?.takeIf { it.isNotBlank() }?.let { parseSourceHost(it) }
                        if (host != null) {
                            append(host)
                        }
                        if (totalItems != null && totalItems > 0) {
                            if (isNotEmpty()) append(" • ")
                            val unit = when (category) {
                                MediaCategory.NOVEL, MediaCategory.MANGA -> "Chapters"
                                MediaCategory.ANIME, MediaCategory.TV -> "Episodes"
                                MediaCategory.GAME -> "Hours"
                                MediaCategory.MOVIE -> "Parts"
                            }
                            append("$totalItems $unit")
                        }
                    }

                    Row(
                        modifier = Modifier.alpha(0.78f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = category.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDisplayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (secondaryInfo.isNotBlank()) {
                            Text(
                                text = " • ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = secondaryInfo,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseSourceHost(url: String): String? {
    return try {
        val host = Uri.parse(url).host?.lowercase() ?: return null
        when {
            host.contains("mangadex.org") -> "MangaDex"
            host.contains("novelupdates.com") -> "NovelUpdates"
            host.contains("royalroad.com") -> "Royal Road"
            host.contains("anilist.co") -> "AniList"
            host.contains("myanimelist.net") -> "MyAnimeList"
            host.contains("steampowered.com") -> "Steam"
            else -> host.removePrefix("www.")
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun DetailCoverImage(
    coverImagePath: String,
    title: String,
    coverImageUrl: String?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.extraSmall,
) {
    val model = when {
        coverImagePath.isNotBlank() && File(coverImagePath).exists() -> File(coverImagePath)
        !coverImageUrl.isNullOrBlank() -> coverImageUrl
        else -> null
    }

    if (model != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = "Cover for $title",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
