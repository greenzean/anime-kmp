package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.data.KtorMalUserApi
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalUser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorMalUserApiTest {
    private val harness = MalAccountHarness(validMalSession())
    private val requests = mutableListOf<HttpRequestData>()

    @AfterTest
    fun tearDown() = harness.close()

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = """{"id":7,"name":"francisco"}""",
        fail: Throwable? = null,
    ): KtorMalUserApi {
        val engine =
            MockEngine { request ->
                requests += request
                if (fail != null) throw fail
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return KtorMalUserApi(HttpClient(engine) { followRedirects = false }, harness.auth)
    }

    @Test
    fun meRequestsTheCurrentUserWithTheBearerToken() =
        runTest {
            api().me()

            val request = requests.single()
            assertEquals("https://api.myanimelist.net/v2/users/@me", request.url.toString().substringBefore('?'))
            assertEquals("Bearer stored-access", request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun meMapsTheName() =
        runTest {
            val api = api(body = """{"id":7,"name":"francisco","extra":1}""")
            assertEquals(Result.Success(MalUser("francisco")), api.me())
        }

    @Test
    fun unauthorizedIsAnError() =
        runTest {
            assertEquals(Result.Error(MalApiError.UNKNOWN), api(HttpStatusCode.Unauthorized).me())
        }

    @Test
    fun forbiddenIsForbidden() =
        runTest {
            assertEquals(Result.Error(MalApiError.FORBIDDEN), api(HttpStatusCode.Forbidden).me())
        }

    @Test
    fun tooManyRequestsIsThrottled() =
        runTest {
            assertEquals(Result.Error(MalApiError.THROTTLED), api(HttpStatusCode.TooManyRequests).me())
        }

    @Test
    fun serverErrorIsServer() =
        runTest {
            assertEquals(Result.Error(MalApiError.SERVER), api(HttpStatusCode.BadGateway).me())
        }

    @Test
    fun networkFailureIsNoConnection() =
        runTest {
            assertEquals(Result.Error(MalApiError.NO_CONNECTION), api(fail = IOException("offline")).me())
        }

    @Test
    fun malformedBodyIsSerialization() =
        runTest {
            assertEquals(Result.Error(MalApiError.SERIALIZATION), api(body = "not json").me())
        }

    @Test
    fun withoutASessionNoRequestIsSent() =
        runTest {
            val loggedOut = MalAccountHarness()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond("{}", HttpStatusCode.OK)
                }

            val result = KtorMalUserApi(HttpClient(engine), loggedOut.auth).me()

            assertEquals(0, requests.size)
            assertEquals(true, result is Result.Error)
            loggedOut.close()
        }
}
