package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage
import com.greenzean.animekmp.shared.mal.domain.MalSuggestionsApi
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val SUGGESTIONS_NOT_FOUND = 404
private const val SUGGESTIONS_FORBIDDEN = 403
private const val SUGGESTIONS_TOO_MANY_REQUESTS = 429
private const val SUGGESTIONS_REDIRECT_FROM = 300
private const val SUGGESTIONS_REDIRECT_UNTIL = 399
private const val SUGGESTIONS_SERVER_ERROR_FROM = 500

private val suggestionsJson = Json { ignoreUnknownKeys = true }

class KtorMalSuggestionsApi(
    private val client: HttpClient,
    private val auth: MalAuthManager,
) : MalSuggestionsApi {
    override suspend fun suggestions(
        limit: Int,
        offset: Int,
    ): Result<MalSearchPage, MalApiError> {
        val token =
            when (val result = auth.accessToken()) {
                is Result.Success -> result.data
                is Result.Error -> return Result.Error(result.error.toApiError())
            }
        return try {
            val response =
                client.get("$BASE_URL/anime/suggestions") {
                    bearerAuth(token)
                    parameter("limit", limit)
                    parameter("offset", offset)
                    parameter("fields", SEARCH_FIELDS)
                    parameter("nsfw", "false")
                }
            val status = response.status.value
            when {
                response.status.isSuccess() -> {
                    val body = suggestionsJson.decodeFromString<SearchResponse>(response.bodyAsText())
                    Result.Success(MalSearchPage(body.data.map { it.node.toSummary() }, body.paging?.next != null))
                }

                status == SUGGESTIONS_TOO_MANY_REQUESTS ||
                    status in SUGGESTIONS_REDIRECT_FROM..SUGGESTIONS_REDIRECT_UNTIL -> {
                    Result.Error(MalApiError.THROTTLED)
                }

                status == SUGGESTIONS_FORBIDDEN -> {
                    Result.Error(MalApiError.FORBIDDEN)
                }

                status == SUGGESTIONS_NOT_FOUND -> {
                    Result.Error(MalApiError.NOT_FOUND)
                }

                status >= SUGGESTIONS_SERVER_ERROR_FROM -> {
                    Result.Error(MalApiError.SERVER)
                }

                else -> {
                    Result.Error(MalApiError.UNKNOWN)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            Result.Error(MalApiError.NO_CONNECTION)
        } catch (_: SerializationException) {
            Result.Error(MalApiError.SERIALIZATION)
        } catch (_: IllegalArgumentException) {
            Result.Error(MalApiError.SERIALIZATION)
        }
    }
}
