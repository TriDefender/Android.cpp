package tridefender.llama.snapdragon.ui.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import tridefender.llama.snapdragon.model.DownloadState
import tridefender.llama.snapdragon.model.GitHubRelease
import tridefender.llama.snapdragon.model.KernelSource
import tridefender.llama.snapdragon.model.KernelVersion
import tridefender.llama.snapdragon.repository.ImportResult
import tridefender.llama.snapdragon.viewmodel.KernelManagerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "KernelManagerSection"

private data class ImportDialogState(val uri: String, val suggestedName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernelManagerSection(
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

    Box {
        ConfigSectionCard(
            title = "Kernel Manager",
            icon = Icons.Default.Memory,
            iconColor = MaterialTheme.colorScheme.tertiary
        ) {
            KernelVersionList(
                versions = kernelConfig.versions,
                activeVersion = kernelConfig.activeVersion,
                serverRunning = viewModel.isServerRunning(),
                onSwitch = { viewModel.switchVersion(it) },
                onDelete = { showDeleteConfirm = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    Text("Import")
                }

                OutlinedButton(
                    onClick = { showDownloadDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download")
                }
            }

            if (downloadState == DownloadState.DOWNLOADING || downloadState == DownloadState.EXTRACTING || downloadState == DownloadState.VALIDATING) {
                Spacer(modifier = Modifier.height(12.dp))
                Column {
                    val label = when (downloadState) {
                        DownloadState.DOWNLOADING -> "Downloading..."
                        DownloadState.EXTRACTING -> "Extracting..."
                        DownloadState.VALIDATING -> "Validating..."
                        else -> ""
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

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
            onFetch = { viewModel.fetchReleases("TriDefender", "llama.cpp-snapdragon_kernels") },
            onDownload = { viewModel.downloadFromGitHub(it) },
            onDismiss = { showDownloadDialog = false }
        )
    }

    showDeleteConfirm?.let { name ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Kernel") },
            text = { Text("Delete '$name'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteVersion(name)
                    showDeleteConfirm = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
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
private fun KernelVersionList(
    versions: List<KernelVersion>,
    activeVersion: String,
    serverRunning: Boolean,
    onSwitch: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    if (versions.isEmpty()) {
        Text(
            "No kernel versions installed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        versions.forEach { version ->
            KernelVersionRow(
                version = version,
                isActive = version.name == activeVersion,
                serverRunning = serverRunning,
                onSwitch = { onSwitch(version.name) },
                onDelete = { onDelete(version.name) }
            )
        }
    }
}

@Composable
private fun KernelVersionRow(
    version: KernelVersion,
    isActive: Boolean,
    serverRunning: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit
) {
    val canDelete = !version.isBundled && !isActive

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
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
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sourceLabel = when (version.source) {
                        KernelSource.BUNDLED -> "Bundled"
                        KernelSource.GITHUB_RELEASE -> "GitHub"
                        KernelSource.LOCAL_IMPORT -> "Local"
                    }
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (version.missingLibraries.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Missing libraries",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${version.missingLibraries.size} missing",
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
                    onClick = onSwitch,
                    enabled = !serverRunning
                ) {
                    Icon(
                        Icons.Outlined.SwapHoriz,
                        contentDescription = "Activate",
                        tint = if (serverRunning) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
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
        title = { Text("Import Kernel") },
        text = {
            Column {
                Text("Enter a name for this kernel version:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Version name") },
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
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DownloadDialog(
    releases: List<GitHubRelease>,
    isLoading: Boolean,
    downloadState: DownloadState,
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
        title = { Text("Download Kernels") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
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
                        "No releases found or network error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Select a release to download:",
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

                if (downloadState == DownloadState.DONE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Download complete!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (downloadState == DownloadState.ERROR) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Download failed. Please try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                is ImportResult.Success -> "Import Successful"
                is ImportResult.Error -> "Import Failed"
            })
        },
        text = {
            Column {
                when (result) {
                    is ImportResult.Success -> {
                        Text("Kernel imported successfully.")
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
                                        "Missing libraries:",
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
                                        "Some features may not work correctly.",
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
                Text("OK")
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
