package com.greenzean.animekmp.server.graphql

import com.expediagroup.graphql.server.ktor.KtorGraphQLRequestParser
import com.expediagroup.graphql.server.types.GraphQLBatchRequest
import com.expediagroup.graphql.server.types.GraphQLServerRequest
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.request.ApplicationRequest
import tools.jackson.databind.ObjectMapper

class SingleRequestParser(
    mapper: ObjectMapper,
) : KtorGraphQLRequestParser(mapper) {
    override suspend fun parseRequest(request: ApplicationRequest): GraphQLServerRequest? {
        val parsed =
            try {
                super.parseRequest(request)
            } catch (e: IllegalStateException) {
                throw (e.cause as? PayloadTooLargeException) ?: e
            }
        if (parsed is GraphQLBatchRequest) throw BadRequestException("Batched requests are not supported")
        return parsed
    }
}
