package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

val MAL_SUGGESTIONS_TTL = 1.days
const val MAL_SUGGESTIONS_PAGE_SIZE = 20
const val MAL_SUGGESTIONS_MAX = 100

class MalSuggestionsRepository(
    private val api: MalSuggestionsApi,
    private val cache: MalAnimeCache,
    private val throttle: MalThrottle,
    private val clock: Clock = Clock.System,
) {
    suspend fun suggestions(pageStart: Int = 0): Result<MalSearchPage, MalError> =
        loadCached(
            cache = cache,
            clock = clock,
            throttle = throttle,
            cached = cache.suggestions(pageStart),
            ttl = MAL_SUGGESTIONS_TTL,
            fetch = { api.suggestions(MAL_SUGGESTIONS_PAGE_SIZE, pageStart) },
            save = { page, now -> cache.saveSuggestions(pageStart, page, now) },
        ).capped(pageStart)

    private fun Result<MalSearchPage, MalError>.capped(pageStart: Int): Result<MalSearchPage, MalError> =
        when (this) {
            is Result.Success -> {
                if (data.hasMore && pageStart + MAL_SUGGESTIONS_PAGE_SIZE >= MAL_SUGGESTIONS_MAX) {
                    Result.Success(data.copy(hasMore = false))
                } else {
                    this
                }
            }

            is Result.Error -> {
                this
            }
        }
}
