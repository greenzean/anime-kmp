package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.conflict.FakeConflictLocalDataSource
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.FakeOnMalLocalDataSource
import com.greenzean.animekmp.shared.importer.domain.MergeMalEntryUseCase
import com.greenzean.animekmp.shared.importer.malEntry
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.mal.domain.ImportMalListUseCase
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.MalImportCoordinator
import com.greenzean.animekmp.shared.mal.domain.MalImportProgressStore
import com.greenzean.animekmp.shared.mal.domain.MalListApi
import com.greenzean.animekmp.shared.mal.domain.MalListItem
import com.greenzean.animekmp.shared.mal.domain.MalListPage
import com.greenzean.animekmp.shared.mal.domain.MalThrottle
import com.greenzean.animekmp.shared.mal.domain.ObserveMalImportUseCase
import com.greenzean.animekmp.shared.mal.domain.StartMalImportUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

const val IMPORT_PAGE_SIZE = 100

fun listItems(
    from: Int,
    count: Int,
): List<MalListItem> =
    (from until from + count).map { MalListItem(malEntry(malId = it + 1, title = "Title ${it + 1}"), "img-${it + 1}") }

fun listPage(
    from: Int,
    count: Int,
    hasNext: Boolean,
): Result<MalListPage, MalApiError> = Result.Success(MalListPage(listItems(from, count), hasNext))

class FakeMalListApi : MalListApi {
    val results = mutableMapOf<Int, Result<MalListPage, MalApiError>>()
    val gates = mutableMapOf<Int, CompletableDeferred<Unit>>()
    val calls = mutableListOf<Pair<Int, Int>>()

    val offsets: List<Int> get() = calls.map { it.first }

    val updates = mutableListOf<MalChange.Upsert>()
    val deletes = mutableListOf<Int>()
    val updateResults = mutableMapOf<Int, Result<Unit, MalApiError>>()
    val deleteResults = mutableMapOf<Int, Result<Unit, MalApiError>>()
    var onUpdate: suspend (Int) -> Unit = {}

    override suspend fun update(
        malId: Int,
        status: WatchStatus,
        progress: Int,
        score: Int?,
    ): Result<Unit, MalApiError> {
        updates += MalChange.Upsert(malId, status, progress, score)
        onUpdate(malId)
        return updateResults[malId] ?: Result.Success(Unit)
    }

    override suspend fun delete(malId: Int): Result<Unit, MalApiError> {
        deletes += malId
        return deleteResults[malId] ?: Result.Success(Unit)
    }

    override suspend fun page(
        offset: Int,
        limit: Int,
    ): Result<MalListPage, MalApiError> {
        calls += offset to limit
        gates[offset]?.await()
        return results[offset] ?: Result.Success(MalListPage(emptyList(), hasNext = false))
    }
}

class FakeMalImportProgressStore(
    initial: Int = 0,
) : MalImportProgressStore {
    private var current = initial
    val saved = mutableListOf<Int>()
    var clears = 0

    override suspend fun offset(): Int = current

    override suspend fun setOffset(offset: Int) {
        saved += offset
        current = offset
    }

    override suspend fun clear() {
        clears++
        current = 0
    }
}

private val IMPORT_NOW = Instant.parse("2026-10-26T12:00:00Z")

private class ImportSteppingClock : Clock {
    private var current = IMPORT_NOW

    override fun now(): Instant {
        current += 1.seconds
        return current
    }
}

class MalImportHarness(
    scope: CoroutineScope,
    initialOffset: Int = 0,
    clock: Clock = ImportSteppingClock(),
) {
    val local = FakeLibraryLocalDataSource()
    val conflicts = FakeConflictLocalDataSource()
    val onMal = FakeOnMalLocalDataSource()
    val store = FakeMalImportProgressStore(initialOffset)
    val scheduler = FakeSyncScheduler()
    val api = FakeMalListApi()
    val pullStore = FakeMalPullStore()
    val baseStore = FakeMalBaseStore()
    val throttle = MalThrottle(clock)
    val outboxHarness = MalOutboxHarness()
    val merge = MergeMalEntryUseCase(local, conflicts, onMal, outboxHarness.outbox)
    val importList =
        ImportMalListUseCase(
            api,
            throttle,
            merge,
            store,
            scheduler,
            outboxHarness.queueAppOnly,
            outboxHarness.queue,
            pullStore = pullStore,
            baseStore = baseStore,
            clock = clock,
        )
    val coordinator = MalImportCoordinator(importList, scope)
    val observe = ObserveMalImportUseCase(coordinator)
    val start = StartMalImportUseCase(coordinator)
}
