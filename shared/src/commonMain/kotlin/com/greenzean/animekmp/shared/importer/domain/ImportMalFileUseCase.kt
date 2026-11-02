package com.greenzean.animekmp.shared.importer.domain

import com.greenzean.animekmp.shared.conflict.domain.ConflictLocalDataSource
import com.greenzean.animekmp.shared.conflict.domain.EntryValues
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler

class ImportMalFileUseCase(
    private val library: LibraryLocalDataSource,
    private val conflicts: ConflictLocalDataSource,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(
        file: MalExportFile,
        onProgress: (Float) -> Unit,
    ): ImportSummary {
        var added = 0
        var conflictCount = 0
        val total = file.entries.size
        file.entries.forEachIndexed { index, entry ->
            val local = library.getEntryIncludingRemoved(entry.malId)
            if (local == null) {
                library.saveAnime(Anime(malId = entry.malId, title = entry.title, episodes = entry.episodes))
                library.saveEntry(entry.malId, entry.status, entry.progress, entry.score)
                conflicts.deleteConflict(entry.malId)
                added++
            } else {
                val removed = library.getEntry(entry.malId) == null
                val same =
                    local.status == entry.status && local.progress == entry.progress && local.score == entry.score
                if (removed || !same) {
                    conflicts.saveConflict(
                        entry.malId,
                        EntryValues(entry.status, entry.progress, entry.score, removed = false),
                    )
                    conflictCount++
                } else {
                    conflicts.deleteConflict(entry.malId)
                }
            }
            onProgress((index + 1).toFloat() / total)
        }
        if (added > 0) scheduler.requestSync()
        return ImportSummary(added = added, conflicts = conflictCount, invalid = file.invalid)
    }
}
