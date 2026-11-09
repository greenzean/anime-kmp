package com.greenzean.animekmp.server.graphql

import graphql.GraphqlErrorBuilder
import graphql.GraphqlErrorException
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.SimpleDataFetcherExceptionHandler
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

private val log = LoggerFactory.getLogger(SafeExceptionHandler::class.java)

object SafeExceptionHandler : DataFetcherExceptionHandler {
    private val default = SimpleDataFetcherExceptionHandler()

    override fun handleException(
        handlerParameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val cause = rootOf(handlerParameters.exception)
        if (cause is GraphqlErrorException) {
            return default.handleException(handlerParameters)
        }
        log.error("GraphQL resolver failed at {}", handlerParameters.path, cause)
        val error =
            GraphqlErrorBuilder
                .newError()
                .message("Internal server error")
                .path(handlerParameters.path)
                .location(handlerParameters.sourceLocation)
                .extensions(mapOf<String, Any>("code" to "INTERNAL_ERROR"))
                .build()
        return CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult().error(error).build())
    }

    private fun rootOf(throwable: Throwable): Throwable =
        if ((throwable is CompletionException || throwable is InvocationTargetException) && throwable.cause != null) {
            rootOf(checkNotNull(throwable.cause))
        } else {
            throwable
        }
}
