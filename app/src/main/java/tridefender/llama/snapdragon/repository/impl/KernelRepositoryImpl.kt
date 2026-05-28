package tridefender.llama.snapdragon.repository.impl

import android.content.Context
import android.net.Uri
import android.util.Log
import tridefender.llama.snapdragon.model.DownloadState
import tridefender.llama.snapdragon.model.GitHubRelease
import tridefender.llama.snapdragon.model.KernelConfig
import tridefender.llama.snapdragon.model.KernelSource
import tridefender.llama.snapdragon.model.KernelVersion
import tridefender.llama.snapdragon.repository.ImportResult
import tridefender.llama.snapdragon.repository.KernelRepository
import tridefender.llama.snapdragon.util.BinaryExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KernelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : KernelRepository {

    companion object {
        private const val TAG = "KernelRepositoryImpl"
        private const val KERNEL_CONFIG_FILE = "kernel_config.json"
        private const val KERNELS_DIR = "kernels"
        private const val BUFFER_SIZE = 8192

        val EXPECTED_LIBRARIES = listOf(
            "libllama.so",
            "libllama-common.so",
            "libllama-server-impl.so",
            "libmtmd.so",
            "libggml.so",
            "libggml-base.so",
            "libggml-cpu.so",
            "libggml-opencl.so",
            "libggml-hexagon.so",
            "libggml-htp-v68.so",
            "libggml-htp-v69.so",
            "libggml-htp-v73.so",
            "libggml-htp-v75.so",
            "libggml-htp-v79.so",
            "libggml-htp-v81.so"
        )

        val EXPECTED_EXECUTABLES = listOf("libllama-server.so")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _kernelConfig = MutableStateFlow(KernelConfig())
    override val kernelConfig: StateFlow<KernelConfig> = _kernelConfig.asStateFlow()

    private val _downloadState = MutableStateFlow(DownloadState.IDLE)
    override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    override val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private fun getKernelsDir(): File = File(context.filesDir, KERNELS_DIR)

    private fun getConfigFile(): File = File(context.filesDir, KERNEL_CONFIG_FILE)

    private fun getVersionDir(name: String): File = File(getKernelsDir(), name)

    private fun findVersion(config: KernelConfig, name: String): KernelVersion? {
        return config.versions.find { it.name == name }
    }

    private fun loadConfig(): KernelConfig {
        return try {
            val file = getConfigFile()
            if (file.exists()) {
                val config = json.decodeFromString<KernelConfig>(file.readText())
                _kernelConfig.value = config
                config
            } else {
                KernelConfig()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load kernel config", e)
            KernelConfig()
        }
    }

    private fun saveConfig(config: KernelConfig) {
        _kernelConfig.value = config
        try {
            getConfigFile().writeText(json.encodeToString(config))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save kernel config", e)
        }
    }

    override suspend fun ensureBundledExtracted() = withContext(Dispatchers.IO) {
        val config = loadConfig()
        val bundledDir = getVersionDir(KernelConfig.BUNDLED_NAME)

        if (bundledDir.exists() && hasValidContent(bundledDir)) {
            if (config.versions.none { it.name == KernelConfig.BUNDLED_NAME }) {
                val updated = config.copy(
                    versions = config.versions + KernelVersion(
                        name = KernelConfig.BUNDLED_NAME,
                        isBundled = true,
                        source = KernelSource.BUNDLED
                    )
                )
                saveConfig(updated)
            }
            return@withContext
        }

        Log.i(TAG, "Extracting bundled kernels from APK")
        extractBundledFromApk(bundledDir)

        val missing = validateKernelDir(bundledDir)
        val version = KernelVersion(
            name = KernelConfig.BUNDLED_NAME,
            isBundled = true,
            source = KernelSource.BUNDLED,
            missingLibraries = missing
        )

        val updated = if (config.versions.any { it.name == KernelConfig.BUNDLED_NAME }) {
            config.copy(versions = config.versions.map {
                if (it.name == KernelConfig.BUNDLED_NAME) version else it
            })
        } else {
            config.copy(versions = config.versions + version)
        }
        saveConfig(updated)
    }

    override suspend fun prepareActiveVersion(): Boolean = withContext(Dispatchers.IO) {
        ensureBundledExtracted()

        var config = loadConfig()
        var activeVersion = findVersion(config, config.activeVersion)
        var activeDir = getVersionDir(config.activeVersion)

        if (activeVersion == null || !hasValidContent(activeDir)) {
            Log.w(TAG, "Active kernel '${config.activeVersion}' is not usable, reverting if possible")
            checkAndRevertIfNeeded()
            config = _kernelConfig.value
            activeVersion = findVersion(config, config.activeVersion)
            activeDir = getVersionDir(config.activeVersion)
        }

        if (activeVersion == null || !hasValidContent(activeDir)) {
            Log.e(TAG, "No usable active kernel version is available")
            return@withContext false
        }

        BinaryExtractor.clearActiveBinLib(context)
        BinaryExtractor.activateFromVersion(
            context = context,
            versionDir = activeDir,
            useBundledExecutable = activeVersion.isBundled
        )
        true
    }

    private fun extractBundledFromApk(destDir: File) {
        destDir.mkdirs()
        val binDir = File(destDir, "bin")
        val libDir = File(destDir, "lib")
        binDir.mkdirs()
        libDir.mkdirs()

        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        for (exe in BinaryExtractor.EXECUTABLES) {
            val sourceName = "lib$exe.so"
            val source = File(nativeLibDir, sourceName)
            if (source.exists()) {
                val dest = File(binDir, sourceName)
                source.copyTo(dest, overwrite = true)
                dest.setExecutable(true, false)
                Log.i(TAG, "Extracted bundled executable: $sourceName")
            } else {
                Log.w(TAG, "Bundled executable not found: $sourceName")
            }
        }

        for (lib in BinaryExtractor.LIBRARIES) {
            val source = File(nativeLibDir, lib)
            if (source.exists()) {
                val dest = File(libDir, lib)
                source.copyTo(dest, overwrite = true)
            } else {
                Log.w(TAG, "Bundled library not found: $lib")
            }
        }
    }

    private fun hasValidContent(dir: File): Boolean {
        val binDir = File(dir, "bin")
        val libDir = File(dir, "lib")
        if (!binDir.exists() || !libDir.exists()) return false
        val hasExe = EXPECTED_EXECUTABLES.any { File(binDir, it).exists() }
        val hasLibs = libDir.listFiles()?.any { it.name.endsWith(".so") } == true
        return hasExe && hasLibs
    }

    fun validateKernelDir(dir: File): List<String> {
        val missing = mutableListOf<String>()
        val libDir = File(dir, "lib")
        val binDir = File(dir, "bin")

        for (lib in EXPECTED_LIBRARIES) {
            if (!File(libDir, lib).exists()) {
                missing.add(lib)
            }
        }
        for (exe in EXPECTED_EXECUTABLES) {
            if (!File(binDir, exe).exists()) {
                missing.add(exe)
            }
        }
        return missing
    }

    override fun getActiveVersion(): String = _kernelConfig.value.activeVersion

    override fun getInstalledVersions(): List<KernelVersion> = _kernelConfig.value.versions

    override suspend fun switchVersion(name: String): Boolean = withContext(Dispatchers.IO) {
        val config = _kernelConfig.value
        val version = findVersion(config, name) ?: return@withContext false
        val versionDir = getVersionDir(name)

        if (!hasValidContent(versionDir)) {
            Log.e(TAG, "Version dir invalid: $name")
            return@withContext false
        }

        BinaryExtractor.clearActiveBinLib(context)
        BinaryExtractor.activateFromVersion(
            context = context,
            versionDir = versionDir,
            useBundledExecutable = version.isBundled
        )

        saveConfig(config.copy(activeVersion = name))
        Log.i(TAG, "Switched to kernel version: $name")
        true
    }

    override suspend fun importLocalArchive(uri: Uri, name: String): ImportResult = withContext(Dispatchers.IO) {
        val versionDir = getVersionDir(name)
        if (versionDir.exists()) {
            return@withContext ImportResult.Error("Version '$name' already exists")
        }

        try {
            versionDir.mkdirs()
            val binDir = File(versionDir, "bin")
            val libDir = File(versionDir, "lib")
            binDir.mkdirs()
            libDir.mkdirs()

            val mimeType = context.contentResolver.getType(uri)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Error("Cannot open file")

            inputStream.use { stream ->
                when {
                    mimeType == "application/zip" || mimeType == "application/x-zip-compressed" ->
                        extractZip(stream, versionDir)
                    mimeType == "application/gzip" || mimeType == "application/x-gzip" ->
                        extractTarGzip(stream, versionDir)
                    else -> {
                        val name_lower = uri.lastPathSegment?.lowercase() ?: ""
                        when {
                            name_lower.endsWith(".zip") -> extractZip(stream, versionDir)
                            name_lower.endsWith(".tar.gz") || name_lower.endsWith(".tgz") ->
                                extractTarGzip(stream, versionDir)
                            else -> {
                                versionDir.deleteRecursively()
                                return@withContext ImportResult.Error("Unsupported archive format")
                            }
                        }
                    }
                }
            }

            renameExecutable(versionDir)

            val missing = validateKernelDir(versionDir)
            val binExe = File(File(versionDir, "bin"), "libllama-server.so")
            if (!binExe.exists()) {
                versionDir.deleteRecursively()
                return@withContext ImportResult.Error("Invalid package: libllama-server.so not found")
            }

            val version = KernelVersion(
                name = name,
                isBundled = false,
                source = KernelSource.LOCAL_IMPORT,
                missingLibraries = missing
            )

            val config = _kernelConfig.value
            saveConfig(config.copy(versions = config.versions + version))

            ImportResult.Success(missing)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            versionDir.deleteRecursively()
            ImportResult.Error("Import failed: ${e.message}")
        }
    }

    private fun renameExecutable(versionDir: File) {
        val binDir = File(versionDir, "bin")
        val llamaServer = File(binDir, "llama-server")
        val renamed = File(binDir, "libllama-server.so")

        if (llamaServer.exists() && !renamed.exists()) {
            llamaServer.renameTo(renamed)
            renamed.setExecutable(true, false)
            Log.i(TAG, "Renamed llama-server -> libllama-server.so")
        }

        val nestedBin = File(versionDir, "bin/bin")
        if (nestedBin.exists()) {
            val nestedExe = File(nestedBin, "llama-server")
            if (nestedExe.exists() && !renamed.exists()) {
                nestedExe.renameTo(renamed)
                renamed.setExecutable(true, false)
                nestedBin.deleteRecursively()
            }
        }

        val topLevelExe = File(versionDir, "llama-server")
        if (topLevelExe.exists() && !renamed.exists()) {
            topLevelExe.renameTo(renamed)
            renamed.setExecutable(true, false)
        }

        if (renamed.exists()) {
            renamed.setExecutable(true, false)
        }
    }

    private fun extractZip(inputStream: InputStream, destDir: File) {
        ZipInputStream(BufferedInputStream(inputStream)).use { zipStream ->
            var entry = zipStream.nextEntry
            val buffer = ByteArray(BUFFER_SIZE)

            while (entry != null) {
                if (entry.isDirectory) {
                    entry = zipStream.nextEntry
                    continue
                }

                val name = entry.name
                val soName = name.substringAfterLast("/")

                when {
                    soName == "llama-server" || soName == "libllama-server.so" || name.contains("/bin/llama-server") -> {
                        val outFile = File(destDir, "bin/libllama-server.so")
                        copyStream(zipStream, outFile, buffer)
                    }
                    soName.endsWith(".so") && (name.contains("/lib/") || !name.contains("/")) -> {
                        val outFile = File(destDir, "lib/$soName")
                        copyStream(zipStream, outFile, buffer)
                    }
                }

                entry = zipStream.nextEntry
            }
        }
    }

    private fun extractTarGzip(inputStream: InputStream, destDir: File) {
        GZIPInputStream(BufferedInputStream(inputStream)).use { gzipStream ->
            val buffer = ByteArray(BUFFER_SIZE)
            val header = ByteArray(512)

            while (true) {
                val bytesRead = readFully(gzipStream, header)
                if (bytesRead < 512) break

                val isZeroBlock = header.all { it == 0.toByte() }
                if (isZeroBlock) break

                val name = extractTarName(header)
                val size = extractTarSize(header)
                val typeFlag = header[156].toInt().toChar()

                if (typeFlag == '5' || name.isEmpty()) {
                    skipTarData(gzipStream, size)
                    continue
                }

                val soName = name.substringAfterLast("/")

                when {
                    soName == "llama-server" || soName == "libllama-server.so" || name.contains("/bin/llama-server") -> {
                        val outFile = File(destDir, "bin/libllama-server.so")
                        copyTarData(gzipStream, outFile, size, buffer)
                    }
                    soName.endsWith(".so") && (name.contains("/lib/") || !name.contains("/")) -> {
                        val outFile = File(destDir, "lib/$soName")
                        copyTarData(gzipStream, outFile, size, buffer)
                    }
                    else -> skipTarData(gzipStream, size)
                }
            }
        }
    }

    private fun extractTarName(header: ByteArray): String {
        val nameBytes = header.copyOfRange(0, 100)
        val nullIdx = nameBytes.indexOfFirst { it == 0.toByte() }
        return if (nullIdx >= 0) String(nameBytes, 0, nullIdx) else String(nameBytes)
    }

    private fun extractTarSize(header: ByteArray): Long {
        val sizeBytes = header.copyOfRange(124, 136)
        val sizeStr = String(sizeBytes).trim().trimEnd(0.toChar())
        return try {
            sizeStr.toLong(8)
        } catch (e: Exception) {
            0L
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) return offset
            offset += read
        }
        return offset
    }

    private fun skipTarData(input: InputStream, size: Long) {
        val paddedSize = (size + 511) and 511L.inv()
        var remaining = paddedSize
        val skipBuffer = ByteArray(BUFFER_SIZE)
        while (remaining > 0) {
            val toRead = minOf(remaining, BUFFER_SIZE.toLong()).toInt()
            val read = input.read(skipBuffer, 0, toRead)
            if (read < 0) break
            remaining -= read
        }
    }

    private fun copyTarData(input: InputStream, outFile: File, size: Long, buffer: ByteArray) {
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            var remaining = size
            while (remaining > 0) {
                val toRead = minOf(remaining, BUFFER_SIZE.toLong()).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read < 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
        val paddedSize = (size + 511) and 511L.inv()
        val skipRemaining = paddedSize - size
        if (skipRemaining > 0) {
            val skipBuffer = ByteArray(skipRemaining.toInt())
            readFully(input, skipBuffer)
        }
    }

    private fun copyStream(input: InputStream, outFile: File, buffer: ByteArray) {
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
            }
        }
    }

    override suspend fun deleteVersion(name: String): Boolean = withContext(Dispatchers.IO) {
        val config = _kernelConfig.value
        val version = config.versions.find { it.name == name } ?: return@withContext false

        if (version.isBundled) return@withContext false
        if (config.activeVersion == name) return@withContext false

        val versionDir = getVersionDir(name)
        if (versionDir.exists()) {
            versionDir.deleteRecursively()
        }

        saveConfig(config.copy(versions = config.versions.filter { it.name != name }))
        Log.i(TAG, "Deleted kernel version: $name")
        true
    }

    override fun canDelete(name: String): Boolean {
        val config = _kernelConfig.value
        val version = config.versions.find { it.name == name } ?: return false
        return !version.isBundled && config.activeVersion != name
    }

    override suspend fun fetchGitHubReleases(repoOwner: String, repoName: String): List<GitHubRelease> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/$repoOwner/$repoName/releases")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                if (conn.responseCode != 200) {
                    Log.e(TAG, "GitHub API returned ${conn.responseCode}")
                    return@withContext emptyList()
                }

                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val releases = JSONArray(body)
                val result = mutableListOf<GitHubRelease>()

                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    val tagName = release.getString("tag_name")
                    val releaseName = release.optString("name", tagName)
                    val assets = release.getJSONArray("assets")

                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val assetName = asset.getString("name")
                        if (assetName.endsWith(".tar.gz") || assetName.endsWith(".zip")) {
                            result.add(
                                GitHubRelease(
                                    tagName = tagName,
                                    name = releaseName,
                                    assetName = assetName,
                                    assetUrl = asset.getString("browser_download_url"),
                                    assetSize = asset.getLong("size")
                                )
                            )
                        }
                    }
                }

                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch GitHub releases", e)
                emptyList()
            }
        }

    override suspend fun downloadFromGitHub(release: GitHubRelease): Flow<DownloadState> = flow {
        emit(DownloadState.DOWNLOADING)
        _downloadState.value = DownloadState.DOWNLOADING
        _downloadProgress.value = 0f

        try {
            val versionDir = getVersionDir(release.tagName)
            if (versionDir.exists()) versionDir.deleteRecursively()
            versionDir.mkdirs()

            val binDir = File(versionDir, "bin")
            val libDir = File(versionDir, "lib")
            binDir.mkdirs()
            libDir.mkdirs()

            val tempFile = File(context.cacheDir, "kernel_download_${release.tagName}")
            val url = URL(release.assetUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 60000

            val totalSize = conn.contentLength.toLong().coerceAtLeast(release.assetSize)
            var downloaded = 0L

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalSize > 0) {
                            _downloadProgress.value = downloaded.toFloat() / totalSize
                        }
                    }
                }
            }
            conn.disconnect()

            emit(DownloadState.EXTRACTING)
            _downloadState.value = DownloadState.EXTRACTING

            FileInputStream(tempFile).use { fis ->
                when {
                    release.assetName.endsWith(".tar.gz") || release.assetName.endsWith(".tgz") ->
                        extractTarGzip(fis, versionDir)
                    release.assetName.endsWith(".zip") ->
                        extractZip(fis, versionDir)
                }
            }
            tempFile.delete()

            renameExecutable(versionDir)

            emit(DownloadState.VALIDATING)
            _downloadState.value = DownloadState.VALIDATING

            val missing = validateKernelDir(versionDir)
            val binExe = File(File(versionDir, "bin"), "libllama-server.so")
            if (!binExe.exists()) {
                versionDir.deleteRecursively()
                emit(DownloadState.ERROR)
                _downloadState.value = DownloadState.ERROR
                return@flow
            }

            val version = KernelVersion(
                name = release.tagName,
                isBundled = false,
                source = KernelSource.GITHUB_RELEASE,
                missingLibraries = missing
            )

            val config = _kernelConfig.value
            saveConfig(config.copy(versions = config.versions + version))

            emit(DownloadState.DONE)
            _downloadState.value = DownloadState.DONE
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            getVersionDir(release.tagName).deleteRecursively()
            emit(DownloadState.ERROR)
            _downloadState.value = DownloadState.ERROR
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun checkAndRevertIfNeeded(): String? = withContext(Dispatchers.IO) {
        val config = loadConfig()
        val activeDir = getVersionDir(config.activeVersion)

        if (hasValidContent(activeDir)) return@withContext null

        Log.w(TAG, "Active version '${config.activeVersion}' missing, reverting to bundled")
        val bundledDir = getVersionDir(KernelConfig.BUNDLED_NAME)

        if (!hasValidContent(bundledDir)) {
            extractBundledFromApk(bundledDir)
        }

        if (hasValidContent(bundledDir)) {
            BinaryExtractor.clearActiveBinLib(context)
            BinaryExtractor.activateFromVersion(
                context = context,
                versionDir = bundledDir,
                useBundledExecutable = true
            )
            saveConfig(config.copy(activeVersion = KernelConfig.BUNDLED_NAME))
            return@withContext config.activeVersion
        }

        Log.e(TAG, "Even bundled kernels are missing!")
        null
    }
}
