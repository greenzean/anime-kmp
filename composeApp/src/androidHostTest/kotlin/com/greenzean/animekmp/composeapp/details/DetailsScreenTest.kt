package com.greenzean.animekmp.composeapp.details

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.shared.details.presentation.AiringStatus
import com.greenzean.animekmp.shared.details.presentation.AnimeDetailsUi
import com.greenzean.animekmp.shared.details.presentation.DetailsAction
import com.greenzean.animekmp.shared.details.presentation.DetailsState
import com.greenzean.animekmp.shared.details.presentation.DetailsStatus
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val frieren =
    AnimeDetailsUi(
        malId = 52991,
        title = "Frieren: Beyond Journey's End",
        otherTitle = "Sousou no Frieren",
        imageUrl = null,
        synopsis = "After the party of heroes defeated the Demon King.",
        episodes = 28,
        meanScore = 9.3,
        genres = listOf("Adventure", "Drama"),
        airingStatus = AiringStatus.Finished,
        startDate = "Sep 29, 2023",
    )

private val loaded = DetailsState(frieren, inLibrary = false, status = DetailsStatus.Loaded)

private class Screen(
    val actions: MutableList<DetailsAction> = mutableListOf(),
    val openedUrls: MutableList<String> = mutableListOf(),
)

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class DetailsScreenTest {
    private fun ComposeUiTest.show(
        state: DetailsState,
        darkTheme: Boolean = false,
    ): Screen {
        val screen = Screen()
        val uriHandler =
            object : UriHandler {
                override fun openUri(uri: String) {
                    screen.openedUrls += uri
                }
            }
        setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                AppTheme(darkTheme) { DetailsScreen(state = state, onAction = { screen.actions += it }) }
            }
        }
        return screen
    }

    @Test
    fun loadingShowsAProgressIndicatorWithALabel() =
        runComposeUiTest {
            show(DetailsState())

            onNodeWithContentDescription("Loading details").assertIsDisplayed()
        }

    @Test
    fun loadedShowsTitlesFactsGenresAndSynopsis() =
        runComposeUiTest {
            show(loaded)

            onNodeWithText("Frieren: Beyond Journey's End").assertIsDisplayed()
            onNodeWithText("Sousou no Frieren").assertIsDisplayed()
            onNodeWithText("Finished airing · 28 episodes · MAL score 9.3 · Started Sep 29, 2023").assertIsDisplayed()
            onNodeWithText("Adventure").assertIsDisplayed()
            onNodeWithText("Drama").assertIsDisplayed()
            onNodeWithText("After the party of heroes defeated the Demon King.").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun singleEpisodeAnimeSaysEpisodeInTheSingular() =
        runComposeUiTest {
            show(loaded.copy(details = frieren.copy(episodes = 1)))

            onNodeWithText("Finished airing · 1 episode · MAL score 9.3 · Started Sep 29, 2023").assertIsDisplayed()
        }

    @Test
    fun animeNotYetAiredSaysStartsInsteadOfStarted() =
        runComposeUiTest {
            show(loaded.copy(details = frieren.copy(airingStatus = AiringStatus.NotYetAired, startDate = "Oct 2027")))

            onNodeWithText("Not yet aired · 28 episodes · MAL score 9.3 · Starts Oct 2027").assertIsDisplayed()
        }

    @Test
    fun theTopBarTitleIsGenericSoTheAnimeTitleIsReadOnce() =
        runComposeUiTest {
            show(loaded)

            onNodeWithText("Details").assertIsDisplayed()
            onAllNodesWithText("Frieren: Beyond Journey's End").assertCountEquals(1)
        }

    @Test
    fun loadedWithoutASynopsisSaysSo() =
        runComposeUiTest {
            show(loaded.copy(details = frieren.copy(synopsis = null)))

            onNodeWithText("No synopsis available.").assertIsDisplayed()
        }

    @Test
    fun loadedWithMissingFactsOmitsThem() =
        runComposeUiTest {
            show(
                loaded.copy(
                    details =
                        frieren.copy(
                            episodes = null,
                            meanScore = null,
                            airingStatus = null,
                            startDate = null,
                            otherTitle = null,
                            genres = emptyList(),
                        ),
                ),
            )

            onNodeWithText("Sousou no Frieren").assertDoesNotExist()
            onNodeWithText("Finished airing", substring = true).assertDoesNotExist()
            onNodeWithText("episodes", substring = true).assertDoesNotExist()
            onNodeWithText("MAL score", substring = true).assertDoesNotExist()
            onNodeWithText("Adventure").assertDoesNotExist()
            onNodeWithText("Drama").assertDoesNotExist()
            onNodeWithText("Started", substring = true).assertDoesNotExist()
            onNodeWithText("Starts", substring = true).assertDoesNotExist()
        }

    @Test
    fun addButtonSendsAddWhenNotInLibrary() =
        runComposeUiTest {
            val screen = show(loaded)

            onNodeWithText("Add to library").performScrollTo().performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnAddClick), screen.actions)
        }

    @Test
    fun inLibraryHidesTheAddButtonAndSaysSo() =
        runComposeUiTest {
            show(loaded.copy(inLibrary = true))

            onNodeWithText("In library").assertIsDisplayed()
            onNodeWithText("Add to library").assertDoesNotExist()
        }

    @Test
    fun notInLibraryDoesNotSayInLibrary() =
        runComposeUiTest {
            show(loaded)

            onNodeWithText("In library").assertDoesNotExist()
        }

    @Test
    fun viewOnMyAnimeListOpensTheAnimePage() =
        runComposeUiTest {
            val screen = show(loaded)

            onNodeWithText("View on MyAnimeList").performScrollTo().performClick()

            assertEquals(listOf("https://myanimelist.net/anime/52991"), screen.openedUrls)
        }

    @Test
    fun backButtonSendsBack() =
        runComposeUiTest {
            val screen = show(loaded)

            onNodeWithContentDescription("Back").performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnBackClick), screen.actions)
        }

    @Test
    fun backButtonIsAvailableInEveryStatus() {
        DetailsStatus.entries.forEach { status ->
            runComposeUiTest {
                show(DetailsState(details = frieren.takeIf { status == DetailsStatus.Loaded }, status = status))

                onNodeWithContentDescription("Back").assertIsDisplayed()
            }
        }
    }

    @Test
    fun offlineExplainsAndOffersRetry() =
        runComposeUiTest {
            val screen = show(DetailsState(status = DetailsStatus.Offline))

            onNodeWithText("You're offline").assertIsDisplayed()
            onNodeWithText("Try again").performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnRetryClick), screen.actions)
        }

    @Test
    fun errorOffersRetry() =
        runComposeUiTest {
            val screen = show(DetailsState(status = DetailsStatus.Error))

            onNodeWithText("MyAnimeList could not be reached. Try again.").assertIsDisplayed()
            onNodeWithText("Retry").performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnRetryClick), screen.actions)
        }

    @Test
    fun controlsMeetTheMinimumTouchTarget() =
        runComposeUiTest {
            show(loaded)

            onNodeWithText("Add to library").assertHeightIsAtLeast(48.dp)
            onNodeWithText("View on MyAnimeList").assertHeightIsAtLeast(48.dp)
            onNodeWithContentDescription("Back").assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        }

    @Test
    fun loadedContentIsShownInTheDarkTheme() =
        runComposeUiTest {
            var background = Color.Unspecified
            setContent {
                AppTheme(darkTheme = true) {
                    background = MaterialTheme.colorScheme.background
                    DetailsScreen(state = loaded, onAction = {})
                }
            }

            assertTrue(background.luminance() < 0.5f)
            onNodeWithText("Add to library").assertIsDisplayed()
            onNodeWithText("Sousou no Frieren").assertIsDisplayed()
        }

    @Test
    fun loadedContentIsShownInTheLightTheme() =
        runComposeUiTest {
            var background = Color.Unspecified
            setContent {
                AppTheme(darkTheme = false) {
                    background = MaterialTheme.colorScheme.background
                    DetailsScreen(state = loaded, onAction = {})
                }
            }

            assertTrue(background.luminance() > 0.5f)
        }
}
