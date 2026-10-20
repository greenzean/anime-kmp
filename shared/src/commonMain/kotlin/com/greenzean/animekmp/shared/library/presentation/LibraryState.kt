package com.greenzean.animekmp.shared.library.presentation

data class LibraryState(
    val entries: List<EntryUi> = emptyList(),
    val addable: List<AnimeUi> = emptyList(),
)

sealed interface LibraryAction {
    data class OnAddClick(
        val animeId: Long,
    ) : LibraryAction
}
