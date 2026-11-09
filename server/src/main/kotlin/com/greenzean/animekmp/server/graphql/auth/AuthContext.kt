package com.greenzean.animekmp.server.graphql.auth

import com.expediagroup.graphql.generator.extensions.toGraphQLContext
import com.expediagroup.graphql.server.ktor.DefaultKtorGraphQLContextFactory
import com.greenzean.animekmp.server.auth.AuthFailure
import com.greenzean.animekmp.server.auth.SessionTokens
import graphql.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import io.ktor.http.HttpHeaders
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import org.slf4j.LoggerFactory
import java.util.UUID

private const val USER_ID_KEY = "userId"
private const val BEARER_PREFIX = "Bearer "

private val log = LoggerFactory.getLogger(AuthContextFactory::class.java)

fun bearerToken(authorization: String?): String? =
    authorization
        ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
        ?.substring(BEARER_PREFIX.length)
        ?.trim()

fun SessionTokens.userIdOrNull(
    authorization: String?,
    onRejected: (String) -> Unit = {},
): UUID? =
    bearerToken(authorization)?.let {
        try {
            verifyAccess(it)
        } catch (e: AuthFailure) {
            onRejected(e.reason)
            null
        }
    }

private fun logRejected(reason: String) = log.info("Auth rejected: {}", reason)

class AuthContextFactory(
    private val tokens: SessionTokens?,
) : DefaultKtorGraphQLContextFactory() {
    override suspend fun generateContext(request: ApplicationRequest): GraphQLContext {
        val base = super.generateContext(request)
        val userId = tokens?.userIdOrNull(request.header(HttpHeaders.Authorization), ::logRejected) ?: return base
        return (base.stream().toList().associate { it.key to it.value } + (USER_ID_KEY to userId)).toGraphQLContext()
    }
}

fun DataFetchingEnvironment.requireUserId(): UUID =
    graphQlContext.get<UUID>(USER_ID_KEY) ?: throw AuthFailure("No valid access token on a protected operation")
