package app.lazydex.ui.addedit

import android.content.Intent
import android.net.Uri
import android.text.Html
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.UserStatus
import app.lazydex.ui.components.AltTitleEditor
import app.lazydex.ui.components.CoverImage
import app.lazydex.ui.components.StarRating
import app.lazydex.util.formatDate
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

fun extractAverageColor(imagePath: String): Color? {
    if (imagePath.isEmpty()) return null
    val file = java.io.File(imagePath)
    if (!file.exists()) return null
    return try {
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true)
        val pixel = scaled.getPixel(0, 0)
        scaled.recycle()
        bitmap.recycle()
        Color(pixel)
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedAddEditScreen(
    itemId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnifiedAddEditViewModel = koinViewModel()
) {
    val state by viewModel.formState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var dominantColor by remember { mutableStateOf<Color?>(null) }
    var newGenreInput by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }

    LaunchedEffect(state.coverImagePath) {
        if (state.coverImagePath.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val color = extractAverageColor(state.coverImagePath)
                withContext(Dispatchers.Main) {
                    dominantColor = color
                }
            }
        } else {
            dominantColor = null
        }
    }

    LaunchedEffect(state.isDone) {
        if (state.isDone) {
            onBack()
        }
    }

    BackHandler {
        if (viewModel.checkBackPressAllowed()) {
            onBack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Header Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    if (state.coverImagePath.isNotEmpty() && java.io.File(state.coverImagePath).exists()) {
                        AsyncImage(
                            model = java.io.File(state.coverImagePath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(24.dp)
                                .alpha(0.35f)
                        )
                    } else {
                        val fallbackColor = dominantColor ?: MaterialTheme.colorScheme.primary
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(fallbackColor.copy(alpha = 0.25f), Color.Transparent)
                                    )
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                    startY = 120f
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        CoverImage(
                            coverImagePath = state.coverImagePath,
                            title = state.title,
                            modifier = Modifier
                                .size(width = 90.dp, height = 125.dp)
                                .shadow(6.dp, RoundedCornerShape(8.dp))
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = state.title.ifEmpty { "New Media" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (state.author.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "by ${state.author}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.category.displayName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = state.userStatus.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = dominantColor ?: MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Form Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    state.errorMsg?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    if (state.isNew) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.sourceUrl,
                                onValueChange = { viewModel.updateSourceUrl(it) },
                                label = { Text("Import URL (Auto-fill)", fontSize = 12.sp) },
                                placeholder = { Text("https://...", fontSize = 12.sp) },
                                singleLine = true,
                                isError = state.isUrlInvalid,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.scrapeUrl() },
                                enabled = state.sourceUrl.isNotEmpty() && !state.isScraping && !state.isUrlInvalid,
                                modifier = Modifier.height(56.dp)
                            ) {
                                if (state.isScraping) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Fetch", fontSize = 13.sp)
                                }
                            }
                        }

                        state.scrapeError?.let { err ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Scrape error: $err",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    OutlinedTextField(
                        value = state.coverImageUrl ?: "",
                        onValueChange = { viewModel.updateCoverImageUrl(it) },
                        label = { Text("Cover Image URL", fontSize = 12.sp) },
                        placeholder = { Text("https://...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = { viewModel.updateTitle(it) },
                        label = { Text("Title *", fontSize = 12.sp) },
                        singleLine = true,
                        isError = state.isTitleBlank,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            if (state.isTitleBlank) {
                                Text("Title is required", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.author,
                        onValueChange = { viewModel.updateAuthor(it) },
                        label = { Text("Author", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AltTitleEditor(
                        mainTitle = state.title,
                        alternativeTitles = state.alternativeTitles,
                        onMainTitleChanged = { viewModel.updateTitle(it) },
                        onAltTitlesChanged = { viewModel.updateAltTitles(it) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    val chipColors = if (dominantColor != null) {
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = dominantColor!!.copy(alpha = 0.25f),
                            selectedLabelColor = dominantColor!!
                        )
                    } else {
                        FilterChipDefaults.filterChipColors()
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MediaCategory.entries.forEach { category ->
                            FilterChip(
                                selected = state.category == category,
                                onClick = { viewModel.updateCategory(category) },
                                label = { Text(category.displayName, fontSize = 11.sp) },
                                colors = chipColors
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val adaptiveStatusOptions = remember(state.category) {
                        val primaryLabel = when (state.category) {
                            MediaCategory.NOVEL, MediaCategory.MANGA -> UserStatus.READING
                            MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> UserStatus.WATCHING
                            MediaCategory.GAME -> UserStatus.PLAYING
                        }
                        listOf(primaryLabel, UserStatus.COMPLETED, UserStatus.ON_HOLD, UserStatus.DROPPED, UserStatus.PLAN_TO)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        adaptiveStatusOptions.forEach { status ->
                            FilterChip(
                                selected = state.userStatus == status,
                                onClick = { viewModel.updateStatus(status) },
                                label = { Text(status.displayName, fontSize = 11.sp) },
                                colors = chipColors
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress / Total numbers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = state.currentProgress,
                            onValueChange = { viewModel.updateProgress(it) },
                            label = { Text("Current Progress", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = state.isProgressInvalid,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = state.totalItems,
                            onValueChange = { viewModel.updateTotal(it) },
                            label = { Text("Total Items", fontSize = 12.sp) },
                            placeholder = { Text("Ongoing", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = state.isTotalInvalid,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (state.isProgressInvalid) {
                        val errTxt = when {
                            (state.parsedProgress ?: 0) < 0 -> "Progress cannot be negative"
                            state.parsedTotal != null && (state.parsedProgress ?: 0) > (state.parsedTotal ?: 0) -> "Progress cannot exceed total items"
                            else -> "Invalid progress input"
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errTxt,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dates Section (Started Date & Completed Date)
                    Text(
                        text = "Dates",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Started Date Picker Field
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = formatDate(state.startDate),
                                onValueChange = {},
                                enabled = false,
                                label = { Text("Started Date", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = "Start Date",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (state.startDate != null) {
                                        IconButton(onClick = { viewModel.updateStartDate(null) }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear start date",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable {
                                        val cal = java.util.Calendar.getInstance()
                                        if (state.startDate != null) cal.timeInMillis = state.startDate!!
                                        android.app.DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                val selected = java.util.Calendar.getInstance()
                                                selected.set(year, month, day)
                                                viewModel.updateStartDate(selected.timeInMillis)
                                            },
                                            cal.get(java.util.Calendar.YEAR),
                                            cal.get(java.util.Calendar.MONTH),
                                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                            )
                        }

                        // Completed Date Picker Field
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = formatDate(state.endDate),
                                onValueChange = {},
                                enabled = false,
                                label = { Text("Completed Date", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = "Completed Date",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (state.endDate != null) {
                                        IconButton(onClick = { viewModel.updateEndDate(null) }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear end date",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable {
                                        val cal = java.util.Calendar.getInstance()
                                        if (state.endDate != null) cal.timeInMillis = state.endDate!!
                                        android.app.DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                val selected = java.util.Calendar.getInstance()
                                                selected.set(year, month, day)
                                                viewModel.updateEndDate(selected.timeInMillis)
                                            },
                                            cal.get(java.util.Calendar.YEAR),
                                            cal.get(java.util.Calendar.MONTH),
                                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Star Rating (Editable)
                    Text(
                        text = "My Rating",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    StarRating(
                        rating = state.rating,
                        isEditable = true,
                        onRatingChanged = { viewModel.updateRating(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Genres Section
                    Text(
                        text = "Genres",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        state.genres.forEach { genre ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.removeGenre(genre) },
                                label = { Text(genre, fontSize = 11.sp) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove genre",
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = newGenreInput,
                            onValueChange = { newGenreInput = it },
                            placeholder = { Text("Add genre...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (newGenreInput.isNotBlank()) {
                                viewModel.addGenre(newGenreInput)
                                newGenreInput = ""
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add genre")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tags Section
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        state.tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.removeTag(tag) },
                                label = { Text(tag, fontSize = 11.sp) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove tag",
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = newTagInput,
                            onValueChange = { newTagInput = it },
                            placeholder = { Text("Add tag...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (newTagInput.isNotBlank()) {
                                viewModel.addTag(newTagInput)
                                newTagInput = ""
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add tag")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description Field (With Rich Text HTML Preview)
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = { viewModel.updateDescription(it) },
                        label = { Text("Description (HTML supported)", fontSize = 12.sp) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Preview:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    TextView(ctx).apply {
                                        setTextColor(android.graphics.Color.WHITE)
                                        textSize = 14f
                                    }
                                },
                                update = { textView ->
                                    textView.text = Html.fromHtml(state.description, Html.FROM_HTML_MODE_COMPACT)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Notes field
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = { viewModel.updateNotes(it) },
                        label = { Text("Notes", fontSize = 12.sp) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Source URL & Open Link
                    if (!state.isNew && state.sourceUrl.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.sourceUrl,
                                onValueChange = { viewModel.updateSourceUrl(it) },
                                label = { Text("Source URL", fontSize = 12.sp) },
                                singleLine = true,
                                isError = state.isUrlInvalid,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.sourceUrl))
                                    context.startActivity(intent)
                                },
                                enabled = !state.isUrlInvalid,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in browser",
                                    tint = dominantColor ?: MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    } else if (!state.isNew) {
                        OutlinedTextField(
                            value = state.sourceUrl,
                            onValueChange = { viewModel.updateSourceUrl(it) },
                            label = { Text("Source URL", fontSize = 12.sp) },
                            singleLine = true,
                            isError = state.isUrlInvalid,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Save Action Button
                    Button(
                        onClick = { viewModel.save() },
                        enabled = state.canSave,
                        colors = if (dominantColor != null) {
                            ButtonDefaults.buttonColors(
                                containerColor = dominantColor!!,
                                contentColor = Color.White
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(if (state.isNew) "Add Tracker" else "Save Tracker", fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Top AppBar overlay
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (viewModel.checkBackPressAllowed()) {
                                onBack()
                            }
                        },
                        modifier = Modifier.background(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(20.dp)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(
                            onClick = { viewModel.showDeleteConfirm(true) },
                            modifier = Modifier.background(
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(20.dp)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete item",
                                tint = Color.Red
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    }

    // Delete Confirmation Dialog
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirm(false) },
            title = { Text("Delete Tracker") },
            text = { Text("Are you sure you want to delete this media tracker? This will remove all progress history and local cover art.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteItem() }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showDeleteConfirm(false) }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Discard warning dialog
    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDiscardConfirm() },
            title = { Text("Discard Changes") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them and go back?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissDiscardConfirm()
                        onBack()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDiscardConfirm() }
                ) {
                    Text("Keep Editing")
                }
            }
        )
    }
}
