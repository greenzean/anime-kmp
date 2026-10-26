package com.greenzean.animekmp.shared.library.domain

import kotlinx.coroutines.flow.Flow

interface LibraryLocalDataSource {
    fun observeLibrary(): Flow<List<LibraryEntry>>

    suspend fun saveAnime(anime: Anime)

    suspend fun setStatus(
        malId: Int,
        status: WatchStatus,
    )

    suspend fun remove(malId: Int)
}
