package com.greenzean.animekmp.shared.mal

import app.cash.turbine.test
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.http.HttpEngine
import com.greenzean.animekmp.shared.auth.data.ApolloSessionRefresher
import com.greenzean.animekmp.shared.auth.data.InMemorySessionStore
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.auth.domain.SessionManager
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.data.ApolloLinkedPlatformsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-22T12:00:00Z")

private const val LINKED = """{"data":{"account":{"linkedPlatforms":["MAL"]}}}"""
private const val NOT_LINKED = """{"data":{"account":{"linkedPlatforms":[]}}}"""
private const val LINK_RESPONSE = """{"data":{"linkPlatform":{"linkedPlatforms":["MAL"]}}}"""
private const val UNLINK_RESPONSE = """{"data":{"unlinkPlatform":{"linkedPlatforms":[]}}}"""
private const val UNAUTHENTICATED =
    """{"errors":[{"message":"Authentication failed","extensions":{"code":"UNAUTHENTICATED"}}],"data":null}"""
private const val REFRESHED =
    """{"data":{"refreshSession":{"accessToken":"a2","accessTokenExpiresAt":"2026-10-22T12:15:00Z","refreshToken":"r2"}}}"""

private class LinkedPlatformsEngine(
    vararg script: Any,
    private val gate: CompletableDeferred<Unit>? = null,
    private val gatedFrom: Int = 0,
    private val gatedTo: Int = Int.MAX_VALUE,
) : HttpEngine {
    private val steps = script.toList()
    val authorizations = mutableListOf<String?>()
    val bodies = mutableListOf<String>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val index = authorizations.size
        authorizations += request.headers.firstOrNull { it.name == "Authorization" }?.value
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        bodies += buffer.readUtf8()
        val step = steps[index]
        if (index in gatedFrom..gatedTo) gate?.await()
        return when (step) {
            is Throwable -> throw step
            else -> HttpResponse.Builder(200).body(Buffer().writeUtf8(step as String)).build()
        }
    }

    override fun close() = Unit
}

private suspend fun awaitFirstRequest(engine: LinkedPlatformsEngine) {
    withContext(Dispatchers.Default) {
        withTimeout(WAIT_MILLIS) {
            while (engine.authorizations.isEmpty()) delay(POLL_MILLIS)
        }
    }
}

private suspend fun awaitRequests(
    engine: LinkedPlatformsEngine,
    count: Int,
) {
    withContext(Dispatchers.Default) {
        withTimeout(WAIT_MILLIS) {
            while (engine.authorizations.size < count) delay(POLL_MILLIS)
        }
    }
}

private const val WAIT_MILLIS = 5_000L
private const val POLL_MILLIS = 10L

class ApolloLinkedPlatformsRepositoryTest {
    private val store = InMemorySessionStore()

    private fun repository(engine: LinkedPlatformsEngine): ApolloLinkedPlatformsRepository {
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
        return ApolloLinkedPlatformsRepository(
            client,
            SessionManager(store, ApolloSessionRefresher(client), clock),
            store,
        )
    }

    private suspend fun signIn() = store.save(Session("a1", NOW + 15.minutes, "r1"))

    private fun networkFailure() = ApolloNetworkException("offline")

    @Test
    fun withoutASessionNothingIsSentAndTheResultIsSuccess() =
        runTest {
            val engine = LinkedPlatformsEngine()
            val repository = repository(engine)

            assertEquals(Result.Success(Unit), repository.refresh())
            assertEquals(Result.Success(Unit), repository.linkMal())
            assertEquals(Result.Success(Unit), repository.unlinkMal())
            assertEquals(emptyList(), engine.authorizations)
        }

