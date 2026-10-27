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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_add_to_library
import com.greenzean.animekmp.composeapp.generated.resources.action_cancel
import com.greenzean.animekmp.composeapp.generated.resources.action_remove_from_library
import com.greenzean.animekmp.composeapp.generated.resources.action_retry
import com.greenzean.animekmp.composeapp.generated.resources.action_try_again
import com.greenzean.animekmp.composeapp.generated.resources.airing_finished
import com.greenzean.animekmp.composeapp.generated.resources.airing_not_yet_aired
import com.greenzean.animekmp.composeapp.generated.resources.cd_back
import com.greenzean.animekmp.composeapp.generated.resources.cd_loading_details
import com.greenzean.animekmp.composeapp.generated.resources.cd_progress_decrement
import com.greenzean.animekmp.composeapp.generated.resources.cd_progress_edit
import com.greenzean.animekmp.composeapp.generated.resources.cd_progress_increment
import com.greenzean.animekmp.composeapp.generated.resources.cd_score_badge
import com.greenzean.animekmp.composeapp.generated.resources.details_facts_separator
import com.greenzean.animekmp.composeapp.generated.resources.details_mal_score
import com.greenzean.animekmp.composeapp.generated.resources.details_no_synopsis
import com.greenzean.animekmp.composeapp.generated.resources.details_not_found_message
import com.greenzean.animekmp.composeapp.generated.resources.details_not_found_title
import com.greenzean.animekmp.composeapp.generated.resources.details_started
import com.greenzean.animekmp.composeapp.generated.resources.details_starts
import com.greenzean.animekmp.composeapp.generated.resources.details_throttled_message
import com.greenzean.animekmp.composeapp.generated.resources.details_throttled_title
import com.greenzean.animekmp.composeapp.generated.resources.details_title
import com.greenzean.animekmp.composeapp.generated.resources.details_view_on_mal
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_dialog_title
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_label
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_value
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_value_unknown
import com.greenzean.animekmp.composeapp.generated.resources.entry_score_label
import com.greenzean.animekmp.composeapp.generated.resources.entry_score_none
import com.greenzean.animekmp.composeapp.generated.resources.entry_status_label
import com.greenzean.animekmp.composeapp.generated.resources.episodes_count
import com.greenzean.animekmp.composeapp.generated.resources.error_mal_unreachable
import com.greenzean.animekmp.composeapp.generated.resources.offline_title
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_completed
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_dropped
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_on_hold
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_plan_to_watch
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_watching
import com.greenzean.animekmp.composeapp.plural
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.details.presentation.AiringStatus
import com.greenzean.animekmp.shared.details.presentation.AnimeDetailsUi
import com.greenzean.animekmp.shared.details.presentation.DetailsAction
import com.greenzean.animekmp.shared.details.presentation.DetailsState
import com.greenzean.animekmp.shared.details.presentation.DetailsStatus
import com.greenzean.animekmp.shared.details.presentation.EntryEditUi
import com.greenzean.animekmp.shared.details.presentation.MalDate
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import org.jetbrains.compose.resources.StringResource
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
        startDate = MalDate(2023, 9, 29),
    )

private fun facts(
    airing: StringResource,
    episodes: Int,
    startedLabel: String,
    date: String,
): String {
    val start = if (startedLabel == "Starts") Res.string.details_starts else Res.string.details_started
    return listOf(
        str(airing),
        plural(Res.plurals.episodes_count, episodes, episodes),
        str(Res.string.details_mal_score, "9.3"),
        str(start, date),
    ).joinToString(str(Res.string.details_facts_separator))
}

internal val loaded = DetailsState(frieren, status = DetailsStatus.Loaded)

internal val entry =
    EntryEditUi(WatchStatus.WATCHING, progress = 5, totalEpisodes = 28, score = 7, selectableEpisodes = 28)

internal val loadedInLibrary = loaded.copy(entry = entry)

