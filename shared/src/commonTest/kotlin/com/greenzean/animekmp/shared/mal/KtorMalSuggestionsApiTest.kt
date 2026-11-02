package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.data.KtorMalSuggestionsApi
import com.greenzean.animekmp.shared.mal.domain.MalAnimeSummary
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage
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
import kotlin.test.assertTrue

class KtorMalSuggestionsApiTest {
    private val harness = MalAccountHarness(validMalSession())
    private val requests = mutableListOf<HttpRequestData>()

    private val body =
        """
        {"data":[
          {"node":{"id":1,"title":"Cowboy Bebop","alternative_titles":{"en":"Cowboy Bebop EN"},"main_picture":{"medium":"m1","large":"l1"},"mean":8.75,
                   "media_type":"tv","num_episodes":26,"status":"finished_airing"}},
          {"node":{"id":2,"title":"Other","alternative_titles":{"en":""}}}
        ],"paging":{"next":"https://api.myanimelist.net/v2/anime/suggestions?offset=20"}}
        """.trimIndent()

    @AfterTest
    fun tearDown() = harness.close()

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = body,
        fail: Throwable? = null,
        auth: MalAccountHarness = harness,
    ): KtorMalSuggestionsApi {
        val engine =
            MockEngine { request ->
                requests += request
                if (fail != null) throw fail
                respond(responseBody, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return KtorMalSuggestionsApi(HttpClient(engine) { followRedirects = false }, auth.auth)
    }

    @Test
    fun requestsTheSuggestionsPathWithTheBearerToken() =
        runTest {
            api().suggestions(20, 40)

            val request = requests.single()
            assertEquals(
                "https://api.myanimelist.net/v2/anime/suggestions",
                request.url.toString().substringBefore('?'),
            )
            assertEquals("Bearer stored-access", request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun sendsLimitOffsetAndTheSearchFields() =
        runTest {
            api().suggestions(20, 40)

            val request = requests.single()
            assertEquals("20", request.url.parameters["limit"])
            assertEquals("40", request.url.parameters["offset"])
            assertEquals(
                "id,title,alternative_titles,main_picture,mean,media_type,num_episodes,status",
                request.url.parameters["fields"],
            )
        }

    @Test
    fun mapsItemsAndPagingNextToHasMore() =
        runTest {
            val result = api().suggestions(20, 0)

            val expected =
                MalSearchPage(
                    items =
                        listOf(
                            MalAnimeSummary(
                                1,
                                "Cowboy Bebop",
                                "l1",
                                8.75,
                                "tv",
                                26,
                                "finished_airing",
                                "Cowboy Bebop EN",
                            ),
                            MalAnimeSummary(2, "Other", null, null, null, null, null),
                        ),
                    hasMore = true,
                )
            assertEquals(Result.Success(expected), result)
        }

    @Test
    fun withoutNextLinkThereIsNoMore() =
        runTest {
            val result = api(responseBody = """{"data":[{"node":{"id":2,"title":"Other"}}]}""").suggestions(20, 0)

            assertEquals(false, (result as Result.Success).data.hasMore)
        }

    @Test
    fun emptyDataIsAnEmptyPage() =
        runTest {
            val result = api(responseBody = """{"data":[]}""").suggestions(20, 0)

            assertEquals(Result.Success(MalSearchPage(emptyList(), false)), result)
        }

    @Test
    fun unauthorizedIsAnError() =
        runTest {
            assertEquals(Result.Error(MalApiError.UNKNOWN), api(HttpStatusCode.Unauthorized).suggestions(20, 0))
        }

    @Test
    fun forbiddenIsForbidden() =
        runTest {
            assertEquals(Result.Error(MalApiError.FORBIDDEN), api(HttpStatusCode.Forbidden).suggestions(20, 0))
        }

    @Test
    fun tooManyRequestsIsThrottled() =
        runTest {
            assertEquals(Result.Error(MalApiError.THROTTLED), api(HttpStatusCode.TooManyRequests).suggestions(20, 0))
        }

    @Test
    fun redirectIsThrottled() =
        runTest {
            assertEquals(Result.Error(MalApiError.THROTTLED), api(HttpStatusCode.Found).suggestions(20, 0))
        }

    @Test
    fun serverErrorIsServer() =
        runTest {
            assertEquals(Result.Error(MalApiError.SERVER), api(HttpStatusCode.BadGateway).suggestions(20, 0))
        }

    @Test
    fun networkFailureIsNoConnection() =
        runTest {
            assertEquals(Result.Error(MalApiError.NO_CONNECTION), api(fail = IOException("offline")).suggestions(20, 0))
        }

    @Test
    fun malformedBodyIsSerialization() =
        runTest {
            assertEquals(Result.Error(MalApiError.SERIALIZATION), api(responseBody = "not json").suggestions(20, 0))
        }

    @Test
    fun withoutASessionNoRequestIsSent() =
        runTest {
            val loggedOut = MalAccountHarness()

            val result = api(auth = loggedOut).suggestions(20, 0)

            assertEquals(0, requests.size)
            assertTrue(result is Result.Error)
            loggedOut.close()
        }
}
