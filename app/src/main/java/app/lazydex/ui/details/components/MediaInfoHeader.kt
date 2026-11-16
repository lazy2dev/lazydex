package app.lazydex.ui.details.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lazydex.R
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.TypeConfig
import app.lazydex.ui.components.icon
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaInfoHeader(
    title: String,
    author: String,
    mediaType: MediaType,
    formatName: String?,
    sourceUrl: String? = null,
    totalItems: Int? = null,
    coverImagePath: String,
    coverImageUrl: String?,
    bannerUrl: String?,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier,
    isUpdatingCover: Boolean = false,
    isObscured: Boolean = false,
    topPadding: Dp = 0.dp,
    isEditing: Boolean = false,
    onTitleChange: (String) -> Unit = {},
    onAuthorChange: (String) -> Unit = {},
    availableFormats: List<TypeConfig> = emptyList(),
    onFormatSelect: (String?) -> Unit = {},
    onTotalItemsChange: (String) -> Unit = {},
    isAdult: Boolean = false,
    onToggleAdult: () -> Unit = {},
    isNew: Boolean = false,
    onMediaTypeSelect: (MediaType) -> Unit = {},
) {
    var draftTotalInput by remember(totalItems) { mutableStateOf(totalItems?.toString() ?: "") }
    var isFormatMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isEditing) {
        if (!isEditing) {
            isFormatMenuExpanded = false
        }
    }

    Box(modifier = modifier) {
        val backdropGradientColors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.background,
        )
        val backdropPath = coverImagePath.trim()
        val backdropUrl = bannerUrl?.takeIf { it.isNotBlank() } ?: coverImageUrl?.takeIf { it.isNotBlank() }

        if (!isObscured && (backdropPath.isNotBlank() || backdropUrl != null)) {
            DetailCoverImage(
                coverImagePath = backdropPath,
                title = title,
                coverImageUrl = backdropUrl,
                shape = RectangleShape,
                isBackdrop = true,
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
                    .padding(start = 16.dp, top = topPadding + 16.dp, end = 16.dp),
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
                        modifier = Modifier.fillMaxSize(),
                        isObscured = isObscured,
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
                    } else if (isEditing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit artwork",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isEditing) {
                        BasicTextField(
                            value = title,
                            onValueChange = onTitleChange,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (title.isBlank()) {
                                    Text(
                                        text = "Title...",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    } else {
                        Text(
                            text = title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isEditing) {
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
                            BasicTextField(
                                value = author,
                                onValueChange = onAuthorChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (author.isBlank()) {
                                        Text(
                                            text = "Author / Artist...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    } else if (author.isNotBlank()) {
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

                    // Media Type selector if brand new item
                    if (isNew) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            listOf(MediaType.NOVEL, MediaType.MANGA, MediaType.ANIME, MediaType.GAME).forEach { mType ->
                                FilterChip(
                                    selected = mediaType == mType,
                                    onClick = { onMediaTypeSelect(mType) },
                                    label = { Text(mType.displayName, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(28.dp),
                                )
                            }
                        }
                    }

                    // Format Display & Selection
                    val formatDisplayText = when {
                        !formatName.isNullOrBlank() &&
                            !formatName.equals(mediaType.displayName, ignoreCase = true) &&
                            !formatName.startsWith("typ_", ignoreCase = true) -> formatName
                        else -> mediaType.displayName
                    }

                    val unitName = when (mediaType) {
                        MediaType.NOVEL, MediaType.MANGA -> "Chapters"
                        MediaType.ANIME, MediaType.TV -> "Episodes"
                        MediaType.GAME -> "Hours"
                        MediaType.MOVIE -> "Parts"
                    }

                    val sourceHost = sourceUrl?.takeIf { it.isNotBlank() }?.let { parseSourceHost(it) }

                    // Metadata Row: Format, Source Host, Total Items, 18+ Badge
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Format chip / selector
                        Box {
                            Row(
                                modifier = Modifier
                                    .alpha(if (isEditing && availableFormats.isNotEmpty()) 1f else 0.78f)
                                    .then(
                                        if (isEditing && availableFormats.isNotEmpty()) {
                                            Modifier.clickable { isFormatMenuExpanded = true }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = mediaType.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isEditing && availableFormats.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatDisplayText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isEditing && availableFormats.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isEditing && availableFormats.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select format",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = isFormatMenuExpanded,
                                onDismissRequest = { isFormatMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Standard (${mediaType.displayName})") },
                                    onClick = {
                                        onFormatSelect(null)
                                        isFormatMenuExpanded = false
                                    }
                                )
                                availableFormats.forEach { format ->
                                    DropdownMenuItem(
                                        text = { Text(format.name) },
                                        onClick = {
                                            onFormatSelect(format.id)
                                            isFormatMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Source Host (if present)
                        if (sourceHost != null) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = sourceHost,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Total Units (editable inline, zero box)
                        if (isEditing) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                BasicTextField(
                                    value = draftTotalInput,
                                    onValueChange = { input ->
                                        val digits = input.filter { it.isDigit() }
                                        draftTotalInput = digits
                                        onTotalItemsChange(digits)
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (draftTotalInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    decorationBox = { innerTextField ->
                                        if (draftTotalInput.isBlank()) {
                                            Text(
                                                text = "—",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                Text(
                                    text = " $unitName",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else if (totalItems != null && totalItems > 0) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "$totalItems $unitName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 18+ Adult Badge
                        if (isAdult || isEditing) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isAdult) Color(0xFFEF5350).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isAdult) Color(0xFFEF5350) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = if (isEditing) Modifier.clickable { onToggleAdult() } else Modifier
                            ) {
                                Text(
                                    text = "18+",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAdult) Color(0xFFEF5350) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
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
    isObscured: Boolean = false,
    isBackdrop: Boolean = false,
) {
    if (isObscured) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(Color(0xFF2B1414)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                imageVector = ImageVector.vectorResource(R.drawable.cover_error_vector),
                contentDescription = "Adult Content Hidden",
                colorFilter = ColorFilter.tint(Color(0xFFEF5350)),
                modifier = Modifier.size(36.dp)
            )
        }
        return
    }

    val model = remember(coverImagePath, coverImageUrl) {
        if (coverImagePath.isNotBlank() || !coverImageUrl.isNullOrBlank()) {
            app.lazydex.data.coil.CoverModel(
                url = coverImageUrl?.trim()?.takeIf { it.isNotBlank() },
                localPath = coverImagePath.trim().takeIf { it.isNotBlank() },
            )
        } else {
            null
        }
    }

    if (model != null) {
        val context = LocalContext.current
        val request = remember(model, isBackdrop) {
            ImageRequest.Builder(context)
                .data(model)
                .apply {
                    if (isBackdrop) {
                        size(180)
                    }
                }
                .build()
        }
        AsyncImage(
            model = request,
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
