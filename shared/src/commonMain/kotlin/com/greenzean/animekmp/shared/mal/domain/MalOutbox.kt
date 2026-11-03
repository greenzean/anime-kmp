package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.library.domain.WatchStatus
import kotlinx.coroutines.flow.Flow

sealed interface MalChange {
    val malId: Int

    data class Upsert(
        override val malId: Int,
        val status: WatchStatus,
        val progress: Int,
        val score: Int?,
    ) : MalChange

    data class Remove(
        override val malId: Int,
    ) : MalChange
}

data class QueuedMalChange(
    val seq: Long,
    val change: MalChange,
)

enum class MalSendError { NO_CONNECTION, THROTTLED, BLOCKED, SERVER, UNKNOWN }

data class MalOutboxStatus(
    val pendingCount: Int,
    val lastError: MalSendError?,
)

enum class MalSendOutcome { DONE, RETRY }

interface MalOutboxLocalDataSource {
    suspend fun enqueue(change: MalChange)

    suspend fun enqueueAppOnlyEntries()

    suspend fun oldest(): QueuedMalChange?

    suspend fun markSent(item: QueuedMalChange)

    suspend fun drop(item: QueuedMalChange)

    suspend fun markFailed(
        item: QueuedMalChange,
        error: MalSendError,
    )

    suspend fun clearErrors()

    suspend fun clear()

    fun observeStatus(): Flow<MalOutboxStatus>
}

interface MalSendScheduler {
    fun requestSend()
}
