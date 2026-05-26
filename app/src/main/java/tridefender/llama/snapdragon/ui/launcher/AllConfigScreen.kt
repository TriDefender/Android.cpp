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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import tridefender.llama.snapdragon.model.CacheType
import tridefender.llama.snapdragon.model.DeviceType
import tridefender.llama.snapdragon.model.FlashAttentionMode
import tridefender.llama.snapdragon.model.PoolingType
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
                "\u9ed8\u8ba4: $defaultValue", 
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

                viewModel.updateModelPath(it.toString())
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
            isEmbedding = config.isEmbedding,
            poolingType = config.poolingType,
            onIsEmbeddingChange = { viewModel.updateIsEmbedding(it) },
            onPoolingTypeChange = { viewModel.updatePoolingType(it) },
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
    isEmbedding: Boolean,
    poolingType: PoolingType,
    onIsEmbeddingChange: (Boolean) -> Unit,
    onPoolingTypeChange: (PoolingType) -> Unit,
    onBrowseClick: () -> Unit
) {
    ConfigSectionCard(
        title = "\u6a21\u578b\u914d\u7f6e",
        icon = Icons.Default.Storage,
        iconColor = MaterialTheme.colorScheme.primary
    ) {
        val displayName = modelPath.substringAfterLast("%2F").substringAfterLast("/")
        
        OutlinedTextField(
            value = displayName.ifEmpty { "\u70b9\u51fb\u9009\u62e9\u6a21\u578b\u6587\u4ef6" },
            onValueChange = {},
            label = { Text("\u6a21\u578b\u6587\u4ef6") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onBrowseClick) {
                    Icon(Icons.Default.Folder, contentDescription = "\u6d4f\u89c8")
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
        
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
                        "\u5d4c\u5165\u6a21\u5f0f",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "\u7528\u4e8e\u5d4c\u5165\u5411\u91cf\u6a21\u578b",
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
                        value = when (poolingType) {
                            PoolingType.NONE -> "None"
                            PoolingType.MEAN -> "Mean"
                            PoolingType.CLS -> "CLS"
                            PoolingType.LAST -> "Last"
                            PoolingType.RANK -> "Rank"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pooling \u6a21\u5f0f") },
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
                                        Text(when (type) {
                                            PoolingType.NONE -> "None"
                                            PoolingType.MEAN -> "Mean"
                                            PoolingType.CLS -> "CLS"
                                            PoolingType.LAST -> "Last"
                                            PoolingType.RANK -> "Rank"
                                        })
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
        title = "\u8ba1\u7b97\u8bbe\u5907",
        icon = Icons.Default.Memory,
        iconColor = MaterialTheme.colorScheme.secondary
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            val deviceLabel = when (deviceType) {
                DeviceType.CPU -> "CPU"
                DeviceType.OPENCL -> "OpenCL (GPU)"
                DeviceType.HTP0 -> "HTP 0 (NPU)"
                DeviceType.HTP1 -> "HTP 1 (NPU)"
                DeviceType.HTP2 -> "HTP 2 (NPU)"
                DeviceType.HTP3 -> "HTP 3 (NPU)"
                DeviceType.HTP4 -> "HTP 4 (NPU)"
            }
            OutlinedTextField(
                value = deviceLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("\u8bbe\u5907\u7c7b\u578b") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(text = { Text("CPU") }, onClick = { onDeviceTypeChange(DeviceType.CPU); expanded = false })
                DropdownMenuItem(text = { Text("OpenCL (GPU)") }, onClick = { onDeviceTypeChange(DeviceType.OPENCL); expanded = false })
                DropdownMenuItem(text = { Text("HTP 0 (NPU)") }, onClick = { onDeviceTypeChange(DeviceType.HTP0); expanded = false })
                DropdownMenuItem(text = { Text("HTP 1 (NPU)") }, onClick = { onDeviceTypeChange(DeviceType.HTP1); expanded = false })
                DropdownMenuItem(text = { Text("HTP 2 (NPU)") }, onClick = { onDeviceTypeChange(DeviceType.HTP2); expanded = false })
                DropdownMenuItem(text = { Text("HTP 3 (NPU)") }, onClick = { onDeviceTypeChange(DeviceType.HTP3); expanded = false })
                DropdownMenuItem(text = { Text("HTP 4 (NPU)") }, onClick = { onDeviceTypeChange(DeviceType.HTP4); expanded = false })
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
                    label = "GPU \u5c42\u6570 (\u7a7a=99)",
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
        title = "KV \u7f13\u5b58",
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
                Text("KV Offload", style = MaterialTheme.typography.bodyLarge)
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
                    label = { Text("K Cache") },
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
                    label = { Text("V Cache") },
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
                label = { Text("Flash Attention") },
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
        title = "\u670d\u52a1\u5668",
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
                label = "\u7aef\u53e3",
                modifier = Modifier.weight(1f)
            )
            NumberField(
                value = contextSize,
                onValueChange = onContextSizeChange,
                defaultValue = 16384,
                label = "\u4e0a\u4e0b\u6587",
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        NumberField(
            value = batchSize,
            onValueChange = onBatchSizeChange,
            defaultValue = 2048,
            label = "\u6279\u5904\u7406\u5927\u5c0f",
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
                    Text("\u7ed1\u5b9a\u6240\u6709\u63a5\u53e3", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "\u5141\u8bb8\u5916\u90e8\u8bbf\u95ee (0.0.0.0)",
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
        title = "\u81ea\u9002\u5e94\u5185\u5b58",
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
                    Text("\u542f\u7528\u81ea\u9002\u5e94", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "\u81ea\u52a8\u8c03\u6574\u4e0a\u4e0b\u6587\u4ee5\u9002\u5e94\u5185\u5b58",
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
                        label = "\u76ee\u6807\u4f59\u91cf (MiB)",
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = autoFitCtx,
                        onValueChange = onAutoFitCtxChange,
                        defaultValue = 4096,
                        label = "\u6700\u5c0f\u4e0a\u4e0b\u6587",
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
        title = "\u989d\u5916\u53c2\u6570",
        icon = Icons.Default.Settings,
        iconColor = MaterialTheme.colorScheme.tertiary
    ) {
        OutlinedTextField(
            value = extraParams,
            onValueChange = onExtraParamsChange,
            label = { Text("\u81ea\u5b9a\u4e49\u547d\u4ee4\u884c\u53c2\u6570") },
            placeholder = {
                Text(
                    "\u4f8b\u5982: --arg1 value1 --arg2 value2",
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
