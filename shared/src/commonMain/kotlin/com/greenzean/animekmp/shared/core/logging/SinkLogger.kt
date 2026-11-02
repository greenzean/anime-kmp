package com.greenzean.animekmp.shared.core.logging

class SinkLogger(
    private val sinks: List<LogSink>,
) : AppLogger {
    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        sinks.forEach { sink ->
            if (level.ordinal >= sink.minLevel.ordinal) {
                try {
                    sink.log(level, tag, message, throwable)
                } catch (_: Exception) {
                    Unit
                }
            }
        }
    }
}
