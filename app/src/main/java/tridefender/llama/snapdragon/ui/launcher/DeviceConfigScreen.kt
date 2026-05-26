package tridefender.llama.snapdragon.ui.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tridefender.llama.snapdragon.R
import tridefender.llama.snapdragon.model.DeviceType
import tridefender.llama.snapdragon.viewmodel.ModelConfigViewModel

/**
 * GPU Layers mode selection
 */
enum class GpuLayersMode { AUTO, ALL, MANUAL }

private val GPU_LAYERS_MODES = listOf(
    GpuLayersMode.AUTO to R.string.auto,
    GpuLayersMode.ALL to R.string.all,
    GpuLayersMode.MANUAL to R.string.manual
)

/**
 * Device Configuration Screen
 * 
 * Provides UI for configuring:
 * - Device selection (CPU, OpenCL, HTP0-4)
 * - GPU layers offloading (Auto, All, Manual)
 * - Device availability indicators
 */
@Composable
fun DeviceConfigScreen(
    viewModel: ModelConfigViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("deviceConfigScreen")
    ) {
        DeviceSelectionCard(
            selectedDevice = config.deviceType,
            onDeviceChange = viewModel::updateDeviceType,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        GpuLayersCard(
            gpuLayers = config.gpuLayers,
            onGpuLayersChange = viewModel::updateGpuLayers,
            isCpuSelected = config.deviceType == DeviceType.CPU,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Card for device selection with dropdown.
 * Shows device type and availability indicator.
 */
@Composable
fun DeviceSelectionCard(
    selectedDevice: DeviceType,
    onDeviceChange: (DeviceType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ConfigCard(
        title = stringResource(R.string.device_selection),
        subtitle = stringResource(R.string.device_selection_desc),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DeviceIcon(
                        deviceType = selectedDevice,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Column {
                        Text(
                            text = stringResource(getDeviceNameRes(selectedDevice)),
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .testTag("deviceDropdownMenu")
            ) {
                DeviceType.entries.forEach { deviceType ->
                    DeviceDropdownItem(
                        deviceType = deviceType,
                        isSelected = deviceType == selectedDevice,
                        isAvailable = true, // UI only - always show as available
                        onClick = {
                            onDeviceChange(deviceType)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Dropdown item for device selection.
 */
@Composable
fun DeviceDropdownItem(
    deviceType: DeviceType,
    isSelected: Boolean,
    isAvailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AvailabilityDot(
                    isAvailable = isAvailable,
                    size = 8.dp
                )
                
                Column {
                    Text(
                        text = stringResource(getDeviceNameRes(deviceType)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(if (isAvailable) R.string.available else R.string.unavailable),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAvailable) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        onClick = onClick,
        modifier = modifier.testTag("deviceOption_${deviceType.name}"),
        trailingIcon = {
            if (isSelected) {
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

/**
 * Card for GPU layers configuration.
 * Includes mode selection (Auto/All/Manual) and slider.
 */
@Composable
fun GpuLayersCard(
    gpuLayers: Int,
    onGpuLayersChange: (Int) -> Unit,
    isCpuSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val currentMode = remember(gpuLayers) {
        when {
            gpuLayers == -1 -> GpuLayersMode.AUTO
            gpuLayers >= 999 -> GpuLayersMode.ALL
            else -> GpuLayersMode.MANUAL
        }
    }
    
    ConfigCard(
        title = stringResource(R.string.gpu_layers),
        subtitle = stringResource(R.string.gpu_layers_desc),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Mode selection chips
            GpuLayersModeSelector(
                currentMode = currentMode,
                onModeChange = { mode ->
                    val newLayers = when (mode) {
                        GpuLayersMode.AUTO -> -1
                        GpuLayersMode.ALL -> 999
                        GpuLayersMode.MANUAL -> 1
                    }
                    onGpuLayersChange(newLayers)
                },
                enabled = !isCpuSelected
            )
            
            // Manual slider (only visible in manual mode)
            if (currentMode == GpuLayersMode.MANUAL && !isCpuSelected) {
                GpuLayersSlider(
                    value = gpuLayers.coerceIn(1, 100),
                    onValueChange = onGpuLayersChange
                )
            }
            
            // Current value display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isCpuSelected -> "CPU mode - no GPU offload"
                        currentMode == GpuLayersMode.AUTO -> stringResource(R.string.auto)
                        currentMode == GpuLayersMode.ALL -> stringResource(R.string.all)
                        else -> stringResource(R.string.layers_count, gpuLayers)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (isCpuSelected) 
                        MaterialTheme.colorScheme.onSurfaceVariant 
                    else 
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("gpuLayersValue")
                )
            }
        }
    }
}

/**
 * Mode selector chips for GPU layers configuration.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GpuLayersModeSelector(
    currentMode: GpuLayersMode,
    onModeChange: (GpuLayersMode) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GPU_LAYERS_MODES.forEach { (mode, labelRes) ->
            val selected = currentMode == mode
            
            FilterChip(
                selected = selected && enabled,
                onClick = { onModeChange(mode) },
                label = { 
                    Text(
                        text = stringResource(labelRes),
                        fontFamily = FontFamily.Monospace
                    )
                },
                enabled = enabled,
                modifier = Modifier.testTag("gpuModeChip_${mode.name}")
            )
        }
    }
}

/**
 * Slider for manual GPU layers selection.
 */
@Composable
fun GpuLayersSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sliderPosition = remember(value) {
        ((value - 1).toFloat() / 99).coerceIn(0f, 1f)
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderPosition,
            onValueChange = { newPosition ->
                val layers = (1 + newPosition * 99).toInt()
                onValueChange(layers)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gpuLayersSlider"),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "100",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Availability indicator dot.
 * Green for available, red for unavailable.
 */
@Composable
fun AvailabilityDot(
    isAvailable: Boolean,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = if (isAvailable) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.error,
        animationSpec = tween(300),
        label = "availabilityDotColor"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * Device icon based on device type.
 */
@Composable
fun DeviceIcon(
    deviceType: DeviceType,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = Icons.Outlined.Memory,
        contentDescription = null,
        tint = when (deviceType) {
            DeviceType.CPU -> MaterialTheme.colorScheme.onSurfaceVariant
            DeviceType.OPENCL -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        modifier = modifier
    )
}

/**
 * Get string resource ID for device name.
 */
@Composable
private fun getDeviceNameRes(deviceType: DeviceType): Int = when (deviceType) {
    DeviceType.CPU -> R.string.device_cpu
    DeviceType.OPENCL -> R.string.device_opencl
    DeviceType.HTP0 -> R.string.device_htp0
    DeviceType.HTP1 -> R.string.device_htp1
    DeviceType.HTP2 -> R.string.device_htp2
    DeviceType.HTP3 -> R.string.device_htp3
    DeviceType.HTP4 -> R.string.device_htp4
}
