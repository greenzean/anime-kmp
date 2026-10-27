package com.greenzean.animekmp.server.graphql.sync

import com.greenzean.animekmp.server.sync.PushOutcome
import java.time.Instant
import java.util.UUID

enum class ListStatus { WATCHING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_WATCH }

data class ListEntryChangeInput(
    val id: UUID,
    val malId: Int,
    val status: ListStatus,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val progress: Int,
    val score: Int?,
)

data class ListEntryChange(
    val id: UUID,
    val malId: Int,
    val status: ListStatus,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val progress: Int,
    val score: Int?,
)

data class ChangeResult(
    val id: UUID,
    val outcome: PushOutcome,
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
