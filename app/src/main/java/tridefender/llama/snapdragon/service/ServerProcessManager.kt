package tridefender.llama.snapdragon.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import tridefender.llama.snapdragon.model.ServerConfig
import tridefender.llama.snapdragon.model.ServerStatus
import tridefender.llama.snapdragon.model.FlashAttentionMode
import tridefender.llama.snapdragon.model.DeviceType
import tridefender.llama.snapdragon.model.ServerState
import tridefender.llama.snapdragon.repository.ConfigRepository
import tridefender.llama.snapdragon.util.BinaryExtractor
import tridefender.llama.snapdragon.util.UriUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerProcessManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: ConfigRepository
) {
    
    companion object {
        private const val TAG = "ServerProcessManager"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "llama_server_channel"
    }
    
    private var serverProcess: Process? = null
    private var serverJob: Job? = null
    private var outputJob: Job? = null
    private val _serverStatus = MutableStateFlow(ServerStatus(ServerState.IDLE))
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val _serverOutput = MutableStateFlow("")
    val serverOutput: StateFlow<String> = _serverOutput.asStateFlow()

    private val _realtimeOutput = MutableStateFlow("")
    val realtimeOutput: StateFlow<String> = _realtimeOutput.asStateFlow()
    
    init {
        createNotificationChannel()
        Log.d(TAG, "ServerProcessManager initialized")
    }
    
    fun startServer(config: ServerConfig) {
        cleanup()
        
        if (!BinaryExtractor.ensureAllAvailable(context)) {
            Log.e(TAG, "Failed to setup libraries")
            _serverStatus.update { ServerStatus(
                state = ServerState.ERROR, 
                errorMessage = "Failed to setup libraries and binary"
            )}
            return
        }
        
        val binaryPath = BinaryExtractor.getBinaryPath(context)
        Log.i(TAG, "Using binary at: $binaryPath")
        
        val cmd = buildCommand(config, binaryPath)
        
        try {
            val builder = ProcessBuilder(cmd)
            builder.redirectErrorStream(true)
            builder.directory(context.filesDir)
            
            val env = builder.environment()
            val libDir = BinaryExtractor.getLibraryDir(context)
            val filesDir = context.filesDir.absolutePath
            val systemLibDirs = listOf(
                "/system/lib64",
                "/system/vendor/lib64",
                "/vendor/lib64",
                "/vendor/dsp/cdsp"
            )
            val existingLdPath = env["LD_LIBRARY_PATH"] ?: ""
            val allPaths = listOf(filesDir, libDir) + systemLibDirs + listOf(existingLdPath)
            env["LD_LIBRARY_PATH"] = allPaths.filter { it.isNotEmpty() }.joinToString(":")
            env["ADSP_LIBRARY_PATH"] = libDir
            
            val isHexagonDevice = config.deviceType in listOf(
                DeviceType.HTP0, DeviceType.HTP1, DeviceType.HTP2, 
                DeviceType.HTP3, DeviceType.HTP4
            )
            
            if (isHexagonDevice) {
                env["GGML_HEXAGON_EXPERIMENTAL"] = "1"
                if (config.flashAttention == FlashAttentionMode.ON) {
                    env["GGML_HEXAGON_EXPERIMENTAL"] = "1"
                }
                Log.i(TAG, "GGML_HEXAGON_EXPERIMENTAL: ${env["GGML_HEXAGON_EXPERIMENTAL"]}")
            }
            
            val process = builder.start()
            serverProcess = process
            serverJob = process.toJob()
            outputJob = readOutput(process)
            
            _serverStatus.update { ServerStatus(ServerState.STARTING, pid = null) }
            Log.i(TAG, "Server started with command: ${cmd.joinToString(" ")}")
            Log.i(TAG, "LD_LIBRARY_PATH: ${env["LD_LIBRARY_PATH"]}")
            Log.i(TAG, "ADSP_LIBRARY_PATH: ${env["ADSP_LIBRARY_PATH"]}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            _serverStatus.update { ServerStatus(ServerState.ERROR, errorMessage = e.message) }
        }
    }
    
    private fun readOutput(process: Process): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                val output = StringBuilder()
                while (reader.readLine().also { line = it } != null) {
                    line?.let {
                        output.append(it).append("\n")
                        _realtimeOutput.value += it + "\n"
                        Log.d(TAG, "Server: $it")
                        if (it.contains("HTTP server is listening") || it.contains("llama server listening")) {
                            _serverStatus.update { ServerStatus(ServerState.RUNNING) }
                        }
                    }
                }
                _serverOutput.value = output.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error reading server output", e)
            }
        }
    }
    
    fun stopServer() {
        serverProcess?.destroy()
        serverJob?.cancel()
        outputJob?.cancel()
        serverProcess = null
        serverJob = null
        outputJob = null
        _serverStatus.update { ServerStatus(ServerState.IDLE) }
        Log.i(TAG, "Server stopped")
    }
    
    fun isServerRunning(): Boolean {
        return _serverStatus.value.state == ServerState.RUNNING || 
               _serverStatus.value.state == ServerState.STARTING
    }
    
    private fun cleanup() {
        serverProcess?.destroy()
        serverJob?.cancel()
        outputJob?.cancel()
        serverProcess = null
        serverJob = null
        outputJob = null
    }
    
    private fun buildCommand(config: ServerConfig, binaryPath: String): List<String> {
        val cmd = mutableListOf<String>()
        
        cmd.add(binaryPath)
        cmd.add("--model")
        
        val modelPath = if (config.modelPath.startsWith("content://")) {
            val uri = Uri.parse(config.modelPath)
            UriUtils.getRealPath(context, uri) ?: config.modelPath
        } else {
            config.modelPath
        }
        Log.i(TAG, "Model path: $modelPath (original: ${config.modelPath})")
        cmd.add(modelPath)
        
        if (config.isEmbedding) {
            cmd.add("--embedding")
            cmd.add("--pooling")
            cmd.add(config.poolingType.name.lowercase())
        }
        
        cmd.add("--poll")
        cmd.add("1000")
        
        cmd.add("--ctx-size")
        cmd.add(config.getEffectiveContextSize().toString())
        cmd.add("--batch-size")
        cmd.add(config.getEffectiveBatchSize().toString())
        
        if (config.threads > 0) {
            cmd.add("-t")
            cmd.add(config.threads.toString())
        } else if (config.threads == -1) {
            cmd.add("-t")
            cmd.add("6")
            cmd.add("--cpu-mask")
            cmd.add("0xfc")
            cmd.add("--cpu-strict")
            cmd.add("1")
        }
        
        val effectivePort = config.getEffectivePort()
        if (effectivePort != ServerConfig.DEFAULT_PORT) {
            cmd.add("--port")
            cmd.add(effectivePort.toString())
        }
        
        val isHexagonDevice = config.deviceType in listOf(
            DeviceType.HTP0, DeviceType.HTP1, DeviceType.HTP2,
            DeviceType.HTP3, DeviceType.HTP4
        )
        val isAccelerated = config.deviceType != DeviceType.CPU
        
        when (config.deviceType) {
            DeviceType.OPENCL -> {
                cmd.add("--device")
                cmd.add("GPUOpenCL")
            }
            DeviceType.HTP0 -> {
                cmd.add("--device")
                cmd.add("htp0")
            }
            DeviceType.HTP1 -> {
                cmd.add("--device")
                cmd.add("htp1")
            }
            DeviceType.HTP2 -> {
                cmd.add("--device")
                cmd.add("htp2")
            }
            DeviceType.HTP3 -> {
                cmd.add("--device")
                cmd.add("htp3")
            }
            DeviceType.HTP4 -> {
                cmd.add("--device")
                cmd.add("htp4")
            }
            DeviceType.CPU -> { }
        }
        
        if (isAccelerated) {
            if (isHexagonDevice && config.flashAttention == FlashAttentionMode.AUTO) {
                cmd.add("--flash-attn")
                cmd.add("on")
            } else {
                when (config.flashAttention) {
                    FlashAttentionMode.ON -> {
                        cmd.add("--flash-attn")
                        cmd.add("on")
                    }
                    FlashAttentionMode.OFF -> {
                        cmd.add("--flash-attn")
                        cmd.add("off")
                    }
                    FlashAttentionMode.AUTO -> { }
                }
            }
            
            cmd.add("-ngl")
            cmd.add("99")
        } else {
            when (config.flashAttention) {
                FlashAttentionMode.ON -> {
                    cmd.add("--flash-attn")
                    cmd.add("on")
                }
                FlashAttentionMode.OFF -> {
                    cmd.add("--flash-attn")
                    cmd.add("off")
                }
                FlashAttentionMode.AUTO -> { }
            }
        }
        
        cmd.add("--cache-type-k")
        cmd.add(config.cacheTypeK.name.lowercase())
        cmd.add("--cache-type-v")
        cmd.add(config.cacheTypeV.name.lowercase())
        
        if (config.kvOffload) {
            cmd.add("--kv-offload")
        }
        
        if (config.contBatching) {
            cmd.add("--cont-batching")
        }
        
        if (config.autoFit) {
            cmd.add("--fit")
            cmd.add("on")
            cmd.add("--fit-target")
            cmd.add(config.getEffectiveAutoFitTarget().toString())
            cmd.add("--fit-ctx")
            cmd.add(config.getEffectiveAutoFitCtx().toString())
        }
        
        config.apiKey?.let {
            cmd.add("--api-key")
            cmd.add(it)
        }
        
        val effectiveTimeout = config.getEffectiveTimeout()
        if (effectiveTimeout != ServerConfig.DEFAULT_TIMEOUT) {
            cmd.add("--timeout")
            cmd.add(effectiveTimeout.toString())
        }
        
        if (config.bindAll) {
            cmd.add("--host")
            cmd.add("0.0.0.0")
        }
        
        if (config.predictTokens > 0) {
            cmd.add("--predict")
            cmd.add(config.predictTokens.toString())
        }
        
        config.extraParams?.takeIf { it.isNotBlank() }?.let {
            it.split("\\s+".toRegex()).filter { arg -> arg.isNotEmpty() }.forEach { arg ->
                cmd.add(arg)
            }
        }
        
        return cmd
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Llama Server",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.enableVibration(false)
            channel.description = "Llama Server notification channel"
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun Process.toJob(): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                val exitCode = this@toJob.waitFor()
                Log.d(TAG, "Server process exited with code: $exitCode")
                if (exitCode != 0) {
                    _serverStatus.update { ServerStatus(ServerState.ERROR, errorMessage = "Process exited with code $exitCode") }
                } else {
                    _serverStatus.update { ServerStatus(ServerState.STOPPED) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error waiting for process", e)
                _serverStatus.update { ServerStatus(ServerState.ERROR, errorMessage = e.message) }
            }
        }
    }
}
