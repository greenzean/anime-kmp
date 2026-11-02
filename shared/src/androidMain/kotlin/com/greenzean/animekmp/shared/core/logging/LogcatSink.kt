package com.greenzean.animekmp.shared.core.logging

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

private const val TAG_PREFIX = "Anime"
private const val MAX_TAG_LENGTH = 23

fun logcatMinLevel(debuggable: Boolean): LogLevel = if (debuggable) LogLevel.Debug else LogLevel.Warn

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
        when (level) {
            LogLevel.Debug -> Log.d(logTag, message)
            LogLevel.Info -> Log.i(logTag, message)
            LogLevel.Warn -> Log.w(logTag, message)
            LogLevel.Error -> if (throwable != null) Log.e(logTag, message, throwable) else Log.e(logTag, message)
        }
    }
}
