package io.github.mr1ve3r.combined.engine

data class EngineLogEvent(
    val timestamp: Long,
    val level: LogLevel,
    val protocol: Protocol,
    val tag: String,
    val message: String,
)

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

enum class Protocol {
    L2TP,
    SSTP,
}
