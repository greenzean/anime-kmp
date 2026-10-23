package com.greenzean.animekmp.shared.auth.domain

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.sync.domain.AccountLibraryDataSource
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import com.greenzean.animekmp.shared.sync.domain.SyncResult
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.CancellationException

data class MergeSummary(
    val sent: Int,
    val rejected: Int,
    val total: Int,
)

class MergeLibraryUseCase(
    private val engine: SyncEngine,
    private val library: AccountLibraryDataSource,
    private val syncScheduler: SyncScheduler,
) {
    suspend operator fun invoke(): Result<MergeSummary, DataError> =
        when (val result = syncOrQueue()) {
            is SyncResult.Success -> {
                Result.Success(MergeSummary(result.pushed, result.rejected.size, library.activeCount()))
            }

            is SyncResult.Failure -> {
                syncScheduler.requestSync()
                Result.Error(result.error)
            }
        }

    private suspend fun syncOrQueue(): SyncResult =
        try {
            engine.sync()
        } catch (cancelled: CancellationException) {
            syncScheduler.requestSync()
            throw cancelled
        }
}
