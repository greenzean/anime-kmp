package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.data.KtorMalAnimeApi
import com.greenzean.animekmp.shared.mal.domain.MalAnimeSummary
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalRecommendation
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorMalAnimeApiTest {
    private val config = MalConfig("client-1", "https://app.example.test/mal-callback")
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "{}",
        fail: Throwable? = null,
    ): KtorMalAnimeApi {
        val engine =
            MockEngine { request ->
                requests += request
                if (fail != null) throw fail
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return KtorMalAnimeApi(HttpClient(engine) { followRedirects = false }, config)
    }

    private val searchBody =
        """
        {"data":[
          {"node":{"id":1,"title":"Cowboy Bebop","alternative_titles":{"en":"Cowboy Bebop EN"},"main_picture":{"medium":"m1","large":"l1"},"mean":8.75,
                   "media_type":"tv","num_episodes":26,"status":"finished_airing"}},
          {"node":{"id":2,"title":"Other","alternative_titles":{"en":""}}}
        ],"paging":{"next":"https://api.myanimelist.net/v2/anime?offset=20"}}
        """.trimIndent()

    private val detailsBody =
        """
        {"id":21,"title":"One Piece","main_picture":{"medium":"m","large":"l"},"mean":8.7,"media_type":"tv",
         "num_episodes":0,"status":"currently_airing","synopsis":"Pirates.","genres":[{"id":1,"name":"Action"}],
         "start_date":"1999-10-20","rank":50,"unknown_field":true,
         "recommendations":[{"node":{"id":5,"title":"Naruto","main_picture":{"medium":"nm"}},"num_recommendations":9}]}
        """.trimIndent()

    @Test
    fun searchSendsTheClientIdAndTheAgreedFields() =
        runTest {
            api(body = searchBody).search("bebop", 20, 40)

            val request = requests.single()
            assertEquals("client-1", request.headers["X-MAL-CLIENT-ID"])
            assertEquals("https://api.myanimelist.net/v2/anime", request.url.toString().substringBefore('?'))
            assertEquals("bebop", request.url.parameters["q"])
            assertEquals("20", request.url.parameters["limit"])
            assertEquals("40", request.url.parameters["offset"])
            assertEquals(
                "id,title,alternative_titles,main_picture,mean,media_type,num_episodes,status",
                request.url.parameters["fields"],
            )
            assertEquals("false", request.url.parameters["nsfw"])
        }

    @Test
    fun searchMapsItemsAndPaging() =
        runTest {
            val result = api(body = searchBody).search("bebop", 20, 0)

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
    fun searchWithoutNextLinkHasNoMore() =
        runTest {
            val result = api(body = """{"data":[]}""").search("x", 20, 0)

            assertEquals(Result.Success(MalSearchPage(emptyList(), false)), result)
        }

    @Test
    fun detailsSendsTheClientIdAndTheAgreedFields() =
        runTest {
            api(body = detailsBody).details(21)

            val request = requests.single()
            assertEquals("client-1", request.headers["X-MAL-CLIENT-ID"])
            assertEquals("https://api.myanimelist.net/v2/anime/21", request.url.toString().substringBefore('?'))
            assertEquals(
                "id,title,alternative_titles,main_picture,mean,media_type,num_episodes,status," +
                    "synopsis,genres,start_date,rank,recommendations",
                request.url.parameters["fields"],
            )
        }

    @Test
    fun detailsMapsEveryField() =
        runTest {
            val result = api(body = detailsBody).details(21)

            assertIs<Result.Success<*>>(result)
            val details = (result as Result.Success).data
            assertEquals(MalAnimeSummary(21, "One Piece", "l", 8.7, "tv", 0, "currently_airing"), details.summary)
            assertEquals("Pirates.", details.synopsis)
            assertEquals(listOf("Action"), details.genres)
            assertEquals("1999-10-20", details.startDate)
            assertEquals(50, details.rank)
            assertEquals(listOf(MalRecommendation(5, "Naruto", "nm")), details.recommendations)
        }

    @Test
    fun missingAnimeIsNotFound() =
        runTest {
            val result = api(HttpStatusCode.NotFound, """{"error":"not_found"}""").details(1)

            assertEquals(Result.Error(MalApiError.NOT_FOUND), result)
        }

    @Test
    fun tooManyRequestsIsThrottled() =
        runTest {
            assertEquals(Result.Error(MalApiError.THROTTLED), api(HttpStatusCode.TooManyRequests).details(1))
        }

    @Test
    fun redirectToTheErrorPageIsThrottledAndNotFollowed() =
        runTest {
            val result = api(HttpStatusCode.TemporaryRedirect).details(1)

            assertEquals(Result.Error(MalApiError.THROTTLED), result)
            assertEquals(1, requests.size)
        }

    @Test
    fun forbiddenIsReportedSeparately() =
        runTest {
            assertEquals(Result.Error(MalApiError.FORBIDDEN), api(HttpStatusCode.Forbidden).search("abc", 20, 0))
        }

    @Test
    fun serverErrorIsServer() =
        runTest {
            assertEquals(Result.Error(MalApiError.SERVER), api(HttpStatusCode.BadGateway).details(1))
        }

    @Test
    fun otherClientErrorIsUnknown() =
        runTest {
            assertEquals(Result.Error(MalApiError.UNKNOWN), api(HttpStatusCode.BadRequest).search("ab", 20, 0))
        }

    @Test
    fun networkFailureIsNoConnection() =
        runTest {
            assertEquals(Result.Error(MalApiError.NO_CONNECTION), api(fail = IOException("offline")).details(1))
        }

    @Test
    fun malformedBodyIsSerialization() =
        runTest {
            assertEquals(Result.Error(MalApiError.SERIALIZATION), api(body = "not json").details(1))
        }
}
