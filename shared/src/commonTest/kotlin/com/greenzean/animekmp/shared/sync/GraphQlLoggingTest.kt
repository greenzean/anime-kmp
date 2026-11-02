package com.greenzean.animekmp.shared.sync

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import com.greenzean.animekmp.shared.auth.data.ApolloAuthApi
import com.greenzean.animekmp.shared.auth.data.ApolloSessionRefresher
import com.greenzean.animekmp.shared.auth.data.InMemorySessionStore
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.auth.domain.SessionManager
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.core.logging.COMMON_LEAKS
import com.greenzean.animekmp.shared.core.logging.LogLevel
import com.greenzean.animekmp.shared.core.logging.RecordingLogSink
import com.greenzean.animekmp.shared.core.logging.SinkLogger
import com.greenzean.animekmp.shared.sync.data.ApolloSyncApi
import com.greenzean.animekmp.shared.sync.data.GraphQlLoggingInterceptor
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val GRAPHQL = "GraphQL"
private const val ID_TOKEN = "SENTINEL-IDTOKEN-cc33"
private const val REFRESH_TOKEN = "SENTINEL-REFRESH-bb22"
private const val ACCESS_TOKEN = "SENTINEL-ACCESS-aa11"
private const val SECRET_MESSAGE = "secret message"
private const val SECRET_URL = "https://internal.example/path?token=SENTINEL-URLTOKEN-dd44"
private val NOW = Instant.parse("2026-10-22T12:00:00Z")
private val SENTINELS =
    arrayOf(ID_TOKEN, REFRESH_TOKEN, ACCESS_TOKEN, "SENTINEL", SECRET_MESSAGE, "http", *COMMON_LEAKS)

private const val SESSION_BODY =
    """{"data":{"%s":{"accessToken":"$ACCESS_TOKEN","accessTokenExpiresAt":"2026-10-22T12:15:00Z","refreshToken":"$REFRESH_TOKEN"}}}"""
private const val EMPTY_PAGE = """{"data":{"changesSince":{"changes":[],"nextCursor":"7","hasMore":false}}}"""
private const val EMPTY_PUSH = """{"data":{"pushChanges":{"results":[]}}}"""
private const val ERRORS =
    """{"errors":[{"message":"$SECRET_MESSAGE","extensions":{"code":"UNAUTHENTICATED"}}],"data":null}"""

private class BodyEngine(
    private val body: String,
) : HttpEngine {
    override suspend fun execute(request: HttpRequest): HttpResponse =
        HttpResponse.Builder(200).body(Buffer().writeUtf8(body)).build()

    override fun close() = Unit
}

private class ThrowingEngine : HttpEngine {
    override suspend fun execute(request: HttpRequest): HttpResponse = throw SecretTransportException()

    override fun close() = Unit
}

private class SecretTransportException : Exception("failed calling $SECRET_URL with Bearer $ACCESS_TOKEN")

class GraphQlLoggingTest {
    private val sink = RecordingLogSink()
    private val logger = SinkLogger(listOf(sink))
    private val store = InMemorySessionStore()
    private val clock =
        object : Clock {
            override fun now() = NOW
        }

    private fun client(engine: HttpEngine) =
        ApolloClient
            .Builder()
            .serverUrl("http://localhost/graphql")
            .httpEngine(engine)
            .addInterceptor(GraphQlLoggingInterceptor(logger))
            .build()

    private fun client(body: String) = client(BodyEngine(body))

    private fun syncApi(client: ApolloClient) =
        ApolloSyncApi(client, SessionManager(store, ApolloSessionRefresher(client), clock))

    private suspend fun signIn() = store.save(Session(ACCESS_TOKEN, NOW + 15.minutes, REFRESH_TOKEN))

    private fun graphQlEntries() = sink.entries.filter { it.tag == GRAPHQL }

    private fun assertOkMessage(
        message: String,
        prefix: String,
    ) {
        assertTrue(message.startsWith("$prefix -> ok ("), message)
        assertTrue(Regex("""\d+ ms\)""").containsMatchIn(message), message)
    }

    @Test
    fun signInWithGoogleLogsAnOkMutationWithoutTheTokens() =
        runTest {
            val result = ApolloAuthApi(client(SESSION_BODY.format("signInWithGoogle"))).signInWithGoogle(ID_TOKEN)

            assertTrue(result is Result.Success)
            val entry = graphQlEntries().single()
            assertEquals(LogLevel.Debug, entry.level)
            assertOkMessage(entry.message, "mutation SignInWithGoogle")
            sink.assertNoneContains(*SENTINELS)
        }

    @Test
    fun refreshSessionLogsAnOkMutationWithoutTheTokens() =
        runTest {
            val result = ApolloSessionRefresher(client(SESSION_BODY.format("refreshSession"))).refresh(REFRESH_TOKEN)

            assertTrue(result is Result.Success)
            val entry = graphQlEntries().single()
            assertEquals(LogLevel.Debug, entry.level)
            assertOkMessage(entry.message, "mutation RefreshSession")
            sink.assertNoneContains(*SENTINELS)
        }

    @Test
    fun aPullLogsAnOkQueryWithoutTheBearerToken() =
        runTest {
            signIn()

            val result = syncApi(client(EMPTY_PAGE)).pull(null, 10)

            assertTrue(result is Result.Success)
            assertOkMessage(graphQlEntries().single().message, "query ChangesSince")
            sink.assertNoneContains(*SENTINELS)
        }

    @Test
    fun aPushLogsAnOkMutationWithoutTheBearerToken() =
        runTest {
            signIn()

            val result = syncApi(client(EMPTY_PUSH)).push(emptyList())

            assertTrue(result is Result.Success)
            assertOkMessage(graphQlEntries().single().message, "mutation PushChanges")
            sink.assertNoneContains(*SENTINELS)
        }

    @Test
    fun aTransportFailureLogsTheExceptionNameOnly() =
        runTest {
            val result = ApolloAuthApi(client(ThrowingEngine())).signInWithGoogle(ID_TOKEN)

            assertTrue(result is Result.Error)
            val entry = graphQlEntries().single()
            assertEquals(LogLevel.Warn, entry.level)
            assertNull(entry.throwable)
            assertTrue(entry.message.startsWith("mutation SignInWithGoogle failed: "), entry.message)
            assertTrue("Exception" in entry.message, entry.message)
            assertTrue(Regex("""\(\d+ ms\)$""").containsMatchIn(entry.message), entry.message)
            sink.assertNoneContains(*SENTINELS, "failed calling", "internal.example")
        }

    @Test
    fun graphQlErrorsLogTheCodesAndNotTheMessages() =
        runTest {
            val result = ApolloAuthApi(client(ERRORS)).signInWithGoogle(ID_TOKEN)

            assertTrue(result is Result.Error)
            val entry = graphQlEntries().single()
            assertEquals(LogLevel.Warn, entry.level)
            assertNull(entry.throwable)
            assertTrue(
                entry.message.startsWith("mutation SignInWithGoogle -> graphql errors: UNAUTHENTICATED ("),
                entry.message,
            )
            sink.assertNoneContains(*SENTINELS)
        }

    @Test
    fun aRejectedPullLogsTheCodeWithoutTheBearerToken() =
        runTest {
            signIn()

            val result = syncApi(client(ERRORS)).pull(null, 10)

            assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), result)
            assertTrue(
                graphQlEntries().any { "query ChangesSince -> graphql errors: UNAUTHENTICATED" in it.message },
                sink.entries.toString(),
            )
            sink.assertNoneContains(*SENTINELS)
        }
}