    @Test
    fun refreshSetsTheFlagWhenMalIsLinked() =
        runTest {
            signIn()
            val repository = repository(LinkedPlatformsEngine(LINKED))
            repository.observeMalLinked().test {
                assertEquals(false, awaitItem())
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun refreshLeavesTheFlagFalseWhenMalIsNotLinked() =
        runTest {
            signIn()
            val engine = LinkedPlatformsEngine(NOT_LINKED)
            val repository = repository(engine)
            repository.observeMalLinked().test {
                assertEquals(false, awaitItem())
                awaitFirstRequest(engine)
                assertEquals(1, engine.authorizations.size)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun refreshReturnsSuccessAndSendsTheAccountQuery() =
        runTest {
            signIn()
            val engine = LinkedPlatformsEngine(LINKED)

            assertEquals(Result.Success(Unit), repository(engine).refresh())
            assertEquals(true, engine.bodies.single().contains("account"))
        }

    @Test
    fun linkMalSetsTheFlagFromTheResponse() =
        runTest {
            signIn()
            val engine = LinkedPlatformsEngine(NOT_LINKED, LINK_RESPONSE)
            val repository = repository(engine)
            repository.observeMalLinked().test {
                assertEquals(false, awaitItem())
                awaitFirstRequest(engine)
                assertEquals(Result.Success(Unit), repository.linkMal())
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun unlinkMalClearsTheFlagFromTheResponse() =
        runTest {
            signIn()
            val engine = LinkedPlatformsEngine(LINKED, UNLINK_RESPONSE)
            val repository = repository(engine)
            repository.observeMalLinked().test {
                assertEquals(false, awaitItem())
                assertEquals(true, awaitItem())
                assertEquals(Result.Success(Unit), repository.unlinkMal())
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun requestsCarryTheBearerAccessToken() =
        runTest {
            signIn()
            val engine = LinkedPlatformsEngine(LINKED, LINK_RESPONSE, UNLINK_RESPONSE)
            val repository = repository(engine)

            repository.refresh()
            repository.linkMal()
            repository.unlinkMal()

            assertEquals(List<String?>(3) { "Bearer a1" }, engine.authorizations)
        }

    @Test
    fun aRejectedTokenIsRenewedOnceAndTheCallRetried() =
        runTest {
            signIn()
            val engine = LinkedPlatformsEngine(UNAUTHENTICATED, REFRESHED, LINK_RESPONSE)
            val repository = repository(engine)

            assertEquals(Result.Success(Unit), repository.linkMal())
            assertEquals(listOf<String?>("Bearer a1", null, "Bearer a2"), engine.authorizations)
            assertEquals("r2", store.get()?.refreshToken)
        }

    @Test
    fun aSecondRejectionReturnsUnauthorized() =
        runTest {
            signIn()
            val engine = LinkedPlatformsEngine(UNAUTHENTICATED, REFRESHED, UNAUTHENTICATED)

            assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), repository(engine).refresh())
            assertEquals(3, engine.authorizations.size)
        }

    @Test
    fun aNetworkFailureReturnsTheErrorAndLeavesTheFlagUnchanged() =
        runTest {
            signIn()
            val engine = LinkedPlatformsEngine(LINKED, networkFailure(), networkFailure())
            val repository = repository(engine)
            repository.observeMalLinked().test {
                assertEquals(false, awaitItem())
                assertEquals(true, awaitItem())
                assertEquals(Result.Error(DataError.Network.NO_INTERNET), repository.unlinkMal())
                assertEquals(Result.Error(DataError.Network.NO_INTERNET), repository.refresh())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observingWhileSignedOutEmitsFalseWithoutACall() =
        runTest {
            val engine = LinkedPlatformsEngine()
            repository(engine).observeMalLinked().test {
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(emptyList(), engine.authorizations)
        }

    @Test
    fun observingWhileSignedInRefreshesOnCollect() =
        runTest {
            signIn()
            val engine = LinkedPlatformsEngine(LINKED)
            repository(engine).observeMalLinked().test {
                assertEquals(false, awaitItem())
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, engine.authorizations.size)
        }

    @Test
    fun theFlagResetsToFalseAfterSignOut() =
        runTest {
            signIn()
            val repository = repository(LinkedPlatformsEngine(LINKED))
            repository.observeMalLinked().test {
                assertEquals(false, awaitItem())
                assertEquals(true, awaitItem())
                store.clear()
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun theFirstEmissionArrivesBeforeASlowRefreshCompletes() =
        runTest {
            signIn()
            val gate = CompletableDeferred<Unit>()
            val engine = LinkedPlatformsEngine(LINKED, gate = gate)
            repository(engine).observeMalLinked().test {
                assertEquals(false, awaitItem())
                awaitFirstRequest(engine)
                assertEquals(1, engine.authorizations.size)
                expectNoEvents()

                gate.complete(Unit)

                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aRefreshResultArrivingAfterSignOutDoesNotSetTheFlag() =
        runTest {
            signIn()
            val gate = CompletableDeferred<Unit>()
            val engine = LinkedPlatformsEngine(LINKED, NOT_LINKED, gate = gate)
            val repository = repository(engine)
            val refresh = async { repository.refresh() }
            awaitFirstRequest(engine)
            assertEquals(1, engine.authorizations.size)

            store.clear()
            gate.complete(Unit)
            assertEquals(Result.Success(Unit), refresh.await())

            signIn()
            repository.observeMalLinked().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aLinkResultArrivingAfterSignOutDoesNotSetTheFlag() =
        runTest {
            signIn()
            val gate = CompletableDeferred<Unit>()
            val engine = LinkedPlatformsEngine(LINK_RESPONSE, NOT_LINKED, gate = gate)
            val repository = repository(engine)
            val link = async { repository.linkMal() }
            awaitFirstRequest(engine)

            store.clear()
            gate.complete(Unit)
            link.await()

            signIn()
            repository.observeMalLinked().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aRefreshStartedByAnotherAccountDoesNotLeakIntoTheNextSignIn() =
        runTest {
            signIn()
            val gate = CompletableDeferred<Unit>()
            val engine =
                LinkedPlatformsEngine(LINKED, LINKED, NOT_LINKED, NOT_LINKED, gate = gate, gatedFrom = 1, gatedTo = 1)
            val repository = repository(engine)
            repository.observeMalLinked().test {
                assertEquals(false, awaitItem())
                assertEquals(true, awaitItem())
                val refresh = async { repository.refresh() }
                awaitRequests(engine, 2)

                store.clear()
                assertEquals(false, awaitItem())
                signIn()
                assertEquals(false, awaitItem())
                awaitRequests(engine, 3)
                withContext(Dispatchers.Default) { delay(SETTLE_MILLIS) }
                gate.complete(Unit)
                assertEquals(Result.Success(Unit), refresh.await())

                assertEquals(Result.Success(Unit), repository.refresh())
                withContext(Dispatchers.Default) { delay(SETTLE_MILLIS) }
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aLinkStartedByAnotherAccountDoesNotLeakIntoTheNextSignIn() =
        runTest {
            signIn()
            val gate = CompletableDeferred<Unit>()
            val engine =
                LinkedPlatformsEngine(
                    LINKED,
                    LINK_RESPONSE,
                    NOT_LINKED,
                    NOT_LINKED,
                    gate = gate,
                    gatedFrom = 1,
                    gatedTo = 1,
                )
            val repository = repository(engine)
            repository.observeMalLinked().test {
                assertEquals(false, awaitItem())
                assertEquals(true, awaitItem())
                val link = async { repository.linkMal() }
                awaitRequests(engine, 2)

                store.clear()
                assertEquals(false, awaitItem())
                signIn()
                assertEquals(false, awaitItem())
                awaitRequests(engine, 3)
                withContext(Dispatchers.Default) { delay(SETTLE_MILLIS) }
                gate.complete(Unit)
                link.await()

                assertEquals(Result.Success(Unit), repository.refresh())
                withContext(Dispatchers.Default) { delay(SETTLE_MILLIS) }
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aLinkIfWhoseConditionIsFalseSendsNoRequest() =
        runTest {
            signIn()
            val engine = LinkedPlatformsEngine(LINK_RESPONSE)

            assertEquals(Result.Success(Unit), repository(engine).linkMalIf { false })

            assertEquals(emptyList(), engine.authorizations)
        }

    @Test
    fun aLinkIfWhoseConditionIsTrueSendsTheLinkMutation() =
        runTest {
            signIn()
            val engine = LinkedPlatformsEngine(LINK_RESPONSE)
            val repository = repository(engine)

            assertEquals(Result.Success(Unit), repository.linkMalIf { true })

            assertEquals(1, engine.bodies.size)
            assertFalse(engine.bodies.single().contains("unlink", ignoreCase = true))
        }

    @Test
    fun anUnlinkStartedWhileALinkIsInFlightWaitsForItAndIsSentAfterIt() =
        runTest {
            signIn()
            val gate = CompletableDeferred<Unit>()
            val engine = LinkedPlatformsEngine(LINK_RESPONSE, UNLINK_RESPONSE, gate = gate, gatedFrom = 0, gatedTo = 0)
            val repository = repository(engine)
            val link = async { repository.linkMalIf { true } }
            awaitFirstRequest(engine)

            val unlink = async { repository.unlinkMal() }
            withContext(Dispatchers.Default) { delay(SETTLE_MILLIS) }
            assertEquals(1, engine.authorizations.size)
            gate.complete(Unit)

            assertEquals(Result.Success(Unit), link.await())
            assertEquals(Result.Success(Unit), unlink.await())
            assertEquals(2, engine.bodies.size)
            assertFalse(engine.bodies[0].contains("unlink", ignoreCase = true))
            assertEquals(true, engine.bodies[1].contains("unlink", ignoreCase = true))
        }
}

private const val SETTLE_MILLIS = 100L
