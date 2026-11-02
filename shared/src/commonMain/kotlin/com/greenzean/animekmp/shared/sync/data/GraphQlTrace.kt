package com.greenzean.animekmp.shared.sync.data

import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.json.buildJsonString
import com.apollographql.apollo.api.variablesJson
import com.apollographql.apollo.network.http.HttpInfo
import com.greenzean.animekmp.shared.core.logging.API_TAG
import com.greenzean.animekmp.shared.core.logging.ApiCall
import com.greenzean.animekmp.shared.core.logging.AppLogger
import com.greenzean.animekmp.shared.core.logging.TraceFormatter
import com.greenzean.animekmp.shared.core.logging.TraceIds
import com.greenzean.animekmp.shared.core.logging.trace

private const val GRAPHQL_SOURCE = "GraphQL"

internal fun <D : Operation.Data> AppLogger.traceGraphQlCall(
    label: String,
    request: ApolloRequest<D>,
    response: ApolloResponse<D>,
    millis: Long,
) = trace(API_TAG) {
    val adapters = request.executionContext[CustomScalarAdapters] ?: CustomScalarAdapters.Empty
    val variables =
        runCatching { request.operation.variablesJson(adapters) }
            .getOrElse { "variables unavailable: ${it::class.simpleName}" }
    val info = response.executionContext[HttpInfo]
    val exception = response.exception
    val outcome =
        when {
            exception != null -> "FAILED ${exception::class.simpleName}"
            !response.errors.isNullOrEmpty() -> "GRAPHQL ERRORS"
            else -> "ok"
        }
    TraceFormatter.apiCall(
        ApiCall(
            id = TraceIds.next(),
            source = GRAPHQL_SOURCE,
            title = label,
            outcome = outcome,
            durationMs = millis,
            url = null,
            requestHeaders = emptyList(),
            requestBody = variables,
            responseHeaders = info?.headers?.map { it.name to it.value },
            responseBody = responseBody(request, response),
        ),
    )
}

private fun <D : Operation.Data> responseBody(
    request: ApolloRequest<D>,
    response: ApolloResponse<D>,
): String {
    val adapters = request.executionContext[CustomScalarAdapters] ?: CustomScalarAdapters.Empty
    val data = response.data
    val dataJson =
        if (data == null) {
            "null"
        } else {
            runCatching { buildJsonString { response.operation.adapter().toJson(this, adapters, data) } }
                .getOrElse { "\"unavailable: ${it::class.simpleName}\"" }
        }
    val errors = response.errors.orEmpty().joinToString(",") { "\"${it.message}\"" }
    val exception = response.exception?.let { ",\"exception\":\"${it::class.simpleName}: ${it.message}\"" }.orEmpty()
    return "{\"data\":$dataJson,\"errors\":[$errors]$exception}"
}
