package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.core.logging.AppLogger
import com.greenzean.animekmp.shared.core.logging.NoOpAppLogger
import com.greenzean.animekmp.shared.importer.domain.MalExportEntry
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalListApi
import com.greenzean.animekmp.shared.mal.domain.MalListItem
import com.greenzean.animekmp.shared.mal.domain.MalListPage
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

private const val LIST_URL = "https://api.myanimelist.net/v2/users/@me/animelist"
private const val ANIME_URL = "https://api.myanimelist.net/v2/anime"
private const val LIST_PATH = "/v2/users/@me/animelist"
private const val LIST_NOT_FOUND = 404
private const val LIST_FORBIDDEN = 403
private const val LIST_TOO_MANY_REQUESTS = 429
private const val LIST_REDIRECT_FROM = 300
private const val LIST_REDIRECT_UNTIL = 399
private const val LIST_SERVER_ERROR_FROM = 500

private val listJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class ListNodeDto(
    val id: Int,
    val title: String,
    @SerialName("main_picture") val mainPicture: PictureDto? = null,
    @SerialName("num_episodes") val numEpisodes: Int? = null,
)

@Serializable
internal data class ListStatusDto(
    val status: String? = null,
    val score: Int = 0,
    @SerialName("num_episodes_watched") val numEpisodesWatched: Int = 0,
)

@Serializable
internal data class ListItemDto(
    val node: ListNodeDto,
    @SerialName("list_status") val listStatus: ListStatusDto? = null,
)

@Serializable
internal data class ListResponse(
    val data: List<ListItemDto> = emptyList(),
    val paging: PagingDto? = null,
)

internal fun WatchStatus.toMalApiStatus(): String =
    when (this) {
        WatchStatus.WATCHING -> "watching"
        WatchStatus.COMPLETED -> "completed"
        WatchStatus.ON_HOLD -> "on_hold"
        WatchStatus.DROPPED -> "dropped"
        WatchStatus.PLAN_TO_WATCH -> "plan_to_watch"
    }

internal fun String?.toWatchStatus(): WatchStatus? =
    when (this) {
        "watching" -> WatchStatus.WATCHING
        "completed" -> WatchStatus.COMPLETED
        "on_hold" -> WatchStatus.ON_HOLD
        "dropped" -> WatchStatus.DROPPED
        "plan_to_watch" -> WatchStatus.PLAN_TO_WATCH
        else -> null
    }

private val ScoreRange = 1..10

internal fun ListItemDto.toListItem(): MalListItem? {
    val listStatus = listStatus
    val status = listStatus?.status.toWatchStatus()
    val valid = listStatus != null && status != null && node.id > 0 && node.title.isNotBlank()
    return if (valid) {
        MalListItem(
            entry =
                MalExportEntry(
                    malId = node.id,
                    title = node.title,
                    episodes = node.numEpisodes?.takeIf { it > 0 },
                    status = status,
                    progress = listStatus.numEpisodesWatched.coerceAtLeast(0),
                    score = listStatus.score.takeIf { it in ScoreRange },
                ),
            imageUrl = node.mainPicture?.let { it.large ?: it.medium },
        )
    } else {
        null
    }
}

