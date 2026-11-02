package com.greenzean.animekmp.shared.mal.domain

sealed interface MalImportStatus {
    data object Idle : MalImportStatus

    data class Importing(
        val done: Int,
    ) : MalImportStatus

    data class Done(
        val added: Int,
        val conflicts: Int,
    ) : MalImportStatus

    data object Error : MalImportStatus
}
