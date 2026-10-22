package com.greenzean.animekmp.server.graphql.sync

import com.expediagroup.graphql.server.operations.Mutation
import com.expediagroup.graphql.server.operations.Query
import com.greenzean.animekmp.server.graphql.auth.requireUserId
import com.greenzean.animekmp.server.sync.EntryChange
import com.greenzean.animekmp.server.sync.SyncRepository
import graphql.schema.DataFetchingEnvironment

private const val DEFAULT_LIMIT = 500
private const val MAX_LIMIT = 1000
private const val MAX_PUSH_BATCH = 1000

class SyncQuery(
    private val sync: SyncRepository,
) : Query {
    fun changesSince(
        env: DataFetchingEnvironment,
        cursor: String? = null,
        limit: Int? = null,
    ): ChangesPage {
        val slice = sync.changesSince(env.requireUserId(), cursor, (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT))
        return ChangesPage(
            changes = slice.changes.map { it.toGraphQL() },
            nextCursor = slice.nextCursor,
            hasMore = slice.hasMore,
        )
    }
}

class SyncMutation(
    private val sync: SyncRepository,
) : Mutation {
    fun pushChanges(
        env: DataFetchingEnvironment,
        changes: List<ListEntryChangeInput>,
    ): PushResult {
        val userId = env.requireUserId()
        require(changes.size <= MAX_PUSH_BATCH) { "At most $MAX_PUSH_BATCH changes per push" }
        val results = sync.push(userId, changes.map { it.toDomain() })
        return PushResult(results.map { ChangeResult(it.id, it.outcome, it.entryId) })
    }
}

private fun ListEntryChangeInput.toDomain() = EntryChange(id, malId, status.name, updatedAt, deletedAt)

private fun EntryChange.toGraphQL() = ListEntryChange(id, malId, ListStatus.valueOf(status), updatedAt, deletedAt)
