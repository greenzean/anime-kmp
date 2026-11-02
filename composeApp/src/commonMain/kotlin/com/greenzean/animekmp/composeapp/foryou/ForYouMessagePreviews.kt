package com.greenzean.animekmp.composeapp.foryou

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.shared.suggestions.presentation.SuggestionsStatus

@Preview
@Composable
private fun ForYouEmptyLightPreview() = ForYouPreview(stateOf(SuggestionsStatus.Empty), darkTheme = false)

@Preview
@Composable
private fun ForYouEmptyDarkPreview() = ForYouPreview(stateOf(SuggestionsStatus.Empty), darkTheme = true)

@Preview
@Composable
private fun ForYouOfflineLightPreview() = ForYouPreview(stateOf(SuggestionsStatus.Offline), darkTheme = false)

@Preview
@Composable
private fun ForYouOfflineDarkPreview() = ForYouPreview(stateOf(SuggestionsStatus.Offline), darkTheme = true)

@Preview
@Composable
private fun ForYouErrorLightPreview() = ForYouPreview(stateOf(SuggestionsStatus.Error), darkTheme = false)

@Preview
@Composable
private fun ForYouErrorDarkPreview() = ForYouPreview(stateOf(SuggestionsStatus.Error), darkTheme = true)

@Preview
@Composable
private fun ForYouAllAddedLightPreview() = ForYouPreview(stateOf(SuggestionsStatus.Loaded), darkTheme = false)

@Preview
@Composable
private fun ForYouAllAddedDarkPreview() = ForYouPreview(stateOf(SuggestionsStatus.Loaded), darkTheme = true)
