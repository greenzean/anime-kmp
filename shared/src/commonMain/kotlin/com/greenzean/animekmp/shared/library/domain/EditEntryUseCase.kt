package com.greenzean.animekmp.shared.library.domain

import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.QueueMalChangeUseCase
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler

class EditEntryUseCase(
    private val local: LibraryLocalDataSource,
    private val scheduler: SyncScheduler,
    private val queueMalChange: QueueMalChangeUseCase,
) {
    suspend operator fun invoke(
        malId: Int,
        edit: EntryEdit,
        totalEpisodes: Int? = null,
    ) {
        val stored = local.getEntry(malId) ?: return
        val entry = stored.copy(anime = stored.anime.copy(episodes = totalEpisodes ?: stored.anime.episodes))
        val edited = EntryRules.apply(entry, edit)
        local.saveEntry(malId, edited.status, edited.progress, edited.score)
        scheduler.requestSync()
        queueMalChange(MalChange.Upsert(malId, edited.status, edited.progress, edited.score))
    }
}
