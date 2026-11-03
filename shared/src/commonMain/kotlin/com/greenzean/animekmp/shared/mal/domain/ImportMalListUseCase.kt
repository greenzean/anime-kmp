package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.domain.ImportSummary
import com.greenzean.animekmp.shared.importer.domain.MergeMalEntryUseCase
import com.greenzean.animekmp.shared.importer.domain.MergeOutcome
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler

private const val PAGE_SIZE = 100

class ImportMalListUseCase(
    private val api: MalListApi,
    private val throttle: MalThrottle,
    private val merge: MergeMalEntryUseCase,
    private val progress: MalImportProgressStore,
    private val scheduler: SyncScheduler,
    private val queueAppOnly: QueueAppOnlyEntriesUseCase,
) {
    suspend operator fun invoke(onProgress: (done: Int) -> Unit): Result<ImportSummary, MalError> {
        var offset = progress.offset()
        var added = 0
        var conflicts = 0
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
                when (merge(item.entry, item.imageUrl, markOnMal = true)) {
                    MergeOutcome.Added -> added++
                    MergeOutcome.Conflict -> conflicts++
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
        if (added > 0) scheduler.requestSync()
        queueAppOnly()
        return Result.Success(ImportSummary(added = added, conflicts = conflicts, invalid = 0))
    }
}
