package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalCache
import com.greenzean.animekmp.shared.mal.domain.MalAccountStore
import com.greenzean.animekmp.shared.mal.domain.MalAnimeApi
import com.greenzean.animekmp.shared.mal.domain.MalAnimeDetails
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalAnimeSummary
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalAuthApi
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalAuthorizer
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalRedirect
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage
import com.greenzean.animekmp.shared.mal.domain.MalSession
import com.greenzean.animekmp.shared.mal.domain.MalThrottle
import com.greenzean.animekmp.shared.mal.domain.MalTokenStore
import com.greenzean.animekmp.shared.mal.domain.MalTokens
import com.greenzean.animekmp.shared.mal.domain.MalUser
import com.greenzean.animekmp.shared.mal.domain.MalUserApi
import com.greenzean.animekmp.shared.mal.domain.RandomBytes
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.time.Clock
import kotlin.time.Instant

class FakeMalAccountStore(
    consent: Boolean = false,
    username: String? = null,
) : MalAccountStore {
    private val consentState = MutableStateFlow(consent)
    private val usernameState = MutableStateFlow(username)
    override val consentGiven: StateFlow<Boolean> = consentState
    override val username: StateFlow<String?> = usernameState

    override fun setConsent(given: Boolean) {
        consentState.value = given
    }

    override fun setUsername(name: String?) {
        usernameState.value = name
    }
}

class FakeMalUserApi(
    var result: Result<MalUser, MalApiError> = Result.Success(MalUser("francisco")),
) : MalUserApi {
    var calls = 0

    override suspend fun me(): Result<MalUser, MalApiError> {
        calls++
        return result
    }
}

private val ACCOUNT_NOW = Instant.parse("2026-10-26T12:00:00Z")

private class AccountFixedClock : Clock {
    override fun now(): Instant = ACCOUNT_NOW
}

class InMemoryMalTokenStore(
    initial: MalSession? = null,
) : MalTokenStore {
    private val current = MutableStateFlow(initial)
    override val session: Flow<MalSession?> = current

    override suspend fun get(): MalSession? = current.value

    override suspend fun save(session: MalSession): EmptyResult<MalAuthError> {
        current.value = session
        return Result.Success(Unit)
    }

    override suspend fun clear() {
        current.value = null
    }
}

private class ScriptedMalAuthApi : MalAuthApi {
    override suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
    ): Result<MalTokens, MalAuthError> = Result.Success(MalTokens("access-1", "refresh-1", 3600))

    override suspend fun refresh(refreshToken: String): Result<MalTokens, MalAuthError> =
        Result.Success(MalTokens("access-2", "refresh-2", 3600))
}

class ScriptedMalAuthorizer : MalAuthorizer {
    var gate: CompletableDeferred<Unit>? = null
    var error: MalAuthError? = null
    var calls = 0

    override suspend fun authorize(
        url: String,
        state: String,
    ): Result<MalRedirect, MalAuthError> {
        calls++
        gate?.await()
        return error?.let { Result.Error(it) } ?: Result.Success(MalRedirect("code-1", Url(url).parameters["state"]!!))
    }
}

private class CountingRandomBytes : RandomBytes {
    private var calls = 0

    override fun next(size: Int): ByteArray = ByteArray(size) { (calls + it).toByte() }.also { calls++ }
}

class MalAccountHarness(
    session: MalSession? = null,
    consent: Boolean = false,
    username: String? = null,
    userResult: Result<MalUser, MalApiError> = Result.Success(MalUser("francisco")),
) {
    val tokenStore = InMemoryMalTokenStore(session)
    val authorizer = ScriptedMalAuthorizer()
    val accountStore = FakeMalAccountStore(consent, username)
    val userApi = FakeMalUserApi(userResult)
    val auth =
        MalAuthManager(
            MalConfig("client-1", "https://app.example.test/mal-callback"),
            ScriptedMalAuthApi(),
            tokenStore,
            authorizer,
            CountingRandomBytes(),
            AccountFixedClock(),
        )
    val animeApi = CacheProbeMalAnimeApi()
    private val driver = createTestDriver()
    val repository =
        MalAnimeRepository(
            animeApi,
            SqlDelightMalCache(AnimeDatabase(driver), UnconfinedTestDispatcher()),
            MalThrottle(AccountFixedClock()),
            AccountFixedClock(),
        )

    suspend fun warmCache() {
        animeApi.available = true
        repository.details(1)
        animeApi.available = false
    }

    fun close() = driver.close()
}

class CacheProbeMalAnimeApi : MalAnimeApi {
    var available = false
    private val summary = MalAnimeSummary(1, "Cowboy Bebop", null, 8.7, "tv", 26, "finished_airing")

    override suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
    ): Result<MalSearchPage, MalApiError> = Result.Success(MalSearchPage(emptyList(), false))

    override suspend fun details(malId: Int): Result<MalAnimeDetails, MalApiError> =
        if (available) {
            Result.Success(MalAnimeDetails(summary, "Space cowboys.", listOf("Action"), "1998-04-03", 40, emptyList()))
        } else {
            Result.Error(MalApiError.NO_CONNECTION)
        }
}

fun validMalSession(): MalSession =
    MalSession("stored-access", ACCOUNT_NOW + kotlin.time.Duration.parse("1h"), "stored-refresh")
