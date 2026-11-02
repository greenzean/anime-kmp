package com.greenzean.animekmp.shared.mal

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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val T_ACCESS = "SENTINEL-ACCESS-7f3a"
private const val T_REFRESH = "SENTINEL-REFRESH-91bc"
private const val T_CODE = "SENTINEL-CODE-55de"
private const val T_VERIFIER = "SENTINEL-VERIFIER-02aa"
private const val T_SEARCH = "SENTINEL-QUERY-b8e2"
private const val T_TITLE = "SENTINEL-TITLE-6a90"
private const val T_USER = "SENTINEL-USER-c4d1"
private const val T_TAG = "API"

class MalHttpTraceTest {
    private val config = MalConfig("client-1", "https://app.example.test/mal-callback")
    private val sink = RecordingLogSink(minLevel = LogLevel.Trace)
    private val logger = SinkLogger(listOf(sink))
    private val session = MalSession(T_ACCESS, Instant.parse("2026-10-26T13:00:00Z"), T_REFRESH)
    private val harness = MalAccountHarness(session, logger = logger)

    private val animeBody =
        """{"data":[{"node":{"id":1,"title":"$T_TITLE","mean":8.75,"media_type":"tv"}}],"paging":{}}"""
    private val userBody = """{"id":7,"name":"$T_USER"}"""
    private val tokenBody =
        """{"token_type":"Bearer","expires_in":3600,"access_token":"$T_ACCESS","refresh_token":"$T_REFRESH"}"""

    @AfterTest
    fun tearDown() = harness.close()

    private fun client(body: String) =
        HttpClient(
            MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { followRedirects = false }

    private fun trace() =
        sink.entries
            .filter { it.tag == T_TAG && it.level == LogLevel.Trace }
            .joinToString("\n") { it.message }

    private fun everything() = sink.entries.joinToString("\n") { "${it.tag} ${it.message} ${it.throwable}" }

    private fun assertNoSecrets() {
        listOf(T_ACCESS, T_REFRESH, T_CODE, T_VERIFIER).forEach {
            assertFalse(it in everything(), "leaked $it: ${everything()}")
        }
    }

    @Test
    fun searchTraceShowsTheFullUrlWithTheQueryAndTheClientIdHeader() =
        runTest {
            KtorMalAnimeApi(client(animeBody), config, logger = logger).search(T_SEARCH, 20, 0)

            val text = trace()
            assertTrue("GET /v2/anime" in text, text)
            assertTrue("https://api.myanimelist.net/v2/anime?" in text, text)
            assertTrue("q=$T_SEARCH" in text, text)
            assertTrue("limit=20" in text, text)
            assertTrue("X-MAL-CLIENT-ID: client-1" in text, text)
            assertNoSecrets()
        }

    @Test
    fun eachCallProducesOneApiTraceEntry() =
        runTest {
            KtorMalAnimeApi(client(animeBody), config, logger = logger).search(T_SEARCH, 20, 0)

            val entries = sink.entries.filter { it.tag == T_TAG }
            assertEquals(1, entries.size, entries.toString())
            assertTrue(entries.single().level == LogLevel.Trace)
            assertTrue(entries.single().message.startsWith("API #"), entries.single().message)
        }

    @Test
    fun searchTraceShowsTheResponseBodyPrettyPrinted() =
        runTest {
            KtorMalAnimeApi(client(animeBody), config, logger = logger).search(T_SEARCH, 20, 0)

            val text = trace()
            assertTrue("\"title\": \"$T_TITLE\"" in text, text)
        }

    @Test
    fun searchTraceShowsTheStatusDurationAndTheResponseBody() =
        runTest {
            KtorMalAnimeApi(client(animeBody), config, logger = logger).search(T_SEARCH, 20, 0)

            val text = trace()
            assertTrue(Regex("""200(\s+\w+)?\s+\(\d+ ms\)""").containsMatchIn(text), text)
            assertTrue(T_TITLE in text, text)
            assertTrue("application/json" in text, text)
        }

    @Test
    fun detailsTraceShowsThePathAndTheBody() =
        runTest {
            val body = """{"id":21,"title":"$T_TITLE","genres":[],"recommendations":[]}"""

            KtorMalAnimeApi(client(body), config, logger = logger).details(21)

            val text = trace()
            assertTrue("GET /v2/anime/21" in text, text)
            assertTrue("https://api.myanimelist.net/v2/anime/21?" in text, text)
            assertTrue(T_TITLE in text, text)
        }

    @Test
    fun suggestionsTraceShowsTheBearerMasked() =
        runTest {
            KtorMalSuggestionsApi(client(animeBody), harness.auth, logger = logger).suggestions(20, 0)

            val text = trace()
            assertTrue("GET /v2/anime/suggestions" in text, text)
            assertTrue("https://api.myanimelist.net/v2/anime/suggestions?" in text, text)
            assertTrue("Authorization: Bearer ****" in text, text)
            assertTrue(T_TITLE in text, text)
            assertNoSecrets()
        }

    @Test
    fun userTraceShowsTheUsernameAndTheBearerMasked() =
        runTest {
            KtorMalUserApi(client(userBody), harness.auth, logger = logger).me()

            val text = trace()
            assertTrue("GET /v2/users/@me" in text, text)
            assertTrue("https://api.myanimelist.net/v2/users/@me" in text, text)
            assertTrue("Authorization: Bearer ****" in text, text)
            assertTrue(T_USER in text, text)
            assertNoSecrets()
        }

    @Test
    fun tokenExchangeTraceMasksTheFormSecretsAndTheResponseTokens() =
        runTest {
            KtorMalAuthApi(client(tokenBody), config, logger = logger).exchangeCode(T_CODE, T_VERIFIER)

            val text = trace()
            assertTrue("POST /v1/oauth2/token" in text, text)
            assertTrue("https://myanimelist.net/v1/oauth2/token" in text, text)
            assertTrue("grant_type=authorization_code" in text, text)
            assertTrue("client_id=client-1" in text, text)
            assertTrue("code=****" in text, text)
            assertTrue("code_verifier=****" in text, text)
            assertTrue("\"access_token\"" in text, text)
            assertTrue("200" in text, text)
            assertNoSecrets()
        }

    @Test
    fun tokenRefreshTraceMasksTheRefreshTokenAndTheResponseTokens() =
        runTest {
            KtorMalAuthApi(client(tokenBody), config, logger = logger).refresh(T_REFRESH)

            val text = trace()
            assertTrue("grant_type=refresh_token" in text, text)
            assertTrue("refresh_token=****" in text, text)
            assertNoSecrets()
        }

    @Test
    fun theResponseBodyIsTruncatedAtEightKb() =
        runTest {
            val padding = "p".repeat(10_000)
            val body = """{"id":21,"title":"$T_TITLE","genres":[],"recommendations":[],"padding":"$padding"}"""

            KtorMalAnimeApi(client(body), config, logger = logger).details(21)

            val text = trace()
            assertTrue(T_TITLE in text, text)
            assertFalse("p".repeat(9000) in text)
            assertTrue("more chars)" in text, text.takeLast(200))
        }

    @Test
    fun aDebugSinkReceivesNoTraceEntries() =
        runTest {
            val debugSink = RecordingLogSink(minLevel = LogLevel.Debug)
            val debugLogger = SinkLogger(listOf(debugSink))

            KtorMalAnimeApi(client(animeBody), config, logger = debugLogger).search(T_SEARCH, 20, 0)

            assertTrue(debugSink.entries.none { it.level == LogLevel.Trace })
            assertEquals(1, debugSink.entries.size)
        }
}
