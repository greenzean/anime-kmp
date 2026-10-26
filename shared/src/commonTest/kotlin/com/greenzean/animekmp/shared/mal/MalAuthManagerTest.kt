package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalAuthApi
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalAuthorizer
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalRedirect
import com.greenzean.animekmp.shared.mal.domain.MalSession
import com.greenzean.animekmp.shared.mal.domain.MalTokenStore
import com.greenzean.animekmp.shared.mal.domain.MalTokens
import com.greenzean.animekmp.shared.mal.domain.RandomBytes
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-26T12:00:00Z")

private class FixedClock : Clock {
    override fun now(): Instant = NOW
}

private class FakeStore(
    initial: MalSession? = null,
    var failSaves: Boolean = false,
) : MalTokenStore {
    private val current = MutableStateFlow(initial)
    override val session: Flow<MalSession?> = current

    override suspend fun get(): MalSession? = current.value

    override suspend fun save(session: MalSession): EmptyResult<MalAuthError> {
        if (failSaves) return Result.Error(MalAuthError.STORAGE_FAILED)
        current.value = session
        return Result.Success(Unit)
    }

    override suspend fun clear() {
        current.value = null
    }
}

private class LazyStore(
    private val stored: MalSession?,
) : MalTokenStore {
    private val current = MutableStateFlow<MalSession?>(null)
    private var loaded = false
    override val session: Flow<MalSession?> = current.onStart { get() }

    override suspend fun get(): MalSession? {
        if (!loaded) {
            current.value = stored
            loaded = true
        }
        return current.value
    }

    override suspend fun save(session: MalSession): EmptyResult<MalAuthError> {
        loaded = true
        current.value = session
        return Result.Success(Unit)
    }

    override suspend fun clear() {
        loaded = true
        current.value = null
    }
}

private class FakeApi : MalAuthApi {
    val exchanges = mutableListOf<Pair<String, String>>()
    val refreshes = mutableListOf<String>()
    var exchangeResult: Result<MalTokens, MalAuthError> = Result.Success(MalTokens("a1", "r1", 3600))
    var refreshResult: Result<MalTokens, MalAuthError> = Result.Success(MalTokens("a2", "r2", 3600))

    override suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
    ): Result<MalTokens, MalAuthError> {
        exchanges += code to codeVerifier
        return exchangeResult
    }

    override suspend fun refresh(refreshToken: String): Result<MalTokens, MalAuthError> {
        refreshes += refreshToken
        delay(100)
        return refreshResult
    }
}

private class FakeAuthorizer : MalAuthorizer {
    val urls = mutableListOf<String>()
    val states = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null
    var respond: (Url) -> Result<MalRedirect, MalAuthError> = { url ->
        Result.Success(MalRedirect("code-1", url.parameters["state"]!!))
    }

    override suspend fun authorize(
        url: String,
        state: String,
    ): Result<MalRedirect, MalAuthError> {
        urls += url
        states += state
        gate?.await()
        return respond(Url(url))
    }
}

private class CountingRandom : RandomBytes {
    private var calls = 0

    override fun next(size: Int): ByteArray = ByteArray(size) { (calls + it).toByte() }.also { calls++ }
}

class MalAuthManagerTest {
    private val config = MalConfig("client-1", "https://app.example.test/mal-callback")
    private val api = FakeApi()
    private val authorizer = FakeAuthorizer()

    private fun manager(
        store: MalTokenStore = FakeStore(),
        config: MalConfig = this.config,
    ) = MalAuthManager(config, api, store, authorizer, CountingRandom(), FixedClock())

    private fun session(
        expiresIn: kotlin.time.Duration,
        access: String = "old",
        refresh: String = "r0",
    ) = MalSession(access, NOW + expiresIn, refresh)

