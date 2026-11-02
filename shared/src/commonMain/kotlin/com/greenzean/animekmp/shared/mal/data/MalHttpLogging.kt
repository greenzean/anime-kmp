package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.core.logging.API_TAG
import com.greenzean.animekmp.shared.core.logging.ApiCall
import com.greenzean.animekmp.shared.core.logging.AppLogger
import com.greenzean.animekmp.shared.core.logging.LogLevel
import com.greenzean.animekmp.shared.core.logging.LogRedactor
import com.greenzean.animekmp.shared.core.logging.TraceFormatter
import com.greenzean.animekmp.shared.core.logging.TraceIds
import com.greenzean.animekmp.shared.core.logging.trace
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.io.IOException
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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

internal class MalCallTrace(
    private val logger: AppLogger,
    private val method: String,
    private val path: String,
) {
    private val started = TimeSource.Monotonic.markNow()
    private var url: String? = null
    private var requestHeaders: List<Pair<String, String>> = emptyList()
    private var requestBody: String? = null

    fun request(
        builder: HttpRequestBuilder,
        body: String? = null,
    ) {
        if (!logger.isLoggable(LogLevel.Trace)) return
        url = LogRedactor.redactUrl(builder.url.buildString())
        requestHeaders = builder.headers.entries().flatMap { (name, values) -> values.map { name to it } }
        requestBody = body
    }

    suspend fun complete(response: HttpResponse): String {
        val text =
            if (response.status.isSuccess()) {
                response.bodyAsText()
            } else {
                try {
                    response.bodyAsText()
                } catch (_: IOException) {
                    ""
                }
            }
        val status = response.status
        log(
            outcome = "${status.value} ${status.description}".trim(),
            responseHeaders = response.headers.entries().flatMap { (name, values) -> values.map { name to it } },
            responseBody = text,
        )
        return text
    }

    fun failed(cause: Throwable) = log("FAILED ${cause::class.simpleName}", null, null)

    private fun log(
        outcome: String,
        responseHeaders: List<Pair<String, String>>?,
        responseBody: String?,
    ) = logger.trace(API_TAG) {
        TraceFormatter.apiCall(
            ApiCall(
                id = TraceIds.next(),
                source = "MAL",
                title = "$method $path",
                outcome = outcome,
                durationMs = started.elapsedNow().inWholeMilliseconds,
                url = url,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                responseHeaders = responseHeaders,
                responseBody = responseBody,
            ),
        )
    }
}
