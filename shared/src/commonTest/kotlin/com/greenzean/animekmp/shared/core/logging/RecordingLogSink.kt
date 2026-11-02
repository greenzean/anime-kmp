package com.greenzean.animekmp.shared.core.logging

import kotlin.test.assertTrue

data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable?,
)

class RecordingLogSink(
    override val minLevel: LogLevel = LogLevel.Debug,
) : LogSink {
    val entries = mutableListOf<LogEntry>()

    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        entries += LogEntry(level, tag, message, throwable)
    }

    fun messages(tag: String): List<String> = entries.filter { it.tag == tag }.map { it.message }

    fun assertNoneContains(vararg forbidden: String) {
        entries.forEach { entry ->
            val texts = listOfNotNull(entry.tag, entry.message, entry.throwable?.message, entry.throwable?.toString())
            forbidden.forEach { word ->
                assertTrue(texts.none { word in it }, "log entry leaks '$word': $entry")
            }
        }
    }
}

class ThrowingLogSink(
    override val minLevel: LogLevel = LogLevel.Debug,
) : LogSink {
    var calls = 0

    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        calls++
        error("sink failure")
    }
}

val COMMON_LEAKS = arrayOf("Bearer", "Authorization", "?", "q=")
