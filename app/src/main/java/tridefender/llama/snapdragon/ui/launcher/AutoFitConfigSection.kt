package tridefender.llama.snapdragon.ui.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import tridefender.llama.snapdragon.R

/**
 * Auto-Fit Configuration Section
 * 
 * Provides UI for configuring:
 * - Auto-Fit enable/disable toggle
 * - Target memory margin slider (256-4096 MiB)
 * 
 * When Auto-Fit is enabled, the slider becomes visible to adjust
 * the target memory margin for automatic context size adjustment.
 */
@Composable
fun AutoFitConfigSection(
    autoFitEnabled: Boolean,
    targetMemoryMiB: Int,
    onAutoFitChange: (Boolean) -> Unit,
    onTargetMemoryChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Auto-Fit Toggle Card
        ConfigCard(
            title = stringResource(R.string.auto_fit),
            subtitle = stringResource(R.string.auto_fit_desc),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = if (autoFitEnabled) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    
                    Text(
                        text = if (autoFitEnabled) 
                            stringResource(R.string.on) 
                        else 
                            stringResource(R.string.off),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (autoFitEnabled) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("autoFitStatus")
                    )
                }
                
                Switch(
                    checked = autoFitEnabled,
                    onCheckedChange = onAutoFitChange,
                    modifier = Modifier.testTag("autoFitSwitch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
        
        // Target Memory Margin Slider (only visible when Auto-Fit is enabled)
        AnimatedVisibility(
            visible = autoFitEnabled,
            enter = fadeIn(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + expandVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ),
            exit = fadeOut(
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) + shrinkVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            )
        ) {
            TargetMemoryMarginCard(
                targetMemoryMiB = targetMemoryMiB,
                onTargetMemoryChange = onTargetMemoryChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Card for target memory margin configuration.
 * Uses a slider to select values between 256-4096 MiB.
 */
@Composable
private fun TargetMemoryMarginCard(
    targetMemoryMiB: Int,
    onTargetMemoryChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ConfigCard(
        title = stringResource(R.string.target_memory_margin),
        subtitle = stringResource(R.string.memory_margin_desc),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Current value display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.mib_format, targetMemoryMiB),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("targetMemoryValue")
                )
            }
            
            // Slider for memory margin selection
            val sliderPosition = remember(targetMemoryMiB) {
                ((targetMemoryMiB - 256).toFloat() / (4096 - 256)).coerceIn(0f, 1f)
            }
            
            Slider(
                value = sliderPosition,
                onValueChange = { newValue ->
                    val memory = (256 + newValue * (4096 - 256)).toInt()
                    // Round to nearest 64 MiB for cleaner values
                    val rounded = ((memory + 32) / 64) * 64
                    onTargetMemoryChange(rounded.coerceIn(256, 4096))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("targetMemorySlider"),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            
            // Min/Max labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.mib_format, 256),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.mib_format, 4096),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
