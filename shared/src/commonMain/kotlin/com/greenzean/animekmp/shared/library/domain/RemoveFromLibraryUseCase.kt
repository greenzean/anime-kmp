package com.greenzean.animekmp.shared.library.domain

import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.QueueMalChangeUseCase
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler

class RemoveFromLibraryUseCase(
    private val local: LibraryLocalDataSource,
    private val scheduler: SyncScheduler,
    private val queueMalChange: QueueMalChangeUseCase,
) {
    suspend operator fun invoke(malId: Int) {
        local.remove(malId)
        scheduler.requestSync()
        queueMalChange(MalChange.Remove(malId))
    }
}
