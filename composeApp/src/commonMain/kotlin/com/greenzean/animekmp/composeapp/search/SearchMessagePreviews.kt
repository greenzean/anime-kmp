package com.greenzean.animekmp.composeapp.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.shared.search.presentation.SearchState
import com.greenzean.animekmp.shared.search.presentation.SearchStatus

@Preview
@Composable
private fun SearchEmptyLightPreview() =
    SearchPreview(SearchState(query = "zzzzzz", status = SearchStatus.Empty), darkTheme = false)

@Preview
@Composable
private fun SearchEmptyDarkPreview() =
    SearchPreview(SearchState(query = "zzzzzz", status = SearchStatus.Empty), darkTheme = true)

@Preview
@Composable
private fun SearchOfflineLightPreview() =
    SearchPreview(SearchState(query = "frieren", status = SearchStatus.Offline), darkTheme = false)

@Preview
@Composable
private fun SearchOfflineDarkPreview() =
    SearchPreview(SearchState(query = "frieren", status = SearchStatus.Offline), darkTheme = true)

@Preview
@Composable
private fun SearchErrorLightPreview() =
    SearchPreview(SearchState(query = "frieren", status = SearchStatus.Error), darkTheme = false)

@Preview
@Composable
private fun SearchErrorDarkPreview() =
    SearchPreview(SearchState(query = "frieren", status = SearchStatus.Error), darkTheme = true)
