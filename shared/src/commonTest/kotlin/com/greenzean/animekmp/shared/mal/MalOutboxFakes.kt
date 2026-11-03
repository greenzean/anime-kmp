package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalAuthApi
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalOutboxLocalDataSource
import com.greenzean.animekmp.shared.mal.domain.MalOutboxStatus
import com.greenzean.animekmp.shared.mal.domain.MalSendError
import com.greenzean.animekmp.shared.mal.domain.MalSendScheduler
import com.greenzean.animekmp.shared.mal.domain.MalTokens
import com.greenzean.animekmp.shared.mal.domain.QueueAppOnlyEntriesUseCase
import com.greenzean.animekmp.shared.mal.domain.QueueMalChangeUseCase
import com.greenzean.animekmp.shared.mal.domain.QueuedMalChange
import com.greenzean.animekmp.shared.sync.domain.ChangesPage
import com.greenzean.animekmp.shared.sync.domain.PushResult
import com.greenzean.animekmp.shared.sync.domain.SyncApi
import com.greenzean.animekmp.shared.sync.domain.SyncChange
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import com.greenzean.animekmp.shared.sync.domain.SyncLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.time.Instant

class FakeMalOutbox(
    private val log: MutableList<String> = mutableListOf(),
) : MalOutboxLocalDataSource {
    private class Row(
        val seq: Long,
        val change: MalChange,
        var error: MalSendError? = null,
    )

    private val rows = mutableMapOf<Int, Row>()
    private var lastSeq = 0L
    private val status = MutableStateFlow(MalOutboxStatus(0, null))

    val enqueued = mutableListOf<MalChange>()
    val sent = mutableListOf<MalChange>()
    val dropped = mutableListOf<MalChange>()
    val failures = mutableListOf<Pair<MalChange, MalSendError>>()
    var appOnlyEnqueues = 0
    var errorClears = 0
    var clears = 0
    var heldBack: (Int) -> Boolean = { false }

    val pending: List<MalChange> get() = rows.values.sortedBy { it.seq }.map { it.change }

    fun errorOf(malId: Int): MalSendError? = rows[malId]?.error

    fun emit(
        pendingCount: Int,
        lastError: MalSendError? = null,
    ) {
        status.value = MalOutboxStatus(pendingCount, lastError)
    }

    private fun refresh() {
        status.value = MalOutboxStatus(rows.size, rows.values.sortedBy { it.seq }.firstNotNullOfOrNull { it.error })
    }

    override suspend fun enqueue(change: MalChange) {
        log += "enqueue:${change.malId}"
        enqueued += change
        rows[change.malId] = Row(++lastSeq, change)
        refresh()
    }

    override suspend fun enqueueAppOnlyEntries() {
        log += "enqueueAppOnly"
        appOnlyEnqueues++
    }

    override suspend fun oldest(): QueuedMalChange? =
        rows.values
            .filterNot {
                heldBack(
                    it.change.malId,
                )
            }.minByOrNull { it.seq }
            ?.let { QueuedMalChange(it.seq, it.change) }

    override suspend fun isPending(malId: Int): Boolean = rows.containsKey(malId)

    override suspend fun markSent(item: QueuedMalChange) {
        sent += item.change
        if (rows[item.change.malId]?.seq == item.seq) rows.remove(item.change.malId)
        refresh()
    }

    override suspend fun drop(item: QueuedMalChange) {
        dropped += item.change
        if (rows[item.change.malId]?.seq == item.seq) rows.remove(item.change.malId)
        refresh()
    }

    override suspend fun markFailed(
        item: QueuedMalChange,
        error: MalSendError,
    ) {
        failures += item.change to error
        rows[item.change.malId]?.takeIf { it.seq == item.seq }?.error = error
        refresh()
    }

    override suspend fun clearErrors() {
        log += "clearErrors"
        errorClears++
        rows.values.forEach { it.error = null }
        refresh()
    }

    override suspend fun clear() {
        log += "clear"
        clears++
        rows.clear()
        refresh()
    }

    override fun observeStatus(): Flow<MalOutboxStatus> = status
}

class FakeMalSendScheduler(
    private val log: MutableList<String> = mutableListOf(),
) : MalSendScheduler {
    var requests = 0

    override fun requestSend() {
        log += "requestSend"
        requests++
    }
}

private val OUTBOX_NOW = Instant.parse("2026-10-26T12:00:00Z")

private class OutboxFixedClock : Clock {
    override fun now(): Instant = OUTBOX_NOW
}

private class UnusedMalAuthApi : MalAuthApi {
    override suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
    ): Result<MalTokens, MalAuthError> = Result.Error(MalAuthError.UNKNOWN)

    override suspend fun refresh(refreshToken: String): Result<MalTokens, MalAuthError> =
        Result.Error(MalAuthError.UNKNOWN)
}

fun malAuthManager(tokenStore: InMemoryMalTokenStore): MalAuthManager =
    MalAuthManager(
        MalConfig("client-1", "https://app.example.test/mal-callback"),
        UnusedMalAuthApi(),
        tokenStore,
        ScriptedMalAuthorizer(),
        { size -> ByteArray(size) },
        OutboxFixedClock(),
    )

private class NoLocalSync : SyncLocalDataSource {
    override suspend fun pendingChanges(limit: Int): List<SyncChange> = emptyList()

    override suspend fun markSynced(change: SyncChange) = Unit

    override suspend fun rewriteId(
        oldId: String,
        entryId: String,
    ) = Unit

    override suspend fun getCursor(): String? = null

    override suspend fun applyPage(
        changes: List<SyncChange>,
        nextCursor: String,
    ): Int = 0

    override suspend fun parkedMissingAnime(): List<Int> = emptyList()

    override suspend fun retryParked(): Int = 0
}

class LoggingSyncApi(
    private val log: MutableList<String>,
    var error: DataError.Network? = null,
) : SyncApi {
    override suspend fun push(changes: List<SyncChange>): Result<List<PushResult>, DataError.Network> =
        Result.Success(emptyList())

    override suspend fun pull(
        cursor: String?,
        limit: Int,
    ): Result<ChangesPage, DataError.Network> {
        log += "sync"
        return error?.let { Result.Error(it) } ?: Result.Success(ChangesPage(cursor ?: "0", false, emptyList()))
    }
}

class MalOutboxHarness(
    loggedIn: Boolean = true,
) {
    val log = mutableListOf<String>()
    val outbox = FakeMalOutbox(log)
    val scheduler = FakeMalSendScheduler(log)
    val tokenStore = InMemoryMalTokenStore(if (loggedIn) validMalSession() else null)
    val auth = malAuthManager(tokenStore)
    val syncApi = LoggingSyncApi(log)
    val syncEngine = SyncEngine(syncApi, NoLocalSync())
    val queue = QueueMalChangeUseCase(outbox, auth, scheduler)
    val queueAppOnly = QueueAppOnlyEntriesUseCase(outbox, auth, scheduler, syncEngine)
}

fun idleQueueMalChange(): QueueMalChangeUseCase = MalOutboxHarness(loggedIn = false).queue

fun idleQueueAppOnlyEntries(): QueueAppOnlyEntriesUseCase = MalOutboxHarness(loggedIn = false).queueAppOnly
