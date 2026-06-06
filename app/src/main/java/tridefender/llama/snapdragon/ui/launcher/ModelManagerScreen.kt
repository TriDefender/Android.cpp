package tridefender.llama.snapdragon.ui.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import tridefender.llama.snapdragon.R
import tridefender.llama.snapdragon.viewmodel.ModelCatalogEntry
import tridefender.llama.snapdragon.viewmodel.ModelConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    onBack: () -> Unit,
    viewModel: ModelConfigViewModel = hiltViewModel()
) {
    val analysis by viewModel.analysis.collectAsState()
    val config by viewModel.config.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<ModelCatalogEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<ModelCatalogEntry?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshModelCatalog()
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_models)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            stringResource(R.string.model_cache_folder),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.cached_models_count, analysis.catalog.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (analysis.catalog.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_cached_models),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(analysis.catalog, key = { it.path }) { entry ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 1.dp
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    val selected = entry.path == config.modelPath
                                    Text(
                                        if (selected) {
                                            stringResource(R.string.selected_model_with_size, entry.estimate?.let { formatBytes(it.totalBytes) } ?: stringResource(R.string.model_metadata_unavailable))
                                        } else {
                                            entry.estimate?.let { formatBytes(it.totalBytes) } ?: stringResource(R.string.model_metadata_unavailable)
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { renameTarget = entry }) {
                                            Icon(
                                                Icons.Default.DriveFileRenameOutline,
                                                contentDescription = stringResource(R.string.rename)
                                            )
                                        }
                                        IconButton(onClick = { deleteTarget = entry }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { entry ->
        RenameModelDialog(
            entry = entry,
            onDismiss = { renameTarget = null },
            onRename = { newName ->
                val error = viewModel.renameCachedModel(entry, newName)
                renameTarget = null
                snackbarMessage = error
            }
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_model)) },
            text = { Text(stringResource(R.string.delete_model_message, entry.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val error = viewModel.deleteCachedModel(entry)
                        deleteTarget = null
                        snackbarMessage = error
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun RenameModelDialog(
    entry: ModelCatalogEntry,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember(entry.path) { mutableStateOf(entry.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_model)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.model_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }) {
                Text(stringResource(R.string.rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
