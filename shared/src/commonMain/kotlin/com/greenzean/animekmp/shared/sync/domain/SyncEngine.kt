package com.greenzean.animekmp.shared.sync.domain

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncEngine(
    private val api: SyncApi,
    private val local: SyncLocalDataSource,
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncResult =
        mutex.withLock {
            val rejected = mutableListOf<Rejection>()
            var pushed = 0
            when (val push = pushPending(rejected)) {
                is Result.Error -> return@withLock SyncResult.Failure(push.error)
                is Result.Success -> pushed = push.data
            }
            when (val pull = pullAll()) {
                is Result.Error -> SyncResult.Failure(pull.error)
                is Result.Success -> SyncResult.Success(pushed, pull.data, rejected)
            }
        }

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

    private suspend fun pushPending(rejected: MutableList<Rejection>): Result<Int, DataError.Network> {
        var pushed = 0
        val seen = mutableSetOf<String>()
        while (true) {
            val batch =
                local
                    .pendingChanges(PUSH_BATCH_SIZE + seen.size)
                    .filter { it.id !in seen }
                    .take(PUSH_BATCH_SIZE)
            if (batch.isEmpty()) return Result.Success(pushed)
            val results =
                when (val response = api.push(batch)) {
                    is Result.Error -> return response
                    is Result.Success -> response.data.associateBy { it.id }
                }
            for (change in batch) {
                seen += change.id
                val result = results[change.id]
                when (result?.outcome) {
                    PushOutcome.APPLIED, PushOutcome.IGNORED_STALE -> {
                        if (result.entryId != change.id) local.rewriteId(change.id, result.entryId)
                        local.markSynced(change.copy(id = result.entryId))
                        pushed++
                    }

                    PushOutcome.REJECTED_INVALID -> {
                        rejected += Rejection(change.id, result.outcome)
                    }

                    null -> {
                        Unit
                    }
                }
            }
        }
    }

    private suspend fun pullAll(): Result<Int, DataError.Network> {
        var cursor = local.getCursor()
        var pulled = 0
        do {
            val page =
                when (val response = api.pull(cursor, PULL_LIMIT)) {
                    is Result.Error -> return response
                    is Result.Success -> response.data
                }
            pulled += local.applyPage(page.changes, page.nextCursor)
            cursor = page.nextCursor
        } while (page.hasMore)

        pulled += local.retryParked()
        return Result.Success(pulled)
    }

    private companion object {
        const val PUSH_BATCH_SIZE = 1000
        const val PULL_LIMIT = 500
    }
}
