package com.greenzean.animekmp.shared.library.domain

import kotlinx.coroutines.flow.Flow

interface LibraryLocalDataSource {
    fun observeLibrary(): Flow<List<LibraryEntry>>

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
