package com.greenzean.animekmp.server.graphql.sync

import com.greenzean.animekmp.server.sync.PushOutcome
import java.time.Instant
import java.util.UUID

enum class ListStatus { WATCHING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_WATCH }

data class ListEntryChangeInput(
    val id: UUID,
    val animeId: Long,
    val status: ListStatus,
    val updatedAt: Instant,
    /** Non-null makes this change a tombstone. */
    val deletedAt: Instant?,
)

data class ListEntryChange(
    val id: UUID,
    val animeId: Long,
    val status: ListStatus,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)

data class ChangeResult(
    val id: UUID,
    val outcome: PushOutcome,
    /** The server row the change landed on; differs from `id` when the anime already had an entry under another id. */
    val entryId: UUID,
)

data class PushResult(
    val results: List<ChangeResult>,
)

data class ChangesPage(
    val changes: List<ListEntryChange>,
    val nextCursor: String,
    val hasMore: Boolean,
)
