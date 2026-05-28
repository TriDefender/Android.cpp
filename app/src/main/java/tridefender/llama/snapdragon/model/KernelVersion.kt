package tridefender.llama.snapdragon.model

import kotlinx.serialization.Serializable

@Serializable
enum class KernelSource {
    BUNDLED,
    GITHUB_RELEASE,
    LOCAL_IMPORT
}

@Serializable
data class KernelVersion(
    val name: String,
    val isBundled: Boolean = false,
    val installedAt: Long = System.currentTimeMillis(),
    val source: KernelSource = KernelSource.BUNDLED,
    val missingLibraries: List<String> = emptyList()
)

@Serializable
data class KernelConfig(
    val activeVersion: String = BUNDLED_NAME,
    val versions: List<KernelVersion> = emptyList()
) {
    companion object {
        const val BUNDLED_NAME = "Optimized Defaults"
    }
}

enum class DownloadState {
    IDLE,
    DOWNLOADING,
    EXTRACTING,
    VALIDATING,
    DONE,
    ERROR
}

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val assetName: String,
    val assetUrl: String,
    val assetSize: Long
)
