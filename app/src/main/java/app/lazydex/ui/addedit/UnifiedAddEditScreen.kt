package app.lazydex.ui.addedit

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.UserStatus
import app.lazydex.ui.components.AltTitleEditor
import app.lazydex.ui.components.CoverImage
import app.lazydex.ui.components.StarRating
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    // Trigger back pop when save/delete is complete
    LaunchedEffect(state.isDone) {
        if (state.isDone) {
            onBack()
        }
    }

    // Intercept back presses to warn about unsaved changes
    BackHandler {
        if (viewModel.checkBackPressAllowed()) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isNew) "Add Media" else "Edit Media",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (viewModel.checkBackPressAllowed()) {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.showDeleteConfirm(true) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete item",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            // Error Message (database constraints, etc.)
            state.errorMsg?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // URL Scraping block (Visible only in Add Mode)
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

            // Cover Image details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    coverImagePath = state.coverImagePath,
                    title = state.title,
                    modifier = Modifier.size(width = 80.dp, height = 110.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = state.coverImageUrl,
                        onValueChange = { viewModel.updateCoverImageUrl(it) },
                        label = { Text("Cover Image URL", fontSize = 12.sp) },
                        placeholder = { Text("https://...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title Field (Required)
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

            // Alternative Titles editor
            AltTitleEditor(
                mainTitle = state.title,
                alternativeTitles = state.alternativeTitles,
                onMainTitleChanged = { viewModel.updateTitle(it) },
                onAltTitlesChanged = { viewModel.updateAltTitles(it) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category Selection Chips
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Wrap chips nicely in a row
                MediaCategory.entries.take(3).forEach { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { viewModel.updateCategory(category) },
                        label = { Text(category.displayName, fontSize = 11.sp) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaCategory.entries.drop(3).forEach { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { viewModel.updateCategory(category) },
                        label = { Text(category.displayName, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status Selection Chips (Category-Adaptive)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                adaptiveStatusOptions.take(3).forEach { status ->
                    FilterChip(
                        selected = state.userStatus == status,
                        onClick = { viewModel.updateStatus(status) },
                        label = { Text(status.displayName, fontSize = 11.sp) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                adaptiveStatusOptions.drop(3).forEach { status ->
                    FilterChip(
                        selected = state.userStatus == status,
                        onClick = { viewModel.updateStatus(status) },
                        label = { Text(status.displayName, fontSize = 11.sp) }
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

            // Inline progress error reporting
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

            // Notes field
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text("Notes", fontSize = 12.sp) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Source URL & Open Link (Visible in Edit Mode, or Add Mode if manual entry)
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
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open in browser",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            } else if (!state.isNew) {
                OutlinedTextField(
                    value = state.sourceUrl,
                    onValueChange = { viewModel.updateSourceUrl(it) },
                    label = { Text("Source URL", fontSize = 12.sp) },
                    singleLine = true,
                    isError = state.isUrlInvalid,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Save Action Button
            Button(
                onClick = { viewModel.save() },
                enabled = state.canSave,
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
                    Text("Save Tracker", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Confirmation Dialogs
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirm(false) },
            title = { Text("Delete Tracker") },
            text = { Text("Are you sure you want to delete this media tracker? All your progress records for this item will be lost.") },
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

    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDiscardConfirm() },
            title = { Text("Discard Changes") },
            text = { Text("You have unsaved changes in your tracker editing. Are you sure you want to discard them?") },
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
