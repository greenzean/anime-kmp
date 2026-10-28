package com.greenzean.animekmp.shared.library.domain

sealed interface EntryEdit {
    data class Status(
        val status: WatchStatus,
    ) : EntryEdit

    data class Progress(
        val progress: Int,
    ) : EntryEdit

    data class Score(
        val score: Int?,
    ) : EntryEdit

    data object Increment : EntryEdit
}
