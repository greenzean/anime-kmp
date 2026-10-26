package com.greenzean.animekmp.shared.library.domain

import com.greenzean.animekmp.shared.sync.domain.SyncScheduler

class AddToLibraryUseCase(
    private val local: LibraryLocalDataSource,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(anime: Anime) {
        local.saveAnime(anime)
        local.setStatus(anime.malId, WatchStatus.PLAN_TO_WATCH)
        scheduler.requestSync()
    }
}
