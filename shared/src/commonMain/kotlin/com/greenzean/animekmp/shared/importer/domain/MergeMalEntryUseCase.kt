package com.greenzean.animekmp.shared.importer.domain

import com.greenzean.animekmp.shared.conflict.domain.ConflictLocalDataSource
import com.greenzean.animekmp.shared.conflict.domain.EntryValues
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource

enum class MergeOutcome {
    Added,
    Conflict,
    Unchanged,
}

interface OnMalLocalDataSource {
    suspend fun markOnMal(malId: Int)
}

class MergeMalEntryUseCase(
    private val library: LibraryLocalDataSource,
    private val conflicts: ConflictLocalDataSource,
    private val onMal: OnMalLocalDataSource,
) {
    suspend operator fun invoke(
        entry: MalExportEntry,
        imageUrl: String?,
        markOnMal: Boolean,
    ): MergeOutcome {
        val local = library.getEntryIncludingRemoved(entry.malId)
        val outcome =
            if (local == null) {
                library.saveAnime(
                    Anime(malId = entry.malId, title = entry.title, imageUrl = imageUrl, episodes = entry.episodes),
                )
                library.saveEntry(entry.malId, entry.status, entry.progress, entry.score)
                conflicts.deleteConflict(entry.malId)
                MergeOutcome.Added
            } else {
                val removed = library.getEntry(entry.malId) == null
                val same =
                    local.status == entry.status && local.progress == entry.progress && local.score == entry.score
                if (removed || !same) {
                    conflicts.saveConflict(
                        entry.malId,
                        EntryValues(entry.status, entry.progress, entry.score, removed = false),
                    )
                    MergeOutcome.Conflict
                } else {
                    conflicts.deleteConflict(entry.malId)
                    MergeOutcome.Unchanged
                }
            }
        if (markOnMal) onMal.markOnMal(entry.malId)
        return outcome
    }
}
