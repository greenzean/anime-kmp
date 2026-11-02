package com.greenzean.animekmp.shared.sync

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import com.greenzean.animekmp.shared.auth.data.ApolloSessionRefresher
import com.greenzean.animekmp.shared.auth.data.InMemorySessionStore
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.auth.domain.SessionManager
import com.greenzean.animekmp.shared.auth.domain.SessionRefresher
import com.greenzean.animekmp.shared.core.data.TestAnimeCatalog
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.core.logging.COMMON_LEAKS
import com.greenzean.animekmp.shared.core.logging.LogLevel
import com.greenzean.animekmp.shared.core.logging.RecordingLogSink
import com.greenzean.animekmp.shared.core.logging.SinkLogger
import com.greenzean.animekmp.shared.library.data.SqlDelightLibraryDataSource
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.sync.data.ApolloSyncApi
import com.greenzean.animekmp.shared.sync.data.SqlDelightSyncLocalDataSource
import com.greenzean.animekmp.shared.sync.domain.ChangesPage
import com.greenzean.animekmp.shared.sync.domain.SyncChange
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val ACCESS = "SENTINEL-ACCESS-aa11"
private const val REFRESH = "SENTINEL-REFRESH-bb22"
private const val SYNC = "Sync"
private val SYNC_NOW = Instant.parse("2026-10-22T12:00:00Z")
private const val EMPTY_PAGE = """{"data":{"changesSince":{"changes":[],"nextCursor":"7","hasMore":false}}}"""
private const val UNAUTHENTICATED =
    """{"errors":[{"message":"Authentication failed","extensions":{"code":"UNAUTHENTICATED"}}],"data":null}"""

private class QueueEngine(
    vararg bodies: String,
) : HttpEngine {
    private val script = bodies.toMutableList()

    override suspend fun execute(request: HttpRequest): HttpResponse =
        HttpResponse.Builder(200).body(Buffer().writeUtf8(script.removeFirst())).build()

    override fun close() = Unit
}

class SyncLoggingTest {
    private val sink = RecordingLogSink()
    private val logger = SinkLogger(listOf(sink))
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver).also(TestAnimeCatalog::apply)
    private val dispatcher = UnconfinedTestDispatcher()
    private val library = SqlDelightLibraryDataSource(db, dispatcher = dispatcher)
    private val local = SqlDelightSyncLocalDataSource(db, dispatcher)
    private val api = FakeSyncApi()
    private val engine = SyncEngine(api, local, logger = logger)
    private val store = InMemorySessionStore()
    private val clock =
        object : Clock {
            override fun now() = SYNC_NOW
        }

    @AfterTest
    fun tearDown() = driver.close()

    private fun assertRedacted() = sink.assertNoneContains(ACCESS, REFRESH, "WATCHING", *COMMON_LEAKS)

    private fun apolloApi(vararg bodies: String): ApolloSyncApi {
        val client =
            ApolloClient
                .Builder()
                .serverUrl("http://localhost/graphql")
                .httpEngine(QueueEngine(*bodies))
                .build()
        return ApolloSyncApi(client, SessionManager(store, ApolloSessionRefresher(client), clock), logger = logger)
    }

    @Test
    fun aSyncLogsThePushCountsAndThePulledRows() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            library.saveEntry(5114, WatchStatus.WATCHING, 0, null)
            api.pages =
                mutableListOf(
                    Result.Success(
                        ChangesPage(
                            "5",
                            false,
                            listOf(
                                SyncChange(
                                    "p1",
                                    9253,
                                    WatchStatus.COMPLETED,
                                    0,
                                    null,
                                    Instant.fromEpochMilliseconds(100),
                                    null,
                                ),
                            ),
                        ),
                    ),
                )

            engine.sync()

            val messages = sink.messages(SYNC)
            assertTrue("push 2 pending -> accepted=2 conflict=0" in messages, messages.toString())
            assertTrue("pull 1 rows" in messages, messages.toString())
            assertTrue(sink.entries.filter { it.tag == SYNC }.all { it.level == LogLevel.Info })
            assertRedacted()
        }

    @Test
    fun aPushFailureLogsTheErrorType() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            api.pushError = DataError.Network.NO_INTERNET

            engine.sync()

            val entry = sink.entries.single { it.level == LogLevel.Warn }
            assertEquals(SYNC, entry.tag)
            assertTrue("push failed" in entry.message && "NO_INTERNET" in entry.message, entry.message)
            assertRedacted()
        }

    @Test
    fun aPullFailureLogsTheErrorType() =
        runTest {
            api.pages = mutableListOf(Result.Error(DataError.Network.SERVER_ERROR))

            engine.sync()

            val entry = sink.entries.single { it.level == LogLevel.Warn }
            assertTrue("pull failed" in entry.message && "SERVER_ERROR" in entry.message, entry.message)
        }

    @Test
    fun aRejectedRefreshTokenLogsSessionEnded() =
        runTest {
            store.save(Session(ACCESS, SYNC_NOW - 1.minutes, REFRESH))
            val refresher = SessionRefresher { Result.Error(DataError.Network.UNAUTHORIZED) }

            SessionManager(store, refresher, clock, logger = logger).accessToken()

            val entry = sink.entries.single { it.message == "session ended" }
            assertEquals(SYNC, entry.tag)
            assertEquals(LogLevel.Warn, entry.level)
            assertRedacted()
        }

    @Test
    fun aRenewedSessionLogsIt() =
        runTest {
            store.save(Session(ACCESS, SYNC_NOW - 1.minutes, REFRESH))
            val refresher =
                SessionRefresher {
                    Result.Success(
                        Session("SENTINEL-ACCESS-new", SYNC_NOW + 15.minutes, "SENTINEL-REFRESH-new"),
                    )
                }

            SessionManager(store, refresher, clock, logger = logger).accessToken()

            assertTrue("session renewed" in sink.messages(SYNC), sink.entries.toString())
            sink.assertNoneContains("SENTINEL", *COMMON_LEAKS)
        }

    @Test
    fun anApolloPullNeverLogsTheTokenOrHeaders() =
        runTest {
            store.save(Session(ACCESS, SYNC_NOW + 15.minutes, REFRESH))

            val result = apolloApi(EMPTY_PAGE).pull(null, 10)

            assertTrue(result is Result.Success)
            assertRedacted()
        }

    @Test
    fun anApolloRejectionLogsTheFailureWithoutTheTokenOrHeaders() =
        runTest {
            store.save(Session(ACCESS, SYNC_NOW + 15.minutes, REFRESH))

            val result = apolloApi(UNAUTHENTICATED, UNAUTHENTICATED).pull(null, 10)

            assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), result)
            assertTrue(sink.entries.any { it.tag == SYNC && it.level == LogLevel.Warn }, sink.entries.toString())
            assertRedacted()
        }
}
