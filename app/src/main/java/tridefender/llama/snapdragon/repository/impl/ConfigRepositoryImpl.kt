package tridefender.llama.snapdragon.repository.impl

import android.content.Context
import android.util.Log
import tridefender.llama.snapdragon.model.ServerConfig
import tridefender.llama.snapdragon.repository.ConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ConfigRepository {
    
    companion object {
        private const val TAG = "ConfigRepositoryImpl"
        private const val CONFIG_FILE = "server_config.json"
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val _config = MutableStateFlow(ServerConfig())
    override val config: StateFlow<ServerConfig> = _config.asStateFlow()
    
    private fun getConfigFile(): File {
        return File(context.filesDir, CONFIG_FILE)
    }
    
    override suspend fun saveConfig(config: ServerConfig) {
        _config.value = config
        try {
            val jsonString = json.encodeToString(config)
            getConfigFile().writeText(jsonString)
            Log.d(TAG, "Config saved: $jsonString")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config", e)
        }
    }
    
    override suspend fun loadConfig(): ServerConfig {
        return try {
            val file = getConfigFile()
            if (file.exists()) {
                val jsonString = file.readText()
                val config = json.decodeFromString<ServerConfig>(jsonString)
                _config.value = config
                Log.d(TAG, "Config loaded: $jsonString")
                config
            } else {
                Log.d(TAG, "No config file, using defaults")
                ServerConfig()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config", e)
            ServerConfig()
        }
    }
}
