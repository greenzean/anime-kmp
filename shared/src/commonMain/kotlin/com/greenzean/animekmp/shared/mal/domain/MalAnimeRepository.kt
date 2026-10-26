package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

val MAL_DETAILS_TTL = 24.hours
val MAL_SEARCH_TTL = 1.hours
val MAL_STALE_LIMIT = 30.days
const val MAL_SEARCH_PAGE_SIZE = 20

class MalAnimeRepository(
    private val api: MalAnimeApi,
    private val cache: MalAnimeCache,
    private val throttle: MalThrottle,
    private val clock: Clock = Clock.System,
) {
    suspend fun search(
        query: String,
        pageStart: Int = 0,
    ): Result<MalSearchPage, MalError> {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return Result.Success(MalSearchPage(emptyList(), false))
        return load(
            cached = cache.search(key, pageStart),
            ttl = MAL_SEARCH_TTL,
            fetch = { api.search(key, MAL_SEARCH_PAGE_SIZE, pageStart) },
            save = { page, now -> cache.saveSearch(key, pageStart, page, now) },
        )
    }

    suspend fun details(malId: Int): Result<MalAnimeDetails, MalError> =
        load(
            cached = cache.details(malId),
            ttl = MAL_DETAILS_TTL,
            fetch = { api.details(malId) },
            save = { details, now -> cache.saveDetails(malId, details, now) },
        )

    suspend fun clearCache() = cache.clear()

    private suspend fun <T> load(
        cached: Cached<T>?,
        ttl: Duration,
        fetch: suspend () -> Result<T, MalApiError>,
        save: suspend (T, Instant) -> Unit,
    ): Result<T, MalError> {
        if (cached != null && clock.now() - cached.fetchedAt < ttl) return Result.Success(cached.value)
        return when (val result = throttle.run(fetch)) {
            is Result.Success -> {
                val now = clock.now()
                save(result.data, now)
                cache.deleteFetchedBefore(now - MAL_STALE_LIMIT)
                result
            }

            is Result.Error -> {
                if (result.error == MalError.NoConnection && cached != null) {
                    Result.Success(cached.value)
                } else {
                    result
                }
            }
        }
    }
}
