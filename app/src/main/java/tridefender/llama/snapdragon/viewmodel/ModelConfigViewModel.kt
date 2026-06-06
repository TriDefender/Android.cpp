package tridefender.llama.snapdragon.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import tridefender.llama.snapdragon.model.CacheType
import tridefender.llama.snapdragon.model.DeviceType
import tridefender.llama.snapdragon.model.FlashAttentionMode
import tridefender.llama.snapdragon.model.ModelMemoryEstimate
import tridefender.llama.snapdragon.model.PoolingType
import tridefender.llama.snapdragon.model.ServerConfig
import tridefender.llama.snapdragon.repository.ConfigRepository
import tridefender.llama.snapdragon.util.GgufMetadataReader
import tridefender.llama.snapdragon.util.ModelMemoryCalculator
import tridefender.llama.snapdragon.util.UriUtils
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class ModelCatalogEntry(
    val path: String,
    val name: String,
    val estimate: ModelMemoryEstimate? = null
)

data class ModelAnalysisState(
    val freeBytes: Long = 0L,
    val catalog: List<ModelCatalogEntry> = emptyList(),
    val selectedEstimate: ModelMemoryEstimate? = null
)

data class HuggingFaceModelEntry(
    val modelId: String,
    val downloads: Int? = null,
    val likes: Int? = null
)

data class HuggingFaceFileEntry(
    val repoId: String,
    val fileName: String,
    val sizeBytes: Long? = null
)

data class HuggingFacePickerState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<HuggingFaceModelEntry> = emptyList(),
    val selectedRepoId: String? = null,
    val isLoadingFiles: Boolean = false,
    val files: List<HuggingFaceFileEntry> = emptyList(),
    val downloadFileName: String? = null,
    val downloadProgress: Float? = null,
    val message: String? = null
)

@Serializable
private data class HfModelDto(
    @SerialName("modelId") val modelId: String? = null,
    val id: String? = null,
    val downloads: Int? = null,
    val likes: Int? = null
)

@Serializable
private data class HfModelInfoDto(
    val siblings: List<HfSiblingDto> = emptyList()
)

@Serializable
private data class HfSiblingDto(
    val rfilename: String,
    val size: Long? = null
)

