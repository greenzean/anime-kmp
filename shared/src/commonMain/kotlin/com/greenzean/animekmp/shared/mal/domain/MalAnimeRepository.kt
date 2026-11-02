package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.core.logging.API_TAG
import com.greenzean.animekmp.shared.core.logging.AppLogger
import com.greenzean.animekmp.shared.core.logging.NoOpAppLogger
import com.greenzean.animekmp.shared.core.logging.trace
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
            labels = CacheLabels("search", "search '$key' page $pageStart"),
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
            labels = CacheLabels("details", "details $malId"),
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
    labels: CacheLabels,
    fetch: suspend () -> Result<T, MalApiError>,
    save: suspend (T, Instant) -> Unit,
): Result<T, MalError> {
    val started = clock.now()
    if (cached != null && started - cached.fetchedAt < ttl) {
        logger.debug(CACHE_TAG, "${labels.debug} hit")
        logger.trace(API_TAG) {
            val age = (started - cached.fetchedAt).shortText()
            val left = (ttl - (started - cached.fetchedAt)).shortText()
            "API  CACHE hit  ${labels.trace}  (age $age, fresh for another $left)  no request sent"
        }
        return Result.Success(cached.value)
    }
    logger.trace(API_TAG) {
        if (cached == null) {
            "API  CACHE miss  ${labels.trace}  -> calling MAL"
        } else {
            "API  CACHE expired  ${labels.trace}  (age ${(started - cached.fetchedAt).shortText()})  -> calling MAL"
        }
    }
    return when (val result = throttle.run(fetch)) {
        is Result.Success -> {
            logger.debug(CACHE_TAG, "${labels.debug} miss")
            val now = clock.now()
            save(result.data, now)
            cache.deleteFetchedBefore(now - MAL_STALE_LIMIT)
            result
        }

        is Result.Error -> {
            if (result.error == MalError.NoConnection && cached != null) {
                logger.debug(CACHE_TAG, "${labels.debug} stale-fallback")
                logger.trace(API_TAG) {
                    "API  CACHE stale-fallback  ${labels.trace}  " +
                        "(no connection, age ${(clock.now() - cached.fetchedAt).shortText()})"
                }
                Result.Success(cached.value)
            } else {
                result
            }
        }
    }
}

internal class CacheLabels(
    val debug: String,
    val trace: String,
)

private fun Duration.shortText(): String =
    toComponents { hours, minutes, seconds, _ ->
        when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
