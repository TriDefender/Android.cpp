package tridefender.llama.snapdragon.ui.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import tridefender.llama.snapdragon.model.LogEntry
import tridefender.llama.snapdragon.model.LogLevel
import tridefender.llama.snapdragon.model.ServerState
import tridefender.llama.snapdragon.model.ServerStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeScreen(
    viewModel: RuntimeViewModel = hiltViewModel()
) {
    val serverStatus by viewModel.serverStatus.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val config by viewModel.config.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                        text = "Server Status",
                        style = MaterialTheme.typography.titleMedium
                    )
                    StatusIndicator(serverStatus.state)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                ServerInfoRow("Model", java.net.URLDecoder.decode(config.modelPath, "UTF-8").substringAfterLast("/"))
                ServerInfoRow("Port", config.getEffectivePort().toString())
                ServerInfoRow("Context", config.getEffectiveContextSize().toString())
                ServerInfoRow("Device", config.deviceType.name)
                
                if (serverStatus.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: ${serverStatus.errorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.startServer() },
                enabled = serverStatus.state != ServerState.RUNNING && serverStatus.state != ServerState.STARTING,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start")
            }
            
            Button(
                onClick = { viewModel.stopServer() },
                enabled = serverStatus.state == ServerState.RUNNING || serverStatus.state == ServerState.STARTING,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Logs",
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(onClick = { viewModel.clearLogs() }) {
                        Text("Clear")
                    }
                }
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    reverseLayout = true
                ) {
                    items(logs.reversed()) { log ->
                        LogEntryItem(log)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(state: ServerState) {
    val (color, text) = when (state) {
        ServerState.IDLE -> Color.Gray to "Idle"
        ServerState.STARTING -> Color.Yellow to "Starting"
        ServerState.RUNNING -> Color.Green to "Running"
        ServerState.STOPPING -> Color(0xFFFFA500) to "Stopping"
        ServerState.STOPPED -> Color.Gray to "Stopped"
        ServerState.ERROR -> Color.Red to "Error"
    }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(6.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ServerInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
fun LogEntryItem(log: LogEntry) {
    val color = when (log.level) {
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.WARNING -> Color(0xFFFFA000)
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "[${log.level.name.first()}]",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip
        )
    }
}
