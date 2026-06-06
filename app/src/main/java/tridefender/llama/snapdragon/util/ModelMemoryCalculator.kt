package tridefender.llama.snapdragon.util

import tridefender.llama.snapdragon.model.CacheType
import tridefender.llama.snapdragon.model.MemoryFitState
import tridefender.llama.snapdragon.model.ModelMemoryEstimate
import kotlin.math.max

object ModelMemoryCalculator {
    private const val ONE_GIB = 1L shl 30
    private const val DEFAULT_OVERHEAD_BYTES = 256L shl 20

    fun estimate(
        fileSizeBytes: Long,
        metadata: GgufMetadata?,
        contextSize: Int,
        cacheTypeK: CacheType,
        cacheTypeV: CacheType,
        freeBytes: Long
    ): ModelMemoryEstimate {
        val architecture = metadata?.architecture?.lowercase()
        val contextLength = metadata?.contextLength
        val blockCount = metadata?.blockCount
        val embeddingLength = metadata?.embeddingLength
        val headCount = metadata?.headCount
        val headCountKv = metadata?.headCountKv?.takeIf { it > 0 } ?: headCount
        val keyLength = metadata?.keyLength ?: if (embeddingLength != null && headCount != null && headCount > 0) {
            max(1L, embeddingLength / headCount)
        } else {
            null
        }
        val valueLength = metadata?.valueLength ?: keyLength

        val layers = blockCount ?: 0L
        val kvTokens = maxOf(contextSize, 1)
        val kvCacheBytes = if (layers > 0 && headCountKv != null && keyLength != null && valueLength != null) {
            val perToken = layers * headCountKv * (keyLength * bytesPerElement(cacheTypeK) + valueLength * bytesPerElement(cacheTypeV))
            perToken * kvTokens
        } else {
            0L
        }

        val overheadBytes = DEFAULT_OVERHEAD_BYTES + if (architecture == "llama") 64L shl 20 else 0L
        val totalBytes = fileSizeBytes + kvCacheBytes + overheadBytes
        val remainingBytes = freeBytes - totalBytes
        val fitState = when {
            remainingBytes < 0 -> MemoryFitState.WONT_FIT
            remainingBytes < ONE_GIB -> MemoryFitState.TIGHT_FIT
            else -> MemoryFitState.GOOD_FIT
        }

        return ModelMemoryEstimate(
            modelBytes = fileSizeBytes,
            kvCacheBytes = kvCacheBytes,
            overheadBytes = overheadBytes,
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            remainingBytes = remainingBytes,
            fitState = fitState,
            architecture = metadata?.architecture,
            contextLength = contextLength,
            blockCount = blockCount,
            embeddingLength = embeddingLength,
            headCount = headCount,
            headCountKv = headCountKv,
            keyLength = keyLength,
            valueLength = valueLength,
            fileType = metadata?.fileType,
            quantizationVersion = metadata?.quantizationVersion
        )
    }

    private fun bytesPerElement(type: CacheType): Long = when (type) {
        CacheType.F32 -> 4L
        CacheType.F16 -> 2L
        CacheType.BF16 -> 2L
        CacheType.Q8_0 -> 1L
        CacheType.Q4_0 -> 1L
        CacheType.Q4_1 -> 1L
        CacheType.Q5_0 -> 1L
        CacheType.Q5_1 -> 1L
        CacheType.IQ4_NL -> 1L
    }
}
