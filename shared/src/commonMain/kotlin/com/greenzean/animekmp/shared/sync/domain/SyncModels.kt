package com.greenzean.animekmp.shared.sync.domain

import com.greenzean.animekmp.shared.library.domain.WatchStatus
import kotlin.time.Instant

data class SyncChange(
    val id: String,
    val malId: Int,
    val status: WatchStatus,
    val progress: Int,
    val score: Int?,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)

enum class PushOutcome { APPLIED, IGNORED_STALE, REJECTED_INVALID }

data class PushResult(
    val id: String,
    val outcome: PushOutcome,
    val entryId: String,
)

data class ChangesPage(
    val nextCursor: String,
    val hasMore: Boolean,
    val changes: List<SyncChange>,
)

data class Rejection(
    val id: String,
    val outcome: PushOutcome,
)

sealed interface SyncResult {
    data class Success(
        val pushed: Int,
        val pulled: Int,
        val rejected: List<Rejection>,
    ) : SyncResult

    data class Failure(
        val error: com.greenzean.animekmp.shared.core.domain.DataError,
    ) : SyncResult
}
