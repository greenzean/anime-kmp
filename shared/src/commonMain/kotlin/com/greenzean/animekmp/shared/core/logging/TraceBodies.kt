package com.greenzean.animekmp.shared.core.logging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val BODY_INDENT = "  "
private const val REQUEST_BODY_INDENT = "    "
private const val HEADER_PAD = "            "

private val prettyJson =
    Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

private val usefulHeaderNames = setOf("content-type", "content-length", "retry-after", "location", "www-authenticate")
private const val RATE_LIMIT_PREFIX = "x-ratelimit"

internal fun isUsefulResponseHeader(name: String): Boolean {
    val lower = name.lowercase()
    return lower in usefulHeaderNames || lower.startsWith(RATE_LIMIT_PREFIX)
}

internal fun prettyBodyText(
    body: String,
    max: Int,
): String =
    runCatching { LogRedactor.truncate(LogRedactor.redactText(layoutBody(body)), max) }
        .getOrElse { LogRedactor.truncate(body, max) }

private fun layoutBody(body: String): String {
    val trimmed = body.trim()
    return jsonLayout(trimmed) ?: formLayout(trimmed) ?: body
}

private fun jsonLayout(text: String): String? {
    val element =
        text
            .takeIf { it.startsWith("{") || it.startsWith("[") }
            ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
            ?.takeIf { it is JsonObject || it is JsonArray }
    return element?.let { prettyJson.encodeToString(JsonElement.serializer(), it) }
}

private fun formLayout(text: String): String? {
    val pairs = text.split('&')
    val isForm =
        text.isNotEmpty() &&
            text.none { it.isWhitespace() } &&
            pairs.all { it.contains('=') && !it.startsWith("=") }
    return if (isForm) pairs.joinToString("\n") else null
}

private fun indentBlock(
    text: String,
    indent: String,
): String = text.lines().joinToString("\n") { indent + it }

internal fun formatApiCall(call: ApiCall): String =
    buildString {
        append("API #${call.id}  ${call.source}  ${call.title}  ->  ${call.outcome}  (${call.durationMs} ms)")
        call.url?.let { append("\n  url:      $it") }
        val requestLines =
            call.requestHeaders.map { (name, value) -> "$name: ${LogRedactor.redactHeader(name, value)}" }
        requestLines.forEachIndexed { index, line ->
            append(if (index == 0) "\n  request:  $line" else "\n$HEADER_PAD$line")
        }
        call.requestBody?.takeIf { it.isNotBlank() }?.let {
            append("\n  request body:\n${indentBlock(TraceFormatter.prettyBody(it), REQUEST_BODY_INDENT)}")
        }
        call.responseHeaders?.let { headers ->
            val useful =
                TraceFormatter.usefulResponseHeaders(headers).joinToString(", ") { (name, value) ->
                    "$name: ${LogRedactor.redactHeader(name, value)}"
                }
            append("\n  response:${if (useful.isEmpty()) "" else " $useful"}")
            call.responseBody?.takeIf { it.isNotBlank() }?.let {
                append("\n${indentBlock(TraceFormatter.prettyBody(it), BODY_INDENT)}")
            }
        }
    }
