package com.greenzean.animekmp.shared.auth

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import com.greenzean.animekmp.shared.auth.data.ApolloSessionRefresher
import com.greenzean.animekmp.shared.auth.data.InMemorySessionStore
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.auth.domain.SessionManager
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.sync.data.ApolloSyncApi
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-22T12:00:00Z")

private const val EMPTY_PAGE = """{"data":{"changesSince":{"changes":[],"nextCursor":"7","hasMore":false}}}"""
private const val UNAUTHENTICATED =
    """{"errors":[{"message":"Authentication failed","extensions":{"code":"UNAUTHENTICATED"}}],"data":null}"""
private const val REFRESHED =
    """{"data":{"refreshSession":{"accessToken":"a2","accessTokenExpiresAt":"2026-10-22T12:15:00Z","refreshToken":"r2"}}}"""

private class ScriptedEngine(
    vararg bodies: String,
) : HttpEngine {
    private val script = bodies.toMutableList()
    val authorizations = mutableListOf<String?>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        authorizations += request.headers.firstOrNull { it.name == "Authorization" }?.value
        return HttpResponse.Builder(200).body(Buffer().writeUtf8(script.removeFirst())).build()
    }

    override fun close() = Unit
}

class ApolloSyncApiAuthTest {
    private val store = InMemorySessionStore()

    private fun api(engine: ScriptedEngine): ApolloSyncApi {
        val client =
            ApolloClient
                .Builder()
                .serverUrl("http://localhost/graphql")
                .httpEngine(engine)
                .build()
        val clock =
            object : Clock {
                override fun now() = NOW
            }
        return ApolloSyncApi(client, SessionManager(store, ApolloSessionRefresher(client), clock))
    }

    private suspend fun signIn(
        access: String = "a1",
        expiresIn: kotlin.time.Duration = 15.minutes,
    ) = store.save(Session(access, NOW + expiresIn, "r1"))

    @Test
    fun noSessionSendsNothing() =
        runTest {
            val engine = ScriptedEngine()
            assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), api(engine).pull(null, 10))
            assertEquals(emptyList(), engine.authorizations)
        }

    @Test
    fun theAccessTokenIsSentAsABearerToken() =
        runTest {
            signIn()
            val engine = ScriptedEngine(EMPTY_PAGE)
            assertIs<Result.Success<*>>(api(engine).pull(null, 10))
            assertEquals(listOf<String?>("Bearer a1"), engine.authorizations)
        }

    @Test
    fun anExpiredTokenIsRefreshedBeforeTheCall() =
        runTest {
            signIn(expiresIn = (-1).minutes)
            val engine = ScriptedEngine(REFRESHED, EMPTY_PAGE)
            assertIs<Result.Success<*>>(api(engine).pull(null, 10))
            assertEquals(listOf<String?>(null, "Bearer a2"), engine.authorizations)
            assertEquals("r2", store.get()?.refreshToken)
        }

    @Test
    fun aRejectedTokenIsRefreshedAndTheCallRetriedOnce() =
        runTest {
            signIn()
            val engine = ScriptedEngine(UNAUTHENTICATED, REFRESHED, EMPTY_PAGE)
            assertIs<Result.Success<*>>(api(engine).pull(null, 10))
            assertEquals(listOf<String?>("Bearer a1", null, "Bearer a2"), engine.authorizations)
        }

    @Test
    fun aRejectionAfterTheRetryIsUnauthorized() =
        runTest {
            signIn()
            val engine = ScriptedEngine(UNAUTHENTICATED, REFRESHED, UNAUTHENTICATED)
            assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), api(engine).pull(null, 10))
            assertEquals(3, engine.authorizations.size)
        }

    @Test
    fun aRejectedRefreshTokenSignsOut() =
        runTest {
            signIn()
            val engine = ScriptedEngine(UNAUTHENTICATED, UNAUTHENTICATED)
            assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), api(engine).pull(null, 10))
            assertNull(store.get())
        }
}
