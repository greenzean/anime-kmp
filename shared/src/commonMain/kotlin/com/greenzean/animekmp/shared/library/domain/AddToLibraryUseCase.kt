package com.greenzean.animekmp.shared.library.domain

import com.greenzean.animekmp.shared.sync.domain.SyncScheduler

/** Adds the anime as plan-to-watch and asks for a sync so the entry reaches the server. */
class AddToLibraryUseCase(
    private val local: LibraryLocalDataSource,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(malId: Int) {
        local.setStatus(malId, WatchStatus.PLAN_TO_WATCH)
        scheduler.requestSync()
    }
}
