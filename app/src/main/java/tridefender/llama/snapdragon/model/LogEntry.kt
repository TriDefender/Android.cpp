package tridefender.llama.snapdragon.model

data class LogEntry(
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LogLevel {
    INFO,
    WARNING,
    ERROR
}