@HiltViewModel
class ModelConfigViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    private val _analysis = MutableStateFlow(ModelAnalysisState())
    val analysis: StateFlow<ModelAnalysisState> = _analysis.asStateFlow()

    private val _hfPicker = MutableStateFlow(HuggingFacePickerState())
    val hfPicker: StateFlow<HuggingFacePickerState> = _hfPicker.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    
    init {
        viewModelScope.launch {
            _config.value = configRepository.loadConfig()
            refreshModelCatalog()
        }
    }
    
    private fun saveConfig() {
        viewModelScope.launch {
            configRepository.saveConfig(_config.value)
        }
    }
    
    fun updateModelPath(path: String) {
        _config.update { it.copy(modelPath = path) }
        saveConfig()
        analyzeSelectedModel(path)
    }

    fun importModelUri(uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolvedPath = resolveLocalFile(uriString)?.absolutePath ?: uriString
            _config.update { it.copy(modelPath = resolvedPath) }
            configRepository.saveConfig(_config.value)
            refreshAnalysis(resolvedPath)
        }
    }
    
    fun updateContextSize(size: Int?) {
        _config.update { it.copy(contextSize = size ?: -1) }
        saveConfig()
        analyzeSelectedModel(_config.value.modelPath)
    }
    
    fun updateBatchSize(size: Int?) {
        _config.update { it.copy(batchSize = size ?: -1) }
        saveConfig()
    }
    
    fun updatePredictTokens(tokens: Int?) {
        _config.update { it.copy(predictTokens = tokens ?: -1) }
        saveConfig()
    }
    
    fun updateIsEmbedding(enabled: Boolean) {
        _config.update { it.copy(isEmbedding = enabled) }
        saveConfig()
    }
    
    fun updatePoolingType(poolingType: PoolingType) {
        _config.update { it.copy(poolingType = poolingType) }
        saveConfig()
    }
    
    fun updateDeviceType(deviceType: DeviceType) {
        _config.update { it.copy(deviceType = deviceType) }
        saveConfig()
    }
    
    fun updateGpuLayers(layers: Int?) {
        _config.update { it.copy(gpuLayers = layers ?: -1) }
        saveConfig()
    }
    
    fun updateCacheTypeK(type: CacheType) {
        _config.update { it.copy(cacheTypeK = type) }
        saveConfig()
        analyzeSelectedModel(_config.value.modelPath)
    }
    
    fun updateCacheTypeV(type: CacheType) {
        _config.update { it.copy(cacheTypeV = type) }
        saveConfig()
        analyzeSelectedModel(_config.value.modelPath)
    }
    
    fun updateKvOffload(enabled: Boolean) {
        _config.update { it.copy(kvOffload = enabled) }
        saveConfig()
    }
    
    fun updateFlashAttention(mode: FlashAttentionMode) {
        _config.update { it.copy(flashAttention = mode) }
        saveConfig()
    }
    
    fun updateAutoFit(enabled: Boolean) {
        _config.update { it.copy(autoFit = enabled) }
        saveConfig()
    }
    
    fun updateAutoFitTargetMiB(target: Int?) {
        _config.update { it.copy(autoFitTargetMiB = target ?: -1) }
        saveConfig()
    }
    
    fun updateAutoFitCtx(ctx: Int?) {
        _config.update { it.copy(autoFitCtx = ctx ?: -1) }
        saveConfig()
    }
    
    fun updatePort(port: Int?) {
        _config.update { it.copy(port = port ?: -1) }
        saveConfig()
    }
    
    fun updateBindAll(bindAll: Boolean) {
        _config.update { it.copy(bindAll = bindAll) }
        saveConfig()
    }
    
    fun updateApiKey(apiKey: String?) {
        _config.update { it.copy(apiKey = apiKey) }
        saveConfig()
    }
    
    fun updateTimeout(timeout: Int?) {
        _config.update { it.copy(timeout = timeout ?: -1) }
        saveConfig()
    }
    
    fun updateExtraParams(params: String?) {
        _config.update { it.copy(extraParams = params?.ifBlank { null }) }
        saveConfig()
    }

    fun refreshModelCatalog() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshAnalysis(_config.value.modelPath)
        }
    }

    fun updateHfQuery(query: String) {
        _hfPicker.update { it.copy(query = query, message = null) }
    }

    fun searchHuggingFaceModels() {
        val query = _hfPicker.value.query.trim()
        if (query.isBlank()) {
            _hfPicker.update { it.copy(message = "Enter a model search term or repo name.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _hfPicker.update {
                it.copy(
                    isSearching = true,
                    searchResults = emptyList(),
                    selectedRepoId = null,
                    files = emptyList(),
                    message = null
                )
            }
            runCatching {
                val url = "https://huggingface.co/api/models?search=${query.urlEncode()}&filter=gguf&limit=25"
                val results = readText(url).let { body ->
                    json.decodeFromString<List<HfModelDto>>(body)
                }.mapNotNull { dto ->
                    val modelId = dto.modelId ?: dto.id
                    modelId?.let { HuggingFaceModelEntry(it, dto.downloads, dto.likes) }
                }

                _hfPicker.update {
                    it.copy(
                        isSearching = false,
                        searchResults = results,
                        message = if (results.isEmpty()) "No GGUF repositories found." else null
                    )
                }
            }.onFailure { error ->
                _hfPicker.update {
                    it.copy(isSearching = false, message = error.userMessage("Search failed"))
                }
            }
        }
    }

    fun loadHuggingFaceFiles(repoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _hfPicker.update {
                it.copy(
                    selectedRepoId = repoId,
                    isLoadingFiles = true,
                    files = emptyList(),
                    message = null
                )
            }
            runCatching {
                val info = readText("https://huggingface.co/api/models/${repoId.encodeRepoPath()}")
                    .let { json.decodeFromString<HfModelInfoDto>(it) }
                val files = info.siblings
                    .filter { it.rfilename.endsWith(".gguf", ignoreCase = true) }
                    .sortedBy { it.rfilename }
                    .map { HuggingFaceFileEntry(repoId, it.rfilename, it.size) }
                _hfPicker.update {
                    it.copy(
                        isLoadingFiles = false,
                        files = files,
                        message = if (files.isEmpty()) "No GGUF files found in $repoId." else null
                    )
                }
            }.onFailure { error ->
                _hfPicker.update {
                    it.copy(isLoadingFiles = false, message = error.userMessage("Could not load files"))
                }
            }
        }
    }

    fun downloadHuggingFaceFile(file: HuggingFaceFileEntry) {
        if (_hfPicker.value.downloadFileName != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val modelsDir = modelsDir().apply { mkdirs() }
            val safeName = uniqueFileName(file.fileName.substringAfterLast("/"))
            val destination = File(modelsDir, safeName)
            val partial = File(modelsDir, "$safeName.part")
            _hfPicker.update {
                it.copy(downloadFileName = file.fileName, downloadProgress = 0f, message = null)
            }
            runCatching {
                downloadToFile(file.downloadUrl(), partial, destination) { progress ->
                    _hfPicker.update { it.copy(downloadProgress = progress) }
                }
                _config.update { it.copy(modelPath = destination.absolutePath) }
                configRepository.saveConfig(_config.value)
                refreshAnalysis(destination.absolutePath)
                _hfPicker.update {
                    it.copy(
                        downloadFileName = null,
                        downloadProgress = null,
                        message = "Downloaded ${destination.name}."
                    )
                }
            }.onFailure { error ->
                partial.delete()
                _hfPicker.update {
                    it.copy(
                        downloadFileName = null,
                        downloadProgress = null,
                        message = error.userMessage("Download failed")
                    )
                }
            }
        }
    }

    fun clearHfMessage() {
        _hfPicker.update { it.copy(message = null) }
    }

    fun renameCachedModel(entry: ModelCatalogEntry, newName: String): String? {
        val cleaned = sanitizeModelName(newName)
        if (cleaned.isBlank()) return "Name cannot be empty."
        return runCatching {
            val source = File(entry.path)
            val target = File(modelsDir(), cleaned)
            when {
                !source.exists() -> "Model no longer exists."
                !isInsideModelsDir(source) -> "Only cached models can be renamed."
                target.exists() && target.absolutePath != source.absolutePath -> "A model with that name already exists."
                target.absolutePath == source.absolutePath -> null
                source.renameTo(target) -> {
                    if (_config.value.modelPath == source.absolutePath) {
                        _config.update { it.copy(modelPath = target.absolutePath) }
                        saveConfig()
                    }
                    refreshModelCatalog()
                    null
                }
                else -> "Rename failed."
            }
        }.getOrElse { it.userMessage("Rename failed") }
    }

    fun deleteCachedModel(entry: ModelCatalogEntry): String? {
        return runCatching {
            val file = File(entry.path)
            when {
                !file.exists() -> null
                !isInsideModelsDir(file) -> "Only cached models can be deleted."
                file.delete() -> {
                    if (_config.value.modelPath == file.absolutePath) {
                        _config.update { it.copy(modelPath = "") }
                        saveConfig()
                    }
                    refreshModelCatalog()
                    null
                }
                else -> "Delete failed."
            }
        }.getOrElse { it.userMessage("Delete failed") }
    }

    private fun analyzeSelectedModel(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            refreshAnalysis(path)
        }
    }

    private fun refreshAnalysis(selectedPath: String) {
        val freeBytes = readFreeBytes()
        val selectedFile = resolveLocalFile(selectedPath)
        val estimate = selectedFile?.let { estimateFor(it, freeBytes) }
        val catalog = loadCatalog(freeBytes).withSelectedFile(selectedFile, estimate)
        _analysis.update { state ->
            state.copy(
                freeBytes = freeBytes,
                catalog = catalog,
                selectedEstimate = estimate ?: catalog.firstOrNull { it.path == selectedPath }?.estimate
            )
        }
    }

    private fun loadCatalog(freeBytes: Long): List<ModelCatalogEntry> {
        return modelsDir().takeIf { it.exists() }?.listFiles { file ->
            file.isFile && file.name.endsWith(".gguf", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() }?.map { file ->
            ModelCatalogEntry(
                path = file.absolutePath,
                name = file.name,
                estimate = estimateFor(file, freeBytes)
            )
        }.orEmpty()
    }

    private fun List<ModelCatalogEntry>.withSelectedFile(
        selectedFile: File?,
        estimate: ModelMemoryEstimate?
    ): List<ModelCatalogEntry> {
        if (selectedFile == null || any { it.path == selectedFile.absolutePath }) return this
        return listOf(
            ModelCatalogEntry(
                path = selectedFile.absolutePath,
                name = selectedFile.name,
                estimate = estimate
            )
        ) + this
    }

    private fun resolveLocalFile(path: String): File? {
        if (path.isBlank()) return null
        if (path.startsWith("content://")) {
            val resolved = UriUtils.getRealPath(context, Uri.parse(path))
            return resolved?.let { File(it) }?.takeIf { it.exists() && it.isFile }
        }
        val file = File(path)
        return if (file.exists() && file.isFile) file else null
    }

    private fun estimateFor(file: File, freeBytes: Long): ModelMemoryEstimate? {
        val metadata = GgufMetadataReader.read(file)
        return ModelMemoryCalculator.estimate(
            fileSizeBytes = file.length(),
            metadata = metadata,
            contextSize = _config.value.getEffectiveContextSize(),
            cacheTypeK = _config.value.cacheTypeK,
            cacheTypeV = _config.value.cacheTypeV,
            freeBytes = freeBytes
        )
    }

    private fun readFreeBytes(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem
    }

    private fun modelsDir(): File = File(context.filesDir, "models")

    private fun isInsideModelsDir(file: File): Boolean {
        val root = modelsDir().canonicalFile
        val candidate = file.canonicalFile
        return candidate.parentFile == root
    }

    private fun sanitizeModelName(name: String): String {
        val cleaned = name.trim()
            .substringAfterLast("/")
            .substringAfterLast("\\")
            .replace(Regex("[\\r\\n\\t]"), "_")
        return if (cleaned.endsWith(".gguf", ignoreCase = true)) cleaned else "$cleaned.gguf"
    }

    private fun uniqueFileName(name: String): String {
        val cleaned = sanitizeModelName(name)
        val base = cleaned.removeSuffix(".gguf")
        var candidate = cleaned
        var index = 2
        while (File(modelsDir(), candidate).exists()) {
            candidate = "$base-$index.gguf"
            index += 1
        }
        return candidate
    }

    private fun readText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        return connection.useResponse { input ->
            input.bufferedReader().use { it.readText() }
        }
    }

    private fun downloadToFile(
        url: String,
        partial: File,
        destination: File,
        onProgress: (Float?) -> Unit
    ) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        connection.useResponse { input ->
            val total = connection.contentLengthLong
            var copied = 0L
            partial.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    onProgress(if (total > 0L) copied.toFloat() / total else null)
                }
            }
        }
        if (destination.exists()) destination.delete()
        check(partial.renameTo(destination)) { "Could not finalize download." }
    }

    private fun <T> HttpURLConnection.useResponse(block: (java.io.InputStream) -> T): T {
        return try {
            val code = responseCode
            val stream = if (code in 200..299) inputStream else errorStream ?: inputStream
            val body = stream.use(block)
            if (code !in 200..299) error("HTTP $code: ${body.toString().take(200)}")
            body
        } finally {
            disconnect()
        }
    }

    private fun HuggingFaceFileEntry.downloadUrl(): String {
        return "https://huggingface.co/${repoId.encodeRepoPath()}/resolve/main/${fileName.encodePathSegments()}?download=true"
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private fun String.encodeRepoPath(): String = split("/").joinToString("/") { it.urlEncode() }

    private fun String.encodePathSegments(): String = split("/").joinToString("/") { it.urlEncode() }

    private fun Throwable.userMessage(prefix: String): String {
        return message?.takeIf { it.isNotBlank() }?.let { "$prefix: $it" } ?: prefix
    }
}
