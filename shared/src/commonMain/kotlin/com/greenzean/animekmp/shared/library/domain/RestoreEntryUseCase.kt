package com.greenzean.animekmp.shared.library.domain

import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.QueueMalChangeUseCase
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler

class RestoreEntryUseCase(
    private val local: LibraryLocalDataSource,
    private val scheduler: SyncScheduler,
    private val queueMalChange: QueueMalChangeUseCase,
) {
    suspend operator fun invoke(entry: LibraryEntry) {
        local.saveEntry(entry.anime.malId, entry.status, entry.progress, entry.score)
        scheduler.requestSync()
        queueMalChange(MalChange.Upsert(entry.anime.malId, entry.status, entry.progress, entry.score))
    }
}
