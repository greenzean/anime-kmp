package com.greenzean.animekmp.shared.sync.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.toAnime

interface ParkedAnimeResolver {
    suspend fun resolve()
}

object NoParkedAnimeResolver : ParkedAnimeResolver {
    override suspend fun resolve() = Unit
}

class MalParkedAnimeResolver(
    private val sync: SyncLocalDataSource,
    private val library: LibraryLocalDataSource,
    private val mal: MalAnimeRepository,
) : ParkedAnimeResolver {
    override suspend fun resolve() {
        for (malId in sync.parkedMissingAnime()) {
            when (val result = mal.details(malId)) {
                is Result.Success -> library.saveAnime(result.data.summary.toAnime())
                is Result.Error -> if (result.error.stopsResolving()) return
            }
        }
    }

    private fun MalError.stopsResolving(): Boolean = this != MalError.NotFound
}
