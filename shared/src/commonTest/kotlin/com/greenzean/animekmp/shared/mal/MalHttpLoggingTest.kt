package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.logging.COMMON_LEAKS
import com.greenzean.animekmp.shared.core.logging.LogLevel
import com.greenzean.animekmp.shared.core.logging.RecordingLogSink
import com.greenzean.animekmp.shared.core.logging.SinkLogger
import com.greenzean.animekmp.shared.mal.data.KtorMalAnimeApi
import com.greenzean.animekmp.shared.mal.data.KtorMalAuthApi
import com.greenzean.animekmp.shared.mal.data.KtorMalSuggestionsApi
import com.greenzean.animekmp.shared.mal.data.KtorMalUserApi
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val ACCESS = "SENTINEL-ACCESS-7f3a"
private const val REFRESH = "SENTINEL-REFRESH-91bc"
private const val AUTH_CODE = "SENTINEL-CODE-55de"
private const val VERIFIER = "SENTINEL-VERIFIER-02aa"
private const val USERNAME = "SENTINEL-USER-c4d1"
private const val SEARCH_TEXT = "SENTINEL-QUERY-b8e2"
private const val TITLE = "SENTINEL-TITLE-6a90"
private const val MAL_HTTP = "MalHttp"

class MalHttpLoggingTest {
    private val config = MalConfig("client-1", "https://app.example.test/mal-callback")
    private val sink = RecordingLogSink()
    private val logger = SinkLogger(listOf(sink))
    private val session = MalSession(ACCESS, Instant.parse("2026-10-26T13:00:00Z"), REFRESH)
    private val harness = MalAccountHarness(session, logger = logger)

    private val animeBody =
        """
        {"data":[{"node":{"id":1,"title":"$TITLE","alternative_titles":{"en":"$TITLE EN"},"mean":8.75,
                   "media_type":"tv","num_episodes":26,"status":"finished_airing"}}],
         "paging":{"next":"https://api.myanimelist.net/v2/anime?offset=20&q=$SEARCH_TEXT"}}
        """.trimIndent()

    private val detailsBody =
        """{"id":21,"title":"$TITLE","synopsis":"$TITLE","genres":[],"recommendations":[]}"""

    private val userBody = """{"id":7,"name":"$USERNAME"}"""

    private val tokenBody =
        """{"token_type":"Bearer","expires_in":3600,"access_token":"$ACCESS","refresh_token":"$REFRESH"}"""

    @AfterTest
    fun tearDown() = harness.close()