internal class Screen(
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

            onNodeWithContentDescription(str(Res.string.cd_loading_details)).assertIsDisplayed()
        }

    @Test
    fun loadedShowsTitlesFactsGenresAndSynopsis() =
        runComposeUiTest {
            show(loaded)

            onNodeWithText("Frieren: Beyond Journey's End").assertIsDisplayed()
            onNodeWithText("Sousou no Frieren").assertIsDisplayed()
            onNodeWithText(facts(Res.string.airing_finished, 28, "Started", "Sep 29, 2023")).assertIsDisplayed()
            onNodeWithText("Adventure").assertIsDisplayed()
            onNodeWithText("Drama").assertIsDisplayed()
            onNodeWithText("After the party of heroes defeated the Demon King.").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun singleEpisodeAnimeSaysEpisodeInTheSingular() =
        runComposeUiTest {
            show(loaded.copy(details = frieren.copy(episodes = 1)))

            onNodeWithText(facts(Res.string.airing_finished, 1, "Started", "Sep 29, 2023")).assertIsDisplayed()
        }

    @Test
    fun animeNotYetAiredSaysStartsInsteadOfStarted() =
        runComposeUiTest {
            val upcoming = frieren.copy(airingStatus = AiringStatus.NotYetAired, startDate = MalDate(2027, 10, null))
            show(loaded.copy(details = upcoming))

            onNodeWithText(facts(Res.string.airing_not_yet_aired, 28, "Starts", "Oct 2027")).assertIsDisplayed()
        }

    @Test
    fun dateWithOnlyAYearOrMonthAndYearIsFormatted() {
        listOf(MalDate(2023, null, null) to "2023", MalDate(2023, 9, null) to "Sep 2023").forEach { (date, text) ->
            runComposeUiTest {
                show(loaded.copy(details = frieren.copy(startDate = date)))

                onNodeWithText(str(Res.string.details_started, text), substring = true).assertIsDisplayed()
            }
        }
    }

    @Test
    fun theTopBarTitleIsGenericSoTheAnimeTitleIsReadOnce() =
        runComposeUiTest {
            show(loaded)

            onNodeWithText(str(Res.string.details_title)).assertIsDisplayed()
            onAllNodesWithText("Frieren: Beyond Journey's End").assertCountEquals(1)
        }

    @Test
    fun loadedWithoutASynopsisSaysSo() =
        runComposeUiTest {
            show(loaded.copy(details = frieren.copy(synopsis = null)))

            onNodeWithText(str(Res.string.details_no_synopsis)).assertIsDisplayed()
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
            onNodeWithText(str(Res.string.airing_finished), substring = true).assertDoesNotExist()
            onNodeWithText(
                plural(Res.plurals.episodes_count, 28, 28).substringAfter(" "),
                substring = true,
            ).assertDoesNotExist()
            onNodeWithText(str(Res.string.details_mal_score, "").trim(), substring = true).assertDoesNotExist()
            onNodeWithText("Adventure").assertDoesNotExist()
            onNodeWithText("Drama").assertDoesNotExist()
            onNodeWithText(str(Res.string.details_started, "").trim(), substring = true).assertDoesNotExist()
            onNodeWithText(str(Res.string.details_starts, "").trim(), substring = true).assertDoesNotExist()
        }

    @Test
    fun addButtonSendsAddWhenNotInLibrary() =
        runComposeUiTest {
            val screen = show(loaded)

            onNodeWithText(str(Res.string.action_add_to_library)).performScrollTo().performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnAddClick), screen.actions)
        }

    @Test
    fun inLibraryHidesTheAddButtonAndShowsTheEditor() =
        runComposeUiTest {
            show(loadedInLibrary)

            onNodeWithText(str(Res.string.action_add_to_library)).assertDoesNotExist()
            onNodeWithText(str(Res.string.entry_status_label)).performScrollTo().assertIsDisplayed()
        }

    @Test
    fun notInLibraryHasNoEditor() =
        runComposeUiTest {
            show(loaded)

            onNodeWithText(str(Res.string.entry_status_label)).assertDoesNotExist()
            onNodeWithText(str(Res.string.entry_progress_label)).assertDoesNotExist()
            onNodeWithText(str(Res.string.entry_score_label)).assertDoesNotExist()
            onNodeWithText(str(Res.string.action_remove_from_library)).assertDoesNotExist()
        }

    @Test
    fun editorShowsStatusProgressAndScore() =
        runComposeUiTest {
            show(loadedInLibrary)

            onNodeWithText(str(Res.string.entry_status_label)).performScrollTo().assertIsDisplayed()
            onNodeWithText(str(Res.string.watch_status_watching)).performScrollTo().assertIsDisplayed()
            onNodeWithText(str(Res.string.entry_progress_label)).performScrollTo().assertIsDisplayed()
            onNodeWithText(str(Res.string.entry_progress_value, 5, 28)).performScrollTo().assertIsDisplayed()
            onNodeWithText(str(Res.string.entry_score_label)).performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription(str(Res.string.cd_score_badge, 7)).performScrollTo().assertIsDisplayed()
        }

    @Test
    fun editorShowsAnUnknownTotalWithAQuestionMark() =
        runComposeUiTest {
            show(loaded.copy(entry = entry.copy(totalEpisodes = null)))

            onNodeWithText(str(Res.string.entry_progress_value_unknown, 5)).performScrollTo().assertIsDisplayed()
        }

    @Test
    fun editorSaysNoScoreWhenThereIsNone() =
        runComposeUiTest {
            show(loaded.copy(entry = entry.copy(score = null)))

            onNodeWithContentDescription(str(Res.string.entry_score_none)).performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription(str(Res.string.cd_score_badge, 7)).assertDoesNotExist()
        }

    @Test
    fun editorIsShownOfflineWithoutDetails() =
        runComposeUiTest {
            val screen = show(DetailsState(entry = entry, status = DetailsStatus.Offline))

            onNodeWithText(str(Res.string.offline_title)).performScrollTo().assertIsDisplayed()
            onNodeWithText(str(Res.string.entry_status_label)).assertIsDisplayed()
            onNodeWithText(str(Res.string.entry_progress_value, 5, 28)).assertIsDisplayed()
            onNodeWithText(str(Res.string.action_remove_from_library)).performScrollTo().assertIsDisplayed()
            onNodeWithText(str(Res.string.action_try_again)).performScrollTo().performClick()
            assertEquals(listOf<DetailsAction>(DetailsAction.OnRetryClick), screen.actions)
        }

    @Test
    fun offlineWithoutAnEntryHasNoEditor() =
        runComposeUiTest {
            show(DetailsState(status = DetailsStatus.Offline))

            onNodeWithText(str(Res.string.entry_status_label)).assertDoesNotExist()
        }

    @Test
    fun editorIsShownInEveryMessageStatus() {
        listOf(DetailsStatus.Error, DetailsStatus.Throttled, DetailsStatus.NotFound).forEach { status ->
            runComposeUiTest {
                show(DetailsState(entry = entry, status = status))

                onNodeWithText(str(Res.string.entry_status_label)).assertIsDisplayed()
                onNodeWithText(str(Res.string.action_remove_from_library)).assertIsDisplayed()
            }
        }
    }

    @Test
    fun loadingHasNoEditor() =
        runComposeUiTest {
            show(DetailsState(entry = entry, status = DetailsStatus.Loading))

            onNodeWithText(str(Res.string.entry_status_label)).assertDoesNotExist()
        }

    @Test
    fun statusRowOpensADialogWithEveryStatus() =
        runComposeUiTest {
            show(loadedInLibrary)

            onNodeWithText(str(Res.string.entry_status_label)).performScrollTo().performClick()

            onAllNodesWithText(str(Res.string.watch_status_watching)).assertCountEquals(2)
            onNodeWithText(str(Res.string.watch_status_completed)).assertIsDisplayed()
            onNodeWithText(str(Res.string.watch_status_on_hold)).assertIsDisplayed()
            onNodeWithText(str(Res.string.watch_status_dropped)).assertIsDisplayed()
            onNodeWithText(str(Res.string.watch_status_plan_to_watch)).assertIsDisplayed()
            onNodeWithText(str(Res.string.action_cancel)).assertDoesNotExist()
        }

    @Test
    fun choosingAStatusSendsItAndClosesTheDialog() =
        runComposeUiTest {
            val screen = show(loadedInLibrary)
            onNodeWithText(str(Res.string.entry_status_label)).performScrollTo().performClick()

            onNodeWithText(str(Res.string.watch_status_on_hold)).performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnStatusChange(WatchStatus.ON_HOLD)), screen.actions)
            onNodeWithText(str(Res.string.action_cancel)).assertDoesNotExist()
        }

    @Test
    fun plusButtonAddsOneEpisode() =
        runComposeUiTest {
            val screen = show(loadedInLibrary)

            onNodeWithContentDescription(str(Res.string.cd_progress_increment)).performScrollTo().performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnProgressChange(6)), screen.actions)
        }

    @Test
    fun minusButtonRemovesOneEpisode() =
        runComposeUiTest {
            val screen = show(loadedInLibrary)

            onNodeWithContentDescription(str(Res.string.cd_progress_decrement)).performScrollTo().performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnProgressChange(4)), screen.actions)
        }

    @Test
    fun minusButtonIsDisabledAtZeroEpisodes() =
        runComposeUiTest {
            show(loaded.copy(entry = entry.copy(progress = 0)))

            onNodeWithContentDescription(str(Res.string.cd_progress_decrement)).performScrollTo().assertIsNotEnabled()
            onNodeWithContentDescription(str(Res.string.cd_progress_increment)).performScrollTo().assertIsEnabled()
        }

    private val progressEdit
        get() = str(Res.string.cd_progress_edit, str(Res.string.entry_progress_value, 5, 28))

    @Test
    fun minusButtonIsDisabledAtOneEpisode() =
        runComposeUiTest {
            show(loaded.copy(entry = entry.copy(progress = 1)))

            onNodeWithContentDescription(str(Res.string.cd_progress_decrement)).performScrollTo().assertIsNotEnabled()
            onNodeWithContentDescription(str(Res.string.cd_progress_increment)).performScrollTo().assertIsEnabled()
        }

    @Test
    fun progressButtonOpensAGridWithEveryEpisodeAndNoTextField() =
        runComposeUiTest {
            show(loadedInLibrary)

            onNodeWithContentDescription(progressEdit).performScrollTo().performClick()

            onAllNodesWithText(str(Res.string.entry_progress_dialog_title)).assertCountEquals(2)
            onNodeWithText(str(Res.string.action_cancel)).assertDoesNotExist()
            onNode(hasSetTextAction()).assertDoesNotExist()
            for (episode in 1..28) {
                onNodeWithText(episode.toString()).performScrollTo().assertIsDisplayed()
            }
        }

    @Test
    fun theGridEndsAtTheSelectableEpisodes() =
        runComposeUiTest {
            show(loaded.copy(entry = entry.copy(selectableEpisodes = 10)))
            onNodeWithContentDescription(progressEdit).performScrollTo().performClick()

            onNodeWithText("10").performScrollTo().assertIsDisplayed()
            onNodeWithText("11").assertDoesNotExist()
        }

    @Test
    fun choosingAnEpisodeSendsItAndClosesTheDialog() =
        runComposeUiTest {
            val screen = show(loadedInLibrary)
            onNodeWithContentDescription(progressEdit).performScrollTo().performClick()

            onNodeWithText("12").performScrollTo().performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnProgressChange(12)), screen.actions)
            onNodeWithText(str(Res.string.action_cancel)).assertDoesNotExist()
        }

    @Test
    fun choosingTheLastEpisodeSendsIt() =
        runComposeUiTest {
            val screen = show(loadedInLibrary)
            onNodeWithContentDescription(progressEdit).performScrollTo().performClick()

            onNodeWithText("28").performScrollTo().performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnProgressChange(28)), screen.actions)
        }

    @Test
    fun progressWithAnUnknownTotalOpensTheGrid() =
        runComposeUiTest {
            val screen = show(loaded.copy(entry = entry.copy(totalEpisodes = null, selectableEpisodes = 17)))
            onNodeWithContentDescription(
                str(Res.string.cd_progress_edit, str(Res.string.entry_progress_value_unknown, 5)),
            ).performScrollTo()
                .performClick()

            onNodeWithText("17").performScrollTo().performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnProgressChange(17)), screen.actions)
        }

    @Test
    fun planToWatchHidesTheEpisodesRowButKeepsStatusAndScore() =
        runComposeUiTest {
            show(loaded.copy(entry = entry.copy(status = WatchStatus.PLAN_TO_WATCH, progress = 0)))

            onNodeWithText(str(Res.string.entry_progress_label)).assertDoesNotExist()
            onNodeWithContentDescription(str(Res.string.cd_progress_decrement)).assertDoesNotExist()
            onNodeWithContentDescription(str(Res.string.cd_progress_increment)).assertDoesNotExist()
            onNodeWithContentDescription(str(Res.string.entry_progress_value, 0, 28)).assertDoesNotExist()
            onNodeWithText(str(Res.string.entry_progress_value, 0, 28)).assertDoesNotExist()
            onNodeWithText(str(Res.string.entry_status_label)).performScrollTo().assertIsDisplayed()
            onNodeWithText(str(Res.string.watch_status_plan_to_watch)).performScrollTo().assertIsDisplayed()
            onNodeWithText(str(Res.string.entry_score_label)).performScrollTo().assertIsDisplayed()
        }

    @Test
    fun removeSendsRemove() =
        runComposeUiTest {
            val screen = show(loadedInLibrary)

            onNodeWithText(str(Res.string.action_remove_from_library)).performScrollTo().performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnRemoveClick), screen.actions)
        }

    @Test
    fun editorControlsMeetTheMinimumTouchTarget() =
        runComposeUiTest {
            show(loadedInLibrary)

            listOf(Res.string.cd_progress_increment, Res.string.cd_progress_decrement).forEach {
                onNodeWithContentDescription(str(it))
                    .performScrollTo()
                    .assertTouchWidthIsEqualTo(48.dp)
                    .assertTouchHeightIsEqualTo(48.dp)
            }
            onNodeWithContentDescription(progressEdit).performScrollTo().assertHeightIsAtLeast(48.dp)
            onNodeWithText(str(Res.string.entry_status_label)).performScrollTo().assertTouchHeightIsEqualTo(48.dp)
            onNodeWithText(str(Res.string.entry_score_label)).performScrollTo().assertTouchHeightIsEqualTo(48.dp)
            onNodeWithText(str(Res.string.action_remove_from_library)).performScrollTo().assertHeightIsAtLeast(48.dp)
        }

    @Test
    fun viewOnMyAnimeListOpensTheAnimePage() =
        runComposeUiTest {
            val screen = show(loaded)

            onNodeWithText(str(Res.string.details_view_on_mal)).performScrollTo().performClick()

            assertEquals(listOf("https://myanimelist.net/anime/52991"), screen.openedUrls)
        }

    @Test
    fun backButtonSendsBack() =
        runComposeUiTest {
            val screen = show(loaded)

            onNodeWithContentDescription(str(Res.string.cd_back)).performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnBackClick), screen.actions)
        }

    @Test
    fun backButtonIsAvailableInEveryStatus() {
        DetailsStatus.entries.forEach { status ->
            runComposeUiTest {
                show(DetailsState(details = frieren.takeIf { status == DetailsStatus.Loaded }, status = status))

                onNodeWithContentDescription(str(Res.string.cd_back)).assertIsDisplayed()
            }
        }
    }

    @Test
    fun offlineExplainsAndOffersRetry() =
        runComposeUiTest {
            val screen = show(DetailsState(status = DetailsStatus.Offline))

            onNodeWithText(str(Res.string.offline_title)).assertIsDisplayed()
            onNodeWithText(str(Res.string.action_try_again)).performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnRetryClick), screen.actions)
        }

    @Test
    fun notFoundExplainsTheAnimeIsMissingWithoutRetry() =
        runComposeUiTest {
            show(DetailsState(status = DetailsStatus.NotFound))

            onNodeWithText(str(Res.string.details_not_found_title)).assertIsDisplayed()
            onNodeWithText(str(Res.string.details_not_found_message)).assertIsDisplayed()
            onNodeWithText(str(Res.string.action_retry)).assertDoesNotExist()
            onNodeWithText(str(Res.string.action_try_again)).assertDoesNotExist()
            onNodeWithText(str(Res.string.error_mal_unreachable)).assertDoesNotExist()
        }

    @Test
    fun throttledSaysMalIsBusyAndOffersRetry() =
        runComposeUiTest {
            val screen = show(DetailsState(status = DetailsStatus.Throttled))

            onNodeWithText(str(Res.string.details_throttled_title)).assertIsDisplayed()
            onNodeWithText(str(Res.string.details_throttled_message)).assertIsDisplayed()
            onNodeWithText(str(Res.string.error_mal_unreachable)).assertDoesNotExist()
            onNodeWithText(str(Res.string.action_retry)).performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnRetryClick), screen.actions)
        }

    @Test
    fun errorOffersRetry() =
        runComposeUiTest {
            val screen = show(DetailsState(status = DetailsStatus.Error))

            onNodeWithText(str(Res.string.error_mal_unreachable)).assertIsDisplayed()
            onNodeWithText(str(Res.string.action_retry)).performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnRetryClick), screen.actions)
        }

    @Test
    fun controlsMeetTheMinimumTouchTarget() =
        runComposeUiTest {
            show(loaded)

            onNodeWithText(str(Res.string.action_add_to_library)).assertHeightIsAtLeast(48.dp)
            onNodeWithText(str(Res.string.details_view_on_mal)).assertHeightIsAtLeast(48.dp)
            onNodeWithContentDescription(
                str(Res.string.cd_back),
            ).assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
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
            onNodeWithText(str(Res.string.action_add_to_library)).assertIsDisplayed()
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
