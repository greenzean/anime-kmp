package com.greenzean.animekmp.shared.suggestions.presentation

import com.greenzean.animekmp.shared.search.presentation.SearchResultUi

enum class SuggestionsStatus { NotConnected, Loading, Loaded, Empty, Offline, Error }

data class SuggestionsState(
    val items: List<SearchResultUi> = emptyList(),
    val status: SuggestionsStatus = SuggestionsStatus.Loading,
    val canLoadMore: Boolean = false,
)

sealed interface SuggestionsAction {
    data class OnAddClick(
        val malId: Int,
    ) : SuggestionsAction

    data class OnItemClick(
        val malId: Int,
    ) : SuggestionsAction

    data object OnRetryClick : SuggestionsAction

    data object OnLoadMore : SuggestionsAction
}

sealed interface SuggestionsEvent {
    data class NavigateToDetails(
        val malId: Int,
    ) : SuggestionsEvent
}
