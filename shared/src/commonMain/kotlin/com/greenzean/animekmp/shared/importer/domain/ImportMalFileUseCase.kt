package com.greenzean.animekmp.shared.importer.domain

import com.greenzean.animekmp.shared.sync.domain.SyncScheduler

class ImportMalFileUseCase(
    private val merge: MergeMalEntryUseCase,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(
        file: MalExportFile,
        onProgress: (Float) -> Unit,
    ): ImportSummary {
        var added = 0
        var conflictCount = 0
        val total = file.entries.size
        file.entries.forEachIndexed { index, entry ->
            when (merge(entry, imageUrl = null, markOnMal = false)) {
                MergeOutcome.Added -> added++
                MergeOutcome.Conflict -> conflictCount++
                is MergeOutcome.KeptLocal, MergeOutcome.TookMal, MergeOutcome.Unchanged -> Unit
            }
            onProgress((index + 1).toFloat() / total)
        }
        if (added > 0) scheduler.requestSync()
        return ImportSummary(added = added, conflicts = conflictCount, invalid = file.invalid)
    }
}
