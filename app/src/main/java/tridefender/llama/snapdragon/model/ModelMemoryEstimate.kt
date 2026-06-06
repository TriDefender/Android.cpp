package tridefender.llama.snapdragon.model

data class ModelMemoryEstimate(
    val modelBytes: Long,
    val kvCacheBytes: Long,
    val overheadBytes: Long,
    val totalBytes: Long,
    val freeBytes: Long,
    val remainingBytes: Long,
    val fitState: MemoryFitState,
    val architecture: String? = null,
    val contextLength: Long? = null,
    val blockCount: Long? = null,
    val embeddingLength: Long? = null,
    val headCount: Long? = null,
    val headCountKv: Long? = null,
    val keyLength: Long? = null,
    val valueLength: Long? = null,
    val fileType: Long? = null,
    val quantizationVersion: Long? = null
)

enum class MemoryFitState {
    WONT_FIT,
    TIGHT_FIT,
    GOOD_FIT
}