    @Test
    fun authorizeUrlCarriesPkcePlainAndTheRedirect() =
        runTest {
            manager().login()

            val url = Url(authorizer.urls.single())
            assertEquals("myanimelist.net", url.host)
            assertEquals("/v1/oauth2/authorize", url.encodedPath)
            assertEquals("code", url.parameters["response_type"])
            assertEquals("client-1", url.parameters["client_id"])
            assertEquals("plain", url.parameters["code_challenge_method"])
            assertEquals(config.redirectUri, url.parameters["redirect_uri"])
            val verifier = url.parameters["code_challenge"]!!
            assertTrue(verifier.length in 43..128)
            assertTrue(url.parameters["state"]!!.isNotBlank())
        }

    @Test
    fun loginExchangesTheCodeWithTheVerifierAndStoresTheTokens() =
        runTest {
            val store = FakeStore()

            val result = manager(store).login()

            assertEquals(Result.Success(Unit), result)
            val verifier = Url(authorizer.urls.single()).parameters["code_challenge"]!!
            assertEquals(listOf("code-1" to verifier), api.exchanges)
            assertEquals(MalSession("a1", NOW + 60.minutes, "r1"), store.get())
        }

    @Test
    fun loginWithoutClientIdIsNotConfigured() =
        runTest {
            val result = manager(config = config.copy(clientId = "")).login()

            assertEquals(Result.Error(MalAuthError.NOT_CONFIGURED), result)
            assertTrue(authorizer.urls.isEmpty())
        }

    @Test
    fun loginWithoutRedirectUriIsNotConfigured() =
        runTest {
            val result = manager(config = config.copy(redirectUri = "")).login()

            assertEquals(Result.Error(MalAuthError.NOT_CONFIGURED), result)
            assertTrue(authorizer.urls.isEmpty())
        }

    @Test
    fun theAuthorizerIsGivenTheStateInTheUrl() =
        runTest {
            manager().login()

            assertEquals(Url(authorizer.urls.single()).parameters["state"], authorizer.states.single())
        }

    @Test
    fun aSecondLoginWhileOneIsPendingIsRejected() =
        runTest {
            authorizer.gate = CompletableDeferred()
            val manager = manager()
            val first = async { manager.login() }
            testScheduler.advanceUntilIdle()

            assertEquals(Result.Error(MalAuthError.IN_PROGRESS), manager.login())
            authorizer.gate?.complete(Unit)
            assertEquals(Result.Success(Unit), first.await())
        }

    @Test
    fun eachLoginAsksForFreshRandomBytes() =
        runTest {
            val manager = manager()
            manager.login()
            manager.login()

            val verifiers = authorizer.urls.map { Url(it).parameters["code_challenge"]!! }
            assertEquals(2, verifiers.toSet().size)
        }

    @Test
    fun aFailedSaveIsAFailedLogin() =
        runTest {
            val store = FakeStore(failSaves = true)
            val manager = manager(store)

            assertEquals(Result.Error(MalAuthError.STORAGE_FAILED), manager.login())
            assertFalse(manager.isLoggedIn.first())
        }

    @Test
    fun aFailedSaveAfterRefreshIsReported() =
        runTest {
            val store = FakeStore(session(0.minutes), failSaves = true)

            assertEquals(Result.Error(MalAuthError.STORAGE_FAILED), manager(store).accessToken())
        }

    @Test
    fun logoutWhileTheLoginIsPendingWinsOverTheLateSave() =
        runTest {
            authorizer.gate = CompletableDeferred()
            val store = FakeStore()
            val manager = manager(store)
            val login = async { manager.login() }
            testScheduler.advanceUntilIdle()

            manager.logout()
            authorizer.gate?.complete(Unit)

            assertEquals(Result.Error(MalAuthError.CANCELLED), login.await())
            assertNull(store.get())
        }

    @Test
    fun stateMismatchStoresNothing() =
        runTest {
            val store = FakeStore()
            authorizer.respond = { Result.Success(MalRedirect("code-1", "forged")) }

            val result = manager(store).login()

            assertEquals(Result.Error(MalAuthError.STATE_MISMATCH), result)
            assertTrue(api.exchanges.isEmpty())
            assertNull(store.get())
        }

