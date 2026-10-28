package com.greenzean.animekmp.shared.library.domain

import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import kotlinx.coroutines.flow.Flow

interface LibraryLocalDataSource {
    fun observeLibrary(
        query: String,
        status: WatchStatus?,
        sort: LibrarySort,
        titleLanguage: TitleLanguage,
    ): Flow<List<LibraryEntry>>

    fun observeActiveCount(): Flow<Int>

    suspend fun getEntry(malId: Int): LibraryEntry?

    suspend fun saveAnime(anime: Anime)

    suspend fun saveEntry(
        malId: Int,
        status: WatchStatus,
        progress: Int,
        score: Int?,
    )

    suspend fun remove(malId: Int)
}
