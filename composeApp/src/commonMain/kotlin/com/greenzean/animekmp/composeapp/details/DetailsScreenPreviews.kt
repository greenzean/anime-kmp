package com.greenzean.animekmp.composeapp.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.details.presentation.AiringStatus
import com.greenzean.animekmp.shared.details.presentation.AnimeDetailsUi
import com.greenzean.animekmp.shared.details.presentation.DetailsState
import com.greenzean.animekmp.shared.details.presentation.DetailsStatus
import com.greenzean.animekmp.shared.details.presentation.MalDate

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

private val loaded = DetailsState(previewDetails, inLibrary = false, status = DetailsStatus.Loaded)

@Preview
@Composable
private fun DetailsLoadedLightPreview() = DetailsPreview(loaded, darkTheme = false)

@Preview
@Composable
private fun DetailsLoadedDarkPreview() = DetailsPreview(loaded, darkTheme = true)

@Preview
@Composable
private fun DetailsInLibraryLightPreview() = DetailsPreview(loaded.copy(inLibrary = true), darkTheme = false)

@Preview
@Composable
private fun DetailsInLibraryDarkPreview() = DetailsPreview(loaded.copy(inLibrary = true), darkTheme = true)

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
