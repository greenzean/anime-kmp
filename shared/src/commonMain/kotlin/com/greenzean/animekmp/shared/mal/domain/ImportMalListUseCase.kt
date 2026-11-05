package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.domain.ImportSummary
import com.greenzean.animekmp.shared.importer.domain.MergeMalEntryUseCase
import com.greenzean.animekmp.shared.importer.domain.MergeOutcome
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlin.time.Clock

private const val PAGE_SIZE = 100

class ImportMalListUseCase(
    private val api: MalListApi,
    private val throttle: MalThrottle,
    private val merge: MergeMalEntryUseCase,
    private val progress: MalImportProgressStore,
    private val scheduler: SyncScheduler,
    private val queueAppOnly: QueueAppOnlyEntriesUseCase,
    private val queueMalChange: QueueMalChangeUseCase,
    private val pullStore: MalPullStore,
    private val baseStore: MalBaseStore,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(onProgress: (done: Int) -> Unit): Result<ImportSummary, MalError> {
        val firstImport = pullStore.lastPulledAt() == null
        var offset = progress.offset()
        var added = 0
        var conflicts = 0
        var tookMal = 0
        var seen = offset
        onProgress(seen)
        var hasNext = true
        while (hasNext) {
            val page =
                when (val result = throttle.run { api.page(offset, PAGE_SIZE) }) {
                    is Result.Success -> result.data
                    is Result.Error -> return Result.Error(result.error)
                }
            page.items.forEach { item ->
                val malChanged = baseStore.get(item.entry.malId) != item.entry.toBase()
                baseStore.save(item.entry.malId, item.entry.toBase())
                when (
                    val outcome =
                        merge(
                            item.entry,
                            item.imageUrl,
                            markOnMal = true,
                            malUpdatedAt = item.updatedAt,
                            malChanged = malChanged,
                        )
                ) {
                    MergeOutcome.Added -> added++
                    MergeOutcome.Conflict -> conflicts++
                    MergeOutcome.TookMal -> tookMal++
                    is MergeOutcome.KeptLocal -> if (firstImport) queueMalChange(outcome.change)
                    MergeOutcome.Unchanged -> Unit
                }
            }
            offset += PAGE_SIZE
            seen += page.items.size
            progress.setOffset(offset)
            onProgress(seen)
            hasNext = page.hasNext
        }
        progress.clear()
        if (added + tookMal > 0) scheduler.requestSync()
        queueAppOnly()
        pullStore.setLastPulledAt(clock.now())
        return Result.Success(ImportSummary(added = added, conflicts = conflicts, invalid = 0))
    }
}
