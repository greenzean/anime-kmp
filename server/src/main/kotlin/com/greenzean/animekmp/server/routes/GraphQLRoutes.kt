package com.greenzean.animekmp.server.routes

import com.expediagroup.graphql.server.ktor.graphQLPostRoute
import com.expediagroup.graphql.server.ktor.graphiQLRoute
import com.greenzean.animekmp.server.Limits
import com.greenzean.animekmp.server.auth.SessionTokens
import com.greenzean.animekmp.server.graphql.auth.userIdOrNull
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.RateLimiter
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import java.net.InetAddress

private val perIp = RateLimitName("graphql-ip")
private val perUser = RateLimitName("graphql-user")

fun Application.installGraphQLRateLimits(
    limits: Limits,
    tokens: SessionTokens?,
) {
    install(RateLimit) {
        register(perIp) {
            rateLimiter(limit = limits.requestsPerIp, refillPeriod = limits.rateWindow)
            requestKey { call ->
                tokens?.userIdOrNull(call.request.header(HttpHeaders.Authorization))?.let { "user:$it" }
                    ?: rateLimitAddress(call.request.origin.remoteAddress)
            }
        }
        register(perUser) {
            rateLimiter { _, key ->
                val limit =
                    if (key.toString().startsWith(
                            ANONYMOUS_PREFIX,
                        )
                    ) {
                        limits.requestsPerIp
                    } else {
                        limits.requestsPerUser
                    }
                RateLimiter.default(limit, limits.rateWindow)
            }
            requestKey { call ->
                tokens?.userIdOrNull(call.request.header(HttpHeaders.Authorization))?.let { "user:$it" }
                    ?: (ANONYMOUS_PREFIX + rateLimitAddress(call.request.origin.remoteAddress))
            }
        }
    }
}

fun Route.graphQLRoutes(
    limits: Limits,
    introspection: Boolean,
) {
    install(RequestBodyLimit) { bodyLimit { limits.maxBodyBytes } }
    rateLimit(perIp) {
        rateLimit(perUser) {
            graphQLPostRoute()
        }
    }
    if (introspection) graphiQLRoute()
}

private const val IPV6_PREFIX_BYTES = 8
private const val ANONYMOUS_PREFIX = "anon:"

fun rateLimitAddress(address: String): String {
    val bytes = if (address.contains(':')) runCatching { InetAddress.getByName(address).address }.getOrNull() else null
    return if (bytes != null && bytes.size == 2 * IPV6_PREFIX_BYTES) {
        bytes.take(IPV6_PREFIX_BYTES).joinToString("", prefix = "v6:") { "%02x".format(it) }
    } else {
        address
    }
}
