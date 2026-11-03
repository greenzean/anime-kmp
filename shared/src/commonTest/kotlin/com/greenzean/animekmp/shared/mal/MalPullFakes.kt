package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.malEntry
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalListItem
import com.greenzean.animekmp.shared.mal.domain.MalListPage
import com.greenzean.animekmp.shared.mal.domain.MalPullCoordinator
import com.greenzean.animekmp.shared.mal.domain.MalPullStore
import com.greenzean.animekmp.shared.mal.domain.MalThrottle
import com.greenzean.animekmp.shared.mal.domain.PullMalChangesUseCase
import com.greenzean.animekmp.shared.mal.domain.SendMalChangesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.time.Instant

val PULL_NOW: Instant = Instant.parse("2026-11-03T12:00:00Z")

class PullTestClock(
    var current: Instant = PULL_NOW,
) : Clock {
    override fun now(): Instant = current
}

class FakeMalPullStore(
    initial: Instant? = null,
) : MalPullStore {
    private val current = MutableStateFlow(initial)
    val saved = mutableListOf<Instant>()
    var clears = 0

    override fun observeLastPulledAt(): Flow<Instant?> = current

    override suspend fun lastPulledAt(): Instant? = current.value

    override suspend fun setLastPulledAt(at: Instant) {
        saved += at
        current.value = at
    }

    override suspend fun clear() {
        clears++
        current.value = null
    }
}

fun pullItem(
    malId: Int,
    status: WatchStatus = WatchStatus.COMPLETED,
    progress: Int = 26,
    score: Int? = 9,
    updatedAt: Instant? = null,
) = MalListItem(
    malEntry(malId = malId, title = "Title $malId", status = status, progress = progress, score = score),
    "img-$malId",
    updatedAt,
)

fun pullPage(
    vararg items: MalListItem,
    hasNext: Boolean = false,
): Result<MalListPage, MalApiError> = Result.Success(MalListPage(items.toList(), hasNext))

class MalPullHarness(
    scope: CoroutineScope,
    loggedIn: Boolean = true,
    initialOffset: Int = 0,
    lastPulledAt: Instant? = null,
) {
    val clock = PullTestClock()
    val import = MalImportHarness(scope, initialOffset)
    val api = import.api
    val library = import.local
    val conflicts = import.conflicts
    val onMal = import.onMal
    val syncScheduler = import.scheduler
    val outbox = FakeMalOutbox()
    val pullStore = FakeMalPullStore(lastPulledAt)
    val baseStore = import.baseStore
    val tokenStore = InMemoryMalTokenStore(if (loggedIn) validMalSession() else null)
    val auth = malAuthManager(tokenStore)
    val throttle = MalThrottle(clock)
    val pull =
        PullMalChangesUseCase(
            api,
            throttle,
            auth,
            library,
            conflicts,
            onMal,
            outbox,
            pullStore,
            import.store,
            syncScheduler,
            baseStore,
            clock,
        )
    val coordinator = MalPullCoordinator(pull, import.coordinator, scope)
    val send = SendMalChangesUseCase(outbox, api, throttle, auth, baseStore, pullStore, coordinator, clock)
}
