package tridefender.llama.snapdragon.repository

import tridefender.llama.snapdragon.model.Preset
import kotlinx.coroutines.flow.Flow

interface PresetRepository {
    suspend fun savePreset(preset: Preset)
    suspend fun loadPreset(name: String): Preset?
    suspend fun deletePreset(name: String)
    suspend fun listPresets(): List<Preset>
    suspend fun exportPreset(preset: Preset): String
    suspend fun importPreset(json: String): Preset
}