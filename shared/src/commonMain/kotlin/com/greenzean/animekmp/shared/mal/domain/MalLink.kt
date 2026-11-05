package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import com.greenzean.animekmp.shared.sync.domain.SyncResult
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

interface LinkedPlatformsRepository {
    fun observeMalLinked(): Flow<Boolean>

    suspend fun refresh(): EmptyResult<DataError>

    suspend fun linkMal(): EmptyResult<DataError>

    suspend fun linkMalIf(condition: () -> Boolean): EmptyResult<DataError>

    suspend fun unlinkMal(): EmptyResult<DataError>
}

data class MalLinkState(
    val linkedToAccount: Boolean,
    val linkedElsewhere: Boolean,
)

class ObserveMalLinkStateUseCase(
    private val repository: LinkedPlatformsRepository,
    private val auth: MalAuthManager,
    private val store: MalAccountStore,
) {
    operator fun invoke(): Flow<MalLinkState> =
        combine(repository.observeMalLinked(), auth.isLoggedIn, store.notOnThisDevice) { linked, connected, dismissed ->
            MalLinkState(
                linkedToAccount = linked,
                linkedElsewhere = linked && !connected && !dismissed,
            )
        }
}

class RefreshLinkedPlatformsUseCase(
    private val repository: LinkedPlatformsRepository,
) {
    suspend operator fun invoke(): EmptyResult<DataError> = repository.refresh()
}

class RequestMalLinkUseCase(
    private val store: MalAccountStore,
    private val scheduler: SyncScheduler,
) {
    operator fun invoke() {
        store.setLinkPending(true)
        scheduler.requestSync()
    }
}

class RetryPendingMalLinkUseCase(
    private val repository: LinkedPlatformsRepository,
    private val store: MalAccountStore,
) {
    suspend operator fun invoke() {
        if (!store.linkPending.value) return
        val result = repository.linkMalIf { store.linkPending.value }
        if (result is Result.Success) store.setLinkPending(false)
    }
}

class BackgroundSyncUseCase(
    private val engine: SyncEngine,
    private val retryPendingMalLink: RetryPendingMalLinkUseCase,
    private val refreshLinkedPlatforms: RefreshLinkedPlatformsUseCase,
) {
    suspend operator fun invoke(): SyncResult {
        val result = engine.sync()
        if (result is SyncResult.Success) {
            retryPendingMalLink()
            refreshLinkedPlatforms()
        }
        return result
    }
}

class LinkMalIfConnectedUseCase(
    private val auth: MalAuthManager,
    private val requestLink: RequestMalLinkUseCase,
    private val store: MalAccountStore,
) {
    suspend operator fun invoke() {
        store.setNotOnThisDevice(false)
        if (auth.isLoggedIn.first()) requestLink()
    }
}

class UnlinkMalFromAccountUseCase(
    private val repository: LinkedPlatformsRepository,
    private val store: MalAccountStore,
) {
    suspend operator fun invoke(): EmptyResult<DataError> {
        val result = repository.unlinkMal()
        if (result is Result.Success) store.setLinkPending(false)
        return result
    }
}

class DismissMalPromptUseCase(
    private val store: MalAccountStore,
) {
    operator fun invoke(): Unit = store.setNotOnThisDevice(true)
}
