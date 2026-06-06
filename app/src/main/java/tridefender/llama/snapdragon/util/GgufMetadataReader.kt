package tridefender.llama.snapdragon.util

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufMetadata(
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

object GgufMetadataReader {
    private const val TAG = "GgufMetadataReader"
    private const val MAGIC = 0x46554747
    private const val MAX_STRING_BYTES = 1 shl 20

    fun read(file: File): GgufMetadata? {
        if (!file.isFile || !file.canRead()) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.readIntLe() != MAGIC) return null
                val version = raf.readIntLe()
                if (version !in 1..3) return null

                raf.readLongLe() // tensor count
                val kvCount = raf.readLongLe()

                var architecture: String? = null
                var contextLength: Long? = null
                var blockCount: Long? = null
                var embeddingLength: Long? = null
                var headCount: Long? = null
                var headCountKv: Long? = null
                var keyLength: Long? = null
                var valueLength: Long? = null
                var fileType: Long? = null
                var quantizationVersion: Long? = null

                repeat(kvCount.toBoundedInt()) {
                    val key = readString(raf) ?: return null
                    val type = raf.readIntLe()
                    when (key) {
                        "general.architecture" -> architecture = readStringValue(raf, type)
                        "general.quantization_version" -> quantizationVersion = readUIntValue(raf, type)
                        "general.file_type" -> fileType = readUIntValue(raf, type)
                        "llama.context_length",
                        "mpt.context_length",
                        "gptneox.context_length",
                        "gptj.context_length",
                        "gpt2.context_length",
                        "bloom.context_length",
                        "falcon.context_length",
                        "mamba.context_length",
                        "rwkv.context_length",
                        "whisper.encoder.context_length",
                        "whisper.decoder.context_length",
                        "llm.context_length" -> contextLength = readUIntValue(raf, type)
                        "llama.block_count",
                        "mpt.block_count",
                        "gptneox.block_count",
                        "gptj.block_count",
                        "gpt2.block_count",
                        "bloom.block_count",
                        "falcon.block_count",
                        "mamba.block_count",
                        "rwkv.block_count",
                        "whisper.encoder.block_count",
                        "whisper.decoder.block_count",
                        "llm.block_count" -> blockCount = readUIntValue(raf, type)
                        "llama.embedding_length",
                        "mpt.embedding_length",
                        "gptneox.embedding_length",
                        "gptj.embedding_length",
                        "gpt2.embedding_length",
                        "bloom.embedding_length",
                        "falcon.embedding_length",
                        "mamba.embedding_length",
                        "rwkv.embedding_length",
                        "whisper.encoder.embedding_length",
                        "whisper.decoder.embedding_length",
                        "llm.embedding_length" -> embeddingLength = readUIntValue(raf, type)
                        "llama.attention.head_count",
                        "mpt.attention.head_count",
                        "gptneox.attention.head_count",
                        "gptj.attention.head_count",
                        "gpt2.attention.head_count",
                        "bloom.attention.head_count",
                        "falcon.attention.head_count",
                        "whisper.encoder.attention.head_count",
                        "whisper.decoder.attention.head_count",
                        "llm.attention.head_count" -> headCount = readUIntValue(raf, type)
                        "llama.attention.head_count_kv",
                        "falcon.attention.head_count_kv",
                        "llm.attention.head_count_kv" -> headCountKv = readUIntValue(raf, type)
                        "llm.attention.key_length" -> keyLength = readUIntValue(raf, type)
                        "llm.attention.value_length" -> valueLength = readUIntValue(raf, type)
                        else -> skipValue(raf, type)
                    }
                }

                GgufMetadata(
                    architecture = architecture,
                    contextLength = contextLength,
                    blockCount = blockCount,
                    embeddingLength = embeddingLength,
                    headCount = headCount,
                    headCountKv = headCountKv,
                    keyLength = keyLength,
                    valueLength = valueLength,
                    fileType = fileType,
                    quantizationVersion = quantizationVersion
                )
            }
        }.getOrElse { error ->
            Log.w(TAG, "Failed to read GGUF metadata from ${file.absolutePath}", error)
            null
        }
    }

    private fun readUIntValue(raf: RandomAccessFile, type: Int): Long? {
        return when (type) {
            0 -> raf.readByteUnsigned().toLong()
            2 -> raf.readShortLe().toLong()
            4 -> raf.readIntLeUnsigned()
            10 -> raf.readLongLeUnsigned()
            else -> {
                skipValue(raf, type)
                null
            }
        }
    }

    private fun readStringValue(raf: RandomAccessFile, type: Int): String? {
        return when (type) {
            8 -> readString(raf)
            else -> {
                skipValue(raf, type)
                null
            }
        }
    }

    private fun skipValue(raf: RandomAccessFile, type: Int) {
        when (type) {
            0, 1, 7 -> raf.skipBytesCompat(1)
            2, 3 -> raf.skipBytesCompat(2)
            4, 5, 6 -> raf.skipBytesCompat(4)
            8 -> readString(raf)
            9 -> {
                val arrayType = raf.readIntLe()
                val length = raf.readLongLe().toBoundedInt()
                repeat(length) { skipValue(raf, arrayType) }
            }
            10, 11, 12 -> raf.skipBytesCompat(8)
        }
    }

    private fun readString(raf: RandomAccessFile): String? {
        val rawLength = raf.readLongLe()
        if (rawLength < 0 || rawLength > MAX_STRING_BYTES) return null
        val length = rawLength.toInt()
        val bytes = ByteArray(length)
        raf.readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun RandomAccessFile.skipBytesCompat(length: Int) {
        if (length <= 0) return
        seek(filePointer + length)
    }

    private fun RandomAccessFile.readByteUnsigned(): Int = readUnsignedByte()
    private fun RandomAccessFile.readShortLe(): Int = ByteBuffer.wrap(readBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
    private fun RandomAccessFile.readIntLe(): Int = ByteBuffer.wrap(readBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
    private fun RandomAccessFile.readIntLeUnsigned(): Long = readIntLe().toLong() and 0xffffffffL
    private fun RandomAccessFile.readLongLe(): Long = ByteBuffer.wrap(readBytes(8)).order(ByteOrder.LITTLE_ENDIAN).long
    private fun RandomAccessFile.readLongLeUnsigned(): Long = readLongLe()
    private fun RandomAccessFile.readBytes(count: Int): ByteArray {
        val bytes = ByteArray(count)
        readFully(bytes)
        return bytes
    }

    private fun Long.toBoundedInt(): Int = when {
        this <= 0L -> 0
        this >= Int.MAX_VALUE.toLong() -> Int.MAX_VALUE
        else -> this.toInt()
    }
}
