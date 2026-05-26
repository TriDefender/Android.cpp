package tridefender.llama.snapdragon.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tridefender.llama.snapdragon.data.SettingsDataStore
import tridefender.llama.snapdragon.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsDataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.Lazily, ThemeMode.SYSTEM)
    
    val language: StateFlow<String> = settingsDataStore.language
        .stateIn(viewModelScope, SharingStarted.Lazily, "en")
    
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }
    
    fun setLanguage(lang: String) {
        viewModelScope.launch {
            settingsDataStore.setLanguage(lang)
        }
    }
}