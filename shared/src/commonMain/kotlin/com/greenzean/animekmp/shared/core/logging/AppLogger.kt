package com.greenzean.animekmp.shared.core.logging

interface AppLogger {
    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )

    fun isLoggable(level: LogLevel): Boolean

    fun trace(
        tag: String,
        message: String,
    ) = log(LogLevel.Trace, tag, message)

    fun debug(
        tag: String,
        message: String,
    ) = log(LogLevel.Debug, tag, message)

    fun info(
        tag: String,
        message: String,
    ) = log(LogLevel.Info, tag, message)

    fun warn(
        tag: String,
        message: String,
    ) = log(LogLevel.Warn, tag, message)

    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) = log(LogLevel.Error, tag, message, throwable)
}

inline fun AppLogger.trace(
    tag: String,
    message: () -> String,
) {
    if (isLoggable(LogLevel.Trace)) trace(tag, message())
}

object NoOpAppLogger : AppLogger {
    override fun isLoggable(level: LogLevel) = false

    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = Unit
}
