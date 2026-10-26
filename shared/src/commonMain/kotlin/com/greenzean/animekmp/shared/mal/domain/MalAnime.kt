package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Error
import com.greenzean.animekmp.shared.core.domain.Result
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class MalAnimeSummary(
    val malId: Int,
    val title: String,
    val imageUrl: String?,
    val mean: Double?,
    val mediaType: String?,
    val numEpisodes: Int?,
    val status: String?,
)

@Serializable
data class MalRecommendation(
    val malId: Int,
    val title: String,
    val imageUrl: String?,
)

@Serializable
data class MalAnimeDetails(
    val summary: MalAnimeSummary,
    val synopsis: String?,
    val genres: List<String>,
    val startDate: String?,
    val rank: Int?,
    val recommendations: List<MalRecommendation>,
)

@Serializable
data class MalSearchPage(
    val items: List<MalAnimeSummary>,
    val hasMore: Boolean,
)

enum class MalApiError : Error {
    NO_CONNECTION,
    NOT_FOUND,
    THROTTLED,
    FORBIDDEN,
    SERVER,
    SERIALIZATION,
    UNKNOWN,
}

sealed interface MalError : Error {
    data object NoConnection : MalError

    data object NotFound : MalError

    data object Server : MalError

    data object Serialization : MalError

    data object Unknown : MalError

    data class Throttled(
        val retryAt: Instant,
    ) : MalError

    data class Blocked(
        val until: Instant,
    ) : MalError
}

interface MalAnimeApi {
    suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
    ): Result<MalSearchPage, MalApiError>

    suspend fun details(malId: Int): Result<MalAnimeDetails, MalApiError>
}

data class Cached<out T>(
    val value: T,
    val fetchedAt: Instant,
)

interface MalAnimeCache {
    suspend fun details(malId: Int): Cached<MalAnimeDetails>?

    suspend fun saveDetails(
        malId: Int,
        details: MalAnimeDetails,
        fetchedAt: Instant,
    )

    suspend fun search(
        queryKey: String,
        pageStart: Int,
    ): Cached<MalSearchPage>?

    suspend fun saveSearch(
        queryKey: String,
        pageStart: Int,
        page: MalSearchPage,
        fetchedAt: Instant,
    )

    suspend fun deleteFetchedBefore(threshold: Instant)

    suspend fun clear()
}
