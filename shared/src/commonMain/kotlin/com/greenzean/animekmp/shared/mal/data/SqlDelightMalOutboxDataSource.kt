package com.greenzean.animekmp.shared.mal.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.data.db.Mal_outbox
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.MalOutboxLocalDataSource
import com.greenzean.animekmp.shared.mal.domain.MalOutboxStatus
import com.greenzean.animekmp.shared.mal.domain.MalSendError
import com.greenzean.animekmp.shared.mal.domain.QueuedMalChange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

private const val OP_UPSERT = "UPSERT"
private const val OP_REMOVE = "REMOVE"

class SqlDelightMalOutboxDataSource(
    private val db: AnimeDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MalOutboxLocalDataSource {
    private val outbox get() = db.malOutboxQueries

    override suspend fun enqueue(change: MalChange) {
        withContext(dispatcher) {
            when (change) {
                is MalChange.Upsert -> {
                    outbox.upsert(
                        change.malId.toLong(),
                        OP_UPSERT,
                        change.status.name,
                        change.progress.toLong(),
                        change.score?.toLong(),
                    )
                }

                is MalChange.Remove -> {
                    outbox.upsert(
                        change.malId.toLong(),
                        OP_REMOVE,
                        null,
                        null,
                        null,
                    )
                }
            }
        }
    }

    override suspend fun enqueueAppOnlyEntries() {
        withContext(dispatcher) { outbox.enqueueAppOnly() }
    }

    override suspend fun oldest(): QueuedMalChange? =
        withContext(dispatcher) { outbox.oldest().executeAsOneOrNull()?.toQueued() }

    override suspend fun markSent(item: QueuedMalChange) {
        withContext(dispatcher) {
            db.transaction {
                outbox.deleteSent(item.change.malId.toLong(), item.seq)
                when (item.change) {
                    is MalChange.Upsert -> db.listEntryQueries.markOnMal(item.change.malId.toLong())
                    is MalChange.Remove -> db.listEntryQueries.clearOnMal(item.change.malId.toLong())
                }
            }
        }
    }

    override suspend fun drop(item: QueuedMalChange) {
        withContext(dispatcher) { outbox.deleteSent(item.change.malId.toLong(), item.seq) }
    }

    override suspend fun markFailed(
        item: QueuedMalChange,
        error: MalSendError,
    ) {
        withContext(dispatcher) { outbox.setError(error.name, item.change.malId.toLong(), item.seq) }
    }

    override suspend fun isPending(malId: Int): Boolean =
        withContext(dispatcher) { outbox.selectByMalId(malId.toLong()).executeAsOneOrNull() != null }

    override suspend fun clearErrors() {
        withContext(dispatcher) { outbox.clearErrors() }
    }

    override suspend fun clear() {
        withContext(dispatcher) { outbox.clear() }
    }

    override fun observeStatus(): Flow<MalOutboxStatus> =
        combine(
            outbox.countPending().asFlow().mapToOne(dispatcher),
            outbox.firstError().asFlow().mapToOneOrNull(dispatcher),
        ) { count, error ->
            MalOutboxStatus(
                pendingCount = count.toInt(),
                lastError = MalSendError.entries.firstOrNull { it.name == error },
            )
        }
}

private fun Mal_outbox.toQueued(): QueuedMalChange {
    val change =
        if (op == OP_REMOVE) {
            MalChange.Remove(mal_id.toInt())
        } else {
            MalChange.Upsert(
                malId = mal_id.toInt(),
                status = status?.let { WatchStatus.valueOf(it) } ?: WatchStatus.PLAN_TO_WATCH,
                progress = progress?.toInt() ?: 0,
                score = score?.toInt(),
            )
        }
    return QueuedMalChange(seq, change)
}
