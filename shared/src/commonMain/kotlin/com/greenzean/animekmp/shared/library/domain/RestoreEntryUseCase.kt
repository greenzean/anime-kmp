package com.greenzean.animekmp.shared.library.domain

import com.greenzean.animekmp.shared.sync.domain.SyncScheduler

class RestoreEntryUseCase(
    private val local: LibraryLocalDataSource,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(entry: LibraryEntry) {
        local.saveEntry(entry.anime.malId, entry.status, entry.progress, entry.score)
        scheduler.requestSync()
    }
}
