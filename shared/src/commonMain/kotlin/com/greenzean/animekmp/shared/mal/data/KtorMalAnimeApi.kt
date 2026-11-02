package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalAnimeApi
import com.greenzean.animekmp.shared.mal.domain.MalAnimeDetails
import com.greenzean.animekmp.shared.mal.domain.MalAnimeSummary
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalRecommendation
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal const val BASE_URL = "https://api.myanimelist.net/v2"
private const val CLIENT_ID_HEADER = "X-MAL-CLIENT-ID"
internal const val SEARCH_FIELDS = "id,title,alternative_titles,main_picture,mean,media_type,num_episodes,status"
private const val DETAILS_FIELDS =
    "id,title,alternative_titles,main_picture,mean,media_type,num_episodes,status,synopsis,genres,start_date,rank," +
        "recommendations"
private const val NOT_FOUND = 404
private const val FORBIDDEN = 403
private const val TOO_MANY_REQUESTS = 429
private const val REDIRECT_FROM = 300
private const val REDIRECT_UNTIL = 399
private const val SERVER_ERROR_FROM = 500

private val json = Json { ignoreUnknownKeys = true }

@Serializable
internal data class PictureDto(
    val medium: String? = null,
    val large: String? = null,
)

@Serializable
internal data class AlternativeTitlesDto(
    val en: String? = null,
)

@Serializable
internal data class GenreDto(
    val name: String,
)

@Serializable
internal data class RecommendationNodeDto(
    val id: Int,
    val title: String,
    @SerialName("main_picture") val mainPicture: PictureDto? = null,
)

@Serializable
internal data class RecommendationDto(
    val node: RecommendationNodeDto,
)

@Serializable
internal data class AnimeDto(
    val id: Int,
    val title: String,
    @SerialName("alternative_titles") val alternativeTitles: AlternativeTitlesDto? = null,
    @SerialName("main_picture") val mainPicture: PictureDto? = null,
    val mean: Double? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("num_episodes") val numEpisodes: Int? = null,
    val status: String? = null,
    val synopsis: String? = null,
    val genres: List<GenreDto> = emptyList(),
    @SerialName("start_date") val startDate: String? = null,
    val rank: Int? = null,
    val recommendations: List<RecommendationDto> = emptyList(),
)

@Serializable
internal data class SearchItemDto(
    val node: AnimeDto,
)

@Serializable
internal data class PagingDto(
    val next: String? = null,
)

@Serializable
internal data class SearchResponse(
    val data: List<SearchItemDto> = emptyList(),
    val paging: PagingDto? = null,
)

private fun PictureDto?.url(): String? = this?.large ?: this?.medium

internal fun AnimeDto.toSummary() =
    MalAnimeSummary(
        malId = id,
        title = title,
        titleEn = alternativeTitles?.en?.takeIf { it.isNotBlank() },
        imageUrl = mainPicture.url(),
        mean = mean,
        mediaType = mediaType,
        numEpisodes = numEpisodes,
        status = status,
    )

private fun AnimeDto.toDetails() =
    MalAnimeDetails(
        summary = toSummary(),
        synopsis = synopsis,
        genres = genres.map { it.name },
        startDate = startDate,
        rank = rank,
        recommendations =
            recommendations.map {
                MalRecommendation(it.node.id, it.node.title, it.node.mainPicture.url())
            },
    )

class KtorMalAnimeApi(
    private val client: HttpClient,
    private val config: MalConfig,
) : MalAnimeApi {
    override suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
    ): Result<MalSearchPage, MalApiError> =
        call(
            request = {
                client.get("$BASE_URL/anime") {
                    header(CLIENT_ID_HEADER, config.clientId)
                    parameter("q", query)
                    parameter("limit", limit)
                    parameter("offset", offset)
                    parameter("fields", SEARCH_FIELDS)
                    parameter("nsfw", "false")
                }
            },
            parse = { body ->
                val response = json.decodeFromString<SearchResponse>(body)
                MalSearchPage(response.data.map { it.node.toSummary() }, response.paging?.next != null)
            },
        )

    override suspend fun details(malId: Int): Result<MalAnimeDetails, MalApiError> =
        call(
            request = {
                client.get("$BASE_URL/anime/$malId") {
                    header(CLIENT_ID_HEADER, config.clientId)
                    parameter("fields", DETAILS_FIELDS)
                }
            },
            parse = { body -> json.decodeFromString<AnimeDto>(body).toDetails() },
        )

    private suspend fun <T> call(
        request: suspend () -> HttpResponse,
        parse: (String) -> T,
    ): Result<T, MalApiError> =
        try {
            val response = request()
            val status = response.status.value
            when {
                response.status.isSuccess() -> {
                    Result.Success(parse(response.bodyAsText()))
                }

                status == TOO_MANY_REQUESTS || status in REDIRECT_FROM..REDIRECT_UNTIL -> {
                    Result.Error(
                        MalApiError.THROTTLED,
                    )
                }

                status == FORBIDDEN -> {
                    Result.Error(MalApiError.FORBIDDEN)
                }

                status == NOT_FOUND -> {
                    Result.Error(MalApiError.NOT_FOUND)
                }

                status >= SERVER_ERROR_FROM -> {
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
