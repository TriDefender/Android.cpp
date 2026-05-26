package tridefender.llama.snapdragon.ui.launcher

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tridefender.llama.snapdragon.R
import tridefender.llama.snapdragon.model.CacheType
import tridefender.llama.snapdragon.model.FlashAttentionMode
import tridefender.llama.snapdragon.viewmodel.ModelConfigViewModel

/**
 * KV Cache Configuration Screen
 * 
 * Provides UI for configuring:
 * - K Cache Type (quantization format for key cache)
 * - V Cache Type (quantization format for value cache)
 * - KV Offload toggle (offload KV cache to GPU)
 * - Flash Attention mode (Auto/On/Off)
 */
@Composable
fun KVCacheConfigScreen(
    viewModel: ModelConfigViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("kvCacheConfigScreen")
    ) {
        KCacheTypeCard(
            selectedType = config.cacheTypeK,
            onTypeChange = viewModel::updateCacheTypeK,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        VCacheTypeCard(
            selectedType = config.cacheTypeV,
            onTypeChange = viewModel::updateCacheTypeV,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        KvOffloadCard(
            enabled = config.kvOffload,
            onEnabledChange = viewModel::updateKvOffload,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FlashAttentionCard(
            mode = config.flashAttention,
            onModeChange = viewModel::updateFlashAttention,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Card for K Cache Type selection with dropdown.
 */
@Composable
fun KCacheTypeCard(
    selectedType: CacheType,
    onTypeChange: (CacheType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ConfigCard(
        title = stringResource(R.string.k_cache_type),
        subtitle = stringResource(R.string.k_cache_type_desc),
        modifier = modifier
    ) {
        CacheTypeDropdown(
            selectedType = selectedType,
            expanded = expanded,
            onExpandChange = { expanded = it },
            onTypeChange = onTypeChange,
            testTagPrefix = "kCache"
        )
    }
}

/**
 * Card for V Cache Type selection with dropdown.
 */
@Composable
fun VCacheTypeCard(
    selectedType: CacheType,
    onTypeChange: (CacheType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ConfigCard(
        title = stringResource(R.string.v_cache_type),
        subtitle = stringResource(R.string.v_cache_type_desc),
        modifier = modifier
    ) {
        CacheTypeDropdown(
            selectedType = selectedType,
            expanded = expanded,
            onExpandChange = { expanded = it },
            onTypeChange = onTypeChange,
            testTagPrefix = "vCache"
        )
    }
}

/**
 * Reusable dropdown for cache type selection.
 */
@Composable
fun CacheTypeDropdown(
    selectedType: CacheType,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onTypeChange: (CacheType) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onExpandChange(true) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = selectedType.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandChange(false) },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .testTag("${testTagPrefix}DropdownMenu")
        ) {
            CacheType.entries.forEach { cacheType ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = cacheType.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    onClick = {
                        onTypeChange(cacheType)
                        onExpandChange(false)
                    },
                    modifier = Modifier.testTag("${testTagPrefix}Option_${cacheType.name}"),
                    trailingIcon = {
                        if (cacheType == selectedType) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

/**
 * Card for KV Offload toggle.
 */
@Composable
fun KvOffloadCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ConfigCard(
        title = stringResource(R.string.kv_offload),
        subtitle = stringResource(R.string.kv_offload_desc),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (enabled) stringResource(R.string.on) else stringResource(R.string.off),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (enabled) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.testTag("kvOffloadSwitch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

/**
 * Card for Flash Attention mode selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlashAttentionCard(
    mode: FlashAttentionMode,
    onModeChange: (FlashAttentionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    ConfigCard(
        title = stringResource(R.string.flash_attention),
        subtitle = stringResource(R.string.flash_attention_desc),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlashAttentionMode.entries.forEach { flashMode ->
                    FilterChip(
                        selected = mode == flashMode,
                        onClick = { onModeChange(flashMode) },
                        label = { 
                            Text(
                                text = when (flashMode) {
                                    FlashAttentionMode.AUTO -> stringResource(R.string.auto)
                                    FlashAttentionMode.ON -> stringResource(R.string.on)
                                    FlashAttentionMode.OFF -> stringResource(R.string.off)
                                },
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        modifier = Modifier.testTag("flashAttentionChip_${flashMode.name}")
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (mode) {
                        FlashAttentionMode.AUTO -> stringResource(R.string.flash_attention_auto)
                        FlashAttentionMode.ON -> stringResource(R.string.flash_attention_on)
                        FlashAttentionMode.OFF -> stringResource(R.string.flash_attention_off)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("flashAttentionValue")
                )
            }
        }
    }
}