    @Test
    fun cancelledAndDeniedStoreNothing() =
        runTest {
            val store = FakeStore()
            for (error in listOf(MalAuthError.CANCELLED, MalAuthError.ACCESS_DENIED, MalAuthError.NO_BROWSER)) {
                authorizer.respond = { Result.Error(error) }

                assertEquals(Result.Error(error), manager(store).login())
            }
            assertTrue(api.exchanges.isEmpty())
            assertNull(store.get())
        }

    @Test
    fun exchangeFailureStoresNothing() =
        runTest {
            val store = FakeStore()
            api.exchangeResult = Result.Error(MalAuthError.NO_INTERNET)

            val result = manager(store).login()

            assertEquals(Result.Error(MalAuthError.NO_INTERNET), result)
            assertNull(store.get())
        }

    @Test
    fun freshTokenIsReturnedWithoutRefreshing() =
        runTest {
            val manager = manager(FakeStore(session(10.days)))

            assertEquals(Result.Success("old"), manager.accessToken())
            assertTrue(api.refreshes.isEmpty())
        }

    @Test
    fun expiringTokenIsRefreshedAndTheNewRefreshTokenIsKept() =
        runTest {
            val store = FakeStore(session(10.seconds))

            val result = manager(store).accessToken()

            assertEquals(Result.Success("a2"), result)
            assertEquals(listOf("r0"), api.refreshes)
            assertEquals("r2", store.get()?.refreshToken)
        }

    @Test
    fun concurrentCallsRefreshOnce() =
        runTest {
            val manager = manager(FakeStore(session(0.minutes)))

            val tokens = (1..5).map { async { manager.accessToken() } }.awaitAll()

            assertEquals(1, api.refreshes.size)
            assertTrue(tokens.all { it == Result.Success("a2") })
        }

    @Test
    fun invalidGrantOnRefreshEndsTheSession() =
        runTest {
            val store = FakeStore(session(0.minutes))
            api.refreshResult = Result.Error(MalAuthError.INVALID_GRANT)
            val manager = manager(store)

            assertEquals(Result.Error(MalAuthError.INVALID_GRANT), manager.accessToken())
            assertNull(store.get())
            assertFalse(manager.isLoggedIn.first())
        }

    @Test
    fun transientRefreshFailureKeepsTheSession() =
        runTest {
            val store = FakeStore(session(0.minutes))
            api.refreshResult = Result.Error(MalAuthError.NO_INTERNET)

            assertEquals(Result.Error(MalAuthError.NO_INTERNET), manager(store).accessToken())
            assertEquals("r0", store.get()?.refreshToken)
        }

    @Test
    fun aTransientRefreshFailureKeepsUsingATokenThatIsStillValid() =
        runTest {
            val store = FakeStore(session(10.seconds))
            api.refreshResult = Result.Error(MalAuthError.SERVER)

            assertEquals(Result.Success("old"), manager(store).accessToken())
            assertEquals("r0", store.get()?.refreshToken)
        }

    @Test
    fun storedTokensCountAsLoggedInAfterAColdStart() =
        runTest {
            val manager = manager(LazyStore(session(10.days)))

            assertTrue(manager.isLoggedIn.first())
        }

    @Test
    fun noSessionMeansNotLoggedIn() =
        runTest {
            val manager = manager()

            assertEquals(Result.Error(MalAuthError.NOT_LOGGED_IN), manager.accessToken())
            assertFalse(manager.isLoggedIn.first())
        }

    @Test
    fun logoutDeletesTheTokens() =
        runTest {
            val store = FakeStore(session(10.days))
            val manager = manager(store)
            assertTrue(manager.isLoggedIn.first())

            manager.logout()

            assertNull(store.get())
            assertFalse(manager.isLoggedIn.first())
        }
}
