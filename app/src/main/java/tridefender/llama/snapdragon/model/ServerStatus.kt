package tridefender.llama.snapdragon.model

data class ServerStatus(
    val state: ServerState,
    val pid: Int? = null,
    val startTime: Long? = null,
    val errorMessage: String? = null
)

enum class ServerState { IDLE, STARTING, RUNNING, STOPPING, STOPPED, ERROR }