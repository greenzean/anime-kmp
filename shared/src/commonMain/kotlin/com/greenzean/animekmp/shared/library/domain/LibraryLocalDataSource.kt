package com.greenzean.animekmp.shared.library.domain

import kotlinx.coroutines.flow.Flow

interface LibraryLocalDataSource {
    fun observeAnime(): Flow<List<Anime>>

    fun observeLibrary(): Flow<List<LibraryEntry>>

    /** Adds the anime to the library or changes its status. The entry becomes pending sync. */
    suspend fun setStatus(
        malId: Int,
        status: WatchStatus,
    )

    /** Soft-deletes the entry (tombstone) so a later sync can push the deletion. */
    suspend fun remove(malId: Int)
}
