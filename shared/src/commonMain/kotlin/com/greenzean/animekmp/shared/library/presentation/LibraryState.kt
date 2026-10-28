package com.greenzean.animekmp.shared.library.presentation

import com.greenzean.animekmp.shared.library.domain.LibrarySort
import com.greenzean.animekmp.shared.library.domain.WatchStatus

data class LibraryState(
    val entries: List<EntryUi> = emptyList(),
    val query: String = "",
    val statusFilter: WatchStatus? = null,
    val sort: LibrarySort = LibrarySort.LAST_UPDATED,
    val isLibraryEmpty: Boolean = true,
)

sealed interface LibraryAction {
    data class OnEntryClick(
        val malId: Int,
    ) : LibraryAction

    data class OnIncrementClick(
        val malId: Int,
    ) : LibraryAction

    data object OnSettingsClick : LibraryAction

    data class OnQueryChange(
        val query: String,
    ) : LibraryAction

    data class OnStatusFilterClick(
        val status: WatchStatus?,
    ) : LibraryAction

    data class OnSortChange(
        val sort: LibrarySort,
    ) : LibraryAction
}

sealed interface LibraryEvent {
    data class NavigateToDetails(
        val malId: Int,
    ) : LibraryEvent

    data object NavigateToSettings : LibraryEvent
}
