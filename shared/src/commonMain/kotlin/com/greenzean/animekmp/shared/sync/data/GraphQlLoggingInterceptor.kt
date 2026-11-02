package com.greenzean.animekmp.shared.sync.data

import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import com.greenzean.animekmp.shared.core.logging.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlin.time.TimeSource

class GraphQlLoggingInterceptor(
    private val logger: AppLogger,
) : ApolloInterceptor {
    override fun <D : Operation.Data> intercept(
        request: ApolloRequest<D>,
        chain: ApolloInterceptorChain,
    ): Flow<ApolloResponse<D>> =
        flow {
            val start = TimeSource.Monotonic.markNow()
            val operation = request.operation
            val label = "${if (operation is Mutation<*>) "mutation" else "query"} ${operation.name()}"
            emitAll(
                chain.proceed(request).onEach { response ->
                    val millis = start.elapsedNow().inWholeMilliseconds
                    logger.traceGraphQlCall(label, request, response, millis)
                    logResponse(label, response, millis)
                },
            )
        }

    private fun logResponse(
        label: String,
        response: ApolloResponse<*>,
        millis: Long,
    ) {
        val exception = response.exception
        val errors = response.errors
        when {
            exception != null -> {
                logger.warn(TAG, "$label failed: ${exception::class.simpleName} ($millis ms)")
            }

            !errors.isNullOrEmpty() -> {
                val codes = errors.joinToString(",") { it.extensions?.get("code")?.toString() ?: "UNKNOWN" }
                logger.warn(TAG, "$label -> graphql errors: $codes ($millis ms)")
            }

            else -> {
                logger.debug(TAG, "$label -> ok ($millis ms)")
            }
        }
    }

    private companion object {
        const val TAG = "GraphQL"
    }
}
