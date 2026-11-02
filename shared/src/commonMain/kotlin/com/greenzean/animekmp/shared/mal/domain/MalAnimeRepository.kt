package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.core.logging.AppLogger
import com.greenzean.animekmp.shared.core.logging.NoOpAppLogger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

val MAL_DETAILS_TTL = 24.hours
val MAL_SEARCH_TTL = 1.hours
val MAL_STALE_LIMIT = 30.days
const val MAL_SEARCH_PAGE_SIZE = 20
private const val CACHE_TAG = "MalCache"

class MalAnimeRepository(
    private val api: MalAnimeApi,
    private val cache: MalAnimeCache,
    private val throttle: MalThrottle,
    private val clock: Clock = Clock.System,
    private val logger: AppLogger = NoOpAppLogger,
) {
    suspend fun search(
        query: String,
        pageStart: Int = 0,
    ): Result<MalSearchPage, MalError> {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return Result.Success(MalSearchPage(emptyList(), false))
        return loadCached(
            cache = cache,
            clock = clock,
            throttle = throttle,
            cached = cache.search(key, pageStart),
            ttl = MAL_SEARCH_TTL,
            logger = logger,
            label = "search",
            fetch = { api.search(key, MAL_SEARCH_PAGE_SIZE, pageStart) },
            save = { page, now -> cache.saveSearch(key, pageStart, page, now) },
        )
    }

    suspend fun details(malId: Int): Result<MalAnimeDetails, MalError> =
        loadCached(
            cache = cache,
            clock = clock,
            throttle = throttle,
            cached = cache.details(malId),
            ttl = MAL_DETAILS_TTL,
            logger = logger,
            label = "details",
            fetch = { api.details(malId) },
            save = { details, now -> cache.saveDetails(malId, details, now) },
        )

    suspend fun clearCache() = cache.clear()
}

internal suspend fun <T> loadCached(
    cache: MalAnimeCache,
    clock: Clock,
    throttle: MalThrottle,
    cached: Cached<T>?,
    ttl: Duration,
    logger: AppLogger,
    label: String,
    fetch: suspend () -> Result<T, MalApiError>,
    save: suspend (T, Instant) -> Unit,
): Result<T, MalError> {
    if (cached != null && clock.now() - cached.fetchedAt < ttl) {
        logger.debug(CACHE_TAG, "$label hit")
        return Result.Success(cached.value)
    }
    return when (val result = throttle.run(fetch)) {
        is Result.Success -> {
            logger.debug(CACHE_TAG, "$label miss")
            val now = clock.now()
            save(result.data, now)
            cache.deleteFetchedBefore(now - MAL_STALE_LIMIT)
            result
        }

        is Result.Error -> {
            if (result.error == MalError.NoConnection && cached != null) {
                logger.debug(CACHE_TAG, "$label stale-fallback")
                Result.Success(cached.value)
            } else {
                result
            }
        }
    }
}
