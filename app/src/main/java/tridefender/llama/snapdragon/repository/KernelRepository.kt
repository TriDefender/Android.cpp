package tridefender.llama.snapdragon.repository

import android.net.Uri
import tridefender.llama.snapdragon.model.DownloadState
import tridefender.llama.snapdragon.model.GitHubRelease
import tridefender.llama.snapdragon.model.KernelConfig
import tridefender.llama.snapdragon.model.KernelVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface KernelRepository {
    val kernelConfig: StateFlow<KernelConfig>
    val downloadState: StateFlow<DownloadState>
    val downloadProgress: StateFlow<Float>

    suspend fun ensureBundledExtracted()
    suspend fun prepareActiveVersion(): Boolean
    fun getActiveVersion(): String
    fun getInstalledVersions(): List<KernelVersion>
    suspend fun switchVersion(name: String): Boolean
    suspend fun importLocalArchive(uri: Uri, name: String): ImportResult
    suspend fun deleteVersion(name: String): Boolean
    fun canDelete(name: String): Boolean
    suspend fun fetchGitHubReleases(repoOwner: String, repoName: String): List<GitHubRelease>
    suspend fun downloadFromGitHub(release: GitHubRelease): Flow<DownloadState>
    suspend fun checkAndRevertIfNeeded(): String?
}

sealed class ImportResult {
    data class Success(val missingLibraries: List<String>) : ImportResult()
    data class Error(val message: String) : ImportResult()
}
