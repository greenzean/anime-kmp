package com.greenzean.animekmp.shared.core.logging

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

private const val TAG_PREFIX = "Anime"
private const val MAX_TAG_LENGTH = 23

fun chunkForLogcat(
    message: String,
    max: Int = 3500,
): List<String> {
    if (message.length <= max) return listOf(message)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < message.length) {
        val end = minOf(start + max, message.length)
        if (end == message.length) {
            chunks += message.substring(start)
            break
        }
        val newline = message.lastIndexOf('\n', end - 1)
        if (newline > start) {
            chunks += message.substring(start, newline)
            start = newline + 1
        } else {
            chunks += message.substring(start, end)
            start = end
        }
    }
    return chunks
}

fun logcatMinLevel(debuggable: Boolean): LogLevel = if (debuggable) LogLevel.Trace else LogLevel.Warn

class LogcatSink(
    context: Context,
) : LogSink {
    override val minLevel: LogLevel =
        logcatMinLevel(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)

    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        val logTag = (TAG_PREFIX + tag).take(MAX_TAG_LENGTH)
        val chunks = chunkForLogcat(message)
        chunks.forEachIndexed { index, chunk ->
            write(level, logTag, chunk, if (index == chunks.lastIndex) throwable else null)
        }
    }

    private fun write(
        level: LogLevel,
        logTag: String,
        message: String,
        throwable: Throwable?,
    ) {
        when (level) {
            LogLevel.Trace -> Log.v(logTag, message)
            LogLevel.Debug -> Log.d(logTag, message)
            LogLevel.Info -> Log.i(logTag, message)
            LogLevel.Warn -> Log.w(logTag, message)
            LogLevel.Error -> if (throwable != null) Log.e(logTag, message, throwable) else Log.e(logTag, message)
        }
    }
}
