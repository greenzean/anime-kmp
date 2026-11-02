package com.greenzean.animekmp.shared.mal.presentation

import com.greenzean.animekmp.shared.mal.domain.MalImportStatus

data class MalImportState(
    val status: MalImportStatus = MalImportStatus.Idle,
)

sealed interface MalImportAction {
    data object OnImportClick : MalImportAction

    data object OnRetryClick : MalImportAction
}
