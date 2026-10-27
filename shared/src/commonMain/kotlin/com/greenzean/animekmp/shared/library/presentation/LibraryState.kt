package com.greenzean.animekmp.shared.library.presentation

data class LibraryState(
    val entries: List<EntryUi> = emptyList(),
)

sealed interface LibraryAction {
    data class OnEntryClick(
        val malId: Int,
    ) : LibraryAction

    data object OnSettingsClick : LibraryAction
}

sealed interface LibraryEvent {
    data class NavigateToDetails(
        val malId: Int,
    ) : LibraryEvent

    data object NavigateToSettings : LibraryEvent
}
