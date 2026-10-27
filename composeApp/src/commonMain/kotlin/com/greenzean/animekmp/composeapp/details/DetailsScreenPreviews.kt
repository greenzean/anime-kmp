package com.greenzean.animekmp.composeapp.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.details.presentation.AiringStatus
import com.greenzean.animekmp.shared.details.presentation.AnimeDetailsUi
import com.greenzean.animekmp.shared.details.presentation.DetailsState
import com.greenzean.animekmp.shared.details.presentation.DetailsStatus
import com.greenzean.animekmp.shared.details.presentation.EntryEditUi
import com.greenzean.animekmp.shared.details.presentation.MalDate
import com.greenzean.animekmp.shared.library.domain.WatchStatus

private val previewDetails =
    AnimeDetailsUi(
        malId = 52991,
        title = "Frieren: Beyond Journey's End",
        otherTitle = "Sousou no Frieren",
        imageUrl = null,
        synopsis = "After the party of heroes defeated the Demon King, they restored peace to the land.",
        episodes = 28,
        meanScore = 9.3,
        genres = listOf("Adventure", "Drama", "Fantasy"),
        airingStatus = AiringStatus.Finished,
        startDate = MalDate(2023, 9, 29),
    )

private val previewEntry =
    EntryEditUi(WatchStatus.WATCHING, progress = 12, totalEpisodes = 28, score = 9, selectableEpisodes = 28)

private val planEntry = previewEntry.copy(status = WatchStatus.PLAN_TO_WATCH, progress = 0, score = null)

private val loaded = DetailsState(previewDetails, entry = null, status = DetailsStatus.Loaded)

@Preview
@Composable
private fun DetailsLoadedLightPreview() = DetailsPreview(loaded, darkTheme = false)

@Preview
@Composable
private fun DetailsLoadedDarkPreview() = DetailsPreview(loaded, darkTheme = true)

@Preview
@Composable
private fun DetailsInLibraryLightPreview() = DetailsPreview(loaded.copy(entry = previewEntry), darkTheme = false)

@Preview
@Composable
private fun DetailsInLibraryDarkPreview() = DetailsPreview(loaded.copy(entry = previewEntry), darkTheme = true)

@Preview
@Composable
private fun DetailsPlanToWatchLightPreview() = DetailsPreview(loaded.copy(entry = planEntry), darkTheme = false)

@Preview
@Composable
private fun DetailsPlanToWatchDarkPreview() = DetailsPreview(loaded.copy(entry = planEntry), darkTheme = true)

@Preview
@Composable
private fun DetailsOfflineEditorLightPreview() =
    DetailsPreview(
        DetailsState(entry = previewEntry.copy(totalEpisodes = null, score = null), status = DetailsStatus.Offline),
        darkTheme = false,
    )

@Preview
@Composable
private fun DetailsOfflineEditorDarkPreview() =
    DetailsPreview(
        DetailsState(entry = previewEntry.copy(totalEpisodes = null, score = null), status = DetailsStatus.Offline),
        darkTheme = true,
    )

@Preview
@Composable
private fun DetailsLoadingLightPreview() = DetailsPreview(DetailsState(), darkTheme = false)

@Preview
@Composable
private fun DetailsLoadingDarkPreview() = DetailsPreview(DetailsState(), darkTheme = true)

@Composable
internal fun DetailsPreview(
    state: DetailsState,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) { DetailsScreen(state = state, onAction = {}) }
}
