package tridefender.llama.snapdragon.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val modelPath: String = "",
    val contextSize: Int = -1,
    val batchSize: Int = -1,
    val ubatchSize: Int = 512,
    val predictTokens: Int = -1,
    val isEmbedding: Boolean = false,
    val poolingType: PoolingType = PoolingType.CLS,
    val deviceType: DeviceType = DeviceType.CPU,
    val gpuLayers: Int = -1,
    val cacheTypeK: CacheType = CacheType.F16,
    val cacheTypeV: CacheType = CacheType.F16,
    val kvOffload: Boolean = true,
    val flashAttention: FlashAttentionMode = FlashAttentionMode.AUTO,
    val autoFit: Boolean = false,
    val autoFitTargetMiB: Int = -1,
    val autoFitCtx: Int = -1,
    val port: Int = -1,
    val host: String = "127.0.0.1",
    val bindAll: Boolean = false,
    val apiKey: String? = null,
    val timeout: Int = -1,
    val threads: Int = -1,
    val parallel: Int = -1,
    val contBatching: Boolean = true,
    val extraParams: String? = null
) {
    companion object {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_CONTEXT_SIZE = 16384
        const val DEFAULT_BATCH_SIZE = 2048
        const val DEFAULT_GPU_LAYERS = 99
        const val DEFAULT_AUTO_FIT_TARGET = 1024
        const val DEFAULT_AUTO_FIT_CTX = 4096
        const val DEFAULT_TIMEOUT = 600
    }
    
    fun getEffectivePort(): Int = if (port == -1) DEFAULT_PORT else port
    fun getEffectiveContextSize(): Int = if (contextSize == -1) DEFAULT_CONTEXT_SIZE else contextSize
    fun getEffectiveBatchSize(): Int = if (batchSize == -1) DEFAULT_BATCH_SIZE else batchSize
    fun getEffectiveGpuLayers(): Int = if (gpuLayers == -1) DEFAULT_GPU_LAYERS else gpuLayers
    fun getEffectiveAutoFitTarget(): Int = if (autoFitTargetMiB == -1) DEFAULT_AUTO_FIT_TARGET else autoFitTargetMiB
    fun getEffectiveAutoFitCtx(): Int = if (autoFitCtx == -1) DEFAULT_AUTO_FIT_CTX else autoFitCtx
    fun getEffectiveTimeout(): Int = if (timeout == -1) DEFAULT_TIMEOUT else timeout
}