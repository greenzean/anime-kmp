package com.greenzean.animekmp.shared.importer.presentation

import com.greenzean.animekmp.shared.importer.domain.ImportError

sealed interface ImportStatus {
    data object Idle : ImportStatus

    data class Importing(
        val progress: Float,
    ) : ImportStatus

    data class Done(
        val added: Int,
        val conflicts: Int,
        val invalid: Int,
    ) : ImportStatus

    data class Error(
        val reason: ImportError,
    ) : ImportStatus
}

data class ImportFileState(
    val status: ImportStatus = ImportStatus.Idle,
    val conflictCount: Int = 0,
)

sealed interface ImportFileAction {
    data object OnImportClick : ImportFileAction

    data class OnFileChosen(
        val source: String?,
    ) : ImportFileAction

    data object OnConflictsClick : ImportFileAction
}

sealed interface ImportFileEvent {
    data object LaunchOpenDialog : ImportFileEvent

    data object NavigateToConflicts : ImportFileEvent
}
