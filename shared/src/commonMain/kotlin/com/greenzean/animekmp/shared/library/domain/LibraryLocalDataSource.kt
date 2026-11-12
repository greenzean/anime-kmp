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

    suspend fun getEntryIncludingRemoved(malId: Int): LibraryEntry?

    suspend fun animeMissingImage(): List<Int>

    suspend fun saveAnime(anime: Anime)

    suspend fun saveEntry(
        malId: Int,
        status: WatchStatus,
        progress: Int,
        score: Int?,
    )

    suspend fun saveImported(
        anime: Anime,
        status: WatchStatus,
        progress: Int,
        score: Int?,
        markOnMal: Boolean,
    )

    suspend fun remove(malId: Int)
}
