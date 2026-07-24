package app.lazydex.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.UserStatus
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.util.Calendar

@Composable
fun KomikkuMangaInfoHeader(
    coverImagePath: String,
    title: String,
    author: String,
    category: MediaCategory,
    status: UserStatus,
    rating: Int?,
    currentProgress: String,
    totalItems: String,
    startDate: Long?,
    endDate: Long?,
    isEditing: Boolean,
    topPadding: Dp = 0.dp,
    onTitleClick: () -> Unit = {},
    onAuthorClick: () -> Unit = {},
    onCategoryChange: (MediaCategory) -> Unit = {},
    onStatusChange: (UserStatus) -> Unit = {},
    onRatingChange: (Int?) -> Unit = {},
    onProgressClick: () -> Unit = {},
    onStartDateChange: (Long?) -> Unit = {},
    onEndDateChange: (Long?) -> Unit = {},
    onCoverClick: () -> Unit = {},
    onTrackerClick: () -> Unit = {},
    onWebviewClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.background

    var showCategoryMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        // Blurred Backdrop (Exact Komikku Styling: blur(7.dp).alpha(0.25f) + Gradient)
        if (coverImagePath.isNotEmpty() && java.io.File(coverImagePath).exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(java.io.File(coverImagePath))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, backgroundColor),
                                startY = size.height / 2f
                            )
                        )
                    }
                    .background(MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.4f))
                    .blur(7.dp)
                    .alpha(0.25f)
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                backgroundColor
                            )
                        )
                    )
            )
        }

        // Header Layout (Clean Presentation at All Times)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Poster Cover Art (2:3 Aspect Ratio)
                Box(
                    modifier = Modifier.clickable { onCoverClick() }
                ) {
                    CoverImage(
                        coverImagePath = coverImagePath,
                        title = title,
                        modifier = Modifier
                            .size(width = 100.dp, height = 150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .shadow(6.dp, RoundedCornerShape(8.dp))
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // MangaAndSourceTitlesSmall (Clean Typography in Both Modes)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    SelectionContainer {
                        Text(
                            text = title.ifEmpty { "Untitled Media" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = if (isEditing) Modifier.clickable { onTitleClick() } else Modifier
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = if (author.isNotBlank()) "by $author" else "by Unknown Author",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isEditing) Modifier.clickable { onAuthorClick() } else Modifier
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category & Status Badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            CategoryBadge(
                                category = category,
                                modifier = if (isEditing) Modifier.clickable { showCategoryMenu = true } else Modifier
                            )
                            DropdownMenu(
                                expanded = showCategoryMenu,
                                onDismissRequest = { showCategoryMenu = false }
                            ) {
                                MediaCategory.entries.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.displayName) },
                                        onClick = {
                                            onCategoryChange(cat)
                                            showCategoryMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Box {
                            StatusBadge(
                                status = status,
                                modifier = if (isEditing) Modifier.clickable { showStatusMenu = true } else Modifier
                            )
                            DropdownMenu(
                                expanded = showStatusMenu,
                                onDismissRequest = { showStatusMenu = false }
                            ) {
                                UserStatus.entries.forEach { st ->
                                    DropdownMenuItem(
                                        text = { Text(st.displayName) },
                                        onClick = {
                                            onStatusChange(st)
                                            showStatusMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Star Rating
                    StarRating(
                        rating = rating,
                        isEditable = isEditing,
                        onRatingChanged = onRatingChange
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress & Dates Text
                    val progressStr = "Ch. ${currentProgress.ifEmpty { "0" }} / ${totalItems.ifEmpty { "Ongoing" }}"
                    val startStr = formatDate(startDate) ?: "Not Started"
                    val endStr = formatDate(endDate) ?: "Not Finished"
                    
                    Box {
                        Text(
                            text = "$progressStr • 📅 $startStr ─ $endStr",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (isEditing) Modifier.clickable { showDateMenu = true } else Modifier
                        )

                        DropdownMenu(
                            expanded = showDateMenu,
                            onDismissRequest = { showDateMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Update Progress ($progressStr)") },
                                onClick = {
                                    showDateMenu = false
                                    onProgressClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Set Start Date ($startStr)") },
                                onClick = {
                                    showDateMenu = false
                                    val cal = Calendar.getInstance()
                                    if (startDate != null) cal.timeInMillis = startDate
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val selected = Calendar.getInstance()
                                            selected.set(y, m, d)
                                            onStartDateChange(selected.timeInMillis)
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Set End Date ($endStr)") },
                                onClick = {
                                    showDateMenu = false
                                    val cal = Calendar.getInstance()
                                    if (endDate != null) cal.timeInMillis = endDate
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val selected = Calendar.getInstance()
                                            selected.set(y, m, d)
                                            onEndDateChange(selected.timeInMillis)
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Komikku Action Row (Category, Status, Tracker, Webview)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box {
                    KomikkuActionButton(
                        title = "Category",
                        icon = Icons.Default.Book,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { showCategoryMenu = true }
                    )
                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        MediaCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.displayName) },
                                onClick = {
                                    onCategoryChange(cat)
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }

                Box {
                    KomikkuActionButton(
                        title = "Status",
                        icon = Icons.Outlined.FavoriteBorder,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { showStatusMenu = true }
                    )
                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }
                    ) {
                        UserStatus.entries.forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st.displayName) },
                                onClick = {
                                    onStatusChange(st)
                                    showStatusMenu = false
                                }
                            )
                        }
                    }
                }

                KomikkuActionButton(
                    title = "Tracker",
                    icon = Icons.Outlined.Sync,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onTrackerClick
                )

                if (onWebviewClick != null) {
                    KomikkuActionButton(
                        title = "Webview",
                        icon = Icons.Outlined.Public,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = onWebviewClick
                    )
                }
            }
        }
    }
}

@Composable
fun KomikkuActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1
        )
    }
}
