package com.greenzean.animekmp.shared.sync.domain

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.EmptyResult

interface SyncLocalDataSource {
    suspend fun pendingChanges(limit: Int): List<SyncChange>

    suspend fun markSynced(change: SyncChange)

    suspend fun rewriteId(
        oldId: String,
        entryId: String,
    )

    suspend fun getCursor(): String?

    suspend fun applyPage(
        changes: List<SyncChange>,
        nextCursor: String,
    ): Int

    suspend fun parkedMissingAnime(): List<Int>

    suspend fun retryParked(): Int
}
