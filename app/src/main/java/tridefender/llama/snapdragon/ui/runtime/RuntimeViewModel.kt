package tridefender.llama.snapdragon.ui.runtime

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tridefender.llama.snapdragon.model.LogEntry
import tridefender.llama.snapdragon.model.LogLevel
import tridefender.llama.snapdragon.model.ServerConfig
import tridefender.llama.snapdragon.model.ServerState
import tridefender.llama.snapdragon.model.ServerStatus
import tridefender.llama.snapdragon.repository.ConfigRepository
import tridefender.llama.snapdragon.service.ServerProcessManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RuntimeViewModel @Inject constructor(
    private val serverProcessManager: ServerProcessManager,
    private val configRepository: ConfigRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "RuntimeViewModel"
    }
    
    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()
    
    private val _serverStatus = MutableStateFlow(ServerStatus(ServerState.IDLE))
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _serverOutput = MutableStateFlow("")
    val serverOutput: StateFlow<String> = _serverOutput.asStateFlow()

    private var lastOutputLength = 0

    init {
        viewModelScope.launch {
            serverProcessManager.serverStatus.collect { status ->
                _serverStatus.value = status
                val effectivePort = _config.value.getEffectivePort()
                when (status.state) {
                    ServerState.STARTING -> addLog(LogEntry(LogLevel.INFO, "Server starting..."))
                    ServerState.RUNNING -> addLog(LogEntry(LogLevel.INFO, "Server running on port $effectivePort"))
                    ServerState.STOPPING -> addLog(LogEntry(LogLevel.INFO, "Server stopping..."))
                    ServerState.STOPPED -> addLog(LogEntry(LogLevel.INFO, "Server stopped"))
                    ServerState.ERROR -> addLog(LogEntry(LogLevel.ERROR, status.errorMessage ?: "Unknown error"))
                    ServerState.IDLE -> { }
                }
            }
        }

        viewModelScope.launch {
            serverProcessManager.realtimeOutput.collect { output ->
                _serverOutput.value = output
                if (output.length > lastOutputLength) {
                    val newLines = output.substring(lastOutputLength).lines().filter { it.isNotBlank() }
                    newLines.forEach { line ->
                        val level = when {
                            line.lowercase().contains("error") || line.lowercase().contains("fail") || line.lowercase().contains("exception") -> LogLevel.ERROR
                            line.lowercase().contains("warn") -> LogLevel.WARNING
                            else -> LogLevel.INFO
                        }
                        addLog(LogEntry(level, line))
                    }
                    lastOutputLength = output.length
                }
            }
        }

        loadConfig()
    }
    
    private fun loadConfig() {
        viewModelScope.launch {
            val savedConfig = configRepository.loadConfig()
            _config.value = savedConfig
        }
    }
    
    fun startServer() {
        viewModelScope.launch {
            lastOutputLength = 0
            clearLogs()
            val currentConfig = configRepository.loadConfig()
            _config.value = currentConfig
            if (currentConfig.modelPath.isNotBlank()) {
                serverProcessManager.startServer(currentConfig)
                addLog(LogEntry(LogLevel.INFO, "Server starting with model: ${currentConfig.modelPath}"))
            } else {
                addLog(LogEntry(LogLevel.ERROR, "No model path configured"))
            }
        }
    }

    fun stopServer() {
        serverProcessManager.stopServer()
        addLog(LogEntry(LogLevel.INFO, "Server stopped by user"))
    }
    
    fun clearLogs() {
        _logs.value = emptyList()
    }
    
    private fun addLog(entry: LogEntry) {
        _logs.update { it + entry }
        Log.d(TAG, "${entry.level}: ${entry.message}")
    }
    
    fun isServerRunning(): Boolean {
        return serverProcessManager.isServerRunning()
    }
}
