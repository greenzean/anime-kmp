package com.greenzean.animekmp.server.sync

import com.greenzean.animekmp.server.db.AnimeTable
import com.greenzean.animekmp.server.db.ListEntryTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.nextLongVal
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

private val MAX_CLOCK_SKEW: Duration = Duration.ofMinutes(5)

val VALID_STATUSES = setOf("WATCHING", "COMPLETED", "ON_HOLD", "DROPPED", "PLAN_TO_WATCH")

data class EntryChange(
    val id: UUID,
    val animeId: Long,
    val status: String,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)

/** `entryId` is the server row the change landed on; it differs from `id` for a re-added anime. */
data class ChangeResult(
    val id: UUID,
    val outcome: PushOutcome,
    val entryId: UUID,
)

data class ChangesSlice(
    val changes: List<EntryChange>,
    val nextCursor: String,
    val hasMore: Boolean,
)

/** `database` null means Exposed's default (last connected) database. */
class SyncRepository(
    private val database: Database? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val seq = Sequence("list_entry_sync_seq")

    /**
     * Applies the batch in one transaction. Re-pushing the same batch changes nothing (idempotent).
     *
     * Pushes of one user are serialized by a transaction-scoped advisory lock taken before any `nextval`.
     * Without it a push could take seq 10, a second push seq 11 and commit first, and a pull would move its
     * cursor past 11 before 10 becomes visible, so that device would never see 10 (ADR 0006). The lock also
     * makes the check-then-insert below race-free.
     *
     * Two assumptions the lock depends on: the transaction runs at READ COMMITTED (Exposed's default for
     * Postgres), so rows committed while waiting for the lock are visible afterwards; and every writer of
     * `list_entry` takes this lock first, because the column default `nextval` would hand a lockless insert
     * a sequence value silently.
     *
     * Timestamps are truncated to milliseconds: Postgres keeps microseconds and the app sends milliseconds, so
     * a replay of a finer-grained timestamp would otherwise compare newer than the stored row.
     */
    fun push(
        userId: UUID,
        batch: List<EntryChange>,
    ): List<ChangeResult> =
        transaction(database) {
            exec("SELECT pg_advisory_xact_lock(hashtextextended('list_entry:$userId', 0))") {}
            batch.map { apply(userId, it.truncatedToMillis()) }
        }

    fun changesSince(
        userId: UUID,
        cursor: String?,
        limit: Int,
    ): ChangesSlice {
        val after = Cursor.decode(cursor)
        return transaction(database) {
            val rows =
                ListEntryTable
                    .selectAll()
                    .where {
                        (ListEntryTable.userId eq userId.toKotlinUuid()) and
                            (ListEntryTable.serverSeq greater after)
                    }.orderBy(ListEntryTable.serverSeq, SortOrder.ASC)
                    .limit(limit + 1)
                    .toList()
            val page = rows.take(limit)
            ChangesSlice(
                changes = page.map { it.toChange() },
                nextCursor = Cursor.encode(page.lastOrNull()?.get(ListEntryTable.serverSeq) ?: after),
                hasMore = rows.size > limit,
            )
        }
    }

    private fun apply(
        userId: UUID,
        change: EntryChange,
    ): ChangeResult {
        val rejected = rejection(change)
        val byId =
            if (rejected == null) {
                ListEntryTable.selectAll().where { ListEntryTable.id eq change.id.toKotlinUuid() }.singleOrNull()
            } else {
                null
            }
        return when {
            rejected != null -> ChangeResult(change.id, rejected, change.id)
            byId != null -> update(userId, change, byId)
            else -> applyToSlot(userId, change)
        }
    }

    // One row per (user, anime). The same anime added under another id (two devices, or a re-add after a delete)
    // is merged into the existing row by last-write-wins; entryId tells the client which id the row has (ADR 0006).
    private fun applyToSlot(
        userId: UUID,
        change: EntryChange,
    ): ChangeResult {
        val slot =
            ListEntryTable
                .selectAll()
                .where {
                    (ListEntryTable.userId eq userId.toKotlinUuid()) and (ListEntryTable.animeId eq change.animeId)
                }.singleOrNull()
        return if (slot == null) insert(userId, change) else update(userId, change, slot)
    }

    private fun rejection(change: EntryChange): PushOutcome? {
        val latest = clock.instant().plus(MAX_CLOCK_SKEW)
        return when {
            change.status !in VALID_STATUSES -> {
                PushOutcome.REJECTED_INVALID
            }

            // A far-future timestamp would win every later last-write-wins comparison and freeze the entry.
            change.updatedAt.isAfter(latest) || change.deletedAt?.isAfter(latest) == true -> {
                PushOutcome.REJECTED_INVALID
            }

            AnimeTable.select(AnimeTable.id).where { AnimeTable.id eq change.animeId }.empty() -> {
                PushOutcome.REJECTED_UNKNOWN_ANIME
            }

            else -> {
                null
            }
        }
    }

    /** Applies last-write-wins to the stored row `existing`, which may carry another id than `change.id`. */
    private fun update(
        userId: UUID,
        change: EntryChange,
        existing: ResultRow,
    ): ChangeResult {
        val entryId = existing[ListEntryTable.id]
        val outcome =
            when {
                existing[ListEntryTable.userId] != userId.toKotlinUuid() -> {
                    PushOutcome.REJECTED_INVALID
                }

                existing[ListEntryTable.animeId] != change.animeId -> {
                    PushOutcome.REJECTED_INVALID
                }

                !isNewer(change.updatedAt, existing[ListEntryTable.updatedAt].toInstant()) -> {
                    PushOutcome.IGNORED_STALE
                }

                else -> {
                    ListEntryTable.update({ ListEntryTable.id eq entryId }) {
                        it[status] = change.status
                        it[updatedAt] = change.updatedAt.toOffset()
                        it[deletedAt] = change.deletedAt?.toOffset()
                        it[serverSeq] = seq.nextLongVal()
                    }
                    PushOutcome.APPLIED
                }
            }
        return ChangeResult(change.id, outcome, entryId.toJavaUuid())
    }

    private fun insert(
        userId: UUID,
        change: EntryChange,
    ): ChangeResult {
        ListEntryTable.insert {
            it[ListEntryTable.id] = change.id.toKotlinUuid()
            it[ListEntryTable.userId] = userId.toKotlinUuid()
            it[animeId] = change.animeId
            it[status] = change.status
            it[updatedAt] = change.updatedAt.toOffset()
            it[deletedAt] = change.deletedAt?.toOffset()
        }
        return ChangeResult(change.id, PushOutcome.APPLIED, change.id)
    }

    private fun EntryChange.truncatedToMillis() =
        copy(
            updatedAt = updatedAt.truncatedTo(ChronoUnit.MILLIS),
            deletedAt = deletedAt?.truncatedTo(ChronoUnit.MILLIS),
        )

    private fun ResultRow.toChange() =
        EntryChange(
            id = this[ListEntryTable.id].toJavaUuid(),
            animeId = this[ListEntryTable.animeId],
            status = this[ListEntryTable.status],
            updatedAt = this[ListEntryTable.updatedAt].toInstant(),
            deletedAt = this[ListEntryTable.deletedAt]?.toInstant(),
        )

    private fun Instant.toOffset(): OffsetDateTime = atOffset(ZoneOffset.UTC)
}
