package com.greenzean.animekmp.shared.sync.domain

import com.greenzean.animekmp.shared.library.domain.WatchStatus
import kotlin.time.Instant

/** A local list_entry row as the server sees it. [deletedAt] set means tombstone. */
data class SyncChange(
    val id: String,
    val animeId: Long,
    val status: WatchStatus,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)

enum class PushOutcome { APPLIED, IGNORED_STALE, REJECTED_INVALID, REJECTED_UNKNOWN_ANIME }

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
    /** [rejected] rows stay PENDING and are not retried by this run. */
    data class Success(
        val pushed: Int,
        val pulled: Int,
        val rejected: List<Rejection>,
    ) : SyncResult

    data class Failure(
        val error: com.greenzean.animekmp.shared.core.domain.DataError,
    ) : SyncResult
}
