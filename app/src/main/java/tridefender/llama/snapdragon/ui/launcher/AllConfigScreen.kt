package tridefender.llama.snapdragon.ui.launcher

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import tridefender.llama.snapdragon.R
import tridefender.llama.snapdragon.model.CacheType
import tridefender.llama.snapdragon.model.DeviceType
import tridefender.llama.snapdragon.model.FlashAttentionMode
import tridefender.llama.snapdragon.model.MemoryFitState
import tridefender.llama.snapdragon.model.PoolingType
import tridefender.llama.snapdragon.viewmodel.ModelCatalogEntry
import tridefender.llama.snapdragon.viewmodel.ModelConfigViewModel

private const val TAG = "AllConfigScreen"

@Composable
fun NumberField(
    value: Int,
    onValueChange: (Int?) -> Unit,
    defaultValue: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    var displayValue by remember(value) { 
        mutableStateOf(if (value == -1) "" else value.toString()) 
    }
    
    OutlinedTextField(
        value = displayValue,
        onValueChange = { newText ->
            displayValue = newText
            if (newText.isEmpty()) {
                onValueChange(-1)
            } else {
                newText.toIntOrNull()?.let { onValueChange(it) }
            }
        },
        label = { Text(label) },
        placeholder = { 
            Text(
                stringResource(R.string.default_value, defaultValue),
                style = MaterialTheme.typography.bodySmall
            ) 
        },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllConfigScreen(
    viewModel: ModelConfigViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsState()
    val analysis by viewModel.analysis.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uri = data?.data
            uri?.let {
                val takeFlags = (data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                runCatching {
                    context.contentResolver.takePersistableUriPermission(it, takeFlags)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to persist URI permission for $it", error)
                }

                viewModel.importModelUri(it.toString())
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModelSection(
            modelPath = config.modelPath,
            modelCatalog = analysis.catalog,
            isEmbedding = config.isEmbedding,
            poolingType = config.poolingType,
            onModelSelect = { viewModel.updateModelPath(it) },
            onIsEmbeddingChange = { viewModel.updateIsEmbedding(it) },
            onPoolingTypeChange = { viewModel.updatePoolingType(it) },
            onRefreshModels = { viewModel.refreshModelCatalog() },
            onBrowseClick = {
                filePickerLauncher.launch(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }
                )
            }
        )
        
        DeviceSection(
            deviceType = config.deviceType,
            gpuLayers = config.gpuLayers,
            onDeviceTypeChange = { viewModel.updateDeviceType(it) },
            onGpuLayersChange = { viewModel.updateGpuLayers(it) }
        )
        
        KvCacheSection(
            cacheTypeK = config.cacheTypeK,
            cacheTypeV = config.cacheTypeV,
            kvOffload = config.kvOffload,
            flashAttention = config.flashAttention,
            onCacheTypeKChange = { viewModel.updateCacheTypeK(it) },
            onCacheTypeVChange = { viewModel.updateCacheTypeV(it) },
            onKvOffloadChange = { viewModel.updateKvOffload(it) },
            onFlashAttentionChange = { viewModel.updateFlashAttention(it) }
        )
        
        ServerSection(
            port = config.port,
            contextSize = config.contextSize,
            batchSize = config.batchSize,
            bindAll = config.bindAll,
            onPortChange = { viewModel.updatePort(it) },
            onContextSizeChange = { viewModel.updateContextSize(it) },
            onBatchSizeChange = { viewModel.updateBatchSize(it) },
            onBindAllChange = { viewModel.updateBindAll(it) }
        )
        
        AutoFitSection(
            autoFit = config.autoFit,
            autoFitTargetMiB = config.autoFitTargetMiB,
            autoFitCtx = config.autoFitCtx,
            onAutoFitChange = { viewModel.updateAutoFit(it) },
            onAutoFitTargetChange = { viewModel.updateAutoFitTargetMiB(it) },
            onAutoFitCtxChange = { viewModel.updateAutoFitCtx(it) }
        )
        
        ExtraParamsSection(
            extraParams = config.extraParams ?: "",
            onExtraParamsChange = { viewModel.updateExtraParams(it) }
        )
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSection(
    modelPath: String,
    modelCatalog: List<ModelCatalogEntry>,
    isEmbedding: Boolean,
    poolingType: PoolingType,
    onModelSelect: (String) -> Unit,
    onIsEmbeddingChange: (Boolean) -> Unit,
    onPoolingTypeChange: (PoolingType) -> Unit,
    onRefreshModels: () -> Unit,
    onBrowseClick: () -> Unit
) {
    ConfigSectionCard(
        title = stringResource(R.string.model_config),
        icon = Icons.Default.Storage,
        iconColor = MaterialTheme.colorScheme.primary
    ) {
        val displayName = modelPath.substringAfterLast("%2F").substringAfterLast("/")

        var modelMenuExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = modelMenuExpanded,
            onExpandedChange = { modelMenuExpanded = it && modelCatalog.isNotEmpty() }
        ) {
            OutlinedTextField(
                value = displayName.ifEmpty { stringResource(R.string.tap_to_select_model_file) },
                onValueChange = {},
                label = { Text(stringResource(R.string.model_file)) },
                supportingText = {
                    Text(
                        text = if (modelCatalog.isEmpty()) {
                            stringResource(R.string.no_cached_models)
                        } else {
                            stringResource(R.string.cached_models_count, modelCatalog.size)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                shape = RoundedCornerShape(12.dp)
            )

            ExposedDropdownMenu(
                expanded = modelMenuExpanded,
                onDismissRequest = { modelMenuExpanded = false }
            ) {
                modelCatalog.forEach { entry ->
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = entry.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = entry.estimate?.let {
                                        stringResource(
                                            R.string.model_menu_memory_line,
                                            formatBytes(it.totalBytes),
                                            fitLabel(it.fitState)
                                        )
                                    } ?: stringResource(R.string.model_metadata_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = entry.estimate?.fitState?.fitColor()
                                        ?: MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onModelSelect(entry.path)
                            modelMenuExpanded = false
                        },
                        trailingIcon = {
                            if (entry.path == modelPath) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onRefreshModels) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.refresh_models))
            }
            TextButton(onClick = onBrowseClick) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.browse))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.embedding_mode),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.embedding_mode_for_models),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEmbedding, 
                    onCheckedChange = onIsEmbeddingChange
                )
            }
        }
        
        AnimatedVisibility(
            visible = isEmbedding,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = poolingType.localizedName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.pooling_mode_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        PoolingType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(type.localizedName())
                                        if (type == poolingType) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                Icons.Outlined.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = { onPoolingTypeChange(type); expanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSection(
    deviceType: DeviceType,
    gpuLayers: Int,
    onDeviceTypeChange: (DeviceType) -> Unit,
    onGpuLayersChange: (Int?) -> Unit
) {
    ConfigSectionCard(
        title = stringResource(R.string.compute_device),
        icon = Icons.Default.Memory,
        iconColor = MaterialTheme.colorScheme.secondary
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            val deviceLabel = when (deviceType) {
                DeviceType.CPU -> stringResource(R.string.device_cpu)
                DeviceType.OPENCL -> stringResource(R.string.device_opencl_gpu)
                DeviceType.HTP0 -> stringResource(R.string.device_htp_npu, 0)
                DeviceType.HTP1 -> stringResource(R.string.device_htp_npu, 1)
                DeviceType.HTP2 -> stringResource(R.string.device_htp_npu, 2)
                DeviceType.HTP3 -> stringResource(R.string.device_htp_npu, 3)
                DeviceType.HTP4 -> stringResource(R.string.device_htp_npu, 4)
            }
            OutlinedTextField(
                value = deviceLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.device_type)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(text = { Text(stringResource(R.string.device_cpu)) }, onClick = { onDeviceTypeChange(DeviceType.CPU); expanded = false })
                DropdownMenuItem(text = { Text(stringResource(R.string.device_opencl_gpu)) }, onClick = { onDeviceTypeChange(DeviceType.OPENCL); expanded = false })
                DropdownMenuItem(text = { Text(stringResource(R.string.device_htp_npu, 0)) }, onClick = { onDeviceTypeChange(DeviceType.HTP0); expanded = false })
                DropdownMenuItem(text = { Text(stringResource(R.string.device_htp_npu, 1)) }, onClick = { onDeviceTypeChange(DeviceType.HTP1); expanded = false })
                DropdownMenuItem(text = { Text(stringResource(R.string.device_htp_npu, 2)) }, onClick = { onDeviceTypeChange(DeviceType.HTP2); expanded = false })
                DropdownMenuItem(text = { Text(stringResource(R.string.device_htp_npu, 3)) }, onClick = { onDeviceTypeChange(DeviceType.HTP3); expanded = false })
                DropdownMenuItem(text = { Text(stringResource(R.string.device_htp_npu, 4)) }, onClick = { onDeviceTypeChange(DeviceType.HTP4); expanded = false })
            }
        }
        
        AnimatedVisibility(
            visible = deviceType != DeviceType.CPU,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                NumberField(
                    value = gpuLayers,
                    onValueChange = onGpuLayersChange,
                    defaultValue = 99,
                    label = stringResource(R.string.gpu_layers_empty_default),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KvCacheSection(
    cacheTypeK: CacheType,
    cacheTypeV: CacheType,
    kvOffload: Boolean,
    flashAttention: FlashAttentionMode,
    onCacheTypeKChange: (CacheType) -> Unit,
    onCacheTypeVChange: (CacheType) -> Unit,
    onKvOffloadChange: (Boolean) -> Unit,
    onFlashAttentionChange: (FlashAttentionMode) -> Unit
) {
    ConfigSectionCard(
        title = stringResource(R.string.kv_cache),
        icon = Icons.Default.Settings,
        iconColor = MaterialTheme.colorScheme.tertiary
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.kv_offload), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = kvOffload, onCheckedChange = onKvOffloadChange)
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var expandedK by remember { mutableStateOf(false) }
            var expandedV by remember { mutableStateOf(false) }
            
            ExposedDropdownMenuBox(
                expanded = expandedK,
                onExpandedChange = { expandedK = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = cacheTypeK.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.k_cache)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedK) },
                    modifier = Modifier.menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = expandedK, onDismissRequest = { expandedK = false }) {
                    CacheType.entries.forEach { 
                        DropdownMenuItem(text = { Text(it.name) }, onClick = { onCacheTypeKChange(it); expandedK = false })
                    }
                }
            }
            
            ExposedDropdownMenuBox(
                expanded = expandedV,
                onExpandedChange = { expandedV = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = cacheTypeV.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.v_cache)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedV) },
                    modifier = Modifier.menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = expandedV, onDismissRequest = { expandedV = false }) {
                    CacheType.entries.forEach { 
                        DropdownMenuItem(text = { Text(it.name) }, onClick = { onCacheTypeVChange(it); expandedV = false })
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        var expandedFa by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expandedFa,
            onExpandedChange = { expandedFa = it }
        ) {
            OutlinedTextField(
                value = flashAttention.name,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.flash_attention)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFa) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = expandedFa, onDismissRequest = { expandedFa = false }) {
                FlashAttentionMode.entries.forEach { 
                    DropdownMenuItem(text = { Text(it.name) }, onClick = { onFlashAttentionChange(it); expandedFa = false })
                }
            }
        }
    }
}