    private fun client(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String,
        fail: Throwable? = null,
    ) = HttpClient(
        MockEngine {
            if (fail != null) throw fail
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ) { followRedirects = false }

    private fun assertRedacted() {
        sink.assertNoneContains(
            ACCESS,
            REFRESH,
            AUTH_CODE,
            VERIFIER,
            USERNAME,
            SEARCH_TEXT,
            TITLE,
            *COMMON_LEAKS,
        )
    }

    private fun assertSuccessEntry(
        method: String,
        pathPart: String,
        status: Int,
    ) {
        val entry = sink.entries.single { it.tag == MAL_HTTP }
        assertEquals(LogLevel.Debug, entry.level)
        assertTrue(entry.message.startsWith("$method "), entry.message)
        assertTrue(pathPart in entry.message, entry.message)
        assertTrue("-> $status" in entry.message, entry.message)
        assertTrue(entry.message.endsWith(" ms)"), entry.message)
    }

    private fun assertFailureEntry(
        method: String,
        pathPart: String,
        code: String,
        cause: String,
    ) {
        val entry = sink.entries.single { it.tag == MAL_HTTP }
        assertEquals(LogLevel.Warn, entry.level)
        assertTrue(entry.message.startsWith("$method "), entry.message)
        assertTrue(pathPart in entry.message, entry.message)
        assertTrue("failed: $code ($cause)" in entry.message, entry.message)
        assertEquals(null, entry.throwable)
    }

    @Test
    fun searchLogsStatusAndDurationWithoutTheQueryOrTitles() =
        runTest {
            KtorMalAnimeApi(client(body = animeBody), config, logger = logger).search(SEARCH_TEXT, 20, 0)

            assertSuccessEntry("GET", "/v2/anime", 200)
            assertRedacted()
        }

    @Test
    fun detailsLogsStatusAndDurationWithoutTitles() =
        runTest {
            KtorMalAnimeApi(client(body = detailsBody), config, logger = logger).details(21)

            assertSuccessEntry("GET", "/v2/anime/21", 200)
            assertRedacted()
        }

    @Test
    fun animeFailureLogsTheCodeAndTheExceptionClassOnly() =
        runTest {
            val api =
                KtorMalAnimeApi(
                    client(body = "", fail = IOException("https://api.myanimelist.net/v2/anime?q=$SEARCH_TEXT")),
                    config,
                    logger = logger,
                )

            api.search(SEARCH_TEXT, 20, 0)

            assertFailureEntry("GET", "/v2/anime", "NO_CONNECTION", "IOException")
            assertRedacted()
        }

    @Test
    fun animeServerErrorLogsTheStatus() =
        runTest {
            val api = KtorMalAnimeApi(client(HttpStatusCode.InternalServerError, "{}"), config, logger = logger)
            api.search(SEARCH_TEXT, 20, 0)

            assertSuccessEntry("GET", "/v2/anime", 500)
            assertRedacted()
        }

    @Test
    fun suggestionsLogStatusAndDurationWithoutTokensOrTitles() =
        runTest {
            KtorMalSuggestionsApi(client(body = animeBody), harness.auth, logger = logger).suggestions(20, 0)

            assertSuccessEntry("GET", "/v2/anime/suggestions", 200)
            assertRedacted()
        }

    @Test
    fun suggestionsFailureLogsTheCodeAndTheExceptionClassOnly() =
        runTest {
            val api =
                KtorMalSuggestionsApi(
                    client(body = "", fail = IOException("Bearer $ACCESS at /v2/anime/suggestions?limit=20")),
                    harness.auth,
                    logger = logger,
                )

            api.suggestions(20, 0)

            assertFailureEntry("GET", "/v2/anime/suggestions", "NO_CONNECTION", "IOException")
            assertRedacted()
        }

    @Test
    fun userCallLogsStatusAndDurationWithoutTheUsernameOrToken() =
        runTest {
            KtorMalUserApi(client(body = userBody), harness.auth, logger = logger).me()

            assertSuccessEntry("GET", "/v2/users/@me", 200)
            assertRedacted()
        }

    @Test
    fun userCallFailureLogsTheCodeAndTheExceptionClassOnly() =
        runTest {
            val failing = client(body = "", fail = IOException("Bearer $ACCESS"))
            val api = KtorMalUserApi(failing, harness.auth, logger = logger)

            api.me()

            assertFailureEntry("GET", "/v2/users/@me", "NO_CONNECTION", "IOException")
            assertRedacted()
        }

    @Test
    fun userCallForbiddenLogsTheStatus() =
        runTest {
            KtorMalUserApi(client(HttpStatusCode.Forbidden, userBody), harness.auth, logger = logger).me()

            assertSuccessEntry("GET", "/v2/users/@me", 403)
            assertRedacted()
        }

    @Test
    fun tokenExchangeLogsStatusAndDurationWithoutCodesOrTokens() =
        runTest {
            KtorMalAuthApi(client(body = tokenBody), config, logger = logger).exchangeCode(AUTH_CODE, VERIFIER)

            assertSuccessEntry("POST", "/v1/oauth2/token", 200)
            assertRedacted()
        }

    @Test
    fun tokenRefreshLogsStatusAndDurationWithoutTokens() =
        runTest {
            KtorMalAuthApi(client(body = tokenBody), config, logger = logger).refresh(REFRESH)

            assertSuccessEntry("POST", "/v1/oauth2/token", 200)
            assertRedacted()
        }

    @Test
    fun tokenFailureLogsTheCodeAndTheExceptionClassOnly() =
        runTest {
            val api =
                KtorMalAuthApi(
                    client(
                        body = "",
                        fail = IOException("code=$AUTH_CODE&code_verifier=$VERIFIER&refresh_token=$REFRESH"),
                    ),
                    config,
                    logger = logger,
                )

            api.exchangeCode(AUTH_CODE, VERIFIER)

            assertFailureEntry("POST", "/v1/oauth2/token", "NO_INTERNET", "IOException")
            assertRedacted()
        }
}
