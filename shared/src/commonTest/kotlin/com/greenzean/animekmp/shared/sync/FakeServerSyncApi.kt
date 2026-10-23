package com.greenzean.animekmp.shared.sync

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.sync.domain.ChangesPage
import com.greenzean.animekmp.shared.sync.domain.PushOutcome
import com.greenzean.animekmp.shared.sync.domain.PushResult
import com.greenzean.animekmp.shared.sync.domain.SyncApi
import com.greenzean.animekmp.shared.sync.domain.SyncChange
import kotlinx.coroutines.CompletableDeferred

class FakeServerSyncApi : SyncApi {
    private class Row(
        val change: SyncChange,
        val seq: Int,
    )

    private val rows = mutableMapOf<Int, Row>()
    private var seq = 0

    var offline = false

    val rejectedMalIds = mutableSetOf<Int>()

    var pullGate: CompletableDeferred<Unit>? = null

    val changes: List<SyncChange> get() = rows.values.map { it.change }

    fun change(malId: Int): SyncChange? = rows[malId]?.change

    override suspend fun push(changes: List<SyncChange>): Result<List<PushResult>, DataError.Network> {
        if (offline) return Result.Error(DataError.Network.NO_INTERNET)
        val results =
            changes.map { incoming ->
                val existing = rows[incoming.malId]
                when {
                    incoming.malId in rejectedMalIds -> {
                        PushResult(incoming.id, PushOutcome.REJECTED_INVALID, incoming.id)
                    }

                    existing != null && existing.change.updatedAt >= incoming.updatedAt -> {
                        PushResult(incoming.id, PushOutcome.IGNORED_STALE, existing.change.id)
                    }

                    else -> {
                        val id = existing?.change?.id ?: incoming.id
                        rows[incoming.malId] = Row(incoming.copy(id = id), ++seq)
                        PushResult(incoming.id, PushOutcome.APPLIED, id)
                    }
                }
            }
        return Result.Success(results)
    }

    override suspend fun pull(
        cursor: String?,
        limit: Int,
    ): Result<ChangesPage, DataError.Network> {
        pullGate?.await()
        if (offline) return Result.Error(DataError.Network.NO_INTERNET)
        val after = cursor?.toInt() ?: 0
        val page =
            rows.values
                .filter { it.seq > after }
                .sortedBy { it.seq }
                .take(limit)
        val next = page.lastOrNull()?.seq ?: after
        val hasMore = rows.values.any { it.seq > next }
        return Result.Success(ChangesPage(next.toString(), hasMore, page.map { it.change }))
    }
}
