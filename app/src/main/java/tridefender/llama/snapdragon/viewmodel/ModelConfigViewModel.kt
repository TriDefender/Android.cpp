package tridefender.llama.snapdragon.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tridefender.llama.snapdragon.model.CacheType
import tridefender.llama.snapdragon.model.DeviceType
import tridefender.llama.snapdragon.model.FlashAttentionMode
import tridefender.llama.snapdragon.model.PoolingType
import tridefender.llama.snapdragon.model.ServerConfig
import tridefender.llama.snapdragon.repository.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelConfigViewModel @Inject constructor(
    private val configRepository: ConfigRepository
) : ViewModel() {
    
    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()
    
    init {
        viewModelScope.launch {
            _config.value = configRepository.loadConfig()
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
    }
    
    fun updateContextSize(size: Int?) {
        _config.update { it.copy(contextSize = size ?: -1) }
        saveConfig()
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
    }
    
    fun updateCacheTypeV(type: CacheType) {
        _config.update { it.copy(cacheTypeV = type) }
        saveConfig()
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
}
