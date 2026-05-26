package tridefender.llama.snapdragon.repository.impl

import tridefender.llama.snapdragon.model.Preset
import tridefender.llama.snapdragon.repository.PresetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetRepositoryImpl @Inject constructor() : PresetRepository {
    
    private val presets = mutableMapOf<String, Preset>()
    private val jsonParser = Json { ignoreUnknownKeys = true; prettyPrint = true }
    
    override suspend fun savePreset(preset: Preset) {
        presets[preset.name] = preset
    }
    
    override suspend fun loadPreset(name: String): Preset? {
        return presets[name]
    }
    
    override suspend fun deletePreset(name: String) {
        presets.remove(name)
    }
    
    override suspend fun listPresets(): List<Preset> {
        return presets.values.toList()
    }
    
    override suspend fun exportPreset(preset: Preset): String {
        return jsonParser.encodeToString(preset)
    }
    
    override suspend fun importPreset(json: String): Preset {
        return jsonParser.decodeFromString<Preset>(json)
    }
}
