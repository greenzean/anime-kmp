package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.data.KtorMalAuthApi
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalTokens
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class KtorMalAuthApiTest {
    private val config = MalConfig("client-1", "https://app.example.test/mal-callback")
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(respond: suspend () -> Pair<HttpStatusCode, String>): KtorMalAuthApi {
        val engine =
            MockEngine { request ->
                requests += request
                val (status, body) = respond()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return KtorMalAuthApi(HttpClient(engine), config)
    }

    private val tokenBody = """{"token_type":"Bearer","expires_in":2678400,"access_token":"a1","refresh_token":"r1"}"""

    @Test
    fun exchangeSendsTheVerifierWithoutASecret() =
        runTest {
            val result = api { HttpStatusCode.OK to tokenBody }.exchangeCode("code-1", "verifier-1")

            assertEquals(Result.Success(MalTokens("a1", "r1", 2_678_400)), result)
            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https://myanimelist.net/v1/oauth2/token", request.url.toString())
            val form =
                request.body
                    .toByteArray()
                    .decodeToString()
                    .parseUrlEncodedParameters()
            assertEquals("authorization_code", form["grant_type"])
            assertEquals("code-1", form["code"])
            assertEquals("verifier-1", form["code_verifier"])
            assertEquals("client-1", form["client_id"])
            assertEquals(config.redirectUri, form["redirect_uri"])
            assertFalse(form.contains("client_secret"))
            assertNull(request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun refreshSendsTheRefreshToken() =
        runTest {
            val result = api { HttpStatusCode.OK to tokenBody }.refresh("old-refresh")

            assertEquals(Result.Success(MalTokens("a1", "r1", 2_678_400)), result)
            val form =
                requests
                    .single()
                    .body
                    .toByteArray()
                    .decodeToString()
                    .parseUrlEncodedParameters()
            assertEquals("refresh_token", form["grant_type"])
            assertEquals("old-refresh", form["refresh_token"])
            assertFalse(form.contains("client_secret"))
        }

    @Test
    fun invalidGrantIsMapped() =
        runTest {
            val result =
                api { HttpStatusCode.BadRequest to """{"error":"invalid_grant","message":"x"}""" }.exchangeCode(
                    "c",
                    "v",
                )

            assertEquals(Result.Error(MalAuthError.INVALID_GRANT), result)
        }

    @Test
    fun refreshRejectionOfAnyKindEndsTheSession() =
        runTest {
            assertEquals(
                Result.Error(MalAuthError.INVALID_GRANT),
                api { HttpStatusCode.Unauthorized to """{"error":"invalid_token"}""" }.refresh("r"),
            )
            assertEquals(
                Result.Error(MalAuthError.INVALID_GRANT),
                api { HttpStatusCode.BadRequest to "" }.refresh("r"),
            )
        }

    @Test
    fun throttleSignalsAreRateLimited() =
        runTest {
            listOf(HttpStatusCode.Forbidden, HttpStatusCode.TemporaryRedirect).forEach { status ->
                assertEquals(
                    Result.Error(MalAuthError.RATE_LIMITED),
                    api { status to "" }.refresh("r"),
                )
            }
        }

    @Test
    fun otherClientErrorsAreUnknown() =
        runTest {
            val result = api { HttpStatusCode.Unauthorized to """{"error":"invalid_client"}""" }.exchangeCode("c", "v")

            assertEquals(Result.Error(MalAuthError.UNKNOWN), result)
        }

    @Test
    fun rateLimitIsMapped() =
        runTest {
            assertEquals(
                Result.Error(MalAuthError.RATE_LIMITED),
                api { HttpStatusCode.TooManyRequests to "" }.refresh("r"),
            )
        }

    @Test
    fun serverErrorIsMapped() =
        runTest {
            assertEquals(
                Result.Error(MalAuthError.SERVER),
                api { HttpStatusCode.BadGateway to "<html>" }.refresh("r"),
            )
        }

    @Test
    fun ioFailureIsNoInternet() =
        runTest {
            assertEquals(
                Result.Error(MalAuthError.NO_INTERNET),
                api { throw IOException("offline") }.refresh("r"),
            )
        }

    @Test
    fun malformedSuccessBodyIsUnknown() =
        runTest {
            assertEquals(
                Result.Error(MalAuthError.UNKNOWN),
                api { HttpStatusCode.OK to "not json" }.refresh("r"),
            )
        }
}
