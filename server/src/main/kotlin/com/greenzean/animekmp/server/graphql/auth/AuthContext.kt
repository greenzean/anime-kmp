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

/**
 * Puts the user id of a valid `Authorization: Bearer <access token>` into the GraphQL context (ADR 0010).
 * A missing or bad token leaves the context empty instead of failing, so the sign-in operations stay open;
 * operations that need a user call [requireUserId]. `tokens` is null when sign-in is not configured (dev only).
 */
class AuthContextFactory(
    private val tokens: SessionTokens?,
) : DefaultKtorGraphQLContextFactory() {
    override suspend fun generateContext(request: ApplicationRequest): GraphQLContext {
        val base = super.generateContext(request)
        val userId = bearerToken(request)?.let(::userIdOrNull) ?: return base
        return (base.stream().toList().associate { it.key to it.value } + (USER_ID_KEY to userId)).toGraphQLContext()
    }

    private fun bearerToken(request: ApplicationRequest): String? =
        request
            .header(HttpHeaders.Authorization)
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.trim()

    private fun userIdOrNull(token: String): UUID? =
        try {
            tokens?.verifyAccess(token)
        } catch (e: AuthFailure) {
            log.info("Auth rejected: {}", e.reason)
            null
        }
}

/** The signed-in user of this request, never taken from operation input. */
fun DataFetchingEnvironment.requireUserId(): UUID =
    graphQlContext.get<UUID>(USER_ID_KEY) ?: throw AuthFailure("No valid access token on a protected operation")
