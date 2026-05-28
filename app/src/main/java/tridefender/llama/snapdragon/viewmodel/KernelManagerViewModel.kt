package tridefender.llama.snapdragon.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tridefender.llama.snapdragon.model.DownloadState
import tridefender.llama.snapdragon.model.GitHubRelease
import tridefender.llama.snapdragon.model.KernelConfig
import tridefender.llama.snapdragon.model.KernelVersion
import tridefender.llama.snapdragon.repository.ImportResult
import tridefender.llama.snapdragon.repository.KernelRepository
import tridefender.llama.snapdragon.service.ServerProcessManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KernelManagerViewModel @Inject constructor(
    private val kernelRepository: KernelRepository,
    private val serverProcessManager: ServerProcessManager
) : ViewModel() {

    companion object {
        private const val TAG = "KernelManagerViewModel"
    }

    val kernelConfig: StateFlow<KernelConfig> = kernelRepository.kernelConfig
    val downloadState: StateFlow<DownloadState> = kernelRepository.downloadState
    val downloadProgress: StateFlow<Float> = kernelRepository.downloadProgress

    private val _revertMessage = MutableStateFlow<String?>(null)
    val revertMessage: StateFlow<String?> = _revertMessage.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val _switchError = MutableStateFlow<String?>(null)
    val switchError: StateFlow<String?> = _switchError.asStateFlow()

    private val _releases = MutableStateFlow<List<GitHubRelease>>(emptyList())
    val releases: StateFlow<List<GitHubRelease>> = _releases.asStateFlow()

    private val _isLoadingReleases = MutableStateFlow(false)
    val isLoadingReleases: StateFlow<Boolean> = _isLoadingReleases.asStateFlow()

    init {
        viewModelScope.launch {
            kernelRepository.ensureBundledExtracted()
            val reverted = kernelRepository.checkAndRevertIfNeeded()
            if (reverted != null) {
                _revertMessage.value = "Previously active kernel '$reverted' was missing. Reverted to Optimized Defaults."
            }
        }
    }

    fun isServerRunning(): Boolean = serverProcessManager.isServerRunning()

    fun switchVersion(name: String) {
        if (serverProcessManager.isServerRunning()) {
            _switchError.value = "Stop the server before switching kernel versions"
            return
        }

        viewModelScope.launch {
            val success = kernelRepository.switchVersion(name)
            if (!success) {
                _switchError.value = "Failed to switch to '$name'"
            }
        }
    }

    fun deleteVersion(name: String) {
        if (serverProcessManager.isServerRunning()) {
            _switchError.value = "Stop the server before deleting kernel versions"
            return
        }

        viewModelScope.launch {
            kernelRepository.deleteVersion(name)
        }
    }

    fun importLocalArchive(uri: Uri, name: String) {
        viewModelScope.launch {
            _importResult.value = null
            val result = kernelRepository.importLocalArchive(uri, name)
            _importResult.value = result
        }
    }

    fun fetchReleases(repoOwner: String, repoName: String) {
        viewModelScope.launch {
            _isLoadingReleases.value = true
            val result = kernelRepository.fetchGitHubReleases(repoOwner, repoName)
            _releases.value = result
            _isLoadingReleases.value = false
        }
    }

    fun downloadFromGitHub(release: GitHubRelease) {
        viewModelScope.launch {
            kernelRepository.downloadFromGitHub(release).collect { }
        }
    }

    fun canDelete(name: String): Boolean = kernelRepository.canDelete(name)

    fun clearRevertMessage() {
        _revertMessage.value = null
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    fun clearSwitchError() {
        _switchError.value = null
    }
}
