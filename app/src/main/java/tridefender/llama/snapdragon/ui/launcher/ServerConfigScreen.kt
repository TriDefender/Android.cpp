package tridefender.llama.snapdragon.ui.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tridefender.llama.snapdragon.R
import tridefender.llama.snapdragon.viewmodel.ModelConfigViewModel
import kotlinx.coroutines.launch

/**
 * Server Configuration Screen
 *
 * Provides UI for configuring:
 * - Port number (1-65535)
 * - Bind to all interfaces toggle
 * - API key (optional, with show/hide)
 * - Timeout (seconds)
 * - Server URL display with copy button
 */
@Composable
fun ServerConfigScreen(
    viewModel: ModelConfigViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val serverUrl = remember(config.port, config.bindAll) {
        if (config.bindAll) {
            "http://0.0.0.0:${config.port}"
        } else {
            "http://127.0.0.1:${config.port}"
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("serverConfigScreen")
    ) {
        ServerUrlCard(
            serverUrl = serverUrl,
            onCopyClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Server URL", serverUrl)
                clipboard.setPrimaryClip(clip)
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.copied))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        PortConfigCard(
            port = config.port,
            onPortChange = viewModel::updatePort,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        NetworkBindingCard(
            bindAll = config.bindAll,
            onBindAllChange = viewModel::updateBindAll,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        ApiKeyCard(
            apiKey = config.apiKey,
            onApiKeyChange = viewModel::updateApiKey,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        TimeoutCard(
            timeout = config.timeout,
            onTimeoutChange = viewModel::updateTimeout,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Card displaying the server URL with copy button.
 * Prominently styled to serve as the primary server access point.
 */
@Composable
fun ServerUrlCard(
    serverUrl: String,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.server_url),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(
                    onClick = onCopyClick,
                    modifier = Modifier.testTag("copyUrlButton")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.copy))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = serverUrl,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("serverUrlDisplay")
                )
            }
        }
    }
}

/**
 * Card for port configuration.
 * Validates range 1-65535.
 */
@Composable
fun PortConfigCard(
    port: Int,
    onPortChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var portText by remember(port) { mutableStateOf(port.toString()) }
    var isError by remember { mutableStateOf(false) }

    ConfigCard(
        title = stringResource(R.string.port),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.port_range_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = portText,
                onValueChange = { newValue ->
                    portText = newValue
                    val parsed = newValue.toIntOrNull()
                    if (parsed != null && parsed in 1..65535) {
                        isError = false
                        onPortChange(parsed)
                    } else if (newValue.isNotEmpty()) {
                        isError = true
                    }
                },
                label = { Text(stringResource(R.string.port_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = isError,
                supportingText = if (isError) {
                    { Text(stringResource(R.string.port_error)) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("portField"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    errorBorderColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

/**
 * Card for network binding configuration.
 * Toggle between localhost (127.0.0.1) and all interfaces (0.0.0.0).
 */
@Composable
fun NetworkBindingCard(
    bindAll: Boolean,
    onBindAllChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ConfigCard(
        title = stringResource(R.string.bind_all_interfaces),
        subtitle = stringResource(R.string.bind_all_desc),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (bindAll) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                    )
                    Text(
                        text = if (bindAll) "0.0.0.0" else "127.0.0.1",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (bindAll) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = bindAll,
                onCheckedChange = onBindAllChange,
                modifier = Modifier.testTag("bindAllSwitch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

/**
 * Card for API key configuration.
 * Optional field with show/hide toggle for password visibility.
 */
@Composable
fun ApiKeyCard(
    apiKey: String?,
    onApiKeyChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showApiKey by remember { mutableStateOf(false) }

    ConfigCard(
        title = stringResource(R.string.api_key),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.api_key_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = apiKey ?: "",
                onValueChange = { newValue ->
                    onApiKeyChange(newValue.ifBlank { null })
                },
                label = { Text(stringResource(R.string.api_key)) },
                placeholder = { Text(stringResource(R.string.api_key_optional)) },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { showApiKey = !showApiKey },
                        modifier = Modifier.testTag("apiKeyVisibilityToggle")
                    ) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Outlined.VisibilityOff
                                         else Icons.Outlined.Visibility,
                            contentDescription = if (showApiKey) stringResource(R.string.hide)
                                                else stringResource(R.string.show)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apiKeyField"),
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
 * Card for timeout configuration.
 * Time in seconds before request timeout.
 */
@Composable
fun TimeoutCard(
    timeout: Int,
    onTimeoutChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var timeoutText by remember(timeout) { mutableStateOf(timeout.toString()) }
    var isError by remember { mutableStateOf(false) }

    ConfigCard(
        title = stringResource(R.string.timeout),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.timeout_format, timeout),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("timeoutValue")
                )
            }

            OutlinedTextField(
                value = timeoutText,
                onValueChange = { newValue ->
                    timeoutText = newValue
                    val parsed = newValue.toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        isError = false
                        onTimeoutChange(parsed)
                    } else if (newValue.isNotEmpty()) {
                        isError = true
                    }
                },
                label = { Text(stringResource(R.string.timeout_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = isError,
                supportingText = if (isError) {
                    { Text(stringResource(R.string.timeout_error)) }
                } else null,
                suffix = { Text(stringResource(R.string.seconds)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("timeoutField"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    errorBorderColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}
