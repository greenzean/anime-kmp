package com.greenzean.animekmp.shared.importer.domain

import com.greenzean.animekmp.shared.conflict.domain.ConflictLocalDataSource
import com.greenzean.animekmp.shared.conflict.domain.EntryValues
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.MalOutboxLocalDataSource
import kotlin.time.Instant

sealed interface MergeOutcome {
    data object Added : MergeOutcome

    data object Conflict : MergeOutcome

    data object Unchanged : MergeOutcome

    data object TookMal : MergeOutcome

    data class KeptLocal(
        val change: MalChange.Upsert,
    ) : MergeOutcome
}

interface OnMalLocalDataSource {
    suspend fun markOnMal(malId: Int)

    suspend fun activeOnMalIds(): List<Int>
}

class MergeMalEntryUseCase(
    private val library: LibraryLocalDataSource,
    private val conflicts: ConflictLocalDataSource,
    private val onMal: OnMalLocalDataSource,
    private val outbox: MalOutboxLocalDataSource,
) {
    suspend operator fun invoke(
        entry: MalExportEntry,
        imageUrl: String?,
        markOnMal: Boolean,
        malUpdatedAt: Instant? = null,
        malChanged: Boolean = true,
    ): MergeOutcome {
        val local = library.getEntryIncludingRemoved(entry.malId)
        val outcome =
            if (local == null) {
                addNew(entry, imageUrl, markOnMal)
            } else {
                resolve(entry, local, malUpdatedAt, malChanged)
            }
        if (markOnMal && outcome != MergeOutcome.Added) onMal.markOnMal(entry.malId)
        return outcome
    }

    private suspend fun addNew(
        entry: MalExportEntry,
        imageUrl: String?,
        markOnMal: Boolean,
    ): MergeOutcome {
        library.saveImported(
            Anime(malId = entry.malId, title = entry.title, imageUrl = imageUrl, episodes = entry.episodes),
            entry.status,
            entry.progress,
            entry.score,
            markOnMal,
        )
        conflicts.deleteConflict(entry.malId)
        return MergeOutcome.Added
    }

    private suspend fun resolve(
        entry: MalExportEntry,
        local: LibraryEntry,
        malUpdatedAt: Instant?,
        malChanged: Boolean,
    ): MergeOutcome {
        val removed = library.getEntry(entry.malId) == null
        val same = local.status == entry.status && local.progress == entry.progress && local.score == entry.score
        return when {
            !removed && same -> {
                conflicts.deleteConflict(entry.malId)
                MergeOutcome.Unchanged
            }

            !removed && malUpdatedAt != null -> {
                settleByTime(entry, local, malUpdatedAt, malChanged)
            }

            else -> {
                saveConflict(entry)
            }
        }
    }

    private suspend fun settleByTime(
        entry: MalExportEntry,
        local: LibraryEntry,
        malUpdatedAt: Instant,
        malChanged: Boolean,
    ): MergeOutcome =
        when {
            outbox.isPending(entry.malId) -> {
                if (malChanged) saveConflict(entry) else MergeOutcome.Unchanged
            }

            malUpdatedAt > local.updatedAt -> {
                library.saveEntry(entry.malId, entry.status, entry.progress, entry.score)
                conflicts.deleteConflict(entry.malId)
                MergeOutcome.TookMal
            }

            else -> {
                conflicts.deleteConflict(entry.malId)
                MergeOutcome.KeptLocal(MalChange.Upsert(entry.malId, local.status, local.progress, local.score))
            }
        }

    private suspend fun saveConflict(entry: MalExportEntry): MergeOutcome {
        conflicts.saveConflict(
            entry.malId,
            EntryValues(entry.status, entry.progress, entry.score, removed = false),
        )
        return MergeOutcome.Conflict
    }
}
