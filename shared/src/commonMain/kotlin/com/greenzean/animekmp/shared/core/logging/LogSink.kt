package com.greenzean.animekmp.shared.core.logging

interface LogSink {
    val minLevel: LogLevel

    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    )
}
