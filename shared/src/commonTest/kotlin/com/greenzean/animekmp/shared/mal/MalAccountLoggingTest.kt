package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.core.logging.COMMON_LEAKS
import com.greenzean.animekmp.shared.core.logging.LogLevel
import com.greenzean.animekmp.shared.core.logging.RecordingLogSink
import com.greenzean.animekmp.shared.core.logging.SinkLogger
import com.greenzean.animekmp.shared.mal.domain.ConnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DisconnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.MalAuthApi
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalAuthorizer
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalRedirect
import com.greenzean.animekmp.shared.mal.domain.MalSession
import com.greenzean.animekmp.shared.mal.domain.MalTokens
import com.greenzean.animekmp.shared.mal.domain.MalUser
import com.greenzean.animekmp.shared.mal.domain.RandomBytes
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private const val ACCESS = "SENTINEL-ACCESS-12ab"
private const val REFRESH = "SENTINEL-REFRESH-34cd"
private const val NEW_ACCESS = "SENTINEL-NEW-ACCESS-56ef"
private const val NEW_REFRESH = "SENTINEL-NEW-REFRESH-78aa"
private const val AUTH_CODE = "SENTINEL-CODE-90bb"
private const val USERNAME = "SENTINEL-USER-11cc"
private const val MAL_AUTH = "MalAuth"
private val LOGGING_NOW = Instant.parse("2026-10-26T12:00:00Z")

private class ScriptedAuthApi(
    var refreshResult: Result<MalTokens, MalAuthError> = Result.Success(MalTokens(NEW_ACCESS, NEW_REFRESH, 3600)),
) : MalAuthApi {
    override suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
    ): Result<MalTokens, MalAuthError> = Result.Success(MalTokens(ACCESS, REFRESH, 3600))

    override suspend fun refresh(refreshToken: String): Result<MalTokens, MalAuthError> = refreshResult
}

private fun importStarter() = MalImportHarness(CoroutineScope(UnconfinedTestDispatcher())).start

class MalAccountLoggingTest {
    private val sink = RecordingLogSink()
    private val logger = SinkLogger(listOf(sink))
    private var harness = MalAccountHarness(logger = logger, userResult = Result.Success(MalUser(USERNAME)))

    @AfterTest
    fun tearDown() = harness.close()

    private fun assertRedacted() {
        sink.assertNoneContains(
            ACCESS,
            REFRESH,
            NEW_ACCESS,
            NEW_REFRESH,
            AUTH_CODE,
            "code-1",
            "access-1",
            "refresh-1",
            USERNAME,
            *COMMON_LEAKS,
        )
    }

    private fun manager(
        api: ScriptedAuthApi = ScriptedAuthApi(),
        stored: MalSession? = null,
    ) = MalAuthManager(
        MalConfig("client-1", "https://app.example.test/mal-callback"),
        api,
        InMemoryMalTokenStore(stored),
        MalAuthorizer { url, _ -> Result.Success(MalRedirect(AUTH_CODE, Url(url).parameters["state"]!!)) },
        RandomBytes { ByteArray(it) },
        object : Clock {
            override fun now(): Instant = LOGGING_NOW
        },
        logger = logger,
    )

    @Test
    fun loginLogsItsStepsWithoutCodesOrTokens() =
        runTest {
            manager().login()

            val messages = sink.messages(MAL_AUTH)
            assertTrue("login started" in messages, messages.toString())
            assertTrue("token exchanged" in messages, messages.toString())
            assertEquals(LogLevel.Info, sink.entries.first { it.message == "login started" }.level)
            assertRedacted()
        }

    @Test
    fun aSuccessfulRefreshLogsTheOutcomeOnly() =
        runTest {
            val manager = manager(stored = MalSession(ACCESS, LOGGING_NOW, REFRESH))

            manager.accessToken()

            assertEquals(listOf("token refresh ok"), sink.messages(MAL_AUTH))
            assertEquals(LogLevel.Info, sink.entries.single().level)
            assertRedacted()
        }

    @Test
    fun aFailedRefreshLogsTheCode() =
        runTest {
            val api = ScriptedAuthApi(Result.Error(MalAuthError.INVALID_GRANT))
            val manager = manager(api, MalSession(ACCESS, LOGGING_NOW, REFRESH))

            manager.accessToken()

            val entry = sink.entries.single { it.tag == MAL_AUTH }
            assertEquals(LogLevel.Warn, entry.level)
            assertEquals("token refresh failed: INVALID_GRANT", entry.message)
            assertRedacted()
        }

    @Test
    fun logoutLogsIt() =
        runTest {
            manager(stored = MalSession(ACCESS, LOGGING_NOW, REFRESH)).logout()

            assertTrue("logged out" in sink.messages(MAL_AUTH), sink.entries.toString())
            assertRedacted()
        }

    @Test
    fun connectLogsOkWithoutTheUsername() =
        runTest {
            ConnectMalUseCase(harness.auth, harness.userApi, harness.accountStore, importStarter(), logger = logger)()

            val messages = sink.messages(MAL_AUTH)
            assertTrue("login started" in messages, messages.toString())
            assertTrue("connect ok" in messages, messages.toString())
            assertEquals("SENTINEL-USER-11cc", harness.accountStore.username.value)
            assertRedacted()
        }

    @Test
    fun connectFailureLogsTheCode() =
        runTest {
            harness.authorizer.error = MalAuthError.CANCELLED

            ConnectMalUseCase(harness.auth, harness.userApi, harness.accountStore, importStarter(), logger = logger)()

            val entry = sink.entries.single { it.message.startsWith("connect failed") }
            assertEquals("connect failed: CANCELLED", entry.message)
            assertEquals(MAL_AUTH, entry.tag)
            assertRedacted()
        }

    @Test
    fun disconnectLogsTheLogoutAndTheCacheClear() =
        runTest {
            harness.close()
            harness = MalAccountHarness(validMalSession(), consent = true, username = USERNAME, logger = logger)

            DisconnectMalUseCase(
                harness.auth,
                harness.accountStore,
                harness.repository,
                FakeMalImportProgressStore(),
                FakeMalOutbox(),
                logger = logger,
            )()

            val messages = sink.messages(MAL_AUTH)
            assertTrue("logged out" in messages, messages.toString())
            assertTrue("disconnected, cache cleared" in messages, messages.toString())
            assertRedacted()
        }
}
