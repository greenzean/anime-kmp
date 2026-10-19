package com.greenzean.animekmp.shared.sync.data

import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.data.db.List_entry
import com.greenzean.animekmp.shared.library.domain.LOCAL_USER_ID
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.sync.domain.SyncChange
import com.greenzean.animekmp.shared.sync.domain.SyncLocalDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant

class SqlDelightSyncLocalDataSource(
    private val db: AnimeDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SyncLocalDataSource {
    private val listEntries get() = db.listEntryQueries

    override suspend fun pendingChanges(limit: Int): List<SyncChange> =
        withContext(dispatcher) {
            listEntries.selectPendingBatch(limit.toLong()).executeAsList().map { it.toSyncChange() }
        }

    override suspend fun markSynced(change: SyncChange) {
        withContext(dispatcher) {
            listEntries.markSynced(change.id, change.updatedAt.toEpochMilliseconds())
        }
    }

    override suspend fun rewriteId(
        oldId: String,
        entryId: String,
    ) {
        withContext(dispatcher) { listEntries.rewriteId(newId = entryId, oldId = oldId) }
    }

    override suspend fun getCursor(): String? =
        withContext(dispatcher) { db.syncCursorQueries.selectCursor().executeAsOneOrNull() }

    override suspend fun applyPage(
        changes: List<SyncChange>,
        nextCursor: String,
    ): Int =
        withContext(dispatcher) {
            db.transactionWithResult {
                val applied = changes.count(::applyPulled)
                db.syncCursorQueries.saveCursor(nextCursor)
                applied
            }
        }

    /**
     * Merge rule from ADR 0006: one row per (user, anime). Adopt the server id first, then apply the
     * pulled row only if local is SYNCED or older; a newer (or equal) PENDING edit is never overwritten.
     * A row for an anime missing from the local catalog is skipped (the FK cannot be satisfied).
     */
    private fun applyPulled(change: SyncChange): Boolean =
        if (db.animeQueries.existsById(change.animeId).executeAsOne() == 0L) {
            false
        } else {
            val local = listEntries.selectByUserAndAnime(LOCAL_USER_ID, change.animeId).executeAsOneOrNull()
            if (local == null) insertPulled(change) else mergePulled(change, local)
        }

    private fun insertPulled(change: SyncChange): Boolean {
        listEntries.insertPulled(
            change.id,
            LOCAL_USER_ID,
            change.animeId,
            change.status.name,
            change.updatedAt.toEpochMilliseconds(),
            change.deletedAt?.toEpochMilliseconds(),
        )
        return true
    }

    private fun mergePulled(
        change: SyncChange,
        local: List_entry,
    ): Boolean {
        val updatedAt = change.updatedAt.toEpochMilliseconds()
        if (local.id != change.id) listEntries.rewriteId(newId = change.id, oldId = local.id)
        val localWins = local.sync_state == "PENDING" && local.updated_at >= updatedAt
        if (!localWins) {
            listEntries.updatePulled(
                change.status.name,
                updatedAt,
                change.deletedAt?.toEpochMilliseconds(),
                change.id,
            )
        }
        return !localWins
    }
}

private fun List_entry.toSyncChange() =
    SyncChange(
        id = id,
        animeId = anime_id,
        status = WatchStatus.valueOf(status),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
        deletedAt = deleted_at?.let(Instant::fromEpochMilliseconds),
    )
