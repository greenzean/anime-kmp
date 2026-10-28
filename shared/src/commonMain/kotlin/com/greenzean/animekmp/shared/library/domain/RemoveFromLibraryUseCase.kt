package com.greenzean.animekmp.shared.library.domain

import com.greenzean.animekmp.shared.sync.domain.SyncScheduler

class RemoveFromLibraryUseCase(
    private val local: LibraryLocalDataSource,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(malId: Int) {
        local.remove(malId)
        scheduler.requestSync()
    }
}
