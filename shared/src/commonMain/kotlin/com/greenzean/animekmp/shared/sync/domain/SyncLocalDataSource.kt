package com.greenzean.animekmp.shared.sync.domain

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.EmptyResult

interface SyncLocalDataSource {
    /** At most [limit] PENDING rows (tombstones included), oldest first. */
    suspend fun pendingChanges(limit: Int): List<SyncChange>

    /** Marks [id] SYNCED only if its updated_at still equals [change]'s, so a newer local edit stays PENDING. */
    suspend fun markSynced(change: SyncChange)

    /** The server kept [entryId] for the same (user, anime): adopt it locally, content unchanged. */
    suspend fun rewriteId(
        oldId: String,
        entryId: String,
    )

    suspend fun getCursor(): String?

    /** Applies one pulled page and stores [nextCursor] in a single transaction. Returns rows applied. */
    suspend fun applyPage(
        changes: List<SyncChange>,
        nextCursor: String,
    ): Int
}
