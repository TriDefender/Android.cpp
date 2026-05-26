package tridefender.llama.snapdragon.model

import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    val name: String,
    val config: ServerConfig,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)