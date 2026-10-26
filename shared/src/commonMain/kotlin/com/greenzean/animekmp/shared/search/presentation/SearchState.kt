package com.greenzean.animekmp.shared.search.presentation

enum class SearchStatus { Idle, Loading, Results, Empty, Offline, Error }

data class SearchResultUi(
    val malId: Int,
    val title: String,
    val imageUrl: String?,
    val inLibrary: Boolean,
)

data class SearchState(
    val query: String = "",
    val results: List<SearchResultUi> = emptyList(),
    val status: SearchStatus = SearchStatus.Idle,
    val canLoadMore: Boolean = false,
)

sealed interface SearchAction {
    data class OnQueryChange(
        val text: String,
    ) : SearchAction

    data class OnAddClick(
        val malId: Int,
    ) : SearchAction

    data object OnRetryClick : SearchAction

    data object OnLoadMore : SearchAction
}
