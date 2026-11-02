package com.greenzean.animekmp.shared.export.presentation

import com.greenzean.animekmp.shared.export.domain.ExportError

sealed interface ExportStatus {
    data object Idle : ExportStatus

    data object Exporting : ExportStatus

    data class Done(
        val count: Int,
    ) : ExportStatus

    data class Error(
        val reason: ExportError,
    ) : ExportStatus
}

data class ExportState(
    val status: ExportStatus = ExportStatus.Idle,
)

sealed interface ExportAction {
    data object OnExportClick : ExportAction

    data class OnFileChosen(
        val destination: String?,
    ) : ExportAction
}

sealed interface ExportEvent {
    data class LaunchSaveDialog(
        val fileName: String,
    ) : ExportEvent
}
