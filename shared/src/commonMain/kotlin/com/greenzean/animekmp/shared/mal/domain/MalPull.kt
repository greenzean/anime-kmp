package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.conflict.domain.ConflictLocalDataSource
import com.greenzean.animekmp.shared.conflict.domain.EntryValues
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.domain.MalExportEntry
import com.greenzean.animekmp.shared.importer.domain.OnMalLocalDataSource
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val PAGE_SIZE = 100
private val STALE_AFTER = 15.minutes

enum class MalPullResult { DONE, SKIPPED, RETRY }

data class MalPullInfo(
    val lastSyncedAt: Instant?,
    val syncing: Boolean,
)

interface MalPullStore {
    fun observeLastPulledAt(): Flow<Instant?>

    suspend fun lastPulledAt(): Instant?

    suspend fun setLastPulledAt(at: Instant)

    suspend fun clear()
}

interface MalPullScheduler {
    fun schedulePeriodic()
}

class PullMalChangesUseCase(
    private val api: MalListApi,
    private val throttle: MalThrottle,
    private val auth: MalAuthManager,
    private val library: LibraryLocalDataSource,
    private val conflicts: ConflictLocalDataSource,
    private val onMal: OnMalLocalDataSource,
    private val outbox: MalOutboxLocalDataSource,
    private val pullStore: MalPullStore,
    private val importProgress: MalImportProgressStore,
    private val syncScheduler: SyncScheduler,
    private val baseStore: MalBaseStore,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(): MalPullResult =
        if (!auth.isLoggedIn.first() || importProgress.offset() > 0 || pullStore.lastPulledAt() == null) {
            MalPullResult.SKIPPED
        } else {
            pullAll()
        }

    private suspend fun pullAll(): MalPullResult {
        val seen = HashSet<Int>()
        var offset = 0
        var hasNext = true
        var changed = false
        while (hasNext) {
            val page =
                when (val result = throttle.run { api.page(offset, PAGE_SIZE) }) {
                    is Result.Success -> result.data
                    is Result.Error -> return MalPullResult.RETRY
                }
            page.items.forEach { item ->
                seen += item.entry.malId
                if (applyItem(item)) changed = true
            }
            offset += PAGE_SIZE
            hasNext = page.hasNext
        }
        if (removeMissing(seen)) changed = true
        if (changed) syncScheduler.requestSync()
        pullStore.setLastPulledAt(clock.now())
        return MalPullResult.DONE
    }

    private suspend fun applyItem(item: MalListItem): Boolean {
        val entry = item.entry
        val local = library.getEntryIncludingRemoved(entry.malId)
        val observed = entry.toBase()
        val malChanged = baseStore.get(entry.malId) != observed
        val changed =
            if (local == null) {
                library.saveAnime(
                    Anime(
                        malId = entry.malId,
                        title = entry.title,
                        imageUrl = item.imageUrl,
                        episodes = entry.episodes,
                    ),
                )
                saveMalValues(entry)
                true
            } else {
                val removed = library.getEntry(entry.malId) == null
                val same =
                    !removed &&
                        local.status == entry.status &&
                        local.progress == entry.progress &&
                        local.score == entry.score
                when {
                    same -> {
                        conflicts.deleteConflict(entry.malId)
                        false
                    }

                    outbox.isPending(entry.malId) -> {
                        if (malChanged) {
                            conflicts.saveConflict(
                                entry.malId,
                                EntryValues(entry.status, entry.progress, entry.score, removed = false),
                            )
                        }
                        false
                    }

                    item.updatedAt?.let { it > local.updatedAt } == true -> {
                        saveMalValues(entry)
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
        baseStore.save(entry.malId, observed)
        onMal.markOnMal(entry.malId)
        return changed
    }

    private suspend fun saveMalValues(entry: MalExportEntry) {
        library.saveEntry(entry.malId, entry.status, entry.progress, entry.score)
        conflicts.deleteConflict(entry.malId)
    }

    private suspend fun removeMissing(seen: Set<Int>): Boolean {
        var changed = false
        onMal.activeOnMalIds().filter { it !in seen }.forEach { malId ->
            baseStore.delete(malId)
            val local = library.getEntry(malId) ?: return@forEach
            if (outbox.isPending(malId)) {
                conflicts.saveConflict(malId, EntryValues(local.status, local.progress, local.score, removed = true))
            } else {
                library.remove(malId)
                changed = true
            }
        }
        return changed
    }
}

class MalPullCoordinator(
    private val pull: PullMalChangesUseCase,
    private val importCoordinator: MalImportCoordinator,
    private val scope: CoroutineScope,
) {
    private val mutableSyncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = mutableSyncing.asStateFlow()

    private val mutex = Mutex()

    fun start() {
        if (mutableSyncing.value) return
        scope.launch { pull() }
    }

    suspend fun pull(): MalPullResult {
        if (importCoordinator.status.value is MalImportStatus.Importing) return MalPullResult.SKIPPED
        return mutex.withLock {
            mutableSyncing.value = true
            try {
                pull.invoke()
            } finally {
                mutableSyncing.value = false
            }
        }
    }
}

class ObserveMalPullUseCase(
    private val pullStore: MalPullStore,
    private val coordinator: MalPullCoordinator,
) {
    operator fun invoke(): Flow<MalPullInfo> =
        combine(pullStore.observeLastPulledAt(), coordinator.syncing) { last, syncing -> MalPullInfo(last, syncing) }
}

class SyncMalNowUseCase(
    private val coordinator: MalPullCoordinator,
) {
    operator fun invoke() = coordinator.start()
}

class PullMalIfStaleUseCase(
    private val auth: MalAuthManager,
    private val pullStore: MalPullStore,
    private val coordinator: MalPullCoordinator,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke() {
        if (!auth.isLoggedIn.first()) return
        val last = pullStore.lastPulledAt()
        if (last == null || clock.now() - last > STALE_AFTER) coordinator.start()
    }
}
