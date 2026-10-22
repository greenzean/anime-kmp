package com.greenzean.animekmp.shared.library.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Anime that have no active library entry yet. */
class ObserveAddableAnimeUseCase(
    private val local: LibraryLocalDataSource,
) {
    operator fun invoke(): Flow<List<Anime>> =
        combine(local.observeAnime(), local.observeLibrary()) { anime, entries ->
            val added = entries.mapTo(mutableSetOf()) { it.anime.malId }
            anime.filter { it.malId !in added }
        }
}