@Composable
fun ServerSection(
    port: Int,
    contextSize: Int,
    batchSize: Int,
    bindAll: Boolean,
    onPortChange: (Int?) -> Unit,
    onContextSizeChange: (Int?) -> Unit,
    onBatchSizeChange: (Int?) -> Unit,
    onBindAllChange: (Boolean) -> Unit
) {
    ConfigSectionCard(
        title = stringResource(R.string.server),
        icon = Icons.Default.Cloud,
        iconColor = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NumberField(
                value = port,
                onValueChange = onPortChange,
                defaultValue = 8080,
                label = stringResource(R.string.port),
                modifier = Modifier.weight(1f)
            )
            NumberField(
                value = contextSize,
                onValueChange = onContextSizeChange,
                defaultValue = 16384,
                label = stringResource(R.string.context),
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        NumberField(
            value = batchSize,
            onValueChange = onBatchSizeChange,
            defaultValue = 2048,
            label = stringResource(R.string.batch_size),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.bind_all_interfaces_short), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.allow_external_access),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = bindAll, onCheckedChange = onBindAllChange)
            }
        }
    }
}

@Composable
fun AutoFitSection(
    autoFit: Boolean,
    autoFitTargetMiB: Int,
    autoFitCtx: Int,
    onAutoFitChange: (Boolean) -> Unit,
    onAutoFitTargetChange: (Int?) -> Unit,
    onAutoFitCtxChange: (Int?) -> Unit
) {
    ConfigSectionCard(
        title = stringResource(R.string.adaptive_memory),
        icon = Icons.Default.Android,
        iconColor = MaterialTheme.colorScheme.secondary
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.enable_auto_fit), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.auto_fit_context_to_memory),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoFit, onCheckedChange = onAutoFitChange)
            }
        }
        
        AnimatedVisibility(
            visible = autoFit,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumberField(
                        value = autoFitTargetMiB,
                        onValueChange = onAutoFitTargetChange,
                        defaultValue = 1024,
                        label = stringResource(R.string.target_memory_margin),
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = autoFitCtx,
                        onValueChange = onAutoFitCtxChange,
                        defaultValue = 4096,
                        label = stringResource(R.string.minimum_context),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ExtraParamsSection(
    extraParams: String,
    onExtraParamsChange: (String) -> Unit
) {
    ConfigSectionCard(
        title = stringResource(R.string.extra_parameters),
        icon = Icons.Default.Settings,
        iconColor = MaterialTheme.colorScheme.tertiary
    ) {
        OutlinedTextField(
            value = extraParams,
            onValueChange = onExtraParamsChange,
            label = { Text(stringResource(R.string.custom_command_line_parameters)) },
            placeholder = {
                Text(
                    stringResource(R.string.extra_params_example),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 4,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun PoolingType.localizedName(): String = when (this) {
    PoolingType.NONE -> stringResource(R.string.none)
    PoolingType.MEAN -> stringResource(R.string.mean)
    PoolingType.CLS -> stringResource(R.string.cls)
    PoolingType.LAST -> stringResource(R.string.last)
    PoolingType.RANK -> stringResource(R.string.rank)
}

@Composable
private fun fitLabel(state: MemoryFitState): String = when (state) {
    MemoryFitState.WONT_FIT -> stringResource(R.string.fit_wont_fit)
    MemoryFitState.TIGHT_FIT -> stringResource(R.string.fit_tight_fit)
    MemoryFitState.GOOD_FIT -> stringResource(R.string.fit_good_fit)
}

@Composable
private fun MemoryFitState.fitColor(): Color = when (this) {
    MemoryFitState.WONT_FIT -> MaterialTheme.colorScheme.error
    MemoryFitState.TIGHT_FIT -> Color(0xFFB26A00)
    MemoryFitState.GOOD_FIT -> Color(0xFF2E7D32)
}

private fun formatBytes(bytes: Long): String {
    val absBytes = kotlin.math.abs(bytes.toDouble())
    val gib = 1024.0 * 1024.0 * 1024.0
    val mib = 1024.0 * 1024.0
    return if (absBytes >= gib) {
        "%.2f GiB".format(bytes / gib)
    } else {
        "%.0f MiB".format(bytes / mib)
    }
}

@Composable
fun ConfigSectionCard(
    title: String,
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            content()
        }
    }
}
