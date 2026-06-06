package tridefender.llama.snapdragon.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import tridefender.llama.snapdragon.model.CacheType
import tridefender.llama.snapdragon.model.DeviceType
import tridefender.llama.snapdragon.model.FlashAttentionMode
import tridefender.llama.snapdragon.model.ModelMemoryEstimate
import tridefender.llama.snapdragon.model.PoolingType
import tridefender.llama.snapdragon.model.ServerConfig
import tridefender.llama.snapdragon.repository.ConfigRepository
import tridefender.llama.snapdragon.util.GgufMetadataReader
import tridefender.llama.snapdragon.util.ModelMemoryCalculator
import tridefender.llama.snapdragon.util.UriUtils
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelCatalogEntry(
    val path: String,
    val name: String,
    val estimate: ModelMemoryEstimate? = null
)

data class ModelAnalysisState(
    val freeBytes: Long = 0L,
    val catalog: List<ModelCatalogEntry> = emptyList(),
    val selectedEstimate: ModelMemoryEstimate? = null
)

@HiltViewModel
class ModelConfigViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    private val _analysis = MutableStateFlow(ModelAnalysisState())
    val analysis: StateFlow<ModelAnalysisState> = _analysis.asStateFlow()
    
    init {
        viewModelScope.launch {
            _config.value = configRepository.loadConfig()
            refreshModelCatalog()
        }
    }
    
    private fun saveConfig() {
        viewModelScope.launch {
            configRepository.saveConfig(_config.value)
        }
    }
    
    fun updateModelPath(path: String) {
        _config.update { it.copy(modelPath = path) }
        saveConfig()
        analyzeSelectedModel(path)
    }
    
    fun updateContextSize(size: Int?) {
        _config.update { it.copy(contextSize = size ?: -1) }
        saveConfig()
        analyzeSelectedModel(_config.value.modelPath)
    }
    
    fun updateBatchSize(size: Int?) {
        _config.update { it.copy(batchSize = size ?: -1) }
        saveConfig()
    }
    
    fun updatePredictTokens(tokens: Int?) {
        _config.update { it.copy(predictTokens = tokens ?: -1) }
        saveConfig()
    }
    
    fun updateIsEmbedding(enabled: Boolean) {
        _config.update { it.copy(isEmbedding = enabled) }
        saveConfig()
    }
    
    fun updatePoolingType(poolingType: PoolingType) {
        _config.update { it.copy(poolingType = poolingType) }
        saveConfig()
    }
    
    fun updateDeviceType(deviceType: DeviceType) {
        _config.update { it.copy(deviceType = deviceType) }
        saveConfig()
    }
    
    fun updateGpuLayers(layers: Int?) {
        _config.update { it.copy(gpuLayers = layers ?: -1) }
        saveConfig()
    }
    
    fun updateCacheTypeK(type: CacheType) {
        _config.update { it.copy(cacheTypeK = type) }
        saveConfig()
        analyzeSelectedModel(_config.value.modelPath)
    }
    
    fun updateCacheTypeV(type: CacheType) {
        _config.update { it.copy(cacheTypeV = type) }
        saveConfig()
        analyzeSelectedModel(_config.value.modelPath)
    }
    
    fun updateKvOffload(enabled: Boolean) {
        _config.update { it.copy(kvOffload = enabled) }
        saveConfig()
    }
    
    fun updateFlashAttention(mode: FlashAttentionMode) {
        _config.update { it.copy(flashAttention = mode) }
        saveConfig()
    }
    
    fun updateAutoFit(enabled: Boolean) {
        _config.update { it.copy(autoFit = enabled) }
        saveConfig()
    }
    
    fun updateAutoFitTargetMiB(target: Int?) {
        _config.update { it.copy(autoFitTargetMiB = target ?: -1) }
        saveConfig()
    }
    
    fun updateAutoFitCtx(ctx: Int?) {
        _config.update { it.copy(autoFitCtx = ctx ?: -1) }
        saveConfig()
    }
    
    fun updatePort(port: Int?) {
        _config.update { it.copy(port = port ?: -1) }
        saveConfig()
    }
    
    fun updateBindAll(bindAll: Boolean) {
        _config.update { it.copy(bindAll = bindAll) }
        saveConfig()
    }
    
    fun updateApiKey(apiKey: String?) {
        _config.update { it.copy(apiKey = apiKey) }
        saveConfig()
    }
    
    fun updateTimeout(timeout: Int?) {
        _config.update { it.copy(timeout = timeout ?: -1) }
        saveConfig()
    }
    
    fun updateExtraParams(params: String?) {
        _config.update { it.copy(extraParams = params?.ifBlank { null }) }
        saveConfig()
    }

    fun refreshModelCatalog() {
        viewModelScope.launch(Dispatchers.IO) {
            val freeBytes = readFreeBytes()
            val modelsDir = File(context.filesDir, "models")
            val files = modelsDir.takeIf { it.exists() }?.listFiles { file ->
                file.isFile && file.name.endsWith(".gguf", ignoreCase = true)
            }?.sortedByDescending { it.lastModified() }.orEmpty()

            val entries = files.map { file ->
                val estimate = estimateFor(file, freeBytes)
                ModelCatalogEntry(
                    path = file.absolutePath,
                    name = file.name,
                    estimate = estimate
                )
            }

            _analysis.update { state ->
                state.copy(
                    freeBytes = freeBytes,
                    catalog = entries,
                    selectedEstimate = entries.firstOrNull { it.path == _config.value.modelPath }?.estimate
                )
            }
        }
    }

    private fun analyzeSelectedModel(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val freeBytes = readFreeBytes()
            val file = resolveLocalFile(path)
            val estimate = file?.let { estimateFor(it, freeBytes) }
            val catalog = loadCatalog(freeBytes)
            _analysis.update { state ->
                state.copy(
                    freeBytes = freeBytes,
                    catalog = catalog,
                    selectedEstimate = estimate
                )
            }
        }
    }

    private fun loadCatalog(freeBytes: Long): List<ModelCatalogEntry> {
        val modelsDir = File(context.filesDir, "models")
        return modelsDir.takeIf { it.exists() }?.listFiles { file ->
            file.isFile && file.name.endsWith(".gguf", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() }?.map { file ->
            ModelCatalogEntry(
                path = file.absolutePath,
                name = file.name,
                estimate = estimateFor(file, freeBytes)
            )
        }.orEmpty()
    }

    private fun resolveLocalFile(path: String): File? {
        if (path.isBlank()) return null
        if (path.startsWith("content://")) {
            val resolved = UriUtils.getRealPath(context, Uri.parse(path))
            return resolved?.let { File(it) }?.takeIf { it.exists() && it.isFile }
        }
        val file = File(path)
        return if (file.exists() && file.isFile) file else null
    }

    private fun estimateFor(file: File, freeBytes: Long): ModelMemoryEstimate? {
        val metadata = GgufMetadataReader.read(file)
        return ModelMemoryCalculator.estimate(
            fileSizeBytes = file.length(),
            metadata = metadata,
            contextSize = _config.value.getEffectiveContextSize(),
            cacheTypeK = _config.value.cacheTypeK,
            cacheTypeV = _config.value.cacheTypeV,
            freeBytes = freeBytes
        )
    }

    private fun readFreeBytes(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem
    }
}
