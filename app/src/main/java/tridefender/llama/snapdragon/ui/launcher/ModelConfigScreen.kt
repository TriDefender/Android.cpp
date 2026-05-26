package tridefender.llama.snapdragon.ui.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tridefender.llama.snapdragon.R
import tridefender.llama.snapdragon.model.PoolingType
import tridefender.llama.snapdragon.viewmodel.ModelConfigViewModel
import kotlin.math.log2
import kotlin.math.pow

private val CONTEXT_SIZE_PRESETS = listOf(
    "4K" to 4096,
    "8K" to 8192,
    "16K" to 16384,
    "32K" to 32768,
    "64K" to 65536,
    "128K" to 131072
)

/**
 * Model Configuration Screen
 * 
 * Provides UI for configuring:
 * - Model file path selection
 * - Context size (with presets)
 * - Batch size
 * - Predict tokens
 * - Model alias
 */
@Composable
fun ModelConfigScreen(
    viewModel: ModelConfigViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("modelConfigScreen")
    ) {
        ModelFileCard(
            modelPath = config.modelPath,
            onBrowseClick = { /* TODO: File picker */ },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ContextSizeCard(
            contextSize = config.contextSize,
            onContextSizeChange = viewModel::updateContextSize,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        BatchSizeCard(
            batchSize = config.batchSize,
            onBatchSizeChange = viewModel::updateBatchSize,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        AdvancedSettingsCard(
            predictTokens = config.predictTokens,
            isEmbedding = config.isEmbedding,
            poolingType = config.poolingType,
            onPredictTokensChange = viewModel::updatePredictTokens,
            onIsEmbeddingChange = viewModel::updateIsEmbedding,
            onPoolingTypeChange = viewModel::updatePoolingType,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Card for model file selection.
 * Shows current path and browse button.
 */
@Composable
fun ModelFileCard(
    modelPath: String,
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ConfigCard(
        title = stringResource(R.string.model_file),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = modelPath,
                onValueChange = { /* Read-only, updated via browse */ },
                label = { Text(stringResource(R.string.model_path_label)) },
                placeholder = { Text(stringResource(R.string.select_model_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    TextButton(
                        onClick = onBrowseClick,
                        modifier = Modifier.testTag("browseButton")
                    ) {
                        Text(stringResource(R.string.browse))
                    }
                },
                singleLine = true,
                readOnly = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("modelPathField"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

/**
 * Card for context size configuration.
 * Includes slider and preset chips for quick selection.
 */
@Composable
fun ContextSizeCard(
    contextSize: Int,
    onContextSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ConfigCard(
        title = stringResource(R.string.context_size),
        subtitle = stringResource(R.string.context_size_desc),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val sliderValue = remember(contextSize) {
                // Logarithmic scale for better UX across wide range (512-131072)
                if (contextSize >= 512) {
                    (log2(contextSize.toFloat()) - log2(512f)) / (log2(131072f) - log2(512f))
                } else 0f
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tokens_count, contextSize),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("contextSizeValue")
                )
            }
            
            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    val logMin = log2(512f)
                    val logMax = log2(131072f)
                    val logValue = logMin + newValue * (logMax - logMin)
                    val rounded = 2.0.pow(logValue.roundToInt()).toInt()
                    onContextSizeChange(rounded.coerceIn(512, 131072))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contextSizeSlider"),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            
            PresetChips(
                options = CONTEXT_SIZE_PRESETS,
                selectedValue = contextSize,
                onSelect = onContextSizeChange,
                testTagPrefix = "contextPreset"
            )
        }
    }
}

/**
 * Card for batch size configuration.
 */
@Composable
fun BatchSizeCard(
    batchSize: Int,
    onBatchSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ConfigCard(
        title = stringResource(R.string.batch_size),
        subtitle = stringResource(R.string.batch_size_desc),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.batch_count, batchSize),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("batchSizeValue")
                )
            }
            
            val sliderPosition = remember(batchSize) {
                ((batchSize - 128).toFloat() / (8192 - 128)).coerceIn(0f, 1f)
            }
            
            Slider(
                value = sliderPosition,
                onValueChange = { newValue ->
                    val size = (128 + newValue * (8192 - 128)).toInt()
                    val rounded = ((size + 64) / 128) * 128
                    onBatchSizeChange(rounded.coerceIn(128, 8192))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("batchSizeSlider"),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(256, 512, 1024, 2048).forEach { size ->
                    FilterChip(
                        selected = batchSize == size,
                        onClick = { onBatchSizeChange(size) },
                        label = { 
                            Text(
                                text = size.toString(),
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        modifier = Modifier.testTag("batchPreset$size")
                    )
                }
            }
        }
    }
}

/**
 * Card for advanced settings: predict tokens and embedding mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsCard(
    predictTokens: Int,
    isEmbedding: Boolean,
    poolingType: PoolingType,
    onPredictTokensChange: (Int) -> Unit,
    onIsEmbeddingChange: (Boolean) -> Unit,
    onPoolingTypeChange: (PoolingType) -> Unit,
    modifier: Modifier = Modifier
) {
    ConfigCard(
        title = stringResource(R.string.advanced_settings),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.predict_tokens),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.predict_tokens_info),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                OutlinedTextField(
                    value = if (predictTokens == -1) "" else predictTokens.toString(),
                    onValueChange = { newValue ->
                        val parsed = newValue.toIntOrNull()
                        onPredictTokensChange(parsed ?: -1)
                    },
                    label = { Text(stringResource(R.string.predict_tokens_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("predictTokensField"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.embedding_mode),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.embedding_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    androidx.compose.material3.Switch(
                        checked = isEmbedding,
                        onCheckedChange = onIsEmbeddingChange,
                        modifier = Modifier.testTag("embeddingSwitch")
                    )
                }
            }
            
            AnimatedVisibility(
                visible = isEmbedding,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
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
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(when (type) {
                                    PoolingType.NONE -> "None"
                                    PoolingType.MEAN -> "Mean"
                                    PoolingType.CLS -> "CLS"
                                    PoolingType.LAST -> "Last"
                                    PoolingType.RANK -> "Rank"
                                }) },
                                onClick = { onPoolingTypeChange(type); expanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable card container for configuration sections.
 */
@Composable
fun ConfigCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            content()
        }
    }
}

/**
 * Row of preset chips for quick value selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetChips(
    options: List<Pair<String, Int>>,
    selectedValue: Int,
    onSelect: (Int) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = selectedValue == value,
                onClick = { onSelect(value) },
                label = { 
                    Text(
                        text = label,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("${testTagPrefix}_$label")
            )
        }
    }
}

private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
