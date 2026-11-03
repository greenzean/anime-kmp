package com.greenzean.animekmp.shared.library.domain

import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.QueueMalChangeUseCase
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler

class AddToLibraryUseCase(
    private val local: LibraryLocalDataSource,
    private val scheduler: SyncScheduler,
    private val queueMalChange: QueueMalChangeUseCase,
) {
    suspend operator fun invoke(anime: Anime) {
        local.saveAnime(anime)
        local.saveEntry(anime.malId, WatchStatus.PLAN_TO_WATCH, progress = 0, score = null)
        scheduler.requestSync()
        queueMalChange(MalChange.Upsert(anime.malId, WatchStatus.PLAN_TO_WATCH, 0, null))
    }
}
