package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.domain.MalExportEntry
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.data.KtorMalListApi
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalListItem
import com.greenzean.animekmp.shared.mal.domain.MalListPage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorMalListApiTest {
    private val harness = MalAccountHarness(validMalSession())
    private val requests = mutableListOf<HttpRequestData>()

    @AfterTest
    fun tearDown() = harness.close()

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = pageBody,
        fail: Throwable? = null,
    ): KtorMalListApi {
        val engine =
            MockEngine { request ->
                requests += request
                if (fail != null) throw fail
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return KtorMalListApi(HttpClient(engine) { followRedirects = false }, harness.auth)
    }

    private fun itemBody(
        id: Int,
        status: String,
        score: Int = 0,
        watched: Int = 0,
        episodes: Int = 12,
        picture: String = ""","main_picture":{"medium":"m","large":"https://img.example/$id.jpg"}""",
    ) = """{"node":{"id":$id,"title":"Title $id"$picture,"num_episodes":$episodes},""" +
        """"list_status":{"status":"$status","score":$score,"num_episodes_watched":$watched,"is_rewatching":false}}"""

    private val pageBody =
        """
        {"data":[
          ${itemBody(1, "completed", score = 9, watched = 26, episodes = 26)},
          ${itemBody(2, "watching", score = 0, watched = 3)}
        ],"paging":{"next":"https://api.myanimelist.net/v2/users/@me/animelist?offset=100&limit=100"}}
        """.trimIndent()

    private suspend fun items(body: String): List<MalListItem> =
        (api(body = body).page(0, 100) as Result.Success).data.items

    @Test
    fun pageRequestsTheOwnListWithTheBearerTokenAndFields() =
        runTest {
            api().page(offset = 200, limit = 100)

            val request = requests.single()
            assertEquals(
                "https://api.myanimelist.net/v2/users/@me/animelist",
                request.url.toString().substringBefore('?'),
            )
            assertEquals("Bearer stored-access", request.headers[HttpHeaders.Authorization])
            assertEquals("list_status", request.url.parameters["fields"])
            assertEquals("100", request.url.parameters["limit"])
            assertEquals("200", request.url.parameters["offset"])
            assertEquals("true", request.url.parameters["nsfw"])
        }

    @Test
    fun pageMapsItemsAndHasNext() =
        runTest {
            val result = api().page(0, 100)

            val expected =
                MalListPage(
                    listOf(
                        MalListItem(
                            MalExportEntry(1, "Title 1", 26, WatchStatus.COMPLETED, 26, 9),
                            "https://img.example/1.jpg",
                        ),
                        MalListItem(
                            MalExportEntry(2, "Title 2", 12, WatchStatus.WATCHING, 3, null),
                            "https://img.example/2.jpg",
                        ),
                    ),
                    hasNext = true,
                )
            assertEquals(Result.Success(expected), result)
        }

    @Test
    fun missingPagingNextMeansNoNextPage() =
        runTest {
            val body = """{"data":[${itemBody(1, "completed")}],"paging":{}}"""

            val page = (api(body = body).page(0, 100) as Result.Success).data

            assertEquals(false, page.hasNext)
            assertEquals(1, page.items.size)
        }

    @Test
    fun missingPagingObjectMeansNoNextPage() =
        runTest {
            val page = (api(body = """{"data":[${itemBody(1, "completed")}]}""").page(0, 100) as Result.Success).data

            assertEquals(false, page.hasNext)
        }

    @Test
    fun emptyListIsAnEmptyPage() =
        runTest {
            val result = api(body = """{"data":[],"paging":{}}""").page(0, 100)

            assertEquals(Result.Success(MalListPage(emptyList(), hasNext = false)), result)
        }

    @Test
    fun everyListStatusMapsToTheAppStatus() =
        runTest {
            val body =
                """{"data":[
                    ${itemBody(1, "watching")},
                    ${itemBody(2, "completed")},
                    ${itemBody(3, "on_hold")},
                    ${itemBody(4, "dropped")},
                    ${itemBody(5, "plan_to_watch")}
                ],"paging":{}}"""

            assertEquals(
                listOf(
                    WatchStatus.WATCHING,
                    WatchStatus.COMPLETED,
                    WatchStatus.ON_HOLD,
                    WatchStatus.DROPPED,
                    WatchStatus.PLAN_TO_WATCH,
                ),
                items(body).map { it.entry.status },
            )
        }

    @Test
    fun scoreZeroMeansNoScore() =
        runTest {
            val mapped = items("""{"data":[${itemBody(1, "watching", score = 0)}],"paging":{}}""")

            assertNull(mapped.single().entry.score)
        }

    @Test
    fun nonZeroScoreIsKept() =
        runTest {
            val mapped = items("""{"data":[${itemBody(1, "watching", score = 7)}],"paging":{}}""")

            assertEquals(7, mapped.single().entry.score)
        }

    @Test
    fun missingPictureMeansNoImage() =
        runTest {
            val mapped = items("""{"data":[${itemBody(1, "watching", picture = "")}],"paging":{}}""")

            assertNull(mapped.single().imageUrl)
        }

    @Test
    fun unknownFieldsAreIgnored() =
        runTest {
            val body =
                """{"data":[{"node":{"id":1,"title":"A","extra":1,"num_episodes":2},""" +
                    """"list_status":{"status":"dropped","score":2,"num_episodes_watched":1,"updated_at":"x"}}],""" +
                    """"paging":{},"season":{"year":2020}}"""

            assertEquals(1, items(body).size)
        }

    @Test
    fun unauthorizedIsAnError() =
        runTest {
            assertEquals(Result.Error(MalApiError.UNKNOWN), api(HttpStatusCode.Unauthorized).page(0, 100))
        }

    @Test
    fun forbiddenIsForbidden() =
        runTest {
            assertEquals(Result.Error(MalApiError.FORBIDDEN), api(HttpStatusCode.Forbidden).page(0, 100))
        }

    @Test
    fun tooManyRequestsIsThrottled() =
        runTest {
            assertEquals(Result.Error(MalApiError.THROTTLED), api(HttpStatusCode.TooManyRequests).page(0, 100))
        }

    @Test
    fun serverErrorIsServer() =
        runTest {
            assertEquals(Result.Error(MalApiError.SERVER), api(HttpStatusCode.InternalServerError).page(0, 100))
        }

    @Test
    fun networkFailureIsNoConnection() =
        runTest {
            assertEquals(Result.Error(MalApiError.NO_CONNECTION), api(fail = IOException("offline")).page(0, 100))
        }

    @Test
    fun malformedBodyIsSerialization() =
        runTest {
            assertEquals(Result.Error(MalApiError.SERIALIZATION), api(body = "not json").page(0, 100))
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

            val result = KtorMalListApi(HttpClient(engine), loggedOut.auth).page(0, 100)

            assertEquals(0, requests.size)
            assertTrue(result is Result.Error)
            loggedOut.close()
        }

    private fun form(request: HttpRequestData): Parameters =
        when (val body = request.body) {
            is FormDataContent -> body.formData
            is TextContent -> body.text.parseUrlEncodedParameters()
            else -> error("unexpected body ${body::class}")
        }

    @Test
    fun updatePatchesTheListStatusOfThatAnimeWithTheBearerToken() =
        runTest {
            api(body = "{}").update(52991, WatchStatus.WATCHING, 4, 8)

            val request = requests.single()
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals(
                "https://api.myanimelist.net/v2/anime/52991/my_list_status",
                request.url.toString().substringBefore('?'),
            )
            assertEquals("Bearer stored-access", request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun updateSendsAFormEncodedBody() =
        runTest {
            api(body = "{}").update(1, WatchStatus.COMPLETED, 26, 9)

            val request = requests.single()
            assertEquals(ContentType.Application.FormUrlEncoded, request.body.contentType?.withoutParameters())
            val sent = form(request)
            assertEquals("completed", sent["status"])
            assertEquals("26", sent["num_watched_episodes"])
            assertEquals("9", sent["score"])
        }

    @Test
    fun updateSendsScoreZeroWhenThereIsNoScore() =
        runTest {
            api(body = "{}").update(1, WatchStatus.PLAN_TO_WATCH, 0, null)

            val sent = form(requests.single())
            assertEquals("0", sent["score"])
            assertEquals("plan_to_watch", sent["status"])
            assertEquals("0", sent["num_watched_episodes"])
        }

    @Test
    fun updateMapsEveryStatusToTheMalString() =
        runTest {
            val expected =
                mapOf(
                    WatchStatus.WATCHING to "watching",
                    WatchStatus.COMPLETED to "completed",
                    WatchStatus.ON_HOLD to "on_hold",
                    WatchStatus.DROPPED to "dropped",
                    WatchStatus.PLAN_TO_WATCH to "plan_to_watch",
                )

            expected.keys.forEach { api(body = "{}").update(1, it, 1, null) }

            assertEquals(expected.values.toList(), requests.map { form(it)["status"] })
        }

    @Test
    fun updateSucceedsOnOk() =
        runTest {
            assertEquals(Result.Success(Unit), api(body = "{}").update(1, WatchStatus.WATCHING, 1, null))
        }

    @Test
    fun updateOfAnimeMalDoesNotKnowIsNotFound() =
        runTest {
            val result = api(HttpStatusCode.NotFound).update(1, WatchStatus.WATCHING, 1, null)

            assertEquals(Result.Error(MalApiError.NOT_FOUND), result)
        }

    @Test
    fun updateErrorsMapLikeTheListErrors() =
        runTest {
            assertEquals(
                Result.Error(MalApiError.THROTTLED),
                api(HttpStatusCode.TooManyRequests).update(1, WatchStatus.WATCHING, 1, null),
            )
            assertEquals(
                Result.Error(MalApiError.FORBIDDEN),
                api(HttpStatusCode.Forbidden).update(1, WatchStatus.WATCHING, 1, null),
            )
            assertEquals(
                Result.Error(MalApiError.SERVER),
                api(HttpStatusCode.InternalServerError).update(1, WatchStatus.WATCHING, 1, null),
            )
            assertEquals(
                Result.Error(MalApiError.NO_CONNECTION),
                api(fail = IOException("offline")).update(1, WatchStatus.WATCHING, 1, null),
            )
        }

    @Test
    fun updateWithoutASessionSendsNothing() =
        runTest {
            val loggedOut = MalAccountHarness()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond("{}", HttpStatusCode.OK)
                }

            val result = KtorMalListApi(HttpClient(engine), loggedOut.auth).update(1, WatchStatus.WATCHING, 1, null)

            assertEquals(0, requests.size)
            assertTrue(result is Result.Error)
            loggedOut.close()
        }

    @Test
    fun deleteSendsDeleteToTheListStatusOfThatAnime() =
        runTest {
            api(body = "").delete(52991)

            val request = requests.single()
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals(
                "https://api.myanimelist.net/v2/anime/52991/my_list_status",
                request.url.toString().substringBefore('?'),
            )
            assertEquals("Bearer stored-access", request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun deleteSucceedsOnOkWithAnEmptyBody() =
        runTest {
            assertEquals(Result.Success(Unit), api(body = "").delete(1))
        }

    @Test
    fun deleteOfAnAnimeNotInTheMalListIsNotFound() =
        runTest {
            assertEquals(Result.Error(MalApiError.NOT_FOUND), api(HttpStatusCode.NotFound).delete(1))
        }

    @Test
    fun deleteErrorsMapLikeTheListErrors() =
        runTest {
            assertEquals(Result.Error(MalApiError.THROTTLED), api(HttpStatusCode.TooManyRequests).delete(1))
            assertEquals(Result.Error(MalApiError.FORBIDDEN), api(HttpStatusCode.Forbidden).delete(1))
            assertEquals(Result.Error(MalApiError.SERVER), api(HttpStatusCode.InternalServerError).delete(1))
            assertEquals(Result.Error(MalApiError.NO_CONNECTION), api(fail = IOException("offline")).delete(1))
        }

    @Test
    fun deleteWithoutASessionSendsNothing() =
        runTest {
            val loggedOut = MalAccountHarness()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond("", HttpStatusCode.OK)
                }

            val result = KtorMalListApi(HttpClient(engine), loggedOut.auth).delete(1)

            assertEquals(0, requests.size)
            assertTrue(result is Result.Error)
            loggedOut.close()
        }
}
