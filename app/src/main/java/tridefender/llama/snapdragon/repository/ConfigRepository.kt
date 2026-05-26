package tridefender.llama.snapdragon.repository

import tridefender.llama.snapdragon.model.ServerConfig
import kotlinx.coroutines.flow.StateFlow

interface ConfigRepository {
    val config: StateFlow<ServerConfig>
    suspend fun saveConfig(config: ServerConfig)
    suspend fun loadConfig(): ServerConfig
}