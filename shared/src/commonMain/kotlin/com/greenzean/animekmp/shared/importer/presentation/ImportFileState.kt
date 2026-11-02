package com.greenzean.animekmp.shared.importer.presentation

import com.greenzean.animekmp.shared.importer.domain.ImportError

sealed interface ImportStatus {
    data object Idle : ImportStatus

    data class Importing(
        val progress: Float,
    ) : ImportStatus

    data class Done(
        val added: Int,
        val keptYours: Int,
        val invalid: Int,
    ) : ImportStatus

    data class Error(
        val reason: ImportError,
    ) : ImportStatus
}

data class ImportFileState(
    val status: ImportStatus = ImportStatus.Idle,
)

sealed interface ImportFileAction {
    data object OnImportClick : ImportFileAction

    data class OnFileChosen(
        val source: String?,
    ) : ImportFileAction
}

sealed interface ImportFileEvent {
    data object LaunchOpenDialog : ImportFileEvent
}
