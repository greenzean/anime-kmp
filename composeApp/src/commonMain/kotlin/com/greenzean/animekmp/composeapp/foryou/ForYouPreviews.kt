package com.greenzean.animekmp.composeapp.foryou

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus
import com.greenzean.animekmp.shared.search.presentation.SearchResultUi
import com.greenzean.animekmp.shared.suggestions.presentation.SuggestionsState
import com.greenzean.animekmp.shared.suggestions.presentation.SuggestionsStatus

private val previewItems =
    listOf(
        SearchResultUi(52991, "Frieren: Beyond Journey's End", null, inLibrary = false),
        SearchResultUi(9253, "Steins;Gate", null, inLibrary = false),
        SearchResultUi(1, "Cowboy Bebop", null, inLibrary = false),
    )

internal fun stateOf(status: SuggestionsStatus) = SuggestionsState(status = status)

@Preview
@Composable
private fun ForYouConsentLightPreview() =
    ForYouPreview(stateOf(SuggestionsStatus.NotConnected), darkTheme = false, malStatus = MalAccountStatus.NeedsConsent)

@Preview
@Composable
private fun ForYouConsentDarkPreview() =
    ForYouPreview(stateOf(SuggestionsStatus.NotConnected), darkTheme = true, malStatus = MalAccountStatus.NeedsConsent)

@Preview
@Composable
private fun ForYouNotConnectedLightPreview() =
    ForYouPreview(stateOf(SuggestionsStatus.NotConnected), darkTheme = false, malStatus = MalAccountStatus.NotConnected)

@Preview
@Composable
private fun ForYouNotConnectedDarkPreview() =
    ForYouPreview(stateOf(SuggestionsStatus.NotConnected), darkTheme = true, malStatus = MalAccountStatus.NotConnected)

@Preview
@Composable
private fun ForYouLoadingLightPreview() = ForYouPreview(stateOf(SuggestionsStatus.Loading), darkTheme = false)

@Preview
@Composable
private fun ForYouLoadingDarkPreview() = ForYouPreview(stateOf(SuggestionsStatus.Loading), darkTheme = true)

@Preview
@Composable
private fun ForYouLoadedLightPreview() =
    ForYouPreview(SuggestionsState(previewItems, SuggestionsStatus.Loaded, canLoadMore = true), darkTheme = false)

@Preview
@Composable
private fun ForYouLoadedDarkPreview() =
    ForYouPreview(SuggestionsState(previewItems, SuggestionsStatus.Loaded, canLoadMore = true), darkTheme = true)

@Composable
internal fun ForYouPreview(
    state: SuggestionsState,
    darkTheme: Boolean,
    malStatus: MalAccountStatus = MalAccountStatus.NotConnected,
) {
    ThemedPreview(darkTheme) {
        ForYouScreen(state = state, malState = MalAccountState(malStatus), onAction = {}, onMalAction = {})
    }
}