class KtorMalListApi(
    private val client: HttpClient,
    private val auth: MalAuthManager,
    private val logger: AppLogger = NoOpAppLogger,
) : MalListApi {
    override suspend fun update(
        malId: Int,
        status: WatchStatus,
        progress: Int,
        score: Int?,
    ): Result<Unit, MalApiError> {
        val parameters =
            Parameters.build {
                append("status", status.toMalApiStatus())
                append("num_watched_episodes", progress.toString())
                append("score", (score ?: 0).toString())
            }
        return write(HttpMethod.Patch, malId, parameters.formUrlEncode()) { setBody(FormDataContent(parameters)) }
    }

    override suspend fun delete(malId: Int): Result<Unit, MalApiError> = write(HttpMethod.Delete, malId, null) { }

    override suspend fun page(
        offset: Int,
        limit: Int,
    ): Result<MalListPage, MalApiError> {
        val token =
            when (val result = auth.accessToken()) {
                is Result.Success -> result.data
                is Result.Error -> return Result.Error(result.error.toApiError())
            }
        val trace = MalCallTrace(logger, "GET", LIST_PATH)
        return try {
            val started = TimeSource.Monotonic.markNow()
            val response =
                client.get(LIST_URL) {
                    bearerAuth(token)
                    parameter("fields", "list_status")
                    parameter("limit", limit)
                    parameter("offset", offset)
                    parameter("nsfw", "true")
                    trace.request(this)
                }
            val status = response.status.value
            logger.logHttpResponse("GET", LIST_PATH, status, started)
            val body = trace.complete(response)
            when {
                response.status.isSuccess() -> {
                    val parsed = listJson.decodeFromString<ListResponse>(body)
                    Result.Success(MalListPage(parsed.data.mapNotNull { it.toListItem() }, parsed.paging?.next != null))
                }

                status == LIST_TOO_MANY_REQUESTS || status in LIST_REDIRECT_FROM..LIST_REDIRECT_UNTIL -> {
                    Result.Error(MalApiError.THROTTLED)
                }

                status == LIST_FORBIDDEN -> {
                    Result.Error(MalApiError.FORBIDDEN)
                }

                status == LIST_NOT_FOUND -> {
                    Result.Error(MalApiError.NOT_FOUND)
                }

                status >= LIST_SERVER_ERROR_FROM -> {
                    Result.Error(MalApiError.SERVER)
                }

                else -> {
                    Result.Error(MalApiError.UNKNOWN)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            failure("GET", LIST_PATH, MalApiError.NO_CONNECTION, e, trace)
        } catch (e: SerializationException) {
            failure("GET", LIST_PATH, MalApiError.SERIALIZATION, e, trace)
        } catch (e: IllegalArgumentException) {
            failure("GET", LIST_PATH, MalApiError.SERIALIZATION, e, trace)
        }
    }

    private suspend fun write(
        method: HttpMethod,
        malId: Int,
        traceBody: String?,
        configure: HttpRequestBuilder.() -> Unit,
    ): Result<Unit, MalApiError> {
        val token =
            when (val result = auth.accessToken()) {
                is Result.Success -> result.data
                is Result.Error -> return Result.Error(result.error.toApiError())
            }
        val verb = method.value
        val path = "/v2/anime/$malId/my_list_status"
        val trace = MalCallTrace(logger, verb, path)
        return try {
            val started = TimeSource.Monotonic.markNow()
            val response =
                client.request("$ANIME_URL/$malId/my_list_status") {
                    this.method = method
                    bearerAuth(token)
                    configure()
                    trace.request(this, traceBody)
                }
            val status = response.status.value
            logger.logHttpResponse(verb, path, status, started)
            trace.complete(response)
            when {
                response.status.isSuccess() -> {
                    Result.Success(Unit)
                }

                status == LIST_TOO_MANY_REQUESTS || status in LIST_REDIRECT_FROM..LIST_REDIRECT_UNTIL -> {
                    Result.Error(MalApiError.THROTTLED)
                }

                status == LIST_FORBIDDEN -> {
                    Result.Error(MalApiError.FORBIDDEN)
                }

                status == LIST_NOT_FOUND -> {
                    Result.Error(MalApiError.NOT_FOUND)
                }

                status >= LIST_SERVER_ERROR_FROM -> {
                    Result.Error(MalApiError.SERVER)
                }

                else -> {
                    Result.Error(MalApiError.UNKNOWN)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            failure(verb, path, MalApiError.NO_CONNECTION, e, trace)
        }
    }

    private fun failure(
        method: String,
        path: String,
        error: MalApiError,
        cause: Throwable,
        trace: MalCallTrace,
    ): Result<Nothing, MalApiError> {
        trace.failed(cause)
        logger.logHttpFailure(method, path, error, cause)
        return Result.Error(error)
    }
}
