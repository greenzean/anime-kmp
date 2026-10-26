package com.greenzean.animekmp.composeapp.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.search.presentation.SearchResultUi
import com.greenzean.animekmp.shared.search.presentation.SearchState
import com.greenzean.animekmp.shared.search.presentation.SearchStatus

private val previewResults =
    listOf(
        SearchResultUi(52991, "Frieren: Beyond Journey's End", null, inLibrary = true),
        SearchResultUi(9253, "Steins;Gate", null, inLibrary = false),
        SearchResultUi(1, "Cowboy Bebop", null, inLibrary = false),
    )

@Preview
@Composable
private fun SearchIdleLightPreview() = SearchPreview(SearchState(), darkTheme = false)

@Preview
@Composable
private fun SearchIdleDarkPreview() = SearchPreview(SearchState(), darkTheme = true)

@Preview
@Composable
private fun SearchLoadingLightPreview() =
    SearchPreview(SearchState(query = "frieren", status = SearchStatus.Loading), darkTheme = false)

@Preview
@Composable
private fun SearchLoadingDarkPreview() =
    SearchPreview(SearchState(query = "frieren", status = SearchStatus.Loading), darkTheme = true)

@Preview
@Composable
private fun SearchResultsLightPreview() =
    SearchPreview(SearchState("frieren", previewResults, SearchStatus.Results), darkTheme = false)

@Preview
@Composable
private fun SearchResultsDarkPreview() =
    SearchPreview(SearchState("frieren", previewResults, SearchStatus.Results), darkTheme = true)

@Composable
internal fun SearchPreview(
    state: SearchState,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) { SearchScreen(state = state, onAction = {}) }
}
