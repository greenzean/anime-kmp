package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.core.logging.AppLogger
import kotlin.time.TimeMark

internal const val MAL_HTTP_TAG = "MalHttp"

internal fun AppLogger.logHttpResponse(
    method: String,
    path: String,
    status: Int,
    started: TimeMark,
) = debug(MAL_HTTP_TAG, "$method $path -> $status (${started.elapsedNow().inWholeMilliseconds} ms)")

internal fun AppLogger.logHttpFailure(
    method: String,
    path: String,
    error: Enum<*>,
    cause: Throwable,
) = warn(MAL_HTTP_TAG, "$method $path failed: ${error.name} (${cause::class.simpleName})")
