package com.greenzean.animekmp.server.sync

import com.greenzean.animekmp.server.auth.AuthFailure
import com.greenzean.animekmp.server.db.AnimeTable
import com.greenzean.animekmp.server.db.AppUserTable
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
import org.jetbrains.exposed.v1.jdbc.insertIgnore
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
    val malId: Int,
    val status: String,
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

data class ChangesSlice(
    val changes: List<EntryChange>,
    val nextCursor: String,
    val hasMore: Boolean,
)

class SyncRepository(
    private val database: Database? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val seq = Sequence("list_entry_sync_seq")

    fun push(
        userId: UUID,
        batch: List<EntryChange>,
    ): List<ChangeResult> =
        transaction(database) {
            exec("SELECT pg_advisory_xact_lock(hashtextextended('list_entry:$userId', 0))") {}
            requireUser(userId)
            batch.map { apply(userId, it.truncatedToMillis()) }
        }

    fun changesSince(
        userId: UUID,
        cursor: String?,
        limit: Int,
    ): ChangesSlice {
        val after = Cursor.decode(cursor)
        return transaction(database) {
            requireUser(userId)
            val rows =
                ListEntryTable
                    .innerJoin(AnimeTable)
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
        if (rejected != null) return ChangeResult(change.id, rejected, change.id)
        val animeId = animeIdFor(change.malId)
        return if (byId != null) update(userId, change, animeId, byId) else applyToSlot(userId, change, animeId)
    }

    private fun animeIdFor(malId: Int): Long {
        AnimeTable.insertIgnore { it[AnimeTable.malId] = malId }
        return AnimeTable
            .select(AnimeTable.id)
            .where { AnimeTable.malId eq malId }
            .single()[AnimeTable.id]
    }

    private fun applyToSlot(
        userId: UUID,
        change: EntryChange,
        animeId: Long,
    ): ChangeResult {
        val slot =
            ListEntryTable
                .selectAll()
                .where {
                    (ListEntryTable.userId eq userId.toKotlinUuid()) and (ListEntryTable.animeId eq animeId)
                }.singleOrNull()
        return if (slot == null) insert(userId, change, animeId) else update(userId, change, animeId, slot)
    }

    private fun rejection(change: EntryChange): PushOutcome? {
        val latest = clock.instant().plus(MAX_CLOCK_SKEW)
        return when {
            change.status !in VALID_STATUSES -> {
                PushOutcome.REJECTED_INVALID
            }

            change.updatedAt.isAfter(latest) || change.deletedAt?.isAfter(latest) == true -> {
                PushOutcome.REJECTED_INVALID
            }

            change.malId <= 0 -> {
                PushOutcome.REJECTED_INVALID
            }

            change.progress !in 0..MAX_PROGRESS -> {
                PushOutcome.REJECTED_INVALID
            }

            change.score != null && change.score !in MIN_SCORE..MAX_SCORE -> {
                PushOutcome.REJECTED_INVALID
            }

            else -> {
                null
            }
        }
    }

    private fun update(
        userId: UUID,
        change: EntryChange,
        animeId: Long,
        existing: ResultRow,
    ): ChangeResult {
        val entryId = existing[ListEntryTable.id]
        val outcome =
            when {
                existing[ListEntryTable.userId] != userId.toKotlinUuid() -> {
                    PushOutcome.REJECTED_INVALID
                }

                existing[ListEntryTable.animeId] != animeId -> {
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
                        it[progress] = change.progress
                        it[score] = change.score
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
        animeId: Long,
    ): ChangeResult {
        ListEntryTable.insert {
            it[ListEntryTable.id] = change.id.toKotlinUuid()
            it[ListEntryTable.userId] = userId.toKotlinUuid()
            it[ListEntryTable.animeId] = animeId
            it[status] = change.status
            it[updatedAt] = change.updatedAt.toOffset()
            it[deletedAt] = change.deletedAt?.toOffset()
            it[progress] = change.progress
            it[score] = change.score
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
            malId = this[AnimeTable.malId],
            status = this[ListEntryTable.status],
            updatedAt = this[ListEntryTable.updatedAt].toInstant(),
            deletedAt = this[ListEntryTable.deletedAt]?.toInstant(),
            progress = this[ListEntryTable.progress],
            score = this[ListEntryTable.score],
        )

    private fun Instant.toOffset(): OffsetDateTime = atOffset(ZoneOffset.UTC)
}

private fun requireUser(userId: UUID) {
    val exists =
        !AppUserTable
            .select(AppUserTable.id)
            .where { AppUserTable.id eq userId.toKotlinUuid() }
            .empty()
    if (!exists) throw AuthFailure("User no longer exists")
}
