package com.greenzean.animekmp.shared.importer.domain

import com.greenzean.animekmp.shared.core.domain.Error
import com.greenzean.animekmp.shared.library.domain.WatchStatus

enum class ImportError : Error {
    Unreadable,
    NotMalExport,
}

data class ImportSummary(
    val added: Int,
    val conflicts: Int,
    val invalid: Int,
)

data class MalExportEntry(
    val malId: Int,
    val title: String,
    val episodes: Int?,
    val status: WatchStatus,
    val progress: Int,
    val score: Int?,
)

data class MalExportFile(
    val entries: List<MalExportEntry>,
    val invalid: Int,
)
