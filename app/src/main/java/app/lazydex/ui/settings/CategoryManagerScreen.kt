package app.lazydex.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.Category
import app.lazydex.ui.components.CategoryCreatorSheet
import app.lazydex.ui.components.getCategoryIcon
import app.lazydex.ui.dex.DexViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DexViewModel = koinViewModel()
) {
    val categories by viewModel.topLevelCategories.collectAsState()
    val pinnedCategoryIds by viewModel.pinnedCategoryIds.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Manage Categories",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Navigation Bar (Max 4 pinned)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Pinned categories appear as the first 4 items in the bottom navigation bar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
            }

            items(categories, key = { it.id }) { cat ->
                val isPinned = cat.id in pinnedCategoryIds
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPinned) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(cat),
                            contentDescription = null,
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            val unitStr = cat.unitName ?: "Item"
                            val verbStr = cat.actionVerb ?: "Track"
                            Text(
                                text = "$verbStr • $unitStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = {
                            val newPinned = if (isPinned) {
                                pinnedCategoryIds - cat.id
                            } else {
                                if (pinnedCategoryIds.size >= 4) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Maximum 4 categories can be pinned to navigation bar")
                                    }
                                    pinnedCategoryIds
                                } else {
                                    pinnedCategoryIds + cat.id
                                }
                            }
                            viewModel.setPinnedCategories(newPinned)
                        }) {
                            Icon(
                                imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (isPinned) "Unpin" else "Pin",
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { categoryToEdit = cat }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { categoryToDelete = cat }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Edit Category Dialog
    if (categoryToEdit != null) {
        val cat = categoryToEdit!!
        var editedName by remember { mutableStateOf(cat.name) }
        var editedUnit by remember { mutableStateOf(cat.unitName ?: "") }
        var editedVerb by remember { mutableStateOf(cat.actionVerb ?: "") }

        AlertDialog(
            onDismissRequest = { categoryToEdit = null },
            title = { Text("Edit Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editedUnit,
                        onValueChange = { editedUnit = it },
                        label = { Text("Unit Name (e.g. Chapter)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editedVerb,
                        onValueChange = { editedVerb = it },
                        label = { Text("Action Verb (e.g. Read)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editedName.isNotBlank()) {
                            viewModel.updateCategory(
                                cat.copy(
                                    name = editedName.trim(),
                                    unitName = editedUnit.trim().ifBlank { null },
                                    actionVerb = editedVerb.trim().ifBlank { null }
                                )
                            )
                            categoryToEdit = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (categoryToDelete != null) {
        val cat = categoryToDelete!!
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category?") },
            text = {
                Text("Are you sure you want to delete '${cat.name}'? You cannot delete categories that still have tracking items.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(cat.id)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create Category Sheet
    if (showCreateSheet) {
        CategoryCreatorSheet(
            parentCategory = null,
            onDismiss = { showCreateSheet = false },
            onSaveCategory = { newCat ->
                viewModel.createCategory(newCat)
                showCreateSheet = false
            }
        )
    }
}

