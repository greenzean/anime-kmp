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
import com.greenzean.animekmp.shared.core.logging.LogLevel
import com.greenzean.animekmp.shared.core.logging.RecordingLogSink
import com.greenzean.animekmp.shared.core.logging.SinkLogger
import com.greenzean.animekmp.shared.sync.data.ApolloSyncApi
import com.greenzean.animekmp.shared.sync.data.GraphQlLoggingInterceptor
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val TRACE_ID_TOKEN = "SENTINEL-IDTOKEN-t1c1"
private const val TRACE_REFRESH_TOKEN = "SENTINEL-REFRESH-t2c2"
private const val TRACE_ACCESS_TOKEN = "SENTINEL-ACCESS-t3c3"
private const val TRACE_ERROR_MESSAGE = "Visible error message for the developer"
private val TRACE_NOW = Instant.parse("2026-10-22T12:00:00Z")

private const val TRACE_SESSION_BODY =
    """{"data":{"%s":{"accessToken":"$TRACE_ACCESS_TOKEN","accessTokenExpiresAt":"2026-10-22T12:15:00Z","refreshToken":"$TRACE_REFRESH_TOKEN"}}}"""
private const val TRACE_EMPTY_PAGE =
    """{"data":{"changesSince":{"changes":[],"nextCursor":"7","hasMore":false}}}"""
private const val TRACE_ERRORS =
    """{"errors":[{"message":"$TRACE_ERROR_MESSAGE","extensions":{"code":"UNAUTHENTICATED"}}],"data":null}"""

private class TraceBodyEngine(
    private val body: String,
) : HttpEngine {
    override suspend fun execute(request: HttpRequest): HttpResponse =
        HttpResponse.Builder(200).body(Buffer().writeUtf8(body)).build()

    override fun close() = Unit
}

class GraphQlTraceTest {
    private val sink = RecordingLogSink(minLevel = LogLevel.Trace)
    private val logger = SinkLogger(listOf(sink))
    private val store = InMemorySessionStore()
    private val clock =
        object : Clock {
            override fun now() = TRACE_NOW
        }

    private fun client(body: String) =
        ApolloClient
            .Builder()
            .serverUrl("http://localhost/graphql")
            .httpEngine(TraceBodyEngine(body))
            .addInterceptor(GraphQlLoggingInterceptor(logger))
            .build()

    private fun traceText() =
        sink.entries
            .filter { it.tag == "API" && it.level == LogLevel.Trace }
            .joinToString("\n") { it.message }

    private fun allText() = sink.entries.joinToString("\n") { "${it.tag} ${it.message}" }

    @Test
    fun signInTraceShowsTheOperationAndMasksTheIdTokenAndTheResponseTokens() =
        runTest {
            ApolloAuthApi(client(TRACE_SESSION_BODY.format("signInWithGoogle"))).signInWithGoogle(TRACE_ID_TOKEN)

            val text = traceText()
            assertTrue("mutation SignInWithGoogle" in text, text)
            assertTrue(Regex("""\(\d+ ms\)""").containsMatchIn(text), text)
            assertTrue("idToken" in text, text)
            assertTrue("accessToken" in text, text)
            assertTrue("****" in text, text)
            assertFalse(TRACE_ID_TOKEN in allText(), allText())
            assertFalse(TRACE_ACCESS_TOKEN in allText(), allText())
            assertFalse(TRACE_REFRESH_TOKEN in allText(), allText())
        }

    @Test
    fun eachOperationProducesOneApiTraceEntryWithoutAUrlLine() =
        runTest {
            ApolloAuthApi(client(TRACE_SESSION_BODY.format("signInWithGoogle"))).signInWithGoogle(TRACE_ID_TOKEN)

            val entries = sink.entries.filter { it.tag == "API" }
            assertEquals(1, entries.size, entries.toString())
            assertTrue(entries.single().message.startsWith("API #"), entries.single().message)
            assertFalse("url:" in entries.single().message, entries.single().message)
        }

    @Test
    fun refreshTraceMasksTheRefreshTokenVariableAndTheResponseTokens() =
        runTest {
            ApolloSessionRefresher(client(TRACE_SESSION_BODY.format("refreshSession"))).refresh(TRACE_REFRESH_TOKEN)

            val text = traceText()
            assertTrue("mutation RefreshSession" in text, text)
            assertTrue("refreshToken" in text, text)
            assertFalse(TRACE_REFRESH_TOKEN in allText(), allText())
            assertFalse(TRACE_ACCESS_TOKEN in allText(), allText())
        }

    @Test
    fun aPullTraceShowsTheQueryAndTheResponseDataWithoutTheBearerToken() =
        runTest {
            store.save(Session(TRACE_ACCESS_TOKEN, TRACE_NOW + 15.minutes, TRACE_REFRESH_TOKEN))
            val api =
                ApolloSyncApi(
                    client(TRACE_EMPTY_PAGE),
                    SessionManager(store, ApolloSessionRefresher(client("{}")), clock),
                )

            api.pull(null, 10)

            val text = traceText()
            assertTrue("query ChangesSince" in text, text)
            assertTrue("nextCursor" in text, text)
            assertFalse(TRACE_ACCESS_TOKEN in allText(), allText())
            assertFalse(TRACE_REFRESH_TOKEN in allText(), allText())
        }

    @Test
    fun graphQlErrorMessagesAreShownInTheTrace() =
        runTest {
            ApolloAuthApi(client(TRACE_ERRORS)).signInWithGoogle(TRACE_ID_TOKEN)

            val text = traceText()
            assertTrue(TRACE_ERROR_MESSAGE in text, text)
            assertFalse(TRACE_ID_TOKEN in allText(), allText())
        }

    @Test
    fun aDebugSinkReceivesNoTraceEntries() =
        runTest {
            val debugSink = RecordingLogSink(minLevel = LogLevel.Debug)
            val debugClient =
                ApolloClient
                    .Builder()
                    .serverUrl("http://localhost/graphql")
                    .httpEngine(TraceBodyEngine(TRACE_SESSION_BODY.format("signInWithGoogle")))
                    .addInterceptor(GraphQlLoggingInterceptor(SinkLogger(listOf(debugSink))))
                    .build()

            ApolloAuthApi(debugClient).signInWithGoogle(TRACE_ID_TOKEN)

            assertTrue(debugSink.entries.none { it.level == LogLevel.Trace })
            assertEquals(1, debugSink.entries.size)
        }
}
