package tridefender.llama.snapdragon.ui.kernel

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import tridefender.llama.snapdragon.R
import tridefender.llama.snapdragon.model.DownloadState
import tridefender.llama.snapdragon.model.GitHubRelease
import tridefender.llama.snapdragon.model.KernelSource
import tridefender.llama.snapdragon.model.KernelVersion
import tridefender.llama.snapdragon.repository.ImportResult
import tridefender.llama.snapdragon.viewmodel.KernelManagerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ImportDialogState(val uri: String, val suggestedName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernelScreen(
    viewModel: KernelManagerViewModel = hiltViewModel()
) {
    val kernelConfig by viewModel.kernelConfig.collectAsState()
    val revertMessage by viewModel.revertMessage.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val switchError by viewModel.switchError.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val releases by viewModel.releases.collectAsState()
    val isLoadingReleases by viewModel.isLoadingReleases.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(revertMessage) {
        revertMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearRevertMessage()
        }
    }

    LaunchedEffect(switchError) {
        switchError?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearSwitchError()
        }
    }

    var importDialogState by remember { mutableStateOf<ImportDialogState?>(null) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "imported-kernel"
            val name = fileName
                .removeSuffix(".tar.gz")
                .removeSuffix(".tgz")
                .removeSuffix(".zip")
            importDialogState = ImportDialogState(uri.toString(), name)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active version header
            ActiveVersionHeader(
                activeVersion = kernelConfig.activeVersion,
                versions = kernelConfig.versions
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { showDownloadDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.download))
                }

                OutlinedButton(
                    onClick = {
                        filePickerLauncher.launch(
                            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.import_label))
                }
            }

            // Download progress
            if (downloadState == DownloadState.DOWNLOADING || downloadState == DownloadState.EXTRACTING || downloadState == DownloadState.VALIDATING) {
                DownloadProgressCard(downloadState, downloadProgress)
            }

            // Installed versions header
            Text(
                text = stringResource(R.string.installed_kernels),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Version list
            if (kernelConfig.versions.isEmpty()) {
                Text(
                    stringResource(R.string.no_kernel_versions_installed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                kernelConfig.versions.forEach { version ->
                    KernelVersionRow(
                        version = version,
                        isActive = version.name == kernelConfig.activeVersion,
                        serverRunning = viewModel.isServerRunning(),
                        onSwitch = { viewModel.switchVersion(it) },
                        onDelete = { showDeleteConfirm = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Dialogs
    importDialogState?.let { state ->
        ImportNameDialog(
            initialName = state.suggestedName,
            onConfirm = { name ->
                viewModel.importLocalArchive(Uri.parse(state.uri), name)
                importDialogState = null
            },
            onDismiss = { importDialogState = null }
        )
    }

    if (showDownloadDialog) {
        DownloadDialog(
            releases = releases,
            isLoading = isLoadingReleases,
            downloadState = downloadState,
            downloadProgress = downloadProgress,
            onFetch = { viewModel.fetchReleases("TriDefender", "llama.cpp-snapdragon_kernels") },
            onDownload = { viewModel.downloadFromGitHub(it) },
            onDismiss = { showDownloadDialog = false }
        )
    }

    showDeleteConfirm?.let { name ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.delete_kernel)) },
            text = { Text(stringResource(R.string.delete_kernel_message, name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteVersion(name)
                    showDeleteConfirm = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val currentImportResult = importResult
    if (currentImportResult != null) {
        ImportResultDialog(
            result = currentImportResult,
            onDismiss = { viewModel.clearImportResult() }
        )
    }
}

@Composable
private fun ActiveVersionHeader(
    activeVersion: String,
    versions: List<KernelVersion>
) {
    val active = versions.find { it.name == activeVersion }
    val sourceLabel = active?.let {
        when (it.source) {
            KernelSource.BUNDLED -> stringResource(R.string.bundled)
            KernelSource.GITHUB_RELEASE -> stringResource(R.string.github_release)
            KernelSource.LOCAL_IMPORT -> stringResource(R.string.local_import)
        }
    } ?: "None"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.active_kernel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = activeVersion,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressCard(
    downloadState: DownloadState,
    downloadProgress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val label = when (downloadState) {
                DownloadState.DOWNLOADING -> stringResource(R.string.downloading_kernel)
                DownloadState.EXTRACTING -> stringResource(R.string.extracting_archive)
                DownloadState.VALIDATING -> stringResource(R.string.validating_files)
                else -> ""
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${(downloadProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun KernelVersionRow(
    version: KernelVersion,
    isActive: Boolean,
    serverRunning: Boolean,
    onSwitch: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val canDelete = !version.isBundled && !isActive

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = version.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.active), style = MaterialTheme.typography.labelSmall) },
                            icon = {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sourceLabel = when (version.source) {
                        KernelSource.BUNDLED -> stringResource(R.string.bundled)
                        KernelSource.GITHUB_RELEASE -> stringResource(R.string.github)
                        KernelSource.LOCAL_IMPORT -> stringResource(R.string.local)
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(sourceLabel, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )

                    if (version.missingLibraries.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = stringResource(R.string.missing_libraries),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = stringResource(R.string.missing_count, version.missingLibraries.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    val dateStr = remember(version.installedAt) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .format(Date(version.installedAt))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isActive) {
                IconButton(
                    onClick = { onSwitch(version.name) },
                    enabled = !serverRunning
                ) {
                    Icon(
                        Icons.Outlined.SwapHoriz,
                        contentDescription = stringResource(R.string.activate),
                        tint = if (serverRunning) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (canDelete) {
                IconButton(onClick = { onDelete(version.name) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportNameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_kernel)) },
        text = {
            Column {
                Text(stringResource(R.string.enter_kernel_version_name))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.version_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.import_label))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DownloadDialog(
    releases: List<GitHubRelease>,
    isLoading: Boolean,
    downloadState: DownloadState,
    downloadProgress: Float,
    onFetch: () -> Unit,
    onDownload: (GitHubRelease) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        onFetch()
    }

    AlertDialog(
        onDismissRequest = {
            if (downloadState != DownloadState.DOWNLOADING && downloadState != DownloadState.EXTRACTING) {
                onDismiss()
            }
        },
        title = { Text(stringResource(R.string.download_kernels)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else if (releases.isEmpty()) {
                    Text(
                        stringResource(R.string.no_releases_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        stringResource(R.string.select_release_to_download),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    releases.forEach { release ->
                        ReleaseRow(
                            release = release,
                            enabled = downloadState == DownloadState.IDLE || downloadState == DownloadState.DONE || downloadState == DownloadState.ERROR,
                            onClick = { onDownload(release) }
                        )
                    }
                }

                // Download progress in dialog
                if (downloadState == DownloadState.DOWNLOADING || downloadState == DownloadState.EXTRACTING || downloadState == DownloadState.VALIDATING) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val label = when (downloadState) {
                        DownloadState.DOWNLOADING -> stringResource(R.string.downloading)
                        DownloadState.EXTRACTING -> stringResource(R.string.extracting)
                        DownloadState.VALIDATING -> stringResource(R.string.validating)
                        else -> ""
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (downloadState == DownloadState.DONE) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.download_complete),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (downloadState == DownloadState.ERROR) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.download_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun ReleaseRow(
    release: GitHubRelease,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val sizeStr = remember(release.assetSize) {
        formatFileSize(release.assetSize)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        enabled = enabled,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = release.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${release.assetName} ($sizeStr)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImportResultDialog(
    result: ImportResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (result) {
                is ImportResult.Success -> stringResource(R.string.import_successful)
                is ImportResult.Error -> stringResource(R.string.import_failed)
            })
        },
        text = {
            Column {
                when (result) {
                    is ImportResult.Success -> {
                        Text(stringResource(R.string.kernel_imported_successfully))
                        if (result.missingLibraries.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        stringResource(R.string.missing_libraries),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    result.missingLibraries.forEach { lib ->
                                        Text(
                                            "  - $lib",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.some_features_may_not_work),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    is ImportResult.Error -> {
                        Text(result.message)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
