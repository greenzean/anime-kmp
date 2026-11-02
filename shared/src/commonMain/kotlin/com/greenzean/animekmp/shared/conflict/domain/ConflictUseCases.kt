package com.greenzean.animekmp.shared.conflict.domain

import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveConflictsUseCase(
    private val conflicts: ConflictLocalDataSource,
) {
    operator fun invoke(): Flow<List<Conflict>> = conflicts.observeConflicts()
}

class ObserveConflictCountUseCase(
    private val conflicts: ConflictLocalDataSource,
) {
    operator fun invoke(): Flow<Int> = conflicts.observeConflicts().map { it.size }
}

class ResolveConflictUseCase(
    private val conflicts: ConflictLocalDataSource,
    private val library: LibraryLocalDataSource,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(
        malId: Int,
        keep: ConflictSide,
    ) {
        when (keep) {
            ConflictSide.App -> {
                conflicts.deleteConflict(malId)
            }

            ConflictSide.Other -> {
                val conflict = conflicts.getConflicts().firstOrNull { it.malId == malId } ?: return
                applyOther(library, conflicts, conflict)
                scheduler.requestSync()
            }
        }
    }
}

class ResolveAllConflictsUseCase(
    private val conflicts: ConflictLocalDataSource,
    private val library: LibraryLocalDataSource,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(keep: ConflictSide) {
        val all = conflicts.getConflicts()
        when (keep) {
            ConflictSide.App -> {
                all.forEach { conflicts.deleteConflict(it.malId) }
            }

            ConflictSide.Other -> {
                all.forEach { applyOther(library, conflicts, it) }
                scheduler.requestSync()
            }
        }
    }
}

private suspend fun applyOther(
    library: LibraryLocalDataSource,
    conflicts: ConflictLocalDataSource,
    conflict: Conflict,
) {
    val other = conflict.other
    if (other.removed) {
        library.remove(conflict.malId)
    } else {
        library.saveEntry(conflict.malId, other.status, other.progress, other.score)
    }
    conflicts.deleteConflict(conflict.malId)
}
