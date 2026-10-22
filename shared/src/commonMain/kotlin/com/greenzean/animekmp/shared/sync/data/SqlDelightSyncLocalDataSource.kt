package com.greenzean.animekmp.shared.sync.data

import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.data.db.List_entry
import com.greenzean.animekmp.shared.core.data.db.Parked_change
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
    private val parked get() = db.parkedChangeQueries

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
                val applied = changes.count(::applyOrPark)
                db.syncCursorQueries.saveCursor(nextCursor)
                applied
            }
        }

    override suspend fun retryParked(): Int =
        withContext(dispatcher) {
            db.transactionWithResult {
                parked.selectAll().executeAsList().count { row ->
                    val inCatalog = db.animeQueries.existsByMalId(row.mal_id).executeAsOne() > 0L
                    if (inCatalog) parked.deleteById(row.id)
                    inCatalog && applyPulled(row.toSyncChange())
                }
            }
        }

    private fun applyOrPark(change: SyncChange): Boolean =
        if (db.animeQueries.existsByMalId(change.malId.toLong()).executeAsOne() == 0L) {
            park(change)
            false
        } else {
            parked.deleteByMalId(change.malId.toLong())
            applyPulled(change)
        }

    /** Keeps only the newest parked change per anime, so a stale one cannot overwrite a newer one later. */
    private fun park(change: SyncChange) {
        val existing = parked.selectByMalId(change.malId.toLong()).executeAsOneOrNull()
        if (existing == null || existing.updated_at < change.updatedAt.toEpochMilliseconds()) {
            parked.insertOrReplace(
                change.id,
                change.malId.toLong(),
                change.status.name,
                change.updatedAt.toEpochMilliseconds(),
                change.deletedAt?.toEpochMilliseconds(),
            )
        }
    }

    /**
     * Merge rule from ADR 0006: one row per (user, anime). Adopt the server id first, then apply the
     * pulled row only if local is SYNCED or older; a newer (or equal) PENDING edit is never overwritten.
     * The anime must already be in the local catalog (FK).
     */
    private fun applyPulled(change: SyncChange): Boolean {
        val local = listEntries.selectByUserAndMalId(LOCAL_USER_ID, change.malId.toLong()).executeAsOneOrNull()
        return if (local == null) insertPulled(change) else mergePulled(change, local)
    }

    private fun insertPulled(change: SyncChange): Boolean {
        listEntries.insertPulled(
            change.id,
            LOCAL_USER_ID,
            change.malId.toLong(),
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

private fun Parked_change.toSyncChange() =
    SyncChange(
        id = id,
        malId = mal_id.toInt(),
        status = WatchStatus.valueOf(status),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
        deletedAt = deleted_at?.let(Instant::fromEpochMilliseconds),
    )

private fun List_entry.toSyncChange() =
    SyncChange(
        id = id,
        malId = mal_id.toInt(),
        status = WatchStatus.valueOf(status),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
        deletedAt = deleted_at?.let(Instant::fromEpochMilliseconds),
    )
